package com.codingtestkit.ui

import com.codingtestkit.service.I18n
import com.codingtestkit.model.Language
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import com.codingtestkit.service.CodeRunner
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class TestPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val languageCombo = ComboBox(Language.entries.map { it.displayName }.toTypedArray())
    private val runButton = JButton(I18n.t("전체 실행", "Run All"), AllIcons.Actions.Execute).apply {
        toolTipText = I18n.t("모든 테스트 케이스를 실행합니다", "Run all test cases")
    }
    private val addButton = JButton(AllIcons.General.Add).apply {
        toolTipText = I18n.t("테스트 케이스 추가", "Add test case")
        preferredSize = Dimension(JBUI.scale(28), JBUI.scale(28))
    }
    private val statusLabel = JLabel("").apply {
        border = JBUI.Borders.empty(0, 4)
    }
    private val infoLabel = JLabel("").apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
        border = JBUI.Borders.empty(0, 4)
    }

    // 아코디언 리스트
    private val cardListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(4)
    }
    private val cards = mutableListOf<TestCaseCard>()

    private var testCases = mutableListOf<TestCase>()
    private var problemSource = ProblemSource.BAEKJOON
    private var parameterNames = listOf<String>()

    init {
        border = JBUI.Borders.empty()

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6, 8, 4, 8)
        }

        // Row 1: 언어 + 실행 버튼 + 추가/삭제
        val buttonRow = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(32))
        }

        val leftControls = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        leftControls.add(JLabel(I18n.t("언어:", "Lang:")).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
            foreground = JBColor.GRAY
        })
        leftControls.add(languageCombo)
        leftControls.add(Box.createHorizontalStrut(JBUI.scale(4)))
        leftControls.add(runButton)

        val rightControls = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0))
        rightControls.add(addButton)

        buttonRow.add(leftControls, BorderLayout.WEST)
        buttonRow.add(rightControls, BorderLayout.EAST)
        topPanel.add(buttonRow)

        // Row 2: 정보 + 상태
        val infoRow = JPanel(BorderLayout()).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(20))
        }
        infoRow.add(infoLabel, BorderLayout.WEST)
        infoRow.add(statusLabel, BorderLayout.EAST)
        topPanel.add(infoRow)

        add(topPanel, BorderLayout.NORTH)

        // 중앙: 아코디언 카드 리스트
        val scrollPane = JBScrollPane(cardListPanel).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }
        add(scrollPane, BorderLayout.CENTER)

        // 이벤트
        runButton.addActionListener { runAllTests() }
        addButton.addActionListener { addTestCase() }
    }

    fun setTestCases(cases: List<TestCase>) {
        testCases = cases.toMutableList()
        rebuildCards()
    }

    fun setProblemSource(source: ProblemSource) {
        problemSource = source
    }

    fun setParameterNames(names: List<String>) {
        parameterNames = names
        updateInfoLabel()
    }

    private fun updateInfoLabel() {
        if (problemSource == ProblemSource.PROGRAMMERS || problemSource == ProblemSource.LEETCODE) {
            if (parameterNames.isNotEmpty()) {
                infoLabel.text = I18n.t("파라미터", "Params") + ": ${parameterNames.joinToString(", ")}"
                infoLabel.icon = AllIcons.General.Information
            } else {
                infoLabel.text = I18n.t("+ 버튼으로 추가하세요", "Click + to add")
                infoLabel.icon = AllIcons.General.Information
            }
        } else {
            if (testCases.isNotEmpty()) {
                infoLabel.text = I18n.t("${testCases.size}개 테스트 케이스", "${testCases.size} test cases")
                infoLabel.icon = AllIcons.General.InspectionsOK
            } else {
                infoLabel.text = I18n.t("문제를 먼저 가져오세요", "Fetch a problem first")
                infoLabel.icon = null
            }
        }
    }

    private fun rebuildCards() {
        cardListPanel.removeAll()
        cards.clear()
        for ((i, tc) in testCases.withIndex()) {
            val card = TestCaseCard(i + 1, tc)
            cards.add(card)
            cardListPanel.add(card)
            cardListPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
        }
        // 하단 여백 없이 카드만 표시 (스크롤 영역이 내용 크기에 맞춰짐)
        updateInfoLabel()
        cardListPanel.revalidate()
        cardListPanel.repaint()
    }

    private fun addTestCase() {
        val tc = TestCase(input = "", expectedOutput = "")
        testCases.add(tc)
        rebuildCards()
        // 새로 추가된 카드 펼치기
        cards.lastOrNull()?.expand()
    }

    private fun removeTestCaseAt(index: Int) {
        if (index < 0 || index >= testCases.size) return
        val result = javax.swing.JOptionPane.showConfirmDialog(
            this,
            I18n.t("테스트 케이스 #${index + 1}을 삭제하시겠습니까?", "Delete test case #${index + 1}?"),
            I18n.t("삭제 확인", "Confirm Delete"),
            javax.swing.JOptionPane.YES_NO_OPTION
        )
        if (result == javax.swing.JOptionPane.YES_OPTION) {
            syncCardsToTestCases()
            testCases.removeAt(index)
            rebuildCards()
        }
    }

    private fun getSelectedLanguage(): Language {
        return Language.entries[languageCombo.selectedIndex]
    }

    private fun getCurrentCode(): String {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        return editor?.document?.text ?: ""
    }

    private fun runAllTests() {
        val code = getCurrentCode()
        if (code.isBlank()) {
            statusLabel.text = I18n.t("코드 없음", "No code")
            statusLabel.icon = AllIcons.General.Warning
            statusLabel.foreground = JBColor(Color(200, 120, 0), Color(230, 160, 50))
            return
        }

        // 카드에서 수정된 값 반영
        syncCardsToTestCases()

        if (testCases.isEmpty()) {
            statusLabel.text = I18n.t("케이스 없음", "No cases")
            statusLabel.icon = AllIcons.General.Warning
            statusLabel.foreground = JBColor(Color(200, 120, 0), Color(230, 160, 50))
            return
        }

        val language = getSelectedLanguage()
        runButton.isEnabled = false
        statusLabel.text = I18n.t("실행 중...", "Running...")
        statusLabel.icon = AllIcons.Process.Step_1
        statusLabel.foreground = JBColor.foreground()

        // 모든 카드 상태 초기화
        for (card in cards) card.setRunning()

        ApplicationManager.getApplication().executeOnPooledThread {
            var passCount = 0
            for ((i, tc) in testCases.withIndex()) {
                val result = if (problemSource == ProblemSource.PROGRAMMERS || problemSource == ProblemSource.LEETCODE) {
                    CodeRunner.runProgrammers(code, language, tc, parameterNames)
                } else {
                    CodeRunner.run(code, language, tc)
                }

                if (result.exitCode != 0 && result.error.isNotBlank()) {
                    tc.actualOutput = result.error
                    tc.passed = false
                } else {
                    val stdout = result.output.trim()
                    val stderr = result.error.trim()
                    val expected = tc.expectedOutput.trim()

                    // stdout 전체를 그대로 비교 (줄 단위 trailing whitespace 무시)
                    val stdoutNorm = stdout.lines().map { it.trimEnd() }.joinToString("\n").trimEnd()
                    val expectedNorm = expected.lines().map { it.trimEnd() }.joinToString("\n").trimEnd()
                    tc.actualOutput = stdout
                    tc.passed = stdoutNorm == expectedNorm

                    // stderr가 있으면 디버그 출력으로 표시
                    if (stderr.isNotBlank()) {
                        SwingUtilities.invokeLater {
                            if (i < cards.size) cards[i].setStderrOutput(stderr)
                        }
                    }
                }
                if (tc.passed == true) passCount++

                val idx = i
                val timeMs = result.executionTimeMs
                val memKB = result.peakMemoryKB
                SwingUtilities.invokeLater {
                    if (idx < cards.size) cards[idx].setResult(tc, timeMs, memKB)
                }
            }

            val finalPassCount = passCount
            SwingUtilities.invokeLater {
                runButton.isEnabled = true
                val allPassed = finalPassCount == testCases.size
                statusLabel.text = "$finalPassCount / ${testCases.size} ${I18n.t("통과", "passed")}"
                statusLabel.icon = if (allPassed) AllIcons.General.InspectionsOK else AllIcons.General.Error
                statusLabel.foreground = if (allPassed) {
                    JBColor(Color(46, 160, 67), Color(80, 200, 80))
                } else {
                    JBColor(Color(218, 54, 51), Color(230, 80, 80))
                }
            }
        }
    }

    private fun syncCardsToTestCases() {
        for ((i, card) in cards.withIndex()) {
            if (i < testCases.size) {
                testCases[i].input = card.getInput()
                testCases[i].expectedOutput = card.getExpected()
            }
        }
    }

    // ─── 테스트 케이스 카드 (아코디언) ───

    private inner class TestCaseCard(
        private val number: Int,
        private val testCase: TestCase
    ) : JPanel(BorderLayout()) {

        var isExpanded = false
            private set

        private val headerPanel: JPanel
        private val contentPanel: JPanel
        private val toggleIcon = JLabel(AllIcons.General.ArrowRight)
        private val titleLabel = JLabel("#$number")
        private val statusIcon = JLabel()
        private val inputArea = JTextArea(testCase.input, 3, 20).apply {
            font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
            border = JBUI.Borders.empty(4)
            lineWrap = true
        }
        private val expectedArea = JTextArea(testCase.expectedOutput, 3, 20).apply {
            font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
            border = JBUI.Borders.empty(4)
            lineWrap = true
        }
        private val actualArea = JTextArea("", 3, 20).apply {
            font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
            border = JBUI.Borders.empty(4)
            isEditable = false
            lineWrap = true
            background = JBColor(Color(245, 245, 245), Color(43, 43, 43))
        }
        private val defaultActualFg = JBColor.foreground()
        private val errorFg = JBColor(Color(218, 54, 51), Color(230, 80, 80))

        private val debugArea = JTextArea("", 2, 20).apply {
            font = Font("JetBrains Mono", Font.ITALIC, JBUI.scale(11))
            border = JBUI.Borders.empty(4)
            isEditable = false
            lineWrap = true
            foreground = JBColor(Color(150, 120, 50), Color(200, 180, 100))
            background = JBColor(Color(255, 250, 230), Color(50, 48, 40))
        }
        private val debugPanel = JPanel(BorderLayout()).apply { isVisible = false }

        init {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border()),
                JBUI.Borders.empty(0)
            )
            alignmentX = LEFT_ALIGNMENT

            // 헤더 (클릭하면 토글)
            headerPanel = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
                border = JBUI.Borders.empty(6, 8)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                background = JBColor(Color(240, 240, 240), Color(50, 50, 50))
            }

            val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                isOpaque = false
            }
            leftHeader.add(toggleIcon)
            titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
            leftHeader.add(titleLabel)
            leftHeader.add(statusIcon)

            headerPanel.add(leftHeader, BorderLayout.WEST)

            // 헤더 요약 (접혀있을 때 미리보기)
            val summaryLabel = JLabel().apply {
                foreground = JBColor.GRAY
                font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
            }
            val inputPreview = testCase.input.lines().firstOrNull()?.take(30) ?: ""
            if (inputPreview.isNotBlank()) {
                summaryLabel.text = "${I18n.t("입력", "Input")}: $inputPreview..."
            }
            headerPanel.add(summaryLabel, BorderLayout.CENTER)

            // 삭제 버튼
            val deleteBtn = JLabel(AllIcons.Actions.Close).apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                toolTipText = I18n.t("이 테스트 케이스 삭제", "Delete this test case")
                border = JBUI.Borders.empty(0, 4)
            }
            deleteBtn.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    val idx = cards.indexOf(this@TestCaseCard)
                    if (idx >= 0) removeTestCaseAt(idx)
                }
            })
            headerPanel.add(deleteBtn, BorderLayout.EAST)

            add(headerPanel, BorderLayout.NORTH)

            // 콘텐츠 (펼쳐졌을 때)
            contentPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = JBUI.Borders.empty(4, 8, 8, 8)
                isVisible = false
            }

            contentPanel.add(createFieldPanel(I18n.t("입력", "Input"), inputArea))
            contentPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
            contentPanel.add(createFieldPanel(I18n.t("예상 출력", "Expected"), expectedArea))
            contentPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
            contentPanel.add(createFieldPanel(I18n.t("실제 출력", "Actual"), actualArea))

            // 디버그 출력 (stderr, 조건부 표시)
            contentPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
            contentPanel.add(createResizableOutputPanel(
                debugPanel, debugArea,
                I18n.t("디버그 출력", "Debug Output"),
                JBColor(Color(150, 120, 50), Color(200, 180, 100)),
                AllIcons.General.Information
            ))

            add(contentPanel, BorderLayout.CENTER)

            // 클릭 이벤트
            headerPanel.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    toggle()
                }
            })
            // 자식 컴포넌트도 클릭 가능
            for (comp in leftHeader.components) {
                comp.addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent) {
                        toggle()
                    }
                })
            }

            // 초기 상태: 접힌 상태이므로 헤더 높이만큼만 차지
            updateMaxSize()
        }

        private fun createFieldPanel(label: String, textArea: JTextArea): JPanel {
            var fieldHeight = JBUI.scale(60)

            val scrollPane = JBScrollPane(textArea).apply {
                preferredSize = Dimension(0, fieldHeight)
                minimumSize = Dimension(0, JBUI.scale(30))
            }

            val fieldPanel = object : JPanel(BorderLayout(0, 0)) {
                override fun getPreferredSize(): Dimension {
                    val labelH = getComponent(0)?.preferredSize?.height ?: 0 // label (NORTH)
                    val handleH = getComponent(2)?.preferredSize?.height ?: 0 // handle (SOUTH)
                    return Dimension(super.getPreferredSize().width, labelH + fieldHeight + handleH)
                }
                override fun getMaximumSize(): Dimension {
                    return Dimension(Int.MAX_VALUE, preferredSize.height)
                }
            }
            fieldPanel.alignmentX = LEFT_ALIGNMENT

            // 하단 드래그 핸들로 높이 조절
            val resizeHandle = JPanel().apply {
                preferredSize = Dimension(0, JBUI.scale(8))
                minimumSize = Dimension(0, JBUI.scale(8))
                cursor = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
                background = JBColor(Color(210, 210, 210), Color(65, 65, 65))
            }

            var dragStartY = 0
            var dragStartHeight = 0
            resizeHandle.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mousePressed(e: java.awt.event.MouseEvent) {
                    dragStartY = e.yOnScreen
                    dragStartHeight = fieldHeight
                }
            })
            resizeHandle.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
                override fun mouseDragged(e: java.awt.event.MouseEvent) {
                    val delta = e.yOnScreen - dragStartY
                    fieldHeight = (dragStartHeight + delta).coerceIn(JBUI.scale(30), JBUI.scale(500))
                    scrollPane.preferredSize = Dimension(scrollPane.width, fieldHeight)
                    fieldPanel.revalidate()
                    contentPanel.revalidate()
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                    this@TestCaseCard.revalidate()
                    cardListPanel.revalidate()
                    cardListPanel.repaint()
                }
            })

            fieldPanel.add(JLabel(label).apply {
                font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
                foreground = JBColor.GRAY
            }, BorderLayout.NORTH)
            fieldPanel.add(scrollPane, BorderLayout.CENTER)
            fieldPanel.add(resizeHandle, BorderLayout.SOUTH)
            return fieldPanel
        }

        private fun createResizableOutputPanel(
            wrapperPanel: JPanel, textArea: JTextArea,
            label: String, labelColor: JBColor, icon: Icon
        ): JPanel {
            var panelHeight = JBUI.scale(50)

            val scrollPane = JBScrollPane(textArea).apply {
                preferredSize = Dimension(0, panelHeight)
                minimumSize = Dimension(0, JBUI.scale(30))
            }

            wrapperPanel.removeAll()
            wrapperPanel.layout = BorderLayout(0, 0)

            wrapperPanel.add(JLabel(label).apply {
                font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
                foreground = labelColor
                this.icon = icon
                border = JBUI.Borders.empty(2, 0, 2, 0)
            }, BorderLayout.NORTH)
            wrapperPanel.add(scrollPane, BorderLayout.CENTER)

            val resizeHandle = JPanel().apply {
                preferredSize = Dimension(0, JBUI.scale(8))
                minimumSize = Dimension(0, JBUI.scale(8))
                cursor = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
                background = JBColor(Color(210, 210, 210), Color(65, 65, 65))
            }

            var dragStartY = 0
            var dragStartHeight = 0
            resizeHandle.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mousePressed(e: java.awt.event.MouseEvent) {
                    dragStartY = e.yOnScreen
                    dragStartHeight = panelHeight
                }
            })
            resizeHandle.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
                override fun mouseDragged(e: java.awt.event.MouseEvent) {
                    val delta = e.yOnScreen - dragStartY
                    panelHeight = (dragStartHeight + delta).coerceIn(JBUI.scale(30), JBUI.scale(500))
                    scrollPane.preferredSize = Dimension(scrollPane.width, panelHeight)
                    wrapperPanel.revalidate()
                    contentPanel.revalidate()
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                    this@TestCaseCard.revalidate()
                    cardListPanel.revalidate()
                    cardListPanel.repaint()
                }
            })

            wrapperPanel.add(resizeHandle, BorderLayout.SOUTH)
            wrapperPanel.alignmentX = LEFT_ALIGNMENT
            return wrapperPanel
        }

        fun toggle() {
            isExpanded = !isExpanded
            contentPanel.isVisible = isExpanded
            toggleIcon.icon = if (isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
            updateMaxSize()
            cardListPanel.revalidate()
            cardListPanel.repaint()
            // 스크롤하여 카드 보이게
            if (isExpanded) {
                SwingUtilities.invokeLater {
                    scrollRectToVisible(bounds)
                }
            }
        }

        fun updateMaxSize() {
            if (isExpanded) {
                // preferredSize 기반으로 제한하여 빈 공간 방지
                SwingUtilities.invokeLater {
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                    cardListPanel.revalidate()
                }
            } else {
                val headerHeight = headerPanel.preferredSize.height + insets.top + insets.bottom
                maximumSize = Dimension(Int.MAX_VALUE, headerHeight)
            }
        }

        fun expand() {
            if (!isExpanded) toggle()
        }

        fun getInput(): String = inputArea.text
        fun getExpected(): String = expectedArea.text

        fun setRunning() {
            statusIcon.icon = AllIcons.Process.Step_1
            titleLabel.text = "#$number ${I18n.t("실행 중...", "Running...")}"
            actualArea.text = ""
            actualArea.foreground = defaultActualFg
            debugPanel.isVisible = false
            debugArea.text = ""
        }

        fun setResult(tc: TestCase, timeMs: Long = 0, memKB: Long = 0) {
            actualArea.text = tc.actualOutput
            // 에러일 때 실제 출력을 빨간색으로 표시
            val isError = tc.passed == false && tc.actualOutput.let {
                it.contains("에러") || it.contains("Error") || it.contains("Exception")
                    || it.contains("컴파일") || it.contains("시간 초과") || it.contains("Traceback")
                    || it.startsWith("error:") || it.contains("runtime error", ignoreCase = true)
            }
            actualArea.foreground = if (isError) errorFg else defaultActualFg
            val timeStr = buildString {
                if (timeMs > 0 || memKB > 0) {
                    append(" (")
                    if (timeMs > 0) append("${timeMs}ms")
                    if (timeMs > 0 && memKB > 0) append(" / ")
                    if (memKB > 0) {
                        if (memKB >= 1024) append("%.1fMB".format(memKB / 1024.0))
                        else append("${memKB}KB")
                    }
                    append(")")
                }
            }
            when (tc.passed) {
                true -> {
                    statusIcon.icon = AllIcons.General.InspectionsOK
                    titleLabel.text = "#$number PASS$timeStr"
                    titleLabel.foreground = JBColor(Color(46, 160, 67), Color(80, 200, 80))
                    headerPanel.background = JBColor(Color(235, 250, 235), Color(40, 55, 40))
                }
                false -> {
                    statusIcon.icon = AllIcons.General.Error
                    titleLabel.text = "#$number FAIL$timeStr"
                    titleLabel.foreground = JBColor(Color(218, 54, 51), Color(230, 80, 80))
                    headerPanel.background = JBColor(Color(255, 240, 240), Color(60, 40, 40))
                }
                null -> {
                    statusIcon.icon = null
                    titleLabel.text = "#$number"
                    titleLabel.foreground = JBColor.foreground()
                    headerPanel.background = JBColor(Color(240, 240, 240), Color(50, 50, 50))
                }
            }
            revalidate()
            repaint()
        }

        fun setDebugOutput(text: String) {
            if (text.isNotBlank()) {
                debugArea.text = text
                debugPanel.isVisible = true
                revalidate()
            }
        }

        fun setStderrOutput(text: String) {
            if (text.isNotBlank()) {
                val existing = debugArea.text
                debugArea.text = if (existing.isNotBlank()) {
                    "$existing\n[stderr] $text"
                } else {
                    "[stderr] $text"
                }
                debugPanel.isVisible = true
                revalidate()
            }
        }
    }

}
