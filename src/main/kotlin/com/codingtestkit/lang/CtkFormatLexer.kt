package com.codingtestkit.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * .ctk-format 구문 강조용 렉서 (이슈 #36).
 *
 * PSI 트리를 만들지 않고 색칠에만 쓰이므로, 인터프리터의 파서와 별개로
 * 토큰 종류만 구분한다. 강조가 인터프리터 문법과 어긋나면 오해를 부르니
 * 함수 이름 목록은 CtkFormatTokens 한 곳에서 관리한다.
 */
class CtkFormatLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var startOffset = 0
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var currentToken: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = currentToken
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= endOffset) { currentToken = null; return }

        // 주석 문법은 두지 않는다 — '#'와 '.'는 격자 문제 입력의 실제 데이터라
        // 주석으로 삼으면 인터프리터(리터럴 통과)와 강조가 어긋난다
        val c = buffer[tokenStart]
        when {
            c == '"' -> {
                var i = tokenStart + 1
                while (i < endOffset && buffer[i] != '"') {
                    if (buffer[i] == '\\' && i + 1 < endOffset) i++
                    i++
                }
                tokenEnd = (i + 1).coerceAtMost(endOffset)
                currentToken = CtkFormatTokens.STRING
            }
            c.isDigit() -> {
                var i = tokenStart
                while (i < endOffset && buffer[i].isDigit()) i++
                tokenEnd = i
                currentToken = CtkFormatTokens.NUMBER
            }
            c.isLetter() || c == '_' -> {
                var i = tokenStart
                while (i < endOffset && (buffer[i].isLetterOrDigit() || buffer[i] == '_')) i++
                tokenEnd = i
                val word = buffer.subSequence(tokenStart, i).toString()
                // 뒤에 '('가 오면 호출 — 내장/키워드/사용자 매크로를 구분해 칠한다
                var j = i
                while (j < endOffset && (buffer[j] == ' ' || buffer[j] == '\t')) j++
                val isCall = j < endOffset && buffer[j] == '('
                currentToken = when {
                    word in CtkFormatTokens.KEYWORDS -> CtkFormatTokens.KEYWORD
                    word in CtkFormatTokens.CONSTANTS -> CtkFormatTokens.CONSTANT
                    isCall && word in CtkFormatTokens.BUILTINS -> CtkFormatTokens.BUILTIN
                    isCall -> CtkFormatTokens.MACRO_CALL
                    word.startsWith("_") -> CtkFormatTokens.HIDDEN_LABEL
                    else -> CtkFormatTokens.LABEL
                }
            }
            c == '(' || c == ')' || c == '{' || c == '}' || c == ',' -> {
                tokenEnd = tokenStart + 1
                currentToken = CtkFormatTokens.PUNCTUATION
            }
            c == '+' || c == '-' || c == '*' || c == '/' -> {
                tokenEnd = tokenStart + 1
                currentToken = CtkFormatTokens.OPERATOR
            }
            else -> {                                        // 그 밖은 전부 리터럴 텍스트
                var i = tokenStart
                while (i < endOffset && !isTokenStart(buffer[i])) i++
                tokenEnd = if (i == tokenStart) tokenStart + 1 else i
                currentToken = CtkFormatTokens.TEXT
            }
        }
    }

    private fun isTokenStart(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '"' ||
            c == '(' || c == ')' || c == '{' || c == '}' || c == ',' ||
            c == '+' || c == '-' || c == '*' || c == '/'
}
