package com.codingtestkit.ui

import com.codingtestkit.model.Language
import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.*

class ProblemPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val sourceCombo = ComboBox(ProblemSource.entries.map { it.displayName }.toTypedArray()).apply {
        preferredSize = Dimension(JBUI.scale(100), preferredSize.height)
    }
    private val languageCombo = ComboBox(Language.entries.map { it.displayName }.toTypedArray()).apply {
        preferredSize = Dimension(JBUI.scale(90), preferredSize.height)
    }
    private val problemIdField = JTextField().apply {
        toolTipText = "문제 번호 또는 URL을 입력하세요"
    }
    private val fetchButton = JButton("가져오기", AllIcons.Actions.Download).apply {
        toolTipText = "문제를 가져옵니다"
    }
    private val loginButton = JButton("로그인", AllIcons.General.User)
    private val submitButton = JButton("제출", AllIcons.Actions.Upload).apply {
        toolTipText = "현재 에디터의 코드를 제출합니다"
    }
    private val loginStatusLabel = JLabel("").apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
    }
    private val problemDisplay = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        border = JBUI.Borders.empty(8)
        text = "<html><body style='font-family:sans-serif; padding:8px; color:#999;'>" +
                "<p style='text-align:center; margin-top:40px;'>문제 번호를 입력하고<br>'가져오기'를 클릭하세요</p>" +
                "</body></html>"
    }

    var onProblemFetched: ((Problem) -> Unit)? = null
    private var currentProblem: Problem? = null
    private var currentProblemFolder: java.io.File? = null

    init {
        border = JBUI.Borders.empty()

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6, 8, 4, 8)
        }

        // Row 1: 플랫폼 + 언어
        val row1 = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        row1.add(createLabel("플랫폼"))
        row1.add(sourceCombo)
        row1.add(Box.createHorizontalStrut(JBUI.scale(4)))
        row1.add(createLabel("언어"))
        row1.add(languageCombo)
        topPanel.add(row1)
        topPanel.add(Box.createVerticalStrut(JBUI.scale(4)))

        // Row 2: 문제번호 입력 + 가져오기
        val row2 = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(32))
        }
        row2.add(createLabel(" 번호 "), BorderLayout.WEST)
        row2.add(problemIdField, BorderLayout.CENTER)
        row2.add(fetchButton, BorderLayout.EAST)
        topPanel.add(row2)
        topPanel.add(Box.createVerticalStrut(JBUI.scale(4)))

        // Row 3: 로그인 + 상태 + 제출
        val row3 = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        row3.add(loginButton)
        row3.add(loginStatusLabel)
        row3.add(Box.createHorizontalStrut(JBUI.scale(8)))
        row3.add(submitButton)
        topPanel.add(row3)

        add(topPanel, BorderLayout.NORTH)

        // 문제 표시 영역
        val scrollPane = JBScrollPane(problemDisplay).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
        }
        add(scrollPane, BorderLayout.CENTER)

        submitButton.isEnabled = false
        updateLoginButton()

        // 이벤트
        sourceCombo.addActionListener {
            updateLoginButton()
            updatePlaceholder()
        }
        fetchButton.addActionListener { fetchProblem() }
        loginButton.addActionListener { handleLogin() }
        submitButton.addActionListener { submitSolution() }
        problemIdField.addActionListener { fetchProblem() }
        updatePlaceholder()
    }

    private fun updatePlaceholder() {
        val source = getSelectedSource()
        val (placeholder, tooltip) = when (source) {
            ProblemSource.BAEKJOON -> "예: 1000" to "백준 문제 번호 (URL의 /problem/1000 에서 1000)"
            ProblemSource.PROGRAMMERS -> "예: 12947 (URL의 lessons/뒤 숫자)" to "프로그래머스: URL이 /lessons/12947 이면 12947 입력"
            ProblemSource.SWEA -> "예: 2001 또는 URL 붙여넣기" to "SWEA: 문제 번호 또는 URL을 입력하세요"
        }
        problemIdField.putClientProperty("JTextField.placeholderText", placeholder)
        problemIdField.toolTipText = tooltip
    }

    private fun createLabel(text: String): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(11f).toFloat())
            foreground = JBColor.GRAY
        }
    }

    private fun getSelectedSource(): ProblemSource = ProblemSource.entries[sourceCombo.selectedIndex]
    private fun getSelectedLanguage(): Language = Language.entries[languageCombo.selectedIndex]

    private fun updateLoginButton() {
        val source = getSelectedSource()
        val auth = AuthService.getInstance()
        if (auth.isLoggedIn(source)) {
            loginButton.text = "로그아웃"
            loginButton.icon = AllIcons.Actions.Cancel
            val username = auth.getUsername(source)
            if (username.isNotBlank()) {
                loginStatusLabel.text = "${source.displayName}: $username"
                loginStatusLabel.foreground = JBColor(java.awt.Color(0, 130, 0), java.awt.Color(80, 200, 80))
                loginStatusLabel.icon = AllIcons.General.InspectionsOK
            } else {
                loginStatusLabel.text = "로그인됨"
                loginStatusLabel.foreground = JBColor(java.awt.Color(0, 130, 0), java.awt.Color(80, 200, 80))
                loginStatusLabel.icon = AllIcons.General.InspectionsOK
            }
        } else {
            loginButton.text = "로그인"
            loginButton.icon = AllIcons.General.User
            loginStatusLabel.text = ""
            loginStatusLabel.icon = null
        }
    }

    private fun handleLogin() {
        val source = getSelectedSource()
        val auth = AuthService.getInstance()

        if (auth.isLoggedIn(source)) {
            auth.logout(source)
            updateLoginButton()
            Messages.showInfoMessage(project, "${source.displayName} 로그아웃되었습니다.", "CodingTestKit")
            return
        }

        val dialog = LoginDialog(project, source)
        if (dialog.showAndGet()) {
            val cookies = dialog.getCookies()
            if (cookies.isNotBlank()) {
                auth.setCookies(source, cookies)
                loginStatusLabel.text = "유저 정보 가져오는 중..."
                loginStatusLabel.foreground = JBColor.GRAY
                loginStatusLabel.icon = AllIcons.Process.Step_1
                loginButton.text = "로그아웃"
                loginButton.icon = AllIcons.Actions.Cancel

                // 백그라운드에서 유저네임 가져오기 (최대 2회 시도)
                ApplicationManager.getApplication().executeOnPooledThread {
                    var username = auth.fetchUsername(source)
                    if (username.isBlank()) {
                        // 첫 시도 실패 시 1초 후 재시도 (쿠키 반영 지연 대비)
                        Thread.sleep(1500)
                        username = auth.fetchUsername(source)
                    }
                    auth.setUsername(source, username)
                    SwingUtilities.invokeLater { updateLoginButton() }
                }
            }
        }
    }

    private fun fetchProblem() {
        val problemId = problemIdField.text.trim()
        if (problemId.isBlank()) {
            Messages.showWarningDialog(project, "문제 번호를 입력하세요.", "CodingTestKit")
            return
        }

        val id = extractProblemId(problemId)
        val source = getSelectedSource()
        val language = getSelectedLanguage()
        val cookies = AuthService.getInstance().getCookies(source)

        // SWEA는 JS 렌더링이 필요하므로 JCEF 브라우저로 가져옴
        if (source == ProblemSource.SWEA) {
            fetchSweaProblem(id, language)
            return
        }

        fetchButton.isEnabled = false
        fetchButton.text = "가져오는 중..."

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val problem = when (source) {
                    ProblemSource.BAEKJOON -> BaekjoonCrawler.fetchProblem(id)
                    ProblemSource.PROGRAMMERS -> ProgrammersCrawler.fetchProblem(id, cookies)
                    ProblemSource.SWEA -> throw IllegalStateException("unreachable")
                }

                val files = ProblemFileManager.createProblemFiles(project, problem, language)

                SwingUtilities.invokeLater {
                    handleFetchSuccess(problem, files)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    handleFetchError(e)
                }
            }
        }
    }

    private fun fetchSweaProblem(problemId: String, language: Language) {
        val dialog = SweaFetchDialog(project, problemId)
        if (dialog.showAndGet()) {
            val problem = dialog.getProblem()
            if (problem != null) {
                fetchButton.isEnabled = false
                fetchButton.text = "파일 생성 중..."
                val inputFileContent = dialog.inputFileContent
                val outputFileContent = dialog.outputFileContent
                val imageUrls = dialog.imageUrls
                val sweaCookies = AuthService.getInstance().getCookies(ProblemSource.SWEA)
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        // README용 problem: 다운로드 데이터가 있으면 테스트 케이스 포함
                        val problemForMd = if (inputFileContent.isNotBlank() && outputFileContent.isNotBlank()) {
                            problem.copy(
                                testCases = mutableListOf(
                                    com.codingtestkit.model.TestCase(
                                        input = inputFileContent.trim(),
                                        expectedOutput = outputFileContent.trim()
                                    )
                                )
                            )
                        } else {
                            problem
                        }
                        val files = ProblemFileManager.createProblemFiles(project, problemForMd, language)
                        // SWEA 전용: 이미지 파일 다운로드
                        println("[CodingTestKit] Image URLs to download: ${imageUrls.size} - $imageUrls")
                        println("[CodingTestKit] SWEA cookies length: ${sweaCookies.length}")
                        for ((idx, url) in imageUrls.withIndex()) {
                            try {
                                val response = org.jsoup.Jsoup.connect(url)
                                    .header("Cookie", sweaCookies)
                                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                                    .ignoreContentType(true)
                                    .followRedirects(true)
                                    .maxBodySize(10 * 1024 * 1024)
                                    .timeout(15000)
                                    .execute()
                                val bytes = response.bodyAsBytes()
                                val contentType = response.contentType() ?: ""
                                println("[CodingTestKit] Image $idx: status=${response.statusCode()}, type=$contentType, size=${bytes.size}")
                                val ext = when {
                                    contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
                                    contentType.contains("gif") -> "gif"
                                    contentType.contains("webp") -> "webp"
                                    url.contains(".jpg") || url.contains(".jpeg") -> "jpg"
                                    url.contains(".gif") -> "gif"
                                    else -> "png"
                                }
                                val fileName = "img_${idx + 1}.$ext"
                                if (bytes.size > 100) {
                                    java.io.File(files.folder, fileName).writeBytes(bytes)
                                    println("[CodingTestKit] Saved: ${files.folder}/$fileName (${bytes.size} bytes)")
                                }
                            } catch (e: Exception) {
                                println("[CodingTestKit] Image download failed for $url: ${e.message}")
                            }
                        }
                        // SWEA 전용: input.txt / output.txt 파일 저장
                        if (inputFileContent.isNotBlank()) {
                            java.io.File(files.folder, "input.txt").writeText(inputFileContent)
                        }
                        if (outputFileContent.isNotBlank()) {
                            java.io.File(files.folder, "output.txt").writeText(outputFileContent)
                        }
                        SwingUtilities.invokeLater {
                            handleFetchSuccess(problemForMd, files)
                        }
                    } catch (e: Exception) {
                        SwingUtilities.invokeLater {
                            handleFetchError(e)
                        }
                    }
                }
            } else {
                Messages.showWarningDialog(project, "SWEA 문제를 가져오지 못했습니다.", "CodingTestKit")
            }
        }
    }

    private fun handleFetchSuccess(problem: Problem, files: ProblemFileManager.CreatedFiles) {
        currentProblem = problem
        currentProblemFolder = files.folder
        displayProblem(problem)
        submitButton.isEnabled = true
        onProblemFetched?.invoke(problem)
        fetchButton.isEnabled = true
        fetchButton.text = "가져오기"

        Messages.showInfoMessage(
            project,
            "문제를 가져왔습니다!\n" +
                    "폴더: ${files.folder.name}\n" +
                    "코드: ${files.codeFile.name}\n" +
                    "설명: ${files.markdownFile.name}",
            "CodingTestKit"
        )
    }

    /**
     * 기존 문제 폴더에서 문제를 로드 (파일 열 때 자동 인식용)
     */
    fun loadExistingProblem(problem: Problem, folder: java.io.File) {
        currentProblem = problem
        currentProblemFolder = folder
        displayProblem(problem)
        submitButton.isEnabled = true

        // 플랫폼/번호 필드도 업데이트
        problemIdField.text = problem.id
        for (i in 0 until sourceCombo.itemCount) {
            if (sourceCombo.getItemAt(i) == problem.source.displayName) {
                sourceCombo.selectedIndex = i
                break
            }
        }
    }

    private fun handleFetchError(e: Exception) {
        problemDisplay.text = "<html><body style='font-family:sans-serif; padding:10px; color:#cc4444;'>" +
                "<h3>오류 발생</h3><p>${e.message}</p></body></html>"
        fetchButton.isEnabled = true
        fetchButton.text = "가져오기"
    }

    private fun submitSolution() {
        val problem = currentProblem ?: return
        val source = problem.source
        val cookies = AuthService.getInstance().getCookies(source)
        val language = getSelectedLanguage()

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val code = editor?.document?.text
        val fileName = editor?.virtualFile?.name ?: "알 수 없음"
        val filePath = editor?.virtualFile?.path ?: ""
        if (code.isNullOrBlank()) {
            Messages.showWarningDialog(project, "에디터에 코드가 없습니다.", "CodingTestKit")
            return
        }

        if (cookies.isBlank()) {
            Messages.showWarningDialog(project, "먼저 ${source.displayName}에 로그인하세요.", "CodingTestKit")
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            "[${source.displayName} #${problem.id}] ${problem.title}\n" +
                    "언어: ${language.displayName}\n" +
                    "파일: $fileName (${code.lines().size}줄)\n" +
                    "경로: $filePath\n\n" +
                    "이 파일을 제출하시겠습니까?",
            "코드 제출",
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return

        // JCEF 브라우저로 제출 (코드 자동 입력, 사용자가 직접 제출 확인)
        val submitId = if (source == ProblemSource.SWEA && problem.contestProbId.isNotBlank()) {
            problem.contestProbId
        } else {
            problem.id
        }
        val dialog = CodeSubmitDialog(project, source, submitId, code, language)
        dialog.show()
    }

    private fun extractProblemId(input: String): String {
        val urlPattern = Regex("(?:problems?|lessons|contestProbId=)/?([\\w]+)")
        val match = urlPattern.find(input)
        return match?.groupValues?.get(1) ?: input.trim()
    }

    private fun truncatePreview(text: String, maxLines: Int): String {
        val lines = text.lines()
        return if (lines.size > maxLines) {
            lines.take(maxLines).joinToString("\n") + "\n..."
        } else {
            text
        }
    }

    private fun displayProblem(problem: Problem) {
        val html = buildString {
            append("<html><head><style>")
            append("table { border-collapse:collapse; margin:8px 0; }")
            append("th, td { padding:6px 12px; border:1px solid #555; font-family:monospace; }")
            append("th { background:#3c3f41; color:#bbb; font-weight:bold; }")
            append("</style></head>")
            append("<body style='font-family:-apple-system,sans-serif; padding:10px; line-height:1.6;'>")
            append("<h2 style='margin:0 0 8px 0;'>${problem.title}</h2>")
            append("<div style='color:#888; font-size:12px; margin-bottom:12px;'>")
            append("${problem.source.displayName} #${problem.id}")
            if (problem.timeLimit.isNotBlank()) {
                append(" &nbsp;|&nbsp; 시간: ${problem.timeLimit} &nbsp;|&nbsp; 메모리: ${problem.memoryLimit}")
            }
            append("</div>")
            append("<hr style='border:none; border-top:1px solid #444; margin:8px 0;'>")
            // SWEA: 이미지 로컬 경로 변환 (img_X.png → file:///절대경로/img_X.png)
            var desc = problem.description
            if (problem.source == ProblemSource.SWEA && currentProblemFolder != null) {
                desc = desc.replace(Regex("""src="(img_\d+\.\w+)"""")) { match ->
                    val fileName = match.groupValues[1]
                    val localFile = java.io.File(currentProblemFolder, fileName)
                    "src=\"file://${localFile.absolutePath}\""
                }
            }
            append(desc)

            // 예제 입출력 표시
            if (problem.testCases.isNotEmpty()) {
                if (problem.source == ProblemSource.PROGRAMMERS && problem.parameterNames.isNotEmpty()) {
                    // 프로그래머스: 표 형태로 표시
                    val cellStyle = "padding:8px 12px; border:1px solid #555;"
                    val headerStyle = "$cellStyle background:#3c3f41; color:#bbb; font-weight:bold;"
                    append("<h3>입출력 예</h3>")
                    append("<table cellspacing='0' cellpadding='0' style='border-collapse:collapse; width:100%;'>")
                    append("<tr>")
                    for (param in problem.parameterNames) {
                        append("<th style='$headerStyle'>$param</th>")
                    }
                    append("<th style='$headerStyle'>return</th>")
                    append("</tr>")
                    for (tc in problem.testCases) {
                        append("<tr>")
                        val inputs = tc.input.split("\n")
                        for ((j, _) in problem.parameterNames.withIndex()) {
                            val value = inputs.getOrElse(j) { "" }
                                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                            append("<td style='$cellStyle font-family:monospace;'>$value</td>")
                        }
                        val output = tc.expectedOutput
                            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        append("<td style='$cellStyle font-family:monospace;'>$output</td>")
                        append("</tr>")
                    }
                    append("</table>")
                } else {
                    // 백준/SWEA: 입력/출력 블록
                    for ((i, tc) in problem.testCases.withIndex()) {
                        val preStyle = "background:#2b2b2b; color:#a9b7c6; padding:10px; border:1px solid #555; font-family:monospace;"
                        if (problem.source == ProblemSource.SWEA) {
                            // SWEA: 미리보기만 표시 (10줄/5줄)
                            val inputPreview = truncatePreview(tc.input, 10)
                            val outputPreview = truncatePreview(tc.expectedOutput, 5)
                            val inputHtml = inputPreview.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                            val outputHtml = outputPreview.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                            append("<h2>예제 입력 ${i + 1}</h2>")
                            append("<div style='$preStyle'>$inputHtml</div>")
                            append("<div style='color:#888; font-size:11px;'>전체 데이터는 <code>input.txt</code> 참고</div>")
                            append("<h2>예제 출력 ${i + 1}</h2>")
                            append("<div style='$preStyle'>$outputHtml</div>")
                            append("<div style='color:#888; font-size:11px;'>전체 데이터는 <code>output.txt</code> 참고</div>")
                        } else {
                            val inputHtml = tc.input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                            val outputHtml = tc.expectedOutput.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                            append("<h2>예제 입력 ${i + 1}</h2>")
                            append("<div style='$preStyle'>$inputHtml</div>")
                            append("<h2>예제 출력 ${i + 1}</h2>")
                            append("<div style='$preStyle'>$outputHtml</div>")
                        }
                    }
                }
            }

            append("</body></html>")
        }
        problemDisplay.text = html
        problemDisplay.caretPosition = 0
    }
}
