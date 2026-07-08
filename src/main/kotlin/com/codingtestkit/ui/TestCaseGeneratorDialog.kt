package com.codingtestkit.ui

import com.codingtestkit.service.I18n
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.random.Random

/**
 * 대량 테스트 케이스 생성 다이얼로그 (이슈 #36).
 *
 * 경쟁 프로그래밍에서 손으로 만들기 힘든 큰 입력(랜덤/정렬/상수/순열 배열)을
 * 생성한다. 생성된 케이스는 예상 출력이 비어 있어 판정 없는 중립 실행이 되며,
 * TLE·메모리 확인 용도로 쓰인다.
 */
class TestCaseGeneratorDialog(
    /** 파라미터형 플랫폼(리트코드/프로그래머스)이면 배열 리터럴 형식을 기본 선택 */
    private val parameterStyle: Boolean = false
) : DialogWrapper(true) {

    enum class OutputFormat(val ko: String, val en: String) {
        SPACE("공백 구분 (stdin형 — 코드포스/SWEA)", "Space-separated (stdin — Codeforces/SWEA)"),
        NEWLINE("줄바꿈 구분 (stdin형)", "Newline-separated (stdin)"),
        ARRAY("배열 리터럴 [1,2,3] (파라미터형 — 리트코드/프로그래머스)", "Array literal [1,2,3] (parameters — LeetCode/Programmers)");

        fun label(): String = I18n.t(ko, en)
    }

    enum class Pattern(val ko: String, val en: String) {
        RANDOM("랜덤", "Random"),
        INCREASING("증가 (min부터 연속)", "Increasing (consecutive from min)"),
        DECREASING("감소 (max부터 연속)", "Decreasing (consecutive from max)"),
        CONSTANT("상수 (모두 min)", "Constant (all = min)"),
        PERMUTATION("순열 셔플 (1..N)", "Shuffled permutation (1..N)");

        fun label(): String = I18n.t(ko, en)
    }

    private val caseCountField = JTextField("1", 6)
    private val sizeField = JTextField("100000", 10)
    private val minField = JTextField("1", 12)
    private val maxField = JTextField("1000000000", 12)
    private val patternCombo = ComboBox(Pattern.entries.map { it.label() }.toTypedArray())
    private val formatCombo = ComboBox(OutputFormat.entries.map { it.label() }.toTypedArray()).apply {
        selectedIndex = if (parameterStyle) OutputFormat.ARRAY.ordinal else OutputFormat.SPACE.ordinal
    }
    private val firstLineNCheck = JCheckBox(I18n.t("첫 줄에 N 포함 (stdin형 전용)", "Include N on the first line (stdin only)"), !parameterStyle)
    private val seedField = JTextField("", 10)

    init {
        title = I18n.t("테스트 케이스 생성", "Generate Test Cases")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(4, 4)
            gridx = 0; gridy = 0
        }

        fun addRow(label: String, comp: JComponent) {
            gbc.gridx = 0
            panel.add(JLabel(label), gbc)
            gbc.gridx = 1
            panel.add(comp, gbc)
            gbc.gridy++
        }

        addRow(I18n.t("케이스 수:", "Cases:"), caseCountField)
        addRow(I18n.t("요소 개수 N:", "Element count N:"), sizeField)
        addRow(I18n.t("최솟값:", "Min value:"), minField)
        addRow(I18n.t("최댓값:", "Max value:"), maxField)
        addRow(I18n.t("패턴:", "Pattern:"), patternCombo)
        addRow(I18n.t("형식:", "Format:"), formatCombo)

        gbc.gridx = 0; gbc.gridwidth = 2
        panel.add(firstLineNCheck, gbc); gbc.gridy++
        gbc.gridwidth = 1

        // 배열 리터럴 형식에서는 첫 줄 N이 의미 없음
        formatCombo.addActionListener {
            firstLineNCheck.isEnabled = formatCombo.selectedIndex != OutputFormat.ARRAY.ordinal
        }
        firstLineNCheck.isEnabled = formatCombo.selectedIndex != OutputFormat.ARRAY.ordinal

        addRow(I18n.t("시드 (비우면 랜덤):", "Seed (blank = random):"), seedField)

        gbc.gridx = 0; gbc.gridwidth = 2
        panel.add(JLabel("<html><i>${I18n.t(
            "생성된 케이스는 예상 출력이 비어 있어 판정 없이 실행됩니다 (TLE/메모리 확인용).<br>" +
                "파라미터형(리트코드/프로그래머스)은 배열 리터럴 형식을 쓰세요. 단, 인자를 소스에 넣는 구조라<br>" +
                "컴파일 언어(Java 등)에서는 1만 요소 이상이면 느리거나 컴파일 한계로 실패할 수 있습니다.",
            "Generated cases have no expected output — they run without a verdict (TLE/memory checks).<br>" +
                "For parameter-style platforms (LeetCode/Programmers) use the array-literal format. Since arguments<br>" +
                "are embedded in source, compiled languages (Java etc.) may be slow or fail above ~10k elements."
        )}</i></html>").apply { foreground = com.intellij.ui.JBColor.GRAY }, gbc)

        return panel
    }

    override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
        val cases = caseCountField.text.trim().toIntOrNull()
        if (cases == null || cases !in 1..50) {
            return com.intellij.openapi.ui.ValidationInfo(I18n.t("케이스 수는 1~50", "Cases must be 1–50"), caseCountField)
        }
        val n = sizeField.text.trim().toIntOrNull()
        if (n == null || n !in 1..5_000_000) {
            return com.intellij.openapi.ui.ValidationInfo(I18n.t("N은 1~5,000,000", "N must be 1–5,000,000"), sizeField)
        }
        val min = minField.text.trim().toLongOrNull()
            ?: return com.intellij.openapi.ui.ValidationInfo(I18n.t("최솟값이 숫자가 아닙니다", "Min is not a number"), minField)
        val max = maxField.text.trim().toLongOrNull()
            ?: return com.intellij.openapi.ui.ValidationInfo(I18n.t("최댓값이 숫자가 아닙니다", "Max is not a number"), maxField)
        if (min > max) {
            return com.intellij.openapi.ui.ValidationInfo(I18n.t("최솟값 > 최댓값", "Min > Max"), minField)
        }
        if (seedField.text.isNotBlank() && seedField.text.trim().toLongOrNull() == null) {
            return com.intellij.openapi.ui.ValidationInfo(I18n.t("시드가 숫자가 아닙니다", "Seed is not a number"), seedField)
        }
        return null
    }

    /** OK 이후 호출: 설정대로 입력 문자열들을 생성 */
    fun generateInputs(): List<String> {
        val cases = caseCountField.text.trim().toInt()
        val n = sizeField.text.trim().toInt()
        val min = minField.text.trim().toLong()
        val max = maxField.text.trim().toLong()
        val pattern = Pattern.entries[patternCombo.selectedIndex]
        val format = OutputFormat.entries[formatCombo.selectedIndex]
        val sep = when (format) {
            OutputFormat.NEWLINE -> "\n"
            OutputFormat.ARRAY -> ","
            OutputFormat.SPACE -> " "
        }
        val baseSeed = seedField.text.trim().toLongOrNull()

        return (0 until cases).map { caseIdx ->
            // 시드 지정 시 케이스마다 파생 시드로 재현 가능 + 케이스 간 서로 다름
            val rng = if (baseSeed != null) Random(baseSeed + caseIdx) else Random.Default
            buildString(n * 8) {
                if (format == OutputFormat.ARRAY) append('[')
                else if (firstLineNCheck.isSelected) append(n).append('\n')
                when (pattern) {
                    Pattern.RANDOM -> {
                        for (i in 0 until n) {
                            if (i > 0) append(sep)
                            // min==max면 nextLong 빈 범위 예외 방지, max+1 오버플로 방지
                            append(
                                if (min == max) min
                                else rng.nextLong(min, if (max == Long.MAX_VALUE) max else max + 1)
                            )
                        }
                    }
                    Pattern.INCREASING -> {
                        for (i in 0 until n) {
                            if (i > 0) append(sep)
                            append(min + i)
                        }
                    }
                    Pattern.DECREASING -> {
                        for (i in 0 until n) {
                            if (i > 0) append(sep)
                            append(max - i)
                        }
                    }
                    Pattern.CONSTANT -> {
                        for (i in 0 until n) {
                            if (i > 0) append(sep)
                            append(min)
                        }
                    }
                    Pattern.PERMUTATION -> {
                        val perm = (1..n).toMutableList()
                        perm.shuffle(rng)
                        for ((i, v) in perm.withIndex()) {
                            if (i > 0) append(sep)
                            append(v)
                        }
                    }
                }
                if (format == OutputFormat.ARRAY) append(']')
            }
        }
    }
}
