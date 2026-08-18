package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 플랫폼별 기본 템플릿과 문제별 스텁 조합 규칙 검증 (이슈 #35).
 * 특히 리트코드 Solution 클래스 보존과 코드포스 main 중복 방지가 핵심.
 */
class ComposeInitialCodeTest {

    private fun problem(source: ProblemSource, initialCode: String = "") = Problem(
        source = source, id = "1", title = "t", description = "", difficulty = "",
        testCases = mutableListOf(), initialCode = initialCode
    )

    private val leetStub = "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n    }\n}"

    @Test
    fun `템플릿 없으면 문제별 스텁 그대로`() {
        val code = ProblemFileManager.composeInitialCode(null, problem(ProblemSource.LEETCODE, leetStub), Language.JAVA)
        assertEquals(leetStub, code)
    }

    @Test
    fun `템플릿 없고 스텁도 없으면 기본 코드`() {
        val code = ProblemFileManager.composeInitialCode(null, problem(ProblemSource.CODEFORCES), Language.JAVA)
        assertTrue(code.contains("main"), "코드포스 기본 코드에는 main이 있어야")
    }

    @Test
    fun `SOLUTION 자리표시자에 문제별 스텁이 삽입됨`() {
        val template = "import java.util.*;\n// helpers\n{{SOLUTION}}\n// end"
        val code = ProblemFileManager.composeInitialCode(template, problem(ProblemSource.LEETCODE, leetStub), Language.JAVA)
        assertTrue(code.contains("class Solution"), "Solution 클래스가 보존돼야")
        assertTrue(code.contains("// helpers") && code.contains("// end"), "템플릿 내용 유지")
        assertFalse(code.contains("{{SOLUTION}}"), "자리표시자는 치환돼야")
    }

    @Test
    fun `자리표시자 없어도 리트코드 스텁은 템플릿 뒤에 보존됨`() {
        val template = "import java.util.*;\n// my helpers"
        val code = ProblemFileManager.composeInitialCode(template, problem(ProblemSource.LEETCODE, leetStub), Language.JAVA)
        assertTrue(code.startsWith("import java.util.*;"), "템플릿이 먼저")
        assertTrue(code.contains("class Solution"), "Solution 클래스가 반드시 보존돼야 (제출 호환)")
    }

    @Test
    fun `자리표시자가 여러 번이어도 스텁은 한 번만`() {
        val template = "{{SOLUTION}}\n// helpers\n{{SOLUTION}}"
        val code = ProblemFileManager.composeInitialCode(template, problem(ProblemSource.LEETCODE, leetStub), Language.JAVA)
        assertEquals(1, Regex("class Solution").findAll(code).count(), "Solution 클래스가 중복되면 컴파일이 깨진다")
        assertFalse(code.contains("{{SOLUTION}}"), "남은 자리표시자는 제거돼야")
    }

    @Test
    fun `코드포스는 템플릿만 사용 - main 중복 방지`() {
        val template = "#include <bits/stdc++.h>\nint main() { return 0; }"
        val code = ProblemFileManager.composeInitialCode(template, problem(ProblemSource.CODEFORCES), Language.CPP)
        assertEquals(template, code, "스텁 없는 플랫폼은 템플릿 그대로 — 기본 코드를 덧붙이면 main 중복")
    }
}
