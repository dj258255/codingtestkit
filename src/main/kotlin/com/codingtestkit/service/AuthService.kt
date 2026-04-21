package com.codingtestkit.service

import com.codingtestkit.model.ProblemSource
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jsoup.Jsoup

@Service
@State(name = "CodingTestKitAuth", storages = [Storage("codingtestkit-auth.xml")])
class AuthService : PersistentStateComponent<AuthService.AuthState> {

    /**
     * Secret cookie values are stored in IntelliJ PasswordSafe (OS keychain).
     * Legacy plaintext cookie fields remain for one-time migration from older plugin versions.
     */
    data class AuthState(
        var programmersUsername: String = "",
        var swexpertUsername: String = "",
        var leetcodeUsername: String = "",
        var codeforcesUsername: String = "",
        // Legacy plaintext cookies — migrated to PasswordSafe on first load, then cleared
        var programmersCookies: String = "",
        var swexpertCookies: String = "",
        var leetcodeCookies: String = "",
        var codeforcesCookies: String = ""
    )

    private var authState = AuthState()

    override fun getState(): AuthState = authState

    override fun loadState(state: AuthState) {
        authState = state
        migrateLegacyCookies()
    }

    private fun migrateLegacyCookies() {
        fun migrate(source: ProblemSource, legacy: String, clear: () -> Unit) {
            if (legacy.isNotBlank()) {
                writeCookies(source, legacy)
                clear()
            }
        }
        migrate(ProblemSource.PROGRAMMERS, authState.programmersCookies) { authState.programmersCookies = "" }
        migrate(ProblemSource.SWEA, authState.swexpertCookies) { authState.swexpertCookies = "" }
        migrate(ProblemSource.LEETCODE, authState.leetcodeCookies) { authState.leetcodeCookies = "" }
        migrate(ProblemSource.CODEFORCES, authState.codeforcesCookies) { authState.codeforcesCookies = "" }

        // 과거 BAEKJOON 쿠키 항목을 PasswordSafe에서 제거 (플러그인 업데이트 후 정리)
        PasswordSafe.instance.setPassword(
            CredentialAttributes(generateServiceName("CodingTestKit", "cookie.BAEKJOON")),
            null
        )
    }

    private fun credentialAttributes(source: ProblemSource): CredentialAttributes =
        CredentialAttributes(generateServiceName("CodingTestKit", "cookie.${source.name}"))

    private fun writeCookies(source: ProblemSource, cookies: String) {
        PasswordSafe.instance.setPassword(credentialAttributes(source), cookies.ifBlank { null })
    }

    private fun readCookies(source: ProblemSource): String =
        PasswordSafe.instance.getPassword(credentialAttributes(source)) ?: ""

    fun getCookies(source: ProblemSource): String = readCookies(source)

    fun setCookies(source: ProblemSource, cookies: String) = writeCookies(source, cookies)

    fun getUsername(source: ProblemSource): String = when (source) {
        ProblemSource.PROGRAMMERS -> authState.programmersUsername
        ProblemSource.SWEA -> authState.swexpertUsername
        ProblemSource.LEETCODE -> authState.leetcodeUsername
        ProblemSource.CODEFORCES -> authState.codeforcesUsername
    }

    fun setUsername(source: ProblemSource, username: String) {
        when (source) {
            ProblemSource.PROGRAMMERS -> authState.programmersUsername = username
            ProblemSource.SWEA -> authState.swexpertUsername = username
            ProblemSource.LEETCODE -> authState.leetcodeUsername = username
            ProblemSource.CODEFORCES -> authState.codeforcesUsername = username
        }
    }

    fun isLoggedIn(source: ProblemSource): Boolean = getCookies(source).isNotBlank()

    fun logout(source: ProblemSource) {
        setCookies(source, "")
        setUsername(source, "")
    }

    /**
     * 저장된 쿠키가 아직 유효한지 실제 사이트에 요청해서 확인.
     * 유효하지 않으면 자동 로그아웃하고 false 반환.
     */
    fun validateSession(source: ProblemSource): Boolean {
        if (!isLoggedIn(source)) return false
        val valid = try {
            fetchUsername(source).isNotBlank()
        } catch (_: Exception) {
            false
        }
        if (!valid) logout(source)
        return valid
    }

    /**
     * 쿠키로 사이트에 접속해서 유저네임 가져오기
     */
    fun fetchUsername(source: ProblemSource): String {
        val cookies = getCookies(source)
        if (cookies.isBlank()) return ""

        return try {
            when (source) {
                ProblemSource.PROGRAMMERS -> fetchProgrammersUsername(cookies)
                ProblemSource.SWEA -> fetchSwexpertUsername(cookies)
                ProblemSource.LEETCODE -> fetchLeetCodeUsername(cookies)
                ProblemSource.CODEFORCES -> fetchCodeforcesUsername(cookies)
            }
        } catch (_: Exception) {
            ""
        }
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
        val csrfToken = Regex("csrftoken=([^;]+)").find(cookies)?.groupValues?.get(1) ?: ""
        val json = Jsoup.connect("https://leetcode.com/graphql/")
            .method(org.jsoup.Connection.Method.POST)
            .header("Content-Type", "application/json")
            .header("Cookie", cookies)
            .header("Referer", "https://leetcode.com")
            .header("Origin", "https://leetcode.com")
            .header("x-csrftoken", csrfToken)
            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
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

    private fun fetchCodeforcesUsername(cookies: String): String {
        val doc = Jsoup.connect("https://codeforces.com/")
            .userAgent("Mozilla/5.0")
            .header("Cookie", cookies)
            .timeout(5000)
            .get()
        return doc.select("a[href^=/profile/]").first()?.text()?.trim() ?: ""
    }

    fun getLoginUrl(source: ProblemSource): String = when (source) {
        ProblemSource.PROGRAMMERS -> "https://programmers.co.kr/account/sign_in?referer=https://school.programmers.co.kr/"
        ProblemSource.SWEA -> "https://swexpertacademy.com/main/identity/anonymous/loginPage.do"
        ProblemSource.LEETCODE -> "https://leetcode.com/accounts/login/"
        ProblemSource.CODEFORCES -> "https://codeforces.com/enter"
    }

    fun getCookieHelpText(source: ProblemSource): String = when (source) {
        ProblemSource.PROGRAMMERS -> I18n.t(
            "school.programmers.co.kr에서 로그인 후 쿠키를 복사하세요.\n필요한 쿠키: _programmers_session_production",
            "Log in at school.programmers.co.kr and copy cookies.\nRequired: _programmers_session_production"
        )
        ProblemSource.SWEA -> I18n.t(
            "swexpertacademy.com에서 로그인 후 쿠키를 복사하세요.",
            "Log in at swexpertacademy.com and copy cookies."
        )
        ProblemSource.LEETCODE -> I18n.t(
            "leetcode.com에서 로그인 후 쿠키를 복사하세요.\n필요한 쿠키: LEETCODE_SESSION, csrftoken",
            "Log in at leetcode.com and copy cookies.\nRequired: LEETCODE_SESSION, csrftoken"
        )
        ProblemSource.CODEFORCES -> I18n.t(
            "codeforces.com에서 로그인 후 쿠키를 복사하세요.",
            "Log in at codeforces.com and copy cookies."
        )
    }

    companion object {
        fun getInstance(): AuthService =
            ApplicationManager.getApplication().getService(AuthService::class.java)
    }
}
