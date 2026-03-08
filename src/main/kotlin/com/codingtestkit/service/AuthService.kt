package com.codingtestkit.service

import com.codingtestkit.model.ProblemSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jsoup.Jsoup

@Service
@State(name = "CodingTestKitAuth", storages = [Storage("codingtestkit-auth.xml")])
class AuthService : PersistentStateComponent<AuthService.AuthState> {

    data class AuthState(
        var baekjoonCookies: String = "",
        var programmersCookies: String = "",
        var swexpertCookies: String = "",
        var leetcodeCookies: String = "",
        var baekjoonUsername: String = "",
        var programmersUsername: String = "",
        var swexpertUsername: String = "",
        var leetcodeUsername: String = ""
    )

    private var authState = AuthState()

    override fun getState(): AuthState = authState
    override fun loadState(state: AuthState) { authState = state }

    fun getCookies(source: ProblemSource): String = when (source) {
        ProblemSource.BAEKJOON -> authState.baekjoonCookies
        ProblemSource.PROGRAMMERS -> authState.programmersCookies
        ProblemSource.SWEA -> authState.swexpertCookies
        ProblemSource.LEETCODE -> authState.leetcodeCookies
    }

    fun setCookies(source: ProblemSource, cookies: String) {
        when (source) {
            ProblemSource.BAEKJOON -> authState.baekjoonCookies = cookies
            ProblemSource.PROGRAMMERS -> authState.programmersCookies = cookies
            ProblemSource.SWEA -> authState.swexpertCookies = cookies
            ProblemSource.LEETCODE -> authState.leetcodeCookies = cookies
        }
    }

    fun getUsername(source: ProblemSource): String = when (source) {
        ProblemSource.BAEKJOON -> authState.baekjoonUsername
        ProblemSource.PROGRAMMERS -> authState.programmersUsername
        ProblemSource.SWEA -> authState.swexpertUsername
        ProblemSource.LEETCODE -> authState.leetcodeUsername
    }

    fun setUsername(source: ProblemSource, username: String) {
        when (source) {
            ProblemSource.BAEKJOON -> authState.baekjoonUsername = username
            ProblemSource.PROGRAMMERS -> authState.programmersUsername = username
            ProblemSource.SWEA -> authState.swexpertUsername = username
            ProblemSource.LEETCODE -> authState.leetcodeUsername = username
        }
    }

    fun isLoggedIn(source: ProblemSource): Boolean = getCookies(source).isNotBlank()

    fun logout(source: ProblemSource) {
        setCookies(source, "")
        setUsername(source, "")
    }

    /**
     * 쿠키로 사이트에 접속해서 유저네임 가져오기
     */
    fun fetchUsername(source: ProblemSource): String {
        val cookies = getCookies(source)
        if (cookies.isBlank()) return ""

        return try {
            when (source) {
                ProblemSource.BAEKJOON -> fetchBaekjoonUsername(cookies)
                ProblemSource.PROGRAMMERS -> fetchProgrammersUsername(cookies)
                ProblemSource.SWEA -> fetchSwexpertUsername(cookies)
                ProblemSource.LEETCODE -> fetchLeetCodeUsername(cookies)
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun fetchBaekjoonUsername(cookies: String): String {
        val doc = Jsoup.connect("https://www.acmicpc.net/")
            .userAgent("Mozilla/5.0")
            .header("Cookie", cookies)
            .timeout(5000)
            .get()

        // 백준 상단 네비게이션에서 유저네임 추출
        val username = doc.select("a.username").text().trim()
            .ifBlank { doc.select(".loginbar a[href^=/user/]").text().trim() }
        return username
    }

    private fun fetchProgrammersUsername(cookies: String): String {
        val doc = Jsoup.connect("https://school.programmers.co.kr/")
            .userAgent("Mozilla/5.0")
            .header("Cookie", cookies)
            .timeout(5000)
            .get()

        return doc.select(".header-user-name, .user-name, .nav-user-name").text().trim()
            .ifBlank { doc.select("[class*=user] [class*=name]").text().trim() }
    }

    private fun fetchSwexpertUsername(cookies: String): String {
        val doc = Jsoup.connect("https://swexpertacademy.com/main/main.do")
            .userAgent("Mozilla/5.0")
            .header("Cookie", cookies)
            .timeout(5000)
            .get()

        return doc.select(".user_name, .member_name, #userName").text().trim()
    }

    private fun fetchLeetCodeUsername(cookies: String): String {
        val json = Jsoup.connect("https://leetcode.com/graphql")
            .method(org.jsoup.Connection.Method.POST)
            .header("Content-Type", "application/json")
            .header("Cookie", cookies)
            .header("Referer", "https://leetcode.com")
            .userAgent("Mozilla/5.0")
            .requestBody("""{"query":"{ userStatus { username } }"}""")
            .ignoreContentType(true)
            .timeout(5000)
            .execute()
            .body()
        val root = com.google.gson.JsonParser.parseString(json).asJsonObject
        return root.getAsJsonObject("data")
            ?.getAsJsonObject("userStatus")
            ?.get("username")?.asString ?: ""
    }

    fun getLoginUrl(source: ProblemSource): String = when (source) {
        ProblemSource.BAEKJOON -> "https://www.acmicpc.net/login"
        ProblemSource.PROGRAMMERS -> "https://programmers.co.kr/account/sign_in?referer=https://school.programmers.co.kr/"
        ProblemSource.SWEA -> "https://swexpertacademy.com/main/identity/anonymous/loginPage.do"
        ProblemSource.LEETCODE -> "https://leetcode.com/accounts/login/"
    }

    fun getCookieHelpText(source: ProblemSource): String = when (source) {
        ProblemSource.BAEKJOON -> "acmicpc.net에서 로그인 후 쿠키를 복사하세요.\n필요한 쿠키: OnlineJudge (세션 쿠키)"
        ProblemSource.PROGRAMMERS -> "school.programmers.co.kr에서 로그인 후 쿠키를 복사하세요.\n필요한 쿠키: _programmers_session_production"
        ProblemSource.SWEA -> "swexpertacademy.com에서 로그인 후 쿠키를 복사하세요."
        ProblemSource.LEETCODE -> "leetcode.com에서 로그인 후 쿠키를 복사하세요.\n필요한 쿠키: LEETCODE_SESSION, csrftoken"
    }

    companion object {
        fun getInstance(): AuthService =
            ApplicationManager.getApplication().getService(AuthService::class.java)
    }
}
