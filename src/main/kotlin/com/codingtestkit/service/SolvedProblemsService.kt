package com.codingtestkit.service

import com.codingtestkit.model.ProblemSource
import com.google.gson.JsonParser
import org.jsoup.Jsoup

object SolvedProblemsService {

    data class SolvedProblem(
        val id: String,
        val title: String,
        val difficulty: String,
        val tags: List<String> = emptyList(),
        val solvedDate: String = ""
    )

    data class SolvedPage(
        val problems: List<SolvedProblem>,
        val totalCount: Int,
        val currentPage: Int,
        val totalPages: Int
    )

    private const val PAGE_SIZE = 50

    fun fetch(source: ProblemSource, username: String, page: Int = 1, query: String = ""): SolvedPage {
        return when (source) {
            ProblemSource.CODEFORCES -> fetchCodeforces(username, page, query)
            ProblemSource.LEETCODE -> fetchLeetCode(username, page, query)
            else -> SolvedPage(emptyList(), 0, 1, 0)
        }
    }

    fun isSupported(source: ProblemSource): Boolean =
        source == ProblemSource.CODEFORCES || source == ProblemSource.LEETCODE

    // ─── Codeforces ───

    private var cfCache: Pair<String, List<SolvedProblem>>? = null
    private var cfCacheTime = 0L

    private fun fetchCodeforces(handle: String, page: Int, query: String): SolvedPage {
        val now = System.currentTimeMillis()
        val cached = cfCache

        val allSolved = if (cached != null && cached.first == handle && now - cfCacheTime < 300_000) {
            cached.second
        } else {
            val json = Jsoup.connect("https://codeforces.com/api/user.status?handle=$handle")
                .ignoreContentType(true)
                .userAgent("CodingTestKit-Plugin")
                .timeout(15000)
                .get()
                .body()
                .text()

            val root = JsonParser.parseString(json).asJsonObject
            if (root.get("status")?.asString != "OK") throw Exception("Codeforces API error")

            val submissions = root.getAsJsonArray("result")
            val seen = mutableSetOf<String>()
            val list = mutableListOf<SolvedProblem>()

            for (sub in submissions) {
                val obj = sub.asJsonObject
                if (obj.get("verdict")?.asString != "OK") continue

                val problem = obj.getAsJsonObject("problem") ?: continue
                val contestId = problem.get("contestId")?.asInt ?: continue
                val index = problem.get("index")?.asString ?: continue
                val key = "$contestId$index"
                if (!seen.add(key)) continue

                val name = problem.get("name")?.asString ?: ""
                val rating = problem.get("rating")?.asInt ?: 0
                val tags = problem.getAsJsonArray("tags")?.map { it.asString } ?: emptyList()
                val time = obj.get("creationTimeSeconds")?.asLong ?: 0
                val date = if (time > 0) {
                    java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(time * 1000))
                } else ""

                list.add(SolvedProblem(
                    id = key,
                    title = name,
                    difficulty = if (rating > 0) "*$rating" else "Unrated",
                    tags = tags,
                    solvedDate = date
                ))
            }
            cfCache = handle to list
            cfCacheTime = now
            list
        }

        val filtered = if (query.isBlank()) allSolved else {
            val q = query.lowercase()
            allSolved.filter { p ->
                p.title.lowercase().contains(q) || p.id.contains(q)
            }
        }

        val totalCount = filtered.size
        val totalPages = if (totalCount > 0) (totalCount + PAGE_SIZE - 1) / PAGE_SIZE else 0
        val start = (page - 1) * PAGE_SIZE
        val pageItems = filtered.drop(start).take(PAGE_SIZE)

        return SolvedPage(pageItems, totalCount, page, totalPages)
    }

    // ─── LeetCode ───

    @Suppress("UNUSED_PARAMETER")
    private fun fetchLeetCode(username: String, page: Int, query: String): SolvedPage {
        val cookies = AuthService.getInstance().getCookies(ProblemSource.LEETCODE).ifBlank { null }
        val skip = (page - 1) * PAGE_SIZE

        val result = LeetCodeApi.searchProblems(
            query = query,
            status = "AC",
            limit = PAGE_SIZE,
            skip = skip,
            cookies = cookies
        )

        val problems = result.problems.map { p ->
            SolvedProblem(
                id = p.titleSlug,
                title = "${p.frontendId}. ${p.title}",
                difficulty = p.difficulty,
                tags = p.tags,
                solvedDate = String.format("%.1f%%", p.acRate)
            )
        }

        val totalPages = if (result.totalCount > 0) (result.totalCount + PAGE_SIZE - 1) / PAGE_SIZE else 0
        return SolvedPage(problems, result.totalCount, page, totalPages)
    }
}
