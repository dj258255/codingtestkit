package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.codingtestkit.model.ProblemSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SubmitServiceTest {

    @Test
    fun `submit with blank cookies returns login required`() {
        val result = SubmitService.submit(
            ProblemSource.PROGRAMMERS, "12947", "code", Language.JAVA, ""
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
    fun `submit Codeforces returns unsupported message`() {
        val result = SubmitService.submit(
            ProblemSource.CODEFORCES, "1234A", "code", Language.JAVA, "session=abc"
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("Codeforces"))
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
