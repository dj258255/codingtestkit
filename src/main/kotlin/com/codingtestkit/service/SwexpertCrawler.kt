package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object SwexpertCrawler {

    private const val BASE_URL = "https://swexpertacademy.samsung.com/main/code/problem/problemDetail.do"

    fun fetchProblem(problemId: String, cookies: String): Problem {
        val doc = Jsoup.connect("$BASE_URL?contestProbId=$problemId")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Cookie", cookies)
            .timeout(10000)
            .get()

        val title = extractTitle(doc, problemId)
        val description = extractDescription(doc)
        val testCases = extractTestCases(doc)

        return Problem(
            source = ProblemSource.SWEA,
            id = problemId,
            title = title,
            description = description,
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
