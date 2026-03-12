package com.codingtestkit.ui

import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.AuthService
import com.codingtestkit.service.I18n
import com.codingtestkit.service.SwexpertApi
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
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

class SwexpertSearchDialog(private val project: Project) : DialogWrapper(project) {

    private val searchField = JTextField().apply {
        putClientProperty("JTextField.placeholderText", I18n.t("문제 번호 또는 키워드", "Problem number or keyword"))
    }

    // ─── 난이도 필터 (칩 방식) ───

    private val levelEntries = SwexpertApi.difficultyLevels
    private val selectedLevels = mutableSetOf<Int>()
    private val dialogWidth = JBUI.scale(720)
    private val levelChipPanel = object : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(3))) {
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
    private val addLevelButton = JButton("+").apply {
        font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
        toolTipText = I18n.t("클릭하여 난이도 선택", "Click to select difficulty")
        putClientProperty("JButton.buttonType", "roundRect")
        preferredSize = Dimension(JBUI.scale(36), JBUI.scale(28))
    }

    // ─── 기타 필터 ───

    private val languageCombo = ComboBox(
        SwexpertApi.supportedLanguages.map { it.second }.toTypedArray()
    ).apply {
        preferredSize = Dimension(JBUI.scale(120), preferredSize.height)
        renderer = comboRenderer()
    }

    private val sortCombo = ComboBox(
        SwexpertApi.sortOptions.map { it.second }.toTypedArray()
    ).apply {
        preferredSize = Dimension(JBUI.scale(140), preferredSize.height)
        renderer = comboRenderer()
    }

    private val solvedFilterCombo = ComboBox(arrayOf(
        I18n.t("전체", "All"),
        I18n.t("통과한 문제만", "Solved Only")
    )).apply {
        preferredSize = Dimension(JBUI.scale(150), preferredSize.height)
        renderer = comboRenderer()
        val loggedIn = AuthService.getInstance().isLoggedIn(ProblemSource.SWEA)
        isEnabled = loggedIn
        if (!loggedIn) {
            toolTipText = I18n.t("SWEA 로그인이 필요합니다", "SWEA login required")
            val combo = this
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder(
                            I18n.t("풀이 필터를 사용하려면<br>SWEA에 로그인하세요", "Login to SWEA<br>to use solved filter"),
                            MessageType.WARNING, null
                        )
                        .setFadeoutTime(3000)
                        .createBalloon()
                        .show(RelativePoint.getCenterOf(combo), Balloon.Position.above)
                }
            })
        }
    }

    private val minParticipantsField = JTextField("0").apply {
        font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(13))
        horizontalAlignment = JTextField.CENTER
        val dim = Dimension(JBUI.scale(60), JBUI.scale(28))
        preferredSize = dim; minimumSize = dim; maximumSize = dim
    }

    private val searchButton = JButton(I18n.t("검색", "Search"))

    // ─── 결과 테이블 ───

    private val tableModel = object : DefaultTableModel(
        arrayOf(
            I18n.t("번호", "No."),
            I18n.t("제목", "Title"),
            I18n.t("난이도", "Diff"),
            I18n.t("참여자", "Participants"),
            I18n.t("정답률", "Rate"),
            I18n.t("추천", "Rec")
        ), 0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val resultTable = JBTable(tableModel).apply {
        emptyText.text = I18n.t("검색어를 입력하거나 필터를 선택하세요", "Enter a keyword or select filters")
    }
    private val statusLabel = JLabel(I18n.t("문제 제목이나 필터를 선택하세요", "Enter a keyword or select filters"))

    private var results: List<SwexpertApi.ProblemInfo> = emptyList()
    private var currentPage = 1
    private var totalPages = 1
    private val prevPageButton = JButton("<").apply {
        preferredSize = Dimension(JBUI.scale(40), preferredSize.height)
        isEnabled = false
    }
    private val nextPageButton = JButton(">").apply {
        preferredSize = Dimension(JBUI.scale(40), preferredSize.height)
        isEnabled = false
    }
    private val pageLabel = JLabel("1 / 1")

    var selectedProblemId: String? = null
        private set

    init {
        title = I18n.t("SWEA 문제 검색", "SWEA Problem Search")
        setOKButtonText(I18n.t("가져오기", "Fetch"))
        setCancelButtonText(I18n.t("닫기", "Close"))
        init()
        isOKActionEnabled = false
    }

    override fun createCenterPanel(): JComponent {
        val dialogH = JBUI.scale(520)
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

        // Row 2: 난이도 칩
        updateLevelChips()
        levelChipPanel.border = JBUI.Borders.empty(2, 4)
        levelChipPanel.alignmentX = Component.LEFT_ALIGNMENT
        topPanel.add(levelChipPanel)

        // Row 3: 언어 + 정렬
        val row3 = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40))
        }
        row3.add(createLabel(I18n.t("언어", "Language")))
        row3.add(languageCombo)
        row3.add(Box.createHorizontalStrut(JBUI.scale(12)))
        row3.add(createLabel(I18n.t("정렬", "Sort")))
        row3.add(sortCombo)
        row3.add(Box.createHorizontalStrut(JBUI.scale(12)))
        row3.add(createLabel(I18n.t("풀이", "Solved")))
        row3.add(solvedFilterCombo)
        topPanel.add(row3)

        // Row 4: 최소 참여자
        val row4 = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40))
        }
        row4.add(createLabel(I18n.t("최소 참여자", "Min Participants")))
        row4.add(minParticipantsField)
        topPanel.add(row4)

        panel.add(topPanel, BorderLayout.NORTH)

        // 결과 테이블
        resultTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultTable.rowHeight = JBUI.scale(24)
        resultTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(55)
        resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(280)
        resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(45)
        resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(70)
        resultTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(55)
        resultTable.columnModel.getColumn(5).preferredWidth = JBUI.scale(40)

        panel.add(JScrollPane(resultTable), BorderLayout.CENTER)

        // 하단: 상태 라벨 + 페이지네이션
        val bottomPanel = JPanel(BorderLayout()).apply {
            statusLabel.foreground = JBColor.GRAY
            statusLabel.font = statusLabel.font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
            add(statusLabel, BorderLayout.CENTER)

            val pagePanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0))
            pagePanel.add(prevPageButton)
            pagePanel.add(pageLabel)
            pagePanel.add(nextPageButton)
            add(pagePanel, BorderLayout.EAST)
        }
        panel.add(bottomPanel, BorderLayout.SOUTH)

        // 이벤트
        addLevelButton.addActionListener { showLevelPopup() }
        searchButton.addActionListener { currentPage = 1; doSearch() }
        searchField.addActionListener { currentPage = 1; doSearch() }
        prevPageButton.addActionListener { if (currentPage > 1) { currentPage--; doSearch() } }
        nextPageButton.addActionListener { if (currentPage < totalPages) { currentPage++; doSearch() } }

        resultTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = resultTable.selectedRow
                isOKActionEnabled = row in results.indices
                if (isOKActionEnabled) selectedProblemId = results[row].contestProbId
            }
        }
        resultTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && resultTable.selectedRow >= 0) {
                    selectedProblemId = results[resultTable.selectedRow].contestProbId
                    doOKAction()
                }
            }
        })

        return panel
    }

    // ─── 난이도 팝업 ───

    private fun showLevelPopup() {
        val checkboxes = mutableListOf<JCheckBox>()
        val listPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

        val selectAllCheck = JCheckBox(I18n.t("전체 선택 / 해제", "Select / Deselect All")).apply {
            isSelected = selectedLevels.size == levelEntries.size
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
            border = JBUI.Borders.empty(4, 6)
            isOpaque = false
        }
        selectAllCheck.addActionListener {
            if (selectAllCheck.isSelected) {
                selectedLevels.addAll(levelEntries.map { it.first })
                checkboxes.forEach { it.isSelected = true }
            } else {
                selectedLevels.clear()
                checkboxes.forEach { it.isSelected = false }
            }
            updateLevelChips()
        }
        listPanel.add(selectAllCheck)
        listPanel.add(JSeparator())

        for ((key, display) in levelEntries) {
            val cb = JCheckBox(display).apply {
                isSelected = key in selectedLevels
                font = font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
                border = JBUI.Borders.empty(3, 6)
                isOpaque = false
            }
            cb.addActionListener {
                if (cb.isSelected) selectedLevels.add(key) else selectedLevels.remove(key)
                selectAllCheck.isSelected = selectedLevels.size == levelEntries.size
                updateLevelChips()
            }
            checkboxes.add(cb)
            listPanel.add(cb)
        }

        com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
            .createComponentPopupBuilder(listPanel, null)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .createPopup()
            .showUnderneathOf(addLevelButton)
    }

    private fun updateLevelChips() {
        levelChipPanel.removeAll()
        levelChipPanel.add(createLabel(I18n.t("난이도", "Difficulty")))
        if (selectedLevels.isEmpty()) {
            levelChipPanel.add(JLabel(I18n.t("전체", "All")).apply {
                font = font.deriveFont(JBUI.scaleFontSize(13f).toFloat())
                foreground = JBColor.GRAY
            })
        } else {
            for (key in selectedLevels.sorted()) {
                val display = levelEntries.firstOrNull { it.first == key }?.second ?: "D$key"
                levelChipPanel.add(createChip(display) { selectedLevels.remove(key); updateLevelChips() })
            }
        }
        levelChipPanel.add(addLevelButton)
        preserveWindowSize(levelChipPanel)
    }

    // ─── 공통 유틸 ───

    private fun createChip(display: String, onRemove: () -> Unit): JPanel {
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
                override fun mouseClicked(e: MouseEvent) { onRemove() }
                override fun mouseEntered(e: MouseEvent) { removeBtn.foreground = JBColor.RED }
                override fun mouseExited(e: MouseEvent) { removeBtn.foreground = JBColor.GRAY }
            })
            add(removeBtn)
        }
    }

    private fun createLabel(text: String): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
        }
    }

    private fun comboRenderer(): ListCellRenderer<Any?> {
        return object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                border = JBUI.Borders.empty(4, 8)
                return this
            }
        }
    }

    private fun preserveWindowSize(comp: JComponent) {
        val window = SwingUtilities.getWindowAncestor(comp)
        val prevSize = window?.size
        comp.revalidate()
        comp.repaint()
        if (prevSize != null) window.size = prevSize
    }

    // ─── 검색 ───

    private fun doSearch() {
        if (!AuthService.getInstance().isLoggedIn(ProblemSource.SWEA)) {
            statusLabel.foreground = JBColor.RED
            statusLabel.text = I18n.t(
                "SWEA 로그인이 필요합니다. 메인 화면에서 먼저 로그인하세요.",
                "SWEA login required. Please log in from the main panel first."
            )
            return
        }

        val keyword = searchField.text.trim()
        val levels = selectedLevels.toList()
        val language = SwexpertApi.supportedLanguages[languageCombo.selectedIndex].first
        val orderBy = SwexpertApi.sortOptions[sortCombo.selectedIndex].first
        val minParticipants = try { minParticipantsField.text.trim().toInt().coerceAtLeast(0) } catch (_: Exception) { 0 }
        val passFilterYn = solvedFilterCombo.selectedIndex == 1
        val cookies = AuthService.getInstance().getCookies(ProblemSource.SWEA)

        searchButton.isEnabled = false
        statusLabel.text = I18n.t("검색 중...", "Searching...")
        tableModel.rowCount = 0
        isOKActionEnabled = false

        Thread {
            try {
                val result = SwexpertApi.searchProblems(
                    keyword = keyword,
                    levels = levels,
                    language = language,
                    orderBy = orderBy,
                    passFilterYn = passFilterYn,
                    page = currentPage,
                    pageSize = 30,
                    cookies = cookies
                )
                results = if (minParticipants > 0) {
                    result.problems.filter { it.participants >= minParticipants }
                } else {
                    result.problems
                }
                totalPages = result.totalPages

                SwingUtilities.invokeLater {
                    for (p in results) {
                        tableModel.addRow(arrayOf(
                            p.number, p.title, p.difficultyDisplay,
                            p.participantsDisplay, p.solveRate,
                            p.recommendations.toString()
                        ))
                    }
                    prevPageButton.isEnabled = currentPage > 1
                    nextPageButton.isEnabled = currentPage < totalPages
                    pageLabel.text = "$currentPage / $totalPages"
                    statusLabel.text = if (results.isEmpty())
                        I18n.t("검색 결과가 없습니다", "No results found")
                    else I18n.t(
                        "${results.size}개 문제 (더블클릭으로 가져오기)",
                        "${results.size} problems (double-click to fetch)"
                    )
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
