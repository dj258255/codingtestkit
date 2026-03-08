package com.codingtestkit.ui

import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.AuthService
import com.codingtestkit.service.I18n
import com.codingtestkit.service.SolvedAcApi
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

class RandomProblemDialog(private val project: Project) : DialogWrapper(project) {

    // ─── 필터 모드: 난이도 vs 클래스 ───

    private val filterModeCombo = ComboBox(arrayOf(
        I18n.t("난이도 (티어)", "Tier"),
        I18n.t("클래스", "Class")
    )).apply { renderer = comboRenderer() }

    // ─── 티어 범위 ───

    private data class TierLevel(val display: String, val code: String)

    private val tierLevels: List<TierLevel> = buildList {
        val tiers = listOf("Bronze" to "b", "Silver" to "s", "Gold" to "g",
            "Platinum" to "p", "Diamond" to "d", "Ruby" to "r")
        val subs = listOf("V" to "5", "IV" to "4", "III" to "3", "II" to "2", "I" to "1")
        for ((tierName, tierCode) in tiers) {
            for ((subName, subCode) in subs) {
                add(TierLevel("$tierName $subName", "$tierCode$subCode"))
            }
        }
    }

    private val tierFromCombo = ComboBox(tierLevels.map { it.display }.toTypedArray()).apply {
        renderer = comboRenderer(); selectedIndex = 0
    }
    private val tierToCombo = ComboBox(tierLevels.map { it.display }.toTypedArray()).apply {
        renderer = comboRenderer(); selectedIndex = tierLevels.size - 1
    }

    // ─── 클래스 범위 ───

    private val classNames = (1..10).map { it.toString() }.toTypedArray()
    private val classFromCombo = ComboBox(classNames).apply {
        renderer = comboRenderer(); selectedIndex = 0
    }
    private val classToCombo = ComboBox(classNames).apply {
        renderer = comboRenderer(); selectedIndex = 9
    }

    // 티어/클래스 카드 레이아웃
    private val tierPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
    private val classPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
    private val rangeCards = JPanel(CardLayout())

    // ─── 알고리즘 다중선택 ───

    private val tagEntries = listOf(
        "math" to I18n.t("수학", "Math"),
        "implementation" to I18n.t("구현", "Implementation"),
        "dp" to I18n.t("다이나믹 프로그래밍", "DP"),
        "graphs" to I18n.t("그래프 이론", "Graph"),
        "greedy" to I18n.t("그리디", "Greedy"),
        "sorting" to I18n.t("정렬", "Sorting"),
        "string" to I18n.t("문자열", "String"),
        "bruteforcing" to I18n.t("브루트포스", "Brute Force"),
        "binary_search" to I18n.t("이분 탐색", "Binary Search"),
        "bfs" to "BFS",
        "dfs" to "DFS",
        "trees" to I18n.t("트리", "Trees"),
        "data_structures" to I18n.t("자료 구조", "Data Structures"),
        "shortest_path" to I18n.t("최단 경로", "Shortest Path"),
        "backtracking" to I18n.t("백트래킹", "Backtracking"),
        "two_pointer" to I18n.t("투 포인터", "Two Pointer"),
        "divide_and_conquer" to I18n.t("분할 정복", "Divide & Conquer"),
        "segtree" to I18n.t("세그먼트 트리", "Segment Tree"),
        "union_find" to I18n.t("유니온 파인드", "Union Find"),
        "geometry" to I18n.t("기하학", "Geometry"),
        "number_theory" to I18n.t("정수론", "Number Theory")
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
        toolTipText = I18n.t("클릭하여 알고리즘 태그 선택", "Click to select algorithm tags")
        putClientProperty("JButton.buttonType", "roundRect")
        preferredSize = Dimension(JBUI.scale(36), JBUI.scale(28))
    }

    // ─── 옵션 체크박스 ───

    private val solvedFilterCombo = ComboBox(arrayOf(
        I18n.t("전체", "All"),
        I18n.t("내가 푼 문제 제외", "Exclude my solved"),
        I18n.t("내가 푼 문제에서만", "Only my solved")
    )).apply {
        renderer = comboRenderer()
        val loggedIn = AuthService.getInstance().isLoggedIn(ProblemSource.BAEKJOON)
        isEnabled = loggedIn
        if (!loggedIn) toolTipText = I18n.t("백준 로그인이 필요합니다", "BOJ login required")
    }

    private val excludeObscureCheck = JCheckBox(
        I18n.t("듣보 문제 제외 (맞은 사람", "Exclude obscure (≤")
    ).apply {
        font = font.deriveFont(JBUI.scaleFontSize(13f).toFloat())
        isSelected = true
    }
    private val obscureThresholdField = JTextField("100").apply {
        font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(13))
        horizontalAlignment = JTextField.CENTER
        val dim = Dimension(JBUI.scale(72), JBUI.scale(28))
        preferredSize = dim; minimumSize = dim; maximumSize = dim
    }
    private val obscureThresholdSuffix = JLabel(
        I18n.t("명 이하)", " solvers)")
    ).apply {
        font = font.deriveFont(JBUI.scaleFontSize(13f).toFloat())
    }

    // ─── 기타 ───

    private val countField = JTextField("5").apply {
        font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(13))
        horizontalAlignment = JTextField.CENTER
        val dim = Dimension(JBUI.scale(52), JBUI.scale(28))
        preferredSize = dim
        minimumSize = dim
        maximumSize = dim
    }
    private val searchButton = JButton(I18n.t("뽑기", "Pick"))

    private val checkedRows = mutableSetOf<Int>()
    private val tableModel = object : DefaultTableModel(
        arrayOf("", I18n.t("번호", "No."), I18n.t("제목", "Title"), I18n.t("난이도", "Difficulty"), I18n.t("태그", "Tags"), I18n.t("맞은 사람", "Solved")), 0
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

    private var results: List<SolvedAcApi.ProblemInfo> = emptyList()
    private var headerCheck = JCheckBox().apply { horizontalAlignment = SwingConstants.CENTER }
    var selectedProblemIds: List<Int> = emptyList()
        private set

    init {
        title = I18n.t("랜덤 문제 뽑기 (solved.ac)", "Random Problem Picker (solved.ac)")
        setOKButtonText(I18n.t("가져오기", "Fetch"))
        setCancelButtonText(I18n.t("닫기", "Close"))
        init()
        isOKActionEnabled = false
    }

    override fun doOKAction() {
        selectedProblemIds = checkedRows.filter { it in results.indices }.map { results[it].problemId }
        super.doOKAction()
    }

    private fun updateOKEnabled() {
        isOKActionEnabled = checkedRows.any { it in results.indices }
    }

    override fun createCenterPanel(): JComponent {
        val dialogH = JBUI.scale(470)
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

        // ── Row 1: 필터 모드 + 범위 ──
        val row1 = JPanel(GridBagLayout()).apply { alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40)) }
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(2, 4); fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST
        }

        gbc.gridx = 0; gbc.weightx = 0.0
        row1.add(createLabel(I18n.t("기준", "Filter")), gbc)
        gbc.gridx = 1; gbc.weightx = 0.15
        row1.add(filterModeCombo, gbc)

        // 티어 범위 패널
        tierPanel.add(tierFromCombo)
        tierPanel.add(JLabel(" ~ ").apply { font = font.deriveFont(Font.BOLD) })
        tierPanel.add(tierToCombo)

        // 클래스 범위 패널
        classPanel.add(JLabel("Class "))
        classPanel.add(classFromCombo)
        classPanel.add(JLabel(" ~ ").apply { font = font.deriveFont(Font.BOLD) })
        classPanel.add(classToCombo)

        rangeCards.add(tierPanel, "tier")
        rangeCards.add(classPanel, "class")

        gbc.gridx = 2; gbc.weightx = 0.7; gbc.gridwidth = 3
        row1.add(rangeCards, gbc)
        gbc.gridwidth = 1

        topPanel.add(row1)

        // ── Row 2: 알고리즘 태그 칩 (반응형 래핑) ──
        updateTagChips()
        tagChipPanel.border = JBUI.Borders.empty(2, 4)
        tagChipPanel.alignmentX = Component.LEFT_ALIGNMENT
        topPanel.add(tagChipPanel)

        // ── Row 3: 개수 + 뽑기 ──
        val countRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply { alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40)) }
        countRow.add(createLabel(I18n.t("개수", "Count")))
        countRow.add(countField)
        countRow.add(searchButton)
        topPanel.add(countRow)

        // ── Row 4: 풀이 필터 (드롭다운) ──
        val solvedRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply { alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40)) }
        solvedRow.add(createLabel(I18n.t("풀이 필터", "Solved Filter")))
        solvedRow.add(solvedFilterCombo)
        topPanel.add(solvedRow)

        // ── Row 5: 듣보 문제 제외 ──
        val obscureRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply { alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40)) }
        obscureRow.add(excludeObscureCheck)
        obscureRow.add(obscureThresholdField)
        obscureRow.add(obscureThresholdSuffix)

        excludeObscureCheck.addActionListener {
            obscureThresholdField.isEnabled = excludeObscureCheck.isSelected
        }

        topPanel.add(obscureRow)

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
        resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(55)
        resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(200)
        resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(80)
        resultTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(170)
        resultTable.columnModel.getColumn(5).preferredWidth = JBUI.scale(65)

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

        // 상태 라벨
        statusLabel.foreground = JBColor.GRAY
        statusLabel.font = statusLabel.font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
        panel.add(statusLabel, BorderLayout.SOUTH)

        // 이벤트
        filterModeCombo.addActionListener {
            val cl = rangeCards.layout as CardLayout
            if (filterModeCombo.selectedIndex == 0) cl.show(rangeCards, "tier") else cl.show(rangeCards, "class")
        }
        addTagButton.addActionListener { showTagPopup() }
        searchButton.addActionListener { searchProblems() }
        resultTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && resultTable.selectedRow >= 0) {
                    selectedProblemIds = listOf(results[resultTable.selectedRow].problemId)
                    doOKAction()
                }
            }
        })

        return panel
    }

    // ─── 태그 다중선택 팝업 ───

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
        tagChipPanel.add(createLabel(I18n.t("알고리즘", "Algorithm")))
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

    // ─── 유틸 ───

    private fun comboRenderer() = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            border = JBUI.Borders.empty(4, 8)
            font = font.deriveFont(JBUI.scaleFontSize(13f).toFloat())
            return c
        }
    }

    private fun createLabel(text: String): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
        }
    }

    // ─── 검색 ───

    private fun searchProblems() {
        searchButton.isEnabled = false
        statusLabel.foreground = JBColor.GRAY
        statusLabel.text = I18n.t("검색 중...", "Searching...")
        tableModel.rowCount = 0
        isOKActionEnabled = false

        val queryParts = mutableListOf("solvable:true")

        // 티어 or 클래스
        if (filterModeCombo.selectedIndex == 0) {
            val fromIdx = tierFromCombo.selectedIndex
            val toIdx = tierToCombo.selectedIndex
            if (fromIdx > toIdx) {
                statusLabel.text = I18n.t("시작 티어가 끝 티어보다 높습니다.",
                    "Start tier is higher than end tier.")
                statusLabel.foreground = JBColor.RED
                searchButton.isEnabled = true
                return
            }
            queryParts.add(buildTierQuery())
        } else {
            val fromIdx = classFromCombo.selectedIndex
            val toIdx = classToCombo.selectedIndex
            if (fromIdx > toIdx) {
                statusLabel.text = I18n.t("시작 클래스가 끝 클래스보다 높습니다.",
                    "Start class is higher than end class.")
                statusLabel.foreground = JBColor.RED
                searchButton.isEnabled = true
                return
            }
            queryParts.add(buildClassQuery())
        }

        // 태그
        buildTagQuery()?.let { queryParts.add(it) }

        // 듣보 제외
        if (excludeObscureCheck.isSelected) {
            val threshold = try { obscureThresholdField.text.trim().toInt().coerceAtLeast(1) } catch (_: Exception) { 100 }
            queryParts.add("solved:$threshold..")
        }

        // 풀이 필터
        val solvedMode = solvedFilterCombo.selectedIndex // 0=전체, 1=제외, 2=에서만
        if (solvedMode > 0) {
            val handle = AuthService.getInstance().getUsername(ProblemSource.BAEKJOON)
            if (handle.isBlank()) {
                statusLabel.text = I18n.t("백준 로그인이 필요합니다.",
                    "BOJ login required.")
                statusLabel.foreground = JBColor.RED
                searchButton.isEnabled = true
                return
            }
            if (solvedMode == 1) queryParts.add("-solved_by:$handle")
            else queryParts.add("solved_by:$handle")
        }

        val count = try { countField.text.trim().toInt().coerceIn(1, 20) } catch (_: Exception) { 5 }

        Thread {
            try {
                val result = SolvedAcApi.searchProblems(
                    query = queryParts.joinToString(" "),
                    sort = "random"
                )
                results = result.problems.take(count)
                SwingUtilities.invokeLater {
                    checkedRows.clear()
                    for ((idx, p) in results.withIndex()) {
                        checkedRows.add(idx)
                        tableModel.addRow(arrayOf(
                            true,
                            p.problemId,
                            p.title,
                            SolvedAcApi.levelToString(p.level),
                            p.tags.take(3).joinToString(", "),
                            p.acceptedUserCount
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

    private fun buildTierQuery(): String {
        val fromCode = tierLevels[tierFromCombo.selectedIndex].code
        val toCode = tierLevels[tierToCombo.selectedIndex].code
        return if (fromCode == toCode) "tier:$fromCode" else "tier:$fromCode..$toCode"
    }

    private fun buildClassQuery(): String {
        val from = classFromCombo.selectedIndex + 1
        val to = classToCombo.selectedIndex + 1
        return if (from == to) "class:$from" else "class:$from..$to"
    }

    private fun buildTagQuery(): String? {
        if (selectedTags.isEmpty()) return null
        if (selectedTags.size == 1) return "tag:${selectedTags.first()}"
        return "(${selectedTags.joinToString("|") { "tag:$it" }})"
    }
}
