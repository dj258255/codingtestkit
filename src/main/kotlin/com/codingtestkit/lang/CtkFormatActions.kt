package com.codingtestkit.lang

import com.codingtestkit.service.I18n
import com.codingtestkit.service.TestFormatInterpreter
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JTextField

/**
 * .ctk-format 파일에 대한 액션들 (이슈 #36).
 *
 * 생성 결과를 테스트 패널에 바로 넣는 대신 클립보드/미리보기로 돌려주는 이유:
 * 파일은 어느 문제 폴더에서도 열 수 있어서 어떤 문제의 테스트 패널로 보낼지가
 * 모호하다. 대신 결과를 보여주고 복사하게 하며, 테스트 패널 쪽에서는 생성
 * 다이얼로그의 고급 탭이 같은 인터프리터를 호출한다.
 */

/** 결과 미리보기 — 시드를 바꿔가며 즉시 다시 생성할 수 있다 */
class CtkFormatPreviewDialog(
    private val project: Project?,
    private val source: String
) : DialogWrapper(project, true) {

    private val seedField = JTextField("", 10)
    private val output = JEditorPane("text/plain", "")

    init {
        title = I18n.t("생성 결과 미리보기", "Generated Input Preview")
        setOKButtonText(I18n.t("클립보드로 복사", "Copy to Clipboard"))
        init()
        regenerate()
    }

    override fun createCenterPanel(): JComponent {
        val panel = javax.swing.JPanel(java.awt.BorderLayout(JBUI.scale(4), JBUI.scale(4)))
        val top = javax.swing.JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT))
        top.add(javax.swing.JLabel(I18n.t("시드 (비우면 랜덤):", "Seed (blank = random):")))
        top.add(seedField)
        val regen = javax.swing.JButton(I18n.t("다시 생성", "Regenerate"))
        regen.addActionListener { regenerate() }
        top.add(regen)
        panel.add(top, java.awt.BorderLayout.NORTH)

        output.isEditable = false
        output.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, JBUI.scaleFontSize(12f))
        val scroll = JBScrollPane(output)
        scroll.preferredSize = Dimension(JBUI.scale(680), JBUI.scale(420))
        panel.add(scroll, java.awt.BorderLayout.CENTER)
        return panel
    }

    private fun regenerate() {
        val seed = seedField.text.trim().toLongOrNull()
        output.text = try {
            val text = TestFormatInterpreter.generate(source, seed)
            // 거대한 출력을 통째로 그리면 UI가 멈춘다 — 앞부분만 보여준다
            if (text.length > 200_000)
                text.take(200_000) + "\n\n… " + I18n.t(
                    "출력이 커서 앞부분만 표시합니다 (전체 ${text.length}자). 복사는 전체가 됩니다.",
                    "Output truncated for display (total ${text.length} chars). Copying yields the full text.")
            else text
        } catch (e: TestFormatInterpreter.FormatException) {
            I18n.t("오류: ", "Error: ") + e.message
        }
        output.caretPosition = 0
    }

    override fun doOKAction() {
        val seed = seedField.text.trim().toLongOrNull()
        try {
            val text = TestFormatInterpreter.generate(source, seed)
            java.awt.Toolkit.getDefaultToolkit().systemClipboard
                .setContents(java.awt.datatransfer.StringSelection(text), null)
            super.doOKAction()
        } catch (e: TestFormatInterpreter.FormatException) {
            Messages.showErrorDialog(project, e.message, I18n.t("생성 실패", "Generation Failed"))
        }
    }
}

/** 편집 중인 .ctk-format을 실행해 결과를 미리보기로 */
class GenerateFromFormatFileAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isFormatFile(e.getData(CommonDataKeys.VIRTUAL_FILE))
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project
        // 저장 전 편집 내용도 그대로 실행되도록 문서에서 읽는다
        val document = FileDocumentManager.getInstance().getDocument(file)
        val source = document?.text ?: String(file.contentsToByteArray(), file.charset)

        val error = TestFormatInterpreter.validate(source)
        if (error != null) {
            Messages.showErrorDialog(project, error.message, I18n.t("형식 오류", "Format Error"))
            return
        }
        CtkFormatPreviewDialog(project, source).show()
    }

    private fun isFormatFile(file: VirtualFile?): Boolean =
        file != null && !file.isDirectory && file.extension == "ctk-format"
}

/** 문법 레퍼런스 — 편집기 옆에 두고 보도록 모달이 아닌 창으로 띄운다 */
class ShowFormatDocsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        showDocs(e.project)
    }

    companion object {
        fun showDocs(project: Project?) {
            val pane = JEditorPane("text/html", CtkFormatDocs.html()).apply {
                isEditable = false
                caretPosition = 0
            }
            val dialog = object : DialogWrapper(project, false) {
                init {
                    title = I18n.t("생성 형식 문법", "Generator Format Syntax")
                    init()
                }
                override fun createCenterPanel(): JComponent = JBScrollPane(pane).apply {
                    preferredSize = Dimension(JBUI.scale(720), JBUI.scale(560))
                }
                override fun createActions() = arrayOf(okAction)
            }
            dialog.isModal = false          // 편집기와 나란히 보게
            dialog.show()
        }
    }
}
