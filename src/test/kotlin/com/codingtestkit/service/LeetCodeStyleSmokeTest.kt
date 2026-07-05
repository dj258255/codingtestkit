package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.codingtestkit.model.TestCase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * 리트코드가 실제로 주는 초기 코드 형태(two_sum/twoSum 네이밍, impl Solution 등)가
 * 프로그래머스식 래퍼(runProgrammers)를 올바르게 통과하는지 검증.
 * 입력은 실제 exampleTestcases 포맷(파라미터별 한 줄)을 사용한다.
 * 해당 툴체인이 없는 환경에서는 조용히 스킵된다.
 */
class LeetCodeStyleSmokeTest {

    private fun available(vararg cmd: String) = try {
        ProcessBuilder(*cmd).start().waitFor() == 0
    } catch (_: Exception) { false }

    // 리트코드 exampleTestcases 포맷: 파라미터별 한 줄
    private val tc = TestCase(input = "[2,7,11,15]\n9", expectedOutput = "[0,1]")

    @Test
    fun `Rust leetcode impl Solution style`() {
        if (!available("rustc", "--version")) return
        // 리트코드 Rust 초기 코드 형태 그대로 (struct Solution 선언 없음 → 래퍼가 보충해야 함)
        val code = """
            impl Solution {
                pub fn two_sum(nums: Vec<i32>, target: i32) -> Vec<i32> {
                    for i in 0..nums.len() {
                        for j in (i + 1)..nums.len() {
                            if nums[i] + nums[j] == target {
                                return vec![i as i32, j as i32];
                            }
                        }
                    }
                    vec![]
                }
            }
        """.trimIndent()
        val result = CodeRunner.runProgrammers(code, Language.RUST, tc, listOf("nums", "target"))
        assertEquals(0, result.exitCode, "compile/run failed: ${result.error}")
        assertEquals("[0,1]", result.output.trim())
    }

    @Test
    fun `Go leetcode twoSum style`() {
        if (!available("go", "version")) return
        // 리트코드 Go 초기 코드 형태 그대로 (package 선언 없음, 함수명 twoSum)
        val code = """
            func twoSum(nums []int, target int) []int {
                for i := 0; i < len(nums); i++ {
                    for j := i + 1; j < len(nums); j++ {
                        if nums[i]+nums[j] == target {
                            return []int{i, j}
                        }
                    }
                }
                return nil
            }
        """.trimIndent()
        val result = CodeRunner.runProgrammers(code, Language.GO, tc, listOf("nums", "target"))
        assertEquals(0, result.exitCode, "compile/run failed: ${result.error}")
        assertEquals("[0,1]", result.output.trim())
    }

    @Test
    fun `Ruby leetcode two_sum style`() {
        if (!available("ruby", "--version")) return
        // 리트코드 Ruby 초기 코드 형태 그대로
        val code = """
            # @param {Integer[]} nums
            # @param {Integer} target
            # @return {Integer[]}
            def two_sum(nums, target)
                nums.each_with_index do |a, i|
                    nums.each_with_index do |b, j|
                        return [i, j] if i < j && a + b == target
                    end
                end
                []
            end
        """.trimIndent()
        val result = CodeRunner.runProgrammers(code, Language.RUBY, tc, listOf("nums", "target"))
        assertEquals(0, result.exitCode, "run failed: ${result.error}")
        assertEquals("[0,1]", result.output.trim())
    }

    @Test
    fun `String and 2D array literal conversion`() {
        if (!available("go", "version")) return
        // 문자열 파라미터 + 문자열 리턴
        val code = """
            func greet(name string) string {
                return "hi " + name
            }
        """.trimIndent()
        val strTc = TestCase(input = "\"world\"", expectedOutput = "\"hi world\"")
        val result = CodeRunner.runProgrammers(code, Language.GO, strTc, listOf("name"))
        assertEquals(0, result.exitCode, "run failed: ${result.error}")
        assertEquals("\"hi world\"", result.output.trim())

        // 2차원 배열 파라미터
        val code2 = """
            func sumAll(grid [][]int) int {
                s := 0
                for _, row := range grid {
                    for _, v := range row {
                        s += v
                    }
                }
                return s
            }
        """.trimIndent()
        val gridTc = TestCase(input = "[[1,2],[3,4]]", expectedOutput = "10")
        val result2 = CodeRunner.runProgrammers(code2, Language.GO, gridTc, listOf("grid"))
        assertEquals(0, result2.exitCode, "run failed: ${result2.error}")
        assertEquals("10", result2.output.trim())
    }
}
