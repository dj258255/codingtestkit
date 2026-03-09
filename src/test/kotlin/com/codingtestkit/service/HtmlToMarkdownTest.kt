package com.codingtestkit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HtmlToMarkdownTest {

    // ── 기본 태그 변환 ──

    @Test
    fun `convert paragraph`() {
        val result = HtmlToMarkdown.convert("<p>Hello World</p>")
        assertEquals("Hello World", result)
    }

    @Test
    fun `convert headings`() {
        assertEquals("# Title", HtmlToMarkdown.convert("<h1>Title</h1>").trim())
        assertEquals("## Subtitle", HtmlToMarkdown.convert("<h2>Subtitle</h2>").trim())
        assertEquals("### H3", HtmlToMarkdown.convert("<h3>H3</h3>").trim())
    }

    @Test
    fun `convert bold and italic`() {
        assertTrue(HtmlToMarkdown.convert("<strong>bold</strong>").contains("**bold**"))
        assertTrue(HtmlToMarkdown.convert("<b>bold</b>").contains("**bold**"))
        assertTrue(HtmlToMarkdown.convert("<em>italic</em>").contains("*italic*"))
        assertTrue(HtmlToMarkdown.convert("<i>italic</i>").contains("*italic*"))
    }

    @Test
    fun `convert inline code`() {
        val result = HtmlToMarkdown.convert("<p>Use <code>int x = 1</code> here</p>")
        assertTrue(result.contains("`int x = 1`"))
    }

    @Test
    fun `convert code block in pre`() {
        val result = HtmlToMarkdown.convert("<pre><code>int x = 1;\nint y = 2;</code></pre>")
        assertTrue(result.contains("```"))
        assertTrue(result.contains("int x = 1;"))
    }

    @Test
    fun `convert link`() {
        val result = HtmlToMarkdown.convert("<a href=\"https://example.com\">Click</a>")
        assertEquals("[Click](https://example.com)", result.trim())
    }

    @Test
    fun `convert image`() {
        val result = HtmlToMarkdown.convert("<img src=\"test.png\" alt=\"Test Image\">")
        assertTrue(result.contains("![Test Image](test.png)"))
    }

    @Test
    fun `convert br to newline`() {
        val result = HtmlToMarkdown.convert("Line1<br>Line2")
        assertTrue(result.contains("Line1\nLine2"))
    }

    @Test
    fun `convert hr to horizontal rule`() {
        val result = HtmlToMarkdown.convert("<hr>")
        assertTrue(result.contains("---"))
    }

    // ── 리스트 ──

    @Test
    fun `convert unordered list`() {
        val html = "<ul><li>Item 1</li><li>Item 2</li><li>Item 3</li></ul>"
        val result = HtmlToMarkdown.convert(html)
        assertTrue(result.contains("- Item 1"))
        assertTrue(result.contains("- Item 2"))
        assertTrue(result.contains("- Item 3"))
    }

    @Test
    fun `convert ordered list`() {
        val html = "<ol><li>First</li><li>Second</li><li>Third</li></ol>"
        val result = HtmlToMarkdown.convert(html)
        assertTrue(result.contains("1. First"))
        assertTrue(result.contains("2. Second"))
        assertTrue(result.contains("3. Third"))
    }

    // ── 테이블 ──

    @Test
    fun `convert simple table`() {
        val html = """
            <table>
                <tr><th>Name</th><th>Age</th></tr>
                <tr><td>Alice</td><td>30</td></tr>
                <tr><td>Bob</td><td>25</td></tr>
            </table>
        """.trimIndent()
        val result = HtmlToMarkdown.convert(html)
        assertTrue(result.contains("| Name | Age |"))
        assertTrue(result.contains("| --- | --- |"))
        assertTrue(result.contains("| Alice | 30 |"))
        assertTrue(result.contains("| Bob | 25 |"))
    }

    @Test
    fun `convert table without header`() {
        val html = """
            <table>
                <tr><td>A</td><td>B</td></tr>
                <tr><td>C</td><td>D</td></tr>
            </table>
        """.trimIndent()
        val result = HtmlToMarkdown.convert(html)
        assertTrue(result.contains("| A | B |"))
        assertTrue(result.contains("| C | D |"))
    }

    // ── 블록 요소 ──

    @Test
    fun `convert blockquote`() {
        val result = HtmlToMarkdown.convert("<blockquote>This is a quote</blockquote>")
        assertTrue(result.contains("> This is a quote"))
    }

    @Test
    fun `convert sup and sub`() {
        assertTrue(HtmlToMarkdown.convert("<sup>2</sup>").contains("^2^"))
        assertTrue(HtmlToMarkdown.convert("<sub>i</sub>").contains("~i~"))
    }

    // ── 복합 변환 ──

    @Test
    fun `convert nested elements`() {
        val html = "<p>This is <strong>bold and <em>italic</em></strong> text</p>"
        val result = HtmlToMarkdown.convert(html)
        assertTrue(result.contains("**bold and *italic***"))
    }

    @Test
    fun `convert div and span pass through content`() {
        val result = HtmlToMarkdown.convert("<div><span>Content</span></div>")
        assertTrue(result.contains("Content"))
    }

    @Test
    fun `convert empty HTML`() {
        assertEquals("", HtmlToMarkdown.convert(""))
    }

    @Test
    fun `convert plain text without tags`() {
        assertEquals("Hello", HtmlToMarkdown.convert("Hello"))
    }

    @Test
    fun `convert typical BOJ problem description`() {
        val html = """
            <h2>문제</h2>
            <p>두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.</p>
            <h2>입력</h2>
            <p>첫째 줄에 A와 B가 주어진다. (0 < A, B < 10)</p>
            <h2>출력</h2>
            <p>첫째 줄에 A+B를 출력한다.</p>
            <pre>1 2</pre>
        """.trimIndent()
        val result = HtmlToMarkdown.convert(html)
        assertTrue(result.contains("## 문제"))
        assertTrue(result.contains("두 정수 A와 B를 입력받은"))
        assertTrue(result.contains("## 입력"))
        assertTrue(result.contains("## 출력"))
        assertTrue(result.contains("```"))
        assertTrue(result.contains("1 2"))
    }
}
