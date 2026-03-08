package com.codingtestkit.service

import com.google.gson.JsonParser
import org.jsoup.Jsoup

object SolvedAcApi {

    private const val BASE_URL = "https://solved.ac/api/v3"

    data class ProblemInfo(
        val problemId: Int,
        val title: String,
        val level: Int,
        val tags: List<String>,
        val acceptedUserCount: Int
    )

    data class SearchResult(
        val problems: List<ProblemInfo>,
        val totalCount: Int
    )

    /**
     * 문제 검색 (solved.ac /search/problem)
     */
    fun searchProblems(
        query: String,
        sort: String = "id",
        direction: String = "asc",
        page: Int = 1
    ): SearchResult {
        val json = Jsoup.connect("$BASE_URL/search/problem")
            .data("query", query)
            .data("sort", sort)
            .data("direction", direction)
            .data("page", page.toString())
            .ignoreContentType(true)
            .userAgent("Mozilla/5.0")
            .timeout(10000)
            .get()
            .body()
            .text()

        val root = JsonParser.parseString(json).asJsonObject
        val count = root.get("count")?.asInt ?: 0
        val items = root.getAsJsonArray("items") ?: return SearchResult(emptyList(), 0)

        val problems = items.map { parseProblem(it.asJsonObject) }
        return SearchResult(problems, count)
    }

    /**
     * 랜덤 문제 뽑기
     */
    fun searchRandomProblems(
        tierQuery: String?,
        tagKey: String?,
        count: Int
    ): List<ProblemInfo> {
        val queryParts = mutableListOf("solvable:true")
        if (!tierQuery.isNullOrBlank()) queryParts.add(tierQuery)
        if (!tagKey.isNullOrBlank()) queryParts.add("tag:$tagKey")

        val result = searchProblems(
            query = queryParts.joinToString(" "),
            sort = "random"
        )
        return result.problems.take(count)
    }

    /**
     * 자동완성 제안 (solved.ac /search/suggestion)
     */
    fun searchSuggestions(query: String): List<ProblemInfo> {
        if (query.isBlank()) return emptyList()

        val json = Jsoup.connect("$BASE_URL/search/suggestion")
            .data("query", query)
            .ignoreContentType(true)
            .userAgent("Mozilla/5.0")
            .timeout(5000)
            .get()
            .body()
            .text()

        val root = JsonParser.parseString(json).asJsonObject
        val problems = root.getAsJsonArray("problems") ?: return emptyList()

        return problems.map { parseProblem(it.asJsonObject) }
    }

    private fun parseProblem(obj: com.google.gson.JsonObject): ProblemInfo {
        val tags = obj.getAsJsonArray("tags")?.mapNotNull { tagEl ->
            tagEl.asJsonObject.getAsJsonArray("displayNames")
                ?.firstOrNull { it.asJsonObject.get("language").asString == "ko" }
                ?.asJsonObject?.get("short")?.asString
        } ?: emptyList()

        return ProblemInfo(
            problemId = obj.get("problemId").asInt,
            title = obj.get("titleKo")?.asString ?: "",
            level = obj.get("level")?.asInt ?: 0,
            tags = tags,
            acceptedUserCount = obj.get("acceptedUserCount")?.asInt ?: 0
        )
    }

    fun levelToString(level: Int): String {
        if (level == 0) return "Unrated"
        val tiers = arrayOf("Bronze", "Silver", "Gold", "Platinum", "Diamond", "Ruby")
        val ranks = arrayOf("V", "IV", "III", "II", "I")
        val tierIdx = (level - 1) / 5
        val rankIdx = (level - 1) % 5
        return if (tierIdx in tiers.indices) "${tiers[tierIdx]} ${ranks[rankIdx]}" else "Unrated"
    }
}
