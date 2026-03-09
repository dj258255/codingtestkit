package com.codingtestkit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TranslateServiceTest {

    // ── detectLanguage ──

    @Test
    fun `detect Korean text`() {
        assertEquals("ko", TranslateService.detectLanguage("두 정수 A와 B를 입력받은 다음"))
    }

    @Test
    fun `detect English text`() {
        assertEquals("en", TranslateService.detectLanguage("Given two integers A and B, print A+B"))
    }

    @Test
    fun `detect mixed text with majority Korean`() {
        assertEquals("ko", TranslateService.detectLanguage("첫째 줄에 A와 B가 주어진다"))
    }

    @Test
    fun `detect mixed text with majority English`() {
        assertEquals("en", TranslateService.detectLanguage("The input is A and B, print the sum 입력"))
    }

    @Test
    fun `detect empty text as English`() {
        assertEquals("en", TranslateService.detectLanguage(""))
    }

    @Test
    fun `detect pure numbers as English`() {
        assertEquals("en", TranslateService.detectLanguage("12345"))
    }

    @Test
    fun `detect code-like text as English`() {
        assertEquals("en", TranslateService.detectLanguage("int main() { return 0; }"))
    }

    @Test
    fun `detect Korean consonants and vowels`() {
        // 한글 자모도 한국어로 인식해야 함
        assertEquals("ko", TranslateService.detectLanguage("ㅎㅎㅎㅎㅎㅎㅎㅎㅎㅎ"))
    }

    // ── splitHtml (리플렉션 테스트) ──

    @Test
    fun `splitHtml divides long HTML at tag boundaries`() {
        val method = TranslateService::class.java.getDeclaredMethod(
            "splitHtml", String::class.java, Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val html = "<p>Short</p><p>Another paragraph</p><p>Third paragraph with more text</p>"
        @Suppress("UNCHECKED_CAST")
        val chunks = method.invoke(TranslateService, html, 30) as List<String>

        assertTrue(chunks.size > 1, "Should split into multiple chunks")
        assertEquals(html, chunks.joinToString(""), "Rejoined chunks should equal original")
    }

    @Test
    fun `splitHtml does not split short text`() {
        val method = TranslateService::class.java.getDeclaredMethod(
            "splitHtml", String::class.java, Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val html = "<p>Short</p>"
        @Suppress("UNCHECKED_CAST")
        val chunks = method.invoke(TranslateService, html, 5000) as List<String>

        assertEquals(1, chunks.size)
        assertEquals(html, chunks[0])
    }
}
