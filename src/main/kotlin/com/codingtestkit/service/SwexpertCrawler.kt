package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object SwexpertCrawler {

    private const val BASE_URL = "https://swexpertacademy.com/main/code/problem/problemDetail.do"

    fun fetchProblem(problemId: String, cookies: String): Problem {
        val contestProbId = if (problemId.all { it.isDigit() }) problemId else problemId
        val displayId = problemId

        val doc = try {
            Jsoup.connect("$BASE_URL?contestProbId=$contestProbId")
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", cookies)
                .timeout(10000)
                .get()
        } catch (_: Exception) { null }

        val title = if (doc != null) extractTitle(doc, displayId) else "SWEA #$displayId"
        val description = if (doc != null) extractDescription(doc) else ""
        val testCases = if (doc != null) extractTestCases(doc) else mutableListOf()

        val problemUrl = "$BASE_URL?contestProbId=$contestProbId"
        val finalDescription = if (description.length > 30) {
            description
        } else {
            "<p>SWEA 문제는 JavaScript로 렌더링되어 자동으로 가져올 수 없습니다.</p>" +
            "<p><a href='$problemUrl'>브라우저에서 문제 보기</a></p>" +
            "<p>테스트 케이스는 + 버튼으로 직접 추가해주세요.</p>"
        }

        return Problem(
            source = ProblemSource.SWEA,
            id = displayId,
            title = title,
            description = finalDescription,
            testCases = testCases
        )
    }

    private fun extractTitle(doc: Document, problemId: String): String {
        val selectors = listOf(
            ".problem_title",
            "#title",
            ".title h3",
            ".contest_title"
        )
        for (sel in selectors) {
            val text = doc.select(sel).text().trim()
            if (text.isNotBlank()) return text
        }
        return doc.select("title").text().ifBlank { "SWEA #$problemId" }
    }

    private fun extractDescription(doc: Document): String {
        val selectors = listOf(
            "#problemContent",
            ".problem_description",
            ".problemContent",
            ".problem_area"
        )
        for (sel in selectors) {
            val element = doc.select(sel)
            if (element.isNotEmpty() && element.html().length > 20) {
                return element.html()
            }
        }
        return "<p>문제 설명을 가져올 수 없습니다. 로그인 상태를 확인해주세요.</p>"
    }

    private fun extractTestCases(doc: Document): MutableList<TestCase> {
        val testCases = mutableListOf<TestCase>()

        // SWEA는 보통 "입력" / "출력" 섹션에 예시가 있음
        val inputSection = doc.select(".problem_sample_input, #sampleInput, .sample_input")
        val outputSection = doc.select(".problem_sample_output, #sampleOutput, .sample_output")

        if (inputSection.isNotEmpty() && outputSection.isNotEmpty()) {
            val inputs = inputSection.select("pre").map { it.text().trim() }
            val outputs = outputSection.select("pre").map { it.text().trim() }

            val count = minOf(inputs.size, outputs.size)
            for (i in 0 until count) {
                testCases.add(TestCase(input = inputs[i], expectedOutput = outputs[i]))
            }
        }

        // pre 태그에서 직접 추출 시도
        if (testCases.isEmpty()) {
            val allPre = doc.select("pre")
            if (allPre.size >= 2) {
                testCases.add(
                    TestCase(
                        input = allPre[0].text().trim(),
                        expectedOutput = allPre[1].text().trim()
                    )
                )
            }
        }

        return testCases
    }
}
