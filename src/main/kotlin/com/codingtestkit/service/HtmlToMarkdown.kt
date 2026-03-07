package com.codingtestkit.service

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object HtmlToMarkdown {

    fun convert(html: String): String {
        val doc = Jsoup.parseBodyFragment(html)
        return processNode(doc.body()).trim()
    }

    private fun processNode(node: Node): String {
        val sb = StringBuilder()
        for (child in node.childNodes()) {
            when (child) {
                is TextNode -> sb.append(child.text())
                is Element -> sb.append(processElement(child))
            }
        }
        return sb.toString()
    }

    private fun processElement(el: Element): String {
        val tag = el.tagName().lowercase()
        val inner = processNode(el)

        return when (tag) {
            "h1" -> "\n# $inner\n\n"
            "h2" -> "\n## $inner\n\n"
            "h3" -> "\n### $inner\n\n"
            "h4" -> "\n#### $inner\n\n"
            "h5" -> "\n##### $inner\n\n"
            "h6" -> "\n###### $inner\n\n"
            "p" -> "$inner\n\n"
            "br" -> "\n"
            "strong", "b" -> "**$inner**"
            "em", "i" -> "*$inner*"
            "code" -> if (el.parent()?.tagName() == "pre") inner else "`$inner`"
            "pre" -> "\n```\n${el.text()}\n```\n\n"
            "ul" -> "\n${processListItems(el, false)}\n"
            "ol" -> "\n${processListItems(el, true)}\n"
            "li" -> inner
            "a" -> "[$inner](${el.attr("href")})"
            "img" -> "\n\n![${el.attr("alt")}](${el.attr("src")})\n\n"
            "table" -> "\n${processTable(el)}\n"
            "blockquote" -> inner.lines().joinToString("\n") { "> $it" } + "\n\n"
            "hr" -> "\n---\n\n"
            "div", "section", "article", "span" -> inner
            "sup" -> "^$inner^"
            "sub" -> "~$inner~"
            else -> inner
        }
    }

    private fun processListItems(el: Element, ordered: Boolean): String {
        val sb = StringBuilder()
        var idx = 1
        for (li in el.children()) {
            if (li.tagName() == "li") {
                val prefix = if (ordered) "${idx++}. " else "- "
                sb.append("$prefix${processNode(li).trim()}\n")
            }
        }
        return sb.toString()
    }

    private fun processTable(table: Element): String {
        val sb = StringBuilder()
        val rows = table.select("tr")
        if (rows.isEmpty()) return ""

        for ((i, row) in rows.withIndex()) {
            val cells = row.select("th, td")
            val line = cells.joinToString(" | ") { processNode(it).trim() }
            sb.append("| $line |\n")

            // 헤더 구분선
            if (i == 0 || row.select("th").isNotEmpty()) {
                val separator = cells.joinToString(" | ") { "---" }
                sb.append("| $separator |\n")
            }
        }
        return sb.toString()
    }
}
