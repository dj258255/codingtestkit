package com.codingtestkit.ui

import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.AuthService
import com.codingtestkit.service.I18n
import com.codingtestkit.service.ProgrammersApi
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
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

class ProgrammersRandomDialog(private val project: Project) : DialogWrapper(project) {

    // 다이얼로그 열릴 때 백그라운드에서 기출문제 목록을 동적으로 가져옴
    @Volatile private var examCollections: List<Pair<String, String>> = emptyList()

    // ─── 레벨 필터 (칩 방식) ───

    private val levelEntries = (0..5).map { it to "Lv. $it" }
    private val selectedLevels = mutableSetOf<Int>()
    private val dialogWidth = JBUI.scale(700)
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
        toolTipText = I18n.t("클릭하여 난이도 선택", "Click to select levels")
        putClientProperty("JButton.buttonType", "roundRect")
        preferredSize = Dimension(JBUI.scale(36), JBUI.scale(28))
    }

    // ─── 기출문제 모음 필터 (칩 방식) ───

    private val selectedPartIds = mutableSetOf<String>()
    private val partChipPanel = object : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(3))) {
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
    private val addPartButton = JButton("+").apply {
        font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
        toolTipText = I18n.t("클릭하여 기출문제 모음 선택", "Click to select exam collections")
        putClientProperty("JButton.buttonType", "roundRect")
        preferredSize = Dimension(JBUI.scale(36), JBUI.scale(28))
    }

    // ─── 기타 필터 ───

    private val languageCombo = ComboBox(
        arrayOf(I18n.t("전체", "All")) + ProgrammersApi.supportedLanguages.map { it.second }.toTypedArray()
    ).apply {
        preferredSize = Dimension(JBUI.scale(120), preferredSize.height)
        renderer = comboRenderer()
    }

    private val statusCombo = ComboBox(arrayOf(
        I18n.t("전체", "All"),
        I18n.t("안 푼 문제", "Unsolved"),
        I18n.t("풀고 있는 문제", "Solving"),
        I18n.t("푼 문제 (스스로 해결)", "Solved"),
        I18n.t("푼 문제 (다른 풀이 확인)", "Solved (unlocked)")
    )).apply {
        preferredSize = Dimension(JBUI.scale(200), preferredSize.height)
        renderer = comboRenderer()
        val loggedIn = AuthService.getInstance().isLoggedIn(ProblemSource.PROGRAMMERS)
        isEnabled = loggedIn
        if (!loggedIn) {
            toolTipText = I18n.t("프로그래머스 로그인이 필요합니다", "Programmers login required")
            val combo = this
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder(
                            I18n.t("상태 필터를 사용하려면<br>프로그래머스에 로그인하세요", "Login to Programmers<br>to use status filter"),
                            MessageType.WARNING, null
                        )
                        .setFadeoutTime(3000)
                        .createBalloon()
                        .show(RelativePoint.getCenterOf(combo), Balloon.Position.above)
                }
            })
        }
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
        arrayOf(
            "",
            I18n.t("번호", "ID"),
            I18n.t("제목", "Title"),
            I18n.t("분류", "Category"),
            I18n.t("난이도", "Level"),
            I18n.t("완료", "Finished"),
            I18n.t("정답률", "Rate")
        ), 0
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

    private var results: List<ProgrammersApi.ProblemInfo> = emptyList()
    private var showingTranslated = false
    private var translatedTitles: Map<String, String> = emptyMap()
    private val translateButton = JButton("EN").apply {
        toolTipText = I18n.t("영어로 번역", "Translate to English")
        preferredSize = Dimension(JBUI.scale(50), preferredSize.height)
        addActionListener { toggleTranslation() }
    }
    var selectedProblemIds: List<String> = emptyList()
        private set

    init {
        title = I18n.t("프로그래머스 랜덤 문제 뽑기", "Programmers Random Problem Picker")
        setOKButtonText(I18n.t("가져오기", "Fetch"))
        setCancelButtonText(I18n.t("닫기", "Close"))
        init()
        isOKActionEnabled = false

        // 백그라운드에서 기출문제 목록 로드
        Thread {
            examCollections = ProgrammersApi.fetchExamCollections()
        }.start()
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

        // Row 1: 레벨 칩
        updateLevelChips()
        levelChipPanel.border = JBUI.Borders.empty(2, 4)
        levelChipPanel.alignmentX = Component.LEFT_ALIGNMENT
        topPanel.add(levelChipPanel)

        // Row 2: 기출문제 모음 칩
        updatePartChips()
        partChipPanel.border = JBUI.Borders.empty(2, 4)
        partChipPanel.alignmentX = Component.LEFT_ALIGNMENT
        topPanel.add(partChipPanel)

        // Row 3: 언어 + 상태
        val row3 = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40))
        }
        row3.add(createLabel(I18n.t("언어", "Language")))
        row3.add(languageCombo)
        row3.add(Box.createHorizontalStrut(JBUI.scale(12)))
        row3.add(createLabel(I18n.t("상태", "Status")))
        row3.add(statusCombo)
        topPanel.add(row3)

        // Row 4: 개수 + 뽑기
        val row4 = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(dialogWidth, JBUI.scale(40))
        }
        row4.add(createLabel(I18n.t("개수", "Count")))
        row4.add(countField)
        row4.add(searchButton)
        topPanel.add(row4)

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
        resultTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(50)
        resultTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(220)
        resultTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(110)
        resultTable.columnModel.getColumn(4).preferredWidth = JBUI.scale(45)
        resultTable.columnModel.getColumn(5).preferredWidth = JBUI.scale(65)
        resultTable.columnModel.getColumn(6).preferredWidth = JBUI.scale(40)

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

        // 하단: 상태 라벨 + 번역
        val bottomPanel = JPanel(BorderLayout()).apply {
            statusLabel.foreground = JBColor.GRAY
            statusLabel.font = statusLabel.font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
            add(translateButton, BorderLayout.EAST)
            add(statusLabel, BorderLayout.CENTER)
        }
        panel.add(bottomPanel, BorderLayout.SOUTH)

        // 이벤트
        addLevelButton.addActionListener { showLevelPopup() }
        addPartButton.addActionListener { showPartPopup() }
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

    // ─── 레벨 팝업 ───

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

        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(listPanel, null)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .createPopup()
            .showUnderneathOf(addLevelButton)
    }

    private fun updateLevelChips() {
        levelChipPanel.removeAll()
        levelChipPanel.add(createLabel(I18n.t("난이도", "Level")))
        if (selectedLevels.isEmpty()) {
            levelChipPanel.add(JLabel(I18n.t("전체", "All")).apply {
                font = font.deriveFont(JBUI.scaleFontSize(13f).toFloat())
                foreground = JBColor.GRAY
            })
        } else {
            for (key in selectedLevels.sorted()) {
                val display = levelEntries.firstOrNull { it.first == key }?.second ?: "Lv. $key"
                levelChipPanel.add(createChip(display) { selectedLevels.remove(key); updateLevelChips() })
            }
        }
        levelChipPanel.add(addLevelButton)
        preserveWindowSize(levelChipPanel)
    }

    // ─── 기출문제 팝업 ───

    private fun showPartPopup() {
        val checkboxes = mutableListOf<JCheckBox>()
        val listPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

        val selectAllCheck = JCheckBox(I18n.t("전체 선택 / 해제", "Select / Deselect All")).apply {
            isSelected = selectedPartIds.size == examCollections.size
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
            border = JBUI.Borders.empty(4, 6)
            isOpaque = false
        }
        selectAllCheck.addActionListener {
            if (selectAllCheck.isSelected) {
                selectedPartIds.addAll(examCollections.map { it.first })
                checkboxes.forEach { it.isSelected = true }
            } else {
                selectedPartIds.clear()
                checkboxes.forEach { it.isSelected = false }
            }
            updatePartChips()
        }
        listPanel.add(selectAllCheck)
        listPanel.add(JSeparator())

        for ((key, display) in examCollections) {
            val cb = JCheckBox(display).apply {
                isSelected = key in selectedPartIds
                font = font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
                border = JBUI.Borders.empty(3, 6)
                isOpaque = false
            }
            cb.addActionListener {
                if (cb.isSelected) selectedPartIds.add(key) else selectedPartIds.remove(key)
                selectAllCheck.isSelected = selectedPartIds.size == examCollections.size
                updatePartChips()
            }
            checkboxes.add(cb)
            listPanel.add(cb)
        }

        val scrollPane = JScrollPane(listPanel).apply {
            preferredSize = Dimension(JBUI.scale(300), JBUI.scale(360))
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
            .showUnderneathOf(addPartButton)
    }

    private fun updatePartChips() {
        partChipPanel.removeAll()
        partChipPanel.add(createLabel(I18n.t("기출문제", "Exams")))
        if (selectedPartIds.isEmpty()) {
            partChipPanel.add(JLabel(I18n.t("전체", "All")).apply {
                font = font.deriveFont(JBUI.scaleFontSize(13f).toFloat())
                foreground = JBColor.GRAY
            })
        } else {
            for (key in selectedPartIds.toList()) {
                val display = examCollections.firstOrNull { it.first == key }?.second ?: key
                val shortDisplay = if (display.length > 20) display.take(18) + "…" else display
                partChipPanel.add(createChip(shortDisplay) { selectedPartIds.remove(key); updatePartChips() })
            }
        }
        partChipPanel.add(addPartButton)
        preserveWindowSize(partChipPanel)
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

    // ─── 랜덤 뽑기 ───

    private fun doRandom() {
        val levels = selectedLevels.toList()
        val languages = if (languageCombo.selectedIndex > 0) {
            listOf(ProgrammersApi.supportedLanguages[languageCombo.selectedIndex - 1].first)
        } else {
            ProgrammersApi.supportedLanguages.map { it.first }
        }
        val statuses = when (statusCombo.selectedIndex) {
            1 -> listOf("unsolved")
            2 -> listOf("solving")
            3 -> listOf("solved")
            4 -> listOf("solved_with_unlock")
            else -> emptyList()
        }
        val partIds = selectedPartIds.toList()
        val count = try { countField.text.trim().toInt().coerceIn(1, 20) } catch (_: Exception) { 5 }
        val cookies = AuthService.getInstance().getCookies(ProblemSource.PROGRAMMERS)

        searchButton.isEnabled = false
        statusLabel.foreground = JBColor.GRAY
        statusLabel.text = I18n.t("문제를 가져오는 중...", "Fetching problems...")
        tableModel.rowCount = 0
        isOKActionEnabled = false
        showingTranslated = false
        translatedTitles = emptyMap()
        translateButton.text = "EN"

        Thread {
            try {
                results = ProgrammersApi.randomProblems(
                    levels = levels,
                    languages = languages,
                    statuses = statuses,
                    partIds = partIds,
                    count = count,
                    cookies = cookies
                )
                SwingUtilities.invokeLater {
                    checkedRows.clear()
                    for ((idx, p) in results.withIndex()) {
                        checkedRows.add(idx)
                        tableModel.addRow(arrayOf(
                            true,
                            p.id, p.title, p.partTitle, p.levelDisplay,
                            p.finishedCountDisplay, "${p.acceptanceRate}%"
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
                    val map = mutableMapOf<String, String>()
                    for ((idx, p) in results.withIndex()) {
                        if (idx < translatedList.size) map[p.id] = translatedList[idx].trim()
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
                val title = if (showingTranslated) translatedTitles[p.id] ?: p.title else p.title
                tableModel.setValueAt(title, idx, 2)
            }
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = searchButton
}
