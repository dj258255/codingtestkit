package com.codingtestkit.service

import com.google.gson.JsonParser
import org.jsoup.Jsoup

object SolvedAcApi {

    private const val BASE_URL = "https://solved.ac/api/v3"

    data class ProblemInfo(
        val problemId: Int,
        val title: String,
        val titleEn: String,
        val level: Int,
        val tags: List<String>,
        val tagsEn: List<String>,
        val acceptedUserCount: Int
    )

    data class SearchResult(
        val problems: List<ProblemInfo>,
        val totalCount: Int
    )

    /**
     * 문제 검색 (solved.ac /search/problem)
     */
    fun searchProblems(
        query: String,
        sort: String = "id",
        direction: String = "asc",
        page: Int = 1
    ): SearchResult {
        val json = Jsoup.connect("$BASE_URL/search/problem")
            .data("query", query)
            .data("sort", sort)
            .data("direction", direction)
            .data("page", page.toString())
            .ignoreContentType(true)
            .userAgent("Mozilla/5.0")
            .timeout(10000)
            .get()
            .body()
            .text()

        val root = JsonParser.parseString(json).asJsonObject
        val count = root.get("count")?.asInt ?: 0
        val items = root.getAsJsonArray("items") ?: return SearchResult(emptyList(), 0)

        val problems = items.map { parseProblem(it.asJsonObject) }
        return SearchResult(problems, count)
    }

    /**
     * 랜덤 문제 뽑기
     */
    fun searchRandomProblems(
        tierQuery: String?,
        tagKey: String?,
        count: Int
    ): List<ProblemInfo> {
        val queryParts = mutableListOf("solvable:true")
        if (!tierQuery.isNullOrBlank()) queryParts.add(tierQuery)
        if (!tagKey.isNullOrBlank()) queryParts.add("tag:$tagKey")

        val result = searchProblems(
            query = queryParts.joinToString(" "),
            sort = "random"
        )
        return result.problems.take(count)
    }

    /**
     * 자동완성 제안 (solved.ac /search/suggestion)
     */
    fun searchSuggestions(query: String): List<ProblemInfo> {
        if (query.isBlank()) return emptyList()

        val json = Jsoup.connect("$BASE_URL/search/suggestion")
            .data("query", query)
            .ignoreContentType(true)
            .userAgent("Mozilla/5.0")
            .timeout(5000)
            .get()
            .body()
            .text()

        val root = JsonParser.parseString(json).asJsonObject
        val problems = root.getAsJsonArray("problems") ?: return emptyList()

        return problems.map { parseProblem(it.asJsonObject) }
    }

    private fun parseProblem(obj: com.google.gson.JsonObject): ProblemInfo {
        val tagsKo = obj.getAsJsonArray("tags")?.mapNotNull { tagEl ->
            tagEl.asJsonObject.getAsJsonArray("displayNames")
                ?.firstOrNull { it.asJsonObject.get("language").asString == "ko" }
                ?.asJsonObject?.get("short")?.asString
        } ?: emptyList()

        val tagsEn = obj.getAsJsonArray("tags")?.mapNotNull { tagEl ->
            tagEl.asJsonObject.getAsJsonArray("displayNames")
                ?.firstOrNull { it.asJsonObject.get("language").asString == "en" }
                ?.asJsonObject?.get("short")?.asString
        } ?: emptyList()

        val titleKo = obj.get("titleKo")?.asString ?: ""
        val titleEn = obj.get("titleEn")?.asString ?: ""

        return ProblemInfo(
            problemId = obj.get("problemId").asInt,
            title = stripLatex(titleKo),
            titleEn = stripLatex(titleEn),
            level = obj.get("level")?.asInt ?: 0,
            tags = tagsKo,
            tagsEn = tagsEn,
            acceptedUserCount = obj.get("acceptedUserCount")?.asInt ?: 0
        )
    }

    /** LaTeX 수식($...$)을 평문으로 변환 */
    fun stripLatex(text: String): String {
        // $...$ 내부의 LaTeX를 평문으로 변환
        return text.replace(Regex("\\$([^$]+)\\$")) { match ->
            var s = match.groupValues[1]
            // 공통 LaTeX 명령어 → 유니코드/텍스트
            s = s.replace("\\times", "×")
            s = s.replace("\\cdot", "·")
            s = s.replace("\\div", "÷")
            s = s.replace("\\pm", "±")
            s = s.replace("\\leq", "≤")
            s = s.replace("\\geq", "≥")
            s = s.replace("\\neq", "≠")
            s = s.replace("\\le", "≤")
            s = s.replace("\\ge", "≥")
            s = s.replace("\\ne", "≠")
            s = s.replace("\\lt", "<")
            s = s.replace("\\gt", ">")
            s = s.replace("\\infty", "∞")
            s = s.replace("\\sum", "Σ")
            s = s.replace("\\prod", "Π")
            s = s.replace("\\sqrt", "√")
            s = s.replace("\\log", "log")
            s = s.replace("\\sin", "sin")
            s = s.replace("\\cos", "cos")
            s = s.replace("\\tan", "tan")
            s = s.replace("\\lfloor", "⌊")
            s = s.replace("\\rfloor", "⌋")
            s = s.replace("\\lceil", "⌈")
            s = s.replace("\\rceil", "⌉")
            s = s.replace("\\leftarrow", "←")
            s = s.replace("\\rightarrow", "→")
            s = s.replace("\\Leftarrow", "⇐")
            s = s.replace("\\Rightarrow", "⇒")
            s = s.replace("\\land", "∧")
            s = s.replace("\\lor", "∨")
            s = s.replace("\\lnot", "¬")
            s = s.replace("\\oplus", "⊕")
            s = s.replace("\\alpha", "α")
            s = s.replace("\\beta", "β")
            s = s.replace("\\pi", "π")
            s = s.replace("\\theta", "θ")
            s = s.replace("\\sigma", "σ")
            // 위첨자 ^{...} → 유니코드 위첨자
            s = s.replace(Regex("\\^\\{([^}]+)\\}")) { m -> toSuperscript(m.groupValues[1]) }
            s = s.replace(Regex("\\^(\\d)")) { m -> toSuperscript(m.groupValues[1]) }
            // 아래첨자 _{...} → 유니코드 아래첨자
            s = s.replace(Regex("_\\{([^}]+)\\}")) { m -> toSubscript(m.groupValues[1]) }
            s = s.replace(Regex("_(\\w)")) { m -> toSubscript(m.groupValues[1]) }
            // \frac{a}{b} → a/b
            s = s.replace(Regex("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}")) { m -> "${m.groupValues[1]}/${m.groupValues[2]}" }
            // \binom{n}{k} → C(n,k)
            s = s.replace(Regex("\\\\binom\\{([^}]+)\\}\\{([^}]+)\\}")) { m -> "C(${m.groupValues[1]},${m.groupValues[2]})" }
            // \text{...}, \mathrm{...}, \mathit{...} → 내용만
            s = s.replace(Regex("\\\\(?:text|mathrm|mathit|mathbf|texttt)\\{([^}]+)\\}")) { m -> m.groupValues[1] }
            // 남은 \command → 제거
            s = s.replace(Regex("\\\\[a-zA-Z]+"), "")
            // 중괄호 제거
            s = s.replace("{", "").replace("}", "")
            // 연속 공백 정리
            s = s.replace(Regex("\\s+"), " ").trim()
            s
        }
    }

    private val superscriptMap = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ'
    )

    private val subscriptMap = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
        'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ',
        't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ'
    )

    private fun toSuperscript(s: String): String = s.map { superscriptMap[it] ?: it }.joinToString("")
    private fun toSubscript(s: String): String = s.map { subscriptMap[it] ?: it }.joinToString("")

    fun levelToString(level: Int): String {
        if (level == 0) return "Unrated"
        val tiers = arrayOf("Bronze", "Silver", "Gold", "Platinum", "Diamond", "Ruby")
        val ranks = arrayOf("V", "IV", "III", "II", "I")
        val tierIdx = (level - 1) / 5
        val rankIdx = (level - 1) % 5
        return if (tierIdx in tiers.indices) "${tiers[tierIdx]} ${ranks[rankIdx]}" else "Unrated"
    }
}
