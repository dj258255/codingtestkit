package com.codingtestkit.ui

import com.codingtestkit.service.GitHubService
import com.codingtestkit.service.I18n
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class GitHubConfigDialog(private val project: Project?) : DialogWrapper(project) {

    private val github = GitHubService.getInstance()

    private val tokenField = JPasswordField(github.token)
    private val repoCombo = ComboBox<String>().apply {
        isEditable = false
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
                border = JBUI.Borders.empty(3, 6)
                return c
            }
        }
        addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent) {
                val popup = getAccessibleContext().getAccessibleChild(0)
                if (popup is JPopupMenu) {
                    val scroll = popup.getComponent(0)
                    if (scroll is JScrollPane) {
                        scroll.preferredSize = Dimension(this@apply.width, scroll.preferredSize.height)
                    }
                }
            }
            override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent) {}
            override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent) {}
        })
    }
    private val autoPushToggle = JCheckBox(I18n.t("제출 성공 시 자동 푸시", "Auto push on submit")).apply {
        isSelected = github.autoPushEnabled
        font = font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
        isOpaque = false
    }
    private val statusLabel = JLabel(
        if (github.token.isNotBlank()) I18n.t("토큰 설정됨", "Token configured")
        else I18n.t("로그인 필요", "Login required")
    ).apply {
        foreground = if (github.token.isNotBlank())
            JBColor(Color(0, 130, 0), Color(80, 200, 80)) else JBColor.GRAY
        font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
    }

    init {
        title = I18n.t("GitHub 설정", "GitHub Settings")
        setOKButtonText(I18n.t("저장", "Save"))
        init()
        if (github.token.isNotBlank()) fetchRepos()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(4, 8)
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(2, 4)
            anchor = GridBagConstraints.WEST
        }

        val labelFont = panel.font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())

        // ── Row 0: Token ──
        gbc.gridy = 0; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("Token:").apply { font = labelFont }, gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(tokenField, gbc)

        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        val generateBtn = JButton(I18n.t("토큰 생성", "Generate"), AllIcons.Vcs.Vendors.Github).apply {
            font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
            toolTipText = I18n.t("GitHub에서 토큰을 자동 생성합니다", "Auto-generate token on GitHub")
        }
        generateBtn.addActionListener {
            val loginDialog = GitHubLoginDialog(project)
            loginDialog.show()
            val captured = loginDialog.capturedToken
            if (captured != null) {
                tokenField.text = captured
                statusLabel.text = I18n.t("✓ 토큰 생성됨", "✓ Token generated")
                statusLabel.foreground = JBColor(Color(0, 130, 0), Color(80, 200, 80))
                github.setToken(captured)
                fetchRepos()
            }
        }
        panel.add(generateBtn, gbc)

        // ── Row 1: Status ──
        gbc.gridy = 1; gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(statusLabel, gbc)
        gbc.gridwidth = 1

        // ── Row 2: Repo ──
        gbc.gridy = 2; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("Repo:").apply { font = labelFont }, gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(repoCombo, gbc)

        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        val refreshBtn = JButton(AllIcons.Actions.Refresh).apply {
            toolTipText = I18n.t("레포 목록 새로고침", "Refresh repo list")
            preferredSize = Dimension(JBUI.scale(28), JBUI.scale(28))
        }
        refreshBtn.addActionListener { fetchRepos() }
        panel.add(refreshBtn, gbc)

        // ── Row 3: Auto push ──
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(4, 2, 0, 0)
        panel.add(autoPushToggle, gbc)

        panel.preferredSize = Dimension(JBUI.scale(440), panel.preferredSize.height)
        return panel
    }

    private fun fetchRepos() {
        val token = String(tokenField.password).trim().ifBlank { github.token }
        if (token.isBlank()) return
        statusLabel.text = I18n.t("레포 목록 로딩...", "Loading repos...")
        statusLabel.foreground = JBColor.GRAY
        ApplicationManager.getApplication().executeOnPooledThread {
            github.setToken(token)
            val repos = github.listRepos()
            SwingUtilities.invokeLater {
                val current = repoCombo.selectedItem?.toString() ?: ""
                repoCombo.removeAllItems()
                for (repo in repos) repoCombo.addItem(repo)
                if (current.isNotBlank() && repos.contains(current)) {
                    repoCombo.selectedItem = current
                } else if (repos.isNotEmpty()) {
                    repoCombo.selectedIndex = 0
                }
                statusLabel.text = I18n.t("${repos.size}개 레포 로드됨", "${repos.size} repos loaded")
                statusLabel.foreground = JBColor(Color(0, 130, 0), Color(80, 200, 80))
            }
        }
    }

    override fun doOKAction() {
        val token = String(tokenField.password).trim()
        val repo = repoCombo.selectedItem?.toString()?.trim() ?: ""
        github.setToken(token)
        github.setRepoFullName(repo)
        github.setAutoPushEnabled(autoPushToggle.isSelected)
        super.doOKAction()
    }
}
