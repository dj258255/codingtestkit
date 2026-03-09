package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.jsoup.Connection
import org.jsoup.Jsoup

object LeetCodeApi {

    private const val GRAPHQL_URL = "https://leetcode.com/graphql"
    private val gson = Gson()

    data class LeetCodeProblemInfo(
        val frontendId: String,
        val title: String,
        val titleSlug: String,
        val difficulty: String,
        val acRate: Double = 0.0,
        val tags: List<String> = emptyList()
    )

    data class SearchResult(
        val problems: List<LeetCodeProblemInfo>,
        val totalCount: Int
    )

    /**
     * 문제 번호 또는 slug로 문제를 가져옴
     */
    fun fetchProblem(input: String, language: String = "java"): Problem {
        val slug = resolveSlug(input)

        val query = """
            query questionData(${'$'}titleSlug: String!) {
              question(titleSlug: ${'$'}titleSlug) {
                questionId
                questionFrontendId
                title
                titleSlug
                content
                difficulty
                exampleTestcaseInput
                sampleTestCase
                codeSnippets {
                  lang
                  langSlug
                  code
                }
                metaData
                topicTags {
                  name
                  slug
                }
              }
            }
        """.trimIndent()

        val result = graphql(query, mapOf("titleSlug" to slug))
        val question = result.getAsJsonObject("data")?.getAsJsonObject("question")
            ?: throw RuntimeException("Problem not found: $input")

        val frontendId = question.get("questionFrontendId")?.asString ?: ""
        val title = question.get("title")?.asString ?: ""
        val content = question.get("content")?.asString ?: ""
        val difficulty = question.get("difficulty")?.asString ?: ""

        // 태그 추출
        val tags = question.getAsJsonArray("topicTags")?.map {
            it.asJsonObject.get("name")?.asString ?: ""
        } ?: emptyList()

        // 코드 스니펫에서 선택한 언어의 코드 추출
        val langSlug = languageToLeetCodeSlug(language)
        val codeSnippets = question.getAsJsonArray("codeSnippets")
        val initialCode = codeSnippets?.firstOrNull {
            it.asJsonObject.get("langSlug")?.asString == langSlug
        }?.asJsonObject?.get("code")?.asString ?: ""

        // 테스트 케이스 추출
        val testCases = extractTestCases(content, question.get("exampleTestcaseInput")?.asString)

        // metaData에서 파라미터 이름 추출
        val paramNames = extractParamNames(question.get("metaData")?.asString)

        return Problem(
            source = ProblemSource.LEETCODE,
            id = frontendId,
            title = title,
            description = content,
            testCases = testCases,
            difficulty = difficulty,
            parameterNames = paramNames,
            initialCode = initialCode
        )
    }

    /**
     * 문제 검색
     */
    fun searchProblems(
        query: String = "",
        difficulty: String? = null,
        tags: List<String>? = null,
        status: String? = null,
        limit: Int = 20,
        skip: Int = 0,
        cookies: String? = null
    ): SearchResult {
        val filters = mutableMapOf<String, Any?>()
        if (query.isNotBlank()) filters["searchKeywords"] = query
        if (!difficulty.isNullOrBlank()) filters["difficulty"] = difficulty
        if (!tags.isNullOrEmpty()) filters["tags"] = tags
        if (!status.isNullOrBlank()) filters["status"] = status

        val gql = """
            query problemsetQuestionList(${'$'}categorySlug: String, ${'$'}limit: Int, ${'$'}skip: Int, ${'$'}filters: QuestionListFilterInput) {
              problemsetQuestionList: questionList(
                categorySlug: ${'$'}categorySlug
                limit: ${'$'}limit
                skip: ${'$'}skip
                filters: ${'$'}filters
              ) {
                total: totalNum
                questions: data {
                  questionFrontendId
                  title
                  titleSlug
                  difficulty
                  acRate
                  topicTags {
                    name
                    slug
                  }
                }
              }
            }
        """.trimIndent()

        val variables = mapOf(
            "categorySlug" to "",
            "limit" to limit,
            "skip" to skip,
            "filters" to filters
        )

        val result = graphql(gql, variables, cookies)
        val list = result.getAsJsonObject("data")
            ?.getAsJsonObject("problemsetQuestionList")
            ?: return SearchResult(emptyList(), 0)

        val total = list.get("total")?.asInt ?: 0
        val questions = list.getAsJsonArray("questions")?.map { q ->
            val obj = q.asJsonObject
            LeetCodeProblemInfo(
                frontendId = obj.get("questionFrontendId")?.asString ?: "",
                title = obj.get("title")?.asString ?: "",
                titleSlug = obj.get("titleSlug")?.asString ?: "",
                difficulty = obj.get("difficulty")?.asString ?: "",
                acRate = obj.get("acRate")?.asDouble ?: 0.0,
                tags = obj.getAsJsonArray("topicTags")?.map {
                    it.asJsonObject.get("name")?.asString ?: ""
                } ?: emptyList()
            )
        } ?: emptyList()

        return SearchResult(questions, total)
    }

    /**
     * 입력을 slug로 변환
     * - URL → slug 추출
     * - 숫자 → frontendId로 검색해서 slug 찾기
     * - 그 외 → slug로 직접 사용
     */
    private fun resolveSlug(input: String): String {
        val trimmed = input.trim()

        // URL에서 slug 추출: https://leetcode.com/problems/two-sum/
        val urlMatch = Regex("leetcode\\.com/problems/([a-z0-9-]+)").find(trimmed)
        if (urlMatch != null) return urlMatch.groupValues[1]

        // 숫자면 frontendId로 검색
        if (trimmed.all { it.isDigit() }) {
            val result = searchProblems(query = trimmed, limit = 50)
            val exact = result.problems.firstOrNull { it.frontendId == trimmed }
            if (exact != null) return exact.titleSlug
            throw RuntimeException("Problem #$trimmed not found")
        }

        // slug로 직접 사용 (하이픈/영문)
        return trimmed.lowercase().replace(" ", "-")
    }

    private fun extractTestCases(htmlContent: String, exampleInput: String?): MutableList<TestCase> {
        val testCases = mutableListOf<TestCase>()

        // HTML에서 예제 출력 추출
        val outputs = mutableListOf<String>()
        // <strong>Output:</strong> 패턴으로 출력값 추출
        val outputPattern = Regex("""<strong>Output:?\s*</strong>\s*([^<\n]+)""", RegexOption.IGNORE_CASE)
        for (match in outputPattern.findAll(htmlContent)) {
            outputs.add(match.groupValues[1].trim())
        }

        // exampleTestcaseInput은 줄바꿈으로 구분된 입력들
        // 각 테스트 케이스의 파라미터 수를 metaData에서 알 수 있지만,
        // 간단하게 출력 개수와 맞춰서 분배
        if (exampleInput != null && outputs.isNotEmpty()) {
            val inputLines = exampleInput.split("\n").filter { it.isNotBlank() }

            if (outputs.size == 1 && inputLines.isNotEmpty()) {
                // 테스트 케이스 1개
                testCases.add(TestCase(
                    input = inputLines.joinToString("\n"),
                    expectedOutput = outputs[0]
                ))
            } else if (outputs.size > 1) {
                // 여러 테스트 케이스 — 파라미터 수 계산하여 분배
                val paramsPerCase = if (outputs.isNotEmpty()) inputLines.size / outputs.size else inputLines.size
                if (paramsPerCase > 0) {
                    for (i in outputs.indices) {
                        val start = i * paramsPerCase
                        val end = minOf(start + paramsPerCase, inputLines.size)
                        if (start < inputLines.size) {
                            testCases.add(TestCase(
                                input = inputLines.subList(start, end).joinToString("\n"),
                                expectedOutput = outputs[i]
                            ))
                        }
                    }
                }
            }
        }

        return testCases
    }

    private fun extractParamNames(metaDataJson: String?): List<String> {
        if (metaDataJson.isNullOrBlank()) return emptyList()
        return try {
            val meta = JsonParser.parseString(metaDataJson).asJsonObject
            meta.getAsJsonArray("params")?.map {
                it.asJsonObject.get("name")?.asString ?: ""
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun languageToLeetCodeSlug(language: String): String = when (language.lowercase()) {
        "java" -> "java"
        "python", "py" -> "python3"
        "c++", "cpp" -> "cpp"
        "kotlin", "kt" -> "kotlin"
        "javascript", "js" -> "javascript"
        else -> "java"
    }

    // ─── 전체 문제 통계 (정답자 수 + 풀이 상태) ───

    data class ProblemStat(
        val totalAccepted: Int,
        val status: String?  // "ac", "notac", null
    )

    @Volatile
    private var cachedStats: Map<String, ProblemStat>? = null

    /**
     * /api/problems/all/ 에서 전체 문제 통계를 가져옴
     * cookies가 있으면 풀이 상태(status)도 포함됨
     */
    fun fetchAllProblemStats(cookies: String? = null): Map<String, ProblemStat> {
        cachedStats?.let { return it }

        val conn = Jsoup.connect("https://leetcode.com/api/problems/all/")
            .ignoreContentType(true)
            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .header("Referer", "https://leetcode.com")
            .timeout(20000)
        if (!cookies.isNullOrBlank()) conn.header("Cookie", cookies)

        val json = conn.get().body().text()
        val root = JsonParser.parseString(json).asJsonObject
        val pairs = root.getAsJsonArray("stat_status_pairs") ?: return emptyMap()

        val map = mutableMapOf<String, ProblemStat>()
        for (el in pairs) {
            val obj = el.asJsonObject
            val stat = obj.getAsJsonObject("stat") ?: continue
            val fid = stat.get("frontend_question_id")?.asInt?.toString() ?: continue
            val totalAcs = stat.get("total_acs")?.asInt ?: 0
            val status = if (obj.get("status")?.isJsonNull == false) obj.get("status")?.asString else null
            map[fid] = ProblemStat(totalAcs, status)
        }
        cachedStats = map
        return map
    }

    /** 캐시 초기화 (재로그인 시 호출) */
    fun clearStatsCache() { cachedStats = null }

    private fun graphql(query: String, variables: Map<String, Any?>, cookies: String? = null): com.google.gson.JsonObject {
        val body = gson.toJson(mapOf("query" to query, "variables" to variables))

        val conn = Jsoup.connect(GRAPHQL_URL)
            .method(Connection.Method.POST)
            .header("Content-Type", "application/json")
            .header("Referer", "https://leetcode.com")
            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .requestBody(body)
            .ignoreContentType(true)
            .timeout(15000)
        if (!cookies.isNullOrBlank()) conn.header("Cookie", cookies)
        val response = conn.execute().body()

        return JsonParser.parseString(response).asJsonObject
    }
}
