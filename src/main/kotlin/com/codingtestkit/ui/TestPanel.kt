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
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class TestPanel(private val project: Project) : JPanel(BorderLayout()) {

    companion object {
        /** 이 길이를 넘는 입력은 카드에 미리보기만 표시 (렌더링·sync 보호, 이슈 #36) */
        private const val LARGE_INPUT_CHARS = 100_000

        /**
         * 문제 설명에서 "아무 답이나 출력" 류 문구를 감지하는 휴리스틱 (이슈 #36).
         * 감지되면 불일치를 FAIL 대신 '직접 확인(중립)'으로 완화한다 —
         * 오탐이 나도 FAIL→중립 방향의 완화라 해가 적다 (PASS 판정은 영향 없음).
         */
        fun detectsMultipleAnswers(description: String): Boolean {
            val d = description.lowercase()
            return listOf(
                "any of them", "any one of them", "print any", "output any",
                // LeetCode는 print 대신 return 표현 + "in any order"가 관용구
                "return any", "in any order",
                "multiple valid answer", "multiple answers", "multiple solutions",
                "multiple correct", "any valid answer", "any correct answer",
                "여러 개일 경우", "여러 개인 경우", "여러 가지일 경우", "아무 것이나", "아무거나"
            ).any { it in d }
        }

        private fun largeInputPreview(input: String): String =
            input.take(500) + "\n… " + I18n.t(
                "[총 %,d자 — 대량 입력은 표시를 생략하며 실행 시 원본을 사용합니다]",
                "[%,d chars total — large input is truncated for display; the original is used when running]"
            ).format(input.length)
    }

    private val languageCombo = ComboBox(Language.entries.map { it.displayName }.toTypedArray())
    private val runButton = JButton(I18n.t("전체 실행", "Run All"), AllIcons.Actions.Execute).apply {
        toolTipText = I18n.t("모든 테스트 케이스를 실행합니다", "Run all test cases")
    }
    private val addButton = JButton(AllIcons.General.Add).apply {
        toolTipText = I18n.t("테스트 케이스 추가", "Add test case")
        preferredSize = Dimension(JBUI.scale(28), JBUI.scale(28))
        horizontalAlignment = SwingConstants.CENTER
        margin = JBUI.emptyInsets()
    }
    private val generateButton = JButton(I18n.t("생성", "Generate"), AllIcons.Actions.AddList).apply {
        toolTipText = I18n.t("대량 테스트 케이스 생성 (랜덤/정렬/순열)", "Generate large test cases (random/sorted/permutation)")
    }
    private val checkerButton = JButton(I18n.t("체커", "Checker"), AllIcons.Actions.Checked).apply {
        toolTipText = I18n.t(
            "커스텀 체커 설정 — 복수 정답 문제를 문자열 비교 대신 체커 프로그램으로 판정",
            "Set a custom checker — judge multiple-valid-answer problems with a program instead of string comparison"
        )
    }

    // EDT에서 쓰고 실행(풀 스레드)에서 읽으므로 @Volatile로 가시성 보장
    /** 커스텀 체커 (세션 레벨, 이슈 #36). null이면 미사용 → 문자열 비교/중립 판정 */
    @Volatile
    private var checkerCode: String? = null
    @Volatile
    private var checkerLanguage: Language = Language.PYTHON

    /** 복수 정답 허용 문제 힌트 (문제 설명에서 자동 감지, 이슈 #36) */
    @Volatile
    private var multipleAnswersAllowed = false

    fun setMultipleAnswersHint(allowed: Boolean) {
        multipleAnswersAllowed = allowed
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
    private var problemSource = ProblemSource.PROGRAMMERS
    private var parameterNames = listOf<String>()

    /** 언어 선택 변경 알림 (Problems 탭과 양방향 동기화용) */
    var onLanguageChanged: ((Language) -> Unit)? = null

    /** 외부(Problems 탭)에서 언어 동기화. 같은 값이면 무시해 콜백 루프를 끊는다. */
    fun setLanguage(language: Language) {
        val idx = Language.entries.indexOf(language)
        if (idx >= 0 && languageCombo.selectedIndex != idx) {
            languageCombo.selectedIndex = idx
        }
    }

    init {
        border = JBUI.Borders.empty()

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6, 8, 4, 8)
        }

        // Row 1: 언어 + 실행 버튼 + 추가/삭제
        val buttonRow = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        buttonRow.add(JLabel(I18n.t("언어:", "Lang:")).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
            foreground = JBColor.GRAY
        })
        buttonRow.add(languageCombo)
        languageCombo.addActionListener { onLanguageChanged?.invoke(getSelectedLanguage()) }
        buttonRow.add(runButton)
        buttonRow.add(addButton)
        buttonRow.add(generateButton)
        buttonRow.add(checkerButton)
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
        generateButton.addActionListener { openGeneratorDialog() }
        checkerButton.addActionListener { openCheckerDialog() }

        // 키보드 단축키 액션 등록
        com.codingtestkit.service.CodingTestKitActionService.getInstance(project).runAllAction = {
            runButton.doClick()
        }
    }

    fun setTestCases(cases: List<TestCase>) {
        testCases = cases.toMutableList()
        rebuildCards()
    }

    fun setProblemSource(source: ProblemSource) {
        problemSource = source
    }

    private var parameterTypes: List<String> = emptyList()

    fun setParameterTypes(types: List<String>) {
        parameterTypes = types
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

    /** 체커 설정 다이얼로그 (이슈 #36). 코드를 비우고 저장하면 해제 */
    private fun openCheckerDialog() {
        val dialog = CheckerDialog(checkerLanguage, checkerCode ?: "")
        if (!dialog.showAndGet()) return
        checkerLanguage = dialog.getLanguage()
        checkerCode = dialog.getCode().ifBlank { null }
        checkerButton.text = if (checkerCode != null) {
            I18n.t("체커 ✓", "Checker ✓")
        } else {
            I18n.t("체커", "Checker")
        }
    }

    /** 생성 다이얼로그를 열고, 생성된 입력들을 예상 출력 없는 케이스로 추가 (이슈 #36) */
    private fun openGeneratorDialog() {
        // 파라미터형 플랫폼(리트코드/프로그래머스)은 배열 리터럴 형식을 기본으로
        val parameterStyle = problemSource == ProblemSource.PROGRAMMERS || problemSource == ProblemSource.LEETCODE
        // 타입 정보가 없으면(프로그래머스 — API가 타입 미제공) 첫 예제 케이스의 값 모양에서 추론
        val types = parameterTypes.ifEmpty { inferTypesFromFirstCase() }
        val dialog = TestCaseGeneratorDialog(parameterStyle, parameterNames, types)
        if (!dialog.showAndGet()) return
        syncCardsToTestCases() // 기존 카드 편집 내용 보존
        for (input in dialog.generateInputs()) {
            testCases.add(TestCase(input = input, expectedOutput = ""))
        }
        rebuildCards()
        updateInfoLabel()
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

    /**
     * 첫 예제 케이스의 값 모양에서 파라미터 타입 추론 (이슈 #36).
     * 프로그래머스는 API가 타입을 주지 않지만 예제가 항상 있으므로,
     * 각 줄의 리터럴 형태([..]=배열, ".."=문자열, 숫자, true/false)로 판별한다.
     * 판별 불가(2차원 배열 등)면 빈 문자열 → 생성기가 안내 모드로 폴백.
     */
    private fun inferTypesFromFirstCase(): List<String> {
        val first = testCases.firstOrNull() ?: return emptyList()
        val lines = first.input.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty() || lines.size != parameterNames.size) return emptyList()
        return lines.map { v ->
            when {
                v.startsWith("[[") -> ""  // 2차원 — v1 미지원
                v.startsWith("[") -> if (v.contains("\"")) "string[]" else "integer[]"
                v.startsWith("\"") -> "string"
                v == "true" || v == "false" -> "boolean"
                v.toLongOrNull() != null -> "integer"
                v.toDoubleOrNull() != null -> "double"
                else -> ""
            }
        }
    }

    /** 실행 중 중복 실행 방지 (Run All / 개별 실행 공용) */
    @Volatile
    private var running = false

    /**
     * 실행 전 공통 검증: 실행 중이 아니고, 코드와 케이스가 있어야 한다.
     * 통과하면 실행할 코드를 반환, 아니면 경고 표시 후 null.
     */
    private fun prepareRun(): String? {
        if (running) return null
        val code = getCurrentCode()
        if (code.isBlank()) {
            warnStatus(I18n.t("코드 없음", "No code"))
            return null
        }
        // 카드에서 수정된 값 반영
        syncCardsToTestCases()
        if (testCases.isEmpty()) {
            warnStatus(I18n.t("케이스 없음", "No cases"))
            return null
        }
        return code
    }

    private fun warnStatus(text: String) {
        statusLabel.text = text
        statusLabel.icon = AllIcons.General.Warning
        statusLabel.foreground = JBColor(Color(200, 120, 0), Color(230, 160, 50))
    }

    private fun setRunningStatus() {
        statusLabel.text = I18n.t("실행 중...", "Running...")
        statusLabel.icon = AllIcons.Process.Step_1
        statusLabel.foreground = JBColor.foreground()
    }

    /** 실행 결과 + 체커 메시지 (카드 디버그 영역 표시용) */
    private class ExecOutcome(
        val result: CodeRunner.RunResult,
        val checkerMessage: String? = null
    )

    /**
     * 케이스 하나를 실행하고 판정을 tc에 반영한다. 백그라운드 스레드에서 호출할 것.
     * 판정 우선순위: 실행 에러 → 커스텀 체커 → 문자열 비교 → (예상 출력 없음) 중립.
     */
    private fun executeCase(code: String, language: Language, tc: TestCase): ExecOutcome {
        val result = if (problemSource == ProblemSource.PROGRAMMERS || problemSource == ProblemSource.LEETCODE) {
            CodeRunner.runProgrammers(code, language, tc, parameterNames)
        } else {
            CodeRunner.run(code, language, tc)
        }

        if (result.exitCode != 0 && result.error.isNotBlank()) {
            tc.actualOutput = result.error
            tc.passed = false
            return ExecOutcome(result)
        }

        val stdout = result.output.trim()
        val expected = tc.expectedOutput.trim()
        tc.actualOutput = stdout

        val checker = checkerCode
        if (!checker.isNullOrBlank()) {
            // 커스텀 체커 판정 (복수 정답 문제, 이슈 #36)
            val (ok, message) = runChecker(checker, tc, stdout)
            tc.passed = ok
            return ExecOutcome(result, message)
        }

        if (expected.isBlank()) {
            // 예상 출력이 없으면 판정하지 않는 중립 실행 (이슈 #36):
            // 대량 생성 케이스로 TLE/메모리만 확인하는 용도. passed=null 유지.
            tc.passed = null
        } else {
            // 비교용 정규화: trailing whitespace + 배열 내부 공백 통일
            fun normalize(s: String): String = s
                .lines().joinToString("\n") { it.trimEnd() }.trimEnd()
                .replace(Regex("""\s*,\s*"""), ",")  // [0, 1] → [0,1]
                .replace(Regex("""\[\s+"""), "[")
                .replace(Regex("""\s+]"""), "]")
            val matched = normalize(stdout) == normalize(expected)
            tc.passed = when {
                matched -> true
                // 복수 정답 허용 문제면 불일치를 FAIL이 아닌 '직접 확인(중립)'으로 (이슈 #36)
                multipleAnswersAllowed -> null
                else -> false
            }
        }
        return ExecOutcome(result)
    }

    /**
     * 체커 실행: stdin으로 입력/사용자출력/예상출력을 ===CTK=== 구분으로 전달하고,
     * 체커 stdout 첫 줄이 OK/AC면 통과. 체커 자체가 실패하면 불통과 + 에러 메시지.
     */
    private fun runChecker(checkerSrc: String, tc: TestCase, userOut: String): Pair<Boolean, String> {
        val payload = buildString {
            append(tc.input.trimEnd()).append('\n')
            append("===CTK===\n")
            append(userOut.trimEnd()).append('\n')
            append("===CTK===\n")
            append(tc.expectedOutput.trimEnd()).append('\n')
        }
        val res = CodeRunner.run(
            checkerSrc, checkerLanguage,
            TestCase(input = payload, expectedOutput = ""), timeoutSeconds = 10
        )
        if (res.exitCode != 0 && res.error.isNotBlank()) {
            return false to I18n.t("체커 실행 실패: ", "Checker failed: ") + res.error.trim()
        }
        val out = res.output.trim()
        val firstLine = out.lineSequence().firstOrNull()?.trim()?.uppercase() ?: ""
        val ok = firstLine == "OK" || firstLine == "AC" || firstLine.startsWith("OK ")
        return ok to out
    }

    /** 실행됐지만 판정이 없는 중립 케이스인가 (예상 출력 없음 또는 복수 정답 불일치, 이슈 #36) */
    private fun isNeutralRan(tc: TestCase): Boolean =
        tc.passed == null && tc.actualOutput.isNotBlank()

    /** Build 창에 게시할 진단인가: 컴파일 에러 또는 인터프리터 문법 에러(시작 실패) */
    private fun shouldPublishDiagnostics(result: CodeRunner.RunResult, language: Language): Boolean =
        result.compileError ||
            (result.exitCode != 0 && com.codingtestkit.service.BuildOutputPublisher.isStartupError(language, result.error))

    /**
     * 컴파일/문법 진단을 IDE Build Output 창에 게시 (이슈 #32).
     * 백그라운드 스레드에서 호출 가능 (BuildViewManager 이벤트는 스레드 세이프).
     */
    private fun publishCompileError(result: CodeRunner.RunResult, language: Language) {
        val vf = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
            .selectedFiles.firstOrNull()
        val wrapperStyle = problemSource == ProblemSource.PROGRAMMERS || problemSource == ProblemSource.LEETCODE
        com.codingtestkit.service.BuildOutputPublisher.publishCompileError(
            project, language, result.error,
            vf?.let { java.io.File(it.path) }, wrapperStyle
        )
    }

    /** 실행 결과를 해당 카드에 반영 (EDT에서 호출할 것) */
    private fun applyResultToCard(index: Int, tc: TestCase, outcome: ExecOutcome) {
        if (index >= cards.size) return
        val result = outcome.result
        // 체커 메시지 먼저 (setDebugOutput은 덮어쓰기, setStderrOutput은 이어붙이기)
        outcome.checkerMessage?.takeIf { it.isNotBlank() }?.let {
            cards[index].setDebugOutput("[checker] $it")
        }
        // 정상 종료인데 stderr가 있으면 디버그 출력으로 표시
        if (result.exitCode == 0 && result.error.isNotBlank()) {
            cards[index].setStderrOutput(result.error.trim())
        }
        cards[index].setResult(tc, result.executionTimeMs, result.peakMemoryKB)
    }

    /** 실행된 케이스 기준으로 상태 요약 갱신 */
    private fun updateSummary() {
        val pass = testCases.count { it.passed == true }
        val fail = testCases.count { it.passed == false }
        val neutral = testCases.count { isNeutralRan(it) }
        val green = JBColor(Color(46, 160, 67), Color(80, 200, 80))
        val red = JBColor(Color(218, 54, 51), Color(230, 80, 80))

        if (neutral > 0) {
            // 판정 없는(중립) 케이스가 섞이면 pass/total 형식이 오해를 부르므로 분리 표기
            statusLabel.text = I18n.t(
                "통과 $pass · 실패 $fail · 판정없음 $neutral",
                "pass $pass · fail $fail · no verdict $neutral"
            )
            when {
                fail > 0 -> { statusLabel.icon = AllIcons.General.Error; statusLabel.foreground = red }
                pass > 0 -> { statusLabel.icon = AllIcons.General.InspectionsOK; statusLabel.foreground = green }
                else -> { statusLabel.icon = AllIcons.General.Information; statusLabel.foreground = JBColor.foreground() }
            }
        } else {
            val allPassed = testCases.isNotEmpty() && pass == testCases.size
            statusLabel.text = "$pass / ${testCases.size} ${I18n.t("통과", "passed")}"
            statusLabel.icon = if (allPassed) AllIcons.General.InspectionsOK else AllIcons.General.Error
            statusLabel.foreground = if (allPassed) green else red
        }
    }

    private fun runAllTests() {
        val code = prepareRun() ?: return
        val language = getSelectedLanguage()
        running = true
        runButton.isEnabled = false
        setRunningStatus()

        // 모든 카드 상태 초기화
        for (card in cards) card.setRunning()

        ApplicationManager.getApplication().executeOnPooledThread {
            var compileErrorPublished = false
            for ((i, tc) in testCases.withIndex()) {
                val result = executeCase(code, language, tc)
                // 컴파일/문법 에러는 케이스마다 동일하므로 첫 실패만 Build 창에 게시 (이슈 #32)
                if (!compileErrorPublished && shouldPublishDiagnostics(result.result, language)) {
                    compileErrorPublished = true
                    publishCompileError(result.result, language)
                }
                val idx = i
                SwingUtilities.invokeLater { applyResultToCard(idx, tc, result) }
            }
            SwingUtilities.invokeLater {
                running = false
                runButton.isEnabled = true
                updateSummary()
            }
        }
    }

    /**
     * 케이스 하나를 IDE 디버거로 실행 (카드의 🐞 버튼, 이슈 #36).
     * 언어에 맞는 TestDebugAdapter(EP)를 찾아 디버그 서버에 attach한다.
     * 어댑터가 없으면(그 언어의 디버거가 이 IDE에 없음) 맞는 IDE를 안내한다.
     */
    private fun debugTestAt(index: Int) {
        if (running) return
        val code = getCurrentCode()
        if (code.isBlank()) { warnStatus(I18n.t("코드 없음", "No code")); return }
        syncCardsToTestCases()
        val tc = testCases.getOrNull(index) ?: return
        val language = getSelectedLanguage()

        val adapter = com.codingtestkit.debug.TestDebugAdapter.forLanguage(language)
        if (adapter == null) {
            val all = com.codingtestkit.debug.TestDebugAdapter.EP_NAME.extensionList
            com.intellij.openapi.diagnostic.Logger.getInstance(TestPanel::class.java).warn(
                "[CodingTestKit] no debug adapter for $language; loaded adapters=" +
                    all.joinToString { "${it.javaClass.simpleName}(supports=${it.supports(language)},avail=${runCatching { it.isAvailable() }.getOrNull()})" })
            Messages.showInfoMessage(project, debugUnsupportedMessage(language), "CodingTestKit")
            return
        }

        // Go 등 네이티브 디버깅은 소스 파일 경로로 브레이크포인트를 바인딩하므로
        // 에디터 문서를 디스크에 저장하고 실제 파일 경로를 넘긴다
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val userFile = editor?.let {
            val fdm = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
            // saveDocument는 write-intent 잠금 필요 — 마우스 리스너의 EDT는 잠금이 없어
            // (2026.1부터 엄격) runWriteAction으로 감싼다
            ApplicationManager.getApplication().runWriteAction {
                fdm.saveDocument(it.document)
            }
            fdm.getFile(it.document)?.toNioPath()?.toFile()
        }

        val sessionName = "CodingTestKit #${index + 1}"

        // 실행-소유(Python/Rust/C++): IDE가 프로그램을 처음부터 디버거 아래에서 실행.
        // 케이스 입력은 실행 구성의 입력 리다이렉션으로 전달돼 attach 레이스가 없다.
        if (adapter.ownsLaunch()) {
            if (userFile == null || !userFile.exists()) {
                Messages.showWarningDialog(project, I18n.t(
                    "디버깅하려면 소스 파일을 먼저 저장하세요.", "Please save the source file before debugging."), "CodingTestKit")
                return
            }
            statusLabel.icon = AllIcons.Actions.StartDebugger
            statusLabel.text = I18n.t("디버거 시작 중...", "Starting debugger...")
            running = true
            setRunningStatus()
            ApplicationManager.getApplication().executeOnPooledThread {
                // C++는 -g -O0 바이너리를 미리 빌드해 어댑터에 넘긴다 (수 초 걸릴 수 있어 백그라운드)
                val artifact = if (language == Language.CPP) CodeRunner.prepareCppDebugBinary(code, userFile) else null
                SwingUtilities.invokeLater {
                    running = false
                    runButton.isEnabled = true
                    if (artifact != null && !artifact.ok) {
                        statusLabel.icon = AllIcons.General.Warning
                        statusLabel.text = ""
                        Messages.showWarningDialog(project, artifact.errorMessage
                            ?: I18n.t("디버깅을 시작할 수 없습니다.", "Cannot start debugging."), "CodingTestKit")
                        return@invokeLater
                    }
                    val ok = adapter.launchDebug(project, sessionName, userFile, tc.input, userFile.parentFile, artifact?.file)
                    if (!ok) {
                        statusLabel.text = ""
                        Messages.showWarningDialog(project, I18n.t(
                            "디버깅을 시작할 수 없습니다. 언어 도구(인터프리터/툴체인)가 설정돼 있는지 확인하세요.",
                            "Cannot start debugging. Make sure the language toolchain is configured."), "CodingTestKit")
                    } else {
                        statusLabel.text = I18n.t("디버그 세션 시작됨", "Debug session started")
                    }
                }
            }
            return
        }

        // 정방향(JVM/Go): 프로세스가 디버그 서버로 대기 → IDE가 attach
        running = true
        setRunningStatus()
        ApplicationManager.getApplication().executeOnPooledThread {
            val handle = CodeRunner.startDebug(code, language, tc, parameterNames, problemSource, userFile)
            SwingUtilities.invokeLater {
                finishDebugLaunch(handle) { adapter.attachToPort(project, sessionName, handle.port) }
            }
        }
    }

    /**
     * 디버그 실행 마무리 공통 처리: 실패 안내, attach(정방향만), 프로세스 종료 시 임시 파일 정리.
     * @param attach 정방향 어댑터의 attach 동작 (역방향은 이미 listen 중이므로 null)
     */
    private fun finishDebugLaunch(handle: CodeRunner.DebugHandle, attach: (() -> Boolean)?) {
        running = false
        runButton.isEnabled = true
        if (!handle.ok) {
            statusLabel.icon = AllIcons.General.Warning
            statusLabel.text = ""
            Messages.showWarningDialog(project, handle.errorMessage
                ?: I18n.t("디버깅을 시작할 수 없습니다.", "Cannot start debugging."), "CodingTestKit")
            return
        }
        if (attach != null) {
            statusLabel.icon = AllIcons.Actions.StartDebugger
            statusLabel.text = I18n.t("디버거 연결 중...", "Attaching debugger...")
            if (!attach()) {
                handle.cleanup()
                Messages.showWarningDialog(project, I18n.t(
                    "디버거 연결에 실패했습니다.", "Failed to attach the debugger."), "CodingTestKit")
                statusLabel.text = ""
                return
            }
        }
        // 프로세스가 끝나면 임시 파일 정리 (디버그 세션이 잡고 있던 리소스)
        ApplicationManager.getApplication().executeOnPooledThread {
            try { handle.process?.waitFor() } catch (_: Exception) {}
            handle.cleanup()
        }
        statusLabel.icon = AllIcons.Actions.StartDebugger
        statusLabel.text = I18n.t("디버그 세션 시작됨", "Debug session started")
    }

    /** 현재 IDE에 해당 언어 디버그 어댑터가 없을 때, 어느 IDE에서 되는지 안내 */
    private fun debugUnsupportedMessage(language: Language): String {
        val ide = when (language) {
            Language.JAVA, Language.KOTLIN -> "IntelliJ IDEA"
            Language.GO -> "GoLand"
            Language.RUST -> "RustRover"
            Language.CPP -> "CLion"
            Language.RUBY -> "RubyMine"
            Language.PYTHON -> "PyCharm"
            Language.JAVASCRIPT -> "WebStorm"
        }
        return I18n.t(
            "이 IDE에서는 ${language.displayName} 디버깅을 사용할 수 없습니다.\n" +
                "${language.displayName} 디버깅은 $ide 에서 지원됩니다.",
            "${language.displayName} debugging is not available in this IDE.\n" +
                "It is supported in $ide."
        )
    }

    /** 케이스 하나만 실행 (카드의 ▶ 버튼, 이슈 #36) */
    private fun runTestAt(index: Int) {
        val code = prepareRun() ?: return
        if (index !in testCases.indices) return
        val language = getSelectedLanguage()
        running = true
        runButton.isEnabled = false
        setRunningStatus()
        cards.getOrNull(index)?.setRunning()

        ApplicationManager.getApplication().executeOnPooledThread {
            val tc = testCases[index]
            val result = executeCase(code, language, tc)
            if (shouldPublishDiagnostics(result.result, language)) publishCompileError(result.result, language)
            SwingUtilities.invokeLater {
                applyResultToCard(index, tc, result)
                running = false
                runButton.isEnabled = true
                updateSummary()
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

        /**
         * 생성된 대량 입력은 에디터에 그대로 넣으면 렌더링이 느려지고,
         * 잘라서 표시하면 sync 시 원본이 파괴되므로: 미리보기 + 읽기전용으로 두고
         * getInput()이 모델 원본을 반환한다 (이슈 #36).
         */
        private val inputTooLarge = testCase.input.length > LARGE_INPUT_CHARS
        private val inputArea = JTextArea(
            if (inputTooLarge) largeInputPreview(testCase.input) else testCase.input, 3, 20
        ).apply {
            font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
            border = JBUI.Borders.empty(4)
            lineWrap = true
            if (inputTooLarge) {
                isEditable = false
                foreground = JBColor.GRAY
            }
        }
        private val expectedArea = JTextArea(testCase.expectedOutput, 3, 20).apply {
            font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
            border = JBUI.Borders.empty(4)
            lineWrap = true
        }
        private val actualArea = object : JTextPane() {
            override fun getScrollableTracksViewportWidth() = true
        }.apply {
            font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
            border = JBUI.Borders.empty(4)
            isEditable = false
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
        private lateinit var actualFieldPanel: JPanel

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
            // take()를 먼저 해서 대량 입력에서도 문자열 전체 복사가 일어나지 않게 함
            val inputFirst = testCase.input.take(200).lineSequence().first().trim()
            val outputFirst = testCase.expectedOutput.take(200).lineSequence().first().trim()
            val inPreview = if (inputFirst.length > 20) inputFirst.take(20) + "…" else inputFirst
            val outPreview = if (outputFirst.length > 20) outputFirst.take(20) + "…" else outputFirst
            val parts = mutableListOf<String>()
            if (inPreview.isNotBlank()) parts.add("${I18n.t("입력", "In")}: $inPreview")
            if (outPreview.isNotBlank()) parts.add("${I18n.t("출력", "Out")}: $outPreview")
            if (parts.isNotEmpty()) {
                summaryLabel.text = parts.joinToString("  |  ")
            }
            headerPanel.add(summaryLabel, BorderLayout.CENTER)

            // 개별 실행 버튼 (이슈 #36) — 자체 리스너를 가져 헤더 토글과 분리됨
            val runBtn = JLabel(AllIcons.Actions.Execute).apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                toolTipText = I18n.t("이 케이스만 실행", "Run this case only")
                border = JBUI.Borders.empty(0, 4)
            }
            runBtn.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    val idx = cards.indexOf(this@TestCaseCard)
                    if (idx >= 0) runTestAt(idx)
                }
            })

            // 디버그 버튼 (이슈 #36 Tier 1) — ▶ 옆, tanmay 스케치 위치
            val debugBtn = JLabel(AllIcons.Actions.StartDebugger).apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                toolTipText = I18n.t("이 케이스로 디버그 (Java/Kotlin)", "Debug this case (Java/Kotlin)")
                border = JBUI.Borders.empty(0, 4)
            }
            debugBtn.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    val idx = cards.indexOf(this@TestCaseCard)
                    if (idx >= 0) debugTestAt(idx)
                }
            })

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

            val headerButtons = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply { isOpaque = false }
            headerButtons.add(runBtn)
            headerButtons.add(debugBtn)
            headerButtons.add(deleteBtn)
            headerPanel.add(headerButtons, BorderLayout.EAST)

            add(headerPanel, BorderLayout.NORTH)

            // 콘텐츠 (펼쳐졌을 때)
            contentPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = JBUI.Borders.empty(4, 8, 8, 8)
                isVisible = false
            }

            contentPanel.add(createFieldPanel(I18n.t("입력", "Input"), inputArea))
            contentPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
            contentPanel.add(createFieldPanel(I18n.t("예상 출력 (비우면 판정 없이 실행만)", "Expected (empty = run without verdict)"), expectedArea))
            contentPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
            // 실제 출력은 첫 실행 전까지 숨김 (빈 박스가 자리만 차지, 이슈 #36 피드백)
            actualFieldPanel = createFieldPanel(I18n.t("실제 출력", "Actual"), actualArea)
            actualFieldPanel.isVisible = testCase.actualOutput.isNotBlank()
            contentPanel.add(actualFieldPanel)

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

        private fun createFieldPanel(label: String, textArea: JComponent): JPanel {
            var fieldHeight = JBUI.scale(100)

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
                preferredSize = Dimension(0, JBUI.scale(5))
                minimumSize = Dimension(0, JBUI.scale(5))
                cursor = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
                background = JBColor(Color(190, 195, 200), Color(75, 78, 82))
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
            var panelHeight = JBUI.scale(80)

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
                preferredSize = Dimension(0, JBUI.scale(5))
                minimumSize = Dimension(0, JBUI.scale(5))
                cursor = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
                background = JBColor(Color(190, 195, 200), Color(75, 78, 82))
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

        fun getInput(): String = if (inputTooLarge) testCase.input else inputArea.text
        fun getExpected(): String = expectedArea.text

        fun setRunning() {
            statusIcon.icon = AllIcons.Process.Step_1
            titleLabel.text = "#$number ${I18n.t("실행 중...", "Running...")}"
            actualArea.text = ""
            actualArea.foreground = defaultActualFg
            // 첫 실행이 시작되면 실제 출력 박스 표시
            if (!actualFieldPanel.isVisible) {
                actualFieldPanel.isVisible = true
                updateMaxSize()
            }
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

            // Diff 하이라이팅: FAIL이고 에러가 아닌 경우 줄/글자 단위 차이 표시
            if (tc.passed == false && !isError) {
                applyDiffHighlighting(tc.expectedOutput, tc.actualOutput)
            }

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
                    // setResult는 실행 직후에만 호출되므로 null = '실행됐지만 판정 없음'(중립).
                    // stdout이 비어도(출력 없는 프로그램) '실행됨'으로 표시한다.
                    statusIcon.icon = AllIcons.General.Information
                    val label = if (tc.expectedOutput.isBlank()) {
                        I18n.t("실행됨", "DONE")
                    } else {
                        I18n.t("직접 확인 (복수 정답 가능)", "CHECK (multiple answers possible)")
                    }
                    titleLabel.text = "#$number $label$timeStr"
                    titleLabel.foreground = JBColor(Color(70, 110, 180), Color(120, 160, 230))
                    headerPanel.background = JBColor(Color(238, 243, 250), Color(42, 46, 55))
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

        private fun applyDiffHighlighting(expected: String, @Suppress("UNUSED_PARAMETER") actual: String) {
            val doc = actualArea.styledDocument
            val root = doc.defaultRootElement
            val expectedLines = expected.trimEnd().replace("\r", "").lines().map { it.trimEnd() }

            val lineDiffBg = JBColor(Color(255, 230, 230), Color(70, 40, 40))
            val charDiffBg = JBColor(Color(255, 180, 180), Color(110, 50, 50))

            for (i in 0 until root.elementCount) {
                val elem = root.getElement(i)
                val start = elem.startOffset
                val end = elem.endOffset.coerceAtMost(doc.length)
                if (end <= start) continue
                val lineText = doc.getText(start, end - start).trimEnd('\n', '\r')
                val expectedLine = expectedLines.getOrNull(i)?.trimEnd()

                if (expectedLine == null || lineText != expectedLine) {
                    // 전체 줄: 연한 빨간 배경
                    val lineAttrs = javax.swing.text.SimpleAttributeSet()
                    javax.swing.text.StyleConstants.setBackground(lineAttrs, lineDiffBg)
                    doc.setCharacterAttributes(start, end - start, lineAttrs, false)

                    // 글자 단위: 다른 부분만 진한 빨간 배경
                    if (expectedLine != null && lineText.isNotEmpty()) {
                        val commonPrefix = lineText.zip(expectedLine).takeWhile { (a, b) -> a == b }.size
                        val commonSuffix = lineText.reversed().zip(expectedLine.reversed())
                            .takeWhile { (a, b) -> a == b }.size
                            .coerceAtMost(lineText.length - commonPrefix)
                            .coerceAtMost(expectedLine.length - commonPrefix)
                        val diffStart = commonPrefix
                        val diffEnd = lineText.length - commonSuffix
                        if (diffEnd > diffStart) {
                            val charAttrs = javax.swing.text.SimpleAttributeSet()
                            javax.swing.text.StyleConstants.setBackground(charAttrs, charDiffBg)
                            doc.setCharacterAttributes(start + diffStart, diffEnd - diffStart, charAttrs, false)
                        }
                    }
                }
            }
        }
    }

}
