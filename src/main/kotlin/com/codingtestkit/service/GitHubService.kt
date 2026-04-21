package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.util.Base64

@Service
@State(name = "CodingTestKitGitHub", storages = [Storage("codingtestkit-github.xml")])
class GitHubService : PersistentStateComponent<GitHubService.GitHubState> {

    /**
     * Token stored in IntelliJ PasswordSafe (OS keychain).
     * Legacy plaintext `token` field remains for one-time migration.
     */
    data class GitHubState(
        var repoFullName: String = "",   // "owner/repo"
        var autoPushEnabled: Boolean = false,
        // Legacy plaintext token — migrated to PasswordSafe on first load, then cleared
        var token: String = ""
    )

    private var state = GitHubState()

    override fun getState(): GitHubState = state

    override fun loadState(state: GitHubState) {
        this.state = state
        if (state.token.isNotBlank()) {
            writeToken(state.token)
            state.token = ""
        }
    }

    private val tokenCredentialAttributes: CredentialAttributes =
        CredentialAttributes(generateServiceName("CodingTestKit", "github.token"))

    private fun writeToken(value: String) {
        PasswordSafe.instance.setPassword(tokenCredentialAttributes, value.ifBlank { null })
    }

    private fun readToken(): String =
        PasswordSafe.instance.getPassword(tokenCredentialAttributes) ?: ""

    val token: String get() = readToken()
    val repoFullName: String get() = state.repoFullName
    val autoPushEnabled: Boolean get() = state.autoPushEnabled

    fun setToken(token: String) = writeToken(token)
    fun setRepoFullName(name: String) { state.repoFullName = name }
    fun setAutoPushEnabled(enabled: Boolean) { state.autoPushEnabled = enabled }

    fun isConfigured(): Boolean = token.isNotBlank() && repoFullName.isNotBlank()

    /**
     * GitHub API로 토큰 유효성 검증 + 유저 이름 가져오기
     */
    fun validateToken(): String? {
        if (token.isBlank()) return null
        return try {
            val json = Jsoup.connect("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .userAgent("CodingTestKit-Plugin")
                .ignoreContentType(true)
                .timeout(5000)
                .execute()
                .body()
            val obj = JsonParser.parseString(json).asJsonObject
            obj.get("login")?.asString
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 토큰으로 사용자의 레포 목록 가져오기
     */
    fun listRepos(): List<String> {
        if (token.isBlank()) return emptyList()
        return try {
            val allRepos = mutableListOf<String>()
            var page = 1
            while (true) {
                val json = Jsoup.connect("https://api.github.com/user/repos?sort=updated&per_page=100&page=$page")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .userAgent("CodingTestKit-Plugin")
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute()
                    .body()
                val arr = JsonParser.parseString(json).asJsonArray
                if (arr.size() == 0) break
                for (el in arr) {
                    val fullName = el.asJsonObject.get("full_name")?.asString ?: continue
                    allRepos.add(fullName)
                }
                if (arr.size() < 100) break
                page++
            }
            allRepos
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 레포 존재 여부 확인
     */
    fun validateRepo(): Boolean {
        if (!isConfigured()) return false
        return try {
            val response = Jsoup.connect("https://api.github.com/repos/$repoFullName")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .userAgent("CodingTestKit-Plugin")
                .ignoreContentType(true)
                .timeout(5000)
                .execute()
            response.statusCode() == 200
        } catch (_: Exception) {
            false
        }
    }

    /**
     * GitHub에 파일 업로드 (생성 또는 업데이트)
     * @return 성공 시 commit SHA, 실패 시 null
     */
    fun pushFile(path: String, content: String, commitMessage: String): String? {
        if (!isConfigured()) return null

        val encoded = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        val url = "https://api.github.com/repos/$repoFullName/contents/$path"

        // 기존 파일 SHA 가져오기 (업데이트 시 필요)
        val existingSha = getFileSha(path)

        val body = JsonObject().apply {
            addProperty("message", commitMessage)
            addProperty("content", encoded)
            if (existingSha != null) {
                addProperty("sha", existingSha)
            }
        }

        return try {
            val response = Jsoup.connect(url)
                .method(Connection.Method.PUT)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .userAgent("CodingTestKit-Plugin")
                .requestBody(body.toString())
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .timeout(10000)
                .execute()

            if (response.statusCode() in 200..201) {
                val result = JsonParser.parseString(response.body()).asJsonObject
                result.getAsJsonObject("commit")?.get("sha")?.asString
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 문제 풀이를 GitHub에 푸시
     */
    fun pushSolution(problem: Problem, code: String, language: com.codingtestkit.model.Language): PushResult {
        if (!isConfigured()) return PushResult(false, "GitHub not configured")

        val folderPath = buildFolderPath(problem)
        val codeFileName = "${problem.source.mainClassName}.${language.extension}"

        // 1. 코드 파일 푸시
        val commitMsg = buildCommitMessage(problem, language)
        val codeSha = pushFile("$folderPath/$codeFileName", code, commitMsg)
            ?: return PushResult(false, "Failed to push code file")

        // 2. README 푸시 (설정에서 켜진 경우만)
        if (PluginSettingsService.getInstance().generateReadme) {
            val readme = buildReadme(problem, language)
            pushFile("$folderPath/README.md", readme, "$commitMsg - README")
        }

        return PushResult(true, codeSha)
    }

    private fun getFileSha(path: String): String? {
        return try {
            val response = Jsoup.connect("https://api.github.com/repos/$repoFullName/contents/$path")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .userAgent("CodingTestKit-Plugin")
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .timeout(5000)
                .execute()

            if (response.statusCode() == 200) {
                val obj = JsonParser.parseString(response.body()).asJsonObject
                obj.get("sha")?.asString
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun buildFolderPath(problem: Problem): String {
        val platform = problem.source.localizedName()
        val difficulty = problem.difficulty.ifBlank { "Unrated" }
        val title = "${problem.id}. ${sanitizeFileName(problem.title)}"
        return "$platform/$difficulty/$title"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
    }

    private fun buildCommitMessage(problem: Problem, language: com.codingtestkit.model.Language): String {
        return "[${problem.source.localizedName()} #${problem.id}] ${problem.title} (${language.displayName})"
    }

    private fun buildReadme(problem: Problem, language: com.codingtestkit.model.Language): String {
        val link = getProblemLink(problem)
        return buildString {
            appendLine("# ${problem.title}")
            appendLine()
            appendLine("| | |")
            appendLine("|---|---|")
            appendLine("| **${I18n.t("플랫폼", "Platform")}** | ${problem.source.localizedName()} |")
            appendLine("| **${I18n.t("문제", "Problem")}** | [#${problem.id}]($link) |")
            if (problem.difficulty.isNotBlank()) appendLine("| **${I18n.t("난이도", "Difficulty")}** | ${problem.difficulty} |")
            if (problem.timeLimit.isNotBlank()) appendLine("| **${I18n.t("시간 제한", "Time Limit")}** | ${problem.timeLimit} |")
            if (problem.memoryLimit.isNotBlank()) appendLine("| **${I18n.t("메모리 제한", "Memory Limit")}** | ${problem.memoryLimit} |")
            appendLine("| **${I18n.t("언어", "Language")}** | ${language.displayName} |")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("*Pushed by [CodingTestKit](https://github.com/dj258255/codingtestkit)*")
        }
    }

    private fun getProblemLink(problem: Problem): String = when (problem.source) {
        com.codingtestkit.model.ProblemSource.PROGRAMMERS -> "https://school.programmers.co.kr/learn/courses/30/lessons/${problem.id}"
        com.codingtestkit.model.ProblemSource.SWEA -> "https://swexpertacademy.com/main/code/problem/problemDetail.do?contestProbId=${problem.id}"
        com.codingtestkit.model.ProblemSource.LEETCODE -> "https://leetcode.com/problems/${problem.id}/"
        com.codingtestkit.model.ProblemSource.CODEFORCES -> "https://codeforces.com/problemset/problem/${problem.contestProbId.ifBlank { problem.id }}"
    }

    data class PushResult(val success: Boolean, val message: String)

    companion object {
        fun getInstance(): GitHubService =
            ApplicationManager.getApplication().getService(GitHubService::class.java)
    }
}
