package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object ProgrammersCrawler {

    private const val BASE_URL = "https://school.programmers.co.kr/learn/courses/30/lessons/"
    private val gson = Gson()

    fun fetchProblem(lessonId: String, cookies: String): Problem {
        val doc = Jsoup.connect("$BASE_URL$lessonId")
            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Cookie", cookies)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
            .header("Referer", "https://school.programmers.co.kr/learn/challenges")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "same-origin")
            .followRedirects(true)
            .timeout(10000)
            .get()

        val title = extractTitle(doc)
        val description = extractDescription(doc)
        val (paramNames, testCases) = extractTestCasesWithParams(doc)
        val initialCode = extractInitialCode(doc)
        val difficulty = extractDifficulty(doc)

        // 초기 코드가 없으면 파라미터/테스트 케이스에서 추론하여 생성
        val finalCode = if (initialCode.isNotBlank()) {
            initialCode
        } else if (paramNames.isNotEmpty() && testCases.isNotEmpty()) {
            buildInitialCodeFromParams(paramNames, testCases)
        } else ""

        return Problem(
            source = ProblemSource.PROGRAMMERS,
            id = lessonId,
            title = title,
            description = description,
            testCases = testCases,
            parameterNames = paramNames,
            initialCode = finalCode,
            difficulty = difficulty
        )
    }

    private fun extractTitle(doc: Document): String {
        // 여러 셀렉터 시도
        val selectors = listOf(
            "ol.breadcrumb li.active",
            ".challenge-title h3",
            ".lesson-title"
        )
        for (sel in selectors) {
            val text = doc.select(sel).text()
            if (text.isNotBlank()) return text
        }
        return doc.select("title").text()
            .replace(" | 프로그래머스 스쿨", "")
            .replace("코딩테스트 연습 - ", "")
    }

    private fun extractDescription(doc: Document): String {
        val selectors = listOf(
            ".guide-section-description .markdown",
            ".challenge-description .markdown",
            "#tour2 .description",
            ".description .markdown",
            ".description"
        )

        for (selector in selectors) {
            val element = doc.select(selector)
            if (element.isNotEmpty() && element.html().length > 20) {
                return element.html()
            }
        }

        // script 태그에서 JSON 데이터 추출 시도
        return extractDescriptionFromScript(doc)
            ?: "<p>문제 설명을 가져올 수 없습니다. 로그인 상태를 확인해주세요.</p>"
    }

    private fun extractDescriptionFromScript(doc: Document): String? {
        for (script in doc.select("script")) {
            val data = script.data()

            // Next.js __NEXT_DATA__ 패턴
            if (data.contains("__NEXT_DATA__") || data.contains("\"description\"")) {
                try {
                    val jsonStr = if (data.contains("__NEXT_DATA__")) {
                        data.substringAfter("__NEXT_DATA__ = ").substringBefore("</script>")
                    } else {
                        data
                    }
                    val json = gson.fromJson(jsonStr, JsonObject::class.java)
                    val desc = findJsonValue(json, "description")
                    if (desc != null && desc.length > 20) {
                        return desc.replace("\\n", "\n").replace("\\\"", "\"")
                    }
                } catch (_: Exception) {
                    // JSON 파싱 실패 시 regex fallback
                    val match = Regex("\"description\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(data)
                    if (match != null) {
                        return match.groupValues[1]
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\/", "/")
                    }
                }
            }
        }
        return null
    }

    /**
     * 입출력 예시 테이블에서 파라미터명과 테스트 케이스를 추출
     * 프로그래머스 테이블 형식:
     * | s       | n   | result  |
     * | "hello" | 1   | "elhlo" |
     */
    private fun extractTestCasesWithParams(doc: Document): Pair<List<String>, MutableList<TestCase>> {
        val testCases = mutableListOf<TestCase>()
        var paramNames = listOf<String>()

        // "입출력 예" 헤더 바로 뒤의 테이블을 찾기
        val ioTable = findIOExampleTable(doc)
        if (ioTable != null) {
            val result = parseIOTable(ioTable)
            paramNames = result.first
            testCases.addAll(result.second)
        }

        // 테이블을 못 찾았으면 모든 테이블에서 시도
        if (testCases.isEmpty()) {
            for (table in doc.select("table")) {
                val result = parseIOTable(table)
                if (result.second.isNotEmpty()) {
                    paramNames = result.first
                    testCases.addAll(result.second)
                    break
                }
            }
        }

        return Pair(paramNames, testCases)
    }

    /**
     * "입출력 예" 제목 바로 다음에 오는 테이블 찾기
     */
    private fun findIOExampleTable(doc: Document): Element? {
        // h5, h4, h3 태그에서 "입출력 예" 찾기
        val headers = doc.select("h5, h4, h3, h2, p strong")
        for (header in headers) {
            val text = header.text().trim()
            if (text.contains("입출력 예") && !text.contains("설명")) {
                // 다음 형제 요소에서 테이블 찾기
                var sibling = header.nextElementSibling()
                while (sibling != null) {
                    if (sibling.tagName() == "table") return sibling
                    if (sibling.select("table").isNotEmpty()) return sibling.select("table").first()
                    // 다른 헤더를 만나면 중단
                    if (sibling.tagName().matches(Regex("h[1-6]"))) break
                    sibling = sibling.nextElementSibling()
                }
            }
        }
        return null
    }

    /**
     * 테이블에서 파라미터명과 테스트 케이스 파싱
     */
    private fun parseIOTable(table: Element): Pair<List<String>, List<TestCase>> {
        val testCases = mutableListOf<TestCase>()

        // 헤더 추출
        val headers = table.select("thead th, thead td").map { it.text().trim() }
        if (headers.isEmpty()) return Pair(emptyList(), emptyList())

        // result/return 컬럼 찾기
        val resultIdx = headers.indexOfLast {
            it.equals("result", ignoreCase = true) ||
            it.equals("return", ignoreCase = true) ||
            it.equals("answer", ignoreCase = true) ||
            it.equals("결과", ignoreCase = true)
        }
        if (resultIdx < 0) return Pair(emptyList(), emptyList())

        // 파라미터명 = result 컬럼 제외한 나머지 헤더
        val paramNames = headers.filterIndexed { idx, _ -> idx != resultIdx }

        // 각 행 파싱
        val rows = table.select("tbody tr")
        for (row in rows) {
            val cells = row.select("td")
            if (cells.size != headers.size) continue

            // 입력 파라미터들을 각각 분리해서 저장
            val inputs = cells.filterIndexed { idx, _ -> idx != resultIdx }
                .joinToString("\n") { it.text().trim() }
            val expected = cells[resultIdx].text().trim()

            testCases.add(TestCase(input = inputs, expectedOutput = expected))
        }

        return Pair(paramNames, testCases)
    }

    private fun stripSurroundingQuotes(value: String): String {
        return if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
            value.substring(1, value.length - 1)
        } else value
    }

    /**
     * 초기 코드 (solution 함수 시그니처) 추출
     */
    private fun extractInitialCode(doc: Document): String {
        // code 태그나 pre 태그에서 solution 함수 찾기
        for (code in doc.select("code, pre")) {
            val text = code.text()
            if (text.contains("solution") && (text.contains("def ") || text.contains("class ") ||
                        text.contains("function ") || text.contains("fun "))) {
                return text
            }
        }

        // script 태그의 JSON에서 초기 코드 추출
        val codeKeys = listOf("code", "solution_code", "default_code", "initial_code", "initialCode")
        for (script in doc.select("script")) {
            val data = script.data()
            for (key in codeKeys) {
                if (data.contains("\"$key\"")) {
                    try {
                        val codeMatch = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                            .find(data)
                        if (codeMatch != null) {
                            val extracted = codeMatch.groupValues[1]
                                .replace("\\n", "\n")
                                .replace("\\t", "\t")
                                .replace("\\\"", "\"")
                            // Java 코드인 경우만 반환
                            if (extracted.contains("class Solution") || extracted.contains("public")) {
                                return extracted
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // JSON 배열 형태의 코드 목록에서 Java 찾기
            if (data.contains("class Solution") && data.contains("public")) {
                try {
                    val javaCodeMatch = Regex("\"(class Solution\\s*\\{(?:[^\"\\\\]|\\\\.)*)\"")
                        .find(data)
                    if (javaCodeMatch != null) {
                        return javaCodeMatch.groupValues[1]
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                            .replace("\\\"", "\"")
                    }
                } catch (_: Exception) {}
            }
        }

        return ""
    }

    private fun extractDifficulty(doc: Document): String {
        // 전체 페이지 텍스트에서 Lv. 패턴 검색
        val levelPattern = Regex("Lv\\.?\\s*(\\d)")

        // 1. 브레드크럼
        for (li in doc.select("ol.breadcrumb li, .breadcrumb li, .algorithm-nav li, nav li")) {
            val match = levelPattern.find(li.text())
            if (match != null) return "Level${match.groupValues[1]}"
        }

        // 2. 챌린지 타이틀, 헤더 영역
        for (sel in listOf(".challenge-title", ".lesson-title", "h2", "h3", ".title")) {
            val match = levelPattern.find(doc.select(sel).text())
            if (match != null) return "Level${match.groupValues[1]}"
        }

        // 3. 레벨 배지
        for (sel in listOf("[class*=level]", "[class*=badge]", "span.badge")) {
            val match = levelPattern.find(doc.select(sel).text())
            if (match != null) return "Level${match.groupValues[1]}"
        }

        // 4. title 태그
        val titleMatch = levelPattern.find(doc.select("title").text())
        if (titleMatch != null) return "Level${titleMatch.groupValues[1]}"

        // 5. script 태그의 JSON에서 level 필드 추출
        for (script in doc.select("script")) {
            val data = script.data()
            // "level": 1 또는 "difficulty": "Level 1" 패턴
            val jsonMatch = Regex("\"level\"\\s*:\\s*(\\d)").find(data)
            if (jsonMatch != null) return "Level${jsonMatch.groupValues[1]}"
            val diffMatch = Regex("\"difficulty\"\\s*:\\s*\"?(?:Level\\s*)?(\\d)").find(data)
            if (diffMatch != null) return "Level${diffMatch.groupValues[1]}"
        }

        // 6. 전체 HTML에서 마지막 시도
        val bodyMatch = levelPattern.find(doc.body().text())
        if (bodyMatch != null) return "Level${bodyMatch.groupValues[1]}"

        return "Unrated"
    }

    /**
     * 테스트 케이스 값에서 Java 타입 추론
     */
    private fun inferJavaType(value: String): String {
        val v = value.trim()
        if (v.startsWith("\"") && v.endsWith("\"")) return "String"
        if (v == "true" || v == "false") return "boolean"
        if (v.startsWith("[[")) {
            if (v.contains("\"")) return "String[][]"
            return "int[][]"
        }
        if (v.startsWith("[")) {
            if (v.contains("\"")) return "String[]"
            if (v.contains(".")) return "double[]"
            if (v.contains("true") || v.contains("false")) return "boolean[]"
            return "int[]"
        }
        if (v.contains(".") && v.toDoubleOrNull() != null) return "double"
        if (v.toLongOrNull() != null) {
            val num = v.toLong()
            return if (num > Int.MAX_VALUE || num < Int.MIN_VALUE) "long" else "int"
        }
        return "String"
    }

    private fun defaultValue(type: String): String = when (type) {
        "String" -> "\"\"" ; "int" -> "0" ; "long" -> "0L"
        "double" -> "0.0" ; "boolean" -> "false"
        else -> if (type.endsWith("[]")) "{}" else "null"
    }

    /**
     * 파라미터명 + 테스트 케이스에서 Java 초기 코드 생성
     */
    private fun buildInitialCodeFromParams(paramNames: List<String>, testCases: List<TestCase>): String {
        val firstTC = testCases.first()
        val inputValues = firstTC.input.split("\n").map { it.trim() }

        // 파라미터 타입 추론
        val paramTypes = inputValues.mapIndexed { i, v ->
            if (i < paramNames.size) inferJavaType(v) else "int"
        }

        // 반환 타입 추론 (expectedOutput에서 따옴표가 이미 제거된 상태)
        val expectedRaw = firstTC.expectedOutput.trim()
        // 원본 테이블에서 따옴표가 있었는지 확인 (stripSurroundingQuotes로 제거됨)
        // 따옴표가 제거된 결과가 숫자/배열/불린이 아니면 String
        val returnType = if (expectedRaw.startsWith("[")) {
            inferJavaType(expectedRaw)
        } else if (expectedRaw == "true" || expectedRaw == "false") {
            "boolean"
        } else if (expectedRaw.toLongOrNull() != null) {
            val num = expectedRaw.toLong()
            if (num > Int.MAX_VALUE || num < Int.MIN_VALUE) "long" else "int"
        } else if (expectedRaw.toDoubleOrNull() != null) {
            "double"
        } else {
            "String" // 따옴표가 있었으므로 String
        }

        val params = paramNames.zip(paramTypes).joinToString(", ") { (name, type) -> "$type $name" }
        val defVal = defaultValue(returnType)

        return """class Solution {
    public $returnType solution($params) {
        $returnType answer = $defVal;
        return answer;
    }
}"""
    }

    /**
     * JSON 객체에서 특정 키의 값을 재귀적으로 찾기
     */
    private fun findJsonValue(json: JsonObject, key: String): String? {
        if (json.has(key) && json.get(key).isJsonPrimitive) {
            return json.get(key).asString
        }
        for ((_, value) in json.entrySet()) {
            if (value.isJsonObject) {
                val found = findJsonValue(value.asJsonObject, key)
                if (found != null) return found
            }
        }
        return null
    }
}
