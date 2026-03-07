package com.codingtestkit.ui

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.icons.AllIcons
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class SettingsPanel(private val project: Project) : JPanel() {

    private val autoCompleteToggle = JCheckBox("자동완성 (Auto Complete)")
    private val inspectionToggle = JCheckBox("코드 검사 (Inspections)")

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12)

        // 제목
        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(30))
        }
        titlePanel.add(JLabel("알고리즘 풀이 설정").apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(15f).toFloat())
        })
        add(titlePanel)
        add(Box.createVerticalStrut(JBUI.scale(12)))

        // 토글 섹션
        val toggleSection = createSection("코딩 환경")
        autoCompleteToggle.alignmentX = LEFT_ALIGNMENT
        autoCompleteToggle.isSelected = CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP
        autoCompleteToggle.addActionListener { toggleAutoComplete() }
        toggleSection.add(autoCompleteToggle)
        toggleSection.add(Box.createVerticalStrut(JBUI.scale(4)))

        inspectionToggle.alignmentX = LEFT_ALIGNMENT
        inspectionToggle.isSelected = !PowerSaveMode.isEnabled()
        inspectionToggle.addActionListener { toggleInspections() }
        toggleSection.add(inspectionToggle)
        toggleSection.add(Box.createVerticalStrut(JBUI.scale(8)))

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
        }
        normalModeBtn.addActionListener {
            setAutoComplete(true)
            setInspections(true)
        }

        presetPanel.add(examModeBtn)
        presetPanel.add(normalModeBtn)
        toggleSection.add(presetPanel)
        add(toggleSection)
        add(Box.createVerticalStrut(JBUI.scale(12)))

        // 도움말
        val helpSection = createSection("도움말")
        helpSection.add(createHelpLine(AllIcons.General.Information,
            "자동완성 끄기: 타이핑 시 자동완성 팝업이 나타나지 않습니다"))
        helpSection.add(Box.createVerticalStrut(JBUI.scale(4)))
        helpSection.add(createHelpLine(AllIcons.General.Information,
            "코드 검사 끄기: 절전 모드를 활성화하여 백그라운드 분석을 중지합니다"))
        helpSection.add(Box.createVerticalStrut(JBUI.scale(4)))
        helpSection.add(createHelpLine(AllIcons.General.Tip,
            "두 기능을 모두 끄면 코딩 테스트 환경과 유사하게 연습할 수 있습니다"))
        add(helpSection)
        add(Box.createVerticalStrut(JBUI.scale(12)))

        // 감지된 도구 경로
        val pathSection = createSection("감지된 도구 경로")
        val paths = com.codingtestkit.service.CodeRunner.getDetectedPaths()
        for ((name, path) in paths) {
            val found = path.isNotBlank() && (name == "JAVA_HOME" || java.io.File(path).exists())
            pathSection.add(createPathLine(name, path, found))
            pathSection.add(Box.createVerticalStrut(JBUI.scale(2)))
        }
        add(pathSection)

        add(Box.createVerticalGlue())
    }

    private fun createSection(title: String): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                    JBUI.Borders.customLine(JBColor.border()),
                    " $title ",
                    javax.swing.border.TitledBorder.LEFT,
                    javax.swing.border.TitledBorder.TOP
                ),
                JBUI.Borders.empty(6, 8, 8, 8)
            )
        }
    }

    private fun createHelpLine(icon: Icon, text: String): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(22))
            add(JLabel(icon))
            add(JLabel(text).apply {
                font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
                foreground = JBColor.GRAY
            })
        }
    }

    private fun createPathLine(name: String, path: String, found: Boolean): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(22))
            add(JLabel(if (found) AllIcons.General.InspectionsOK else AllIcons.General.Error))
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
}
