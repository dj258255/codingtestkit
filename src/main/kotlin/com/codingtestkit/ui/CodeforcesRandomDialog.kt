package com.codingtestkit.ui

import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.AuthService
import com.codingtestkit.service.CodeforcesApi
import com.codingtestkit.service.I18n
import com.codingtestkit.service.TranslateService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
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

class CodeforcesRandomDialog(private val project: Project) : DialogWrapper(project) {

    // ─── 태그 다중선택 (칩 방식) ───

    private val tagEntries = listOf(
        "implementation" to I18n.t("구현", "Implementation"),
        "math" to I18n.t("수학", "Math"),
        "greedy" to I18n.t("그리디", "Greedy"),
        "dp" to I18n.t("다이나믹 프로그래밍", "DP"),
        "data structures" to I18n.t("자료 구조", "Data Structures"),
        "brute force" to I18n.t("브루트포스", "Brute Force"),
        "constructive algorithms" to I18n.t("구성적", "Constructive"),
        "graphs" to I18n.t("그래프", "Graphs"),
        "sortings" to I18n.t("정렬", "Sorting"),
        "binary search" to I18n.t("이분 탐색", "Binary Search"),
        "dfs and similar" to I18n.t("DFS 등", "DFS & Similar"),
        "trees" to I18n.t("트리", "Trees"),
        "strings" to I18n.t("문자열", "Strings"),
        "number theory" to I18n.t("정수론", "Number Theory"),
        "geometry" to I18n.t("기하학", "Geometry"),
        "combinatorics" to I18n.t("조합론", "Combinatorics"),
        "two pointers" to I18n.t("투 포인터", "Two Pointers"),
        "bitmasks" to I18n.t("비트마스크", "Bitmasks"),
        "dsu" to I18n.t("유니온 파인드", "DSU"),
        "shortest paths" to I18n.t("최단 경로", "Shortest Paths")
    )

    private val selectedTags = mutableSetOf<String>()
    private val dialogWidth = JBUI.scale(700)
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

    // ─── 필터 ───

    private val ratingMinField = JTextField("800").apply {
        font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(13))
        horizontalAlignment = JTextField.CENTER
        val dim = Dimension(JBUI.scale(72), JBUI.scale(28))
        preferredSize = dim; minimumSize = dim; maximumSize = dim
    }
    private val ratingMaxField = JTextField("1600").apply {
        font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(13))
        horizontalAlignment = JTextField.CENTER
        val dim = Dimension(JBUI.scale(72), JBUI.scale(28))
        preferredSize = dim; minimumSize = dim; maximumSize = dim
    }

    private val excludeObscureCheck = JCheckBox(
        I18n.t("듣보 문제 제외 (맞은 사람", "Exclude obscure (solved ≤")
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

    private val solvedFilterCombo = ComboBox(arrayOf(
        I18n.t("전체", "All"),
        I18n.t("내가 푼 문제 제외", "Exclude my solved"),
        I18n.t("내가 푼 문제에서만", "Only my solved")
    )).apply {
        preferredSize = Dimension(JBUI.scale(200), preferredSize.height)
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                border = JBUI.Borders.empty(4, 8)
                return c
            }
        }
        val loggedIn = AuthService.getInstance().getUsername(ProblemSource.CODEFORCES).isNotBlank()
        isEnabled = loggedIn
        if (!loggedIn) toolTipText = I18n.t("Codeforces 로그인이 필요합니다", "Codeforces login required")
    }

    private val countField = JTextField("5").apply {
        font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(13))
        horizontalAlignment = JTextField.CENTER
        val dim = Dimension(JBUI.scale(52), JBUI.scale(28))
        preferredSize = dim; minimumSize = dim; maximumSize = dim
    }
    private val searchButton = JButton(I18n.t("뽑기", "Pick"))

    // ─── 결과 테이블 ───

    private val checkedRows = mutableSetOf<Int>()
    private val tableModel = object : DefaultTableModel(
        arrayOf("", I18n.t("번호", "ID"), I18n.t("제목", "Title"), I18n.t("난이도", "Rating"), I18n.t("태그", "Tags"), I18n.t("맞은 사람", "Solved")), 0
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
    private val resultTable = JBTable(tableModel).apply {
        emptyText.text = I18n.t("조건을 선택하고 '뽑기'를 클릭하세요", "Select filters and click 'Pick'")
    }
    private val statusLabel = JLabel(I18n.t("조건을 선택하고 '뽑기'를 클릭하세요", "Select filters and click 'Pick'"))
    private var headerCheck = JCheckBox().apply { horizontalAlignment = SwingConstants.CENTER }

    private var results: List<CodeforcesApi.ProblemInfo> = emptyList()
    private var showingTranslated = false
    private var translatedTitles: Map<String, String> = emptyMap()
    private val translateButton = JButton("KO").apply {
        toolTipText = I18n.t("한국어로 번역", "Translate to Korean")
        preferredSize = Dimension(JBUI.scale(50), preferredSize.height)
        addActionListener { toggleTranslation() }
    }
    var selectedProblemIds: List<String> = emptyList()
        private set

    init {
        title = I18n.t("Codeforces 랜덤 문제 뽑기", "Codeforces Random Problem Picker")
        setOKButtonText(I18n.t("가져오기", "Fetch"))
        setCancelButtonText(I18n.t("닫기", "Close"))
        init()
        isOKActionEnabled = false
    }

    private var doubleClicked = false

    override fun doOKAction() {
        if (!doubleClicked) {
            selectedProblemIds = checkedRows.filter { it in results.indices }.map { results[it].id }
        }
        super.doOKAction()
    }

    private fun updateOKEnabled() {
        isOKActionEnabled = checkedRows.any { it in results.indices }
    }

    override fun createCenterPanel(): JComponent {
        val dialogH = JBUI.scale(450)
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

        // Row 1: Rating 범위
        val row1 = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40))
        }
        row1.add(createLabel("Rating"))
        row1.add(ratingMinField)
        row1.add(JLabel(" ~ ").apply { font = font.deriveFont(Font.BOLD) })
        row1.add(ratingMaxField)
        topPanel.add(row1)

        // Row 2: 태그 칩 (반응형 래핑)
        updateTagChips()
        tagChipPanel.border = JBUI.Borders.empty(2, 4)
        tagChipPanel.alignmentX = Component.LEFT_ALIGNMENT
        topPanel.add(tagChipPanel)

        // Row 3: 듣보 문제 제외
        val obscureRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40))
        }
        obscureRow.add(excludeObscureCheck)
        obscureRow.add(obscureThresholdField)
        obscureRow.add(obscureThresholdSuffix)
        excludeObscureCheck.addActionListener { obscureThresholdField.isEnabled = excludeObscureCheck.isSelected }
        topPanel.add(obscureRow)

        // Row 4: 풀이 필터
        val solvedRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40))
        }
        solvedRow.add(createLabel(I18n.t("풀이 필터", "Solved Filter")))
        solvedRow.add(solvedFilterCombo)
        topPanel.add(solvedRow)

        // Row 5: 개수 + 뽑기
        val countRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40))
        }
        countRow.add(createLabel(I18n.t("개수", "Count")))
        countRow.add(countField)
        countRow.add(searchButton)
        topPanel.add(countRow)

        panel.add(topPanel, BorderLayout.NORTH)

        // 결과 테이블
        resultTable.rowHeight = JBUI.scale(24)
        resultTable.tableHeader.preferredSize = Dimension(0, JBUI.scale(28))

        val checkRendererBox = JCheckBox().apply { horizontalAlignment = SwingConstants.CENTER }
        resultTable.columnModel.getColumn(0).cellRenderer = object : javax.swing.table.TableCellRenderer {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): Component {
                checkRendererBox.isSelected = value == true
                checkRendererBox.background = if (isSelected) table.selectionBackground else table.background
                checkRendererBox.isOpaque = true
                return checkRendererBox
            }
        }
        resultTable.columnModel.getColumn(0).apply { preferredWidth = JBUI.scale(30); maxWidth = JBUI.scale(30); minWidth = JBUI.scale(30) }
        resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(65)
        resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(230)
        resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(60)
        resultTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(180)
        resultTable.columnModel.getColumn(5).preferredWidth = JBUI.scale(65)

        // 헤더 전체 선택 체크박스
        resultTable.columnModel.getColumn(0).headerRenderer =
            javax.swing.table.TableCellRenderer { table, _, _, _, _, _ ->
                val defaultRenderer = table.tableHeader.defaultRenderer
                val headerComp = defaultRenderer.getTableCellRendererComponent(table, "", false, false, -1, 0) as JComponent
                headerCheck.background = headerComp.background
                headerCheck.border = headerComp.border
                headerCheck.isOpaque = true
                headerCheck
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

        // 하단: 상태 라벨 + 번역 버튼
        val bottomPanel = JPanel(BorderLayout()).apply {
            statusLabel.foreground = JBColor.GRAY
            statusLabel.font = statusLabel.font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
            add(statusLabel, BorderLayout.CENTER)
            add(translateButton, BorderLayout.EAST)
        }
        panel.add(bottomPanel, BorderLayout.SOUTH)

        // 이벤트
        addTagButton.addActionListener { showTagPopup() }
        searchButton.addActionListener { doRandom() }
        resultTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && resultTable.selectedRow >= 0) {
                    selectedProblemIds = listOf(results[resultTable.selectedRow].id)
                    doubleClicked = true
                    doOKAction()
                }
            }
        })

        return panel
    }

    // ─── 태그 팝업 (JBPopupFactory) ───

    private fun showTagPopup() {
        val checkboxes = mutableListOf<JCheckBox>()
        val listPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

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

    private fun createLabel(text: String): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
        }
    }

    // ─── 랜덤 뽑기 ───

    private fun doRandom() {
        val tags = selectedTags.toList()
        val rMin = ratingMinField.text.trim().toIntOrNull() ?: 800
        val rMax = ratingMaxField.text.trim().toIntOrNull() ?: 1600
        val count = try { countField.text.trim().toInt().coerceIn(1, 20) } catch (_: Exception) { 5 }
        val minSolved = if (excludeObscureCheck.isSelected) {
            try { obscureThresholdField.text.trim().toInt().coerceAtLeast(1) } catch (_: Exception) { 100 }
        } else 0
        val solvedMode = solvedFilterCombo.selectedIndex

        if (solvedMode > 0 && AuthService.getInstance().getUsername(ProblemSource.CODEFORCES).isBlank()) {
            statusLabel.text = I18n.t("Codeforces 로그인이 필요합니다.", "Codeforces login required.")
            statusLabel.foreground = JBColor.RED
            return
        }

        searchButton.isEnabled = false
        statusLabel.foreground = JBColor.GRAY
        statusLabel.text = I18n.t("문제를 가져오는 중...", "Fetching problems...")
        tableModel.rowCount = 0
        isOKActionEnabled = false

        Thread {
            try {
                // 풀이 필터용 solved ID 세트
                val solvedIds = if (solvedMode > 0) {
                    val handle = AuthService.getInstance().getUsername(ProblemSource.CODEFORCES)
                    CodeforcesApi.fetchSolvedIds(handle)
                } else emptySet()

                var candidates = CodeforcesApi.randomProblems(tags, rMin, rMax, count * 5, minSolved)

                if (solvedMode == 1) candidates = candidates.filter { it.id !in solvedIds }
                else if (solvedMode == 2) candidates = candidates.filter { it.id in solvedIds }

                results = candidates.take(count)
                SwingUtilities.invokeLater {
                    checkedRows.clear()
                    for ((idx, p) in results.withIndex()) {
                        checkedRows.add(idx)
                        tableModel.addRow(arrayOf(
                            true,
                            p.id, p.name, p.ratingDisplay,
                            p.tags.take(3).joinToString(", "),
                            p.solvedCount
                        ))
                    }
                    isOKActionEnabled = results.isNotEmpty()
                    headerCheck.isSelected = results.isNotEmpty()
                    statusLabel.text = if (results.isEmpty())
                        I18n.t("조건에 맞는 문제가 없습니다", "No problems match the criteria")
                    else I18n.t("${results.size}개 문제 (체크 후 '가져오기', 더블클릭=1개)",
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

    // ─── 번역 ───

    private val tagToKo = mapOf(
        "implementation" to "구현", "math" to "수학", "greedy" to "그리디",
        "dp" to "DP", "data structures" to "자료 구조", "brute force" to "브루트포스",
        "constructive algorithms" to "구성적", "graphs" to "그래프", "sortings" to "정렬",
        "binary search" to "이분 탐색", "dfs and similar" to "DFS 등", "trees" to "트리",
        "strings" to "문자열", "number theory" to "정수론", "geometry" to "기하학",
        "combinatorics" to "조합론", "two pointers" to "투 포인터", "bitmasks" to "비트마스크",
        "dsu" to "유니온 파인드", "shortest paths" to "최단 경로"
    )

    private fun toggleTranslation() {
        if (results.isEmpty()) return
        showingTranslated = !showingTranslated
        if (showingTranslated && translatedTitles.isEmpty()) {
            translateButton.isEnabled = false
            translateButton.text = "..."
            Thread {
                try {
                    val batch = results.map { it.name }.joinToString("\n")
                    val translated = TranslateService.translate(batch, "en", "ko")
                    val translatedList = translated.split("\n")
                    val map = mutableMapOf<String, String>()
                    for ((idx, p) in results.withIndex()) {
                        if (idx < translatedList.size) map[p.id] = translatedList[idx].trim()
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
        translateButton.toolTipText = if (showingTranslated)
            I18n.t("영어로 보기", "Show in English") else I18n.t("한국어로 번역", "Translate to Korean")
        for ((idx, p) in results.withIndex()) {
            if (idx < tableModel.rowCount) {
                val title = if (showingTranslated) translatedTitles[p.id] ?: p.name else p.name
                tableModel.setValueAt(title, idx, 2)
                val tags = if (showingTranslated)
                    p.tags.take(3).joinToString(", ") { tagToKo[it] ?: it }
                else p.tags.take(3).joinToString(", ")
                tableModel.setValueAt(tags, idx, 4)
            }
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = searchButton
}
