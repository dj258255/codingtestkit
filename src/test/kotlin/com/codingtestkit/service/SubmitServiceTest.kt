package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.codingtestkit.model.ProblemSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SubmitServiceTest {

    // ── parseCookieMap (리플렉션) ──

    private fun callParseCookieMap(cookies: String): Map<String, String> {
        val method = SubmitService::class.java.getDeclaredMethod("parseCookieMap", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(SubmitService, cookies) as Map<String, String>
    }

    @Test
    fun `parseCookieMap parses single cookie`() {
        val result = callParseCookieMap("session=abc123")
        assertEquals(1, result.size)
        assertEquals("abc123", result["session"])
    }

    @Test
    fun `parseCookieMap parses multiple cookies`() {
        val result = callParseCookieMap("session=abc; token=xyz; lang=ko")
        assertEquals(3, result.size)
        assertEquals("abc", result["session"])
        assertEquals("xyz", result["token"])
        assertEquals("ko", result["lang"])
    }

    @Test
    fun `parseCookieMap handles cookie with equals in value`() {
        val result = callParseCookieMap("data=key=value")
        assertEquals(1, result.size)
        assertEquals("key=value", result["data"])
    }

    @Test
    fun `parseCookieMap handles empty cookies`() {
        val result = callParseCookieMap("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseCookieMap trims whitespace`() {
        val result = callParseCookieMap("  session = abc ;  token = xyz  ")
        assertEquals("abc", result["session"])
        assertEquals("xyz", result["token"])
    }

    // ── submit 기본 검증 ──

    @Test
    fun `submit with blank cookies returns login required`() {
        val result = SubmitService.submit(
            ProblemSource.BAEKJOON, "1000", "code", Language.JAVA, ""
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("로그인"))
    }

    @Test
    fun `submit LeetCode returns unsupported message`() {
        val result = SubmitService.submit(
            ProblemSource.LEETCODE, "two-sum", "code", Language.JAVA, "session=abc"
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("LeetCode"))
    }

    @Test
    fun `SWEA submit rejects unsupported language`() {
        // Kotlin은 SWEA에서 sweaId가 -1
        val result = SubmitService.submit(
            ProblemSource.SWEA, "1234", "code", Language.KOTLIN, "session=abc"
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("지원하지 않습니다"))
    }
}
