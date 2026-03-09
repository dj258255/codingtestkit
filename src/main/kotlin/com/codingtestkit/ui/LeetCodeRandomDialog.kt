package com.codingtestkit.ui

import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.AuthService
import com.codingtestkit.service.I18n
import com.codingtestkit.service.LeetCodeApi
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

class LeetCodeRandomDialog(private val project: Project) : DialogWrapper(project) {

    // ─── 난이도 ───

    private val difficultyChecks = mapOf(
        "EASY" to JCheckBox("Easy").apply { isSelected = true },
        "MEDIUM" to JCheckBox("Medium").apply { isSelected = true },
        "HARD" to JCheckBox("Hard").apply { isSelected = true }
    )

    // ─── 태그 다중선택 ───

    private val tagEntries = listOf(
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

    private val selectedTags = mutableSetOf<String>()
    private val dialogWidth = JBUI.scale(680)
    private val tagChipPanel = object : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(3))) {
        override fun getPreferredSize(): Dimension {
            val width = (parent?.width?.takeIf { it > 0 } ?: dialogWidth).coerceAtMost(dialogWidth)
            val layout = layout as FlowLayout
            val insets = insets
            var x = insets.left + layout.hgap
            var y = insets.top + layout.vgap
            var rowHeight = 0
            for (comp in components) {
                if (!comp.isVisible) continue
                val d = comp.preferredSize
                if (x + d.width + layout.hgap + insets.right > width && x > insets.left + layout.hgap) {
                    x = insets.left + layout.hgap
                    y += rowHeight + layout.vgap
                    rowHeight = 0
                }
                x += d.width + layout.hgap
                rowHeight = maxOf(rowHeight, d.height)
            }
            return Dimension(width, y + rowHeight + layout.vgap + insets.bottom)
        }
        override fun getMaximumSize(): Dimension = Dimension(dialogWidth, preferredSize.height)
    }
    private val addTagButton = JButton("+").apply {
        font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
        toolTipText = I18n.t("클릭하여 태그 선택", "Click to select tags")
        putClientProperty("JButton.buttonType", "roundRect")
        preferredSize = Dimension(JBUI.scale(36), JBUI.scale(28))
    }

    // ─── 옵션 ───

    private val minAcceptedCheck = JCheckBox(
        I18n.t("듣보 문제 제외 (정답자", "Exclude obscure (accepted ≥")
    ).apply {
        font = font.deriveFont(JBUI.scaleFontSize(13f).toFloat())
        isSelected = true
    }
    private val minAcceptedField = JTextField("1000").apply {
        font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(13))
        horizontalAlignment = JTextField.CENTER
        val dim = Dimension(JBUI.scale(80), JBUI.scale(28))
        preferredSize = dim; minimumSize = dim; maximumSize = dim
    }
    private val minAcceptedSuffix = JLabel(
        I18n.t("명 이상)", " users)")
    ).apply {
        font = font.deriveFont(JBUI.scaleFontSize(13f).toFloat())
    }

    private val solvedFilterCombo = com.intellij.openapi.ui.ComboBox(arrayOf(
        I18n.t("전체", "All"),
        I18n.t("내가 푼 문제 제외", "Exclude my solved"),
        I18n.t("내가 푼 문제에서만", "Only my solved")
    )).apply {
        val cookies = AuthService.getInstance().getCookies(ProblemSource.LEETCODE)
        isEnabled = cookies.isNotBlank()
        if (cookies.isBlank()) toolTipText = I18n.t(
            "LeetCode 로그인이 필요합니다 (제출 탭에서 로그인)",
            "LeetCode login required (login via Submit tab)"
        )
    }

    // ─── 기타 ───

    private val countField = JTextField("5").apply {
        font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(13))
        horizontalAlignment = JTextField.CENTER
        val dim = Dimension(JBUI.scale(52), JBUI.scale(28))
        preferredSize = dim; minimumSize = dim; maximumSize = dim
    }
    private val searchButton = JButton(I18n.t("뽑기", "Pick"))

    private val checkedRows = mutableSetOf<Int>()
    private val tableModel = object : DefaultTableModel(
        arrayOf("", "#", I18n.t("제목", "Title"), I18n.t("난이도", "Difficulty"),
            I18n.t("정답자", "Accepted"), I18n.t("태그", "Tags")), 0
    ) {
        override fun isCellEditable(row: Int, column: Int) = column == 0
        override fun getColumnClass(columnIndex: Int): Class<*> = if (columnIndex == 0) java.lang.Boolean::class.java else super.getColumnClass(columnIndex)
        override fun setValueAt(aValue: Any?, row: Int, column: Int) {
            super.setValueAt(aValue, row, column)
            if (column == 0) {
                if (aValue == true) checkedRows.add(row) else checkedRows.remove(row)
                updateOKEnabled()
            }
        }
    }
    private val resultTable = JBTable(tableModel)
    private val statusLabel = JLabel(I18n.t("조건을 선택하고 '뽑기'를 클릭하세요", "Select filters and click 'Pick'"))

    private var results: List<LeetCodeApi.LeetCodeProblemInfo> = emptyList()
    private var headerCheck = JCheckBox().apply { horizontalAlignment = SwingConstants.CENTER }
    var selectedProblemSlugs: List<String> = emptyList()
        private set

    init {
        title = I18n.t("LeetCode 랜덤 문제 뽑기", "LeetCode Random Problem Picker")
        setOKButtonText(I18n.t("가져오기", "Fetch"))
        setCancelButtonText(I18n.t("닫기", "Close"))
        init()
        isOKActionEnabled = false
    }

    private var doubleClicked = false

    override fun doOKAction() {
        if (!doubleClicked) {
            selectedProblemSlugs = checkedRows.filter { it in results.indices }.map { results[it].titleSlug }
        }
        super.doOKAction()
    }

    private fun updateOKEnabled() {
        isOKActionEnabled = checkedRows.any { it in results.indices }
    }

    override fun createCenterPanel(): JComponent {
        val dialogH = JBUI.scale(430)
        val panel = object : JPanel(BorderLayout(0, JBUI.scale(6))) {
            override fun getPreferredSize() = Dimension(dialogWidth, dialogH)
            override fun getMinimumSize() = Dimension(dialogWidth, dialogH)
            override fun getMaximumSize() = Dimension(dialogWidth, dialogH)
        }

        val topPanel = object : JPanel() {
            init { layout = BoxLayout(this, BoxLayout.Y_AXIS); border = JBUI.Borders.empty(4) }
            override fun getPreferredSize(): Dimension {
                val d = super.getPreferredSize()
                return Dimension(d.width.coerceAtMost(dialogWidth), d.height)
            }
            override fun getMaximumSize(): Dimension = Dimension(dialogWidth, super.getMaximumSize().height)
        }

        // Row 1: 난이도 체크박스
        val row1 = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply { alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40)) }
        row1.add(createLabel(I18n.t("난이도", "Difficulty") + "  "))
        for ((_, check) in difficultyChecks) {
            check.font = check.font.deriveFont(JBUI.scaleFontSize(13f).toFloat())
            row1.add(check)
        }
        topPanel.add(row1)

        // Row 2: 태그 칩 (반응형 래핑)
        updateTagChips()
        tagChipPanel.border = JBUI.Borders.empty(2, 4)
        tagChipPanel.alignmentX = Component.LEFT_ALIGNMENT
        topPanel.add(tagChipPanel)

        // Row 3: 정답자 수 필터
        val filterRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply { alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40)) }
        filterRow.add(minAcceptedCheck)
        filterRow.add(minAcceptedField)
        filterRow.add(minAcceptedSuffix)
        minAcceptedCheck.addActionListener { minAcceptedField.isEnabled = minAcceptedCheck.isSelected }
        topPanel.add(filterRow)

        // Row 4: 풀이 필터
        val solvedRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply { alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40)) }
        solvedRow.add(createLabel(I18n.t("풀이 필터", "Solved Filter")))
        solvedRow.add(solvedFilterCombo)
        topPanel.add(solvedRow)

        // Row 5: 개수 + 뽑기
        val countRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply { alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40)) }
        countRow.add(createLabel(I18n.t("개수", "Count")))
        countRow.add(countField)
        countRow.add(searchButton)
        topPanel.add(countRow)

        panel.add(topPanel, BorderLayout.NORTH)

        // 결과 테이블
        resultTable.rowHeight = JBUI.scale(24)
        resultTable.tableHeader.preferredSize = Dimension(0, JBUI.scale(28))
        // 체크박스 열 렌더러 (배경색 통일)
        resultTable.columnModel.getColumn(0).cellRenderer = object : javax.swing.table.TableCellRenderer {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): Component {
                return JCheckBox().apply {
                    this.isSelected = value == true
                    horizontalAlignment = SwingConstants.CENTER
                    isOpaque = false
                }
            }
        }
        resultTable.columnModel.getColumn(0).apply { preferredWidth = JBUI.scale(30); maxWidth = JBUI.scale(30); minWidth = JBUI.scale(30) }
        resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(45)
        resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(240)
        resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(65)
        resultTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(65)
        resultTable.columnModel.getColumn(5).preferredWidth = JBUI.scale(180)

        // 헤더 전체 선택 체크박스 (기본 헤더 스타일 적용)
        resultTable.columnModel.getColumn(0).headerRenderer =
            javax.swing.table.TableCellRenderer { table, _, _, _, _, _ ->
                val defaultRenderer = table.tableHeader.defaultRenderer
                val headerComp = defaultRenderer.getTableCellRendererComponent(table, "", false, false, -1, 0) as JComponent
                JPanel(BorderLayout()).apply {
                    background = headerComp.background
                    border = headerComp.border
                    add(headerCheck, BorderLayout.CENTER)
                    headerCheck.isOpaque = false
                }
            }
        resultTable.tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val col = resultTable.columnAtPoint(e.point)
                if (col == 0) {
                    val selectAll = !headerCheck.isSelected
                    headerCheck.isSelected = selectAll
                    for (row in 0 until tableModel.rowCount) {
                        tableModel.setValueAt(selectAll, row, 0)
                    }
                    resultTable.repaint()
                }
            }
        })

        panel.add(JScrollPane(resultTable), BorderLayout.CENTER)

        statusLabel.foreground = JBColor.GRAY
        statusLabel.font = statusLabel.font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
        panel.add(statusLabel, BorderLayout.SOUTH)

        // 이벤트
        addTagButton.addActionListener { showTagPopup() }
        searchButton.addActionListener { searchProblems() }
        resultTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && resultTable.selectedRow >= 0) {
                    selectedProblemSlugs = listOf(results[resultTable.selectedRow].titleSlug)
                    doubleClicked = true
                    doOKAction()
                }
            }
        })

        return panel
    }

    // ─── 태그 팝업 ───

    private fun showTagPopup() {
        val checkboxes = mutableListOf<JCheckBox>()
        val listPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

        // 전체 선택/해제
        val selectAllCheck = JCheckBox(I18n.t("전체 선택 / 해제", "Select / Deselect All")).apply {
            isSelected = selectedTags.size == tagEntries.size
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
            border = JBUI.Borders.empty(4, 6)
            isOpaque = false
        }
        selectAllCheck.addActionListener {
            if (selectAllCheck.isSelected) {
                selectedTags.addAll(tagEntries.map { it.first })
                checkboxes.forEach { it.isSelected = true }
            } else {
                selectedTags.clear()
                checkboxes.forEach { it.isSelected = false }
            }
            updateTagChips()
        }
        listPanel.add(selectAllCheck)
        listPanel.add(JSeparator())

        for ((key, display) in tagEntries) {
            val cb = JCheckBox(display).apply {
                isSelected = key in selectedTags
                font = font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
                border = JBUI.Borders.empty(3, 6)
                isOpaque = false
            }
            cb.addActionListener {
                if (cb.isSelected) selectedTags.add(key) else selectedTags.remove(key)
                selectAllCheck.isSelected = selectedTags.size == tagEntries.size
                updateTagChips()
            }
            checkboxes.add(cb)
            listPanel.add(cb)
        }

        val scrollPane = JScrollPane(listPanel).apply {
            preferredSize = Dimension(JBUI.scale(220), JBUI.scale(320))
            border = null
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }

        // 드래그 스크롤
        var dragStartY = 0
        var dragStartScroll = 0
        listPanel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                dragStartY = e.yOnScreen
                dragStartScroll = scrollPane.verticalScrollBar.value
            }
        })
        listPanel.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                scrollPane.verticalScrollBar.value = dragStartScroll + (dragStartY - e.yOnScreen)
            }
        })

        // JBPopupFactory 사용 (JPopupMenu의 MouseGrabber 문제 회피)
        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollPane, null)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .createPopup()
            .showUnderneathOf(addTagButton)
    }

    private fun updateTagChips() {
        tagChipPanel.removeAll()
        tagChipPanel.add(createLabel(I18n.t("태그", "Tags")))
        if (selectedTags.isEmpty()) {
            tagChipPanel.add(JLabel(I18n.t("전체", "All")).apply {
                font = font.deriveFont(JBUI.scaleFontSize(13f).toFloat())
                foreground = JBColor.GRAY
            })
        } else {
            for (key in selectedTags.toList()) {
                val display = tagEntries.firstOrNull { it.first == key }?.second ?: key
                tagChipPanel.add(createTagChip(display, key))
            }
        }
        tagChipPanel.add(addTagButton)
        // 다이얼로그 현재 크기 유지 (태그 변경 시 가로 확장 방지)
        val window = SwingUtilities.getWindowAncestor(tagChipPanel)
        val prevSize = window?.size
        tagChipPanel.revalidate()
        tagChipPanel.repaint()
        if (prevSize != null) window?.size = prevSize
    }

    private fun createTagChip(display: String, key: String): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0)).apply {
            isOpaque = true
            background = JBColor(Color(220, 230, 245), Color(60, 75, 95))
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor(Color(180, 195, 220), Color(80, 95, 115)), 1, true),
                JBUI.Borders.empty(1, 4, 1, 2)
            )
            add(JLabel(display).apply {
                font = font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
            })
            val removeBtn = JLabel(" ×").apply {
                font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
                foreground = JBColor.GRAY
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }
            removeBtn.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    selectedTags.remove(key)
                    updateTagChips()
                }
                override fun mouseEntered(e: MouseEvent) { removeBtn.foreground = JBColor.RED }
                override fun mouseExited(e: MouseEvent) { removeBtn.foreground = JBColor.GRAY }
            })
            add(removeBtn)
        }
    }

    // ─── 검색 ───

    private fun searchProblems() {
        // 선택된 난이도 수집
        val selectedDifficulties = difficultyChecks.filter { it.value.isSelected }.map { it.key }
        if (selectedDifficulties.isEmpty()) {
            statusLabel.text = I18n.t("난이도를 하나 이상 선택하세요", "Select at least one difficulty")
            statusLabel.foreground = JBColor.RED
            return
        }

        searchButton.isEnabled = false
        statusLabel.foreground = JBColor.GRAY
        statusLabel.text = I18n.t("검색 중...", "Searching...")
        tableModel.rowCount = 0
        isOKActionEnabled = false

        val tags = selectedTags.toList().ifEmpty { null }
        val count = try { countField.text.trim().toInt().coerceIn(1, 20) } catch (_: Exception) { 5 }

        val useAcceptedFilter = minAcceptedCheck.isSelected
        val acceptedThreshold = if (useAcceptedFilter) {
            try { minAcceptedField.text.trim().toInt().coerceAtLeast(0) } catch (_: Exception) { 1000 }
        } else 0
        val solvedMode = solvedFilterCombo.selectedIndex // 0=전체, 1=제외, 2=에서만
        if (solvedMode > 0 && AuthService.getInstance().getCookies(ProblemSource.LEETCODE).isBlank()) {
            statusLabel.text = I18n.t("LeetCode 로그인이 필요합니다.",
                "LeetCode login required.")
            statusLabel.foreground = JBColor.RED
            searchButton.isEnabled = true
            return
        }

        // LeetCode API는 difficulty를 하나만 받으므로 각 난이도별로 요청
        Thread {
            try {
                // 정답자 수 / 풀이 상태를 위해 전체 통계 로드
                val cookies = AuthService.getInstance().getCookies(ProblemSource.LEETCODE)
                val stats = if (useAcceptedFilter || solvedMode > 0)
                    LeetCodeApi.fetchAllProblemStats(cookies.ifBlank { null })
                else emptyMap()

                val allProblems = mutableListOf<LeetCodeApi.LeetCodeProblemInfo>()

                for (diff in selectedDifficulties) {
                    val result = LeetCodeApi.searchProblems(
                        difficulty = diff, tags = tags, limit = 50
                    )
                    allProblems.addAll(result.problems)
                }

                // 정답자 수 필터 적용
                if (useAcceptedFilter) {
                    allProblems.removeAll { p ->
                        val stat = stats[p.frontendId]
                        stat == null || stat.totalAccepted < acceptedThreshold
                    }
                }

                // 풀이 필터 적용
                if (solvedMode == 1) {
                    // 내가 푼 문제 제외
                    allProblems.removeAll { p ->
                        stats[p.frontendId]?.status == "ac"
                    }
                } else if (solvedMode == 2) {
                    // 내가 푼 문제에서만
                    allProblems.removeAll { p ->
                        stats[p.frontendId]?.status != "ac"
                    }
                }

                // 랜덤으로 섞어서 count개 선택
                results = allProblems.shuffled().take(count)

                SwingUtilities.invokeLater {
                    checkedRows.clear()
                    for ((idx, p) in results.withIndex()) {
                        checkedRows.add(idx)
                        val accepted = stats[p.frontendId]?.totalAccepted
                        val acceptedStr = accepted?.let { formatCount(it) } ?: String.format("%.1f%%", p.acRate)
                        tableModel.addRow(arrayOf(
                            true,
                            p.frontendId,
                            p.title,
                            p.difficulty,
                            acceptedStr,
                            p.tags.take(3).joinToString(", ")
                        ))
                    }
                    isOKActionEnabled = results.isNotEmpty()
                    headerCheck.isSelected = results.isNotEmpty()
                    statusLabel.text = if (results.isEmpty())
                        I18n.t("조건에 맞는 문제가 없습니다", "No problems matching criteria")
                    else
                        I18n.t("${results.size}개 문제 (체크 후 '가져오기', 더블클릭=1개)",
                            "Found ${results.size} (check rows + Fetch; double-click = 1)")
                    searchButton.isEnabled = true
                    resultTable.tableHeader.repaint()
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    statusLabel.text = "${I18n.t("오류", "Error")}: ${e.message}"
                    searchButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun createLabel(text: String): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
        }
    }

    private fun formatCount(n: Int): String = when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}
