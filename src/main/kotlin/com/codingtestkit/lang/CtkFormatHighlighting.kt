package com.codingtestkit.lang

import com.codingtestkit.service.I18n
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as D
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import javax.swing.Icon

/**
 * .ctk-format 파일 타입과 구문 강조 (이슈 #36).
 *
 * PSI/파서 없이 렉서 기반 강조만 제공한다 — 이 파일은 편집·생성 입력일 뿐
 * 코드 완성이나 리팩터링 대상이 아니라서, ParserDefinition까지 만들 이유가 없다.
 * 확장자는 플러그인 고유(ctk-format)라 다른 도구의 .format 파일을 건드리지 않는다.
 */
object CtkFormatFileType : FileType {
    override fun getName(): String = "CtkFormat"
    override fun getDescription(): String =
        I18n.t("CodingTestKit 테스트 입력 생성 형식", "CodingTestKit test input format")
    override fun getDefaultExtension(): String = "ctk-format"
    override fun getIcon(): Icon? = com.intellij.icons.AllIcons.FileTypes.Text
    override fun isBinary(): Boolean = false
    override fun isReadOnly(): Boolean = false
}

/** 토큰 → 색상 매핑. 기존 언어의 표준 색을 재사용해 어떤 테마에서도 어울린다. */
class CtkFormatSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = CtkFormatLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {
            CtkFormatTokens.BUILTIN -> pack(BUILTIN)
            CtkFormatTokens.MACRO_CALL -> pack(MACRO_CALL)
            CtkFormatTokens.KEYWORD -> pack(KEYWORD)
            CtkFormatTokens.CONSTANT -> pack(CONSTANT)
            CtkFormatTokens.LABEL -> pack(LABEL)
            CtkFormatTokens.HIDDEN_LABEL -> pack(HIDDEN_LABEL)
            CtkFormatTokens.NUMBER -> pack(NUMBER)
            CtkFormatTokens.STRING -> pack(STRING)
            CtkFormatTokens.OPERATOR -> pack(OPERATOR)
            CtkFormatTokens.PUNCTUATION -> pack(PUNCTUATION)
            else -> TextAttributesKey.EMPTY_ARRAY   // 리터럴 텍스트는 기본색 그대로
        }

    companion object {
        val BUILTIN = TextAttributesKey.createTextAttributesKey("CTK_FORMAT_BUILTIN", D.KEYWORD)
        val MACRO_CALL = TextAttributesKey.createTextAttributesKey("CTK_FORMAT_MACRO", D.FUNCTION_CALL)
        val KEYWORD = TextAttributesKey.createTextAttributesKey("CTK_FORMAT_KEYWORD", D.KEYWORD)
        val CONSTANT = TextAttributesKey.createTextAttributesKey("CTK_FORMAT_CONSTANT", D.STATIC_FIELD)
        val LABEL = TextAttributesKey.createTextAttributesKey("CTK_FORMAT_LABEL", D.LOCAL_VARIABLE)
        // 출력되지 않는 라벨은 주석색으로 — 눈으로 바로 구분된다
        val HIDDEN_LABEL = TextAttributesKey.createTextAttributesKey("CTK_FORMAT_HIDDEN_LABEL", D.LINE_COMMENT)
        val NUMBER = TextAttributesKey.createTextAttributesKey("CTK_FORMAT_NUMBER", D.NUMBER)
        val STRING = TextAttributesKey.createTextAttributesKey("CTK_FORMAT_STRING", D.STRING)
        val OPERATOR = TextAttributesKey.createTextAttributesKey("CTK_FORMAT_OPERATOR", D.OPERATION_SIGN)
        val PUNCTUATION = TextAttributesKey.createTextAttributesKey("CTK_FORMAT_PUNCTUATION", D.PARENTHESES)
    }
}

/** 파서 없는 파일 타입이라 EditorHighlighterProvider로 강조를 붙인다 */
class CtkFormatEditorHighlighterProvider : EditorHighlighterProvider {
    override fun getEditorHighlighter(
        project: Project?, fileType: FileType, virtualFile: VirtualFile?, colors: EditorColorsScheme
    ): EditorHighlighter = LexerEditorHighlighter(CtkFormatSyntaxHighlighter(), colors)
}
