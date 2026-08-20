package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.codingtestkit.model.TestCase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * 래퍼형 디버그 하네스 검증 (이슈 #36).
 *
 * 검증 대상은 "하네스가 사용자 파일을 불러와 실행 경로와 같은 답을 내는가"다.
 * 브레이크포인트 바인딩 자체는 IDE가 하는 일이라 여기서 볼 수 없지만, 하네스가
 * 사용자 파일을 '진짜 파일 경로로' 로드한다는 것이 바인딩의 전제이고 — 그 전제가
 * 실제로 성립하는지(=로드해서 함수가 호출되는지)를 여기서 실행으로 확인한다.
 */
class DebugHarnessTest {

    private fun toolExists(vararg cmd: String): Boolean = try {
        ProcessBuilder(*cmd).redirectErrorStream(true).start().waitFor() == 0
    } catch (_: Exception) { false }

    private fun exec(dir: File, vararg cmd: String): Pair<Int, String> {
        val p = ProcessBuilder(*cmd).directory(dir).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return p.exitValue() to out.trim()
    }

    private val twoSumCase = TestCase(input = "[2,7,11,15]\n9", expectedOutput = "[0,1]")

    /** 하네스를 만들어 파일로 쓰고 실행 — 사용자 파일은 그대로 둔다 */
    private fun harnessFile(
        dir: Path, userName: String, code: String, language: Language, harnessName: String
    ): Pair<File, File> {
        val userFile = dir.resolve(userName).toFile().apply { writeText(code) }
        val harness = CodeRunner.buildDebugHarness(code, language, twoSumCase, listOf("nums", "target"), userFile)
        assertNotNull(harness, "$language 하네스가 생성돼야 한다")
        assertTrue(harness!!.contains(userFile.absolutePath),
            "하네스는 사용자 파일을 '실제 경로'로 불러와야 브레이크포인트가 붙는다")
        assertFalse(harness.contains("def twoSum") || harness.contains("var twoSum"),
            "사용자 코드를 복사해 넣으면 안 된다 — 복사본에는 브레이크포인트가 붙지 않는다")
        return userFile to dir.resolve(harnessName).toFile().apply { writeText(harness) }
    }

    @Test
    fun `python harness loads the user file and returns the answer`(@TempDir dir: Path) {
        assumeTrue(toolExists("python3", "--version"))
        val code = """
            class Solution:
                def twoSum(self, nums, target):
                    seen = {}
                    for i, v in enumerate(nums):
                        if target - v in seen:
                            return [seen[target - v], i]
                        seen[v] = i
        """.trimIndent()
        val (_, harness) = harnessFile(dir, "solution.py", code, Language.PYTHON, "ctk_debug.py")
        val (exit, out) = exec(dir.toFile(), "python3", harness.absolutePath)
        assertEquals(0, exit, out)
        assertEquals("[0,1]", out.lines().last().replace(" ", ""))
    }

    @Test
    fun `javascript harness loads the user file and returns the answer`(@TempDir dir: Path) {
        assumeTrue(toolExists("node", "--version"))
        // 리트코드식 최상위 var — require로는 밖으로 나오지 않는 형태
        val code = """
            var twoSum = function(nums, target) {
                const seen = new Map();
                for (let i = 0; i < nums.length; i++) {
                    if (seen.has(target - nums[i])) return [seen.get(target - nums[i]), i];
                    seen.set(nums[i], i);
                }
            };
        """.trimIndent()
        val (_, harness) = harnessFile(dir, "solution.js", code, Language.JAVASCRIPT, "ctk_debug.js")
        val (exit, out) = exec(dir.toFile(), "node", harness.absolutePath)
        assertEquals(0, exit, out)
        assertEquals("[0,1]", out.lines().last().replace(" ", ""))
    }

    @Test
    fun `ruby harness loads the user file and returns the answer`(@TempDir dir: Path) {
        assumeTrue(toolExists("ruby", "--version"))
        val code = """
            def two_sum(nums, target)
              seen = {}
              nums.each_with_index do |v, i|
                return [seen[target - v], i] if seen.key?(target - v)
                seen[v] = i
              end
            end
        """.trimIndent()
        val (_, harness) = harnessFile(dir, "solution.rb", code, Language.RUBY, "ctk_debug.rb")
        val (exit, out) = exec(dir.toFile(), "ruby", harness.absolutePath)
        assertEquals(0, exit, out)
        assertEquals("[0,1]", out.lines().last().replace(" ", ""))
    }

    @Test
    fun `cpp harness includes the user file and returns the answer`(@TempDir dir: Path) {
        assumeTrue(toolExists("g++", "--version"))
        val code = """
            #include <vector>
            #include <unordered_map>
            using namespace std;
            class Solution {
            public:
                vector<int> twoSum(vector<int>& nums, int target) {
                    unordered_map<int,int> seen;
                    for (int i = 0; i < (int)nums.size(); i++) {
                        auto it = seen.find(target - nums[i]);
                        if (it != seen.end()) return {it->second, i};
                        seen[nums[i]] = i;
                    }
                    return {};
                }
            };
        """.trimIndent()
        val (_, harness) = harnessFile(dir, "solution.cpp", code, Language.CPP, "ctk_debug.cpp")
        val bin = dir.resolve("ctk_debug").toFile()
        val (cExit, cOut) = exec(dir.toFile(), "g++", "-std=c++17", "-g", "-o", bin.absolutePath, harness.absolutePath)
        assertEquals(0, cExit, cOut)
        val (exit, out) = exec(dir.toFile(), bin.absolutePath)
        assertEquals(0, exit, out)
        assertEquals("[0,1]", out.lines().last().replace(" ", ""))
    }

    @Test
    fun `rust harness includes the user file and returns the answer`(@TempDir dir: Path) {
        assumeTrue(toolExists("rustc", "--version"))
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
        val (_, harness) = harnessFile(dir, "solution.rs", code, Language.RUST, "ctk_debug.rs")
        val bin = dir.resolve("ctk_debug_bin").toFile()
        val (cExit, cOut) = exec(dir.toFile(), "rustc", "-g", "--edition", "2021", "-o", bin.absolutePath, harness.absolutePath)
        assertEquals(0, cExit, cOut)
        val (exit, out) = exec(dir.toFile(), bin.absolutePath)
        assertEquals(0, exit, out)
        assertEquals("[0,1]", out.lines().last().replace(" ", ""))
    }

    @Test
    fun `stdin-style code needs no harness`(@TempDir dir: Path) {
        val code = "def solve():\n    pass\n\nif __name__ == \"__main__\":\n    solve()"
        val userFile = dir.resolve("s.py").toFile().apply { writeText(code) }
        assertNull(CodeRunner.buildDebugHarness(code, Language.PYTHON, twoSumCase, emptyList(), userFile))
    }
}
