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
            ProblemSource.PROGRAMMERS -> submitProgrammers(problemId, code, language, cookies)
            ProblemSource.SWEA -> submitSwea(problemId, code, language, cookies)
            ProblemSource.LEETCODE -> SubmitResult(false, I18n.t("LeetCode 제출은 브라우저에서 직접 해주세요.", "Please submit on LeetCode website."))
            ProblemSource.CODEFORCES -> SubmitResult(false, I18n.t("Codeforces 제출은 브라우저에서 직접 해주세요.", "Please submit on Codeforces website."))
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
