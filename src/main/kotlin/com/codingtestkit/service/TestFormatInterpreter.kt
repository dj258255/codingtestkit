package com.codingtestkit.service

import com.codingtestkit.ui.TestCaseGenerators
import kotlin.random.Random

/**
 * 테스트 입력 생성 DSL 인터프리터 (이슈 #36).
 *
 * 리터럴 문자·공백·줄바꿈은 그대로 통과하고 `name(...)` 호출만 생성값으로
 * 치환한다. 라벨은 생성값을 이름에 묶어 뒤에서 재사용하게 하고, `_라벨`은
 * 묶기만 하고 출력하지 않는다.
 *
 *   const(100, t)
 *   repeat(t) {
 *   rand(1, 100, n) rand(n, 1000, m)
 *   array(n, a, SPACE, RANDOM, 0, 1000)
 *   }
 *
 * 재귀·조건문은 의도적으로 없다 — 예측 가능한 치환기로 남겨 무한 루프와
 * 정지 문제를 원천 차단한다. 그 이상이 필요하면 별도의 생성기 프로그램
 * (커스텀 체커와 같은 방식)이 탈출구다.
 */
object TestFormatInterpreter {

    /** 한 번 생성에서 만들 수 있는 최대 출력 크기 — 실수로 IDE를 죽이지 않도록 */
    private const val MAX_OUTPUT = 64 * 1024 * 1024
    /** 배열 하나의 최대 길이 */
    private const val MAX_ELEMENTS = 5_000_000
    /** repeat 총 반복 횟수 상한 (중첩 포함 누적) */
    private const val MAX_ITERATIONS = 1_000_000

    class FormatException(
        message: String,
        val line: Int,
        val column: Int
    ) : Exception(if (line > 0) "$message (line $line, col $column)" else message)

    /** 라벨에 묶이는 값 — 스칼라·배열·문자열·여러 줄 구조(트리/그래프 등) */
    sealed class Bound {
        data class Scalar(val value: GenNum) : Bound()
        data class Arr(val values: List<GenNum>) : Bound()
        data class Str(val value: String) : Bound()
        /** 줄 단위로 출력되는 구조 — 줄 수가 곧 간선 수/행 수 */
        data class Rows(val lines: List<String>) : Bound()
    }

    /**
     * 소스를 실행해 테스트 입력 문자열을 만든다.
     * @param seed null이면 매번 다른 결과, 값을 주면 재현 가능
     */
    fun generate(source: String, seed: Long? = null): String {
        val parser = Parser(source)
        val nodes = parser.parseTemplate(topLevel = true)
        val out = StringBuilder()
        val rng = if (seed != null) Random(seed) else Random.Default
        Evaluator(rng, out, parser.macros).run(nodes, HashMap())
        return normalizeTrailingSpace(out.toString())
    }

    /**
     * 줄 끝 공백을 정리한다 (이슈 #36 명세: "'}' 앞 공백 무시").
     *
     * 파서 단계에서 '}' 앞 공백을 지우지 않는 이유: 그 공백은 서식이 아니라
     * 반복 사이의 구분자다. repeat(3) { rand(1,10,x) }에서 지워버리면 값들이
     * "5372"처럼 붙어 쓸 수 없는 입력이 된다. 대신 각 줄 끝에 남는 공백만
     * 걷어내면 구분자는 살리면서 "1 1 1 " 같은 후행 공백 자국은 사라진다.
     */
    private fun normalizeTrailingSpace(text: String): String =
        text.split("\n").joinToString("\n") { it.trimEnd(' ', '\t') }

    /** 문법 검사만 — 편집기 오류 표시용 (실행 없이 파싱만) */
    fun validate(source: String): FormatException? = try {
        Parser(source).parseTemplate(topLevel = true); null
    } catch (e: FormatException) { e }

    // ─────────────────────────── AST ───────────────────────────

    private sealed class Node {
        data class Literal(val text: String) : Node()
        data class Call(val name: String, val args: List<Arg>, val line: Int, val col: Int) : Node()
        data class Repeat(
            val count: Expr, val counterName: String?, val body: List<Node>,
            val line: Int, val col: Int
        ) : Node()
    }

    /**
     * 인자 하나. 위치에 따라 수식·라벨·키워드·문자열 중 무엇으로 읽을지가 달라서
     * (예: array(length, label, SEP, TYPE, min, max)) 원문 형태를 모두 보존한다.
     */
    private class Arg(val expr: Expr?, val ident: String?, val str: String?, val line: Int, val col: Int)

    private sealed class Expr {
        data class Lit(val value: GenNum) : Expr()
        data class Ref(val name: String, val line: Int, val col: Int) : Expr()
        data class Bin(val op: Char, val l: Expr, val r: Expr, val line: Int, val col: Int) : Expr()
        data class Neg(val e: Expr) : Expr()
        /** 집계: sum/min/max/len — 배열·구조 라벨을 수식 안에서 쓰게 한다 */
        data class Agg(val fn: String, val target: String, val line: Int, val col: Int) : Expr()
    }

    /** 내장 함수 이름 — 매크로가 덮어쓸 수 없다 */
    private val RESERVED = setOf(
        "const", "rand", "rand_float", "array", "string", "perm0", "perm1",
        "array2d", "tree", "graph", "bipartite_graph", "anti_hash_int", "anti_hash_str",
        "repeat", "def", "sum", "min", "max", "len"
    )

    private val AGGREGATES = setOf("sum", "min", "max", "len")

    // ───────────────────────── 파서 ─────────────────────────

    /** 매크로 정의 — 본문은 평범한 템플릿, 파라미터는 수식 자리에 쓰인다 */
    private class Macro(val name: String, val params: List<String>, val body: List<Node>)

    private class Parser(private val src: String) {
        private var pos = 0

        /** def로 정의된 매크로들 (파싱 중 수집) */
        val macros = HashMap<String, Macro>()

        private fun lineColOf(at: Int): Pair<Int, Int> {
            var line = 1; var col = 1
            for (i in 0 until at.coerceAtMost(src.length)) {
                if (src[i] == '\n') { line++; col = 1 } else col++
            }
            return line to col
        }

        private fun fail(message: String, at: Int = pos): Nothing {
            val (l, c) = lineColOf(at)
            throw FormatException(message, l, c)
        }

        /**
         * 템플릿 본문을 읽는다. 식별자 뒤에 '('가 오면 호출, `repeat`은 블록,
         * 그 밖의 모든 문자는 리터럴로 통과.
         */
        fun parseTemplate(topLevel: Boolean): List<Node> {
            val nodes = mutableListOf<Node>()
            val literal = StringBuilder()

            fun flushLiteral() {
                if (literal.isNotEmpty()) { nodes.add(Node.Literal(literal.toString())); literal.clear() }
            }

            while (pos < src.length) {
                val c = src[pos]
                if (c == '}' && !topLevel) {          // 블록 끝 — 호출자가 소비
                    flushLiteral(); return nodes
                }
                if (c == '}' && topLevel) fail("unmatched '}'")
                if (isIdentStart(c)) {
                    val start = pos
                    val name = readIdent()
                    val afterName = pos
                    // def name(params) { ... } — 매크로 정의는 출력 없이 등록만
                    if (name == "def") {
                        flushLiteral()
                        parseMacroDef(start)
                        continue
                    }
                    skipSpacesOnly()
                    if (pos < src.length && src[pos] == '(') {
                        flushLiteral()
                        val (l, cc) = lineColOf(start)
                        nodes.add(if (name == "repeat") parseRepeat(l, cc) else parseCall(name, l, cc))
                        continue
                    }
                    // 호출이 아니면 식별자 자체가 리터럴 텍스트
                    literal.append(src, start, afterName)
                    pos = afterName
                    continue
                }
                literal.append(c); pos++
            }
            if (!topLevel) fail("unterminated block — missing '}'")
            flushLiteral()
            return nodes
        }

        /**
         * def name(a, b) { ... } — 매크로 정의.
         * 본문은 평범한 템플릿이고 파라미터는 수식 자리에서 값으로 쓰인다.
         * 재귀는 없다: 정의 시점에 이미 등록된 매크로만 본문에서 호출할 수 있어
         * 자기 자신·상호 재귀가 문법 단계에서 차단된다 (정지 보장).
         */
        private fun parseMacroDef(startPos: Int) {
            skipWs()
            if (pos >= src.length || !isIdentStart(src[pos])) fail("expected macro name after 'def'", startPos)
            val name = readIdent()
            if (name in RESERVED) fail("'$name' is a built-in function name", startPos)
            if (macros.containsKey(name)) fail("macro '$name' is already defined", startPos)
            skipWs()
            expect('(')
            val params = mutableListOf<String>()
            skipWs()
            if (src.getOrNull(pos) == ')') pos++ else while (true) {
                skipWs()
                if (pos >= src.length || !isIdentStart(src[pos])) fail("expected parameter name")
                params.add(readIdent())
                skipWs()
                when (src.getOrNull(pos)) {
                    ',' -> pos++
                    ')' -> { pos++; break }
                    else -> fail("expected ',' or ')' in parameter list")
                }
            }
            skipWs()
            expect('{')
            skipLayoutWhitespace()
            val body = parseTemplate(topLevel = false)
            expect('}')
            skipLayoutWhitespace()
            // 본문이 아직 정의되지 않은 매크로를 부르면 거부 — 자기 재귀·상호 재귀가
            // 여기서 막혀 매크로 전개가 반드시 끝난다
            validateNoForwardCalls(body, name)
            macros[name] = Macro(name, params, dropTrailingNewline(body))
        }

        /**
         * 매크로 본문 끝의 개행 하나를 떼어낸다.
         *
         * 줄바꿈은 호출 지점이 정하는 게 자연스럽다 — `repeat { row(3) }`처럼
         * 한 줄에 호출하면 그 줄의 개행이 이미 있으므로, 본문 끝 개행까지 남으면
         * 반복마다 빈 줄이 생긴다. 여러 줄을 찍는 매크로의 중간 개행은 그대로 둔다.
         * (repeat 본문은 반대로 끝 개행이 반복 구분자라 유지한다)
         */
        private fun dropTrailingNewline(nodes: List<Node>): List<Node> {
            val last = nodes.lastOrNull() as? Node.Literal ?: return nodes
            val trimmed = last.text.trimEnd(' ', '\t', '\r').removeSuffix("\n")
            if (trimmed == last.text) return nodes
            return nodes.dropLast(1) + if (trimmed.isEmpty()) emptyList() else listOf(Node.Literal(trimmed))
        }

        private fun validateNoForwardCalls(nodes: List<Node>, definingName: String) {
            for (node in nodes) when (node) {
                is Node.Literal -> {}
                is Node.Repeat -> validateNoForwardCalls(node.body, definingName)
                is Node.Call -> {
                    if (node.name in RESERVED || node.name in macros) continue
                    val why = if (node.name == definingName) "macros cannot call themselves"
                              else "unknown function '${node.name}' (macros must be defined before use)"
                    throw FormatException("in def $definingName(): $why", node.line, node.col)
                }
            }
        }

        private fun parseCall(name: String, line: Int, col: Int): Node.Call {
            expect('(')
            val args = parseArgs()
            return Node.Call(name, args, line, col)
        }

        /** repeat(expr) { ... } / repeat(expr as i) { ... } */
        private fun parseRepeat(line: Int, col: Int): Node.Repeat {
            expect('(')
            skipWs()
            val count = parseExpr()
            skipWs()
            var counter: String? = null
            if (pos < src.length && isIdentStart(src[pos])) {
                val kw = readIdent()
                if (kw != "as") fail("expected 'as' or ')' in repeat(...)")
                skipWs()
                if (pos >= src.length || !isIdentStart(src[pos])) fail("expected counter name after 'as'")
                counter = readIdent()
                skipWs()
            }
            expect(')')
            skipWs()
            expect('{')
            skipLayoutWhitespace()  // 여는 중괄호 뒤 공백은 서식 — 본문에 넣지 않는다
            val body = parseTemplate(topLevel = false)
            expect('}')
            skipLayoutWhitespace()  // 닫는 중괄호 뒤 공백도 흘린다
            return Node.Repeat(count, counter, body, line, col)
        }

        private fun parseArgs(): List<Arg> {
            val args = mutableListOf<Arg>()
            skipWs()
            if (pos < src.length && src[pos] == ')') { pos++; return args }
            while (true) {
                skipWs()
                val (l, c) = lineColOf(pos)
                val arg = when {
                    pos < src.length && src[pos] == '"' -> Arg(null, null, readString(), l, c)
                    // 식별자 하나로 끝나면 라벨/키워드, 뒤에 연산자가 붙으면 수식
                    pos < src.length && isIdentStart(src[pos]) -> {
                        val save = pos
                        val id = readIdent()
                        skipWs()
                        val next = src.getOrNull(pos)
                        if (next == ',' || next == ')') Arg(null, id, null, l, c)
                        else { pos = save; Arg(parseExpr(), null, null, l, c) }
                    }
                    else -> Arg(parseExpr(), null, null, l, c)
                }
                args.add(arg)
                skipWs()
                when (src.getOrNull(pos)) {
                    ',' -> { pos++ }
                    ')' -> { pos++; return args }
                    null -> fail("unterminated argument list — missing ')'")
                    else -> fail("expected ',' or ')' in argument list")
                }
            }
        }

        // 수식: 항 (('+'|'-') 항)*, 항: 인자 (('*'|'/') 인자)*
        fun parseExpr(): Expr {
            var left = parseTerm()
            while (true) {
                skipWs()
                val op = src.getOrNull(pos) ?: return left
                if (op != '+' && op != '-') return left
                val (l, c) = lineColOf(pos)
                pos++
                left = Expr.Bin(op, left, parseTerm(), l, c)
            }
        }

        private fun parseTerm(): Expr {
            var left = parseFactor()
            while (true) {
                skipWs()
                val op = src.getOrNull(pos) ?: return left
                if (op != '*' && op != '/') return left
                val (l, c) = lineColOf(pos)
                pos++
                left = Expr.Bin(op, left, parseFactor(), l, c)
            }
        }

        private fun parseFactor(): Expr {
            skipWs()
            val c = src.getOrNull(pos) ?: fail("unexpected end of expression")
            if (c == '-') { pos++; return Expr.Neg(parseFactor()) }
            if (c == '(') {
                pos++
                val e = parseExpr()
                skipWs()
                expect(')')
                return e
            }
            if (c.isDigit()) {
                val start = pos
                while (pos < src.length && src[pos].isDigit()) pos++
                val text = src.substring(start, pos)
                return Expr.Lit(GenNum.parse(text) ?: fail("invalid number '$text'", start))
            }
            if (isIdentStart(c)) {
                val (l, cc) = lineColOf(pos)
                val id = readIdent()
                // 집계 함수 호출: sum(a) / len(g) 등 — 인자는 라벨 하나
                if (id in AGGREGATES) {
                    skipWs()
                    expect('(')
                    skipWs()
                    if (pos >= src.length || !isIdentStart(src[pos])) fail("$id() takes a label name")
                    val target = readIdent()
                    skipWs()
                    expect(')')
                    return Expr.Agg(id, target, l, cc)
                }
                return Expr.Ref(id, l, cc)
            }
            fail("unexpected character '$c' in expression")
        }

        // ── 어휘 보조 ──

        private fun isIdentStart(c: Char) = c.isLetter() || c == '_'
        private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_'

        private fun readIdent(): String {
            val start = pos
            while (pos < src.length && isIdentPart(src[pos])) pos++
            return src.substring(start, pos)
        }

        private fun readString(): String {
            pos++ // 여는 따옴표
            val sb = StringBuilder()
            while (pos < src.length && src[pos] != '"') {
                if (src[pos] == '\\' && pos + 1 < src.length) { pos++; }
                sb.append(src[pos]); pos++
            }
            if (pos >= src.length) fail("unterminated string literal")
            pos++ // 닫는 따옴표
            return sb.toString()
        }

        private fun expect(ch: Char) {
            skipWs()
            if (pos >= src.length || src[pos] != ch) fail("expected '$ch'")
            pos++
        }

        private fun skipWs() { while (pos < src.length && src[pos].isWhitespace()) pos++ }
        private fun skipSpacesOnly() { while (pos < src.length && (src[pos] == ' ' || src[pos] == '\t')) pos++ }

        /**
         * 중괄호 바로 뒤의 공백을 흘린다 — 명세대로 '{' / '}' 뒤 공백은 내용이 아니라 서식.
         */
        private fun skipLayoutWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

    }

    // ───────────────────────── 평가기 ─────────────────────────

    private class Evaluator(
        private val rng: Random,
        private val out: StringBuilder,
        private val macros: Map<String, Macro>
    ) {
        private var iterations = 0

        fun run(nodes: List<Node>, scope: MutableMap<String, Bound>) {
            for (node in nodes) when (node) {
                is Node.Literal -> emit(node.text)
                is Node.Call -> evalCall(node, scope)
                is Node.Repeat -> {
                    val count = evalExpr(node.count, scope).toIntOrNull()
                        ?: throw FormatException("repeat count too large", node.line, node.col)
                    if (count < 0) throw FormatException("repeat count must be >= 0", node.line, node.col)
                    iterations += count
                    if (iterations > MAX_ITERATIONS)
                        throw FormatException("too many repeat iterations (max $MAX_ITERATIONS)", node.line, node.col)
                    repeat(count) { i ->
                        // 반복 카운터는 안쪽 스코프에만 — 바깥 라벨을 덮지 않는다
                        val inner = if (node.counterName != null) HashMap(scope).also {
                            it[node.counterName] = Bound.Scalar(GenNum.of(i.toLong()))
                        } else scope
                        run(node.body, inner)
                        // 안쪽에서 만든 라벨은 다음 반복에도 보이게 유지 (같은 이름 재생성이 자연스럽다)
                        if (inner !== scope) inner.forEach { (k, v) -> if (k != node.counterName) scope[k] = v }
                    }
                }
            }
        }

        private fun emit(text: String) {
            if (out.length + text.length > MAX_OUTPUT)
                throw FormatException("generated output exceeds ${MAX_OUTPUT / (1024 * 1024)} MB", 0, 0)
            out.append(text)
        }

        private fun evalExpr(e: Expr, scope: Map<String, Bound>): GenNum = when (e) {
            is Expr.Lit -> e.value
            is Expr.Neg -> GenNum.ZERO - evalExpr(e.e, scope)
            is Expr.Ref -> when (val b = scope[e.name]) {
                is Bound.Scalar -> b.value
                is Bound.Arr -> throw FormatException("'${e.name}' is an array; use an aggregate like len(${e.name})", e.line, e.col)
                is Bound.Rows -> throw FormatException("'${e.name}' is a structure; use an aggregate like len(${e.name})", e.line, e.col)
                is Bound.Str -> throw FormatException(
                    "'${e.name}' is text, not a number — rand_float and string values can be printed " +
                        "and measured with len(${e.name}), but arithmetic works on integers only",
                    e.line, e.col
                )
                null -> throw FormatException("unknown label '${e.name}'", e.line, e.col)
            }
            is Expr.Agg -> evalAggregate(e, scope)
            is Expr.Bin -> {
                val l = evalExpr(e.l, scope); val r = evalExpr(e.r, scope)
                when (e.op) {
                    '+' -> l + r
                    '-' -> l - r
                    '*' -> l * r
                    '/' -> if (r.isZero()) throw FormatException("division by zero", e.line, e.col) else l.floorDiv(r)
                    else -> throw FormatException("unknown operator '${e.op}'", e.line, e.col)
                }
            }
        }

        /**
         * 없는 함수 오류 — 협의 과정에서 이름이 갈라진 것들은 대체 함수를 안내한다.
         * 초기 스펙의 perm/anti_hash는 0-based·1-based 모호성, 숫자·문자열 구분
         * 때문에 각각 둘로 나뉘었다. 그대로 따라 친 사용자가 헤매지 않도록 한다.
         */
        private fun unknownFunctionMessage(name: String): String = when (name) {
            "perm" -> "unknown function 'perm' — use perm0(n, label) for 0..n-1 or perm1(n, label) for 1..n"
            "anti_hash" -> "unknown function 'anti_hash' — use anti_hash_int(n, label, [prime]) for " +
                "unordered_map/set killers or anti_hash_str(length, labelA, labelB) for colliding strings"
            else -> "unknown function '$name'"
        }

        /** sum/min/max/len — len은 문자열·구조에도 통하고, 나머지는 숫자 배열 전용 */
        private fun evalAggregate(e: Expr.Agg, scope: Map<String, Bound>): GenNum {
            val bound = scope[e.target]
                ?: throw FormatException("unknown label '${e.target}'", e.line, e.col)
            if (e.fn == "len") return when (bound) {
                is Bound.Arr -> GenNum.of(bound.values.size.toLong())
                is Bound.Rows -> GenNum.of(bound.lines.size.toLong())
                is Bound.Str -> GenNum.of(bound.value.length.toLong())
                is Bound.Scalar -> throw FormatException(
                    "len() needs an array, string, or structure; '${e.target}' is a single number", e.line, e.col)
            }
            val values = (bound as? Bound.Arr)?.values ?: throw FormatException(
                "${e.fn}() needs an array of numbers; '${e.target}' is not one (len() may work)", e.line, e.col)
            if (values.isEmpty()) throw FormatException("${e.fn}() of an empty array", e.line, e.col)
            return when (e.fn) {
                "sum" -> values.fold(GenNum.ZERO) { acc, v -> acc + v }
                "min" -> values.min()
                "max" -> values.max()
                else -> throw FormatException("unknown aggregate '${e.fn}'", e.line, e.col)
            }
        }

        private fun evalCall(call: Node.Call, scope: MutableMap<String, Bound>) {
            val a = CallArgs(call, scope, this)
            when (call.name) {
                "const" -> {
                    val v = a.num(0)
                    a.bindAndEmit(1, Bound.Scalar(v), v.toString())
                }
                "rand" -> {
                    val lo = a.num(0); val hi = a.num(1)
                    if (lo > hi) a.fail("min > max")
                    val v = lo.rangeTo(hi, rng)
                    a.bindAndEmit(2, Bound.Scalar(v), v.toString())
                }
                "rand_float" -> {
                    val lo = a.num(0); val hi = a.num(1)
                    if (lo > hi) a.fail("min > max")
                    val decimals = if (a.size > 3) a.num(3).toIntOrNull() ?: a.fail("invalid decimals") else 6
                    if (decimals !in 0..18) a.fail("decimals must be 0..18")
                    val lod = lo.toBigInteger().toDouble(); val hid = hi.toBigInteger().toDouble()
                    val v = lod + (hid - lod) * rng.nextDouble()
                    val text = String.format("%.${decimals}f", v)
                    a.bindAndEmit(2, Bound.Str(text), text)
                }
                "perm0", "perm1" -> {
                    val n = a.count(0)
                    val base = if (call.name == "perm1") 1 else 0
                    val values = MutableList(n) { GenNum.of((it + base).toLong()) }
                    values.shuffle(rng)
                    a.bindAndEmit(1, Bound.Arr(values), values.joinToString(" "))
                }
                "string" -> {
                    val n = a.count(0)
                    val alphabet = if (a.size > 2) expandAlphabet(a.str(2)) else ('a'..'z').toList()
                    if (alphabet.isEmpty()) a.fail("alphabet is empty")
                    val s = buildString(n) { repeat(n) { append(alphabet[rng.nextInt(alphabet.size)]) } }
                    a.bindAndEmit(1, Bound.Str(s), s)
                }
                "array" -> {
                    val n = a.count(0)
                    val sep = separatorOf(if (a.size > 2) a.keyword(2) else "SPACE", a)
                    val type = if (a.size > 3) a.keyword(3) else "RANDOM"
                    val lo = if (a.size > 4) a.num(4) else GenNum.ZERO
                    val hi = if (a.size > 5) a.num(5) else GenNum.of(1_000_000_000L)
                    if (lo > hi) a.fail("min > max")
                    val values = buildArray(type, n, lo, hi, a)
                    a.bindAndEmit(1, Bound.Arr(values), values.joinToString(sep))
                }
                "array2d" -> {
                    val rows = a.count(0); val cols = a.count(1)
                    if (rows.toLong() * cols > MAX_ELEMENTS)
                        a.fail("rows * cols exceeds $MAX_ELEMENTS")
                    val lo = a.num(2); val hi = a.num(3)
                    if (lo > hi) a.fail("min > max")
                    val sep = separatorOf(if (a.size > 5) a.keyword(5) else "SPACE", a)
                    val type = if (a.size > 6) a.keyword(6) else "RANDOM"
                    val lines = (0 until rows).map { buildArray(type, cols, lo, hi, a).joinToString(sep) }
                    a.bindAndEmit(4, Bound.Rows(lines), lines.joinToString("\n"))
                }
                "tree" -> {
                    val n = a.count(0)
                    val type = if (a.size > 2) a.keyword(2) else "RANDOM"
                    val edges = a.guarded { GenStructures.tree(n, type, rng) }
                    val lines = edges.map { it.render() }
                    a.bindAndEmit(1, Bound.Rows(lines), lines.joinToString("\n"))
                }
                "graph" -> {
                    val n = a.count(0); val m = a.count(1)
                    val type = if (a.size > 3) a.keyword(3) else "SIMPLE"
                    val directed = if (a.size > 4) a.bool(4) else false
                    val weighted = if (a.size > 5) a.bool(5) else false
                    val wmin = if (a.size > 6) a.num(6).toLongOrNull() ?: a.fail("wmin too large") else 1L
                    val wmax = if (a.size > 7) a.num(7).toLongOrNull() ?: a.fail("wmax too large") else 1_000_000_000L
                    if (weighted && wmin > wmax) a.fail("wmin > wmax")
                    val edges = a.guarded {
                        GenStructures.graph(n, m, type, directed, rng, weighted, wmin, wmax)
                    }
                    val lines = edges.map { it.render() }
                    a.bindAndEmit(2, Bound.Rows(lines), lines.joinToString("\n"))
                }
                "bipartite_graph" -> {
                    val n1 = a.count(0); val n2 = a.count(1); val m = a.count(2)
                    val directed = if (a.size > 4) a.bool(4) else false
                    val edges = a.guarded { GenStructures.bipartiteGraph(n1, n2, m, directed, rng) }
                    val lines = edges.map { it.render() }
                    a.bindAndEmit(3, Bound.Rows(lines), lines.joinToString("\n"))
                }
                "anti_hash_int" -> {
                    val n = a.count(0)
                    val prime = if (a.size > 2) a.num(2).toLongOrNull() ?: a.fail("prime too large")
                                else GenStructures.bucketPrimeFor(n)
                    if (prime <= 0) a.fail("prime must be positive")
                    val values = a.guarded { GenStructures.antiHashInts(n, prime) }.map { GenNum.of(it) }
                    a.bindAndEmit(1, Bound.Arr(values), values.joinToString(" "))
                }
                "anti_hash_str" -> {
                    val len = a.count(0)
                    val (s1, s2) = TestCaseGenerators.thueMorseAntiHash(len)
                    // 라벨 둘 — 각각 한 줄로 출력 (충돌하는 문자열 쌍).
                    // 구분 개행은 '둘 다 실제로 출력될 때'만 넣는다. _라벨로 숨긴
                    // 쪽이 있는데도 개행을 넣으면 아무것도 출력하지 않기로 한 라벨이
                    // 출력을 내는 셈이 된다 (이슈 #36 명세: _라벨은 묶기만 한다).
                    val firstShown = !a.labelHidden(1)
                    val secondShown = !a.labelHidden(2)
                    a.bindAndEmit(1, Bound.Str(s1), s1)
                    if (firstShown && secondShown) a.emitRaw("\n")
                    a.bindAndEmit(2, Bound.Str(s2), s2)
                }
                else -> {
                    val macro = macros[call.name]
                        ?: throw FormatException(unknownFunctionMessage(call.name), call.line, call.col)
                    expandMacro(macro, call, scope)
                }
            }
        }

        /**
         * 매크로 전개 — 인자를 값으로 평가해 파라미터에 묶고 본문을 실행한다.
         * 본문에서 만든 라벨은 호출 지역이라 바깥 라벨과 충돌하지 않는다
         * (같은 헬퍼를 여러 번 불러도 서로 덮어쓰지 않음).
         */
        private fun expandMacro(macro: Macro, call: Node.Call, scope: MutableMap<String, Bound>) {
            if (call.args.size != macro.params.size) throw FormatException(
                "${macro.name}() expects ${macro.params.size} argument(s), got ${call.args.size}",
                call.line, call.col)
            val local = HashMap<String, Bound>(scope)   // 바깥 라벨은 읽을 수 있게 상속
            for ((i, param) in macro.params.withIndex()) {
                val a = call.args[i]
                val value = when {
                    a.expr != null -> evalExpr(a.expr, scope)
                    a.ident != null -> evalExpr(Expr.Ref(a.ident, a.line, a.col), scope)
                    else -> throw FormatException(
                        "${macro.name}(): argument #${i + 1} must be a number", call.line, call.col)
                }
                local[param] = Bound.Scalar(value)
            }
            run(macro.body, local)   // 지역 스코프 — 바깥으로 새지 않는다
        }

        private fun buildArray(type: String, n: Int, lo: GenNum, hi: GenNum, a: CallArgs): List<GenNum> =
            when (type) {
                "RANDOM" -> List(n) { lo.rangeTo(hi, rng) }
                // 연속 수열은 n개가 [lo,hi] 안에 들어가야 한다. 예전에는 클램프 없이
                // lo+i / hi-i를 그대로 내보내 선언한 범위를 넘었다 (음수까지 내려감).
                // 조용히 범위를 벗어나느니 왜 안 되는지 알려주는 편이 낫다 (이슈 #36).
                "INCREASING", "DECREASING" -> {
                    val span = hi - lo + GenNum.of(1L)
                    if (span < GenNum.of(n.toLong())) a.fail(
                        "$type needs a range wide enough for $n values, but [$lo, $hi] holds only $span"
                    )
                    if (type == "INCREASING") List(n) { lo + GenNum.of(it.toLong()) }
                    else List(n) { hi - GenNum.of(it.toLong()) }
                }
                "CONSTANT" -> List(n) { lo }
                "PERM0" -> MutableList(n) { GenNum.of(it.toLong()) }.also { it.shuffle(rng) }
                "PERM1" -> MutableList(n) { GenNum.of((it + 1).toLong()) }.also { it.shuffle(rng) }
                "QUICKSORT_KILLER" -> TestCaseGenerators.quicksortKiller(n).map { GenNum.of(it.toLong()) }
                else -> a.fail("unknown array type '$type' " +
                    "(RANDOM, INCREASING, DECREASING, CONSTANT, PERM0, PERM1, QUICKSORT_KILLER)")
            }

        private fun separatorOf(keyword: String, a: CallArgs): String = when (keyword) {
            "SPACE" -> " "
            "NEWLINE" -> "\n"
            "COMMA" -> ","
            "NONE" -> ""
            else -> a.fail("unknown separator '$keyword' (SPACE, NEWLINE, COMMA, NONE)")
        }

        /** "a-z0-9" 같은 범위 표기를 문자 목록으로 */
        private fun expandAlphabet(spec: String): List<Char> {
            val chars = mutableListOf<Char>()
            var i = 0
            while (i < spec.length) {
                if (i + 2 < spec.length && spec[i + 1] == '-') {
                    val from = spec[i]; val to = spec[i + 2]
                    if (from <= to) for (c in from..to) chars.add(c) else chars.add(spec[i])
                    i += 3
                } else { chars.add(spec[i]); i++ }
            }
            return chars
        }

        fun emitValue(text: String) = emit(text)
        fun evaluate(e: Expr, scope: Map<String, Bound>) = evalExpr(e, scope)

        /** 호출 인자 접근 도우미 — 위치별로 수식·라벨·키워드·문자열 중 맞는 형태로 읽는다 */
        private class CallArgs(
            val call: Node.Call,
            val scope: MutableMap<String, Bound>,
            val ev: Evaluator
        ) {
            val size: Int get() = call.args.size

            fun fail(message: String): Nothing =
                throw FormatException("${call.name}(): $message", call.line, call.col)

            private fun arg(i: Int): Arg =
                call.args.getOrNull(i) ?: fail("missing argument #${i + 1}")

            fun num(i: Int): GenNum {
                val a = arg(i)
                a.expr?.let { return ev.evaluate(it, scope) }
                // 라벨 하나만 쓴 형태도 수식으로 취급 (rand(n, 1000, m)의 n)
                a.ident?.let { return ev.evaluate(Expr.Ref(it, a.line, a.col), scope) }
                fail("argument #${i + 1} must be a number")
            }

            /** 배열 길이·반복 횟수처럼 Int여야 하는 자리 */
            fun count(i: Int): Int {
                val n = num(i).toIntOrNull() ?: fail("length must fit in 32 bits")
                if (n < 0) fail("length must be >= 0")
                if (n > MAX_ELEMENTS) fail("length exceeds $MAX_ELEMENTS")
                return n
            }

            fun keyword(i: Int): String = arg(i).ident?.uppercase()
                ?: fail("argument #${i + 1} must be a keyword")

            fun str(i: Int): String = arg(i).str
                ?: fail("argument #${i + 1} must be a quoted string")

            fun bool(i: Int): Boolean = when (keyword(i)) {
                "TRUE", "YES" -> true
                "FALSE", "NO" -> false
                else -> fail("argument #${i + 1} must be true or false")
            }

            /** 구조 생성기의 인자 검증 실패를 DSL 위치가 실린 오류로 바꾼다 */
            fun <T> guarded(block: () -> T): T = try { block() }
                catch (e: IllegalArgumentException) { fail(e.message ?: "invalid arguments") }

            fun emitRaw(text: String) = ev.emitValue(text)

            /** 라벨 위치에 값을 묶고, `_` 로 시작하지 않으면 출력도 한다 */
            fun bindAndEmit(labelIndex: Int, value: Bound, text: String) {
                val label = arg(labelIndex).ident ?: fail("argument #${labelIndex + 1} must be a label name")
                scope[label] = value
                if (!label.startsWith("_")) ev.emitValue(text)
            }

            /** 해당 위치의 라벨이 _접두사(출력 안 함)인가 */
            fun labelHidden(labelIndex: Int): Boolean =
                arg(labelIndex).ident?.startsWith("_") == true
        }
    }
}
