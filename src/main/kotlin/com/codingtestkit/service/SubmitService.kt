package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.codingtestkit.model.ProblemSource
import org.jsoup.Connection
import org.jsoup.Jsoup

object SubmitService {

    data class SubmitResult(
        val success: Boolean,
        val message: String,
        val submissionUrl: String = ""
    )

    fun submit(
        source: ProblemSource,
        problemId: String,
        code: String,
        language: Language,
        cookies: String
    ): SubmitResult {
        if (cookies.isBlank()) {
            return SubmitResult(false, I18n.t("로그인이 필요합니다.", "Login required."))
        }

        return when (source) {
            ProblemSource.BAEKJOON -> submitBaekjoon(problemId, code, language, cookies)
            ProblemSource.PROGRAMMERS -> submitProgrammers(problemId, code, language, cookies)
            ProblemSource.SWEA -> submitSwea(problemId, code, language, cookies)
            ProblemSource.LEETCODE -> SubmitResult(false, I18n.t("LeetCode 제출은 브라우저에서 직접 해주세요.", "Please submit on LeetCode website."))
            ProblemSource.CODEFORCES -> SubmitResult(false, I18n.t("Codeforces 제출은 브라우저에서 직접 해주세요.", "Please submit on Codeforces website."))
        }
    }

    // ─── 백준 제출 ───

    private val UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private fun parseCookieMap(cookies: String): Map<String, String> {
        return cookies.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate {
                val idx = it.indexOf("=")
                it.substring(0, idx).trim() to it.substring(idx + 1).trim()
            }
    }

    private fun submitBaekjoon(
        problemId: String,
        code: String,
        language: Language,
        cookies: String
    ): SubmitResult {
        try {
            val submitPageUrl = "https://www.acmicpc.net/submit/$problemId"
            val cookieMap = parseCookieMap(cookies)

            // 1. 제출 페이지에서 CSRF 키 가져오기
            val getResponse = Jsoup.connect(submitPageUrl)
                .userAgent(UA)
                .cookies(cookieMap)
                .header("Referer", "https://www.acmicpc.net/problem/$problemId")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                .followRedirects(true)
                .timeout(10000)
                .execute()

            val submitPage = getResponse.parse()
            val finalUrl = getResponse.url().toString()

            // 로그인 페이지로 리다이렉트된 경우
            if (finalUrl.contains("/login") || finalUrl.contains("/signin")) {
                return SubmitResult(false, I18n.t("로그인 세션이 만료되었습니다. 다시 로그인해주세요.", "Login session expired. Please log in again."))
            }

            // CSRF 키 찾기
            var csrfKey = ""

            // 1. hidden input에서 찾기 (submit_form 우선)
            val submitForm = submitPage.select("form#submit_form").first()
            if (submitForm != null) {
                csrfKey = submitForm.select("input[name=csrf_key]").attr("value")
            }
            if (csrfKey.isBlank()) csrfKey = submitPage.select("input[name=csrf_key]").attr("value")

            // 2. JavaScript 변수에서 찾기 (백준은 JS로 csrf_key를 동적 삽입)
            if (csrfKey.isBlank()) {
                val scripts = submitPage.select("script")
                for (script in scripts) {
                    val scriptData = script.data()
                    // csrf_key = "..." 또는 csrf_key: "..." 패턴 매칭
                    val patterns = listOf(
                        Regex("""csrf_key\s*[=:]\s*["']([^"']+)["']"""),
                        Regex("""csrfKey\s*[=:]\s*["']([^"']+)["']"""),
                        Regex("""csrf[_-]?token\s*[=:]\s*["']([^"']+)["']"""),
                        Regex("""key\s*[=:]\s*["']([a-f0-9]{20,})["']""")
                    )
                    for (pattern in patterns) {
                        val match = pattern.find(scriptData)
                        if (match != null) {
                            csrfKey = match.groupValues[1]
                            break
                        }
                    }
                    if (csrfKey.isNotBlank()) break
                }
            }

            // 3. meta 태그에서 찾기
            if (csrfKey.isBlank()) csrfKey = submitPage.select("meta[name=csrf-token]").attr("content")
            if (csrfKey.isBlank()) csrfKey = submitPage.select("meta[name=csrf_token]").attr("content")

            // 응답에서 추가 쿠키 병합
            val mergedCookies = cookieMap.toMutableMap()
            mergedCookies.putAll(getResponse.cookies())

            // 2. 코드 제출
            val conn = Jsoup.connect(submitPageUrl)
                .userAgent(UA)
                .cookies(mergedCookies)
                .header("Referer", submitPageUrl)
                .header("Origin", "https://www.acmicpc.net")
                .data("problem_id", problemId)
                .data("language", language.baekjoonId.toString())
                .data("code_open", "open")
                .data("source", code)
                .method(Connection.Method.POST)
                .followRedirects(true)
                .timeout(10000)

            // CSRF 키가 있으면 포함
            if (csrfKey.isNotBlank()) {
                conn.data("csrf_key", csrfKey)
            }

            val response = conn.execute()

            val resultUrl = response.url().toString()
            return if (resultUrl.contains("status") || response.statusCode() in 200..399) {
                SubmitResult(true, I18n.t("제출 완료!\n결과: $resultUrl", "Submitted!\nResult: $resultUrl"), resultUrl)
            } else {
                SubmitResult(false, I18n.t("제출 실패", "Submit failed") + ": HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            return SubmitResult(false, I18n.t("제출 오류", "Submit error") + ": ${e.message}")
        }
    }

    // ─── 프로그래머스 제출 ───

    private val programmersLangMap = mapOf(
        Language.JAVA to "java",
        Language.PYTHON to "python3",
        Language.CPP to "cpp",
        Language.KOTLIN to "kotlin",
        Language.JAVASCRIPT to "javascript"
    )

    private fun submitProgrammers(
        lessonId: String,
        code: String,
        language: Language,
        cookies: String
    ): SubmitResult {
        try {
            val langCode = programmersLangMap[language]
                ?: return SubmitResult(false, I18n.t(
                    "프로그래머스에서 ${language.displayName}을 지원하지 않습니다.",
                    "${language.displayName} is not supported on Programmers."
                ))

            // 프로그래머스 제출 API
            val submitUrl = "https://school.programmers.co.kr/learn/courses/30/lessons/$lessonId/submit"

            val response = Jsoup.connect(submitUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json")
                .data("code", code)
                .data("language", langCode)
                .method(Connection.Method.POST)
                .followRedirects(true)
                .ignoreContentType(true)
                .execute()

            return if (response.statusCode() in 200..399) {
                SubmitResult(true, I18n.t(
                    "프로그래머스 제출 완료!\n채점 결과는 사이트에서 확인하세요.",
                    "Submitted to Programmers!\nCheck the results on the website."
                ), "https://school.programmers.co.kr/learn/courses/30/lessons/$lessonId")
            } else {
                SubmitResult(false, I18n.t("제출 실패", "Submit failed") + ": HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            return SubmitResult(false, I18n.t("프로그래머스 제출 오류", "Programmers submit error") + ": ${e.message}")
        }
    }

    // ─── SWEA 제출 ───

    private fun submitSwea(
        problemId: String,
        code: String,
        language: Language,
        cookies: String
    ): SubmitResult {
        if (language.sweaId < 0) {
            return SubmitResult(false, I18n.t(
                "SWEA에서 ${language.displayName}은 지원하지 않습니다.",
                "${language.displayName} is not supported on SWEA."
            ))
        }

        try {
            val submitUrl = "https://swexpertacademy.com/main/code/problem/problemSubmit.do"

            val response = Jsoup.connect(submitUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .data("contestProbId", problemId)
                .data("language", language.sweaId.toString())
                .data("source", code)
                .method(Connection.Method.POST)
                .followRedirects(true)
                .ignoreContentType(true)
                .execute()

            return if (response.statusCode() in 200..399) {
                SubmitResult(true, I18n.t("SWEA 제출 완료!", "Submitted to SWEA!"))
            } else {
                SubmitResult(false, I18n.t("제출 실패", "Submit failed") + ": HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            return SubmitResult(false, I18n.t("SWEA 제출 오류", "SWEA submit error") + ": ${e.message}")
        }
    }
}
