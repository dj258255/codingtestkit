package com.codingtestkit.ui

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.icons.AllIcons
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

class SettingsPanel(private val project: Project) : JPanel() {

    private val autoCompleteToggle = JCheckBox("자동완성 (Auto Complete)")
    private val inspectionToggle = JCheckBox("코드 검사 (Inspections)")
    private val pasteBlockToggle = JCheckBox("외부 붙여넣기 차단")
    private val focusAlertToggle = JCheckBox("포커스 이탈 감지")

    private var focusListener: WindowAdapter? = null
    private var focusLostCount = 0

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(8)

        // 제목
        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(24))
        }
        titlePanel.add(JLabel("알고리즘 풀이 설정").apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(15f).toFloat())
        })
        add(titlePanel)
        add(Box.createVerticalStrut(JBUI.scale(6)))

        // 토글 섹션
        val toggleSection = createSection("코딩 환경")
        autoCompleteToggle.alignmentX = LEFT_ALIGNMENT
        autoCompleteToggle.isSelected = CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP
        autoCompleteToggle.addActionListener { toggleAutoComplete() }
        toggleSection.add(autoCompleteToggle)
        toggleSection.add(Box.createVerticalStrut(JBUI.scale(2)))

        inspectionToggle.alignmentX = LEFT_ALIGNMENT
        inspectionToggle.isSelected = !PowerSaveMode.isEnabled()
        inspectionToggle.addActionListener { toggleInspections() }
        toggleSection.add(inspectionToggle)
        toggleSection.add(Box.createVerticalStrut(JBUI.scale(2)))

        pasteBlockToggle.alignmentX = LEFT_ALIGNMENT
        pasteBlockToggle.toolTipText = "외부 프로그램에서 복사한 텍스트 붙여넣기를 차단합니다"
        pasteBlockToggle.addActionListener { togglePasteBlock() }
        toggleSection.add(pasteBlockToggle)
        toggleSection.add(Box.createVerticalStrut(JBUI.scale(2)))

        focusAlertToggle.alignmentX = LEFT_ALIGNMENT
        focusAlertToggle.toolTipText = "IDE 창에서 포커스가 벗어나면 경고를 표시합니다"
        focusAlertToggle.addActionListener { toggleFocusAlert() }
        toggleSection.add(focusAlertToggle)
        toggleSection.add(Box.createVerticalStrut(JBUI.scale(4)))

        // 프리셋 버튼
        val presetPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        val examModeBtn = JButton("시험 모드", AllIcons.General.Warning).apply {
            toolTipText = "자동완성, 코드 검사 모두 끄기"
            putClientProperty("JButton.buttonType", "roundRect")
        }
        val normalModeBtn = JButton("일반 모드", AllIcons.General.InspectionsOK).apply {
            toolTipText = "자동완성, 코드 검사 모두 켜기"
            putClientProperty("JButton.buttonType", "roundRect")
        }

        examModeBtn.addActionListener {
            setAutoComplete(false)
            setInspections(false)
            setPasteBlock(true)
            setFocusAlert(true)
        }
        normalModeBtn.addActionListener {
            setAutoComplete(true)
            setInspections(true)
            setPasteBlock(false)
            setFocusAlert(false)
        }

        presetPanel.add(examModeBtn)
        presetPanel.add(normalModeBtn)
        toggleSection.add(presetPanel)
        add(toggleSection)
        add(Box.createVerticalStrut(JBUI.scale(6)))

        // 도움말
        val helpSection = createSection("도움말")
        helpSection.add(createHelpLine(AllIcons.General.Information,
            "자동완성 끄기: 타이핑 시 자동완성 팝업이 나타나지 않습니다"))
        helpSection.add(Box.createVerticalStrut(JBUI.scale(2)))
        helpSection.add(createHelpLine(AllIcons.General.Information,
            "코드 검사 끄기: 절전 모드를 활성화하여 백그라운드 분석을 중지합니다"))
        helpSection.add(Box.createVerticalStrut(JBUI.scale(2)))
        helpSection.add(createHelpLine(AllIcons.General.Information,
            "두 기능을 모두 끄면 코딩 테스트 환경과 유사하게 연습할 수 있습니다"))
        helpSection.add(Box.createVerticalStrut(JBUI.scale(2)))
        helpSection.add(createHelpLine(AllIcons.General.Information,
            "붙여넣기 차단: 외부에서 복사한 코드의 붙여넣기를 방지합니다"))
        helpSection.add(Box.createVerticalStrut(JBUI.scale(2)))
        helpSection.add(createHelpLine(AllIcons.General.Information,
            "포커스 감지: IDE를 벗어나면 경고를 표시합니다 (실제 시험 환경과 동일)"))
        add(helpSection)
        add(Box.createVerticalStrut(JBUI.scale(6)))

        // 감지된 도구 경로
        val pathSection = createSection("감지된 도구 경로")
        val paths = com.codingtestkit.service.CodeRunner.getDetectedPaths()
        for ((name, path) in paths) {
            val found = path.isNotBlank() && (name == "JAVA_HOME" || java.io.File(path).exists())
            pathSection.add(createPathLine(name, path, found))
            pathSection.add(Box.createVerticalStrut(JBUI.scale(1)))
        }
        add(pathSection)

        add(Box.createVerticalGlue())
    }

    private fun createSection(title: String): JPanel {
        return object : JPanel() {
            override fun getMaximumSize(): Dimension {
                return Dimension(super.getMaximumSize().width, preferredSize.height)
            }
        }.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                    JBUI.Borders.customLine(JBColor.border()),
                    " $title ",
                    javax.swing.border.TitledBorder.LEFT,
                    javax.swing.border.TitledBorder.TOP
                ),
                JBUI.Borders.empty(4, 8, 6, 8)
            )
        }
    }

    private fun createHelpLine(icon: Icon, text: String): JPanel {
        return JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(20))
            val iconLabel = JLabel(icon).apply {
                preferredSize = Dimension(JBUI.scale(16), JBUI.scale(16))
            }
            add(iconLabel, BorderLayout.WEST)
            add(JLabel(text).apply {
                font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
                foreground = JBColor.GRAY
            }, BorderLayout.CENTER)
        }
    }

    private fun createPathLine(name: String, path: String, found: Boolean): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(20))
            add(JLabel(if (found) AllIcons.General.InspectionsOK else AllIcons.General.Error).apply {
                preferredSize = Dimension(JBUI.scale(16), JBUI.scale(16))
            })
            add(JLabel(name).apply {
                font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
            })
            add(JLabel(path.ifBlank { "찾을 수 없음" }).apply {
                font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
                foreground = if (found) JBColor.GRAY else JBColor(Color(200, 80, 80), Color(230, 100, 100))
            })
        }
    }

    private fun toggleAutoComplete() {
        setAutoComplete(autoCompleteToggle.isSelected)
    }

    private fun toggleInspections() {
        setInspections(inspectionToggle.isSelected)
    }

    private fun setAutoComplete(enabled: Boolean) {
        val settings = CodeInsightSettings.getInstance()
        settings.AUTO_POPUP_COMPLETION_LOOKUP = enabled
        settings.AUTO_POPUP_PARAMETER_INFO = enabled
        settings.AUTO_POPUP_JAVADOC_INFO = enabled
        autoCompleteToggle.isSelected = enabled
    }

    private fun setInspections(enabled: Boolean) {
        PowerSaveMode.setEnabled(!enabled)
        inspectionToggle.isSelected = enabled
    }

    private var originalPasteHandler: EditorActionHandler? = null

    private fun togglePasteBlock() {
        setPasteBlock(pasteBlockToggle.isSelected)
    }

    private fun setPasteBlock(enabled: Boolean) {
        pasteBlockToggle.isSelected = enabled
        ExamModeState.pasteBlockEnabled = enabled

        // 최초 활성화 시 paste handler 설치 (이후에는 flag로만 제어)
        if (enabled && originalPasteHandler == null) {
            val manager = EditorActionManager.getInstance()
            originalPasteHandler = manager.getActionHandler(IdeActions.ACTION_EDITOR_PASTE)
            val original = originalPasteHandler!!
            manager.setActionHandler(IdeActions.ACTION_EDITOR_PASTE, object : EditorActionHandler() {
                override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
                    if (ExamModeState.pasteBlockEnabled) {
                        // IntelliJ 내부 클립보드와 시스템 클립보드 비교
                        val internalText = try {
                            CopyPasteManager.getInstance().contents
                                ?.getTransferData(DataFlavor.stringFlavor) as? String
                        } catch (_: Exception) { null }

                        val systemText = try {
                            Toolkit.getDefaultToolkit().systemClipboard
                                .getData(DataFlavor.stringFlavor) as? String
                        } catch (_: Exception) { null }

                        if (internalText != systemText) {
                            // 외부에서 복사된 내용 → 차단
                            Toolkit.getDefaultToolkit().beep()
                            return
                        }
                    }
                    original.execute(editor, caret, dataContext)
                }

                override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
                    return original.isEnabled(editor, caret, dataContext)
                }
            })
        }
    }

    private fun toggleFocusAlert() {
        setFocusAlert(focusAlertToggle.isSelected)
    }

    private fun setFocusAlert(enabled: Boolean) {
        focusAlertToggle.isSelected = enabled
        ExamModeState.focusAlertEnabled = enabled

        val frame = SwingUtilities.getWindowAncestor(this) as? java.awt.Window ?: return

        if (enabled) {
            focusLostCount = 0
            focusListener = object : WindowAdapter() {
                override fun windowDeactivated(e: WindowEvent) {
                    if (ExamModeState.focusAlertEnabled) {
                        focusLostCount++
                        SwingUtilities.invokeLater {
                            JOptionPane.showMessageDialog(
                                frame,
                                "IDE 창을 벗어났습니다! (${focusLostCount}회)\n실제 시험에서는 부정행위로 간주될 수 있습니다.",
                                "포커스 이탈 감지",
                                JOptionPane.WARNING_MESSAGE
                            )
                        }
                    }
                }
            }
            frame.addWindowListener(focusListener)
        } else {
            focusListener?.let { frame.removeWindowListener(it) }
            focusListener = null
        }
    }

    object ExamModeState {
        var pasteBlockEnabled = false
        var focusAlertEnabled = false
    }
}
