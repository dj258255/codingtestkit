package com.codingtestkit.lang

import com.codingtestkit.service.I18n

/**
 * .ctk-format 문법 레퍼런스 (이슈 #36).
 *
 * 편집기 옆에 띄우는 문서와 새 파일의 주석 없는 시작 템플릿을 함께 관리한다.
 * 함수 목록이 인터프리터와 어긋나면 안 되므로 CtkFormatTokens의 이름 집합을
 * 기준으로 검증한다 (CtkFormatSyntaxTest).
 */
object CtkFormatDocs {

    /** 새 .ctk-format 파일의 초기 내용 — 이슈 예시를 그대로 담아 바로 실행 가능 */
    val STARTER_TEMPLATE = """
        const(3, t)
        repeat(t) {
        rand(1, 100, n) rand(n, 1000, m)
        array(n, a, SPACE, RANDOM, 0, 1000)
        array(m, b, SPACE, RANDOM, 0, 100)
        }
    """.trimIndent() + "\n"

    fun html(): String = buildString {
        append("<html><body style='font-family:sans-serif;'>")
        append("<h2>").append(I18n.t("생성 형식 문법", "Generator format syntax")).append("</h2>")
        append("<p>").append(I18n.t(
            "글자·공백·줄바꿈은 그대로 출력되고, <code>이름(...)</code> 호출만 생성값으로 바뀝니다. " +
                "마지막 인자의 <b>라벨</b>에 값이 묶여 뒤에서 다시 쓸 수 있고, " +
                "<code>_라벨</code>은 묶기만 하고 출력하지 않습니다.",
            "Literal characters, spaces, and newlines pass through; only <code>name(...)</code> calls are " +
                "replaced. The <b>label</b> argument binds the value for later reuse, and " +
                "<code>_label</code> binds without printing."
        )).append("</p>")

        section(I18n.t("값", "Values"), listOf(
            "const(value, label)" to I18n.t("고정값", "Fixed value"),
            "rand(min, max, label)" to I18n.t("정수 랜덤 (2^64 초과도 가능)", "Random integer (beyond 2^64 too)"),
            "rand_float(min, max, label, [decimals=6])" to I18n.t("실수 랜덤", "Random float"),
            "string(length, label, [alphabet=\"a-z\"])" to I18n.t("문자열", "String")
        ))

        section(I18n.t("배열", "Arrays"), listOf(
            "array(length, label, [sep=SPACE], [type=RANDOM], [min], [max])" to
                I18n.t("1차원 배열", "1-D array"),
            "array2d(rows, cols, min, max, label, [sep=SPACE], [type=RANDOM])" to
                I18n.t("2차원 배열 (행마다 한 줄)", "2-D array (one row per line)"),
            "perm0(n, label) / perm1(n, label)" to
                I18n.t("0..n-1 / 1..n 순열", "Permutation of 0..n-1 / 1..n")
        ))

        append("<p><b>type</b>: ")
        append("RANDOM, INCREASING, DECREASING, CONSTANT, PERM0, PERM1, QUICKSORT_KILLER<br>")
        append("<b>sep</b>: SPACE, NEWLINE, COMMA, NONE</p>")

        section(I18n.t("구조", "Structures"), listOf(
            "tree(n, label, [type=RANDOM])" to
                I18n.t("n-1개 간선을 \"u v\" 줄로 (1-based)", "n-1 edges as \"u v\" lines (1-based)"),
            "graph(n, m, label, [type=SIMPLE], [directed=false], [weighted=false], [wmin], [wmax])" to
                I18n.t("m개 간선", "m edges"),
            "bipartite_graph(n1, n2, m, label, [directed=false])" to
                I18n.t("왼쪽 1..n1, 오른쪽 n1+1..n1+n2", "Left 1..n1, right n1+1..n1+n2")
        ))

        append("<p><b>").append(I18n.t("트리 타입", "tree type")).append("</b>: ")
        append("RANDOM, STAR, PATH, BAMBOO, CATERPILLAR, BINARY<br>")
        append("<b>").append(I18n.t("그래프 타입", "graph type")).append("</b>: ")
        append("SIMPLE, CONNECTED, DAG, MULTI, TREE</p>")

        section(I18n.t("적대적 입력", "Adversarial"), listOf(
            "anti_hash_int(n, label, [prime])" to I18n.t(
                "std::unordered_map/set을 한 버킷에 몰아 O(N)로 만듦 (GCC libstdc++ 대상)",
                "Collapses std::unordered_map/set into one bucket, O(N) per op (targets GCC libstdc++)"),
            "anti_hash_str(minLength, labelA, labelB)" to I18n.t(
                "mod 2^64 다항 해시가 어떤 base에서도 충돌하는 두 문자열. " +
                    "충돌 성립에 2의 거듭제곱 길이가 필요해 실제 길이는 minLength 이상의 " +
                    "2의 거듭제곱(최소 4096)으로 올림된다",
                "Two strings colliding under mod-2^64 polynomial hashing for any base. " +
                    "The collision needs a power-of-two length, so the actual length is rounded up " +
                    "to a power of two of at least minLength (minimum 4096)"),
            "array(n, a, SPACE, QUICKSORT_KILLER)" to I18n.t(
                "고전 단일피벗 퀵정렬을 O(n²)로 (introsort/TimSort는 무효)",
                "Forces O(n²) on classic single-pivot quicksort (no effect on introsort/TimSort)")
        ))

        section(I18n.t("반복·매크로·집계", "Repeat, macros, aggregates"), listOf(
            "repeat(n) { ... }" to I18n.t("n번 반복 (중첩 가능)", "Repeat n times (nestable)"),
            "repeat(n as i) { ... }" to I18n.t("i는 0..n-1", "i runs 0..n-1"),
            "def name(a, b) { ... }" to I18n.t(
                "매크로 정의 — 안에서 만든 라벨은 호출마다 지역. 재귀·조건문 없음",
                "Macro definition — labels inside are local per call. No recursion or conditionals"),
            "sum(a) / min(a) / max(a) / len(a)" to I18n.t(
                "수식 어디서나. len은 문자열 길이·구조의 줄 수에도 통함",
                "Usable in any expression. len also works on string length and structure line count")
        ))

        append("<p>").append(I18n.t(
            "수식에는 <code>+ - * /</code>(내림 나눗셈)와 괄호를 쓸 수 있고, 앞서 만든 라벨을 " +
                "그대로 참조할 수 있습니다 — 예: <code>rand(n, 1000, m)</code>.",
            "Expressions support <code>+ - * /</code> (floor division) and parentheses, and can reference " +
                "earlier labels — e.g. <code>rand(n, 1000, m)</code>."
        )).append("</p>")

        append("<h3>").append(I18n.t("예시", "Example")).append("</h3>")
        append("<pre>").append(STARTER_TEMPLATE.trim()).append("</pre>")
        append("<p>").append(I18n.t(
            "위 형식은 t개 테스트마다 <code>n m</code> 한 줄과 길이 n·m 배열 두 줄을 만듭니다.",
            "That format produces, for each of t cases, a line <code>n m</code> and two arrays of length n and m."
        )).append("</p>")
        append("</body></html>")
    }

    private fun StringBuilder.section(title: String, rows: List<Pair<String, String>>) {
        append("<h3>").append(title).append("</h3><table cellpadding='3'>")
        for ((sig, desc) in rows) {
            append("<tr><td valign='top'><code><b>").append(escape(sig)).append("</b></code></td>")
            append("<td valign='top'>").append(desc).append("</td></tr>")
        }
        append("</table>")
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
