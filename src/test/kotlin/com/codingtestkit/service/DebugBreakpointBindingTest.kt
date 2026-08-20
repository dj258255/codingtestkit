package com.codingtestkit.service

import com.codingtestkit.model.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * 브레이크포인트가 사용자 파일에 붙는지 측정 (이슈 #36).
 *
 * 디버거가 브레이크포인트를 붙이는 근거는 하나다 — 런타임(또는 디버그 정보)이
 * 그 함수의 소스 위치를 어느 파일로 해석하는가. 그래서 IDE를 띄우지 않고도,
 * 하네스가 사용자 파일을 불러왔을 때 그 위치가 '사용자 파일'로 해석되는지를
 * 언어별 표준 수단으로 직접 잰다.
 *
 * 여기서 재는 것: 소스 위치 해석. 재지 못하는 것: IDE UI가 실제로 멈추는 장면.
 */
class DebugBreakpointBindingTest {

    private fun toolExists(vararg cmd: String): Boolean = try {
        ProcessBuilder(*cmd).redirectErrorStream(true).start().waitFor() == 0
    } catch (_: Exception) { false }

    private fun exec(dir: File, vararg cmd: String): Pair<Int, String> {
        val p = ProcessBuilder(*cmd).directory(dir).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return p.exitValue() to out.trim()
    }

    private fun loader(language: Language, userFile: File): String =
        CodeRunner.debugLoaderFor(language, userFile)
            ?: fail("$language 로더가 있어야 한다")

    @Test
    fun `python resolves the function to the user file and line`(@TempDir dir: Path) {
        assumeTrue(toolExists("python3", "--version"))
        val user = dir.resolve("solution.py").toFile()
        user.writeText("class Solution:\n    def twoSum(self, nums, target):\n        return [0, 1]\n")

        val probe = dir.resolve("probe.py").toFile()
        probe.writeText(loader(Language.PYTHON, user) + """

import inspect
print(inspect.getsourcefile(Solution.twoSum))
print(inspect.getsourcelines(Solution.twoSum)[1])
""")
        val (exit, out) = exec(dir.toFile(), "python3", probe.absolutePath)
        assertEquals(0, exit, out)
        val lines = out.lines()
        assertEquals(user.absolutePath, lines[0], "함수의 소스 파일이 사용자 파일이어야 브레이크포인트가 붙는다")
        assertEquals("2", lines[1], "줄 번호도 사용자 파일 기준이어야 한다")
    }

    @Test
    fun `ruby resolves the method to the user file and line`(@TempDir dir: Path) {
        assumeTrue(toolExists("ruby", "--version"))
        val user = dir.resolve("solution.rb").toFile()
        user.writeText("def two_sum(nums, target)\n  [0, 1]\nend\n")

        val probe = dir.resolve("probe.rb").toFile()
        probe.writeText(loader(Language.RUBY, user) + "\nputs method(:two_sum).source_location[0]\nputs method(:two_sum).source_location[1]\n")
        val (exit, out) = exec(dir.toFile(), "ruby", probe.absolutePath)
        assertEquals(0, exit, out)
        val lines = out.lines()
        assertEquals(user.absolutePath, lines[0])
        assertEquals("1", lines[1])
    }

    @Test
    fun `javascript resolves the function to the user file and line`(@TempDir dir: Path) {
        assumeTrue(toolExists("node", "--version"))
        val user = dir.resolve("solution.js").toFile()
        user.writeText("var twoSum = function(nums, target) {\n  throw new Error('probe');\n};\n")

        val probe = dir.resolve("probe.js").toFile()
        probe.writeText(loader(Language.JAVASCRIPT, user) + """

try { twoSum([1], 2); } catch (e) { console.log(e.stack.split('\n')[1].trim()); }
""")
        val (exit, out) = exec(dir.toFile(), "node", probe.absolutePath)
        assertEquals(0, exit, out)
        // 예: "at twoSum (/tmp/.../solution.js:2:9)"
        assertTrue(out.contains(user.absolutePath), "스택 프레임이 사용자 파일을 가리켜야 한다: $out")
        assertTrue(out.contains("${user.absolutePath}:2:"), "줄 번호도 보존돼야 한다: $out")
    }

    @Test
    fun `cpp debug info points at the user file`(@TempDir dir: Path) {
        assumeTrue(toolExists("g++", "--version"))
        assumeTrue(toolExists("dwarfdump", "--version"))
        val user = dir.resolve("solution.cpp").toFile()
        user.writeText("#include <vector>\nusing namespace std;\nclass Solution { public: vector<int> twoSum(vector<int>& n, int t) { return {0,1}; } };\n")

        val probe = dir.resolve("probe.cpp").toFile()
        probe.writeText(loader(Language.CPP, user) + "\n#include <iostream>\nint main(){ Solution s; vector<int> a{2,7}; auto r = s.twoSum(a, 9); std::cout << r[0] << r[1]; }\n")

        val obj = dir.resolve("probe.o").toFile()
        val (cExit, cOut) = exec(dir.toFile(), "g++", "-std=c++17", "-g", "-c", probe.absolutePath, "-o", obj.absolutePath)
        assertEquals(0, cExit, cOut)
        val (_, dump) = exec(dir.toFile(), "dwarfdump", "--debug-line", obj.absolutePath)
        assertTrue(dump.contains("solution.cpp"),
            "디버그 정보의 소스 파일 목록에 사용자 파일이 있어야 브레이크포인트가 붙는다")
    }

    @Test
    fun `rust debug info points at the user file`(@TempDir dir: Path) {
        assumeTrue(toolExists("rustc", "--version"))
        assumeTrue(toolExists("dwarfdump", "--version"))
        val user = dir.resolve("solution.rs").toFile()
        user.writeText("pub struct Solution;\nimpl Solution { pub fn two_sum(_n: Vec<i32>, _t: i32) -> Vec<i32> { vec![0,1] } }\n")

        val probe = dir.resolve("probe.rs").toFile()
        probe.writeText(loader(Language.RUST, user) + "\nfn main(){ let r = Solution::two_sum(vec![2,7], 9); println!(\"{:?}\", r); }\n")

        val obj = dir.resolve("probe.o").toFile()
        val (cExit, cOut) = exec(dir.toFile(), "rustc", "-g", "--edition", "2021", "--emit", "obj",
            "-o", obj.absolutePath, probe.absolutePath)
        assertEquals(0, cExit, cOut)
        val (_, dump) = exec(dir.toFile(), "dwarfdump", "--debug-info", obj.absolutePath)
        assertTrue(dump.contains("solution.rs"),
            "디버그 정보가 사용자 파일을 가리켜야 브레이크포인트가 붙는다")
    }
}
