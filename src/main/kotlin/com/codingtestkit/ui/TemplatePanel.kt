package com.codingtestkit.ui

import com.codingtestkit.model.CodeTemplate
import com.codingtestkit.model.Language
import com.codingtestkit.service.TemplateService
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class TemplatePanel(private val project: Project) : JPanel(BorderLayout()) {

    private val templateList = JBList<String>()
    private val templateListModel = DefaultListModel<String>()
    private val nameField = JTextField().apply {
        toolTipText = "템플릿 이름을 입력하세요"
    }
    private val languageCombo = ComboBox(Language.entries.map { it.displayName }.toTypedArray())
    private val saveButton = JButton("저장", AllIcons.Actions.MenuSaveall).apply {
        toolTipText = "현재 에디터의 코드를 템플릿으로 저장"
    }
    private val loadButton = JButton("불러오기", AllIcons.Actions.Upload).apply {
        toolTipText = "선택한 템플릿을 에디터에 불러오기"
    }
    private val deleteButton = JButton(AllIcons.General.Remove).apply {
        toolTipText = "선택한 템플릿 삭제"
        preferredSize = Dimension(JBUI.scale(28), JBUI.scale(28))
    }
    private val previewArea = JTextArea().apply {
        isEditable = false
        font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
        background = JBColor(Color(245, 245, 245), Color(43, 43, 43))
        border = JBUI.Borders.empty(8)
        lineWrap = false
    }

    init {
        border = JBUI.Borders.empty()

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6, 8, 4, 8)
        }

        // Row 1: 이름 + 언어
        val row1 = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(32))
        }

        val namePanel = JPanel(BorderLayout(JBUI.scale(4), 0))
        namePanel.add(JLabel("이름:").apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
            foreground = JBColor.GRAY
        }, BorderLayout.WEST)
        namePanel.add(nameField, BorderLayout.CENTER)

        val langPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0))
        langPanel.add(JLabel("언어:").apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
            foreground = JBColor.GRAY
        })
        langPanel.add(languageCombo)

        row1.add(namePanel, BorderLayout.CENTER)
        row1.add(langPanel, BorderLayout.EAST)
        topPanel.add(row1)
        topPanel.add(Box.createVerticalStrut(JBUI.scale(4)))

        // Row 2: 저장 / 불러오기 / 삭제 버튼
        val row2 = JPanel(BorderLayout()).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(32))
        }

        val actionButtons = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        actionButtons.add(saveButton)
        actionButtons.add(loadButton)

        row2.add(actionButtons, BorderLayout.WEST)
        row2.add(deleteButton, BorderLayout.EAST)
        topPanel.add(row2)

        add(topPanel, BorderLayout.NORTH)

        // 중앙: 리스트 + 미리보기 (세로 분할)
        val centerPanel = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
            dividerSize = JBUI.scale(4)
            resizeWeight = 0.35
        }

        templateList.model = templateListModel
        templateList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        templateList.emptyText.text = "저장된 템플릿이 없습니다"
        templateList.cellRenderer = TemplateListRenderer()
        val listScrollPane = JBScrollPane(templateList).apply {
            minimumSize = Dimension(0, JBUI.scale(80))
        }
        centerPanel.topComponent = listScrollPane

        val previewScrollPane = JBScrollPane(previewArea).apply {
            minimumSize = Dimension(0, JBUI.scale(100))
        }

        // 미리보기 헤더
        val previewPanel = JPanel(BorderLayout())
        val previewHeader = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            background = JBColor(Color(240, 240, 240), Color(50, 50, 50))
            border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
        }
        previewHeader.add(JLabel("미리보기").apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
            foreground = JBColor.GRAY
            icon = AllIcons.Actions.Preview
        })
        previewPanel.add(previewHeader, BorderLayout.NORTH)
        previewPanel.add(previewScrollPane, BorderLayout.CENTER)
        centerPanel.bottomComponent = previewPanel

        add(centerPanel, BorderLayout.CENTER)

        // 이벤트
        saveButton.addActionListener { saveTemplate() }
        loadButton.addActionListener { loadTemplate() }
        deleteButton.addActionListener { deleteTemplate() }
        templateList.addListSelectionListener { previewSelectedTemplate() }

        // 더블클릭으로 불러오기
        templateList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) loadTemplate()
            }
        })

        refreshTemplateList()
    }

    private fun refreshTemplateList() {
        templateListModel.clear()
        val templates = TemplateService.getInstance(project).getTemplates()
        for (t in templates) {
            templateListModel.addElement("${t.name}||${t.language}")
        }
    }

    private fun saveTemplate() {
        val name = nameField.text.trim()
        if (name.isBlank()) {
            Messages.showWarningDialog(project, "템플릿 이름을 입력하세요.", "CodingTestKit")
            return
        }

        val code = getCurrentEditorCode()
        if (code.isBlank()) {
            Messages.showWarningDialog(project, "에디터에 코드가 없습니다.", "CodingTestKit")
            return
        }

        val language = Language.entries[languageCombo.selectedIndex]
        val template = CodeTemplate(
            name = name,
            language = language.displayName,
            code = code
        )

        TemplateService.getInstance(project).saveTemplate(template)
        refreshTemplateList()
        Messages.showInfoMessage(project, "'$name' 템플릿이 저장되었습니다.", "CodingTestKit")
    }

    private fun loadTemplate() {
        val template = getSelectedTemplate() ?: run {
            Messages.showWarningDialog(project, "템플릿을 선택하세요.", "CodingTestKit")
            return
        }

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            Messages.showWarningDialog(project, "열려 있는 에디터가 없습니다.", "CodingTestKit")
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.setText(template.code)
        }

        Messages.showInfoMessage(project, "'${template.name}' 템플릿을 불러왔습니다.", "CodingTestKit")
    }

    private fun deleteTemplate() {
        val selected = templateList.selectedValue ?: return
        val name = selected.substringBefore("||")

        val confirm = Messages.showYesNoDialog(
            project,
            "'$name' 템플릿을 삭제하시겠습니까?",
            "템플릿 삭제",
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return

        TemplateService.getInstance(project).deleteTemplate(name)
        refreshTemplateList()
        previewArea.text = ""
    }

    private fun previewSelectedTemplate() {
        val template = getSelectedTemplate()
        previewArea.text = template?.code ?: ""
        previewArea.caretPosition = 0
    }

    private fun getSelectedTemplate(): CodeTemplate? {
        val selected = templateList.selectedValue ?: return null
        val name = selected.substringBefore("||")
        return TemplateService.getInstance(project).getTemplate(name)
    }

    private fun getCurrentEditorCode(): String {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        return editor?.document?.text ?: ""
    }

    private class TemplateListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val str = value?.toString() ?: ""
            val parts = str.split("||")
            val name = parts.getOrElse(0) { "" }
            val lang = parts.getOrElse(1) { "" }

            text = name
            icon = AllIcons.FileTypes.Any_type
            border = JBUI.Borders.empty(4, 6)

            if (!isSelected) {
                // 언어를 오른쪽에 표시하기 위해 HTML 사용
                text = "<html><b>$name</b> <font color='#888888'>($lang)</font></html>"
            } else {
                text = "$name ($lang)"
            }
            return this
        }
    }
}
