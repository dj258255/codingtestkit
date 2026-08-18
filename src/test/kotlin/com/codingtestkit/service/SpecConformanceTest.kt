package com.codingtestkit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * 이슈 #36에서 이슈어가 올린 정식 스펙을 한 줄씩 그대로 실행해 대조한다.
 *
 * 다른 테스트들이 각 기능의 동작을 검증한다면, 이 파일은 "요청한 문법이
 * 요청한 형태로 실제로 쓰이는가"를 확인하는 대조표다. 스펙 문장과 테스트가
 * 1:1로 붙어 있어, 나중에 문법을 바꾸면 어떤 약속이 깨지는지 바로 드러난다.
 */
class SpecConformanceTest {

    private fun gen(src: String, seed: Long? = 11L) = TestFormatInterpreter.generate(src, seed)

    // "literal characters, spaces, and newlines pass through untouched.
    //  Function calls name(...) are the only things replaced with generated values."
    @Test
    fun `literals pass through and only calls are replaced`() {
        assertEquals("a b\tc\n#.#\n", gen("a b\tc\n#.#\n"))   // '#'·'.'는 격자 입력의 실제 데이터
        assertEquals("n=5", gen("n=rand(5, 5, x)"))
    }

    // "label → generates a value, prints it, and binds it to label for reuse.
    //  _label → generates a value, binds it, but prints nothing"
    @Test
    fun `label prints and binds, underscore label only binds`() {
        assertEquals("7 7", gen("rand(7, 7, a) const(a, b)"))
        assertEquals("7", gen("rand(7, 7, _a)const(_a, b)"))
    }

    // "const(value, label)"
    @Test
    fun `const`() = assertEquals("100", gen("const(100, t)"))

    // "rand(min, max, label)"
    @Test
    fun `rand`() {
        val v = gen("rand(3, 9, x)").toInt()
        assertTrue(v in 3..9)
    }

    // "rand_float(min, max, label, [decimals=6])"
    @Test
    fun `rand_float with default and explicit decimals`() {
        assertTrue(Regex("""^\d\.\d{6}$""").matches(gen("rand_float(0, 1, f)")), gen("rand_float(0, 1, f)"))
        assertTrue(Regex("""^\d\.\d{2}$""").matches(gen("rand_float(0, 1, f, 2)")))
    }

    // "array(length, label, [sep=SPACE], [type=RANDOM], [subtype params like min,max...])"
    @Test
    fun `array with defaults and full argument list`() {
        assertEquals(5, gen("array(5, a)").split(" ").size, "기본 구분자는 SPACE")
        assertEquals("1 2 3", gen("array(3, a, SPACE, INCREASING, 1, 100)"))
        assertEquals("1,2,3", gen("array(3, a, COMMA, INCREASING, 1, 100)"))
    }

    // "array2d(rows, cols, min, max, label, [sep=SPACE], [type=RANDOM])"
    @Test
    fun `array2d argument order matches the spec`() {
        val out = gen("array2d(2, 3, 4, 4, m)")
        assertEquals(listOf("4 4 4", "4 4 4"), out.split("\n"))
    }

    // "string(length, label, [alphabet=\"a-z\"])"
    @Test
    fun `string with default and custom alphabet`() {
        val s = gen("string(8, s)")
        assertEquals(8, s.length)
        assertTrue(s.all { it in 'a'..'z' })
        assertTrue(gen("string(8, s, \"xy\")").all { it == 'x' || it == 'y' })
    }

    // "perm(n, label)" — 협의 과정에서 perm0/perm1로 갈라졌다.
    // 스펙 그대로 친 사용자가 헤매지 않도록 안내 메시지를 낸다.
    @Test
    fun `perm from the original spec points to perm0 and perm1`() {
        val e = assertThrows(TestFormatInterpreter.FormatException::class.java) { gen("perm(5, a)") }
        assertTrue(e.message!!.contains("perm0(n, label)"), e.message)
        assertTrue(e.message!!.contains("perm1(n, label)"), e.message)
        assertEquals((0..4).toList(), gen("perm0(5, a)").split(" ").map { it.toInt() }.sorted())
        assertEquals((1..5).toList(), gen("perm1(5, a)").split(" ").map { it.toInt() }.sorted())
    }

    // "tree(n, label, [type=RANDOM]) — Prints n-1 lines, each u v (1-indexed)"
    // "type=RANDOM/STAR/PATH/CATERPILLAR/BINARY/BAMBOO"
    @Test
    fun `tree prints n-1 one-indexed edge lines for every listed type`() {
        for (type in listOf("RANDOM", "STAR", "PATH", "CATERPILLAR", "BINARY", "BAMBOO")) {
            val lines = gen("tree(7, g, $type)").split("\n")
            assertEquals(6, lines.size, "$type: n-1줄이어야 함")
            for (line in lines) {
                val (u, v) = line.split(" ").map { it.toInt() }
                assertTrue(u in 1..7 && v in 1..7, "$type: 1-based 정점이어야 함 ($line)")
            }
        }
        assertEquals(6, gen("tree(7, g)").split("\n").size, "type 생략 시 RANDOM")
    }

    // "graph(n, m, label, [type=SIMPLE], [directed=false], [weighted=false], [wmin], [wmax])
    //  Prints m lines. Each line u v or u v w if weighted."
    @Test
    fun `graph prints m lines with optional weight for every listed type`() {
        for (type in listOf("SIMPLE", "CONNECTED", "MULTI")) {
            val lines = gen("graph(8, 10, g, $type)").split("\n")
            assertEquals(10, lines.size, "$type: m줄이어야 함")
            assertTrue(lines.all { it.split(" ").size == 2 }, "$type: 가중치 없으면 u v")
        }
        assertEquals(10, gen("graph(8, 10, g)").split("\n").size, "type 생략 시 SIMPLE")
        // directed=true + DAG
        assertTrue(gen("graph(8, 10, g, DAG, true)").split("\n")
            .all { val p = it.split(" ").map(String::toInt); p[0] < p[1] })
        // weighted → u v w
        assertTrue(gen("graph(8, 10, g, SIMPLE, false, true, 2, 3)").split("\n")
            .all { it.split(" ").size == 3 && it.split(" ")[2].toInt() in 2..3 })
        // "type=TREE — alias for tree(n,label) with weighting support"
        assertEquals(6, gen("graph(7, 99, g, TREE)").split("\n").size, "TREE는 n-1줄")
        assertTrue(gen("graph(7, 99, g, TREE, false, true, 5, 5)").split("\n").all { it.endsWith(" 5") })
    }

    // "bipartite_graph(n1, n2, m, label, [directed=false])"
    @Test
    fun `bipartite_graph`() {
        val lines = gen("bipartite_graph(3, 4, 5, g)").split("\n")
        assertEquals(5, lines.size)
        for (line in lines) {
            val (a, b) = line.split(" ").map { it.toInt() }
            val (l, r) = if (a <= 3) a to b else b to a
            assertTrue(l in 1..3 && r in 4..7, "왼쪽 1..3 / 오른쪽 4..7 이어야 함: $line")
        }
    }

    // "`T as i{ ... } ... Here i is the iteration counter, which will go from 0 to T-1 (inclusive)"
    // (문법만 repeat(T as i) { ... } 로 합의)
    @Test
    fun `repeat counter runs from zero to t minus one inclusive`() {
        assertEquals("0 1 2 3", gen("repeat(4 as i) {const(i, v) }"))
    }

    // 확정 문언: "whitespace right after `{` / before `}` ignored"
    // (제보자의 첫 표현은 "after '{' or '}'"였지만 협의에서 위 문장으로 확정됐다.
    //  대조표가 느슨한 쪽을 인용하면 지키지 못한 약속을 통과시키게 된다 — 실제로
    //  '}' 앞 공백이 반복마다 후행 공백으로 남고 있었다.)
    @Test
    fun `whitespace right after the opening brace is ignored`() {
        assertEquals("1\n1\n", gen("repeat(2) {\n   const(1, a)\n}\n   "))
    }

    @Test
    fun `no trailing space survives at the end of a line`() {
        // '}' 앞 스페이스는 반복 사이의 구분자로 살리되, 줄 끝에 남는 자국은 없어야 한다
        assertEquals("1 1 1", gen("repeat(3) {const(1, a) }"))
        assertEquals("0 1 2 3", gen("repeat(4 as i) {const(i, v) }"))
    }

    @Test
    fun `newline before the closing brace still separates iterations`() {
        // 여러 줄 형태에서 마지막 개행은 서식이 아니라 반복 구분자다 — 지우면 안 된다
        assertEquals("1\n1\n1\n", gen("repeat(3) {\nconst(1,a)\n}"))
    }

    // "you can allow simple arithmetic like (+,-,*,floor divide)"
    @Test
    fun `arithmetic operators`() {
        assertEquals("9", gen("const(4 + 5, x)"))
        assertEquals("1", gen("const(5 - 4, x)"))
        assertEquals("20", gen("const(4 * 5, x)"))
        assertEquals("2", gen("const(5 / 2, x)"))
        assertEquals("13", gen("const(1 + 2 * 6, x)"), "곱셈이 덧셈보다 우선")
        assertEquals("18", gen("const((1 + 2) * 6, x)"))
    }

    // 이슈어가 올린 대표 예시 (첫 댓글의 t / n m / 배열 두 줄 형식)
    @Test
    fun `the issue example format`() {
        val src = """
            const(4, t)
            repeat(t) {
            rand(1, 50, n) rand(n, 200, m)
            array(n, a, SPACE, RANDOM, 0, 1000)
            array(m, b, SPACE, RANDOM, 0, 100)
            }
        """.trimIndent()
        val lines = gen(src).trimEnd('\n').split("\n")
        assertEquals("4", lines[0])
        assertEquals(1 + 4 * 3, lines.size)
        for (c in 0 until 4) {
            val (n, m) = lines[1 + c * 3].split(" ").map { it.toInt() }
            assertEquals(n, lines[2 + c * 3].split(" ").size)
            assertEquals(m, lines[3 + c * 3].split(" ").size)
        }
    }

    // "allow the user to write a custom function"
    @Test
    fun `custom function`() {
        val src = """
            def block(k) {
            const(k, n)
            array(k, a, SPACE, CONSTANT, 1, 1)
            }
            block(3)
        """.trimIndent()
        assertEquals("3\n1 1 1", gen(src))
    }

    // "adding a few aggregate functions such as sum, min, max, and len"
    @Test
    fun `aggregates`() {
        assertEquals("10", gen("array(4, _a, SPACE, INCREASING, 1, 9)const(sum(_a), s)"))
        assertEquals("1", gen("array(4, _a, SPACE, INCREASING, 1, 9)const(min(_a), s)"))
        assertEquals("4", gen("array(4, _a, SPACE, INCREASING, 1, 9)const(max(_a), s)"))
        assertEquals("4", gen("array(4, _a, SPACE, INCREASING, 1, 9)const(len(_a), s)"))
    }

    // "it would be important to handle integers larger than 2^64 correctly ... use java.math.BigInteger"
    @Test
    fun `integers beyond 2 to the 64`() {
        val huge = java.math.BigInteger.TWO.pow(80).toString()
        assertEquals(huge, gen("const($huge, v)"))
        // 산술도 승격
        val squared = java.math.BigInteger.TWO.pow(80).let { it.multiply(it) }.toString()
        assertEquals(squared, gen("const($huge * $huge, v)"))
    }

    // "add an anti_hash number generator that produces sequences designed to trigger
    //  worst-case behavior in std::unordered_map and std::unordered_set"
    @Test
    fun `numeric anti hash targets unordered containers`() {
        val values = gen("anti_hash_int(200, a)").split(" ").map { it.toLong() }
        assertEquals(200, values.size)
        val prime = GenStructures.bucketPrimeFor(200)
        assertTrue(values.all { it % prime == 0L }, "libstdc++ 버킷 소수의 배수여야 한 버킷에 몰린다")
        // 초기 스펙의 anti_hash(단일 이름)도 안내를 낸다
        val e = assertThrows(TestFormatInterpreter.FormatException::class.java) { gen("anti_hash(10, a)") }
        assertTrue(e.message!!.contains("anti_hash_int"), e.message)
        assertTrue(e.message!!.contains("anti_hash_str"), e.message)
    }
}
