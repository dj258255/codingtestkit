package com.codingtestkit.ui

import com.codingtestkit.service.I18n
import com.codingtestkit.model.CodeTemplate
import com.codingtestkit.model.Language
import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.ProblemFileManager
import com.codingtestkit.service.TemplateService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
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
    private val languageCombo = ComboBox(Language.entries.map { it.displayName }.toTypedArray())
    private val saveButton = JButton(I18n.t("저장", "Save"), AllIcons.Actions.MenuSaveall).apply {
        toolTipText = I18n.t(
            "에디터 전체·선택 영역·파일을 템플릿으로 저장",
            "Save the editor, a selection, or a file as a template"
        )
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

        // Row 1: 언어 (이름은 저장 시 마지막에 묻고, 플랫폼 기본은 우클릭 메뉴 — 이슈 #35)
        val row1 = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        row1.add(JLabel(I18n.t("언어:", "Lang:")).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
            foreground = JBColor.GRAY
        })
        row1.add(languageCombo)
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

        // 더블클릭으로 불러오기 + 우클릭 컨텍스트 메뉴 (이슈 #35)
        templateList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) loadTemplate()
            }
            override fun mousePressed(e: java.awt.event.MouseEvent) = maybeShowContextMenu(e)
            override fun mouseReleased(e: java.awt.event.MouseEvent) = maybeShowContextMenu(e)
        })

        refreshTemplateList()
    }

    /** 우클릭한 항목을 선택하고 컨텍스트 메뉴 표시 (이슈 #35 — 기본 지정을 발견 가능하게) */
    private fun maybeShowContextMenu(e: java.awt.event.MouseEvent) {
        if (!e.isPopupTrigger) return
        // locationToIndex는 '가장 가까운' 인덱스를 돌려주므로, 목록 아래 빈 영역을
        // 우클릭하면 마지막 항목이 잡힌다 — 그 메뉴에 삭제가 있어서 위험하다.
        // 셀 경계 안쪽인지 확인해야 한다.
        val index = templateList.locationToIndex(e.point)
        if (index < 0) return
        val bounds = templateList.getCellBounds(index, index) ?: return
        if (!bounds.contains(e.point)) return
        templateList.selectedIndex = index
        val template = getSelectedTemplate() ?: return

        val menu = JPopupMenu()
        menu.add(JMenuItem(I18n.t("에디터에 불러오기", "Load into Editor"), AllIcons.Actions.Upload).apply {
            addActionListener { loadTemplate() }
        })
        val defaultMenu = JMenu(I18n.t("플랫폼 기본으로 지정", "Set as Platform Default")).apply {
            icon = AllIcons.Nodes.Favorite
            toolTipText = I18n.t(
                "지정하면 해당 플랫폼에서 새 문제를 열 때 이 템플릿이 초기 코드가 됩니다",
                "When set, new problems on that platform start with this template"
            )
        }
        val currentDefaults = template.defaultPlatforms()
        for (src in ProblemSource.entries) {
            val isCurrent = src.name in currentDefaults
            defaultMenu.add(JCheckBoxMenuItem(src.localizedName(), isCurrent).apply {
                // 체크하면 추가, 이미 체크된 항목을 다시 누르면 그 플랫폼만 해제.
                // 다른 플랫폼 지정은 그대로 둔다 — 한 템플릿이 여러 플랫폼의 기본일 수 있다.
                addActionListener { togglePlatformDefault(template, src) }
            })
        }
        menu.add(defaultMenu)
        menu.addSeparator()
        menu.add(JMenuItem(I18n.t("삭제", "Delete"), AllIcons.General.Remove).apply {
            addActionListener { deleteTemplate() }
        })
        menu.show(templateList, e.x, e.y)
    }

    /** 템플릿의 플랫폼 기본 지정을 토글 (이슈 #35) */
    private fun togglePlatformDefault(template: CodeTemplate, platform: ProblemSource) {
        val service = TemplateService.getInstance(project)
        val current = template.defaultPlatforms()
        val adding = platform.name !in current
        if (adding) {
            val displaced = service.getTemplates().find {
                it.name != template.name && platform.name in it.defaultPlatforms() && it.language == template.language
            }
            if (displaced != null) {
                val ok = Messages.showYesNoDialog(
                    project,
                    I18n.t(
                        "'${displaced.name}'이(가) 현재 ${platform.localizedName()}(${template.language}) 기본입니다.\n'${template.name}'으로 교체할까요?",
                        "'${displaced.name}' is currently the ${platform.englishName} (${template.language}) default.\nReplace it with '${template.name}'?"
                    ),
                    I18n.t("기본 템플릿 교체", "Replace Default Template"),
                    Messages.getQuestionIcon()
                )
                if (ok != Messages.YES) return
            }
        }
        val next = if (adding) current + platform.name else current - platform.name
        service.saveTemplate(template.withDefaultPlatforms(next))
        refreshTemplateList()
    }

    private fun refreshTemplateList() {
        templateListModel.clear()
        val templates = TemplateService.getInstance(project).getTemplates()
        for (t in templates) {
            // 세 번째 조각 = 플랫폼 기본 배지 (없으면 빈 문자열, 이슈 #35)
            val badge = t.defaultPlatforms().joinToString(", ") { p ->
                ProblemSource.entries.find { it.name == p }?.localizedName() ?: p
            }
            templateListModel.addElement("${t.name}||${t.language}||$badge")
        }
    }

    private fun saveTemplate() {
        // 1) 무엇을 저장할지 먼저 정한다 — 이름은 내용이 정해진 다음 (이슈 #35)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val wholeLabel = I18n.t("현재 에디터 전체", "Whole editor")
        val selectionLabel = I18n.t("선택 영역", "Selection")
        val fileLabel = I18n.t("파일에서...", "From file...")
        val options = mutableListOf(wholeLabel)
        if (editor?.selectionModel?.hasSelection() == true) options.add(selectionLabel)
        options.add(fileLabel)
        options.add(I18n.t("취소", "Cancel"))

        val choice = Messages.showDialog(
            project,
            I18n.t("무엇을 템플릿으로 저장할까요?", "What should be saved as the template?"),
            I18n.t("템플릿 저장", "Save Template"),
            options.toTypedArray(), 0, Messages.getQuestionIcon()
        )
        if (choice < 0 || choice == options.lastIndex) return

        // 저장 대상에서 확장자로 언어를 유추한다 (상단 콤보보다 우선)
        var savedLanguage: Language? = null
        val code = when (options[choice]) {
            selectionLabel -> {
                savedLanguage = languageOf(editor?.virtualFile?.extension)
                editor?.selectionModel?.selectedText ?: ""
            }
            fileLabel -> {
                // createSingleFileDescriptor()는 deprecated — jar 내부를 열지 않는 쪽이
                // 소스 파일 선택 용도에도 맞는다
                val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    .withTitle(I18n.t("템플릿으로 저장할 파일 선택", "Choose File to Save as Template"))
                val vf = FileChooser.chooseFile(descriptor, project, null) ?: return
                savedLanguage = languageOf(vf.extension)
                // 바이너리·거대 파일·읽기 실패를 그냥 두면 예외가 EDT로 새어나가
                // IDE 오류 리포트가 뜬다. 여기서 잡아 사용자에게 이유를 알린다.
                val text = runCatching { String(vf.contentsToByteArray(), vf.charset) }.getOrElse { ex ->
                    Messages.showWarningDialog(
                        project,
                        I18n.t("파일을 읽을 수 없습니다: ", "Cannot read the file: ") + (ex.message ?: ex.javaClass.simpleName),
                        "CodingTestKit"
                    )
                    return
                }
                if (text.any { it == '\u0000' }) {
                    Messages.showWarningDialog(
                        project,
                        I18n.t("텍스트 파일이 아닙니다.", "That is not a text file."),
                        "CodingTestKit"
                    )
                    return
                }
                text
            }
            else -> {
                savedLanguage = languageOf(editor?.virtualFile?.extension)
                editor?.document?.text ?: ""
            }
        }
        if (code.isBlank()) {
            Messages.showWarningDialog(project, I18n.t("저장할 코드가 없습니다.", "No code to save."), "CodingTestKit")
            return
        }

        // 2) 이름 입력 (이슈 #35 — 마지막 단계)
        val name = Messages.showInputDialog(
            project,
            I18n.t("템플릿 이름:", "Template name:"),
            I18n.t("템플릿 저장", "Save Template"),
            Messages.getQuestionIcon()
        )?.trim()
        if (name.isNullOrBlank()) return

        val service = TemplateService.getInstance(project)
        val existing = service.getTemplate(name)
        if (existing != null) {
            val ok = Messages.showYesNoDialog(
                project,
                I18n.t("'$name' 템플릿이 이미 있습니다. 덮어쓸까요?", "Template '$name' already exists. Overwrite?"),
                I18n.t("템플릿 저장", "Save Template"),
                Messages.getQuestionIcon()
            )
            if (ok != Messages.YES) return
        }

        // 언어는 저장 대상에서 유추한 값을 우선한다. 상단 콤보는 직전에 미리본
        // 템플릿을 따라 바뀌므로, 그대로 믿으면 .py 파일을 Java로 저장하게 되고
        // 그러면 findPlatformDefault가 영영 매칭되지 않는다 (이슈 #35).
        val language = savedLanguage ?: Language.entries[languageCombo.selectedIndex]
        // 같은 이름 덮어쓰기 시 기존의 플랫폼 기본 지정은 유지 — 지정/해제는 우클릭 메뉴 담당
        service.saveTemplate(
            CodeTemplate(name = name, language = language.displayName, code = code)
                .withDefaultPlatforms(existing?.defaultPlatforms() ?: emptySet())
        )
        refreshTemplateList()
        Messages.showInfoMessage(project, I18n.t("'$name' 템플릿이 저장되었습니다.", "Template '$name' saved."), "CodingTestKit")
    }

    /**
     * 템플릿을 현재 문제에 맞춰 조합한다 (이슈 #35).
     *
     * 전체 교체는 파일을 처음 만들 때와 결과가 같아야 한다. 템플릿 코드를 그대로
     * 쓰면 {{SOLUTION}}이 리터럴로 남고, 리트코드처럼 문제별 스텁이 필요한
     * 플랫폼에서는 Solution 클래스가 사라져 제출이 깨진다 — 이슈가 경고한 바로
     * 그 호환성 파괴다. 현재 문제를 못 찾으면(연습 파일 등) 템플릿 원본을 쓴다.
     */
    private fun composeForCurrentProblem(templateCode: String): String {
        val problem = currentProblem() ?: return templateCode
        val language = currentLanguage() ?: return templateCode
        return ProblemFileManager.composeInitialCode(templateCode, problem, language)
    }

    /** 열려 있는 에디터 파일이 속한 문제 폴더에서 Problem 복원 */
    private fun currentProblem(): Problem? {
        val path = FileEditorManager.getInstance(project).selectedTextEditor
            ?.virtualFile?.path ?: return null
        val base = project.basePath ?: return null
        val folder = ProblemFileManager.findProblemFolder(path, base) ?: return null
        return ProblemFileManager.loadProblemFromFolder(folder)
    }

    /** 확장자로 언어 판별 */
    private fun languageOf(extension: String?): Language? =
        extension?.let { ext -> Language.entries.firstOrNull { it.extension.equals(ext, ignoreCase = true) } }

    /** 에디터 파일 확장자로 언어 판별 (템플릿 언어가 아니라 실제 파일 기준) */
    private fun currentLanguage(): Language? = languageOf(
        FileEditorManager.getInstance(project).selectedTextEditor?.virtualFile?.extension
    )

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
                    editor.document.setText(composeForCurrentProblem(template.code))
                }
                1 -> WriteCommandAction.runWriteCommandAction(project) {
                    editor.document.insertString(editor.caretModel.offset, template.code)
                }
                else -> return
            }
        } else {
            WriteCommandAction.runWriteCommandAction(project) {
                editor.document.setText(composeForCurrentProblem(template.code))
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
        // 선택한 템플릿의 언어를 콤보에 동기화 — 같은 언어로 재저장이 자연스럽도록 (이슈 #35)
        if (template != null) {
            Language.entries.indexOfFirst { it.displayName == template.language }
                .takeIf { it >= 0 }?.let { languageCombo.selectedIndex = it }
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
