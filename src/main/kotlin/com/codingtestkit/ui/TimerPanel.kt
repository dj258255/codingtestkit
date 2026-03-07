package com.codingtestkit.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.table.AbstractTableModel

class TimerPanel : JPanel(BorderLayout()) {

    init {
        val tabbedPane = JTabbedPane().apply {
            border = JBUI.Borders.empty()
        }
        tabbedPane.addTab("스톱워치", AllIcons.Debugger.Db_muted_breakpoint, StopwatchPanel())
        tabbedPane.addTab("타이머", AllIcons.Actions.StartDebugger, CountdownPanel())
        add(tabbedPane, BorderLayout.CENTER)
    }

    // ─── 스톱워치 ───

    private class StopwatchPanel : JPanel(BorderLayout()) {

        private var startTime: Long = 0
        private var accumulatedMs: Long = 0
        private var running = false
        private var timer: Timer? = null
        private var lapCount = 0
        private var lastLapMs: Long = 0

        private val timeLabel = JLabel("00:00.00").apply {
            font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(42))
            horizontalAlignment = SwingConstants.CENTER
            border = JBUI.Borders.empty(8, 0, 4, 0)
            foreground = JBColor.foreground()
        }

        private val startButton = JButton("시작").apply { icon = AllIcons.Actions.Execute }
        private val stopButton = JButton("정지").apply { icon = AllIcons.Actions.Suspend }
        private val resetButton = JButton("초기화").apply { icon = AllIcons.Actions.Restart }
        private val lapButton = JButton("랩").apply { icon = AllIcons.Vcs.History }

        data class LapRecord(val number: Int, val totalMs: Long, val diffMs: Long, var memo: String = "")

        private val lapRecords = mutableListOf<LapRecord>()
        private val lapTableModel = object : AbstractTableModel() {
            override fun getRowCount() = lapRecords.size
            override fun getColumnCount() = 4
            override fun getColumnName(column: Int) = when (column) {
                0 -> "#"; 1 -> "시간"; 2 -> "구간"; 3 -> "메모"; else -> ""
            }
            override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
                val r = lapRecords[rowIndex]
                return when (columnIndex) {
                    0 -> r.number; 1 -> formatTime(r.totalMs)
                    2 -> "+${formatTime(r.diffMs)}"; 3 -> r.memo; else -> ""
                }
            }
            override fun isCellEditable(rowIndex: Int, columnIndex: Int) = columnIndex == 3
            override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
                if (columnIndex == 3) {
                    lapRecords[rowIndex].memo = aValue?.toString() ?: ""
                    fireTableCellUpdated(rowIndex, columnIndex)
                }
            }
        }

        init {
            border = JBUI.Borders.empty(4)

            // 상단: 시간 + 버튼
            val topPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
            }
            topPanel.add(timeLabel.apply { alignmentX = CENTER_ALIGNMENT })

            val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(4), 0))
            buttonPanel.add(startButton)
            buttonPanel.add(stopButton)
            buttonPanel.add(resetButton)
            buttonPanel.add(lapButton)
            topPanel.add(buttonPanel)

            add(topPanel, BorderLayout.NORTH)

            stopButton.isEnabled = false
            lapButton.isEnabled = false

            // 중앙: 랩 기록 테이블
            val lapTable = JTable(lapTableModel).apply {
                font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
                rowHeight = JBUI.scale(22)
                autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
                columnModel.getColumn(0).preferredWidth = 30
                columnModel.getColumn(0).maxWidth = 40
                columnModel.getColumn(1).preferredWidth = 80
                columnModel.getColumn(2).preferredWidth = 80
                columnModel.getColumn(3).preferredWidth = 100
                columnModel.getColumn(3).cellEditor = javax.swing.DefaultCellEditor(JTextField())
                tableHeader.reorderingAllowed = false
                putClientProperty("terminateEditOnFocusLost", true)
            }
            val lapScroll = JScrollPane(lapTable).apply {
                border = BorderFactory.createTitledBorder(
                    JBUI.Borders.customLine(JBColor.border()),
                    "랩 기록"
                )
            }
            add(lapScroll, BorderLayout.CENTER)

            startButton.addActionListener { start() }
            stopButton.addActionListener { stop() }
            resetButton.addActionListener { reset() }
            lapButton.addActionListener { lap() }
        }

        private fun getElapsedMs(): Long {
            return if (running) accumulatedMs + (System.currentTimeMillis() - startTime)
            else accumulatedMs
        }

        private fun start() {
            if (running) return
            running = true
            startTime = System.currentTimeMillis()
            startButton.isEnabled = false
            stopButton.isEnabled = true
            lapButton.isEnabled = true
            timer = Timer(33) { updateDisplay() }
            timer?.start()
        }

        private fun stop() {
            if (!running) return
            accumulatedMs += System.currentTimeMillis() - startTime
            running = false
            timer?.stop()
            startButton.isEnabled = true
            startButton.text = "재개"
            stopButton.isEnabled = false
            lapButton.isEnabled = false
            updateDisplay()
        }

        private fun reset() {
            if (running) { timer?.stop(); running = false }
            accumulatedMs = 0; lapCount = 0; lastLapMs = 0
            lapRecords.clear()
            lapTableModel.fireTableDataChanged()
            startButton.isEnabled = true
            startButton.text = "시작"
            stopButton.isEnabled = false
            lapButton.isEnabled = false
            updateDisplay()
        }

        private fun lap() {
            val elapsed = getElapsedMs()
            lapCount++
            val diff = elapsed - lastLapMs
            lastLapMs = elapsed
            lapRecords.add(LapRecord(lapCount, elapsed, diff))
            lapTableModel.fireTableRowsInserted(lapRecords.size - 1, lapRecords.size - 1)
        }

        private fun updateDisplay() {
            timeLabel.text = formatTime(getElapsedMs())
        }
    }

    // ─── 카운트다운 타이머 ───

    private class CountdownPanel : JPanel(BorderLayout()) {

        private var remainingMs: Long = 0
        private var totalMs: Long = 0
        private var running = false
        private var timer: Timer? = null
        private var endTime: Long = 0

        private val timeLabel = JLabel("30:00.00").apply {
            font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(42))
            horizontalAlignment = SwingConstants.CENTER
            border = JBUI.Borders.empty(8, 0, 2, 0)
            foreground = JBColor.foreground()
        }

        private val progressBar = JProgressBar(0, 1000).apply {
            isStringPainted = false
            border = JBUI.Borders.empty(0, JBUI.scale(16), 0, JBUI.scale(16))
            preferredSize = Dimension(0, JBUI.scale(22))
        }

        private val hourField = createTimeField("0")
        private val minField = createTimeField("30")
        private val secField = createTimeField("0")

        private val startButton = JButton("시작").apply { icon = AllIcons.Actions.Execute }
        private val stopButton = JButton("정지").apply { icon = AllIcons.Actions.Suspend }
        private val resetButton = JButton("초기화").apply { icon = AllIcons.Actions.Restart }

        init {
            border = JBUI.Borders.empty(4)

            val topPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
            }

            // 시간 표시
            topPanel.add(timeLabel.apply { alignmentX = CENTER_ALIGNMENT })
            // 프로그레스 바 (시간 바로 아래)
            progressBar.alignmentX = CENTER_ALIGNMENT
            topPanel.add(progressBar)
            topPanel.add(Box.createVerticalStrut(JBUI.scale(6)))

            // 시간 입력
            val setPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(4), 0))
            setPanel.add(hourField)
            setPanel.add(createUnitLabel("시"))
            setPanel.add(minField)
            setPanel.add(createUnitLabel("분"))
            setPanel.add(secField)
            setPanel.add(createUnitLabel("초"))
            topPanel.add(setPanel)

            // 프리셋 버튼
            val presetPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(4), 0))
            val presets = listOf("30분" to Triple(0, 30, 0), "1시간" to Triple(1, 0, 0), "2시간" to Triple(2, 0, 0), "3시간" to Triple(3, 0, 0))
            for ((label, time) in presets) {
                val btn = JButton(label).apply {
                    font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
                    isFocusPainted = false
                    putClientProperty("JButton.buttonType", "roundRect")
                }
                btn.addActionListener { setPreset(time.first, time.second, time.third) }
                presetPanel.add(btn)
            }
            topPanel.add(presetPanel)
            topPanel.add(Box.createVerticalStrut(JBUI.scale(2)))

            // 컨트롤 버튼
            val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(4), 0))
            buttonPanel.add(startButton)
            buttonPanel.add(stopButton)
            buttonPanel.add(resetButton)
            topPanel.add(buttonPanel)

            add(topPanel, BorderLayout.NORTH)

            stopButton.isEnabled = false

            // 입력 변경 시 즉시 반영
            val docListener = object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = onTimeFieldChanged()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = onTimeFieldChanged()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = onTimeFieldChanged()
            }
            hourField.document.addDocumentListener(docListener)
            minField.document.addDocumentListener(docListener)
            secField.document.addDocumentListener(docListener)

            startButton.addActionListener { start() }
            stopButton.addActionListener { stop() }
            resetButton.addActionListener { reset() }

            onTimeFieldChanged()
        }

        private fun createUnitLabel(text: String): JLabel {
            return JLabel(text).apply {
                font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
                foreground = JBColor.GRAY
            }
        }

        private fun getFieldValue(field: JTextField): Int {
            return try { field.text.trim().toInt().coerceAtLeast(0) } catch (_: Exception) { 0 }
        }

        private fun onTimeFieldChanged() {
            if (!running) {
                val h = getFieldValue(hourField)
                val m = getFieldValue(minField)
                val s = getFieldValue(secField)
                totalMs = ((h * 3600L) + (m * 60L) + s) * 1000
                remainingMs = totalMs
                updateDisplay()
            }
        }

        private fun setPreset(h: Int, m: Int, s: Int) {
            hourField.text = h.toString()
            minField.text = m.toString()
            secField.text = s.toString()
        }

        private fun start() {
            if (running) return
            if (remainingMs <= 0) {
                onTimeFieldChanged()
                if (totalMs <= 0) {
                    JOptionPane.showMessageDialog(this, "시간을 설정하세요.", "CodingTestKit", JOptionPane.WARNING_MESSAGE)
                    return
                }
            }
            running = true
            endTime = System.currentTimeMillis() + remainingMs
            startButton.isEnabled = false
            stopButton.isEnabled = true
            setFieldsEnabled(false)
            timer = Timer(33) {
                remainingMs = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
                if (remainingMs <= 0) timeUp()
                updateDisplay()
            }
            timer?.start()
        }

        private fun stop() {
            if (!running) return
            remainingMs = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
            running = false
            timer?.stop()
            startButton.isEnabled = true
            startButton.text = "재개"
            stopButton.isEnabled = false
        }

        private fun reset() {
            if (running) { timer?.stop(); running = false }
            startButton.isEnabled = true
            startButton.text = "시작"
            stopButton.isEnabled = false
            setFieldsEnabled(true)
            timeLabel.foreground = JBColor.foreground()
            onTimeFieldChanged()
        }

        private fun timeUp() {
            timer?.stop()
            running = false
            remainingMs = 0
            startButton.isEnabled = true
            startButton.text = "시작"
            stopButton.isEnabled = false
            timeLabel.foreground = JBColor(Color.RED, Color(230, 80, 80))
            setFieldsEnabled(true)
            Toolkit.getDefaultToolkit().beep()
            JOptionPane.showMessageDialog(this, "시간이 종료되었습니다!", "타이머 종료", JOptionPane.INFORMATION_MESSAGE)
        }

        private fun setFieldsEnabled(enabled: Boolean) {
            hourField.isEnabled = enabled
            minField.isEnabled = enabled
            secField.isEnabled = enabled
        }

        private fun updateDisplay() {
            timeLabel.text = formatTime(remainingMs)
            if (remainingMs in 1..59999 && running) {
                timeLabel.foreground = JBColor(Color.RED, Color(230, 80, 80))
            } else if (remainingMs > 59999) {
                timeLabel.foreground = JBColor.foreground()
            }
            if (totalMs > 0) {
                progressBar.value = ((remainingMs.toDouble() / totalMs) * 1000).toInt()
            }
        }
    }

    companion object {
        fun createTimeField(initial: String): JTextField {
            return JTextField(initial).apply {
                font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(16))
                horizontalAlignment = JTextField.CENTER
                val dim = Dimension(JBUI.scale(56), JBUI.scale(32))
                preferredSize = dim
                minimumSize = dim
            }
        }

        fun formatTime(ms: Long): String {
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val centis = (ms % 1000) / 10
            return if (hours > 0) {
                String.format("%02d:%02d:%02d.%02d", hours, minutes, seconds, centis)
            } else {
                String.format("%02d:%02d.%02d", minutes, seconds, centis)
            }
        }

        fun parseTimeToMs(timeStr: String): Long {
            val parts = timeStr.split(":", ".")
            return try {
                when (parts.size) {
                    4 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000 + parts[3].toLong() * 10
                    3 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000 + parts[2].toLong() * 10
                    else -> 0
                }
            } catch (_: Exception) { 0 }
        }
    }
}
