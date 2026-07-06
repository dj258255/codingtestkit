package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import org.jsoup.Jsoup

object CodeforcesCrawler {

    private const val BASE_URL = "https://codeforces.com/problemset/problem/"

    private const val MATHJAX_ARTIFACT_SELECTOR =
        ".MathJax, .MathJax_Preview, .MathJax_Display, .MathJax_Error, " +
            ".MathJax_CHTML, .mjx-chtml, .MathJax_SVG, .MathJax_SVG_Display, #MathJax_Message"

    /**
     * 저장된 문제 본문에서 MathJax 렌더링 잔여물을 제거하고 원본 TeX를
     * 최종 구분자($ / $$)로 복원 (이슈 #25).
     *
     * 수정 이전 버전에서 JCEF 폴백으로 가져와 problem.json에 오염된 채
     * 저장된 문제들을 위한 로드 시점 정리 — 재가져오기 없이도 표시·번역이
     * 정상화된다. 오염되지 않은 본문은 빠른 경로로 그대로 반환.
     */
    fun stripMathJaxArtifacts(html: String): String {
        if (!html.contains("MathJax") && !html.contains("math/tex")) return html
        val body = Jsoup.parseBodyFragment(html).body()
        body.select("script[type~=math/tex]").forEach { script ->
            val delim = if (script.attr("type").contains("display")) "\$\$" else "\$"
            script.before(org.jsoup.nodes.TextNode("$delim${script.data()}$delim"))
            script.remove()
        }
        body.select(MATHJAX_ARTIFACT_SELECTOR).remove()
        return body.html()
    }

    /**
     * "1234A" → ("1234", "A"), "1234/A" → ("1234", "A")
     */
    fun parseProblemId(input: String): Pair<String, String> {
        val cleaned = input.trim().replace("/", "")
        val match = Regex("^(\\d+)([A-Za-z]\\d?)$").find(cleaned)
            ?: throw IllegalArgumentException(
                I18n.t("문제 ID 형식이 올바르지 않습니다. 예: 1234A", "Invalid problem ID format. e.g. 1234A")
            )
        return match.groupValues[1] to match.groupValues[2].uppercase()
    }

    fun fetchProblem(problemId: String, cookies: String = ""): Problem {
        val (contestId, letter) = parseProblemId(problemId)

        // 로그인 쿠키(cf_clearance 포함)를 함께 보내면 Cloudflare 챌린지를 통과할 수 있음.
        // cf_clearance는 User-Agent에 바인딩되므로 JCEF(Chrome)와 유사한 UA를 사용.
        // 이전 JCEF 폴백 세션이 챌린지를 통과했다면 그 쿠키도 재사용 (이슈 #21).
        // 같은 이름의 쿠키는 호출자(로그인) 쿠키가 우선.
        val merged = mergeCookieHeaders(CodeforcesJcefFetcher.getCloudflareCookies(), cookies)
        val connection = Jsoup.connect("${BASE_URL}$contestId/$letter?locale=en")
            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            .timeout(30000)
        if (merged.isNotBlank()) connection.header("Cookie", merged)
        val doc = connection.get()
        return parseProblemDoc(doc, contestId, letter)
    }

    /**
     * "a=1; b=2" 형태의 Cookie 헤더 두 개를 병합. 같은 이름이면 override 쪽이 우선.
     */
    internal fun mergeCookieHeaders(base: String, override: String): String {
        val map = LinkedHashMap<String, String>()
        for (part in base.split(";") + override.split(";")) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val i = p.indexOf('=')
            if (i <= 0) continue
            map[p.substring(0, i).trim()] = p.substring(i + 1).trim()
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * JCEF 폴백 경로: 브라우저가 렌더링한 HTML을 동일한 파서로 처리.
     * Cloudflare가 Jsoup의 TLS 지문을 차단할 때 사용된다.
     */
    fun parseProblemHtml(html: String, problemId: String): Problem {
        val (contestId, letter) = parseProblemId(problemId)
        return parseProblemDoc(Jsoup.parse(html, "https://codeforces.com"), contestId, letter)
    }

    private fun parseProblemDoc(doc: org.jsoup.nodes.Document, contestId: String, letter: String): Problem {
        // JCEF 폴백은 MathJax가 이미 렌더링한 DOM을 가져오므로 수식마다
        // 렌더링 결과(span)와 원본 TeX(script)가 공존한다 (이슈 #25).
        // 원본 TeX를 $$$ 구분자 텍스트로 복원하고 렌더링 잔여물은 제거해
        // Jsoup 경로(원본 HTML)와 동일한 형태로 맞춘다. 원본 HTML에는
        // 해당 노드가 없어 no-op.
        doc.select("script[type~=math/tex]").forEach { script ->
            val display = script.attr("type").contains("display")
            val delim = if (display) "\$\$\$\$\$\$" else "\$\$\$" // 아래 정규화($$$→$)를 그대로 타는 원본 구분자
            script.before(org.jsoup.nodes.TextNode("$delim${script.data()}$delim"))
            script.remove()
        }
        doc.select(MATHJAX_ARTIFACT_SELECTOR).remove()

        // 이미지: 상대 경로 → 절대 경로, 고정 크기 제거
        doc.select("img[src]").forEach { img ->
            val src = img.attr("src")
            if (src.startsWith("/")) img.attr("src", "https://codeforces.com$src")
            else if (!src.startsWith("http")) img.attr("src", "https://codeforces.com/$src")
            img.removeAttr("width")
            img.removeAttr("height")
        }

        val statement = doc.select(".problem-statement")
        val titleText = statement.select(".title").first()?.text() ?: ""
        val title = titleText.removePrefix("$letter. ").trim()

        val timeLimit = statement.select(".time-limit").text()
            .replace("time limit per test", "").trim()
        val memoryLimit = statement.select(".memory-limit").text()
            .replace("memory limit per test", "").trim()

        // 본문 섹션 추출 (problem-statement의 직접 자식 div들)
        val sections = statement.select("> div")

        val description = buildString {
            for (div in sections) {
                val cls = div.className()
                when {
                    cls.contains("header") -> {} // skip (title, time, memory)
                    cls.contains("input-specification") -> {
                        append("<h2>Input</h2>")
                        append(div.select("> div.section-title ~ *").outerHtml()
                            .ifBlank { div.html().replace(Regex("<div class=\"section-title\">.*?</div>"), "") })
                    }
                    cls.contains("output-specification") -> {
                        append("<h2>Output</h2>")
                        append(div.select("> div.section-title ~ *").outerHtml()
                            .ifBlank { div.html().replace(Regex("<div class=\"section-title\">.*?</div>"), "") })
                    }
                    cls.contains("sample-tests") -> {
                        val inputs = div.select(".input pre")
                        val outputs = div.select(".output pre")
                        for (i in inputs.indices) {
                            append("<h2>Example Input ${i + 1}</h2>")
                            append("<pre>${inputs.getOrNull(i)?.html() ?: ""}</pre>")
                            append("<h2>Example Output ${i + 1}</h2>")
                            append("<pre>${outputs.getOrNull(i)?.html() ?: ""}</pre>")
                        }
                    }
                    cls.contains("note") -> {
                        append("<h2>Note</h2>")
                        append(div.html().replace(Regex("<div class=\"section-title\">.*?</div>"), ""))
                    }
                    else -> {
                        // 일반 본문 (Problem Statement)
                        append("<h2>Problem</h2>")
                        append(div.html())
                    }
                }
            }
        }

        // Codeforces는 $$$$$$...$$$$$$(display)과 $$$...$$$(inline)을 LaTeX 구분자로 사용
        // ProblemPanel의 KaTeX 처리가 인식하는 $$...$$ / $...$ 형태로 변환
        val descNormalized = description
            .replace("\$\$\$\$\$\$", "\$\$")  // display math: 6→2
            .replace("\$\$\$", "\$")            // inline math: 3→1

        // 테스트 케이스 추출
        val testCases = mutableListOf<TestCase>()
        val sampleInputs = statement.select(".sample-test .input pre")
        val sampleOutputs = statement.select(".sample-test .output pre")
        for (i in sampleInputs.indices) {
            val input = sampleInputs.getOrNull(i)?.let { pre ->
                pre.html().replace("<br>", "\n").replace("<br/>", "\n")
                    .replace("</div>", "\n")
                    .replace(Regex("<[^>]+>"), "").trim()
            } ?: ""
            val output = sampleOutputs.getOrNull(i)?.let { pre ->
                pre.html().replace("<br>", "\n").replace("<br/>", "\n")
                    .replace("</div>", "\n")
                    .replace(Regex("<[^>]+>"), "").trim()
            } ?: ""
            testCases.add(TestCase(input = input, expectedOutput = output))
        }

        val difficulty = fetchDifficulty(contestId, letter)

        return Problem(
            source = ProblemSource.CODEFORCES,
            id = "$contestId$letter",
            title = title,
            description = descNormalized,
            testCases = testCases,
            timeLimit = timeLimit,
            memoryLimit = memoryLimit,
            difficulty = difficulty,
            contestProbId = "$contestId/$letter"
        )
    }

    private fun fetchDifficulty(contestId: String, letter: String): String {
        return try {
            // CodeforcesApi의 캐시된 문제 목록에서 난이도 조회 (별도 API 호출 불필요)
            val id = "$contestId$letter"
            val problems = CodeforcesApi.searchProblems(query = id, limit = 1)
            val match = problems.firstOrNull { it.id == id }
            match?.ratingDisplay ?: "Unrated"
        } catch (_: Exception) {
            "Unrated"
        }
    }
}
