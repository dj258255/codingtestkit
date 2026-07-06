package com.codingtestkit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CodeforcesCrawlerTest {

    // ── 문제 ID 파싱 ──

    @Test
    fun `parse plain id`() {
        assertEquals("1234" to "A", CodeforcesCrawler.parseProblemId("1234A"))
    }

    @Test
    fun `parse id with slash and lowercase`() {
        assertEquals("1234" to "B", CodeforcesCrawler.parseProblemId("1234/b"))
    }

    @Test
    fun `parse id with digit suffix like C1`() {
        assertEquals("2042" to "C1", CodeforcesCrawler.parseProblemId("2042C1"))
    }

    @Test
    fun `invalid id throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            CodeforcesCrawler.parseProblemId("abc")
        }
    }

    // ── 쿠키 헤더 병합 (이슈 #21: JCEF 쿠키 + 로그인 쿠키) ──

    @Test
    fun `merge disjoint cookies`() {
        assertEquals(
            "cf_clearance=abc; JSESSIONID=xyz",
            CodeforcesCrawler.mergeCookieHeaders("cf_clearance=abc", "JSESSIONID=xyz")
        )
    }

    @Test
    fun `override side wins on same name`() {
        assertEquals(
            "cf_clearance=login",
            CodeforcesCrawler.mergeCookieHeaders("cf_clearance=jcef", "cf_clearance=login")
        )
    }

    @Test
    fun `blank sides are handled`() {
        assertEquals("a=1", CodeforcesCrawler.mergeCookieHeaders("a=1", ""))
        assertEquals("a=1", CodeforcesCrawler.mergeCookieHeaders("", "a=1"))
        assertEquals("", CodeforcesCrawler.mergeCookieHeaders("", ""))
    }

    @Test
    fun `whitespace and malformed parts are ignored`() {
        assertEquals(
            "a=1; b=2",
            CodeforcesCrawler.mergeCookieHeaders(" a=1 ;  ; noequals ", " b=2 ")
        )
    }

    @Test
    fun `cookie value containing equals sign is preserved`() {
        assertEquals(
            "token=a=b=c",
            CodeforcesCrawler.mergeCookieHeaders("token=a=b=c", "")
        )
    }
}
