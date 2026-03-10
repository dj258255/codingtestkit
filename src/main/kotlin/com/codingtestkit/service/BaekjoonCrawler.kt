package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import com.google.gson.JsonParser
import org.jsoup.Jsoup

object BaekjoonCrawler {

    private const val BASE_URL = "https://www.acmicpc.net/problem/"

    fun fetchProblem(problemId: String): Problem {
        val doc = Jsoup.connect("$BASE_URL$problemId")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()

        // 이미지: 상대 경로 → 절대 경로, 고정 크기 속성 제거 (반응형)
        doc.select("img[src]").forEach { img ->
            val src = img.attr("src")
            if (src.startsWith("/")) {
                img.attr("src", "https://www.acmicpc.net$src")
            } else if (!src.startsWith("http")) {
                img.attr("src", "https://www.acmicpc.net/$src")
            }
            img.removeAttr("width")
            img.removeAttr("height")
        }

        val title = doc.select("#problem_title").text()
        val description = doc.select("#problem_description").html()
        val inputDesc = doc.select("#problem_input").html()
        val outputDesc = doc.select("#problem_output").html()
        val limitDesc = doc.select("#problem_limit").html()
        val hintDesc = doc.select("#problem_hint").html()

        val infoTds = doc.select("#problem-info tbody tr td")
        val timeLimit = infoTds.getOrNull(0)?.text() ?: ""
        val memoryLimit = infoTds.getOrNull(1)?.text() ?: ""

        val testCases = mutableListOf<TestCase>()
        var i = 1
        while (true) {
            val sampleInput = doc.select("#sample-input-$i")
            val sampleOutput = doc.select("#sample-output-$i")
            if (sampleInput.isEmpty() && sampleOutput.isEmpty()) break
            testCases.add(
                TestCase(
                    input = sampleInput.text().replace("\r\n", "\n"),
                    expectedOutput = sampleOutput.text().replace("\r\n", "\n")
                )
            )
            i++
        }

        val fullDescription = buildString {
            append("<h2>문제</h2>")
            append(description)
            if (inputDesc.isNotBlank()) {
                append("<h2>입력</h2>")
                append(inputDesc)
            }
            if (outputDesc.isNotBlank()) {
                append("<h2>출력</h2>")
                append(outputDesc)
            }
            if (limitDesc.isNotBlank()) {
                append("<h2>제한</h2>")
                append(limitDesc)
            }

            // 예제 입출력 + 예제 설명
            var si = 1
            while (true) {
                val sIn = doc.select("#sample-input-$si")
                val sOut = doc.select("#sample-output-$si")
                if (sIn.isEmpty() && sOut.isEmpty()) break
                append("<h2>예제 입력 $si</h2>")
                append("<pre>${sIn.text()}</pre>")
                append("<h2>예제 출력 $si</h2>")
                append("<pre>${sOut.text()}</pre>")
                val explain = doc.select("#problem_sample_explain_$si").html()
                if (explain.isNotBlank()) {
                    append(explain)
                }
                si++
            }

            if (hintDesc.isNotBlank()) {
                append("<h2>힌트</h2>")
                append(hintDesc)
            }
        }

        val difficulty = fetchDifficulty(problemId)

        return Problem(
            source = ProblemSource.BAEKJOON,
            id = problemId,
            title = title,
            description = fullDescription,
            testCases = testCases,
            timeLimit = timeLimit,
            memoryLimit = memoryLimit,
            difficulty = difficulty
        )
    }

    private fun fetchDifficulty(problemId: String): String {
        return try {
            val json = Jsoup.connect("https://solved.ac/api/v3/problem/show?problemId=$problemId")
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0")
                .timeout(5000)
                .get()
                .body()
                .text()
            val level = JsonParser.parseString(json).asJsonObject.get("level")?.asInt ?: 0
            SolvedAcApi.levelToString(level)
        } catch (_: Exception) {
            "Unrated"
        }
    }
}
