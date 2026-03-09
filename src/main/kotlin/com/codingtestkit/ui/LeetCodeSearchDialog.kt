package com.codingtestkit.ui

import com.codingtestkit.service.AuthService
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.I18n
import com.codingtestkit.service.LeetCodeApi
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
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

    private val tagEntries = listOf(
        "" to I18n.t("전체", "All"),
        "array" to "Array",
        "string" to "String",
        "hash-table" to "Hash Table",
        "dynamic-programming" to "DP",
        "math" to "Math",
        "sorting" to "Sorting",
        "greedy" to "Greedy",
        "depth-first-search" to "DFS",
        "breadth-first-search" to "BFS",
        "binary-search" to "Binary Search",
        "tree" to "Tree",
        "graph" to "Graph",
        "linked-list" to "Linked List",
        "stack" to "Stack",
        "heap-priority-queue" to "Heap",
        "two-pointers" to "Two Pointers",
        "sliding-window" to "Sliding Window",
        "backtracking" to "Backtracking",
        "divide-and-conquer" to "Divide & Conquer",
        "bit-manipulation" to "Bit Manipulation",
        "union-find" to "Union Find"
    )
    private val tagCombo = ComboBox(tagEntries.map { it.second }.toTypedArray()).apply {
        renderer = comboRenderer()
    }

    private val solvedFilterCombo = ComboBox(arrayOf(
        I18n.t("전체", "All"),
        I18n.t("내가 푼 문제만", "Only solved"),
        I18n.t("안 푼 문제만", "Not solved")
    )).apply { renderer = comboRenderer() }

    private val searchButton = JButton(I18n.t("검색", "Search"))

    private val tableModel = object : DefaultTableModel(
        arrayOf(I18n.t("상태", ""), "#", I18n.t("제목", "Title"), I18n.t("난이도", "Difficulty"),
            I18n.t("정답률", "AC Rate"), I18n.t("태그", "Tags")), 0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val resultTable = JBTable(tableModel)
    private val statusLabel = JLabel(I18n.t("키워드를 입력하고 검색하세요", "Enter a keyword and search"))

    private var results: List<LeetCodeApi.LeetCodeProblemInfo> = emptyList()
    private var problemStats: Map<String, LeetCodeApi.ProblemStat> = emptyMap()
    var selectedProblemSlug: String? = null
        private set

    init {
        title = I18n.t("LeetCode 문제 검색", "LeetCode Problem Search")
        setOKButtonText(I18n.t("가져오기", "Fetch"))
        setCancelButtonText(I18n.t("닫기", "Close"))
        init()
        isOKActionEnabled = false
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
        resultTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(30)
        resultTable.columnModel.getColumn(0).maxWidth = JBUI.scale(30)
        resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(50)
        resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(240)
        resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(70)
        resultTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(70)
        resultTable.columnModel.getColumn(5).preferredWidth = JBUI.scale(190)

        // 백그라운드에서 풀이 상태 로드
        Thread {
            try {
                val cookies = AuthService.getInstance().getCookies(ProblemSource.LEETCODE)
                problemStats = LeetCodeApi.fetchAllProblemStats(cookies.ifBlank { null })
            } catch (_: Exception) { }
        }.start()

        panel.add(JScrollPane(resultTable), BorderLayout.CENTER)

        // 상태
        statusLabel.foreground = JBColor.GRAY
        statusLabel.font = statusLabel.font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
        panel.add(statusLabel, BorderLayout.SOUTH)

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
                        val stat = problemStats[p.frontendId]
                        val statusMark = when (stat?.status) {
                            "ac" -> "\u2713"      // ✓
                            "notac" -> "\u2717"    // ✗
                            else -> ""
                        }
                        tableModel.addRow(arrayOf(
                            statusMark,
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

    override fun getPreferredFocusedComponent(): JComponent = searchField
}
