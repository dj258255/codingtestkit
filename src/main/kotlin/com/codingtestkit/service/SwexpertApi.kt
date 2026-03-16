package com.codingtestkit.service

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object SwexpertApi {

    private const val BASE_URL = "https://swexpertacademy.com/main/code/problem/problemList.do"

    data class ProblemInfo(
        val contestProbId: String,
        val number: String,
        val title: String,
        val difficulty: String,
        val participants: Int,
        val submissions: Int,
        val solveRate: String,
        val recommendations: Int,
        val solved: Boolean = false
    ) {
        val difficultyDisplay: String get() = difficulty
        val participantsDisplay: String get() = "%,d명".format(participants)
    }

    data class SearchResult(
        val problems: List<ProblemInfo>,
        val totalPages: Int
    )

    /**
     * SWEA 문제 목록 검색 (HTML 스크래핑)
     */
    fun searchProblems(
        keyword: String = "",
        levels: List<Int> = emptyList(),
        language: String = "ALL",
        orderBy: String = "INQUERY_COUNT",
        passFilterYn: Boolean = false,
        page: Int = 1,
        pageSize: Int = 30,
        cookies: String = ""
    ): SearchResult {
        val doc = fetchPage(keyword, levels, language, orderBy, passFilterYn, page, pageSize, cookies)
        return parsePage(doc)
    }

    /**
     * 조건에 맞는 모든 문제 가져오기 (전체 페이지 탐색, 최대 50페이지)
     */
    fun fetchAllProblems(
        keyword: String = "",
        levels: List<Int> = emptyList(),
        language: String = "ALL",
        orderBy: String = "INQUERY_COUNT",
        passFilterYn: Boolean = false,
        cookies: String = ""
    ): List<ProblemInfo> {
        val allProblems = mutableListOf<ProblemInfo>()
        val first = searchProblems(keyword, levels, language, orderBy, passFilterYn, page = 1, pageSize = 30, cookies = cookies)
        allProblems.addAll(first.problems)

        val maxPages = first.totalPages.coerceAtMost(50)
        for (page in 2..maxPages) {
            val result = searchProblems(keyword, levels, language, orderBy, passFilterYn, page = page, pageSize = 30, cookies = cookies)
            allProblems.addAll(result.problems)
        }
        return allProblems
    }

    /**
     * 랜덤 문제 뽑기 (전체 문제 탐색 후 셔플)
     *
     * @param solvedFilter 0=전체, 1=내가 푼 문제 제외, 2=내가 푼 문제만
     */
    fun randomProblems(
        levels: List<Int> = emptyList(),
        language: String = "ALL",
        count: Int = 5,
        minParticipants: Int = 0,
        solvedFilter: Int = 0,
        cookies: String = ""
    ): List<ProblemInfo> {
        val passFilter = solvedFilter == 2

        val all = when {
            // 난이도 미선택: D1~D8 각각 1페이지씩 조회 (8 req ≈ 240후보, 전체 50페이지 대신)
            levels.isEmpty() -> fetchPerLevel(language, passFilter, cookies)
            // 푼 문제만: passFilterYn=Y 서버 필터
            passFilter -> {
                if (minParticipants > 0)
                    fetchWithEarlyStop(levels, language, minParticipants, passFilterYn = true, cookies = cookies)
                else
                    fetchAllProblems(levels = levels, language = language, passFilterYn = true, cookies = cookies)
            }
            // 최소 참여자: 참여자순 + 조기 종료
            minParticipants > 0 -> fetchWithEarlyStop(levels, language, minParticipants, passFilterYn = false, cookies = cookies)
            // 그 외: 전체 탐색
            else -> fetchAllProblems(levels = levels, language = language, cookies = cookies)
        }

        val problems = if (solvedFilter == 1) all.filter { !it.solved } else all
        val filtered = if (minParticipants > 0) problems.filter { it.participants >= minParticipants } else problems
        return filtered.shuffled().take(count)
    }

    /**
     * D1~D8 각각 1페이지(30개)씩 조회 후 합치기 (8 requests ≈ 240 candidates)
     * 난이도 미선택 시 전체 탐색(최대 50페이지) 대신 사용 → 훨씬 빠름
     */
    private fun fetchPerLevel(
        language: String,
        passFilterYn: Boolean,
        cookies: String
    ): List<ProblemInfo> {
        // D1~D8 병렬 조회 (8 req 순차 ~8초 → 병렬 ~1-2초)
        val futures = (1..8).map { d ->
            java.util.concurrent.CompletableFuture.supplyAsync {
                try {
                    searchProblems(
                        levels = listOf(d), language = language,
                        orderBy = "INQUERY_COUNT",
                        passFilterYn = passFilterYn,
                        page = 1, pageSize = 30, cookies = cookies
                    ).problems
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        return futures.flatMap { it.join() }
    }

    /**
     * 참여자순 정렬 + 최소 참여자 미만이면 조기 종료
     * 참여자순 내림차순이므로, 페이지 마지막 문제가 기준 미만이면 이후 페이지는 전부 미만
     */
    private fun fetchWithEarlyStop(
        levels: List<Int>,
        language: String,
        minParticipants: Int,
        passFilterYn: Boolean,
        cookies: String
    ): List<ProblemInfo> {
        val allProblems = mutableListOf<ProblemInfo>()
        var page = 1

        while (page <= 50) {
            val result = searchProblems(
                levels = levels, language = language,
                orderBy = "INQUERY_COUNT",  // 참여자순 내림차순
                passFilterYn = passFilterYn,
                page = page, pageSize = 30, cookies = cookies
            )
            val pageProblems = result.problems
            if (pageProblems.isEmpty()) break

            allProblems.addAll(pageProblems)

            // 이 페이지의 마지막 문제가 최소 참여자 미만 → 이후 페이지는 전부 미만이므로 중단
            if (pageProblems.last().participants < minParticipants) break
            if (page >= result.totalPages) break

            page++
        }
        return allProblems
    }

    private fun fetchPage(
        keyword: String,
        levels: List<Int>,
        language: String,
        orderBy: String,
        passFilterYn: Boolean,
        page: Int,
        pageSize: Int,
        cookies: String
    ): Document {
        val conn = Jsoup.connect(BASE_URL)
            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .timeout(15000)
            .data("problemTitle", keyword)
            .data("orderBy", orderBy)
            .data("selectCodeLang", language)
            .data("pageSize", pageSize.toString())
            .data("pageIndex", page.toString())
            .data("categoryType", "CODE")

        if (cookies.isNotBlank()) {
            conn.header("Cookie", cookies)
        }
        if (passFilterYn) {
            conn.data("passFilterYn", "Y")
        }
        for (level in levels) {
            conn.data("problemLevel", level.toString())
        }

        return conn.get()
    }

    private val commentCountRegex = Regex("\\s*\\[\\d+]\\s*$")

    private fun cleanTitle(raw: String): String {
        var title = raw.trim()
        // 상태 배지 (정답, 오답 등) 제거
        title = title.replace(Regex("\\s*(정답|오답|New)\\s*$"), "").trim()
        // 댓글 수 [N] 제거
        title = commentCountRegex.replace(title, "").trim()
        // 다시 한번 (순서 관계없이 처리)
        title = title.replace(Regex("\\s*(정답|오답|New)\\s*$"), "").trim()
        return title
    }

    /**
     * SWEA 숫자 파싱: "36K" → 36000, "1.2K" → 1200, "205" → 205
     */
    private fun parseCount(text: String): Int {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.endsWith("K", ignoreCase = true)) {
            val num = cleaned.dropLast(1).toDoubleOrNull() ?: return 0
            return (num * 1000).toInt()
        }
        if (cleaned.endsWith("M", ignoreCase = true)) {
            val num = cleaned.dropLast(1).toDoubleOrNull() ?: return 0
            return (num * 1_000_000).toInt()
        }
        return cleaned.toIntOrNull() ?: 0
    }

    private fun parsePage(doc: Document): SearchResult {
        val problems = mutableListOf<ProblemInfo>()

        // .problem-list 내의 widget만 선택 (사이드바 "추천 Problem" 제외)
        val widgets = doc.select(".problem-list .widget-box-sub")
        for (widget in widgets) {
            try {
                val onclick = widget.select("a[onclick*=fn_move_page]").firstOrNull()
                    ?.attr("onclick") ?: continue
                val idMatch = Regex("fn_move_page\\('([^']+)'\\)").find(onclick) ?: continue
                val contestProbId = idMatch.groupValues[1]

                val number = widget.select("span.week_num").text().trim().removeSuffix(".")
                // ownText()로 자식 요소(배지 등) 텍스트 제외
                val rawTitle = widget.select("span.week_text a").firstOrNull()?.ownText()?.trim() ?: ""
                val title = cleanTitle(rawTitle)
                if (title.isBlank()) continue

                // badge 개별 파싱: D1~D8은 난이도, "정답"은 풀이 상태
                val badges = widget.select("span.badge").map { it.text().trim() }
                val difficulty = badges.firstOrNull { it.matches(Regex("D\\d+")) } ?: ""
                val solved = badges.any { it == "정답" }

                val statValues = widget.select("span.code-sub-mum").map { it.text().trim() }
                val participants = parseCount(statValues.getOrNull(0) ?: "0")
                val submissions = parseCount(statValues.getOrNull(1) ?: "0")
                val solveRate = statValues.getOrNull(2) ?: "0%"
                val recommendations = parseCount(statValues.getOrNull(3) ?: "0")

                problems.add(
                    ProblemInfo(
                        contestProbId = contestProbId,
                        number = number,
                        title = title,
                        difficulty = difficulty,
                        participants = participants,
                        submissions = submissions,
                        solveRate = solveRate,
                        recommendations = recommendations,
                        solved = solved
                    )
                )
            } catch (_: Exception) {
                // 파싱 실패한 항목은 건너뜀
            }
        }

        val totalPages = doc.select("a.page-link[href*=pageIndex]")
            .mapNotNull {
                Regex("pageIndex\\.value=(\\d+)").find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
            }
            .maxOrNull() ?: 1

        return SearchResult(problems, totalPages)
    }

    val supportedLanguages = listOf(
        "ALL" to I18n.t("전체", "All"),
        "CCPP" to "C/C++",
        "JAVA" to "Java",
        "PYTHON" to "Python"
    )

    val sortOptions = listOf(
        "INQUERY_COUNT" to I18n.t("참여자순", "By Participants"),
        "FIRST_REG_DATETIME" to I18n.t("등록순", "By Date"),
        "SUBMIT_COUNT" to I18n.t("제출순", "By Submissions"),
        "PASS_RATE" to I18n.t("정답률순", "By Solve Rate"),
        "RECOMMEND_COUNT" to I18n.t("추천순", "By Recommendations")
    )

    val difficultyLevels = (1..8).map { it to "D$it" }
}
