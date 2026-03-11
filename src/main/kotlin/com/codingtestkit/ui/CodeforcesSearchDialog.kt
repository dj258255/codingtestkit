package com.codingtestkit.ui

import com.codingtestkit.service.CodeforcesApi
import com.codingtestkit.service.I18n
import com.codingtestkit.service.TranslateService
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

class CodeforcesSearchDialog(private val project: Project) : DialogWrapper(project) {

    private val searchField = JTextField().apply {
        putClientProperty("JTextField.placeholderText", I18n.t("문제 이름 또는 번호 입력", "Enter problem name or number"))
    }

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
    private val dialogWidth = JBUI.scale(720)
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

    private val ratingMinField = JTextField("0").apply {
        font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(13))
        horizontalAlignment = JTextField.CENTER
        val dim = Dimension(JBUI.scale(72), JBUI.scale(28))
        preferredSize = dim; minimumSize = dim; maximumSize = dim
    }
    private val ratingMaxField = JTextField("3500").apply {
        font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(13))
        horizontalAlignment = JTextField.CENTER
        val dim = Dimension(JBUI.scale(72), JBUI.scale(28))
        preferredSize = dim; minimumSize = dim; maximumSize = dim
    }
    private val minSolvedField = JTextField("0", 5).apply {
        font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(13))
        toolTipText = I18n.t("최소 맞은 사람 수 (듣보문제 제외용)", "Min solved count (filter obscure problems)")
    }
    private val searchButton = JButton(I18n.t("검색", "Search"))

    private val tableModel = object : DefaultTableModel(
        arrayOf(I18n.t("번호", "ID"), I18n.t("제목", "Title"), I18n.t("난이도", "Rating"), I18n.t("태그", "Tags"), I18n.t("맞은 사람", "Solved")), 0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val resultTable = JBTable(tableModel).apply {
        emptyText.text = I18n.t("표시할 항목이 없습니다", "No items to display")
    }
    private val statusLabel = JLabel(I18n.t("문제 이름이나 번호를 입력하세요", "Enter a problem name or number"))

    private var results: List<CodeforcesApi.ProblemInfo> = emptyList()
    private var showingTranslated = false
    private var translatedTitles: Map<String, String> = emptyMap()
    private val translateButton = JButton("KO").apply {
        toolTipText = I18n.t("한국어로 번역", "Translate to Korean")
        preferredSize = Dimension(JBUI.scale(50), preferredSize.height)
        addActionListener { toggleTranslation() }
    }
    var selectedProblemId: String? = null
        private set

    init {
        title = I18n.t("Codeforces 문제 검색", "Codeforces Problem Search")
        setOKButtonText(I18n.t("가져오기", "Fetch"))
        setCancelButtonText(I18n.t("닫기", "Close"))
        init()
        isOKActionEnabled = false
    }

    override fun createCenterPanel(): JComponent {
        val dialogH = JBUI.scale(480)
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

        // Row 1: 검색어
        val row1 = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(dialogWidth, JBUI.scale(36))
        }
        row1.add(searchField, BorderLayout.CENTER)
        row1.add(searchButton, BorderLayout.EAST)
        topPanel.add(row1)
        topPanel.add(Box.createVerticalStrut(JBUI.scale(2)))

        // Row 2: 태그 칩
        updateTagChips()
        tagChipPanel.border = JBUI.Borders.empty(2, 4)
        tagChipPanel.alignmentX = Component.LEFT_ALIGNMENT
        topPanel.add(tagChipPanel)

        // Row 3: Rating + 최소 풀이
        val row3 = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40))
        }
        row3.add(createLabel("Rating"))
        row3.add(ratingMinField)
        row3.add(JLabel(" ~ ").apply { font = font.deriveFont(Font.BOLD) })
        row3.add(ratingMaxField)
        row3.add(Box.createHorizontalStrut(JBUI.scale(12)))
        row3.add(createLabel(I18n.t("최소 풀이", "Min Solved")))
        row3.add(minSolvedField)
        topPanel.add(row3)

        panel.add(topPanel, BorderLayout.NORTH)

        resultTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultTable.rowHeight = JBUI.scale(24)
        resultTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(70)
        resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(260)
        resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(60)
        resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(200)
        resultTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(70)

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
        searchButton.addActionListener { doSearch() }
        searchField.addActionListener { doSearch() }

        resultTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = resultTable.selectedRow
                isOKActionEnabled = row in results.indices
                if (isOKActionEnabled) selectedProblemId = results[row].id
            }
        }
        resultTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && resultTable.selectedRow >= 0) {
                    selectedProblemId = results[resultTable.selectedRow].id
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

    // ─── 검색 ───

    private fun doSearch() {
        val query = searchField.text.trim()
        val tags = selectedTags.toList()
        val rMin = ratingMinField.text.trim().toIntOrNull() ?: 0
        val rMax = ratingMaxField.text.trim().toIntOrNull() ?: 3500
        val minSolved = minSolvedField.text.trim().toIntOrNull() ?: 0

        searchButton.isEnabled = false
        statusLabel.text = I18n.t("검색 중...", "Searching...")
        tableModel.rowCount = 0
        isOKActionEnabled = false

        Thread {
            try {
                results = CodeforcesApi.searchProblems(query, tags, rMin, rMax, minSolved = minSolved)
                SwingUtilities.invokeLater {
                    for (p in results) {
                        tableModel.addRow(arrayOf(
                            p.id, p.name, p.ratingDisplay,
                            p.tags.take(3).joinToString(", "),
                            p.solvedCount
                        ))
                    }
                    statusLabel.text = if (results.isEmpty())
                        I18n.t("검색 결과가 없습니다", "No results found")
                    else I18n.t("${results.size}개 문제 (더블클릭으로 가져오기)",
                        "${results.size} problems (double-click to fetch)")
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
                tableModel.setValueAt(title, idx, 1)
                val tags = if (showingTranslated)
                    p.tags.take(3).joinToString(", ") { tagToKo[it] ?: it }
                else p.tags.take(3).joinToString(", ")
                tableModel.setValueAt(tags, idx, 3)
            }
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = searchField
}
