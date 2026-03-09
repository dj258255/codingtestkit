package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProblemFileManagerTest {

    // ── sanitizeFolderName (리플렉션) ──

    private fun callSanitize(name: String): String {
        val method = ProblemFileManager::class.java.getDeclaredMethod("sanitizeFolderName", String::class.java)
        method.isAccessible = true
        return method.invoke(ProblemFileManager, name) as String
    }

    @Test
    fun `sanitizeFolderName keeps normal characters`() {
        assertEquals("1000. A+B", callSanitize("1000. A+B"))
    }

    @Test
    fun `sanitizeFolderName removes special characters`() {
        val result = callSanitize("문제: Hello/World?")
        assertFalse(result.contains("/"), "Should not contain /")
        assertFalse(result.contains(":"), "Should not contain :")
        assertFalse(result.contains("?"), "Should not contain ?")
    }

    @Test
    fun `sanitizeFolderName collapses multiple underscores`() {
        val result = callSanitize("a!!!b")
        assertFalse(result.contains("__"), "Should not have consecutive underscores")
    }

    @Test
    fun `sanitizeFolderName truncates to 60 chars`() {
        val longName = "A".repeat(100)
        val result = callSanitize(longName)
        assertTrue(result.length <= 60)
    }

    @Test
    fun `sanitizeFolderName keeps Korean characters`() {
        val result = callSanitize("1000. 에이플러스비")
        assertTrue(result.contains("에이플러스비"))
    }

    // ── JSON 직렬화/역직렬화 ──

    @TempDir
    lateinit var tempDir: File

    private fun createTestProblem(): Problem {
        return Problem(
            source = ProblemSource.BAEKJOON,
            id = "1000",
            title = "A+B",
            description = "<h2>문제</h2><p>두 수를 더하세요</p>",
            testCases = mutableListOf(
                TestCase(input = "1 2", expectedOutput = "3"),
                TestCase(input = "3 4", expectedOutput = "7")
            ),
            timeLimit = "2 초",
            memoryLimit = "256 MB",
            difficulty = "Bronze V",
            parameterNames = emptyList()
        )
    }

    @Test
    fun `saveProblemJson and loadProblemFromFolder roundtrip`() {
        val problem = createTestProblem()

        // saveProblemJson은 private이므로 리플렉션
        val saveMethod = ProblemFileManager::class.java.getDeclaredMethod(
            "saveProblemJson", File::class.java, Problem::class.java
        )
        saveMethod.isAccessible = true
        saveMethod.invoke(ProblemFileManager, tempDir, problem)

        // problem.json이 생성되었는지 확인
        val jsonFile = File(tempDir, "problem.json")
        assertTrue(jsonFile.exists())
        assertTrue(jsonFile.readText().isNotBlank())

        // loadProblemFromFolder로 역직렬화
        val loaded = ProblemFileManager.loadProblemFromFolder(tempDir)
        assertNotNull(loaded)
        assertEquals(problem.source, loaded!!.source)
        assertEquals(problem.id, loaded.id)
        assertEquals(problem.title, loaded.title)
        assertEquals(problem.description, loaded.description)
        assertEquals(problem.timeLimit, loaded.timeLimit)
        assertEquals(problem.memoryLimit, loaded.memoryLimit)
        assertEquals(problem.difficulty, loaded.difficulty)
        assertEquals(2, loaded.testCases.size)
        assertEquals("1 2", loaded.testCases[0].input)
        assertEquals("3", loaded.testCases[0].expectedOutput)
        assertEquals("3 4", loaded.testCases[1].input)
        assertEquals("7", loaded.testCases[1].expectedOutput)
    }

    @Test
    fun `loadProblemFromFolder returns null for missing file`() {
        val emptyDir = File(tempDir, "empty")
        emptyDir.mkdirs()
        assertNull(ProblemFileManager.loadProblemFromFolder(emptyDir))
    }

    @Test
    fun `loadProblemFromFolder returns null for invalid JSON`() {
        val dir = File(tempDir, "invalid")
        dir.mkdirs()
        File(dir, "problem.json").writeText("not valid json {{{")
        assertNull(ProblemFileManager.loadProblemFromFolder(dir))
    }

    @Test
    fun `loadProblemFromFolder handles missing optional fields`() {
        val dir = File(tempDir, "minimal")
        dir.mkdirs()
        File(dir, "problem.json").writeText("""
            {
                "source": "BAEKJOON",
                "id": "1000",
                "title": "Test",
                "description": "desc",
                "testCases": []
            }
        """.trimIndent())

        val loaded = ProblemFileManager.loadProblemFromFolder(dir)
        assertNotNull(loaded)
        assertEquals("", loaded!!.timeLimit)
        assertEquals("", loaded.memoryLimit)
        assertEquals("", loaded.difficulty)
        assertEquals(emptyList<String>(), loaded.parameterNames)
    }

    // ── findProblemFolder ──

    @Test
    fun `findProblemFolder finds folder with problem json`() {
        val problemsDir = File(tempDir, "problems")
        val problemDir = File(problemsDir, "백준/Bronze V/1000. A+B")
        problemDir.mkdirs()
        File(problemDir, "problem.json").writeText("{}")

        val codeFile = File(problemDir, "Main.java")
        codeFile.writeText("public class Main {}")

        val found = ProblemFileManager.findProblemFolder(codeFile.absolutePath, tempDir.absolutePath)
        assertNotNull(found)
        assertEquals(problemDir.absolutePath, found!!.absolutePath)
    }

    @Test
    fun `findProblemFolder returns null when no problems dir`() {
        val result = ProblemFileManager.findProblemFolder("/some/path/file.java", tempDir.absolutePath)
        assertNull(result)
    }

    @Test
    fun `findProblemFolder returns null for file outside problems`() {
        val problemsDir = File(tempDir, "problems")
        problemsDir.mkdirs()
        val outsideFile = File(tempDir, "src/Main.java")
        outsideFile.parentFile.mkdirs()
        outsideFile.writeText("")

        val result = ProblemFileManager.findProblemFolder(outsideFile.absolutePath, tempDir.absolutePath)
        assertNull(result)
    }

    // ── generateMarkdown (리플렉션) ──

    @Test
    fun `generateMarkdown includes title and metadata`() {
        val method = ProblemFileManager::class.java.getDeclaredMethod("generateMarkdown", Problem::class.java)
        method.isAccessible = true

        val problem = createTestProblem()
        val markdown = method.invoke(ProblemFileManager, problem) as String

        assertTrue(markdown.contains("[백준 #1000] A+B"))
        assertTrue(markdown.contains("시간 제한"))
        assertTrue(markdown.contains("2 초"))
        assertTrue(markdown.contains("메모리 제한"))
        assertTrue(markdown.contains("256 MB"))
    }

    @Test
    fun `generateMarkdown includes test cases for Baekjoon`() {
        val method = ProblemFileManager::class.java.getDeclaredMethod("generateMarkdown", Problem::class.java)
        method.isAccessible = true

        val problem = createTestProblem()
        val markdown = method.invoke(ProblemFileManager, problem) as String

        assertTrue(markdown.contains("예제"))
        assertTrue(markdown.contains("1 2"))
        assertTrue(markdown.contains("3"))
    }

    @Test
    fun `generateMarkdown skips test cases for Programmers`() {
        val method = ProblemFileManager::class.java.getDeclaredMethod("generateMarkdown", Problem::class.java)
        method.isAccessible = true

        val problem = Problem(
            source = ProblemSource.PROGRAMMERS,
            id = "12345",
            title = "Test Problem",
            description = "<p>desc</p>",
            testCases = mutableListOf(TestCase(input = "1", expectedOutput = "2"))
        )
        val markdown = method.invoke(ProblemFileManager, problem) as String

        assertFalse(markdown.contains("## 예제"), "Programmers should not have separate test case section")
    }

    @Test
    fun `generateMarkdown skips test cases for LeetCode`() {
        val method = ProblemFileManager::class.java.getDeclaredMethod("generateMarkdown", Problem::class.java)
        method.isAccessible = true

        val problem = Problem(
            source = ProblemSource.LEETCODE,
            id = "two-sum",
            title = "Two Sum",
            description = "<p>desc</p>",
            testCases = mutableListOf(TestCase(input = "[2,7,11]", expectedOutput = "[0,1]"))
        )
        val markdown = method.invoke(ProblemFileManager, problem) as String

        assertFalse(markdown.contains("## 예제"))
    }

    // ── truncatePreview (리플렉션) ──

    @Test
    fun `truncatePreview truncates long text`() {
        val method = ProblemFileManager::class.java.getDeclaredMethod(
            "truncatePreview", String::class.java, Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val text = (1..20).joinToString("\n") { "line $it" }
        val result = method.invoke(ProblemFileManager, text, 5) as String

        assertTrue(result.endsWith("..."))
        assertEquals(6, result.lines().size) // 5 lines + "..."
    }

    @Test
    fun `truncatePreview does not truncate short text`() {
        val method = ProblemFileManager::class.java.getDeclaredMethod(
            "truncatePreview", String::class.java, Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val text = "line1\nline2\nline3"
        val result = method.invoke(ProblemFileManager, text, 10) as String

        assertEquals(text, result)
    }
}
