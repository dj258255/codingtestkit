package com.codingtestkit.ui

import com.codingtestkit.model.Language
import com.codingtestkit.service.I18n
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * 커스텀 체커 설정 다이얼로그 (이슈 #36).
 *
 * 복수 정답이 허용되는 문제를 위해, 문자열 비교 대신 사용자가 작성한
 * 체커 프로그램이 판정한다. 체커는 stdin으로 아래 3개 섹션을 받는다:
 *
 *   <테스트 입력>
 *   ===CTK===
 *   <사용자 출력>
 *   ===CTK===
 *   <예상 출력 (없으면 빈 값)>
 *
 * 체커가 stdout 첫 줄에 "OK"(또는 "AC")를 출력하면 통과, 그 외는 실패.
 * 체커는 main이 있는 완전한 stdin 프로그램이어야 한다.
 */
class CheckerDialog(
    initialLanguage: Language,
    initialCode: String
) : DialogWrapper(true) {

    private val languageCombo = ComboBox(Language.entries.map { it.displayName }.toTypedArray()).apply {
        selectedIndex = Language.entries.indexOf(initialLanguage).coerceAtLeast(0)
    }

    private val codeArea = JTextArea(initialCode.ifBlank { defaultTemplate() }, 18, 70).apply {
        font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
        border = JBUI.Borders.empty(6)
    }

    init {
        title = I18n.t("커스텀 체커 (복수 정답 판정)", "Custom Checker (multiple valid answers)")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(6)))
        panel.preferredSize = Dimension(JBUI.scale(640), JBUI.scale(440))

        val top = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
        top.add(JLabel(I18n.t("체커 언어:", "Checker language:")))
        top.add(languageCombo)
        top.add(JLabel("<html><i>${I18n.t(
            "코드를 비우고 저장하면 체커가 해제됩니다",
            "Save with empty code to disable the checker"
        )}</i></html>").apply { foreground = com.intellij.ui.JBColor.GRAY })
        panel.add(top, BorderLayout.NORTH)

        panel.add(JBScrollPane(codeArea), BorderLayout.CENTER)

        panel.add(JLabel("<html>${I18n.t(
            "stdin: 입력 → <b>===CTK===</b> → 사용자 출력 → <b>===CTK===</b> → 예상 출력. " +
                "첫 줄에 <b>OK</b> 출력 시 통과.",
            "stdin: input → <b>===CTK===</b> → user output → <b>===CTK===</b> → expected output. " +
                "Print <b>OK</b> on the first line to pass."
        )}</html>").apply {
            foreground = com.intellij.ui.JBColor.GRAY
            font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
        }, BorderLayout.SOUTH)

        return panel
    }

    fun getLanguage(): Language = Language.entries[languageCombo.selectedIndex]

    /** 저장된 체커 코드 (기본 템플릿 그대로면 미설정으로 간주하지 않음 — 사용자가 수정해서 쓰는 출발점) */
    fun getCode(): String = codeArea.text

    private fun defaultTemplate(): String = """
        |import sys
        |
        |sections = sys.stdin.read().split("===CTK===")
        |inp = sections[0].strip() if len(sections) > 0 else ""
        |user_out = sections[1].strip() if len(sections) > 1 else ""
        |expected = sections[2].strip() if len(sections) > 2 else ""
        |
        |# 여기에 판정 로직 작성 — 예: 순서 무관 비교
        |ok = sorted(user_out.split()) == sorted(expected.split())
        |
        |print("OK" if ok else "WRONG")
    """.trimMargin()
}
