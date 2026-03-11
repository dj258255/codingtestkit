package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import org.jsoup.Jsoup

object CodeforcesCrawler {

    private const val BASE_URL = "https://codeforces.com/problemset/problem/"

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

    fun fetchProblem(problemId: String): Problem {
        val (contestId, letter) = parseProblemId(problemId)

        val doc = Jsoup.connect("${BASE_URL}$contestId/$letter?locale=en")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .timeout(30000)
            .get()

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
