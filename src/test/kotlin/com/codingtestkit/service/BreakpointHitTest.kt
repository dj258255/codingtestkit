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
 * 브레이크포인트가 '실제로 멈추는지' 검증 (이슈 #36).
 *
 * 디버거가 브레이크포인트에서 멈추는 원리는 (파일 경로, 줄 번호)에 트레이스 훅을 걸고
 * 그 줄이 실행될 때 제어를 넘겨받는 것이다. IDE UI 없이도 같은 훅을 직접 걸어보면
 * "그 줄에서 멈출 수 있는가"는 그대로 측정된다 — pydevd·rdbg가 쓰는 바로 그 메커니즘이다.
 *
 * 앞선 DebugBreakpointBindingTest가 '소스 위치가 사용자 파일로 해석되는가'를 봤다면,
 * 여기서는 한 걸음 더 가서 '그 줄에서 실행이 실제로 잡히는가'와 '그때 지역 변수가
 * 보이는가'까지 확인한다.
 */
class BreakpointHitTest {

    private fun toolExists(vararg cmd: String): Boolean = try {
        ProcessBuilder(*cmd).redirectErrorStream(true).start().waitFor() == 0
    } catch (_: Exception) { false }

    private fun exec(dir: File, vararg cmd: String): Pair<Int, String> {
        val p = ProcessBuilder(*cmd).directory(dir).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return p.exitValue() to out.trim()
    }

    private val twoSum = TestCase(input = "[2,7,11,15]\n9", expectedOutput = "[0,1]")

    @Test
    fun `python breakpoint on the user file is hit with the case arguments`(@TempDir dir: Path) {
        assumeTrue(toolExists("python3", "--version"))

        val user = dir.resolve("Solution.py").toFile()
        user.writeText(
            """
            class Solution:
                def twoSum(self, nums, target):
                    seen = {}
                    for i, v in enumerate(nums):
                        need = target - v
                        if need in seen:
                            return [seen[need], i]
                        seen[v] = i
                    return []
            """.trimIndent()
        )
        val breakLine = 5   // need = target - v

        val harness = CodeRunner.buildDebugHarness(
            user.readText(), Language.PYTHON, twoSum, listOf("nums", "target"), user
        ) ?: fail("하네스가 생성돼야 한다")
        val harnessFile = dir.resolve("ctk_debug_Solution.py").toFile().apply { writeText(harness) }

        // 디버거가 하는 일을 그대로 재현: (파일, 줄)에 훅을 걸고 하네스를 실행한다
        val probe = dir.resolve("probe.py").toFile()
        probe.writeText(
            """
            import sys, json, runpy
            USER = ${'"'}${'"'}${'"'}${user.absolutePath}${'"'}${'"'}${'"'}
            LINE = $breakLine
            hits = []
            def tracer(frame, event, arg):
                if event == 'line' and frame.f_code.co_filename == USER and frame.f_lineno == LINE:
                    hits.append({'nums': frame.f_locals.get('nums'), 'target': frame.f_locals.get('target')})
                return tracer
            sys.settrace(tracer)
            try:
                runpy.run_path(${'"'}${'"'}${'"'}${harnessFile.absolutePath}${'"'}${'"'}${'"'}, run_name='__main__')
            finally:
                sys.settrace(None)
            print('CTK_HITS=' + json.dumps(hits))
            """.trimIndent()
        )

        val (exit, out) = exec(dir.toFile(), "python3", probe.absolutePath)
        assertEquals(0, exit, out)

        val payload = out.lines().first { it.startsWith("CTK_HITS=") }.removePrefix("CTK_HITS=")
        assertNotEquals("[]", payload,
            "사용자 파일 $breakLine 번 줄에 건 브레이크포인트가 한 번도 잡히지 않았다 — 디버거도 멈추지 못한다")
        assertTrue(payload.contains("\"nums\": [2, 7, 11, 15]"), "멈춘 지점에서 케이스 인자가 보여야 한다: $payload")
        assertTrue(payload.contains("\"target\": 9"), "멈춘 지점에서 target이 보여야 한다: $payload")
    }

    @Test
    fun `ruby breakpoint on the user file is hit with the case arguments`(@TempDir dir: Path) {
        assumeTrue(toolExists("ruby", "--version"))

        val user = dir.resolve("solution.rb").toFile()
        user.writeText(
            """
            def two_sum(nums, target)
              seen = {}
              nums.each_with_index do |v, i|
                need = target - v
                return [seen[need], i] if seen.key?(need)
                seen[v] = i
              end
              []
            end
            """.trimIndent()
        )
        val breakLine = 4   // need = target - v

        val harness = CodeRunner.buildDebugHarness(
            user.readText(), Language.RUBY, twoSum, listOf("nums", "target"), user
        ) ?: fail("하네스가 생성돼야 한다")
        val harnessFile = dir.resolve("ctk_debug_solution.rb").toFile().apply { writeText(harness) }

        val probe = dir.resolve("probe.rb").toFile()
        probe.writeText(
            """
            hits = []
            tp = TracePoint.new(:line) do |t|
              if t.path == ${'"'}${user.absolutePath}${'"'} && t.lineno == $breakLine
                b = t.binding
                hits << [b.local_variable_get(:nums), b.local_variable_get(:target)]
              end
            end
            tp.enable
            load ${'"'}${harnessFile.absolutePath}${'"'}
            tp.disable
            STDOUT.puts "CTK_HITS=#{hits.length} #{hits.first.inspect}"
            """.trimIndent()
        )

        val (exit, out) = exec(dir.toFile(), "ruby", probe.absolutePath)
        assertEquals(0, exit, out)

        val line = out.lines().first { it.startsWith("CTK_HITS=") }
        assertFalse(line.startsWith("CTK_HITS=0"),
            "사용자 파일 $breakLine 번 줄에 건 브레이크포인트가 한 번도 잡히지 않았다: $out")
        assertTrue(line.contains("[2, 7, 11, 15]"), "멈춘 지점에서 케이스 인자가 보여야 한다: $line")
        assertTrue(line.contains("9"), "멈춘 지점에서 target이 보여야 한다: $line")
    }
}
