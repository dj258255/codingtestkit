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

    /**
     * Regression test for issue #4: the java run command must force UTF-8 standard I/O encoding.
     *
     * The runtime garbling only reproduces on a real Windows host (native.encoding = MS949), where
     * `-Dstdout.encoding` cannot be overridden from the command line on macOS/Linux. So instead of
     * an unreproducible end-to-end test, we assert the command `javaCommand` builds always carries
     * the UTF-8 encoding flags — this fails OS-independently if the flags regress.
     */
    @Test
    fun `javaCommand forces UTF-8 standard IO encoding`() {
        val method = CodeRunner::class.java.getDeclaredMethod("javaCommand", Array<String>::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val cmd = method.invoke(CodeRunner, arrayOf("-cp", "X", "Main")) as List<String>

        assertTrue(cmd.contains("-Dfile.encoding=UTF-8"), "missing file.encoding flag: $cmd")
        assertTrue(cmd.contains("-Dstdout.encoding=UTF-8"), "missing stdout.encoding flag: $cmd")
        assertTrue(cmd.contains("-Dstderr.encoding=UTF-8"), "missing stderr.encoding flag: $cmd")
        // The caller's arguments must be preserved at the end of the command.
        assertEquals(listOf("-cp", "X", "Main"), cmd.takeLast(3))
    }

    // ── Rust / Go / Ruby (이슈 #7) ──

    private fun isToolAvailable(vararg command: String): Boolean {
        return try {
            ProcessBuilder(*command).start().waitFor() == 0
        } catch (_: Exception) { false }
    }

    @Test
    fun `hasMainFunction detects Rust Go Ruby main`() {
        val method = CodeRunner::class.java.getDeclaredMethod(
            "hasMainFunction", String::class.java, Language::class.java
        )
        method.isAccessible = true

        assertTrue(method.invoke(CodeRunner, "fn main() { }", Language.RUST) as Boolean)
        assertFalse(method.invoke(CodeRunner, "fn solution() -> i32 { 0 }", Language.RUST) as Boolean)
        assertTrue(method.invoke(CodeRunner, "func main() { }", Language.GO) as Boolean)
        assertFalse(method.invoke(CodeRunner, "func solution() int { return 0 }", Language.GO) as Boolean)
        // Ruby는 stdin 사용 여부로 스크립트 스타일 판별
        assertTrue(method.invoke(CodeRunner, "n = gets.to_i", Language.RUBY) as Boolean)
        assertFalse(method.invoke(CodeRunner, "def solution()\n  0\nend", Language.RUBY) as Boolean)
    }

    @Test
    fun `run Rust hello world`() {
        if (!isToolAvailable("rustc", "--version")) return

        val code = """
            use std::io::{self, Read};

            fn main() {
                let mut input = String::new();
                io::stdin().read_to_string(&mut input).unwrap();
                let mut it = input.split_whitespace();
                let a: i64 = it.next().unwrap().parse().unwrap();
                let b: i64 = it.next().unwrap().parse().unwrap();
                println!("{}", a + b);
            }
        """.trimIndent()

        val tc = TestCase(input = "1 2", expectedOutput = "3")
        val result = CodeRunner.run(code, Language.RUST, tc)

        assertEquals(0, result.exitCode, "Exit code should be 0: ${result.error}")
        assertEquals("3", result.output.trim())
    }

    @Test
    fun `run Go hello world`() {
        if (!isToolAvailable("go", "version")) return

        val code = """
            package main

            import "fmt"

            func main() {
                var a, b int
                fmt.Scan(&a, &b)
                fmt.Println(a + b)
            }
        """.trimIndent()

        val tc = TestCase(input = "3 4", expectedOutput = "7")
        val result = CodeRunner.run(code, Language.GO, tc)

        assertEquals(0, result.exitCode, "Exit code should be 0: ${result.error}")
        assertEquals("7", result.output.trim())
    }

    @Test
    fun `run Ruby hello world`() {
        if (!isToolAvailable("ruby", "--version")) return

        val code = """
            a, b = gets.split.map(&:to_i)
            puts a + b
        """.trimIndent()

        val tc = TestCase(input = "5 6", expectedOutput = "11")
        val result = CodeRunner.run(code, Language.RUBY, tc)

        assertEquals(0, result.exitCode, "Exit code should be 0: ${result.error}")
        assertEquals("11", result.output.trim())
    }

    @Test
    fun `run Rust programmers wrapper with vec argument`() {
        if (!isToolAvailable("rustc", "--version")) return

        val code = """
            fn solution(arr: Vec<i32>) -> i32 {
                arr.iter().sum()
            }
        """.trimIndent()

        val tc = TestCase(input = "[1,2,3]", expectedOutput = "6")
        val result = CodeRunner.runProgrammers(code, Language.RUST, tc, listOf("arr"))

        assertEquals(0, result.exitCode, "Exit code should be 0: ${result.error}")
        assertEquals("6", result.output.trim())
    }

    @Test
    fun `run Go programmers wrapper returns compact array`() {
        if (!isToolAvailable("go", "version")) return

        val code = """
            func solution(arr []int) []int {
                doubled := make([]int, len(arr))
                for i, v := range arr {
                    doubled[i] = v * 2
                }
                return doubled
            }
        """.trimIndent()

        val tc = TestCase(input = "[1,2,3]", expectedOutput = "[2,4,6]")
        val result = CodeRunner.runProgrammers(code, Language.GO, tc, listOf("arr"))

        assertEquals(0, result.exitCode, "Exit code should be 0: ${result.error}")
        assertEquals("[2,4,6]", result.output.trim())
    }

    @Test
    fun `run Ruby programmers wrapper redirects user output`() {
        if (!isToolAvailable("ruby", "--version")) return

        val code = """
            def solution(arr)
                puts "debug output"
                arr.map { |x| x * 2 }
            end
        """.trimIndent()

        val tc = TestCase(input = "[1, 2, 3]", expectedOutput = "[2,4,6]")
        val result = CodeRunner.runProgrammers(code, Language.RUBY, tc, listOf("arr"))

        assertEquals(0, result.exitCode, "Exit code should be 0: ${result.error}")
        // 사용자 puts는 stderr로 리다이렉트되고 stdout에는 리턴값만 출력
        assertEquals("[2,4,6]", result.output.trim())
    }

    /**
     * 자식이 대량 입력을 읽지 않고 종료해도 Broken pipe로 실패하면 안 된다 (이슈 #36).
     * 파이프 버퍼(~64KB)를 넘는 입력에서만 재현되므로 ~2MB로 검증.
     */
    @Test
    fun `run with large unconsumed input does not fail with broken pipe`() {
        if (!isPythonAvailable()) return

        val code = """print("ok")"""  // stdin을 전혀 읽지 않음
        val bigInput = buildString(2_000_000) { repeat(250_000) { append("1234567\n") } }
        val tc = TestCase(input = bigInput, expectedOutput = "ok")
        val result = CodeRunner.run(code, Language.PYTHON, tc)

        assertEquals(0, result.exitCode, "Broken pipe로 실패하면 안 됨: ${result.error}")
        assertEquals("ok", result.output.trim())
    }

    /**
     * 대량 출력도 파이프 블로킹 없이 수집되어야 한다 (이슈 #36).
     * 종료 후에 읽는 구조면 자식이 stdout 버퍼를 채우고 블로킹되어 허위 시간 초과가 난다.
     */
    @Test
    fun `run with large output does not falsely time out`() {
        if (!isPythonAvailable()) return

        val code = "for i in range(200000): print(i)"  // ~1.3MB 출력
        val tc = TestCase(input = "", expectedOutput = "")
        val result = CodeRunner.run(code, Language.PYTHON, tc, timeoutSeconds = 10)

        assertFalse(result.timedOut, "대량 출력이 시간 초과로 오판되면 안 됨: ${result.error}")
        assertEquals(0, result.exitCode, "Exit code should be 0: ${result.error}")
        assertTrue(result.output.lines().size >= 200_000, "출력 전체가 수집되어야 함")
    }

    /**
     * 빈 입력이어도 자식 프로세스의 stdin이 닫혀 EOF가 전달되어야 한다.
     * 닫지 않으면 stdin을 읽는 코드가 타임아웃까지 블로킹되어 허위 시간 초과가 발생한다.
     */
    @Test
    fun `run with blank input closes stdin so child sees EOF`() {
        if (!isPythonAvailable()) return

        val code = """
            import sys
            print(len(sys.stdin.read()))
        """.trimIndent()

        val tc = TestCase(input = "", expectedOutput = "0")
        val result = CodeRunner.run(code, Language.PYTHON, tc, timeoutSeconds = 3)

        assertFalse(result.timedOut, "Should not time out on blank input: ${result.error}")
        assertEquals(0, result.exitCode, "Exit code should be 0: ${result.error}")
        assertEquals("0", result.output.trim())
    }
}
