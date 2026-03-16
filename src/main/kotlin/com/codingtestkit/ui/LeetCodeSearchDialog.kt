package com.codingtestkit.ui

import com.codingtestkit.service.AuthService
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.I18n
import com.codingtestkit.service.LeetCodeApi
import com.codingtestkit.service.TranslateService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

class LeetCodeSearchDialog(private val project: Project) : DialogWrapper(project) {

    private val searchField = JTextField().apply {
        putClientProperty("JTextField.placeholderText", I18n.t("문제 제목이나 키워드 입력", "Enter title or keyword"))
    }
    private val difficultyCombo = ComboBox(arrayOf(
        I18n.t("전체", "All"), "Easy", "Medium", "Hard"
    )).apply { renderer = comboRenderer() }

    private var tagEntries: List<Pair<String, String>> = listOf("" to I18n.t("전체", "All"))
    private val tagCombo = ComboBox(tagEntries.map { it.second }.toTypedArray()).apply {
        renderer = comboRenderer()
    }

    private val solvedFilterCombo = ComboBox(arrayOf(
        I18n.t("전체", "All"),
        I18n.t("내가 푼 문제만", "Only solved"),
        I18n.t("안 푼 문제만", "Not solved")
    )).apply {
        renderer = comboRenderer()
        val cookies = AuthService.getInstance().getCookies(ProblemSource.LEETCODE)
        isEnabled = cookies.isNotBlank()
        if (cookies.isBlank()) {
            toolTipText = I18n.t("LeetCode 로그인이 필요합니다", "LeetCode login required")
            val combo = this
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder(
                            I18n.t("풀이 필터를 사용하려면<br>LeetCode에 로그인하세요", "Login to LeetCode<br>to use solved filter"),
                            MessageType.WARNING, null
                        )
                        .setFadeoutTime(3000)
                        .createBalloon()
                        .show(RelativePoint.getCenterOf(combo), Balloon.Position.above)
                }
            })
        }
    }

    private val searchButton = JButton(I18n.t("검색", "Search"))

    private val tableModel = object : DefaultTableModel(
        arrayOf("#", I18n.t("제목", "Title"), I18n.t("난이도", "Difficulty"),
            I18n.t("정답률", "AC Rate"), I18n.t("태그", "Tags")), 0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val resultTable = JBTable(tableModel).apply {
        emptyText.text = I18n.t("표시할 항목이 없습니다", "No items to display")
    }
    private val statusLabel = JLabel(I18n.t("키워드를 입력하고 검색하세요", "Enter a keyword and search"))

    private var results: List<LeetCodeApi.LeetCodeProblemInfo> = emptyList()
    private var translatedTitles: Map<String, String> = emptyMap() // slug → translated title
    private var showingTranslated = false
    private val translateButton = JButton("KO").apply {
        toolTipText = I18n.t("한국어로 번역", "Translate to Korean")
        preferredSize = Dimension(JBUI.scale(50), preferredSize.height)
        addActionListener { toggleTranslation() }
    }
    var selectedProblemSlug: String? = null
        private set

    init {
        title = I18n.t("LeetCode 문제 검색", "LeetCode Problem Search")
        setOKButtonText(I18n.t("가져오기", "Fetch"))
        setCancelButtonText(I18n.t("닫기", "Close"))
        init()
        isOKActionEnabled = false
        loadTagsAsync()
    }

    private fun loadTagsAsync() {
        Thread {
            try {
                val cookies = AuthService.getInstance().getCookies(ProblemSource.LEETCODE)
                val tags = LeetCodeApi.fetchTopicTags(cookies.ifBlank { null })
                val entries = listOf("" to I18n.t("전체", "All")) +
                    tags.map { it.slug to I18n.t(LeetCodeApi.tagToKo(it.name), it.name) }
                SwingUtilities.invokeLater {
                    tagEntries = entries
                    tagCombo.removeAllItems()
                    for ((_, display) in entries) tagCombo.addItem(display)
                }
            } catch (_: Exception) { }
        }.start()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(720), JBUI.scale(450))

        // 검색 바
        val searchPanel = JPanel(BorderLayout(JBUI.scale(4), 0))
        searchPanel.border = JBUI.Borders.empty(4)
        searchPanel.add(searchField, BorderLayout.CENTER)

        val filterPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        filterPanel.add(difficultyCombo)
        filterPanel.add(tagCombo)
        filterPanel.add(solvedFilterCombo)
        filterPanel.add(searchButton)
        searchPanel.add(filterPanel, BorderLayout.EAST)

        panel.add(searchPanel, BorderLayout.NORTH)

        // 결과 테이블
        resultTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultTable.rowHeight = JBUI.scale(24)
        resultTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(50)
        resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(260)
        resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(70)
        resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(70)
        resultTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(190)

        panel.add(JScrollPane(resultTable), BorderLayout.CENTER)

        // 하단: 상태 라벨 + 번역 버튼
        statusLabel.foreground = JBColor.GRAY
        statusLabel.font = statusLabel.font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
        val bottomPanel = JPanel(BorderLayout()).apply {
            add(statusLabel, BorderLayout.CENTER)
            add(translateButton, BorderLayout.EAST)
        }
        panel.add(bottomPanel, BorderLayout.SOUTH)

        // 이벤트
        searchButton.addActionListener { doSearch() }
        searchField.addActionListener { doSearch() }

        // 자동완성 (debounce)
        val debounceTimer = Timer(400, null).apply { isRepeats = false }
        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent) {
                if (e.keyCode in listOf(KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE, KeyEvent.VK_UP, KeyEvent.VK_DOWN)) return
                debounceTimer.stop()
                debounceTimer.actionListeners.forEach { debounceTimer.removeActionListener(it) }
                debounceTimer.addActionListener { doSearch() }
                debounceTimer.start()
            }
        })

        resultTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = resultTable.selectedRow
                isOKActionEnabled = row in results.indices
                if (isOKActionEnabled) selectedProblemSlug = results[row].titleSlug
            }
        }
        resultTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && resultTable.selectedRow >= 0) {
                    selectedProblemSlug = results[resultTable.selectedRow].titleSlug
                    doOKAction()
                }
            }
        })

        return panel
    }

    private fun doSearch() {
        val query = searchField.text.trim()
        searchButton.isEnabled = false
        statusLabel.text = I18n.t("검색 중...", "Searching...")
        tableModel.rowCount = 0
        isOKActionEnabled = false

        val difficultyValues = arrayOf(null, "EASY", "MEDIUM", "HARD")
        val difficulty = difficultyValues[difficultyCombo.selectedIndex]
        val tagKey = tagEntries[tagCombo.selectedIndex].first.ifBlank { null }
        val tags = if (tagKey != null) listOf(tagKey) else null

        val statusValues = arrayOf(null, "AC", "NOT_STARTED")
        val statusFilter = statusValues[solvedFilterCombo.selectedIndex]
        val cookies = AuthService.getInstance().getCookies(ProblemSource.LEETCODE).ifBlank { null }

        if (statusFilter != null && cookies == null) {
            statusLabel.text = I18n.t("LeetCode 로그인이 필요합니다.", "LeetCode login required.")
            statusLabel.foreground = JBColor.RED
            searchButton.isEnabled = true
            return
        }

        Thread {
            try {
                val result = LeetCodeApi.searchProblems(
                    query = query, difficulty = difficulty, tags = tags,
                    status = statusFilter, limit = 50, cookies = cookies
                )
                results = result.problems
                SwingUtilities.invokeLater {
                    for (p in results) {
                        tableModel.addRow(arrayOf(
                            p.frontendId,
                            p.title,
                            p.difficulty,
                            String.format("%.1f%%", p.acRate),
                            p.tags.take(3).joinToString(", ")
                        ))
                    }
                    statusLabel.text = when {
                        results.isEmpty() -> I18n.t("검색 결과가 없습니다", "No results found")
                        result.totalCount > results.size ->
                            I18n.t("총 ${result.totalCount}개 중 ${results.size}개 표시",
                                "Showing ${results.size} of ${result.totalCount}")
                        else -> I18n.t("${results.size}개 문제를 찾았습니다",
                            "Found ${results.size} problems")
                    }
                    searchButton.isEnabled = true
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    statusLabel.text = "${I18n.t("오류", "Error")}: ${e.message}"
                    searchButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun comboRenderer() = object : DefaultListCellRenderer() {
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

    private fun toggleTranslation() {
        if (results.isEmpty()) return
        showingTranslated = !showingTranslated
        if (showingTranslated && translatedTitles.isEmpty()) {
            // 번역 실행 (백그라운드)
            translateButton.isEnabled = false
            translateButton.text = "..."
            Thread {
                try {
                    val titles = results.map { it.title }
                    val batch = titles.joinToString("\n")
                    val translated = TranslateService.translate(batch, "en", "ko")
                    val translatedList = translated.split("\n")
                    val map = mutableMapOf<String, String>()
                    for ((idx, p) in results.withIndex()) {
                        if (idx < translatedList.size) map[p.titleSlug] = translatedList[idx].trim()
                    }
                    translatedTitles = map
                    SwingUtilities.invokeLater { applyTranslation(); translateButton.isEnabled = true }
                } catch (_: Exception) {
                    SwingUtilities.invokeLater {
                        showingTranslated = false
                        translateButton.text = "KO"
                        translateButton.isEnabled = true
                        statusLabel.text = I18n.t("번역 실패", "Translation failed")
                        statusLabel.foreground = JBColor.RED
                    }
                }
            }.start()
        } else {
            applyTranslation()
        }
    }

    private fun applyTranslation() {
        translateButton.text = if (showingTranslated) "EN" else "KO"
        translateButton.toolTipText = if (showingTranslated) I18n.t("영어로 보기", "Show in English") else I18n.t("한국어로 번역", "Translate to Korean")
        for ((idx, p) in results.withIndex()) {
            if (idx < tableModel.rowCount) {
                val title = if (showingTranslated) translatedTitles[p.titleSlug] ?: p.title else p.title
                tableModel.setValueAt(title, idx, 1)
                val tags = if (showingTranslated) p.tags.take(3).map { LeetCodeApi.tagToKo(it) }.joinToString(", ")
                    else p.tags.take(3).joinToString(", ")
                tableModel.setValueAt(tags, idx, 4)
            }
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = searchField
}
