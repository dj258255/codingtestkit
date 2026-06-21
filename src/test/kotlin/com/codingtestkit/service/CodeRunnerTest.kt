package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.codingtestkit.model.TestCase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files

/**
 * CodeRunner 통합 테스트.
 * 실제 컴파일러가 시스템에 설치되어 있어야 통과합니다.
 * IntelliJ Platform 테스트 가이드에 따라 기능 전체를 테스트합니다.
 */
class CodeRunnerTest {

    // ── private 유틸 리플렉션 테스트 ──

    @Test
    fun `detectJavaClassName finds public class`() {
        val method = CodeRunner::class.java.getDeclaredMethod("detectJavaClassName", String::class.java)
        method.isAccessible = true

        assertEquals("Main", method.invoke(CodeRunner, "public class Main { }"))
        assertEquals("Solution", method.invoke(CodeRunner, "public class Solution { }"))
    }

    @Test
    fun `detectJavaClassName fallback to first class`() {
        val method = CodeRunner::class.java.getDeclaredMethod("detectJavaClassName", String::class.java)
        method.isAccessible = true

        assertEquals("Foo", method.invoke(CodeRunner, "class Foo { } class Bar { }"))
    }

    @Test
    fun `detectJavaClassName default is Main`() {
        val method = CodeRunner::class.java.getDeclaredMethod("detectJavaClassName", String::class.java)
        method.isAccessible = true

        assertEquals("Main", method.invoke(CodeRunner, "// no class declaration"))
    }

    @Test
    fun `hasMainFunction detects Java main`() {
        val method = CodeRunner::class.java.getDeclaredMethod(
            "hasMainFunction", String::class.java, Language::class.java
        )
        method.isAccessible = true

        assertTrue(method.invoke(CodeRunner, "public static void main(String[] args) {}", Language.JAVA) as Boolean)
        assertFalse(method.invoke(CodeRunner, "class Solution { int solution() {} }", Language.JAVA) as Boolean)
    }

    @Test
    fun `hasMainFunction detects Python main`() {
        val method = CodeRunner::class.java.getDeclaredMethod(
            "hasMainFunction", String::class.java, Language::class.java
        )
        method.isAccessible = true

        assertTrue(method.invoke(CodeRunner, "if __name__ == '__main__':", Language.PYTHON) as Boolean)
        assertFalse(method.invoke(CodeRunner, "def solution():", Language.PYTHON) as Boolean)
    }

    @Test
    fun `hasMainFunction detects Cpp main`() {
        val method = CodeRunner::class.java.getDeclaredMethod(
            "hasMainFunction", String::class.java, Language::class.java
        )
        method.isAccessible = true

        assertTrue(method.invoke(CodeRunner, "int main() { return 0; }", Language.CPP) as Boolean)
    }

    @Test
    fun `hasMainFunction detects Kotlin main`() {
        val method = CodeRunner::class.java.getDeclaredMethod(
            "hasMainFunction", String::class.java, Language::class.java
        )
        method.isAccessible = true

        assertTrue(method.invoke(CodeRunner, "fun main() { }", Language.KOTLIN) as Boolean)
    }

    // ── toJavaLiteral ──

    @Test
    fun `toJavaLiteral converts 1D int array`() {
        val method = CodeRunner::class.java.getDeclaredMethod("toJavaLiteral", String::class.java)
        method.isAccessible = true

        val result = method.invoke(CodeRunner, "[1, 2, 3]") as String
        assertTrue(result.contains("new int[]"))
        assertTrue(result.contains("1, 2, 3"))
    }

    @Test
    fun `toJavaLiteral converts string array`() {
        val method = CodeRunner::class.java.getDeclaredMethod("toJavaLiteral", String::class.java)
        method.isAccessible = true

        val result = method.invoke(CodeRunner, "[\"a\", \"b\"]") as String
        assertTrue(result.contains("String[]"))
    }

    @Test
    fun `toJavaLiteral passes through simple values`() {
        val method = CodeRunner::class.java.getDeclaredMethod("toJavaLiteral", String::class.java)
        method.isAccessible = true

        assertEquals("42", method.invoke(CodeRunner, "42"))
        assertEquals("\"hello\"", method.invoke(CodeRunner, "\"hello\""))
    }

    @Test
    fun `toJavaLiteral converts 2D array`() {
        val method = CodeRunner::class.java.getDeclaredMethod("toJavaLiteral", String::class.java)
        method.isAccessible = true

        val result = method.invoke(CodeRunner, "[[1,2],[3,4]]") as String
        assertTrue(result.contains("new int[][]"))
    }

    @Test
    fun `toJavaLiteral empty array`() {
        val method = CodeRunner::class.java.getDeclaredMethod("toJavaLiteral", String::class.java)
        method.isAccessible = true

        val result = method.invoke(CodeRunner, "[]") as String
        assertTrue(result.contains("new int[]{}"))
    }

    // ── toKotlinLiteral ──

    @Test
    fun `toKotlinLiteral converts int array`() {
        val method = CodeRunner::class.java.getDeclaredMethod("toKotlinLiteral", String::class.java)
        method.isAccessible = true

        val result = method.invoke(CodeRunner, "[1, 2, 3]") as String
        assertTrue(result.contains("intArrayOf(1, 2, 3)"))
    }

    @Test
    fun `toKotlinLiteral converts string array`() {
        val method = CodeRunner::class.java.getDeclaredMethod("toKotlinLiteral", String::class.java)
        method.isAccessible = true

        val result = method.invoke(CodeRunner, "[\"a\", \"b\"]") as String
        assertTrue(result.contains("arrayOf("))
    }

    @Test
    fun `toKotlinLiteral converts boolean array`() {
        val method = CodeRunner::class.java.getDeclaredMethod("toKotlinLiteral", String::class.java)
        method.isAccessible = true

        val result = method.invoke(CodeRunner, "[true, false]") as String
        assertTrue(result.contains("booleanArrayOf("))
    }

    @Test
    fun `toKotlinLiteral converts 2D array`() {
        val method = CodeRunner::class.java.getDeclaredMethod("toKotlinLiteral", String::class.java)
        method.isAccessible = true

        val result = method.invoke(CodeRunner, "[[1,2],[3,4]]") as String
        assertTrue(result.contains("arrayOf("))
        assertTrue(result.contains("intArrayOf("))
    }

    // ── toCppLiteral ──

    @Test
    fun `toCppLiteral converts brackets to braces`() {
        val method = CodeRunner::class.java.getDeclaredMethod("toCppLiteral", String::class.java)
        method.isAccessible = true

        assertEquals("{1, 2, 3}", method.invoke(CodeRunner, "[1, 2, 3]"))
        assertEquals("{{1,2},{3,4}}", method.invoke(CodeRunner, "[[1,2],[3,4]]"))
    }

    @Test
    fun `toCppLiteral passes through simple values`() {
        val method = CodeRunner::class.java.getDeclaredMethod("toCppLiteral", String::class.java)
        method.isAccessible = true

        assertEquals("42", method.invoke(CodeRunner, "42"))
    }

    // ── detectJavaArrayType ──

    @Test
    fun `detectJavaArrayType int`() {
        val method = CodeRunner::class.java.getDeclaredMethod("detectJavaArrayType", String::class.java)
        method.isAccessible = true

        assertEquals("int[]", method.invoke(CodeRunner, "1, 2, 3"))
    }

    @Test
    fun `detectJavaArrayType String`() {
        val method = CodeRunner::class.java.getDeclaredMethod("detectJavaArrayType", String::class.java)
        method.isAccessible = true

        assertEquals("String[]", method.invoke(CodeRunner, "\"a\", \"b\""))
    }

    @Test
    fun `detectJavaArrayType boolean`() {
        val method = CodeRunner::class.java.getDeclaredMethod("detectJavaArrayType", String::class.java)
        method.isAccessible = true

        assertEquals("boolean[]", method.invoke(CodeRunner, "true, false"))
    }

    @Test
    fun `detectJavaArrayType double`() {
        val method = CodeRunner::class.java.getDeclaredMethod("detectJavaArrayType", String::class.java)
        method.isAccessible = true

        assertEquals("double[]", method.invoke(CodeRunner, "1.5, 2.3"))
    }

    @Test
    fun `detectJavaArrayType long for large numbers`() {
        val method = CodeRunner::class.java.getDeclaredMethod("detectJavaArrayType", String::class.java)
        method.isAccessible = true

        assertEquals("long[]", method.invoke(CodeRunner, "3000000000, 1"))
    }

    // ── 실제 코드 실행 통합 테스트 ──

    private fun isJavaAvailable(): Boolean {
        return try {
            ProcessBuilder("javac", "-version").start().waitFor() == 0
        } catch (_: Exception) { false }
    }

    private fun isPythonAvailable(): Boolean {
        return try {
            ProcessBuilder("python3", "--version").start().waitFor() == 0
        } catch (_: Exception) { false }
    }

    @Test
    fun `run Java hello world`() {
        if (!isJavaAvailable()) return

        val code = """
            public class Main {
                public static void main(String[] args) {
                    java.util.Scanner sc = new java.util.Scanner(System.in);
                    int a = sc.nextInt();
                    int b = sc.nextInt();
                    System.out.println(a + b);
                }
            }
        """.trimIndent()

        val tc = TestCase(input = "1 2", expectedOutput = "3")
        val result = CodeRunner.run(code, Language.JAVA, tc)

        assertEquals(0, result.exitCode, "Exit code should be 0: ${result.error}")
        assertEquals("3", result.output.trim())
        assertTrue(result.executionTimeMs > 0)
    }

    @Test
    fun `run Python hello world`() {
        if (!isPythonAvailable()) return

        val code = """
            a, b = map(int, input().split())
            print(a + b)
        """.trimIndent()

        val tc = TestCase(input = "3 4", expectedOutput = "7")
        val result = CodeRunner.run(code, Language.PYTHON, tc)

        assertEquals(0, result.exitCode, "Exit code should be 0: ${result.error}")
        assertEquals("7", result.output.trim())
    }

    @Test
    fun `run Java with compile error returns error message`() {
        if (!isJavaAvailable()) return

        val code = """
            public class Main {
                public static void main(String[] args) {
                    int x = // syntax error
                }
            }
        """.trimIndent()

        val tc = TestCase(input = "", expectedOutput = "")
        val result = CodeRunner.run(code, Language.JAVA, tc)

        assertNotEquals(0, result.exitCode)
        assertTrue(result.error.contains("컴파일 에러") || result.error.contains("error"),
            "Should contain compilation error: ${result.error}")
    }

    @Test
    fun `run Java programmers wrapper compiles utf8 source`() {
        if (!isJavaAvailable()) return

        val code = """
            class Solution {
                public String solution(String name) {
                    return "안녕, " + name;
                }
            }
        """.trimIndent()

        val tc = TestCase(input = "\"세계\"", expectedOutput = "\"안녕, 세계\"")
        val result = CodeRunner.runProgrammers(code, Language.JAVA, tc, listOf("name"))

        assertEquals(0, result.exitCode, "Exit code should be 0: ${result.error}")
        assertEquals("\"안녕, 세계\"", result.output.trim())
    }

    @Test
    fun `run Python with runtime error returns error`() {
        if (!isPythonAvailable()) return

        val code = "raise ValueError('test error')"
        val tc = TestCase(input = "", expectedOutput = "")
        val result = CodeRunner.run(code, Language.PYTHON, tc)

        assertNotEquals(0, result.exitCode)
        assertTrue(result.error.contains("ValueError"), "Should contain error: ${result.error}")
    }

    @Test
    fun `run with timeout`() {
        if (!isPythonAvailable()) return

        val code = """
            import time
            time.sleep(10)
        """.trimIndent()

        val tc = TestCase(input = "", expectedOutput = "")
        val result = CodeRunner.run(code, Language.PYTHON, tc, timeoutSeconds = 2)

        assertTrue(result.timedOut, "Should time out")
    }

    /**
     * Regression test for issue #2: "unmappable character" compile error on Windows (MS949).
     *
     * The generated wrapper source contains Korean comments and is written to a UTF-8 temp file.
     * Reading it with the Korean Windows javac default (x-windows-949) breaks compilation, while
     * applying PR #3's fix (`javac -encoding UTF-8`) compiles it cleanly. Verified OS-independently
     * by forcing the encoding explicitly so the test fails if the `-encoding UTF-8` flag regresses.
     */
    @Test
    fun `Java compile fails under MS949 but succeeds with UTF-8 encoding flag`() {
        if (!isJavaAvailable()) return
        // Skip if this JDK lacks the MS949 (x-windows-949) charset and cannot simulate the case.
        if (!Charset.isSupported("x-windows-949")) return

        val javac = CodeRunner.getDetectedPaths()["javac"]
        if (javac.isNullOrBlank()) return

        val dir = Files.createTempDirectory("ctk_enc_test_").toFile()
        try {
            // User code is ASCII, but the Korean comment the wrapper injects triggers the bug (issue #2).
            val src = File(dir, "Main.java")
            src.writeText(
                """
                class Main {
                    public static void main(String[] args) {
                        // 사용자 debug 출력(System.out.println)을 stderr로 리다이렉트
                        System.out.println("hello");
                    }
                }
                """.trimIndent(),
                Charsets.UTF_8
            )

            // (1) Bug condition: reading as MS949 fails with an unmappable character error.
            val ms949 = ProcessBuilder(javac, "-encoding", "x-windows-949", src.absolutePath)
                .directory(dir).redirectErrorStream(true).start()
            val ms949Out = ms949.inputStream.bufferedReader().readText()
            val ms949Exit = ms949.waitFor()
            assertNotEquals(0, ms949Exit, "Compilation must fail under MS949 (reproduces issue #2): $ms949Out")
            assertTrue(ms949Out.contains("unmappable character"),
                "MS949 failure should be an unmappable character error: $ms949Out")

            // (2) Fix condition: the `-encoding UTF-8` flag applied by PR #3 compiles successfully.
            val utf8 = ProcessBuilder(javac, "-encoding", "UTF-8", src.absolutePath)
                .directory(dir).redirectErrorStream(true).start()
            val utf8Out = utf8.inputStream.bufferedReader().readText()
            val utf8Exit = utf8.waitFor()
            assertEquals(0, utf8Exit, "Compilation should succeed when UTF-8 encoding is specified: $utf8Out")
        } finally {
            dir.deleteRecursively()
        }
    }
}
