package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.intellij.build.events.MessageEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** 언어별 컴파일러 출력 파서 검증 (이슈 #32) */
class BuildOutputPublisherTest {

    @Test
    fun `parse javac diagnostics`() {
        val out = """
            Main.java:9: error: method twoSum in class Solution cannot be applied to given types;
                    Object _result = sol.twoSum(new int[]{1,2});
                                        ^
            Main.java:12: warning: [deprecation] foo() in Bar has been deprecated
            2 errors
        """.trimIndent()
        val d = BuildOutputPublisher.parse(Language.JAVA, out)
        assertEquals(2, d.size)
        assertEquals("Main.java", d[0].fileName)
        assertEquals(9, d[0].line)
        assertEquals(MessageEvent.Kind.ERROR, d[0].severity)
        assertTrue(d[0].message.startsWith("method twoSum"))
        assertEquals(MessageEvent.Kind.WARNING, d[1].severity)
        assertEquals(12, d[1].line)
    }

    @Test
    fun `parse kotlinc diagnostics`() {
        val out = "Solution.kt:3:10: error: unresolved reference: foo"
        val d = BuildOutputPublisher.parse(Language.KOTLIN, out)
        assertEquals(1, d.size)
        assertEquals("Solution.kt", d[0].fileName)
        assertEquals(3, d[0].line)
        assertEquals(10, d[0].column)
    }

    @Test
    fun `parse gcc diagnostics`() {
        val out = """
            solution.cpp:5:10: error: 'cout' was not declared in this scope
            solution.cpp:7:1: warning: control reaches end of non-void function [-Wreturn-type]
        """.trimIndent()
        val d = BuildOutputPublisher.parse(Language.CPP, out)
        assertEquals(2, d.size)
        assertEquals(5, d[0].line)
        assertEquals(MessageEvent.Kind.WARNING, d[1].severity)
    }

    @Test
    fun `parse rustc two-line diagnostics`() {
        val out = """
            error[E0308]: mismatched types
              --> solution.rs:5:9
               |
            5  |     let x: i32 = "hello";
            warning: unused variable: `y`
              --> solution.rs:8:9
        """.trimIndent()
        val d = BuildOutputPublisher.parse(Language.RUST, out)
        assertEquals(2, d.size)
        assertEquals("solution.rs", d[0].fileName)
        assertEquals(5, d[0].line)
        assertEquals(MessageEvent.Kind.ERROR, d[0].severity)
        assertEquals("mismatched types", d[0].message)
        assertEquals(MessageEvent.Kind.WARNING, d[1].severity)
        assertEquals(8, d[1].line)
    }

    @Test
    fun `parse go diagnostics`() {
        val out = "./solution.go:5:2: undefined: fmt.Printlnn"
        val d = BuildOutputPublisher.parse(Language.GO, out)
        assertEquals(1, d.size)
        assertEquals("solution.go", d[0].fileName)
        assertEquals(5, d[0].line)
        assertEquals(2, d[0].column)
    }

    @Test
    fun `parse python syntax error`() {
        // 실제 python3 출력 형식
        val out = """
              File "/tmp/ctk_run_1/solution.py", line 3
                def foo(:
                        ^
            SyntaxError: invalid syntax
        """.trimIndent()
        val d = BuildOutputPublisher.parse(Language.PYTHON, out)
        assertEquals(1, d.size)
        assertEquals("solution.py", d[0].fileName)
        assertEquals(3, d[0].line)
        assertEquals("SyntaxError: invalid syntax", d[0].message)
    }

    @Test
    fun `parse node syntax error`() {
        // 실제 node 출력 형식
        val out = """
            /tmp/ctk_run_1/solution.js:3
            def foo(:
                ^^^

            SyntaxError: Unexpected identifier
        """.trimIndent()
        val d = BuildOutputPublisher.parse(Language.JAVASCRIPT, out)
        assertEquals(1, d.size)
        assertEquals("solution.js", d[0].fileName)
        assertEquals(3, d[0].line)
        assertTrue(d[0].message.startsWith("SyntaxError"))
    }

    @Test
    fun `parse ruby syntax error only`() {
        // 실제 ruby 출력 형식
        val out = "solution.rb:3: syntax error, unexpected ':', expecting ')'"
        val d = BuildOutputPublisher.parse(Language.RUBY, out)
        assertEquals(1, d.size)
        assertEquals(3, d[0].line)
        assertTrue(d[0].message.startsWith("syntax error"))
        // 런타임 에러 형식은 매칭되지 않아야 함
        assertTrue(BuildOutputPublisher.parse(Language.RUBY, "solution.rb:5:in '<main>': divided by 0 (ZeroDivisionError)").isEmpty())
    }

    @Test
    fun `isStartupError gates interpreted languages`() {
        assertTrue(BuildOutputPublisher.isStartupError(Language.PYTHON, "SyntaxError: invalid syntax"))
        assertFalse(BuildOutputPublisher.isStartupError(Language.PYTHON, "IndexError: list index out of range"))
        assertTrue(BuildOutputPublisher.isStartupError(Language.JAVASCRIPT, "SyntaxError: Unexpected token"))
        assertFalse(BuildOutputPublisher.isStartupError(Language.JAVASCRIPT, "TypeError: undefined is not a function"))
        assertTrue(BuildOutputPublisher.isStartupError(Language.RUBY, "solution.rb:1: syntax error, unexpected ':'"))
        assertFalse(BuildOutputPublisher.isStartupError(Language.RUBY, "divided by 0 (ZeroDivisionError)"))
        assertFalse(BuildOutputPublisher.isStartupError(Language.JAVA, "SyntaxError"))
    }
}
