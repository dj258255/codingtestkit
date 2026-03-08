package com.codingtestkit.ui

import com.codingtestkit.service.I18n
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import javax.swing.*
import javax.swing.table.AbstractTableModel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class TimerPanel : JPanel(BorderLayout()) {

    init {
        val tabbedPane = JTabbedPane().apply {
            border = JBUI.Borders.empty()
        }
        tabbedPane.addTab(I18n.t("스톱워치", "Stopwatch"), AllIcons.Debugger.Db_muted_breakpoint, StopwatchPanel())
        tabbedPane.addTab(I18n.t("타이머", "Timer"), AllIcons.Actions.StartDebugger, CountdownPanel())
        add(tabbedPane, BorderLayout.CENTER)
    }

    // ─── 원형 타이머 뷰 (이미지 스타일) ───

    private class CircularTimerView : JPanel() {
        var remainingMs: Long = 0
        var totalMs: Long = 0
        var isRunning: Boolean = false
        var onCenterClick: (() -> Unit)? = null

        private val sectorColor = JBColor(Color(217, 100, 100), Color(200, 85, 85))
        private val bgColor = JBColor(Color(245, 245, 245), Color(50, 53, 55))
        private val centerBg = JBColor(Color(255, 255, 255), Color(65, 68, 70))
        private val centerBorder = JBColor(Color(200, 200, 200), Color(90, 93, 95))
        private val tickColor = JBColor(Color(180, 180, 180), Color(120, 120, 120))
        private val numberColor = JBColor(Color(100, 100, 100), Color(170, 170, 170))

        init {
            isOpaque = false
            val size = JBUI.scale(240)
            preferredSize = Dimension(size, size)
            minimumSize = Dimension(JBUI.scale(160), JBUI.scale(160))

            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    val cx = width / 2.0
                    val cy = height / 2.0
                    val side = min(width, height)
                    val outerR = side / 2.0 - JBUI.scale(28)
                    val innerR = outerR * 0.36
                    val dx = e.x - cx
                    val dy = e.y - cy
                    if (dx * dx + dy * dy <= innerR * innerR) {
                        onCenterClick?.invoke()
                    }
                }

                override fun mouseEntered(e: java.awt.event.MouseEvent) { cursor = Cursor.getDefaultCursor() }
                override fun mouseMoved(e: java.awt.event.MouseEvent) {
                    val cx = width / 2.0
                    val cy = height / 2.0
                    val side = min(width, height)
                    val outerR = side / 2.0 - JBUI.scale(28)
                    val innerR = outerR * 0.36
                    val dx = e.x - cx
                    val dy = e.y - cy
                    cursor = if (dx * dx + dy * dy <= innerR * innerR)
                        Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    else Cursor.getDefaultCursor()
                }
            })
            addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
                override fun mouseMoved(e: java.awt.event.MouseEvent) {
                    val cx = width / 2.0
                    val cy = height / 2.0
                    val side = min(width, height)
                    val outerR = side / 2.0 - JBUI.scale(28)
                    val innerR = outerR * 0.36
                    val dx = e.x - cx
                    val dy = e.y - cy
                    cursor = if (dx * dx + dy * dy <= innerR * innerR)
                        Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    else Cursor.getDefaultCursor()
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)

            val side = min(width, height)
            val cx = width / 2.0
            val cy = height / 2.0
            val outerR = side / 2.0 - JBUI.scale(28)  // 빨간 원의 반지름
            val innerR = outerR * 0.36                   // 중앙 버튼 반지름

            // 배경 원 (밝은 회색)
            g2.color = bgColor
            g2.fill(Ellipse2D.Double(cx - outerR, cy - outerR, outerR * 2, outerR * 2))

            // 빨간 원 (남은 시간) — 12시 고정, 반시계방향으로 갭이 커짐
            if (totalMs > 0 && remainingMs > 0) {
                val remainingFraction = remainingMs.toDouble() / totalMs
                val sweepAngle = -(remainingFraction * 360.0)

                if (remainingFraction > 0.001) {
                    val arc = Arc2D.Double(
                        cx - outerR, cy - outerR, outerR * 2, outerR * 2,
                        90.0, sweepAngle, Arc2D.PIE
                    )
                    g2.color = sectorColor
                    g2.fill(arc)
                }
            } else if (totalMs > 0 && remainingMs <= 0) {
                // 시간 종료 - 빈 원
            } else {
                // totalMs == 0, 전체 빨간
                g2.color = sectorColor
                g2.fill(Ellipse2D.Double(cx - outerR, cy - outerR, outerR * 2, outerR * 2))
            }

            // ── 눈금과 숫자: 총 시간을 5등분 (분 또는 초 단위) ──
            val totalSeconds = (totalMs / 1000).toInt().coerceAtLeast(1)
            val useSeconds = totalSeconds < 60  // 1분 미만이면 초 단위
            val totalUnits = if (useSeconds) totalSeconds else (totalSeconds / 60)
            val tickInnerR = outerR + JBUI.scale(2)
            val tickMajorOuterR = outerR + JBUI.scale(10)
            val numRadius = outerR + JBUI.scale(22)
            val divisions = 5
            val step = totalUnits / divisions

            for (i in 0 until divisions) {
                val unitValue = step * i
                val angle = Math.toRadians(90.0 - (i.toDouble() / divisions) * 360.0)

                val x1 = cx + cos(angle) * tickInnerR
                val y1 = cy - sin(angle) * tickInnerR
                val x2 = cx + cos(angle) * tickMajorOuterR
                val y2 = cy - sin(angle) * tickMajorOuterR

                g2.color = tickColor
                g2.stroke = BasicStroke(JBUI.scale(2).toFloat())
                g2.drawLine(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())

                val nx = cx + cos(angle) * numRadius
                val ny = cy - sin(angle) * numRadius
                val label = if (useSeconds) "${unitValue}s" else unitValue.toString()
                g2.color = numberColor
                g2.font = Font("SansSerif", Font.BOLD, JBUI.scale(12))
                val fm = g2.fontMetrics
                g2.drawString(label, (nx - fm.stringWidth(label) / 2).toInt(), (ny + fm.ascent / 2.5).toInt())
            }

            // 중앙 원 (버튼 역할)
            g2.color = centerBg
            g2.fill(Ellipse2D.Double(cx - innerR, cy - innerR, innerR * 2, innerR * 2))
            g2.color = centerBorder
            g2.stroke = BasicStroke(JBUI.scale(2).toFloat())
            g2.draw(Ellipse2D.Double(cx - innerR, cy - innerR, innerR * 2, innerR * 2))

            // 중앙 아이콘: 실행 중이면 일시정지(||), 아니면 재생(▶)
            g2.color = sectorColor
            if (isRunning) {
                // 일시정지 아이콘 (두 개의 세로 막대)
                val barW = JBUI.scale(5).toDouble()
                val barH = innerR * 0.65
                val barGap = JBUI.scale(5).toDouble()
                g2.fill(java.awt.geom.RoundRectangle2D.Double(
                    cx - barGap - barW, cy - barH / 2, barW, barH, barW, barW))
                g2.fill(java.awt.geom.RoundRectangle2D.Double(
                    cx + barGap, cy - barH / 2, barW, barH, barW, barW))
            } else {
                // 재생 아이콘 (삼각형)
                val triSize = innerR * 0.45
                val playX = intArrayOf(
                    (cx - triSize * 0.4).toInt(),
                    (cx - triSize * 0.4).toInt(),
                    (cx + triSize * 0.7).toInt()
                )
                val playY = intArrayOf(
                    (cy - triSize).toInt(),
                    (cy + triSize).toInt(),
                    cy.toInt()
                )
                g2.fillPolygon(playX, playY, 3)
            }

            g2.dispose()
        }

        fun update(remaining: Long, total: Long, running: Boolean) {
            this.remainingMs = remaining
            this.totalMs = total
            this.isRunning = running
            repaint()
        }
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

        private val startButton = JButton(I18n.t("시작", "Start")).apply { icon = AllIcons.Actions.Execute }
        private val stopButton = JButton(I18n.t("정지", "Stop")).apply { icon = AllIcons.Actions.Suspend }
        private val resetButton = JButton(I18n.t("초기화", "Reset")).apply { icon = AllIcons.Actions.Restart }
        private val lapButton = JButton(I18n.t("랩", "Lap")).apply { icon = AllIcons.Vcs.History }

        data class LapRecord(val number: Int, val totalMs: Long, val diffMs: Long, var memo: String = "")

        private val lapRecords = mutableListOf<LapRecord>()
        private val lapTableModel = object : AbstractTableModel() {
            override fun getRowCount() = lapRecords.size
            override fun getColumnCount() = 4
            override fun getColumnName(column: Int) = when (column) {
                0 -> "#"; 1 -> I18n.t("시간", "Time"); 2 -> I18n.t("구간", "Split"); 3 -> I18n.t("메모", "Memo"); else -> ""
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
                    I18n.t("랩 기록", "Lap Records")
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
            startButton.text = I18n.t("재개", "Resume")
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
            startButton.text = I18n.t("시작", "Start")
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

        // 표시 요소
        private val circularTimer = CircularTimerView()
        private val digitalLabel = JLabel("30:00").apply {
            font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(38))
            horizontalAlignment = SwingConstants.CENTER
            foreground = JBColor.foreground()
            border = JBUI.Borders.empty(4, 0)
        }
        private val progressBar = JProgressBar(0, 1000).apply {
            isStringPainted = false
            border = JBUI.Borders.empty(0, JBUI.scale(16), 0, JBUI.scale(16))
            preferredSize = Dimension(0, JBUI.scale(18))
            value = 1000
        }

        // 표시 모드 체크박스
        private val showCircular = JCheckBox(I18n.t("원형 타이머", "Circular Timer"), true).apply {
            font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat()); isOpaque = false
        }
        private val showDigital = JCheckBox(I18n.t("디지털 시계", "Digital Clock"), true).apply {
            font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat()); isOpaque = false
        }
        private val showProgress = JCheckBox(I18n.t("프로그레스 바", "Progress Bar"), false).apply {
            font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat()); isOpaque = false
        }

        // 표시 요소 래퍼
        private val circularWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0))
        private val digitalWrapper = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0))
        private val progressWrapper = JPanel(BorderLayout())

        private val hourField = createTimeField("0")
        private val minField = createTimeField("30")
        private val secField = createTimeField("0")

        private val startButton = JButton(I18n.t("시작", "Start")).apply { icon = AllIcons.Actions.Execute }
        private val stopButton = JButton(I18n.t("정지", "Stop")).apply { icon = AllIcons.Actions.Suspend }
        private val resetButton = JButton(I18n.t("초기화", "Reset")).apply { icon = AllIcons.Actions.Restart }

        init {
            border = JBUI.Borders.empty(4)

            val topPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
            }

            // 표시 모드 체크박스
            val modePanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(8), 0))
            modePanel.add(showCircular)
            modePanel.add(showDigital)
            modePanel.add(showProgress)
            topPanel.add(modePanel)
            topPanel.add(Box.createVerticalStrut(JBUI.scale(4)))

            // 원형 타이머
            circularWrapper.add(circularTimer)
            topPanel.add(circularWrapper)

            // 디지털 시계
            digitalWrapper.add(digitalLabel)
            topPanel.add(digitalWrapper)

            // 프로그레스 바
            progressWrapper.add(progressBar, BorderLayout.CENTER)
            progressWrapper.isVisible = false
            topPanel.add(progressWrapper)
            topPanel.add(Box.createVerticalStrut(JBUI.scale(6)))

            // 시간 입력
            val setPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(4), 0))
            setPanel.add(hourField)
            setPanel.add(createUnitLabel(I18n.t("시", "h")))
            setPanel.add(minField)
            setPanel.add(createUnitLabel(I18n.t("분", "m")))
            setPanel.add(secField)
            setPanel.add(createUnitLabel(I18n.t("초", "s")))
            topPanel.add(setPanel)

            // 프리셋 버튼
            val presetPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(4), 0))
            val presets = listOf(
                I18n.t("30분", "30min") to Triple(0, 30, 0),
                I18n.t("1시간", "1hr") to Triple(1, 0, 0),
                I18n.t("2시간", "2hr") to Triple(2, 0, 0),
                I18n.t("3시간", "3hr") to Triple(3, 0, 0)
            )
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

            // 체크박스 이벤트
            showCircular.addActionListener { circularWrapper.isVisible = showCircular.isSelected }
            showDigital.addActionListener { digitalWrapper.isVisible = showDigital.isSelected }
            showProgress.addActionListener { progressWrapper.isVisible = showProgress.isSelected }

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

            circularTimer.onCenterClick = { if (running) stop() else start() }

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
                    JOptionPane.showMessageDialog(this, I18n.t("시간을 설정하세요.", "Please set a time."), "CodingTestKit", JOptionPane.WARNING_MESSAGE)
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
            startButton.text = I18n.t("재개", "Resume")
            stopButton.isEnabled = false
            updateDisplay()
        }

        private fun reset() {
            if (running) { timer?.stop(); running = false }
            startButton.isEnabled = true
            startButton.text = I18n.t("시작", "Start")
            stopButton.isEnabled = false
            setFieldsEnabled(true)
            digitalLabel.foreground = JBColor.foreground()
            onTimeFieldChanged()
        }

        private fun timeUp() {
            timer?.stop()
            running = false
            remainingMs = 0
            startButton.isEnabled = true
            startButton.text = I18n.t("시작", "Start")
            stopButton.isEnabled = false
            setFieldsEnabled(true)
            digitalLabel.foreground = JBColor(Color.RED, Color(230, 80, 80))
            Toolkit.getDefaultToolkit().beep()
            JOptionPane.showMessageDialog(this, I18n.t("시간이 종료되었습니다!", "Time's up!"), I18n.t("타이머 종료", "Timer Ended"), JOptionPane.INFORMATION_MESSAGE)
        }

        private fun setFieldsEnabled(enabled: Boolean) {
            hourField.isEnabled = enabled
            minField.isEnabled = enabled
            secField.isEnabled = enabled
        }

        private fun updateDisplay() {
            circularTimer.update(remainingMs, totalMs, running)

            // 디지털 시계
            val totalSec = remainingMs / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            digitalLabel.text = if (h > 0) String.format("%d:%02d:%02d", h, m, s)
            else String.format("%d:%02d", m, s)

            if (remainingMs in 1..59999 && running) {
                digitalLabel.foreground = JBColor(Color.RED, Color(230, 80, 80))
            } else if (remainingMs > 59999) {
                digitalLabel.foreground = JBColor.foreground()
            }

            // 프로그레스 바
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
