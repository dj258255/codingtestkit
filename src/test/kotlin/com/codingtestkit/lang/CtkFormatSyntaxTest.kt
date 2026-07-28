package com.codingtestkit.lang

import com.codingtestkit.service.TestFormatInterpreter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * 구문 강조와 인터프리터가 어긋나지 않는지 검증 (이슈 #36).
 *
 * 강조는 별도의 렉서라 함수 하나를 추가하고 목록 갱신을 빠뜨리면 조용히
 * 어긋난다 — 색이 안 칠해지거나(있는 함수를 모름), 색은 칠해지는데 실행하면
 * "unknown function"이 나는(없는 함수를 앎) 상황이 된다. 여기서 잡는다.
 */
class CtkFormatSyntaxTest {

    /** 강조가 아는 생성 함수는 전부 인터프리터에서 실제로 동작해야 한다 */
    @Test
    fun `every highlighted builtin exists in the interpreter`() {
        for (name in CtkFormatTokens.GENERATORS) {
            val e = assertThrows(TestFormatInterpreter.FormatException::class.java) {
                // 인자 없이 호출 — 존재하면 인자 오류, 없으면 unknown function
                TestFormatInterpreter.generate("$name()", 1L)
            }
            assertFalse(
                e.message!!.contains("unknown function"),
                "강조는 '$name'을 내장 함수로 칠하는데 인터프리터에는 없다: ${e.message}"
            )
        }
    }

    /** 집계 함수도 마찬가지 — 수식 안에서 인식되어야 한다 */
    @Test
    fun `every highlighted aggregate is recognized in expressions`() {
        for (name in CtkFormatTokens.AGGREGATES) {
            val e = assertThrows(TestFormatInterpreter.FormatException::class.java) {
                TestFormatInterpreter.generate("const($name(nope), x)", 1L)
            }
            // 라벨을 못 찾는 오류여야 한다 — 집계 자체를 모르면 다른 메시지가 난다
            assertTrue(
                e.message!!.contains("unknown label"),
                "집계 '$name'이 수식에서 인식되지 않는다: ${e.message}"
            )
        }
    }

    /** 반대 방향: 인터프리터의 내장 함수가 강조 목록에서 빠지지 않았는지 */
    @Test
    fun `interpreter builtins are all known to the highlighter`() {
        // 인터프리터가 아는 이름 = 매크로가 덮어쓸 수 없는 예약어
        val reserved = listOf(
            "const", "rand", "rand_float", "array", "string", "perm0", "perm1",
            "array2d", "tree", "graph", "bipartite_graph", "anti_hash_int", "anti_hash_str",
            "sum", "min", "max", "len"
        )
        for (name in reserved) {
            assertTrue(
                name in CtkFormatTokens.BUILTINS,
                "인터프리터의 '$name'이 강조 목록(BUILTINS)에 없다"
            )
        }
        for (kw in listOf("repeat", "def", "as")) {
            assertTrue(kw in CtkFormatTokens.KEYWORDS, "키워드 '$kw'가 강조 목록에 없다")
        }
    }

    /** 예약어를 매크로 이름으로 쓰면 거부되는지 — 강조 목록과 같은 근거로 막힌다 */
    @Test
    fun `builtin names cannot be redefined as macros`() {
        for (name in CtkFormatTokens.BUILTINS) {
            val e = assertThrows(TestFormatInterpreter.FormatException::class.java) {
                TestFormatInterpreter.generate("def $name() {\nx\n}", 1L)
            }
            assertTrue(e.message!!.contains("built-in"), "'$name' 재정의가 막히지 않는다: ${e.message}")
        }
    }

    /** 문서에 적힌 상수(타입·구분자)가 실제로 통하는지 */
    @Test
    fun `documented array types and separators work`() {
        for (type in listOf("RANDOM", "INCREASING", "DECREASING", "CONSTANT", "PERM0", "PERM1", "QUICKSORT_KILLER")) {
            assertDoesNotThrow({ TestFormatInterpreter.generate("array(4, a, SPACE, $type, 1, 9)", 1L) },
                "array 타입 $type 가 동작하지 않는다")
            assertTrue(type in CtkFormatTokens.CONSTANTS, "$type 가 강조 상수 목록에 없다")
        }
        for (sep in listOf("SPACE", "NEWLINE", "COMMA", "NONE")) {
            assertDoesNotThrow({ TestFormatInterpreter.generate("array(3, a, $sep, CONSTANT, 1, 1)", 1L) },
                "구분자 $sep 가 동작하지 않는다")
            assertTrue(sep in CtkFormatTokens.CONSTANTS, "$sep 가 강조 상수 목록에 없다")
        }
        for (t in listOf("RANDOM", "STAR", "PATH", "BAMBOO", "CATERPILLAR", "BINARY")) {
            assertDoesNotThrow({ TestFormatInterpreter.generate("tree(5, g, $t)", 1L) }, "트리 타입 $t 실패")
            assertTrue(t in CtkFormatTokens.CONSTANTS, "$t 가 강조 상수 목록에 없다")
        }
        for (t in listOf("SIMPLE", "CONNECTED", "MULTI", "TREE")) {
            assertDoesNotThrow({ TestFormatInterpreter.generate("graph(6, 5, g, $t)", 1L) }, "그래프 타입 $t 실패")
            assertTrue(t in CtkFormatTokens.CONSTANTS, "$t 가 강조 상수 목록에 없다")
        }
        assertDoesNotThrow({ TestFormatInterpreter.generate("graph(6, 5, g, DAG, true)", 1L) }, "DAG 실패")
    }

    /** 새 파일의 시작 템플릿은 항상 실행 가능해야 한다 */
    @Test
    fun `starter template runs`() {
        val out = TestFormatInterpreter.generate(CtkFormatDocs.STARTER_TEMPLATE, 7L)
        assertTrue(out.isNotBlank())
        assertEquals("3", out.lineSequence().first(), "예시는 케이스 수 3으로 시작한다")
        assertNull(TestFormatInterpreter.validate(CtkFormatDocs.STARTER_TEMPLATE))
    }

    /** 문서 HTML이 비어 있거나 깨지지 않는지 (함수 시그니처가 실제로 들어있는지) */
    @Test
    fun `docs mention every generator function`() {
        val html = CtkFormatDocs.html()
        for (name in CtkFormatTokens.GENERATORS) {
            assertTrue(html.contains("$name("), "문서에 '$name' 설명이 없다")
        }
        for (name in CtkFormatTokens.AGGREGATES) {
            assertTrue(html.contains("$name("), "문서에 집계 '$name' 설명이 없다")
        }
    }

    /** 렉서가 대표적인 형식 소스를 토큰으로 훑을 수 있는지 (무한 루프·누락 없이) */
    @Test
    fun `lexer covers the whole source without gaps`() {
        val src = CtkFormatDocs.STARTER_TEMPLATE + "\ndef helper(k) {\nstring(k, s, \"a-z\")\n}\nhelper(3)\n"
        val lexer = CtkFormatLexer()
        lexer.start(src, 0, src.length, 0)
        var covered = 0
        var guard = 0
        while (lexer.tokenType != null) {
            assertEquals(covered, lexer.tokenStart, "토큰 사이에 빈 구간이 있다")
            assertTrue(lexer.tokenEnd > lexer.tokenStart, "토큰이 전진하지 않는다")
            covered = lexer.tokenEnd
            lexer.advance()
            assertTrue(guard++ < src.length + 10, "렉서가 끝나지 않는다")
        }
        assertEquals(src.length, covered, "소스 끝까지 훑지 못했다")
    }
}
