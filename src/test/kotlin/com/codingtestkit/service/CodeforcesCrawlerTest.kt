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

    // ── MathJax 렌더링 잔여물 정리 (이슈 #25: JCEF 폴백 경로) ──

    @Test
    fun `MathJax rendered DOM is restored to TeX source`() {
        // JCEF가 가져오는 렌더링된 DOM 구조: 렌더링 span + 원본 TeX script 공존
        val html = """
            <html><body><div class="problem-statement">
              <div class="header"><div class="title">A. Test</div></div>
              <div><p>You are given
                <span class="MathJax_Preview">n</span>
                <span class="MathJax" role="presentation" style="position:relative;"><span>n</span></span>
                <script type="math/tex">n</script>
                vertices.</p></div>
              <div class="sample-tests"><div class="input"><pre>1</pre></div><div class="output"><pre>2</pre></div></div>
            </div></body></html>
        """.trimIndent()

        val problem = CodeforcesCrawler.parseProblemHtml(html, "1A")

        // 렌더링 잔여물이 제거되어 수식이 중복 표시되지 않고, TeX가 $ 구분자로 복원됨
        assertFalse(problem.description.contains("role=\"presentation\""), "MathJax span should be removed")
        assertFalse(problem.description.contains("MathJax"), "MathJax preview should be removed")
        assertTrue(problem.description.contains("${'$'}n${'$'}"), "TeX source should be restored with dollar delimiters")
    }

    @Test
    fun `display math script is restored with double dollar delimiters`() {
        val html = """
            <html><body><div class="problem-statement">
              <div class="header"><div class="title">A. Test</div></div>
              <div><p>Formula:</p>
                <div class="MathJax_Display">rendered</div>
                <script type="math/tex; mode=display">a_i \le 10^6</script>
              </div>
            </div></body></html>
        """.trimIndent()

        val problem = CodeforcesCrawler.parseProblemHtml(html, "1A")

        assertTrue(
            problem.description.contains("${'$'}${'$'}a_i \\le 10^6${'$'}${'$'}"),
            "Display TeX should use double dollar delimiters"
        )
        assertFalse(problem.description.contains("rendered"), "Rendered display div should be removed")
    }

    @Test
    fun `plain HTML without MathJax is unaffected`() {
        val html = """
            <html><body><div class="problem-statement">
              <div class="header"><div class="title">A. Test</div></div>
              <div><p>Given ${'$'}${'$'}${'$'}n${'$'}${'$'}${'$'} vertices.</p></div>
            </div></body></html>
        """.trimIndent()

        val problem = CodeforcesCrawler.parseProblemHtml(html, "1A")

        // Jsoup 경로의 원본 $$$ 구분자는 기존 정규화대로 $ 하나로 변환됨
        assertTrue(problem.description.contains("${'$'}n${'$'}"))
    }
}
