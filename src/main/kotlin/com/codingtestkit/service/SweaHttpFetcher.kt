package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import com.intellij.openapi.diagnostic.Logger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * SWEA 문제를 Jsoup HTTP POST로 직접 가져오는 fetcher.
 * JCEF(headless browser) 없이 raw HTML을 파싱하여 콘텐츠를 추출한다.
 *
 * SWEA는 AngularJS 기반이지만, 문제 데이터가 서버에서 HTML에 주입되는 경우
 * 브라우저 렌더링 없이도 추출 가능하다. (문제당 ~0.5-1초 vs JCEF ~5-15초)
 *
 * 추출 실패 시 null을 반환하여 호출자가 JCEF fallback을 사용하도록 한다.
 */
object SweaHttpFetcher {

    private val LOG = Logger.getInstance(SweaHttpFetcher::class.java)

    private const val BASE_URL = "https://swexpertacademy.com"
    private const val DETAIL_URL = "$BASE_URL/main/code/problem/problemDetail.do"

    var lastDiagnostic: String = ""
        private set

    fun fetchProblem(contestProbId: String, cookies: String): Problem? {
        if (cookies.isBlank()) {
            lastDiagnostic = "No cookies"
            return null
        }

        try {
            val doc = Jsoup.connect(DETAIL_URL)
                .data("contestProbId", contestProbId)
                .data("categoryId", contestProbId)
                .data("categoryType", "CODE")
                .header("Cookie", cookies)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                .referrer("$BASE_URL/main/code/problem/problemList.do")
                .timeout(15_000)
                .maxBodySize(5 * 1024 * 1024)
                .post()

            // 로그인 페이지로 리다이렉트 체크
            val location = doc.location() ?: ""
            if (location.contains("login") || location.contains("signUp")) {
                lastDiagnostic = "Redirected to login"
                LOG.info("[CodingTestKit] SweaHttpFetcher: redirected to login")
                return null
            }

            return extractFromDocument(doc, contestProbId)
        } catch (e: Exception) {
            lastDiagnostic = "HTTP error: ${e.message}"
            LOG.info("[CodingTestKit] SweaHttpFetcher failed: ${e.message}")
            return null
        }
    }

    private fun extractFromDocument(doc: Document, contestProbId: String): Problem? {
        val diag = StringBuilder()

        // ── 제목 추출 ──
        var title = ""
        var problemNumber = ""

        val titleSelectors = listOf(
            "div.problem_box > p.problem_title",
            ".problem_title",
            "#problem_title",
            "div.problem_box > h3"
        )
        for (sel in titleSelectors) {
            val el = doc.selectFirst(sel) ?: continue
            val clone = el.clone()
            clone.select(".badge").remove()
            val text = clone.text().trim()
            val numMatch = Regex("^(\\d+)\\.\\s*").find(text)
            if (numMatch != null) problemNumber = numMatch.groupValues[1]
            val cleaned = text.replace(Regex("^\\d+\\.\\s*"), "")
            if (cleaned.isNotBlank()) {
                title = cleaned
                break
            }
        }
        if (title.isBlank()) {
            title = doc.title() ?: ""
        }
        if (problemNumber.isBlank()) {
            val dtMatch = Regex("(\\d{4,6})\\.").find(doc.title() ?: "")
            if (dtMatch != null) problemNumber = dtMatch.groupValues[1]
        }
        diag.appendLine("title='$title', number='$problemNumber'")

        // ── 난이도 ──
        var difficulty = ""
        val badgeEl = doc.selectFirst(".badge, [class*=badge], [class*=difficulty]")
        if (badgeEl != null) {
            val badgeText = badgeEl.text().trim()
            if (badgeText.matches(Regex("D\\d"))) difficulty = badgeText
        }
        if (difficulty.isBlank()) {
            val titleEl = doc.selectFirst(".problem_title, h2, h3")
            if (titleEl != null) {
                val dMatch = Regex("D(\\d)").find(titleEl.text())
                if (dMatch != null) difficulty = "D${dMatch.groupValues[1]}"
            }
        }

        // ── 시간/메모리 제한 ──
        var timeLimit = ""
        var memoryLimit = ""
        for (li in doc.select("li")) {
            val text = li.text().replace(Regex("\\s+"), " ").trim()
            if (text.length > 80) continue
            if (text.contains(Regex("시간|Time", RegexOption.IGNORE_CASE)) &&
                text.contains(Regex("\\d+\\s*초|\\d+\\s*sec", RegexOption.IGNORE_CASE))
            ) {
                timeLimit = text.replace(Regex("^[·•\\s]*(시간|Time\\s*Limit)\\s*:?\\s*", RegexOption.IGNORE_CASE), "").trim()
            }
            if (text.contains(Regex("메모리|Memory", RegexOption.IGNORE_CASE)) &&
                text.contains(Regex("\\d+\\s*MB|\\d+\\s*KB", RegexOption.IGNORE_CASE))
            ) {
                memoryLimit = text.replace(Regex("^[·•\\s]*(메모리|Memory\\s*Limit)\\s*:?\\s*", RegexOption.IGNORE_CASE), "").trim()
            }
        }

        // ── 문제 설명 추출 (여러 전략) ──
        var description = ""

        // 전략 1: inline script에서 problemCont / contestProb JSON 추출
        for (script in doc.select("script")) {
            val content = script.html()
            if (content.contains("problemCont") || content.contains("contestProb")) {
                diag.appendLine("strategy1: found script with problemCont/contestProb (len=${content.length})")

                // $scope.contestProb = {...} 패턴
                val jsonMatch = Regex("""contestProb\s*=\s*(\{[\s\S]*?\});""").find(content)
                if (jsonMatch != null) {
                    val json = jsonMatch.groupValues[1]
                    val contMatch = Regex(""""problemCont"\s*:\s*"([\s\S]*?)"""").find(json)
                    if (contMatch != null) {
                        description = unescapeJs(contMatch.groupValues[1])
                        diag.appendLine("strategy1: extracted from JSON (len=${description.length})")
                    }
                }

                // problemCont = "..." or problemCont: "..." 패턴
                if (description.isBlank()) {
                    val directMatch = Regex("""problemCont\s*[=:]\s*['"](.+?)['"];""", RegexOption.DOT_MATCHES_ALL).find(content)
                    if (directMatch != null) {
                        description = unescapeJs(directMatch.groupValues[1])
                        diag.appendLine("strategy1: extracted direct assignment (len=${description.length})")
                    }
                }

                // HTML-escaped content in script
                if (description.isBlank()) {
                    val htmlMatch = Regex("""problemCont\s*[=:]\s*['"]([\s\S]+?)['"]""").find(content)
                    if (htmlMatch != null) {
                        description = unescapeJs(htmlMatch.groupValues[1])
                        diag.appendLine("strategy1: extracted html-escaped (len=${description.length})")
                    }
                }
            }
        }

        // 전략 2: ng-init 속성에서 추출
        if (description.length < 100) {
            for (el in doc.select("[ng-init]")) {
                val init = el.attr("ng-init")
                if (init.contains("problemCont") || init.contains("contestProb")) {
                    diag.appendLine("strategy2: found ng-init with problemCont (len=${init.length})")
                    val contMatch = Regex("""problemCont\s*[=:]\s*['"](.+?)['"]""", RegexOption.DOT_MATCHES_ALL).find(init)
                    if (contMatch != null) {
                        description = unescapeJs(contMatch.groupValues[1])
                        diag.appendLine("strategy2: extracted (len=${description.length})")
                    }
                }
            }
        }

        // 전략 3: ng-bind-html 요소의 innerHTML (서버사이드 프리렌더링 된 경우)
        if (description.length < 100) {
            val contentParts = mutableListOf<String>()
            for (el in doc.select("[ng-bind-html]")) {
                val html = el.html()
                if (html.length < 15) continue
                val text = el.text().trim()
                if (text.contains(Regex("※.*무단\\s*복제"))) continue
                if (isCommentContent(html)) continue
                if (contentParts.none { it == html || it.contains(html) }) {
                    contentParts.add(html)
                }
            }
            if (contentParts.isNotEmpty()) {
                description = contentParts.joinToString("<hr>")
                diag.appendLine("strategy3: ng-bind-html parts=${contentParts.size}, len=${description.length}")
            }
        }

        // 전략 4: div.box4
        if (description.length < 100) {
            val box4 = doc.selectFirst("div.box4")
            if (box4 != null && box4.html().length > 100) {
                description = box4.html()
                diag.appendLine("strategy4: div.box4 (len=${description.length})")
            }
        }

        // 전략 5: p.txt
        if (description.length < 100) {
            val ptxt = doc.selectFirst("p.txt")
            if (ptxt != null && ptxt.html().length > 100) {
                description = ptxt.html()
                diag.appendLine("strategy5: p.txt (len=${description.length})")
            }
        }

        // 전략 6: [입력]/[출력] 키워드가 있는 요소
        if (description.length < 100) {
            var bestHtml = ""
            var bestLen = Int.MAX_VALUE
            for (el in doc.select("div, section, p")) {
                val text = el.text()
                val html = el.html()
                if ((text.contains("[입력]") || text.contains("[출력]")) &&
                    html.length in 201..49999
                ) {
                    if (!text.contains(Regex("참여자.*제출.*정답")) && !isCommentContent(html)) {
                        if (html.length < bestLen) {
                            bestHtml = html; bestLen = html.length
                        }
                    }
                }
            }
            if (bestHtml.isNotBlank()) {
                description = bestHtml
                diag.appendLine("strategy6: keyword search (len=${description.length})")
            }
        }

        // 전략 7: 공통 CSS 셀렉터
        if (description.length < 100) {
            val selectors = listOf(
                "#problemContent", ".problem_description", ".problemContent",
                ".problem_txt", ".desc_box", ".problem_content", ".view_content"
            )
            for (sel in selectors) {
                val el = doc.selectFirst(sel)
                if (el != null && el.html().length > 100) {
                    description = el.html()
                    diag.appendLine("strategy7: selector '$sel' (len=${description.length})")
                    break
                }
            }
        }

        // ── 설명 후처리 ──
        if (description.isNotBlank()) {
            description = description
                .replace("&amp;", "&")
                .replace(Regex("""ng-src=""""), """src="""")
                .replace(Regex("""data-src=""""), """src="""")
                .replace(Regex("""src="/([^"]*?)""""), """src="$BASE_URL/$1"""")
                .replace(Regex("""src="(?!https?:|data:|file:)([^"]*?)""""), """src="$BASE_URL/$1"""")
                .replace(Regex("""<a[^>]*>[^<]*다운로드[^<]*</a>""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""<a[^>]*>[^<]*download[^<]*</a>""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""<button[^>]*>[^<]*다운로드[^<]*</button>""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\S*input\S*\.txt""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\S*output\S*\.txt""", RegexOption.IGNORE_CASE), "")
        }

        // ── 테스트 케이스 ──
        var inputText = ""
        var outputText = ""
        for (box in doc.select("div.box5")) {
            val header = box.selectFirst("span.title1") ?: continue
            val firstTd = box.selectFirst("table td:first-child") ?: continue
            val headerText = header.text().trim()
            val data = cleanTestData(firstTd.text())
            if (headerText == "입력" && data.isNotBlank() && inputText.isBlank()) inputText = data
            if (headerText == "출력" && data.isNotBlank() && outputText.isBlank()) outputText = data
        }
        if (inputText.isBlank() || outputText.isBlank()) {
            val preEls = doc.select("pre, code")
            if (preEls.size >= 2) {
                if (inputText.isBlank()) inputText = cleanTestData(preEls[0].text())
                if (outputText.isBlank()) outputText = cleanTestData(preEls[1].text())
            }
        }
        val testCases = if (inputText.isNotBlank() && outputText.isNotBlank()) {
            mutableListOf(TestCase(input = inputText, expectedOutput = outputText))
        } else mutableListOf()

        // ── 결과 판단 ──
        lastDiagnostic = diag.toString()
        LOG.info("[CodingTestKit] SweaHttpFetcher diagnostic:\n$diag")

        if (description.length < 50 && title.length < 3) {
            LOG.info("[CodingTestKit] SweaHttpFetcher: content too short, returning null for JCEF fallback")
            return null
        }

        return Problem(
            source = ProblemSource.SWEA,
            id = problemNumber.ifBlank { "" },
            title = title.ifBlank { "SWEA" },
            description = description,
            testCases = testCases,
            timeLimit = timeLimit,
            memoryLimit = memoryLimit,
            difficulty = difficulty.ifBlank { "Unrated" },
            contestProbId = contestProbId
        )
    }

    private fun isCommentContent(html: String): Boolean {
        val text = html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
        if (text.contains(Regex("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}")) && text.contains("댓글")) return true
        if (text.contains(Regex("\\d+\\s*/\\s*\\d+자"))) return true
        if (text.contains("등록") && text.contains(Regex("@\\S+"))) return true
        return false
    }

    private fun cleanTestData(text: String): String =
        text.lines()
            .filter { !it.trimStart().startsWith("//") }
            .joinToString("\n")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()

    private fun unescapeJs(s: String): String =
        s.replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\")
            .replace("\\u003c", "<")
            .replace("\\u003e", ">")
            .replace("\\u0026", "&")
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
}
