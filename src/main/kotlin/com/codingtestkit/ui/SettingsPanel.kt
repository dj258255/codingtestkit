package com.codingtestkit.ui

import com.codingtestkit.service.GitHubService
import com.codingtestkit.service.I18n
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
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

class SettingsPanel(private val project: Project) : JPanel() {

    private val autoCompleteToggle = JCheckBox(I18n.t("자동완성 끄기 (Auto Complete OFF)", "Auto Complete OFF"))
    private val inspectionToggle = JCheckBox(I18n.t("코드 검사 끄기 (Inspections OFF)", "Inspections OFF"))
    private val pasteBlockToggle = JCheckBox(I18n.t("외부 붙여넣기 차단 (Paste Block)", "Paste Block"))
    private val focusAlertToggle = JCheckBox(I18n.t("포커스 이탈 감지 (Focus Alert)", "Focus Alert"))

    private var focusListener: WindowAdapter? = null
    private var focusLostCount = 0
    private var showingAlert = false

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(10, 12)

        // 제목
        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(28))
        }
        titlePanel.add(JLabel(I18n.t("⚙ 알고리즘 풀이 설정", "⚙ Algorithm Practice Settings")).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(16f).toFloat())
        })
        add(titlePanel)
        add(Box.createVerticalStrut(JBUI.scale(8)))

        // 언어 설정
        val langSection = createSection(I18n.t("언어 설정", "Language"))
        val langPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        val langCombo = ComboBox(I18n.Lang.entries.map { it.displayName }.toTypedArray()).apply {
            selectedIndex = I18n.currentLang.ordinal
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int,
                    isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    border = JBUI.Borders.empty(4, 8)
                    font = font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
                    return c
                }
            }
        }
        langPanel.add(JLabel(I18n.t("UI 언어 | Language:", "UI Language:")).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
        })
        langPanel.add(langCombo)
        val langNotePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
        }
        langNotePanel.add(JLabel("* 변경 후 도구 창을 다시 열어야 적용됩니다").apply {
            font = font.deriveFont(JBUI.scaleFontSize(10f).toFloat())
            foreground = JBColor.GRAY
            alignmentX = LEFT_ALIGNMENT
        })
        langNotePanel.add(JLabel("* Reopen tool window to apply changes").apply {
            font = font.deriveFont(JBUI.scaleFontSize(10f).toFloat())
            foreground = JBColor.GRAY
            alignmentX = LEFT_ALIGNMENT
        })
        langSection.add(langPanel)
        langSection.add(Box.createVerticalStrut(JBUI.scale(2)))
        langSection.add(langNotePanel)
        langCombo.addActionListener {
            I18n.setLanguage(I18n.Lang.entries[langCombo.selectedIndex])
        }
        add(langSection)
        add(Box.createVerticalStrut(JBUI.scale(8)))

        // 토글 섹션
        val toggleSection = createSection(I18n.t("코딩 환경", "Coding Environment"))
        val toggles = listOf(autoCompleteToggle, inspectionToggle, pasteBlockToggle, focusAlertToggle)
        for (toggle in toggles) {
            toggle.alignmentX = LEFT_ALIGNMENT
            toggle.isOpaque = false
            toggle.font = toggle.font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
        }
        autoCompleteToggle.isSelected = !CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP
        autoCompleteToggle.addActionListener { toggleAutoComplete() }
        toggleSection.add(autoCompleteToggle)
        toggleSection.add(Box.createVerticalStrut(JBUI.scale(4)))

        inspectionToggle.isSelected = PowerSaveMode.isEnabled()
        inspectionToggle.addActionListener { toggleInspections() }
        toggleSection.add(inspectionToggle)
        toggleSection.add(Box.createVerticalStrut(JBUI.scale(4)))

        pasteBlockToggle.toolTipText = I18n.t("외부 프로그램에서 복사한 텍스트 붙여넣기를 차단합니다", "Block pasting text copied from external programs")
        pasteBlockToggle.addActionListener { togglePasteBlock() }
        toggleSection.add(pasteBlockToggle)
        toggleSection.add(Box.createVerticalStrut(JBUI.scale(4)))

        focusAlertToggle.toolTipText = I18n.t("IDE 창에서 포커스가 벗어나면 경고를 표시합니다", "Show alert when IDE window loses focus")
        focusAlertToggle.addActionListener { toggleFocusAlert() }
        toggleSection.add(focusAlertToggle)
        toggleSection.add(Box.createVerticalStrut(JBUI.scale(8)))

        // 프리셋 버튼
        val presetPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        val examModeBtn = JButton(I18n.t("  시험 모드  ", "  Exam Mode  "), AllIcons.General.Warning).apply {
            toolTipText = I18n.t("4가지 제한을 모두 활성화합니다", "Enable all 4 restrictions")
            putClientProperty("JButton.buttonType", "roundRect")
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
        }
        val normalModeBtn = JButton(I18n.t("  일반 모드  ", "  Normal Mode  "), AllIcons.General.InspectionsOK).apply {
            toolTipText = I18n.t("4가지 제한을 모두 해제합니다", "Disable all 4 restrictions")
            putClientProperty("JButton.buttonType", "roundRect")
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
        }

        examModeBtn.addActionListener {
            setAutoCompleteOff(true)
            setInspectionsOff(true)
            setPasteBlock(true)
            setFocusAlert(true)
        }
        normalModeBtn.addActionListener {
            setAutoCompleteOff(false)
            setInspectionsOff(false)
            setPasteBlock(false)
            setFocusAlert(false)
        }

        presetPanel.add(examModeBtn)
        presetPanel.add(normalModeBtn)
        toggleSection.add(presetPanel)
        add(toggleSection)
        add(Box.createVerticalStrut(JBUI.scale(8)))

        // 도움말
        val helpSection = createSection(I18n.t("도움말", "Help"))
        helpSection.add(createHelpLine(AllIcons.General.Information,
            I18n.t("자동완성 끄기: 타이핑 시 자동완성 팝업이 나타나지 않습니다",
                "Auto Complete OFF: Disables auto-completion popups while typing")))
        helpSection.add(Box.createVerticalStrut(JBUI.scale(2)))
        helpSection.add(createHelpLine(AllIcons.General.Information,
            I18n.t("코드 검사 끄기: 절전 모드를 활성화하여 백그라운드 코드 분석을 중지합니다",
                "Inspections OFF: Enables power save mode to stop background code analysis")))
        helpSection.add(Box.createVerticalStrut(JBUI.scale(2)))
        helpSection.add(createHelpLine(AllIcons.General.Information,
            I18n.t("붙여넣기 차단: IDE 외부에서 복사한 텍스트의 붙여넣기를 차단합니다",
                "Paste Block: Blocks pasting text copied from outside the IDE")))
        helpSection.add(Box.createVerticalStrut(JBUI.scale(2)))
        helpSection.add(createHelpLine(AllIcons.General.Information,
            I18n.t("포커스 감지: IDE 창을 벗어나면 경고를 표시합니다",
                "Focus Alert: Shows a warning when you leave the IDE window")))
        helpSection.add(Box.createVerticalStrut(JBUI.scale(2)))
        helpSection.add(createHelpLine(AllIcons.General.Information,
            I18n.t("4가지를 모두 켜면 실제 코딩 테스트와 동일한 환경에서 연습할 수 있습니다",
                "Enable all 4 to practice in an environment identical to real coding tests")))
        add(helpSection)
        add(Box.createVerticalStrut(JBUI.scale(8)))

        // GitHub 설정
        val githubSection = createSection(I18n.t("GitHub 연동", "GitHub Integration"))
        val github = GitHubService.getInstance()

        val autoPushToggle = JCheckBox(I18n.t("제출 성공 시 자동 푸시", "Auto push on submit")).apply {
            font = font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
            isSelected = github.autoPushEnabled
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
        }

        val repoCombo = ComboBox<String>().apply {
            isEditable = false
            val dim = Dimension(JBUI.scale(260), JBUI.scale(28))
            preferredSize = dim; maximumSize = dim
            if (github.repoFullName.isNotBlank()) {
                addItem(github.repoFullName)
                selectedItem = github.repoFullName
            }
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int,
                    isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    border = JBUI.Borders.empty(2, 6)
                    font = font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
                    return c
                }
            }
        }
        val fetchReposBtn = JButton(AllIcons.Actions.Refresh).apply {
            toolTipText = I18n.t("레포 목록 새로고침", "Refresh repo list")
            preferredSize = Dimension(JBUI.scale(28), JBUI.scale(28))
        }
        val githubStatusLabel = JLabel(
            if (github.token.isNotBlank()) I18n.t("토큰 설정됨", "Token configured")
            else I18n.t("로그인 필요", "Login required")
        ).apply {
            foreground = if (github.token.isNotBlank()) JBColor(Color(0, 130, 0), Color(80, 200, 80)) else JBColor.GRAY
            font = font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
        }

        // 레포 목록 가져오기 함수
        val fetchReposFn: () -> Unit = fn@{
            if (github.token.isBlank()) return@fn
            githubStatusLabel.text = I18n.t("레포 목록 로딩...", "Loading repos...")
            githubStatusLabel.foreground = JBColor.GRAY
            fetchReposBtn.isEnabled = false
            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                val repos = github.listRepos()
                SwingUtilities.invokeLater {
                    val currentSelected = repoCombo.selectedItem?.toString() ?: ""
                    repoCombo.removeAllItems()
                    for (repo in repos) repoCombo.addItem(repo)
                    if (currentSelected.isNotBlank() && repos.contains(currentSelected)) {
                        repoCombo.selectedItem = currentSelected
                    } else if (repos.isNotEmpty()) {
                        repoCombo.selectedIndex = 0
                    }
                    githubStatusLabel.text = I18n.t("${repos.size}개 레포 로드됨", "${repos.size} repos loaded")
                    githubStatusLabel.foreground = JBColor(Color(0, 130, 0), Color(80, 200, 80))
                    fetchReposBtn.isEnabled = true
                }
            }
        }
        fetchReposBtn.addActionListener { fetchReposFn() }

        // 토큰 입력 필드
        val tokenField = JPasswordField(github.token).apply {
            val dim = Dimension(JBUI.scale(240), JBUI.scale(28))
            preferredSize = dim; maximumSize = dim
        }

        // 토큰 생성 도우미 버튼 (JCEF 브라우저로 GitHub 토큰 생성 페이지 열기)
        val generateBtn = JButton(I18n.t("토큰 생성", "Generate Token"), AllIcons.Vcs.Vendors.Github).apply {
            putClientProperty("JButton.buttonType", "roundRect")
            font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
            toolTipText = I18n.t(
                "GitHub에서 토큰을 생성합니다 (토큰이 없는 경우)",
                "Generate a token on GitHub (if you don't have one)"
            )
        }
        generateBtn.addActionListener {
            val dialog = GitHubLoginDialog(project)
            if (dialog.showAndGet() || dialog.capturedToken != null) {
                val token = dialog.capturedToken ?: return@addActionListener
                tokenField.text = token
                github.setToken(token)
                githubStatusLabel.text = I18n.t("✓ 토큰 저장됨", "✓ Token saved")
                githubStatusLabel.foreground = JBColor(Color(0, 130, 0), Color(80, 200, 80))
                com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                    val username = github.validateToken()
                    SwingUtilities.invokeLater {
                        if (username != null) {
                            githubStatusLabel.text = I18n.t("✓ 연결: $username", "✓ Connected: $username")
                            githubStatusLabel.foreground = JBColor(Color(0, 130, 0), Color(80, 200, 80))
                            fetchReposFn()
                        }
                    }
                }
            }
        }

        // 저장 & 검증 버튼
        val saveBtn = JButton(I18n.t("저장 & 검증", "Save & Verify")).apply {
            putClientProperty("JButton.buttonType", "roundRect")
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
        }
        saveBtn.addActionListener {
            val tokenText = String(tokenField.password).trim()
            val repoText = repoCombo.selectedItem?.toString()?.trim() ?: ""
            github.setToken(tokenText)
            github.setRepoFullName(repoText)
            github.setAutoPushEnabled(autoPushToggle.isSelected)

            githubStatusLabel.text = I18n.t("검증 중...", "Verifying...")
            githubStatusLabel.foreground = JBColor.GRAY

            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                val username = github.validateToken()
                val repoOk = if (username != null) github.validateRepo() else false
                SwingUtilities.invokeLater {
                    if (username != null && repoOk) {
                        githubStatusLabel.text = I18n.t("✓ 연결: $username / $repoText", "✓ Connected: $username / $repoText")
                        githubStatusLabel.foreground = JBColor(Color(0, 130, 0), Color(80, 200, 80))
                        fetchReposFn()
                    } else if (username != null) {
                        githubStatusLabel.text = I18n.t("✗ 토큰 OK, 저장소를 찾을 수 없음", "✗ Token OK, repo not found")
                        githubStatusLabel.foreground = JBColor(Color(200, 130, 0), Color(230, 160, 50))
                    } else {
                        githubStatusLabel.text = I18n.t("✗ 토큰이 유효하지 않음", "✗ Invalid token")
                        githubStatusLabel.foreground = JBColor(Color(200, 80, 80), Color(230, 100, 100))
                    }
                }
            }
        }

        autoPushToggle.addActionListener { github.setAutoPushEnabled(autoPushToggle.isSelected) }
        repoCombo.addActionListener {
            val selected = repoCombo.selectedItem?.toString()?.trim() ?: ""
            if (selected.isNotBlank()) github.setRepoFullName(selected)
        }

        // ── 토큰 입력 행 ──
        val tokenRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply { alignmentX = LEFT_ALIGNMENT }
        tokenRow.add(JLabel("Token: ").apply { font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat()) })
        tokenRow.add(tokenField)
        tokenRow.add(generateBtn)
        tokenRow.add(saveBtn)
        githubSection.add(tokenRow)
        githubSection.add(Box.createVerticalStrut(JBUI.scale(2)))

        // ── 상태 ──
        val statusRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply { alignmentX = LEFT_ALIGNMENT }
        statusRow.add(githubStatusLabel)
        githubSection.add(statusRow)
        githubSection.add(Box.createVerticalStrut(JBUI.scale(6)))

        // ── 레포 선택 ──
        val repoRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply { alignmentX = LEFT_ALIGNMENT }
        repoRow.add(JLabel("Repo: ").apply { font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat()) })
        repoRow.add(repoCombo)
        repoRow.add(fetchReposBtn)
        githubSection.add(repoRow)
        githubSection.add(Box.createVerticalStrut(JBUI.scale(4)))
        githubSection.add(autoPushToggle)
        githubSection.add(Box.createVerticalStrut(JBUI.scale(4)))
        add(githubSection)
        add(Box.createVerticalStrut(JBUI.scale(8)))

        // 감지된 도구 경로
        val pathSection = createSection(I18n.t("감지된 도구 경로", "Detected Tool Paths"))
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
            val titledBorder = BorderFactory.createTitledBorder(
                JBUI.Borders.customLine(JBColor.border()),
                "  $title  ",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP
            )
            titledBorder.titleFont = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
            border = BorderFactory.createCompoundBorder(
                titledBorder,
                JBUI.Borders.empty(6, 10, 8, 10)
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
            add(JLabel(path.ifBlank { I18n.t("찾을 수 없음", "Not found") }).apply {
                font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
                foreground = if (found) JBColor.GRAY else JBColor(Color(200, 80, 80), Color(230, 100, 100))
            })
        }
    }

    private fun toggleAutoComplete() {
        setAutoCompleteOff(autoCompleteToggle.isSelected)
    }

    private fun toggleInspections() {
        setInspectionsOff(inspectionToggle.isSelected)
    }

    /** checked = 자동완성 끄기 활성 */
    private fun setAutoCompleteOff(off: Boolean) {
        val settings = CodeInsightSettings.getInstance()
        settings.AUTO_POPUP_COMPLETION_LOOKUP = !off
        settings.AUTO_POPUP_PARAMETER_INFO = !off
        settings.AUTO_POPUP_JAVADOC_INFO = !off
        autoCompleteToggle.isSelected = off
    }

    /** checked = 코드 검사 끄기 활성 */
    private fun setInspectionsOff(off: Boolean) {
        PowerSaveMode.setEnabled(off)
        inspectionToggle.isSelected = off
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
                    if (ExamModeState.focusAlertEnabled && !showingAlert) {
                        focusLostCount++
                        showingAlert = true
                        SwingUtilities.invokeLater {
                            JOptionPane.showMessageDialog(
                                frame,
                                I18n.t(
                                    "IDE 창을 벗어났습니다! (${focusLostCount}회)\n실제 시험에서는 부정행위로 간주될 수 있습니다.",
                                    "IDE window lost focus! (${focusLostCount} times)\nThis may be considered cheating in actual tests."
                                ),
                                I18n.t("포커스 이탈 감지", "Focus Loss Detection"),
                                JOptionPane.WARNING_MESSAGE
                            )
                            showingAlert = false
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
