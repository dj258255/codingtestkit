package com.codingtestkit.service

import com.google.gson.JsonParser
import org.jsoup.Jsoup

object CodeforcesApi {

    data class ProblemInfo(
        val contestId: Int,
        val index: String,
        val name: String,
        val rating: Int,
        val tags: List<String>,
        val solvedCount: Int
    ) {
        val id: String get() = "$contestId$index"
        val ratingDisplay: String get() = if (rating > 0) "*$rating" else "Unrated"
    }

    private var cachedProblems: List<ProblemInfo>? = null
    private var lastFetchTime = 0L

    private fun fetchAllProblems(): List<ProblemInfo> {
        val now = System.currentTimeMillis()
        val cached = cachedProblems
        if (cached != null && now - lastFetchTime < 300_000) return cached // 5분 캐시

        val json = Jsoup.connect("https://codeforces.com/api/problemset.problems?lang=en")
            .ignoreContentType(true)
            .userAgent("Mozilla/5.0")
            .maxBodySize(0)
            .timeout(15000)
            .execute()
            .body()

        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("status")?.asString != "OK") throw Exception("Codeforces API error")

        val result = root.getAsJsonObject("result")
        val problems = result.getAsJsonArray("problems")
        val stats = result.getAsJsonArray("problemStatistics")

        // solvedCount 맵 생성
        val solvedMap = mutableMapOf<String, Int>()
        for (s in stats) {
            val obj = s.asJsonObject
            val key = "${obj.get("contestId").asInt}${obj.get("index").asString}"
            solvedMap[key] = obj.get("solvedCount")?.asInt ?: 0
        }

        val list = problems.mapNotNull { p ->
            val obj = p.asJsonObject
            val contestId = obj.get("contestId")?.asInt ?: return@mapNotNull null
            val index = obj.get("index")?.asString ?: return@mapNotNull null
            val name = obj.get("name")?.asString ?: ""
            val rating = obj.get("rating")?.asInt ?: 0
            val tags = obj.getAsJsonArray("tags")?.map { it.asString } ?: emptyList()
            ProblemInfo(contestId, index, name, rating, tags, solvedMap["$contestId$index"] ?: 0)
        }

        cachedProblems = list
        lastFetchTime = now
        return list
    }

    fun searchProblems(
        query: String = "",
        tags: List<String> = emptyList(),
        ratingMin: Int = 0,
        ratingMax: Int = 4000,
        limit: Int = 100,
        minSolved: Int = 0
    ): List<ProblemInfo> {
        val all = fetchAllProblems()
        val queryLower = query.lowercase()

        return all.filter { p ->
            val matchesQuery = query.isBlank() ||
                    p.name.lowercase().contains(queryLower) ||
                    p.id.contains(query)
            val matchesTags = tags.isEmpty() || tags.all { tag -> p.tags.contains(tag) }
            val matchesRating = (p.rating == 0 && ratingMin == 0) ||
                    (p.rating in ratingMin..ratingMax)
            val matchesSolved = p.solvedCount >= minSolved
            matchesQuery && matchesTags && matchesRating && matchesSolved
        }.take(limit)
    }

    fun randomProblems(
        tags: List<String> = emptyList(),
        ratingMin: Int = 800,
        ratingMax: Int = 3500,
        count: Int = 5,
        minSolved: Int = 0
    ): List<ProblemInfo> {
        val filtered = fetchAllProblems().filter { p ->
            val matchesTags = tags.isEmpty() || tags.all { tag -> p.tags.contains(tag) }
            val matchesRating = p.rating in ratingMin..ratingMax
            val matchesSolved = p.solvedCount >= minSolved
            matchesTags && matchesRating && matchesSolved
        }
        return filtered.shuffled().take(count)
    }

    // ─── 유저 풀이 기록 ───

    private var solvedCache: Pair<String, Set<String>>? = null
    private var solvedCacheTime = 0L

    fun fetchSolvedIds(handle: String): Set<String> {
        val now = System.currentTimeMillis()
        val cached = solvedCache
        if (cached != null && cached.first == handle && now - solvedCacheTime < 300_000) return cached.second

        val json = Jsoup.connect("https://codeforces.com/api/user.status?handle=$handle")
            .ignoreContentType(true)
            .userAgent("CodingTestKit-Plugin")
            .maxBodySize(0)
            .timeout(15000)
            .execute()
            .body()

        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("status")?.asString != "OK") throw Exception("Codeforces API error")

        val ids = mutableSetOf<String>()
        for (sub in root.getAsJsonArray("result")) {
            val obj = sub.asJsonObject
            if (obj.get("verdict")?.asString != "OK") continue
            val problem = obj.getAsJsonObject("problem") ?: continue
            val contestId = problem.get("contestId")?.asInt ?: continue
            val index = problem.get("index")?.asString ?: continue
            ids.add("$contestId$index")
        }

        solvedCache = handle to ids
        solvedCacheTime = now
        return ids
    }

    val commonTags = listOf(
        "implementation", "math", "greedy", "dp", "data structures",
        "brute force", "constructive algorithms", "graphs", "sortings",
        "binary search", "dfs and similar", "trees", "strings",
        "number theory", "geometry", "combinatorics", "two pointers",
        "bitmasks", "dsu", "shortest paths"
    )
}
