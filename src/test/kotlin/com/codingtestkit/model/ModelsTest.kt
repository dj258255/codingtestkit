package com.codingtestkit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ModelsTest {

    // ── ProblemSource ──

    @Test
    fun `fromDisplayName returns correct source for Korean names`() {
        assertEquals(ProblemSource.BAEKJOON, ProblemSource.fromDisplayName("백준"))
        assertEquals(ProblemSource.PROGRAMMERS, ProblemSource.fromDisplayName("프로그래머스"))
        assertEquals(ProblemSource.SWEA, ProblemSource.fromDisplayName("SWEA"))
        assertEquals(ProblemSource.LEETCODE, ProblemSource.fromDisplayName("LeetCode"))
    }

    @Test
    fun `fromDisplayName throws for unknown name`() {
        assertThrows<NoSuchElementException> {
            ProblemSource.fromDisplayName("unknown")
        }
    }

    @Test
    fun `ProblemSource has correct folder names`() {
        assertEquals("baekjoon", ProblemSource.BAEKJOON.folderName)
        assertEquals("programmers", ProblemSource.PROGRAMMERS.folderName)
        assertEquals("swea", ProblemSource.SWEA.folderName)
        assertEquals("leetcode", ProblemSource.LEETCODE.folderName)
    }

    @Test
    fun `ProblemSource has correct main class names`() {
        assertEquals("Main", ProblemSource.BAEKJOON.mainClassName)
        assertEquals("Solution", ProblemSource.PROGRAMMERS.mainClassName)
        assertEquals("Solution", ProblemSource.SWEA.mainClassName)
        assertEquals("Solution", ProblemSource.LEETCODE.mainClassName)
    }

    // ── Language ──

    @Test
    fun `Language extensions are correct`() {
        assertEquals("java", Language.JAVA.extension)
        assertEquals("py", Language.PYTHON.extension)
        assertEquals("cpp", Language.CPP.extension)
        assertEquals("kt", Language.KOTLIN.extension)
    }

    @Test
    fun `Language baekjoon IDs are set`() {
        assertEquals(93, Language.JAVA.baekjoonId)
        assertEquals(28, Language.PYTHON.baekjoonId)
        assertEquals(84, Language.CPP.baekjoonId)
        assertEquals(69, Language.KOTLIN.baekjoonId)
    }

    @Test
    fun `Java defaultCode for Baekjoon has Main class with Scanner`() {
        val code = Language.JAVA.defaultCode(ProblemSource.BAEKJOON)
        assertTrue(code.contains("public class Main"))
        assertTrue(code.contains("Scanner"))
        assertTrue(code.contains("public static void main"))
    }

    @Test
    fun `Java defaultCode for Programmers has Solution class`() {
        val code = Language.JAVA.defaultCode(ProblemSource.PROGRAMMERS)
        assertTrue(code.contains("class Solution"))
        assertTrue(code.contains("solution()"))
    }

    @Test
    fun `Java defaultCode for SWEA has loop for test cases`() {
        val code = Language.JAVA.defaultCode(ProblemSource.SWEA)
        assertTrue(code.contains("public class Solution"))
        assertTrue(code.contains("int T = sc.nextInt()"))
        assertTrue(code.contains("for (int tc = 1"))
    }

    @Test
    fun `Python defaultCode for Baekjoon is empty`() {
        val code = Language.PYTHON.defaultCode(ProblemSource.BAEKJOON)
        assertEquals("", code)
    }

    @Test
    fun `Python defaultCode for Programmers has solution function`() {
        val code = Language.PYTHON.defaultCode(ProblemSource.PROGRAMMERS)
        assertTrue(code.contains("def solution():"))
        assertTrue(code.contains("return answer"))
    }

    @Test
    fun `Cpp defaultCode for Baekjoon has main function`() {
        val code = Language.CPP.defaultCode(ProblemSource.BAEKJOON)
        assertTrue(code.contains("#include <iostream>"))
        assertTrue(code.contains("int main()"))
    }

    @Test
    fun `Kotlin defaultCode for Baekjoon has main function`() {
        val code = Language.KOTLIN.defaultCode(ProblemSource.BAEKJOON)
        assertTrue(code.contains("fun main()"))
    }

    @Test
    fun `LeetCode defaultCode is empty for all languages`() {
        for (lang in Language.entries) {
            assertEquals("", lang.defaultCode(ProblemSource.LEETCODE),
                "${lang.displayName} should have empty LeetCode template")
        }
    }

    // ── TestCase ──

    @Test
    fun `TestCase default values`() {
        val tc = TestCase(input = "1 2", expectedOutput = "3")
        assertEquals("1 2", tc.input)
        assertEquals("3", tc.expectedOutput)
        assertEquals("", tc.actualOutput)
        assertNull(tc.passed)
    }

    @Test
    fun `TestCase mutable fields can be updated`() {
        val tc = TestCase(input = "1", expectedOutput = "2")
        tc.actualOutput = "2"
        tc.passed = true
        assertEquals("2", tc.actualOutput)
        assertTrue(tc.passed!!)
    }

    // ── Problem ──

    @Test
    fun `Problem creation with defaults`() {
        val problem = Problem(
            source = ProblemSource.BAEKJOON,
            id = "1000",
            title = "A+B",
            description = "<p>두 수를 더하세요</p>",
            testCases = mutableListOf()
        )
        assertEquals("", problem.timeLimit)
        assertEquals("", problem.memoryLimit)
        assertEquals("", problem.difficulty)
        assertEquals(emptyList<String>(), problem.parameterNames)
        assertEquals("", problem.initialCode)
    }
}
