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
    private val parameterStyle: Boolean = false,
    /** 파라미터형 문제의 파라미터 이름들 (다중 파라미터 안내용) */
    private val parameterNames: List<String> = emptyList(),
    /** 파라미터 타입들 (리트코드 metaData 제공 시 전 파라미터 자동 생성, 이슈 #36) */
    private val parameterTypes: List<String> = emptyList()
) : DialogWrapper(true) {

    companion object {
        private val INT_ARRAY_TYPES = setOf("integer[]", "long[]", "list<integer>", "list<long>")
        private val STRING_ARRAY_TYPES = setOf("string[]", "list<string>")
        private val SCALAR_INT_TYPES = setOf("integer", "long")
        private val INT_2D_TYPES = setOf("integer[][]", "int[][]", "long[][]",
            "list<list<integer>>", "list<list<long>>")
        private val TREE_TYPES = setOf("treenode", "treenode*")

        /** 자동 생성을 지원하는 파라미터 타입인가 (이슈 #36: 2차원 배열·TreeNode 추가) */
        fun isSupportedType(type: String): Boolean {
            val t = type.trim().lowercase()
            return t in INT_ARRAY_TYPES || t in STRING_ARRAY_TYPES || t in SCALAR_INT_TYPES ||
                t in INT_2D_TYPES || t in TREE_TYPES ||
                t == "string" || t == "boolean" || t == "double"
        }
    }

    /** 모든 파라미터 타입을 알고 있고 지원 가능하면 전 파라미터 자동 생성 모드 */
    private val autoParams: Boolean = parameterStyle &&
        parameterTypes.isNotEmpty() &&
        parameterTypes.size == parameterNames.size &&
        parameterTypes.all { isSupportedType(it) }

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
        PERMUTATION("순열 셔플 (1..N)", "Shuffled permutation (1..N)"),
        QUICKSORT_KILLER(
            "퀵정렬 킬러 (고전 단일피벗 O(n²) 저격 — introsort/TimSort는 무효)",
            "Quicksort killer (forces O(n²) on classic single-pivot sort — no effect on introsort/TimSort)"),
        ANTI_HASH(
            "anti-hash (mod 2^64 overflow 해시 충돌 — base 무관, 두 문자열 출력)",
            "anti-hash (collides mod-2^64 overflow polynomial hash — any base, outputs two strings)");

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

    // ─── 고급 탭: 생성 형식 DSL (이슈 #36) ───
    private val formatArea = javax.swing.JTextArea(com.codingtestkit.lang.CtkFormatDocs.STARTER_TEMPLATE, 12, 60).apply {
        font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, JBUI.scaleFontSize(12f))
    }
    private val tabs = com.intellij.ui.components.JBTabbedPane()

    /**
     * 고급 탭 전용 시드 입력칸.
     * Swing 컴포넌트는 부모가 하나뿐이라 같은 필드를 두 탭에 넣으면 나중 탭으로
     * 옮겨가며 앞 탭에서 사라진다 — 탭마다 별도 필드를 둔다.
     */
    private val advancedSeedField = JTextField("", 10)

    /** 고급 탭이 선택돼 있으면 DSL로 생성한다 */
    private fun isAdvanced(): Boolean = tabs.selectedIndex == 1

    /** 현재 탭의 시드 입력칸 */
    private fun activeSeedField(): JTextField = if (isAdvanced()) advancedSeedField else seedField

    override fun createCenterPanel(): JComponent {
        tabs.addTab(I18n.t("간단", "Simple"), createSimplePanel())
        tabs.addTab(I18n.t("고급 (형식)", "Advanced (format)"), createAdvancedPanel())
        return tabs
    }

    private fun createAdvancedPanel(): JComponent {
        val panel = JPanel(java.awt.BorderLayout(JBUI.scale(4), JBUI.scale(4)))
        panel.add(JLabel("<html>" + I18n.t(
            "글자·줄바꿈은 그대로, <code>이름(...)</code> 호출만 생성값으로 바뀝니다. " +
                "복잡한 입력 형식(케이스 수·n m·배열들)을 그대로 기술할 수 있습니다.",
            "Literals pass through; only <code>name(...)</code> calls are replaced. " +
                "Lets you describe multi-part input formats (case count, n m, arrays) directly."
        ) + "</html>"), java.awt.BorderLayout.NORTH)
        panel.add(com.intellij.ui.components.JBScrollPane(formatArea), java.awt.BorderLayout.CENTER)

        val south = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT))
        south.add(JLabel(I18n.t("시드 (비우면 랜덤):", "Seed (blank = random):")))
        south.add(advancedSeedField)
        val docsButton = javax.swing.JButton(I18n.t("문법 도움말", "Syntax help"))
        docsButton.addActionListener { com.codingtestkit.lang.ShowFormatDocsAction.showDocs(null) }
        south.add(docsButton)
        south.add(JLabel("<html><i>" + I18n.t(
            ".ctk-format 파일로 저장하면 구문 강조와 함께 편집할 수 있습니다.",
            "Save as a .ctk-format file to edit it with syntax highlighting."
        ) + "</i></html>").apply { foreground = com.intellij.ui.JBColor.GRAY })
        panel.add(south, java.awt.BorderLayout.SOUTH)
        return panel
    }

    private fun createSimplePanel(): JComponent {
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

        // 전 파라미터 자동 생성 모드 (리트코드 — API가 타입 제공)
        if (autoParams) {
            formatCombo.isEnabled = false     // 배열은 항상 리터럴 형식
            firstLineNCheck.isEnabled = false // 파라미터형에는 첫 줄 N 없음
            gbc.gridx = 0; gbc.gridwidth = 2
            val mapping = parameterNames.indices.joinToString(", ") { i ->
                "${parameterNames[i]} (${parameterTypes[i]})"
            }
            panel.add(JLabel("<html><b>${I18n.t(
                "✓ 전 파라미터 자동 생성: $mapping<br>" +
                    "배열은 N·범위·패턴 적용, 숫자는 범위 내 랜덤, 문자열은 길이 N (최대 10만).",
                "✓ Auto-generating all parameters: $mapping<br>" +
                    "Arrays use N/range/pattern; numbers are random in range; strings use length N (max 100k)."
            )}</b></html>").apply {
                foreground = com.intellij.ui.JBColor(java.awt.Color(46, 140, 60), java.awt.Color(90, 190, 100))
            }, gbc)
            gbc.gridy++
            gbc.gridwidth = 1
        }

        // 다중 파라미터 문제 안내: 생성 입력은 첫 파라미터 줄만 채움 (이슈 #36)
        if (!autoParams && parameterStyle && parameterNames.size > 1) {
            gbc.gridx = 0; gbc.gridwidth = 2
            panel.add(JLabel("<html><b>${I18n.t(
                "⚠ 이 문제의 파라미터: ${parameterNames.joinToString(", ")} (${parameterNames.size}개)<br>" +
                    "생성 입력은 첫 파라미터(${parameterNames.first()}) 줄만 채웁니다.<br>" +
                    "나머지 파라미터 값은 생성 후 카드의 입력에 줄로 직접 추가하세요.",
                "⚠ This problem's parameters: ${parameterNames.joinToString(", ")} (${parameterNames.size})<br>" +
                    "The generated input fills only the first parameter (${parameterNames.first()}).<br>" +
                    "Add the remaining parameter values as extra lines in the case input."
            )}</b></html>").apply {
                foreground = com.intellij.ui.JBColor(java.awt.Color(200, 120, 0), java.awt.Color(230, 160, 50))
            }, gbc)
            gbc.gridy++
            gbc.gridwidth = 1
        }

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
        // 고급 탭은 DSL 문법만 검사한다 (간단 탭의 숫자 필드는 무관)
        if (isAdvanced()) {
            if (advancedSeedField.text.isNotBlank() && advancedSeedField.text.trim().toLongOrNull() == null) {
                return com.intellij.openapi.ui.ValidationInfo(
                    I18n.t("시드가 숫자가 아닙니다", "Seed is not a number"), advancedSeedField)
            }
            val error = com.codingtestkit.service.TestFormatInterpreter.validate(formatArea.text)
            return error?.let { com.intellij.openapi.ui.ValidationInfo(it.message ?: "invalid format", formatArea) }
        }
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
        // 고급 탭 — DSL이 만든 결과 전체가 케이스 하나의 입력이다
        // (여러 케이스가 필요하면 형식 안에서 repeat로 표현한다)
        if (isAdvanced()) {
            val seed = activeSeedField().text.trim().toLongOrNull()
            return listOf(com.codingtestkit.service.TestFormatInterpreter.generate(formatArea.text, seed))
        }
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

        // anti-hash는 숫자 배열이 아니라 충돌하는 두 문자열 — 형식·타입 설정을 무시하고 특수 출력.
        // 플랫폼 인식: stdin형(코드포스/SWEA)은 두 줄, 파라미터형(리트코드/프로그래머스)은
        // 각 문자열을 따옴표 리터럴 케이스로 (함수 시그니처 문자열 파라미터 입력에 맞춤).
        if (pattern == Pattern.ANTI_HASH) {
            val (a, b) = TestCaseGenerators.thueMorseAntiHash(n)
            return if (parameterStyle) listOf("\"$a\"", "\"$b\"")  // 충돌하는 두 문자열, 각각 한 케이스
                   else (0 until cases).map { "$a\n$b" }           // stdin: 두 줄(A, B)
        }

        return (0 until cases).map { caseIdx ->
            // 시드 지정 시 케이스마다 파생 시드로 재현 가능 + 케이스 간 서로 다름
            val rng = if (baseSeed != null) Random(baseSeed + caseIdx) else Random.Default
            if (autoParams) {
                // 전 파라미터 자동 생성: 줄 = 파라미터 (리트코드 입력 형식)
                parameterTypes.joinToString("\n") { valueForType(it, rng, n, min, max, pattern) }
            } else buildString(n * 8) {
                if (format == OutputFormat.ARRAY) append('[')
                else if (firstLineNCheck.isSelected) append(n).append('\n')
                appendSeries(this, rng, sep, n, min, max, pattern)
                if (format == OutputFormat.ARRAY) append(']')
            }
        }
    }

    /** min==max 빈 범위·max+1 오버플로를 처리하는 범위 랜덤 */
    private fun randLong(rng: Random, min: Long, max: Long): Long =
        if (min == max) min else rng.nextLong(min, if (max == Long.MAX_VALUE) max else max + 1)

    /** 패턴에 따라 n개 값을 sep로 이어 붙임 */
    private fun appendSeries(sb: StringBuilder, rng: Random, sep: String, n: Int, min: Long, max: Long, pattern: Pattern) {
        when (pattern) {
            Pattern.RANDOM -> for (i in 0 until n) {
                if (i > 0) sb.append(sep)
                sb.append(randLong(rng, min, max))
            }
            Pattern.INCREASING -> for (i in 0 until n) {
                if (i > 0) sb.append(sep)
                sb.append(min + i)
            }
            Pattern.DECREASING -> for (i in 0 until n) {
                if (i > 0) sb.append(sep)
                sb.append(max - i)
            }
            Pattern.CONSTANT -> for (i in 0 until n) {
                if (i > 0) sb.append(sep)
                sb.append(min)
            }
            Pattern.PERMUTATION -> {
                val perm = (1..n).toMutableList()
                perm.shuffle(rng)
                for ((i, v) in perm.withIndex()) {
                    if (i > 0) sb.append(sep)
                    sb.append(v)
                }
            }
            Pattern.QUICKSORT_KILLER -> {
                // 고전 단일피벗 퀵정렬을 O(n²)로 모는 킬러 순열 (0..n-1)
                val killer = TestCaseGenerators.quicksortKiller(n)
                for ((i, v) in killer.withIndex()) {
                    if (i > 0) sb.append(sep)
                    sb.append(v)
                }
            }
            Pattern.ANTI_HASH -> { /* generateInputs에서 특수 처리 (문자열 쌍) */ }
        }
    }

    /** 파라미터 타입별 값 생성 (리트코드 입력 표기: 배열 [..], 문자열 "..", 숫자/불리언 리터럴) */
    private fun valueForType(type: String, rng: Random, n: Int, min: Long, max: Long, pattern: Pattern): String {
        val t = type.trim().lowercase()
        return when {
            t in INT_ARRAY_TYPES -> buildString(n * 8) {
                append('['); appendSeries(this, rng, ",", n, min, max, pattern); append(']')
            }
            t in INT_2D_TYPES -> TestCaseGenerators.int2dArray(n) { randLong(rng, min, max) }
            t in TREE_TYPES -> TestCaseGenerators.completeTree(n) { randLong(rng, min, max) }
            t in STRING_ARRAY_TYPES -> (0 until n).joinToString(",", "[", "]") { "\"${randomWord(rng, 5)}\"" }
            t in SCALAR_INT_TYPES -> randLong(rng, min, max).toString()
            t == "string" -> "\"${randomWord(rng, n.coerceAtMost(100_000))}\""
            t == "boolean" -> if (rng.nextBoolean()) "true" else "false"
            t == "double" -> (min.toDouble() + (max - min).toDouble() * rng.nextDouble()).toString()
            else -> "" // isSupportedType가 걸러서 도달하지 않음
        }
    }

    private fun randomWord(rng: Random, len: Int): String =
        buildString(len) { repeat(len) { append('a' + rng.nextInt(26)) } }
}
