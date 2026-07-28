package com.codingtestkit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger

/** 테스트 입력 생성 DSL 인터프리터 검증 (이슈 #36) */
class TestFormatInterpreterTest {

    private fun gen(src: String, seed: Long? = 42L) = TestFormatInterpreter.generate(src, seed)

    @Test
    fun `literals pass through untouched`() {
        assertEquals("hello world\n2 lines\n", gen("hello world\n2 lines\n"))
    }

    @Test
    fun `const prints and binds`() {
        assertEquals("100\n", gen("const(100, t)\n"))
        // 라벨 재사용: t를 뒤에서 참조
        assertEquals("5 5\n", gen("const(5, t)\nconst(t, u)\n").replace("\n", " ").trim() + "\n")
    }

    @Test
    fun `underscore label binds without printing`() {
        assertEquals("", gen("const(7, _hidden)"))
        assertEquals("7", gen("const(7, _h)rand(_h, _h, v)"))
    }

    @Test
    fun `rand respects range and is reproducible with seed`() {
        val a = gen("repeat(20) {rand(1, 10, x) }", seed = 123L)
        val b = gen("repeat(20) {rand(1, 10, x) }", seed = 123L)
        assertEquals(a, b, "같은 시드는 같은 결과여야 한다")
        a.trim().split(Regex("\\s+")).forEach {
            val v = it.toInt()
            assertTrue(v in 1..10, "값 $v 이 범위 밖")
        }
    }

    @Test
    fun `label reference works as a bound for a later call`() {
        // rand(n, 1000, m) — n을 하한으로 사용
        val out = gen("rand(500, 500, n) rand(n, 1000, m)\n").trim().split(" ")
        assertEquals("500", out[0])
        assertTrue(out[1].toInt() in 500..1000)
    }

    @Test
    fun `arithmetic including floor division`() {
        assertEquals("7", gen("const(3, _a)const(4, _b)rand(_a + _b, _a + _b, s)"))
        assertEquals("12", gen("rand(3 * 4, 3 * 4, v)"))
        assertEquals("3", gen("rand(7 / 2, 7 / 2, v)"))                  // 내림
        assertEquals("-4", gen("rand((0 - 7) / 2, (0 - 7) / 2, v)"))     // 음수도 0이 아닌 바닥 방향
        assertEquals("-3", gen("rand(0 - 7 / 2, 0 - 7 / 2, v)"))         // '/'가 '-'보다 우선
        assertEquals("14", gen("rand((3 + 4) * 2, (3 + 4) * 2, v)"))
    }

    @Test
    fun `array patterns`() {
        assertEquals("1 2 3 4 5", gen("array(5, a, SPACE, INCREASING, 1, 100)"))
        assertEquals("10 9 8", gen("array(3, a, SPACE, DECREASING, 1, 10)"))
        assertEquals("7,7,7", gen("array(3, a, COMMA, CONSTANT, 7, 9)"))
        assertEquals("1\n2\n3", gen("array(3, a, NEWLINE, INCREASING, 1, 100)"))
    }

    @Test
    fun `perm0 and perm1 cover the right ranges`() {
        val p0 = gen("perm0(6, a)").trim().split(" ").map { it.toInt() }.sorted()
        assertEquals((0..5).toList(), p0)
        val p1 = gen("perm1(6, a)").trim().split(" ").map { it.toInt() }.sorted()
        assertEquals((1..6).toList(), p1)
        // array의 PERM0/PERM1 타입도 동일
        val ap1 = gen("array(4, a, SPACE, PERM1)").trim().split(" ").map { it.toInt() }.sorted()
        assertEquals((1..4).toList(), ap1)
    }

    @Test
    fun `string uses alphabet spec`() {
        val s = gen("string(50, s)")
        assertEquals(50, s.length)
        assertTrue(s.all { it in 'a'..'z' })
        val digits = gen("string(30, s, \"0-9\")")
        assertTrue(digits.all { it.isDigit() }, "0-9 알파벳이 적용되어야 한다: $digits")
        val ab = gen("string(20, s, \"ab\")")
        assertTrue(ab.all { it == 'a' || it == 'b' })
    }

    @Test
    fun `repeat with counter and nesting`() {
        assertEquals("0 1 2 ", gen("repeat(3 as i) {const(i, v) }"))
        // 중첩: 바깥 카운터를 안쪽에서 참조
        assertEquals("0-0 0-1 1-0 1-1 ", gen("repeat(2 as i) {repeat(2 as j) {const(i, a)-const(j, b) } }"))
    }

    @Test
    fun `braces swallow their trailing newline`() {
        // 이슈어의 예시 형태 — 반복마다 한 줄씩, 앞뒤로 빈 줄이 생기지 않아야 한다
        val out = gen("""
            repeat(3) {
            const(1, a) const(2, b)
            }
        """.trimIndent())
        assertEquals("1 2\n1 2\n1 2\n", out)
    }

    @Test
    fun `issue example produces the intended shape`() {
        val src = """
            const(3, t)
            repeat(t) {
            rand(1, 100, n) rand(n, 1000, m)
            array(n, a, SPACE, RANDOM, 0, 1000)
            array(m, b, SPACE, RANDOM, 0, 100)
            }
        """.trimIndent()
        val lines = gen(src).trimEnd('\n').split("\n")
        assertEquals("3", lines[0], "첫 줄은 테스트 케이스 수")
        assertEquals(1 + 3 * 3, lines.size, "케이스마다 3줄(n m / 배열 a / 배열 b)")
        for (case in 0 until 3) {
            val header = lines[1 + case * 3].split(" ")
            val n = header[0].toInt()
            val m = header[1].toInt()
            assertTrue(n in 1..100)
            assertTrue(m in n..1000)
            assertEquals(n, lines[2 + case * 3].split(" ").size, "배열 a의 길이는 n")
            assertEquals(m, lines[3 + case * 3].split(" ").size, "배열 b의 길이는 m")
            assertTrue(lines[2 + case * 3].split(" ").all { it.toInt() in 0..1000 })
            assertTrue(lines[3 + case * 3].split(" ").all { it.toInt() in 0..100 })
        }
    }

    @Test
    fun `big integers beyond 64 bits`() {
        val huge = BigInteger.TWO.pow(70)              // 2^70 — Long 범위 밖
        val out = gen("const($huge, v)").trim()
        assertEquals(huge.toString(), out)
        // 산술 승격: 2^62 * 4 는 Long 오버플로 → BigInteger로
        val big = BigInteger.TWO.pow(62).multiply(BigInteger.valueOf(4))
        assertEquals(big.toString(), gen("rand(${BigInteger.TWO.pow(62)} * 4, ${BigInteger.TWO.pow(62)} * 4, v)").trim())
        // 큰 범위 랜덤도 범위 안에 들어와야 한다
        val lo = BigInteger.TWO.pow(80)
        val hi = lo.add(BigInteger.valueOf(1000))
        repeat(20) {
            val v = BigInteger(gen("rand($lo, $hi, v)", seed = null).trim())
            assertTrue(v >= lo && v <= hi, "$v 가 [$lo, $hi] 밖")
        }
    }

    @Test
    fun `errors report a useful message`() {
        val e1 = assertThrows(TestFormatInterpreter.FormatException::class.java) { gen("rand(1, 10, x") }
        assertTrue(e1.message!!.contains("')'"), e1.message)

        val e2 = assertThrows(TestFormatInterpreter.FormatException::class.java) { gen("const(unknownLabel, v)") }
        assertTrue(e2.message!!.contains("unknown label"), e2.message)

        val e3 = assertThrows(TestFormatInterpreter.FormatException::class.java) { gen("nosuchfunc(1, v)") }
        assertTrue(e3.message!!.contains("unknown function"), e3.message)

        val e4 = assertThrows(TestFormatInterpreter.FormatException::class.java) { gen("array(3, a, SPACE, NOPE)") }
        assertTrue(e4.message!!.contains("unknown array type"), e4.message)

        val e5 = assertThrows(TestFormatInterpreter.FormatException::class.java) { gen("rand(10, 1, x)") }
        assertTrue(e5.message!!.contains("min > max"), e5.message)

        // 줄 번호가 실려야 편집기에서 위치를 표시할 수 있다
        val e6 = assertThrows(TestFormatInterpreter.FormatException::class.java) { gen("ok\nline\nrand(1, 2, x") }
        assertTrue(e6.line >= 3, "줄 번호가 3 이상이어야 함: ${e6.line}")
    }

    @Test
    fun `validate reports syntax errors without running`() {
        assertNull(TestFormatInterpreter.validate("const(1, a)\nrepeat(2) {\nrand(1, 5, x)\n}\n"))
        assertNotNull(TestFormatInterpreter.validate("repeat(2) {\nrand(1, 5, x)\n"))
        assertNotNull(TestFormatInterpreter.validate("const(1, a"))
    }

    @Test
    fun `guards reject runaway sizes`() {
        val e = assertThrows(TestFormatInterpreter.FormatException::class.java) {
            gen("array(999999999, a, SPACE, CONSTANT, 1, 1)")
        }
        assertTrue(e.message!!.contains("exceeds"), e.message)
    }

    @Test
    fun `rand_float honors decimals`() {
        val v = gen("rand_float(0, 1, f, 3)").trim()
        assertTrue(Regex("""^\d\.\d{3}$""").matches(v), "3자리 소수여야 함: $v")
        assertTrue(v.toDouble() in 0.0..1.0)
    }
}
