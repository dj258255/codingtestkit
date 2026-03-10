package com.codingtestkit.ui

import com.codingtestkit.service.I18n
import com.codingtestkit.model.CodeTemplate
import com.codingtestkit.model.Language
import com.codingtestkit.service.TemplateService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class TemplatePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val templateList = JBList<String>()
    private val templateListModel = DefaultListModel<String>()
    private val nameField = JTextField().apply {
        toolTipText = I18n.t("템플릿 이름을 입력하세요", "Enter template name")
    }
    private val languageCombo = ComboBox(Language.entries.map { it.displayName }.toTypedArray())
    private val saveButton = JButton(I18n.t("저장", "Save"), AllIcons.Actions.MenuSaveall).apply {
        toolTipText = I18n.t("현재 에디터의 코드를 템플릿으로 저장", "Save current editor code as template")
    }
    private val loadButton = JButton(I18n.t("불러오기", "Load"), AllIcons.Actions.Upload).apply {
        toolTipText = I18n.t("선택한 템플릿을 에디터에 불러오기", "Load selected template into editor")
    }
    private val deleteButton = JButton(AllIcons.General.Remove).apply {
        toolTipText = I18n.t("선택한 템플릿 삭제", "Delete selected template")
        preferredSize = Dimension(JBUI.scale(28), JBUI.scale(28))
        horizontalAlignment = SwingConstants.CENTER
        margin = JBUI.emptyInsets()
    }
    private var previewEditor: EditorEx? = null
    private val previewPanel = JPanel(BorderLayout())

    init {
        border = JBUI.Borders.empty()

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6, 8, 4, 8)
        }

        // Row 1: 이름 + 언어 (WrapLayout으로 반응형)
        val row1 = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        row1.add(JLabel(I18n.t("이름:", "Name:")).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
            foreground = JBColor.GRAY
        })
        nameField.preferredSize = Dimension(JBUI.scale(120), nameField.preferredSize.height)
        row1.add(nameField)
        row1.add(JLabel(I18n.t("언어:", "Lang:")).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
            foreground = JBColor.GRAY
        })
        row1.add(languageCombo)
        topPanel.add(row1)

        // Row 2: 저장 / 불러오기 / 삭제 버튼
        val row2 = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        row2.add(saveButton)
        row2.add(loadButton)
        row2.add(deleteButton)
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
        templateList.emptyText.text = I18n.t("저장된 템플릿이 없습니다", "No saved templates")
        templateList.cellRenderer = TemplateListRenderer()
        val listScrollPane = JBScrollPane(templateList).apply {
            minimumSize = Dimension(0, JBUI.scale(80))
        }
        centerPanel.topComponent = listScrollPane

        // 미리보기 헤더
        val previewHeader = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            background = JBColor(Color(240, 240, 240), Color(50, 50, 50))
            border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
        }
        previewHeader.add(JLabel(I18n.t("미리보기", "Preview")).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
            foreground = JBColor.GRAY
            icon = AllIcons.Actions.Preview
        })
        previewPanel.add(previewHeader, BorderLayout.NORTH)
        previewPanel.minimumSize = Dimension(0, JBUI.scale(100))
        updatePreviewEditor("", "Java")
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
            Messages.showWarningDialog(project, I18n.t("템플릿 이름을 입력하세요.", "Please enter a template name."), "CodingTestKit")
            return
        }

        val code = getCurrentEditorCode()
        if (code.isBlank()) {
            Messages.showWarningDialog(project, I18n.t("에디터에 코드가 없습니다.", "No code in editor."), "CodingTestKit")
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
        Messages.showInfoMessage(project, I18n.t("'$name' 템플릿이 저장되었습니다.", "Template '$name' saved."), "CodingTestKit")
    }

    private fun loadTemplate() {
        val template = getSelectedTemplate() ?: run {
            Messages.showWarningDialog(project, I18n.t("템플릿을 선택하세요.", "Please select a template."), "CodingTestKit")
            return
        }

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            Messages.showWarningDialog(project, I18n.t("열려 있는 에디터가 없습니다.", "No open editor."), "CodingTestKit")
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.setText(template.code)
        }

        Messages.showInfoMessage(project, I18n.t("'${template.name}' 템플릿을 불러왔습니다.", "Template '${template.name}' loaded."), "CodingTestKit")
    }

    private fun deleteTemplate() {
        val selected = templateList.selectedValue ?: return
        val name = selected.substringBefore("||")

        val confirm = Messages.showYesNoDialog(
            project,
            I18n.t("'$name' 템플릿을 삭제하시겠습니까?", "Delete template '$name'?"),
            I18n.t("템플릿 삭제", "Delete Template"),
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return

        TemplateService.getInstance(project).deleteTemplate(name)
        refreshTemplateList()
        updatePreviewEditor("", "Java")
    }

    private fun previewSelectedTemplate() {
        val template = getSelectedTemplate()
        updatePreviewEditor(template?.code ?: "", template?.language ?: "Java")
    }

    private fun updatePreviewEditor(code: String, language: String) {
        // 기존 에디터 제거
        previewEditor?.let { editor ->
            previewPanel.remove(editor.component)
            EditorFactory.getInstance().releaseEditor(editor)
        }

        // 새 에디터 생성
        val document = EditorFactory.getInstance().createDocument(code)
        val editor = EditorFactory.getInstance().createViewer(document, project) as EditorEx

        // 언어별 구문 강조 설정
        val extension = when (language) {
            "Java" -> "java"
            "Python" -> "py"
            "C++" -> "cpp"
            "Kotlin" -> "kt"
            "JavaScript" -> "js"
            else -> "txt"
        }
        val fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension)
        val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType)
        editor.highlighter = highlighter

        // 에디터 설정
        editor.settings.apply {
            isLineNumbersShown = true
            isFoldingOutlineShown = false
            isAdditionalPageAtBottom = false
            isLineMarkerAreaShown = false
            isIndentGuidesShown = true
            isRightMarginShown = false
        }

        previewEditor = editor
        previewPanel.add(editor.component, BorderLayout.CENTER)
        previewPanel.revalidate()
        previewPanel.repaint()
    }

    override fun dispose() {
        previewEditor?.let { editor ->
            EditorFactory.getInstance().releaseEditor(editor)
            previewEditor = null
        }
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
