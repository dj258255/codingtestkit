package com.codingtestkit.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

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

        private var elapsedMs: Long = 0
        private var running = false
        private var timer: Timer? = null
        private var lapCount = 0

        private val timeLabel = JLabel("00:00.00").apply {
            font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(48))
            horizontalAlignment = SwingConstants.CENTER
            border = JBUI.Borders.empty(24, 0, 12, 0)
            foreground = JBColor.foreground()
        }

        private val startButton = JButton("시작").apply { icon = AllIcons.Actions.Execute }
        private val stopButton = JButton("정지").apply { icon = AllIcons.Actions.Suspend }
        private val resetButton = JButton("초기화").apply { icon = AllIcons.Actions.Restart }
        private val lapButton = JButton("랩").apply { icon = AllIcons.Vcs.History }
        private val lapListModel = DefaultListModel<String>()

        init {
            border = JBUI.Borders.empty(8)
            add(timeLabel, BorderLayout.NORTH)

            val centerPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
            }

            val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(6), JBUI.scale(4))).apply {
                alignmentX = CENTER_ALIGNMENT
            }
            buttonPanel.add(startButton)
            buttonPanel.add(stopButton)
            buttonPanel.add(resetButton)
            buttonPanel.add(lapButton)
            centerPanel.add(buttonPanel)
            centerPanel.add(Box.createVerticalStrut(JBUI.scale(8)))

            stopButton.isEnabled = false
            lapButton.isEnabled = false

            val lapList = JList(lapListModel).apply {
                font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
                background = JBColor(Color(245, 245, 245), Color(43, 43, 43))
                cellRenderer = LapCellRenderer()
            }
            val lapScroll = JScrollPane(lapList).apply {
                border = BorderFactory.createTitledBorder(
                    JBUI.Borders.customLine(JBColor.border()),
                    "랩 기록"
                )
            }
            centerPanel.add(lapScroll)

            add(centerPanel, BorderLayout.CENTER)

            startButton.addActionListener { start() }
            stopButton.addActionListener { stop() }
            resetButton.addActionListener { reset() }
            lapButton.addActionListener { lap() }
        }

        private fun start() {
            if (running) return
            running = true
            startButton.isEnabled = false
            stopButton.isEnabled = true
            lapButton.isEnabled = true
            timer = Timer(10) { elapsedMs += 10; updateDisplay() }
            timer?.start()
        }

        private fun stop() {
            running = false
            timer?.stop()
            startButton.isEnabled = true
            startButton.text = "재개"
            stopButton.isEnabled = false
            lapButton.isEnabled = false
        }

        private fun reset() {
            stop()
            elapsedMs = 0
            lapCount = 0
            lapListModel.clear()
            startButton.text = "시작"
            updateDisplay()
        }

        private fun lap() {
            lapCount++
            val diff = if (lapCount > 0 && lapListModel.size() > 0) {
                val prevTotal = parseLapMs(lapListModel.lastElement())
                elapsedMs - prevTotal
            } else elapsedMs

            lapListModel.addElement("#$lapCount  ${formatTime(elapsedMs)}  (+${formatTime(diff)})")
        }

        private fun parseLapMs(lapText: String): Long {
            val timeStr = lapText.substringAfter("#").substringAfter("  ").substringBefore("  (")
            return parseTimeToMs(timeStr)
        }

        private fun updateDisplay() {
            timeLabel.text = formatTime(elapsedMs)
        }
    }

    // ─── 카운트다운 타이머 ───

    private class CountdownPanel : JPanel(BorderLayout()) {

        private var remainingMs: Long = 0
        private var totalMs: Long = 0
        private var running = false
        private var timer: Timer? = null

        private val timeLabel = JLabel("00:00.00").apply {
            font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(48))
            horizontalAlignment = SwingConstants.CENTER
            border = JBUI.Borders.empty(24, 0, 12, 0)
            foreground = JBColor.foreground()
        }

        private val hourSpinner = JSpinner(SpinnerNumberModel(0, 0, 23, 1))
        private val minSpinner = JSpinner(SpinnerNumberModel(30, 0, 59, 1))
        private val secSpinner = JSpinner(SpinnerNumberModel(0, 0, 59, 1))

        private val startButton = JButton("시작").apply { icon = AllIcons.Actions.Execute }
        private val stopButton = JButton("정지").apply { icon = AllIcons.Actions.Suspend }
        private val resetButton = JButton("초기화").apply { icon = AllIcons.Actions.Restart }

        private val progressBar = JProgressBar(0, 1000).apply {
            isStringPainted = false
            preferredSize = Dimension(JBUI.scale(200), JBUI.scale(6))
            border = JBUI.Borders.empty()
        }

        init {
            border = JBUI.Borders.empty(8)
            add(timeLabel, BorderLayout.NORTH)

            val centerPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
            }

            // 시간 설정
            val setPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(4), JBUI.scale(4))).apply {
                alignmentX = CENTER_ALIGNMENT
                border = JBUI.Borders.empty(4)
            }

            styleSpinner(hourSpinner)
            styleSpinner(minSpinner)
            styleSpinner(secSpinner)

            setPanel.add(hourSpinner)
            setPanel.add(createUnitLabel("시"))
            setPanel.add(minSpinner)
            setPanel.add(createUnitLabel("분"))
            setPanel.add(secSpinner)
            setPanel.add(createUnitLabel("초"))
            centerPanel.add(setPanel)

            // 프리셋 버튼
            val presetPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(4), JBUI.scale(2))).apply {
                alignmentX = CENTER_ALIGNMENT
            }
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
            centerPanel.add(presetPanel)
            centerPanel.add(Box.createVerticalStrut(JBUI.scale(4)))

            // 컨트롤 버튼
            val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(6), JBUI.scale(4))).apply {
                alignmentX = CENTER_ALIGNMENT
            }
            buttonPanel.add(startButton)
            buttonPanel.add(stopButton)
            buttonPanel.add(resetButton)
            centerPanel.add(buttonPanel)

            // 프로그레스 바
            val progressPanel = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
                alignmentX = CENTER_ALIGNMENT
            }
            progressPanel.add(progressBar)
            centerPanel.add(progressPanel)

            add(centerPanel, BorderLayout.CENTER)

            stopButton.isEnabled = false

            startButton.addActionListener { start() }
            stopButton.addActionListener { stop() }
            resetButton.addActionListener { reset() }
        }

        private fun createUnitLabel(text: String): JLabel {
            return JLabel(text).apply {
                font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
                foreground = JBColor.GRAY
            }
        }

        private fun styleSpinner(spinner: JSpinner) {
            spinner.preferredSize = Dimension(JBUI.scale(55), JBUI.scale(28))
            spinner.font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(14))
        }

        private fun setPreset(h: Int, m: Int, s: Int) {
            hourSpinner.value = h
            minSpinner.value = m
            secSpinner.value = s
            reset()
        }

        private fun start() {
            if (running) return

            if (remainingMs <= 0) {
                val h = hourSpinner.value as Int
                val m = minSpinner.value as Int
                val s = secSpinner.value as Int
                totalMs = ((h * 3600L) + (m * 60L) + s) * 1000
                remainingMs = totalMs

                if (totalMs <= 0) {
                    JOptionPane.showMessageDialog(this, "시간을 설정하세요.", "CodingTestKit", JOptionPane.WARNING_MESSAGE)
                    return
                }
            }

            running = true
            startButton.isEnabled = false
            stopButton.isEnabled = true
            setSpinnersEnabled(false)

            timer = Timer(10) {
                remainingMs -= 10
                if (remainingMs <= 0) {
                    remainingMs = 0
                    timeUp()
                }
                updateDisplay()
            }
            timer?.start()
        }

        private fun stop() {
            running = false
            timer?.stop()
            startButton.isEnabled = true
            startButton.text = "재개"
            stopButton.isEnabled = false
        }

        private fun reset() {
            stop()
            val h = hourSpinner.value as Int
            val m = minSpinner.value as Int
            val s = secSpinner.value as Int
            totalMs = ((h * 3600L) + (m * 60L) + s) * 1000
            remainingMs = totalMs
            startButton.text = "시작"
            setSpinnersEnabled(true)
            timeLabel.foreground = JBColor.foreground()
            updateDisplay()
        }

        private fun timeUp() {
            stop()
            startButton.text = "시작"
            timeLabel.foreground = JBColor(Color.RED, Color(230, 80, 80))
            setSpinnersEnabled(true)

            Toolkit.getDefaultToolkit().beep()
            JOptionPane.showMessageDialog(
                this,
                "시간이 종료되었습니다!",
                "타이머 종료",
                JOptionPane.INFORMATION_MESSAGE
            )
        }

        private fun setSpinnersEnabled(enabled: Boolean) {
            hourSpinner.isEnabled = enabled
            minSpinner.isEnabled = enabled
            secSpinner.isEnabled = enabled
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

    // ─── 랩 셀 렌더러 ───

    private class LapCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            border = JBUI.Borders.empty(3, 8)
            font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
            return this
        }
    }

    companion object {
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
                    4 -> {
                        (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000 + parts[3].toLong() * 10
                    }
                    3 -> {
                        (parts[0].toLong() * 60 + parts[1].toLong()) * 1000 + parts[2].toLong() * 10
                    }
                    else -> 0
                }
            } catch (_: Exception) { 0 }
        }
    }
}
