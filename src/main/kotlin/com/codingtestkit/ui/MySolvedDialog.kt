package com.codingtestkit.ui

import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.AuthService
import com.codingtestkit.service.I18n
import com.codingtestkit.service.SolvedProblemsService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

class MySolvedDialog(
    private val project: Project,
    private val source: ProblemSource
) : DialogWrapper(project) {

    private val username: String = AuthService.getInstance().getUsername(source)

    private val searchField = JTextField().apply {
        putClientProperty("JTextField.placeholderText", I18n.t("풀이 기록 내 검색", "Search within solved"))
    }
    private val searchButton = JButton(I18n.t("검색", "Search"))

    private val columns = when (source) {
        ProblemSource.LEETCODE -> arrayOf(
            I18n.t("제목", "Title"), I18n.t("난이도", "Difficulty"),
            I18n.t("정답률", "Acceptance"), I18n.t("태그", "Tags")
        )
        ProblemSource.BAEKJOON -> arrayOf(
            I18n.t("번호", "ID"), I18n.t("제목", "Title"),
            I18n.t("난이도", "Rating"), I18n.t("태그", "Tags")
        )
        else -> arrayOf(
            I18n.t("번호", "ID"), I18n.t("제목", "Title"),
            I18n.t("난이도", "Rating"), I18n.t("태그", "Tags"),
            I18n.t("날짜", "Date")
        )
    }

    private val tableModel = object : DefaultTableModel(columns, 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val resultTable = JBTable(tableModel).apply {
        emptyText.text = I18n.t("로딩 중...", "Loading...")
    }
    private val statusLabel = JLabel("")
    private val prevButton = JButton("< " + I18n.t("이전", "Prev"))
    private val nextButton = JButton(I18n.t("다음", "Next") + " >")
    private val pageLabel = JLabel("")

    private var currentPage = 1
    private var totalPages = 0
    private var currentQuery = ""
    private var currentResults: List<SolvedProblemsService.SolvedProblem> = emptyList()

    var selectedProblemId: String? = null
        private set

    init {
        val platformName = source.localizedName()
        title = I18n.t("$platformName 풀이 기록 — $username", "$platformName Solved — $username")
        setOKButtonText(I18n.t("가져오기", "Fetch"))
        setCancelButtonText(I18n.t("닫기", "Close"))
        init()
        isOKActionEnabled = false

        // 초기 로드
        loadPage(1)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(720), JBUI.scale(450))

        // 상단: 검색
        val searchPanel = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            border = JBUI.Borders.empty(4)
        }
        searchPanel.add(searchField, BorderLayout.CENTER)
        searchPanel.add(searchButton, BorderLayout.EAST)
        panel.add(searchPanel, BorderLayout.NORTH)

        // 테이블
        resultTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultTable.rowHeight = JBUI.scale(24)

        when (source) {
            ProblemSource.LEETCODE -> {
                resultTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(280)
                resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(70)
                resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(70)
                resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(200)
            }
            ProblemSource.BAEKJOON -> {
                resultTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(70)
                resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(300)
                resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(80)
                resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(200)
            }
            else -> {
                resultTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(70)
                resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(250)
                resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(80)
                resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(200)
                resultTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(90)
            }
        }

        panel.add(JScrollPane(resultTable), BorderLayout.CENTER)

        // 하단: 페이지네이션 + 상태
        val bottomPanel = JPanel(BorderLayout())
        statusLabel.foreground = JBColor.GRAY
        statusLabel.font = statusLabel.font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
        bottomPanel.add(statusLabel, BorderLayout.WEST)

        val pagePanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0))
        prevButton.isEnabled = false
        nextButton.isEnabled = false
        pagePanel.add(prevButton)
        pagePanel.add(pageLabel)
        pagePanel.add(nextButton)
        bottomPanel.add(pagePanel, BorderLayout.EAST)

        panel.add(bottomPanel, BorderLayout.SOUTH)

        // 이벤트
        searchButton.addActionListener { doSearch() }
        searchField.addActionListener { doSearch() }
        prevButton.addActionListener { if (currentPage > 1) loadPage(currentPage - 1) }
        nextButton.addActionListener { if (currentPage < totalPages) loadPage(currentPage + 1) }

        resultTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = resultTable.selectedRow
                isOKActionEnabled = row in currentResults.indices
                if (isOKActionEnabled) selectedProblemId = currentResults[row].id
            }
        }
        resultTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && resultTable.selectedRow >= 0) {
                    selectedProblemId = currentResults[resultTable.selectedRow].id
                    doOKAction()
                }
            }
        })

        return panel
    }

    private fun doSearch() {
        currentQuery = searchField.text.trim()
        loadPage(1)
    }

    private fun loadPage(page: Int) {
        searchButton.isEnabled = false
        prevButton.isEnabled = false
        nextButton.isEnabled = false
        statusLabel.text = I18n.t("불러오는 중...", "Loading...")
        tableModel.rowCount = 0
        isOKActionEnabled = false

        Thread {
            try {
                val result = SolvedProblemsService.fetch(source, username, page, currentQuery)
                SwingUtilities.invokeLater {
                    currentResults = result.problems
                    currentPage = result.currentPage
                    totalPages = result.totalPages

                    for (p in result.problems) {
                        when (source) {
                            ProblemSource.LEETCODE -> tableModel.addRow(arrayOf(
                                p.title, p.difficulty,
                                p.solvedDate,
                                p.tags.take(3).joinToString(", ")
                            ))
                            ProblemSource.BAEKJOON -> tableModel.addRow(arrayOf(
                                p.id, p.title, p.difficulty,
                                p.tags.take(3).joinToString(", ")
                            ))
                            else -> tableModel.addRow(arrayOf(
                                p.id, p.title, p.difficulty,
                                p.tags.take(3).joinToString(", "),
                                p.solvedDate
                            ))
                        }
                    }

                    statusLabel.text = if (result.totalCount == 0)
                        I18n.t("풀이 기록이 없습니다", "No solved problems found")
                    else I18n.t("총 ${result.totalCount}문제 (더블클릭으로 가져오기)",
                        "${result.totalCount} problems solved (double-click to fetch)")

                    pageLabel.text = if (totalPages > 0) "$currentPage / $totalPages" else ""
                    prevButton.isEnabled = currentPage > 1
                    nextButton.isEnabled = currentPage < totalPages
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

    override fun getPreferredFocusedComponent(): JComponent = searchField
}
