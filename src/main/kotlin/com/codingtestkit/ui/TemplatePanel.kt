package com.codingtestkit.ui

import com.codingtestkit.service.I18n
import com.codingtestkit.model.CodeTemplate
import com.codingtestkit.model.Language
import com.codingtestkit.model.ProblemSource
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
    /** 이 템플릿을 어느 플랫폼의 기본으로 쓸지 (이슈 #35) — 0 = 지정 안 함 */
    private val platformDefaultCombo = ComboBox(
        (listOf(I18n.t("기본 지정 안 함", "Not a default")) + ProblemSource.entries.map {
            I18n.t("${it.displayName} 기본", "${it.englishName} default")
        }).toTypedArray()
    ).apply {
        toolTipText = I18n.t(
            "지정하면 해당 플랫폼에서 새 문제를 열 때 이 템플릿이 초기 코드가 됩니다",
            "When set, new problems on that platform start with this template"
        )
    }
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
        row1.add(platformDefaultCombo)
        topPanel.add(row1)

        // {{SOLUTION}} 자리 표시자 안내 (이슈 #35)
        topPanel.add(JLabel(I18n.t(
            "팁: 코드에 {{SOLUTION}}을 넣으면 문제별 스텁(리트코드 Solution 등)이 그 자리에 들어갑니다",
            "Tip: put {{SOLUTION}} in your code where the per-problem stub (e.g. LeetCode Solution) should go"
        )).apply {
            alignmentX = LEFT_ALIGNMENT
            font = font.deriveFont(JBUI.scaleFontSize(10f).toFloat())
            foreground = JBColor.GRAY
            border = JBUI.Borders.empty(2, 0, 0, 0)
        })

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
            // 세 번째 조각 = 플랫폼 기본 배지 (없으면 빈 문자열, 이슈 #35)
            val badge = t.defaultForPlatform?.let { p ->
                ProblemSource.entries.find { it.name == p }?.localizedName() ?: p
            } ?: ""
            templateListModel.addElement("${t.name}||${t.language}||$badge")
        }
    }

    private fun saveTemplate() {
        val name = nameField.text.trim()
        if (name.isBlank()) {
            Messages.showWarningDialog(project, I18n.t("템플릿 이름을 입력하세요.", "Please enter a template name."), "CodingTestKit")
            return
        }

        val service = TemplateService.getInstance(project)
        val existing = service.getTemplate(name)
        // 에디터가 비어도 기존 템플릿의 메타데이터(언어·플랫폼 기본)만 바꾸는 건 허용 — 기존 코드 유지 (이슈 #35)
        val code = getCurrentEditorCode().ifBlank { existing?.code ?: "" }
        if (code.isBlank()) {
            Messages.showWarningDialog(project, I18n.t(
                "에디터에 코드가 없습니다. (기존 템플릿 수정은 리스트에서 선택 후 저장)",
                "No code in editor. (To edit an existing template, select it in the list first, then Save.)"
            ), "CodingTestKit")
            return
        }

        val language = Language.entries[languageCombo.selectedIndex]
        // 플랫폼 기본 지정 (0 = 지정 안 함, 이슈 #35)
        val platformIdx = platformDefaultCombo.selectedIndex
        val newPlatform = if (platformIdx > 0) ProblemSource.entries[platformIdx - 1].name else null

        // 같은 (플랫폼, 언어)에 이미 다른 기본 템플릿이 있으면 교체 여부 확인
        if (newPlatform != null) {
            val displaced = service.getTemplates().find {
                it.name != name && it.defaultForPlatform == newPlatform && it.language == language.displayName
            }
            if (displaced != null) {
                val platformLabel = ProblemSource.entries.first { it.name == newPlatform }.localizedName()
                val ok = Messages.showYesNoDialog(
                    project,
                    I18n.t(
                        "'${displaced.name}'이(가) 현재 $platformLabel($language) 기본입니다.\n'$name'으로 교체할까요?",
                        "'${displaced.name}' is currently the $platformLabel ($language) default.\nReplace it with '$name'?"
                    ),
                    I18n.t("기본 템플릿 교체", "Replace Default Template"),
                    Messages.getQuestionIcon()
                )
                if (ok != Messages.YES) return
            }
        }

        service.saveTemplate(CodeTemplate(name = name, language = language.displayName, code = code, defaultForPlatform = newPlatform))
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

        // 작성 중인 코드가 있으면 통째로 덮어쓰기 전에 확인 — 교체/커서 삽입/취소 (이슈 #35)
        val current = editor.document.text
        if (current.isNotBlank()) {
            val choice = Messages.showDialog(
                project,
                I18n.t(
                    "에디터에 작성 중인 코드가 있습니다. 어떻게 불러올까요?",
                    "The editor already has code. How should the template be loaded?"
                ),
                I18n.t("템플릿 불러오기", "Load Template"),
                arrayOf(
                    I18n.t("전체 교체", "Replace all"),
                    I18n.t("커서 위치에 삽입", "Insert at cursor"),
                    I18n.t("취소", "Cancel")
                ),
                0,
                Messages.getQuestionIcon()
            )
            when (choice) {
                0 -> WriteCommandAction.runWriteCommandAction(project) {
                    editor.document.setText(template.code)
                }
                1 -> WriteCommandAction.runWriteCommandAction(project) {
                    editor.document.insertString(editor.caretModel.offset, template.code)
                }
                else -> return
            }
        } else {
            WriteCommandAction.runWriteCommandAction(project) {
                editor.document.setText(template.code)
            }
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
        // 선택한 템플릿의 속성을 입력 폼에 동기화 — 같은 이름으로 재저장(수정)이 자연스럽도록 (이슈 #35)
        if (template != null) {
            nameField.text = template.name
            Language.entries.indexOfFirst { it.displayName == template.language }
                .takeIf { it >= 0 }?.let { languageCombo.selectedIndex = it }
            val platformIdx = template.defaultForPlatform?.let { p ->
                ProblemSource.entries.indexOfFirst { it.name == p }
            } ?: -1
            platformDefaultCombo.selectedIndex = if (platformIdx >= 0) platformIdx + 1 else 0
        }
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
            val badge = parts.getOrElse(2) { "" }  // 플랫폼 기본 배지 (이슈 #35)

            text = name
            icon = if (badge.isNotBlank()) AllIcons.Nodes.Favorite else AllIcons.FileTypes.Any_type
            border = JBUI.Borders.empty(4, 6)

            val badgeText = if (badge.isNotBlank()) " ★$badge" else ""
            if (!isSelected) {
                // 언어·배지를 오른쪽에 표시하기 위해 HTML 사용
                text = "<html><b>$name</b> <font color='#888888'>($lang)</font>" +
                    (if (badge.isNotBlank()) " <font color='#e0a030'>★$badge</font>" else "") + "</html>"
            } else {
                text = "$name ($lang)$badgeText"
            }
            return this
        }
    }
}
