package com.codingtestkit.lang

import com.intellij.psi.tree.IElementType
import com.intellij.openapi.fileTypes.PlainTextLanguage

/**
 * .ctk-format 토큰 종류와 이름 목록 (이슈 #36).
 *
 * 이름 목록은 강조와 인터프리터가 어긋나지 않도록 여기 한 곳에만 둔다.
 * 새 생성 함수를 추가하면 TestFormatInterpreter와 BUILTINS를 함께 갱신해야
 * 하며, CtkFormatSyntaxTest가 둘의 불일치를 잡는다.
 */
object CtkFormatTokens {

    private class CtkTokenType(debugName: String) : IElementType(debugName, PlainTextLanguage.INSTANCE)

    val BUILTIN: IElementType = CtkTokenType("CTK_BUILTIN")
    val MACRO_CALL: IElementType = CtkTokenType("CTK_MACRO_CALL")
    val KEYWORD: IElementType = CtkTokenType("CTK_KEYWORD")
    val CONSTANT: IElementType = CtkTokenType("CTK_CONSTANT")
    val LABEL: IElementType = CtkTokenType("CTK_LABEL")
    val HIDDEN_LABEL: IElementType = CtkTokenType("CTK_HIDDEN_LABEL")
    val NUMBER: IElementType = CtkTokenType("CTK_NUMBER")
    val STRING: IElementType = CtkTokenType("CTK_STRING")
    val OPERATOR: IElementType = CtkTokenType("CTK_OPERATOR")
    val PUNCTUATION: IElementType = CtkTokenType("CTK_PUNCTUATION")
    val TEXT: IElementType = CtkTokenType("CTK_TEXT")

    /** 값을 만들어 출력하는 내장 함수들 */
    val GENERATORS = setOf(
        "const", "rand", "rand_float", "array", "array2d", "string",
        "perm0", "perm1", "tree", "graph", "bipartite_graph",
        "anti_hash_int", "anti_hash_str"
    )

    /** 수식 안에서 쓰는 집계 함수들 */
    val AGGREGATES = setOf("sum", "min", "max", "len")

    val BUILTINS = GENERATORS + AGGREGATES

    val KEYWORDS = setOf("repeat", "def", "as")

    /** 인자로 쓰이는 대문자 상수 (패턴·구분자·타입·불리언) */
    val CONSTANTS = setOf(
        "RANDOM", "INCREASING", "DECREASING", "CONSTANT", "PERM0", "PERM1", "QUICKSORT_KILLER",
        "SPACE", "NEWLINE", "COMMA", "NONE",
        "STAR", "PATH", "BAMBOO", "CATERPILLAR", "BINARY",
        "SIMPLE", "CONNECTED", "DAG", "MULTI", "TREE",
        "true", "false", "TRUE", "FALSE", "YES", "NO"
    )
}
