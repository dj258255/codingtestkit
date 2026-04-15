package com.codingtestkit.service

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

object ProgrammersApi {

    private const val API_BASE = "https://school.programmers.co.kr/api"
    private val gson = Gson()

    data class ProblemInfo(
        val id: String,
        val title: String,
        val level: Int,
        val partTitle: String,
        val finishedCount: Int,
        val acceptanceRate: Int,
        val status: String
    ) {
        val levelDisplay: String get() = if (level >= 0) "Lv. $level" else "Unrated"
        val finishedCountDisplay: String get() = "%,d명".format(finishedCount)
    }

    data class SearchResult(
        val problems: List<ProblemInfo>,
        val totalCount: Int,
        val totalPages: Int
    )

    /**
     * 프로그래머스 문제 목록 검색 (JSON API)
     */
    fun searchProblems(
        keyword: String = "",
        levels: List<Int> = emptyList(),
        languages: List<String> = emptyList(),
        statuses: List<String> = emptyList(),
        partIds: List<String> = emptyList(),
        order: String = "acceptance_desc",
        page: Int = 1,
        perPage: Int = 20,
        cookies: String = ""
    ): SearchResult {
        val url = buildApiUrl(keyword, levels, languages, statuses, partIds, order, page, perPage)
        val json = fetchJson(url, cookies)
        return parseApiResponse(json)
    }

    /**
     * 조건에 맞는 모든 문제 가져오기 (자동 전체 페이지 탐색, perPage=200)
     */
    fun fetchAllProblems(
        levels: List<Int> = emptyList(),
        languages: List<String> = emptyList(),
        statuses: List<String> = emptyList(),
        partIds: List<String> = emptyList(),
        cookies: String = ""
    ): List<ProblemInfo> {
        val allProblems = mutableListOf<ProblemInfo>()
        val first = searchProblems(
            levels = levels, languages = languages, statuses = statuses,
            partIds = partIds, page = 1, perPage = 200, cookies = cookies
        )
        allProblems.addAll(first.problems)

        for (page in 2..first.totalPages) {
            val result = searchProblems(
                levels = levels, languages = languages, statuses = statuses,
                partIds = partIds, page = page, perPage = 200, cookies = cookies
            )
            allProblems.addAll(result.problems)
        }
        return allProblems
    }

    /**
     * 랜덤 문제 뽑기 (전체 문제 탐색 후 셔플)
     */
    fun randomProblems(
        levels: List<Int> = emptyList(),
        languages: List<String> = emptyList(),
        statuses: List<String> = emptyList(),
        partIds: List<String> = emptyList(),
        count: Int = 5,
        cookies: String = ""
    ): List<ProblemInfo> {
        val all = fetchAllProblems(
            levels = levels, languages = languages,
            statuses = statuses, partIds = partIds, cookies = cookies
        )
        return all.shuffled().take(count)
    }

    private fun buildApiUrl(
        keyword: String,
        levels: List<Int>,
        languages: List<String>,
        statuses: List<String>,
        partIds: List<String>,
        order: String,
        page: Int,
        perPage: Int
    ): String {
        val params = mutableListOf<String>()
        params.add("perPage=$perPage")
        params.add("order=$order")
        params.add("page=$page")
        if (keyword.isNotBlank()) params.add("search=${URLEncoder.encode(keyword, "UTF-8")}")
        for (level in levels) params.add("levels%5B%5D=$level")
        for (lang in languages) params.add("languages%5B%5D=$lang")
        for (status in statuses) params.add("statuses%5B%5D=$status")
        for (partId in partIds) params.add("partIds%5B%5D=$partId")
        return "$API_BASE/v2/school/challenges/?${params.joinToString("&")}"
    }

    private fun fetchJson(url: String, cookies: String): String {
        val conn = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            setRequestProperty("Referer", "https://school.programmers.co.kr/learn/challenges")
            if (cookies.isNotBlank()) setRequestProperty("Cookie", cookies)
            connectTimeout = 15000
            readTimeout = 15000
        }
        if (conn.responseCode != 200) {
            throw RuntimeException("HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseApiResponse(json: String): SearchResult {
        val obj = gson.fromJson(json, JsonObject::class.java)
        val totalEntries = obj.get("totalEntries")?.asInt ?: 0
        val totalPages = obj.get("totalPages")?.asInt ?: 1
        val resultArray = obj.getAsJsonArray("result") ?: JsonArray()

        val problems = resultArray.map { elem ->
            val o = elem.asJsonObject
            ProblemInfo(
                id = o.get("id")?.asString ?: "",
                title = o.get("title")?.asString?.trim() ?: "",
                level = o.get("level")?.asInt ?: -1,
                partTitle = o.get("partTitle")?.asString ?: "",
                finishedCount = o.get("finishedCount")?.asInt ?: 0,
                acceptanceRate = o.get("acceptanceRate")?.asInt ?: 0,
                status = o.get("status")?.asString ?: "unknown"
            )
        }

        return SearchResult(problems, totalEntries, totalPages)
    }

    // ─── 기출문제 모음 (JSON API + 캐시) ───

    @Volatile private var cachedExamCollections: List<Pair<String, String>>? = null
    private var examCacheTime = 0L

    /**
     * 기출문제 모음 목록 (JSON API, 10분 캐시)
     */
    fun fetchExamCollections(): List<Pair<String, String>> {
        val now = System.currentTimeMillis()
        val cached = cachedExamCollections
        if (cached != null && now - examCacheTime < 600_000) return cached

        return try {
            val json = fetchJson("$API_BASE/v1/school/challenges/parts/", "")
            val parts = gson.fromJson(json, JsonArray::class.java)
            val collections = parts.map { elem ->
                val obj = elem.asJsonObject
                obj.get("id").asString to obj.get("title").asString
            }.filter { it.first.isNotBlank() && it.second.isNotBlank() }

            if (collections.isNotEmpty()) {
                cachedExamCollections = collections
                examCacheTime = now
            }
            collections
        } catch (_: Exception) {
            emptyList()
        }
    }

    val supportedLanguages = listOf(
        "java" to "Java",
        "python3" to "Python3",
        "cpp" to "C++",
        "javascript" to "JavaScript",
        "kotlin" to "Kotlin"
    )
}
