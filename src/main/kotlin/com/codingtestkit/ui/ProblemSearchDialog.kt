package com.codingtestkit.ui

import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.AuthService
import com.codingtestkit.service.I18n
import com.codingtestkit.service.SolvedAcApi
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

class ProblemSearchDialog(private val project: Project) : DialogWrapper(project) {

    private val searchField = JTextField().apply {
        putClientProperty("JTextField.placeholderText", I18n.t("문제 제목, 번호, 또는 solved.ac 쿼리 입력", "Enter title, number, or solved.ac query"))
    }
    private val sortCombo = ComboBox(arrayOf(
        I18n.t("번호순", "By Number"),
        I18n.t("난이도순", "By Difficulty"),
        I18n.t("제목순", "By Title"),
        I18n.t("푼 사람순", "By Solved")
    )).apply {
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
    private val searchButton = JButton(I18n.t("검색", "Search"))
    private val solvedFilterCombo = ComboBox(arrayOf(
        I18n.t("전체", "All"),
        I18n.t("내가 푼 문제 제외", "Exclude my solved"),
        I18n.t("내가 푼 문제만", "Only my solved")
    )).apply {
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
        val loggedIn = AuthService.getInstance().isLoggedIn(ProblemSource.BAEKJOON)
        isEnabled = loggedIn
        if (!loggedIn) {
            toolTipText = I18n.t("백준 로그인이 필요합니다", "BOJ login required")
            val combo = this
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder(
                            I18n.t("풀이 필터를 사용하려면<br>백준에 로그인하세요", "Login to BOJ<br>to use solved filter"),
                            MessageType.WARNING, null
                        )
                        .setFadeoutTime(3000)
                        .createBalloon()
                        .show(RelativePoint.getCenterOf(combo), Balloon.Position.above)
                }
            })
        }
    }

    // 자동완성
    private val suggestionPopup = JPopupMenu()
    private val debounceTimer = Timer(300, null).apply { isRepeats = false }

    // 결과 테이블
    private val tableModel = object : DefaultTableModel(
        arrayOf(I18n.t("번호", "No."), I18n.t("제목", "Title"), I18n.t("난이도", "Difficulty"), I18n.t("태그", "Tags"), I18n.t("맞은 사람", "Solved")), 0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val resultTable = JBTable(tableModel).apply {
        emptyText.text = I18n.t("표시할 항목이 없습니다", "No items to display")
    }
    private val statusLabel = JLabel(I18n.t("문제 제목이나 번호를 입력하세요", "Enter a problem title or number"))

    private var results: List<SolvedAcApi.ProblemInfo> = emptyList()
    private var showingTranslated = false
    private var translatedTitles: Map<Int, String> = emptyMap()
    private val translateButton = JButton("EN").apply {
        toolTipText = I18n.t("영어로 번역", "Translate to English")
        preferredSize = Dimension(JBUI.scale(50), preferredSize.height)
        addActionListener { toggleTranslation() }
    }
    var selectedProblemId: Int? = null
        private set

    init {
        title = I18n.t("문제 검색 (solved.ac)", "Problem Search (solved.ac)")
        setOKButtonText(I18n.t("가져오기", "Fetch"))
        setCancelButtonText(I18n.t("닫기", "Close"))
        init()
        isOKActionEnabled = false
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(700), JBUI.scale(450))

        // 검색 바
        val searchPanel = JPanel(BorderLayout(JBUI.scale(4), 0))
        searchPanel.border = JBUI.Borders.empty(4)
        searchPanel.add(searchField, BorderLayout.CENTER)

        val rightPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        rightPanel.add(sortCombo)
        rightPanel.add(solvedFilterCombo)
        rightPanel.add(searchButton)
        searchPanel.add(rightPanel, BorderLayout.EAST)

        panel.add(searchPanel, BorderLayout.NORTH)

        // 결과 테이블
        resultTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultTable.rowHeight = JBUI.scale(24)
        resultTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(60)
        resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(250)
        resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(90)
        resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(180)
        resultTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(70)

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
        setupAutocomplete()
        searchButton.addActionListener { doSearch() }
        searchField.addActionListener { doSearch() }

        resultTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = resultTable.selectedRow
                isOKActionEnabled = row in results.indices
                if (isOKActionEnabled) {
                    selectedProblemId = results[row].problemId
                }
            }
        }
        resultTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && resultTable.selectedRow >= 0) {
                    selectedProblemId = results[resultTable.selectedRow].problemId
                    doOKAction()
                }
            }
        })

        return panel
    }

    private fun setupAutocomplete() {
        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent) {
                // Enter, ESC, 방향키는 무시
                if (e.keyCode in listOf(
                    KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE,
                    KeyEvent.VK_UP, KeyEvent.VK_DOWN
                )) return

                debounceTimer.stop()
                debounceTimer.actionListeners.forEach { debounceTimer.removeActionListener(it) }
                debounceTimer.addActionListener { fetchSuggestions() }
                debounceTimer.start()
            }
        })
    }

    private fun fetchSuggestions() {
        val query = searchField.text.trim()
        if (query.length < 2) {
            suggestionPopup.isVisible = false
            return
        }

        Thread {
            try {
                val suggestions = SolvedAcApi.searchSuggestions(query)
                SwingUtilities.invokeLater {
                    showSuggestions(suggestions)
                }
            } catch (_: Exception) {
                // 자동완성 실패는 무시
            }
        }.start()
    }

    private fun showSuggestions(suggestions: List<SolvedAcApi.ProblemInfo>) {
        suggestionPopup.removeAll()
        if (suggestions.isEmpty()) {
            suggestionPopup.isVisible = false
            return
        }

        for (p in suggestions.take(10)) {
            val level = SolvedAcApi.levelToString(p.level)
            val title = p.title
            val text = "${p.problemId}. $title  [$level]"
            val item = JMenuItem(text).apply {
                font = font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
            }
            item.addActionListener {
                searchField.text = p.problemId.toString()
                suggestionPopup.isVisible = false
                doSearch()
            }
            suggestionPopup.add(item)
        }

        suggestionPopup.show(searchField, 0, searchField.height)
        searchField.requestFocusInWindow()
    }

    private fun doSearch() {
        val baseQuery = searchField.text.trim()
        val solvedMode = solvedFilterCombo.selectedIndex // 0=전체, 1=제외, 2=만

        if (baseQuery.isBlank() && solvedMode == 0) return

        suggestionPopup.isVisible = false
        searchButton.isEnabled = false
        statusLabel.text = I18n.t("검색 중...", "Searching...")
        statusLabel.foreground = JBColor.GRAY
        tableModel.rowCount = 0
        isOKActionEnabled = false

        val sortKeys = arrayOf("id", "level", "title", "solved")
        val sort = sortKeys[sortCombo.selectedIndex]

        Thread {
            try {
                // 풀이 필터: 로그인된 BOJ 핸들로 solved_by 쿼리 자동 추가
                var query = baseQuery
                if (solvedMode > 0) {
                    val auth = AuthService.getInstance()
                    var handle = auth.getUsername(ProblemSource.BAEKJOON)
                    if (handle.isBlank() && auth.isLoggedIn(ProblemSource.BAEKJOON)) {
                        handle = auth.fetchUsername(ProblemSource.BAEKJOON)
                        if (handle.isNotBlank()) auth.setUsername(ProblemSource.BAEKJOON, handle)
                    }
                    if (handle.isBlank()) {
                        SwingUtilities.invokeLater {
                            statusLabel.text = I18n.t("백준 로그인이 필요합니다.", "BOJ login required.")
                            statusLabel.foreground = JBColor.RED
                            searchButton.isEnabled = true
                        }
                        return@Thread
                    }
                    query = if (solvedMode == 1) "$query -solved_by:$handle" else "$query solved_by:$handle"
                }
                if (query.isBlank()) {
                    SwingUtilities.invokeLater { searchButton.isEnabled = true }
                    return@Thread
                }
                val result = SolvedAcApi.searchProblems(query, sort)
                results = result.problems
                SwingUtilities.invokeLater {
                    translatedTitles = emptyMap() // 새 검색 시 번역 캐시 초기화
                    for (p in results) {
                        val title = if (showingTranslated) translatedTitles[p.problemId] ?: p.title else p.title
                        tableModel.addRow(arrayOf(
                            p.problemId,
                            title,
                            SolvedAcApi.levelToString(p.level),
                            p.tags.take(3).joinToString(", "),
                            p.acceptedUserCount
                        ))
                    }
                    statusLabel.text = when {
                        results.isEmpty() -> I18n.t("검색 결과가 없습니다", "No results found")
                        result.totalCount > results.size ->
                            I18n.t("총 ${result.totalCount}개 중 ${results.size}개 표시 (더블클릭으로 가져오기)",
                                "Showing ${results.size} of ${result.totalCount} (double-click to fetch)")
                        else -> I18n.t("${results.size}개 문제를 찾았습니다 (더블클릭으로 가져오기)",
                            "Found ${results.size} problems (double-click to fetch)")
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

    private fun toggleTranslation() {
        if (results.isEmpty()) return
        showingTranslated = !showingTranslated
        if (showingTranslated && translatedTitles.isEmpty()) {
            translateButton.isEnabled = false
            translateButton.text = "..."
            Thread {
                try {
                    val batch = results.map { it.title }.joinToString("\n")
                    val translated = TranslateService.translate(batch, "ko", "en")
                    val translatedList = translated.split("\n")
                    val map = mutableMapOf<Int, String>()
                    for ((idx, p) in results.withIndex()) {
                        if (idx < translatedList.size) map[p.problemId] = translatedList[idx].trim()
                    }
                    translatedTitles = map
                    SwingUtilities.invokeLater { applyTranslation(); translateButton.isEnabled = true }
                } catch (_: Exception) {
                    SwingUtilities.invokeLater {
                        showingTranslated = false
                        translateButton.text = "EN"
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
        translateButton.text = if (showingTranslated) "KO" else "EN"
        translateButton.toolTipText = if (showingTranslated)
            I18n.t("한국어 원문 보기", "Show original Korean") else I18n.t("영어로 번역", "Translate to English")
        for ((idx, p) in results.withIndex()) {
            if (idx < tableModel.rowCount) {
                val title = if (showingTranslated) translatedTitles[p.problemId] ?: p.title else p.title
                tableModel.setValueAt(title, idx, 1)
                val tags = if (showingTranslated) p.tagsEn.take(3).joinToString(", ") else p.tags.take(3).joinToString(", ")
                tableModel.setValueAt(tags, idx, 3)
            }
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = searchField
}
