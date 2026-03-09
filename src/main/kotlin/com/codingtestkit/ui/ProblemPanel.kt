package com.codingtestkit.ui

import com.codingtestkit.service.I18n
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
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import org.cef.callback.CefCallback
import java.io.ByteArrayInputStream
import java.io.InputStream
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.*

class ProblemPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val sourceCombo = ComboBox(ProblemSource.entries.map { it.displayName }.toTypedArray()).apply {
        preferredSize = Dimension(JBUI.scale(100), preferredSize.height)
        renderer = createComboRenderer()
    }
    private val languageCombo = ComboBox(Language.entries.map { it.displayName }.toTypedArray()).apply {
        preferredSize = Dimension(JBUI.scale(90), preferredSize.height)
        renderer = createComboRenderer()
    }
    private val problemIdField = JTextField().apply {
        toolTipText = I18n.t("문제 번호 또는 URL을 입력하세요", "Enter problem number or URL")
    }
    private val fetchButton = JButton(I18n.t("가져오기", "Fetch"), AllIcons.Actions.Download).apply {
        toolTipText = I18n.t("문제를 가져옵니다", "Fetch the problem")
    }
    private val randomButton = JButton(I18n.t("랜덤", "Random"), AllIcons.Actions.Refresh).apply {
        toolTipText = I18n.t("solved.ac에서 랜덤 문제를 뽑습니다", "Pick random problems from solved.ac")
    }
    private val searchButton2 = JButton(I18n.t("검색", "Search"), AllIcons.Actions.Search).apply {
        toolTipText = I18n.t("solved.ac에서 문제를 검색합니다", "Search problems on solved.ac")
    }
    private val loginButton = JButton(I18n.t("로그인", "Login"), AllIcons.General.User)
    private val submitButton = JButton(I18n.t("제출", "Submit"), AllIcons.Actions.Upload).apply {
        toolTipText = I18n.t("현재 에디터의 코드를 제출합니다", "Submit code from current editor")
    }
    private val githubPushButton = JButton("GitHub", AllIcons.Vcs.Push).apply {
        toolTipText = I18n.t("현재 문제를 GitHub에 푸시합니다", "Push current problem to GitHub")
    }
    private val translateButton = JButton(I18n.t("번역", "Translate"), AllIcons.Actions.Preview).apply {
        toolTipText = I18n.t("문제를 번역합니다 (한↔영)", "Translate problem (KR↔EN)")
        isEnabled = false
    }
    private val githubLoginButton = JButton("GitHub", AllIcons.Vcs.Vendors.Github).apply {
        toolTipText = I18n.t("GitHub 토큰 설정", "GitHub token setup")
        font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
        iconTextGap = 0
    }
    private val problemDisplay = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        border = JBUI.Borders.empty(8)
        text = "<html><body style='font-family:sans-serif; padding:8px; color:#999;'>" +
                "<p style='text-align:center; margin-top:40px;'>${I18n.t("문제 번호를 입력하고<br>'가져오기'를 클릭하세요", "Enter a problem number and<br>click 'Fetch'")}</p>" +
                "</body></html>"
    }

    private val useCef = try { JBCefApp.isSupported() } catch (_: Exception) { false }
    private var cefBrowser: JBCefBrowser? = null
    private var currentProblemHtml = ""

    var onProblemFetched: ((Problem) -> Unit)? = null
    private var currentProblem: Problem? = null
    private var currentProblemFolder: java.io.File? = null
    private var isTranslated = false
    private var originalHtml: String? = null
    private var translatedHtml: String? = null

    init {
        border = JBUI.Borders.empty()

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6, 8, 4, 8)
        }

        // Row 1: 플랫폼 + 언어 + 로그인 | GitHub 로그인
        val row1 = JPanel(BorderLayout()).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        val row1Left = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        row1Left.add(createLabel(I18n.t("플랫폼", "Platform")))
        row1Left.add(sourceCombo)
        row1Left.add(Box.createHorizontalStrut(JBUI.scale(4)))
        row1Left.add(createLabel(I18n.t("언어", "Lang")))
        row1Left.add(languageCombo)
        row1Left.add(Box.createHorizontalStrut(JBUI.scale(8)))
        row1Left.add(loginButton)
        row1.add(row1Left, BorderLayout.WEST)

        val row1Right = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0))
        row1Right.add(githubLoginButton)
        row1.add(row1Right, BorderLayout.EAST)
        topPanel.add(row1)
        topPanel.add(Box.createVerticalStrut(JBUI.scale(4)))

        // Row 2: 문제번호 입력 + 가져오기
        val row2 = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(32))
        }
        row2.add(createLabel(I18n.t(" 번호 ", " ID ")), BorderLayout.WEST)
        row2.add(problemIdField, BorderLayout.CENTER)
        val fetchPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0))
        fetchPanel.add(fetchButton)
        fetchPanel.add(randomButton)
        fetchPanel.add(searchButton2)
        row2.add(fetchPanel, BorderLayout.EAST)
        topPanel.add(row2)
        topPanel.add(Box.createVerticalStrut(JBUI.scale(4)))

        // Row 3: 제출 + GitHub
        val row3 = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        row3.add(submitButton)
        row3.add(githubPushButton)
        row3.add(translateButton)
        topPanel.add(row3)

        add(topPanel, BorderLayout.NORTH)

        // 문제 표시 영역 (JCEF 지원 시 KaTeX LaTeX 렌더링 가능)
        if (useCef) {
            cefBrowser = JBCefBrowser()

            // http://localhost/* 요청을 가로채서 JAR 리소스를 서빙
            val panel = this
            cefBrowser!!.jbCefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    browser: CefBrowser, frame: CefFrame, request: CefRequest,
                    userGesture: Boolean, isRedirect: Boolean
                ): Boolean {
                    val url = request.url ?: return false
                    // localhost 요청만 허용, 외부 링크는 차단
                    return !url.startsWith("http://localhost/")
                }

                override fun getResourceRequestHandler(
                    browser: CefBrowser, frame: CefFrame, request: CefRequest,
                    isNavigation: Boolean, isDownload: Boolean, requestInitiator: String, disableDefaultHandling: BoolRef
                ): CefResourceRequestHandler? {
                    val url = request.url ?: return null
                    if (!url.startsWith("http://localhost/")) return null
                    val path = url.removePrefix("http://localhost").substringBefore("?")
                    return object : CefResourceRequestHandlerAdapter() {
                        override fun getResourceHandler(browser: CefBrowser, frame: CefFrame, request: CefRequest): CefResourceHandler? {
                            return when {
                                path == "/problem.html" -> panel.DynamicHtmlHandler()
                                path == "/katex.min.css" -> KaTeXResourceHandler("/katex/katex.min.css", "text/css")
                                path == "/katex.min.js" -> KaTeXResourceHandler("/katex/katex.min.js", "text/javascript")
                                path == "/auto-render.min.js" -> KaTeXResourceHandler("/katex/auto-render.min.js", "text/javascript")
                                path.startsWith("/fonts/") && path.endsWith(".woff2") ->
                                    KaTeXResourceHandler("/katex$path", "font/woff2")
                                else -> null
                            }
                        }
                    }
                }
            }, cefBrowser!!.cefBrowser)

            val cefPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
                add(cefBrowser!!.component, BorderLayout.CENTER)
            }
            add(cefPanel, BorderLayout.CENTER)

            // 초기 플레이스홀더
            val isDark = !JBColor.isBright()
            val pc = if (isDark) "#999" else "#888"
            val bg = if (isDark) "#2b2d30" else "#ffffff"
            currentProblemHtml = "<!DOCTYPE html><html><head><meta charset='utf-8'></head>" +
                "<body style='font-family:sans-serif; padding:8px; color:$pc; background:$bg;'>" +
                "<p style='text-align:center; margin-top:40px;'>${I18n.t("문제 번호를 입력하고<br>'가져오기'를 클릭하세요", "Enter a problem number and<br>click 'Fetch'")}</p>" +
                "</body></html>"
            cefBrowser!!.loadURL("http://localhost/problem.html")
        } else {
            val scrollPane = JBScrollPane(problemDisplay).apply {
                border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
            }
            add(scrollPane, BorderLayout.CENTER)
        }

        submitButton.isEnabled = false
        githubPushButton.isEnabled = false
        updateLoginButton()
        updateGitHubButton()

        // 이벤트
        sourceCombo.addActionListener {
            updateLoginButton()
            updatePlaceholder()
        }
        fetchButton.addActionListener { fetchProblem() }
        randomButton.addActionListener { openRandomDialog() }
        searchButton2.addActionListener { openSearchDialog() }
        loginButton.addActionListener { handleLogin() }
        submitButton.addActionListener { submitSolution() }
        githubPushButton.addActionListener { pushToGitHub() }
        translateButton.addActionListener { translateProblem() }
        githubLoginButton.addActionListener { handleGitHubLogin() }
        problemIdField.addActionListener { fetchProblem() }
        updatePlaceholder()
    }

    private fun updatePlaceholder() {
        val source = getSelectedSource()
        val (placeholder, tooltip) = when (source) {
            ProblemSource.BAEKJOON -> I18n.t("예: 1000", "e.g. 1000") to
                    I18n.t("백준 문제 번호 (URL의 /problem/1000 에서 1000)", "BOJ problem number (1000 from /problem/1000)")
            ProblemSource.PROGRAMMERS -> I18n.t("예: 12947 (URL의 lessons/뒤 숫자)", "e.g. 12947 (number after /lessons/)") to
                    I18n.t("프로그래머스: URL이 /lessons/12947 이면 12947 입력", "Programmers: Enter 12947 if URL is /lessons/12947")
            ProblemSource.SWEA -> I18n.t("예: 2001 또는 URL 붙여넣기", "e.g. 2001 or paste URL") to
                    I18n.t("SWEA: 문제 번호 또는 URL을 입력하세요", "SWEA: Enter problem number or URL")
            ProblemSource.LEETCODE -> I18n.t("예: 1 또는 two-sum", "e.g. 1 or two-sum") to
                    I18n.t("LeetCode: 문제 번호, slug, 또는 URL 입력", "LeetCode: Enter number, slug, or URL")
        }
        problemIdField.putClientProperty("JTextField.placeholderText", placeholder)
        problemIdField.toolTipText = tooltip

        // 프로그래머스/SWEA는 검색·랜덤 미지원
        val hasSearchRandom = source == ProblemSource.BAEKJOON || source == ProblemSource.LEETCODE
        randomButton.isVisible = hasSearchRandom
        searchButton2.isVisible = hasSearchRandom

        if (hasSearchRandom) {
            val isLeetCode = source == ProblemSource.LEETCODE
            randomButton.toolTipText = if (isLeetCode) I18n.t("LeetCode에서 랜덤 문제를 뽑습니다", "Pick random problems from LeetCode")
                else I18n.t("solved.ac에서 랜덤 문제를 뽑습니다", "Pick random problems from solved.ac")
            searchButton2.toolTipText = if (isLeetCode) I18n.t("LeetCode에서 문제를 검색합니다", "Search problems on LeetCode")
                else I18n.t("solved.ac에서 문제를 검색합니다", "Search problems on solved.ac")
        }
    }

    private fun createComboRenderer(): ListCellRenderer<Any?> {
        return ListCellRenderer { list, value, index, isSelected, cellHasFocus ->
            JLabel(value?.toString() ?: "").apply {
                isOpaque = true
                border = JBUI.Borders.empty(2, 8)
                background = if (isSelected) list.selectionBackground else list.background
                foreground = if (isSelected) list.selectionForeground else list.foreground
                font = list.font
            }
        }
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
            loginButton.text = I18n.t("로그아웃", "Logout")
            loginButton.icon = AllIcons.Actions.Cancel
            val username = auth.getUsername(source)
            if (username.isNotBlank()) {
                loginButton.toolTipText = "${source.displayName}: $username"
            }
        } else {
            loginButton.text = I18n.t("로그인", "Login")
            loginButton.icon = AllIcons.General.User
            loginButton.toolTipText = null
        }
    }

    private fun handleLogin() {
        val source = getSelectedSource()
        val auth = AuthService.getInstance()

        if (auth.isLoggedIn(source)) {
            auth.logout(source)
            updateLoginButton()
            Messages.showInfoMessage(project, I18n.t("${source.displayName} 로그아웃되었습니다.", "Logged out from ${source.displayName}."), "CodingTestKit")
            return
        }

        val dialog = LoginDialog(project, source)
        if (dialog.showAndGet()) {
            val cookies = dialog.getCookies()
            if (cookies.isNotBlank()) {
                auth.setCookies(source, cookies)
                loginButton.text = I18n.t("로그아웃", "Logout")
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

    private fun handleGitHubLogin() {
        val dialog = GitHubConfigDialog(project)
        dialog.show()
        updateGitHubButton()
    }

    private fun updateGitHubButton() {
        val github = GitHubService.getInstance()
        if (github.token.isNotBlank()) {
            githubLoginButton.text = "<html>GitHub <font color='#50C878'>✓</font></html>"
            githubLoginButton.toolTipText = I18n.t("GitHub 연결됨 (클릭하여 재설정)", "GitHub connected (click to reconfigure)")
        } else {
            githubLoginButton.text = "GitHub"
            githubLoginButton.toolTipText = I18n.t("GitHub 토큰 설정", "GitHub token setup")
        }
    }

    private fun openSearchDialog() {
        val source = getSelectedSource()
        if (source == ProblemSource.LEETCODE) {
            val dialog = LeetCodeSearchDialog(project)
            if (dialog.showAndGet()) {
                val slug = dialog.selectedProblemSlug ?: return
                problemIdField.text = slug
                fetchProblem()
            }
        } else {
            val dialog = ProblemSearchDialog(project)
            if (dialog.showAndGet()) {
                val problemId = dialog.selectedProblemId ?: return
                problemIdField.text = problemId.toString()
                sourceCombo.selectedIndex = 0 // 백준
                fetchProblem()
            }
        }
    }

    private fun openRandomDialog() {
        val source = getSelectedSource()
        if (source == ProblemSource.LEETCODE) {
            val dialog = LeetCodeRandomDialog(project)
            if (dialog.showAndGet()) {
                val slugs = dialog.selectedProblemSlugs
                if (slugs.isEmpty()) return
                fetchMultipleProblems(slugs)
            }
        } else {
            val dialog = RandomProblemDialog(project)
            if (dialog.showAndGet()) {
                val ids = dialog.selectedProblemIds
                if (ids.isEmpty()) return
                sourceCombo.selectedIndex = 0 // 백준
                fetchMultipleProblems(ids.map { it.toString() })
            }
        }
    }

    private fun fetchMultipleProblems(problemIds: List<String>) {
        if (problemIds.size == 1) {
            problemIdField.text = problemIds[0]
            fetchProblem()
            return
        }
        // 여러 문제를 순차적으로 가져오기
        Thread {
            for (id in problemIds) {
                SwingUtilities.invokeAndWait {
                    problemIdField.text = id
                    fetchProblem()
                }
                Thread.sleep(500) // API 부하 방지
            }
        }.start()
    }

    private fun fetchProblem() {
        val problemId = problemIdField.text.trim()
        if (problemId.isBlank()) {
            Messages.showWarningDialog(project, I18n.t("문제 번호를 입력하세요.", "Please enter a problem number."), "CodingTestKit")
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
        fetchButton.text = I18n.t("가져오는 중...", "Fetching...")

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val problem = when (source) {
                    ProblemSource.BAEKJOON -> BaekjoonCrawler.fetchProblem(id)
                    ProblemSource.PROGRAMMERS -> ProgrammersCrawler.fetchProblem(id, cookies)
                    ProblemSource.LEETCODE -> LeetCodeApi.fetchProblem(id, language.extension)
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
                fetchButton.text = I18n.t("파일 생성 중...", "Creating files...")
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
                Messages.showWarningDialog(project, I18n.t("SWEA 문제를 가져오지 못했습니다.", "Failed to fetch SWEA problem."), "CodingTestKit")
            }
        }
    }

    private fun handleFetchSuccess(problem: Problem, files: ProblemFileManager.CreatedFiles) {
        currentProblem = problem
        currentProblemFolder = files.folder
        isTranslated = false
        translatedHtml = null
        translateButton.text = I18n.t("번역", "Translate")
        displayProblem(problem)
        translateButton.isEnabled = true
        submitButton.isEnabled = true
        githubPushButton.isEnabled = true
        onProblemFetched?.invoke(problem)
        fetchButton.isEnabled = true
        fetchButton.text = I18n.t("가져오기", "Fetch")

        com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("CodingTestKit")
            .createNotification(
                I18n.t("문제를 가져왔습니다!", "Problem fetched!"),
                I18n.t("폴더", "Folder") + ": ${files.folder.name} | " + I18n.t("코드", "Code") + ": ${files.codeFile.name}",
                com.intellij.notification.NotificationType.INFORMATION
            )
            .notify(project)
    }

    /**
     * 기존 문제 폴더에서 문제를 로드 (파일 열 때 자동 인식용)
     */
    fun loadExistingProblem(problem: Problem, folder: java.io.File) {
        currentProblem = problem
        currentProblemFolder = folder
        displayProblem(problem)
        submitButton.isEnabled = true
        githubPushButton.isEnabled = true

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
        setDisplayHtml("<!DOCTYPE html><html><head><meta charset='utf-8'></head>" +
                "<body style='font-family:sans-serif; padding:10px; color:#cc4444;'>" +
                "<h3>${I18n.t("오류 발생", "Error")}</h3><p>${e.message}</p></body></html>")
        fetchButton.isEnabled = true
        fetchButton.text = I18n.t("가져오기", "Fetch")
    }

    private fun submitSolution() {
        val problem = currentProblem ?: return
        val source = problem.source
        val cookies = AuthService.getInstance().getCookies(source)
        val language = getSelectedLanguage()

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val code = editor?.document?.text
        val fileName = editor?.virtualFile?.name ?: I18n.t("알 수 없음", "Unknown")
        val filePath = editor?.virtualFile?.path ?: ""
        if (code.isNullOrBlank()) {
            Messages.showWarningDialog(project, I18n.t("에디터에 코드가 없습니다.", "No code in editor."), "CodingTestKit")
            return
        }

        if (cookies.isBlank()) {
            Messages.showWarningDialog(project, I18n.t("먼저 ${source.displayName}에 로그인하세요.", "Please log in to ${source.displayName} first."), "CodingTestKit")
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            "[${source.displayName} #${problem.id}] ${problem.title}\n" +
                    "${I18n.t("언어", "Language")}: ${language.displayName}\n" +
                    "${I18n.t("파일", "File")}: $fileName (${code.lines().size}${I18n.t("줄", " lines")})\n" +
                    "${I18n.t("경로", "Path")}: $filePath\n\n" +
                    I18n.t("이 파일을 제출하시겠습니까?", "Submit this file?"),
            I18n.t("코드 제출", "Code Submission"),
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

        // 채점 통과(Accepted) 시 자동 GitHub 푸시
        val github = GitHubService.getInstance()
        if (github.isConfigured() && github.autoPushEnabled) {
            val capturedProblem = problem
            val capturedCode = code
            val capturedLang = language
            dialog.onAccepted = {
                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = github.pushSolution(capturedProblem, capturedCode, capturedLang)
                    SwingUtilities.invokeLater {
                        val notification = com.intellij.notification.NotificationGroupManager.getInstance()
                            .getNotificationGroup("CodingTestKit")
                        if (result.success) {
                            notification.createNotification(
                                "GitHub Push",
                                I18n.t("GitHub에 자동 푸시되었습니다!", "Auto-pushed to GitHub!"),
                                com.intellij.notification.NotificationType.INFORMATION
                            ).notify(project)
                        } else {
                            notification.createNotification(
                                "GitHub Push",
                                I18n.t("GitHub 푸시 실패: ${result.message}", "GitHub push failed: ${result.message}"),
                                com.intellij.notification.NotificationType.WARNING
                            ).notify(project)
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun pushToGitHub() {
        val problem = currentProblem ?: return
        val github = GitHubService.getInstance()
        val language = getSelectedLanguage()

        if (!github.isConfigured()) {
            Messages.showWarningDialog(project,
                I18n.t("설정에서 GitHub 토큰과 저장소를 먼저 설정해주세요.",
                    "Please configure GitHub token and repository in Settings first."),
                "GitHub Push")
            return
        }

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val code = editor?.document?.text
        if (code.isNullOrBlank()) {
            Messages.showWarningDialog(project,
                I18n.t("에디터에 코드가 없습니다.", "No code in editor."), "GitHub Push")
            return
        }

        githubPushButton.isEnabled = false
        githubPushButton.text = I18n.t("푸시 중...", "Pushing...")

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = github.pushSolution(problem, code, language)
            SwingUtilities.invokeLater {
                githubPushButton.isEnabled = true
                githubPushButton.text = "GitHub"
                val notification = com.intellij.notification.NotificationGroupManager.getInstance()
                    .getNotificationGroup("CodingTestKit")
                if (result.success) {
                    notification.createNotification(
                        "GitHub Push",
                        I18n.t("[${problem.source.displayName} #${problem.id}] GitHub에 푸시 완료!",
                            "[${problem.source.displayName} #${problem.id}] Pushed to GitHub!"),
                        com.intellij.notification.NotificationType.INFORMATION
                    ).notify(project)
                } else {
                    notification.createNotification(
                        "GitHub Push",
                        I18n.t("GitHub 푸시 실패: ${result.message}",
                            "GitHub push failed: ${result.message}"),
                        com.intellij.notification.NotificationType.WARNING
                    ).notify(project)
                }
            }
        }
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

    /**
     * JAR 리소스를 http://localhost/ 로 서빙하는 CEF 리소스 핸들러
     */
    private class KaTeXResourceHandler(private val resourcePath: String, private val mimeType: String) : CefResourceHandler {
        private var stream: InputStream? = null
        private var responseLength = 0

        override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
            val bytes = ProblemPanel::class.java.getResourceAsStream(resourcePath)?.readBytes()
            if (bytes != null) {
                stream = ByteArrayInputStream(bytes)
                responseLength = bytes.size
                callback.Continue()
                return true
            }
            return false
        }

        override fun getResponseHeaders(response: CefResponse, responseLength: org.cef.misc.IntRef, redirectUrl: StringRef) {
            response.mimeType = mimeType
            response.status = 200
            responseLength.set(this.responseLength)
        }

        override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: org.cef.misc.IntRef, callback: CefCallback): Boolean {
            val s = stream ?: return false
            val available = s.available()
            if (available == 0) {
                bytesRead.set(0)
                return false
            }
            val read = s.read(dataOut, 0, minOf(bytesToRead, available))
            bytesRead.set(read)
            return true
        }

        override fun cancel() {
            stream?.close()
            stream = null
        }
    }

    /**
     * 동적 HTML 콘텐츠를 서빙하는 CEF 리소스 핸들러
     */
    private inner class DynamicHtmlHandler : CefResourceHandler {
        private var stream: InputStream? = null
        private var responseLength = 0

        override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
            val bytes = currentProblemHtml.toByteArray(Charsets.UTF_8)
            stream = ByteArrayInputStream(bytes)
            responseLength = bytes.size
            callback.Continue()
            return true
        }

        override fun getResponseHeaders(response: CefResponse, responseLength: org.cef.misc.IntRef, redirectUrl: StringRef) {
            response.mimeType = "text/html"
            response.setHeaderByName("Content-Type", "text/html; charset=utf-8", true)
            response.status = 200
            responseLength.set(this.responseLength)
        }

        override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: org.cef.misc.IntRef, callback: CefCallback): Boolean {
            val s = stream ?: return false
            val available = s.available()
            if (available == 0) {
                bytesRead.set(0)
                return false
            }
            val read = s.read(dataOut, 0, minOf(bytesToRead, available))
            bytesRead.set(read)
            return true
        }

        override fun cancel() {
            stream?.close()
            stream = null
        }
    }

    /**
     * JCEF 또는 JEditorPane에 HTML을 표시하는 헬퍼
     * JCEF: http://localhost/problem.html → CefResourceHandler로 동적 HTML 서빙
     */
    private fun setDisplayHtml(html: String) {
        if (useCef && cefBrowser != null) {
            currentProblemHtml = html
            cefBrowser!!.loadURL("http://localhost/problem.html?t=${System.currentTimeMillis()}")
        } else {
            problemDisplay.text = html
            problemDisplay.caretPosition = 0
        }
    }

    /**
     * @param overrideLang 번역 시 예제 헤더 등을 타겟 언어로 강제 ("ko" or "en"), null이면 UI 언어 사용
     */
    private fun displayProblem(problem: Problem, overrideLang: String? = null) {
        // overrideLang이 지정되면 I18n 대신 직접 선택
        fun t(ko: String, en: String): String = when (overrideLang) {
            "ko" -> ko
            "en" -> en
            else -> I18n.t(ko, en)
        }

        // 플랫폼 이름도 타겟 언어에 맞게 변환
        val sourceName = when (overrideLang) {
            "en" -> when (problem.source) {
                ProblemSource.BAEKJOON -> "BOJ (Baekjoon)"
                ProblemSource.PROGRAMMERS -> "Programmers"
                ProblemSource.SWEA -> "SWEA"
                ProblemSource.LEETCODE -> "LeetCode"
            }
            "ko" -> when (problem.source) {
                ProblemSource.BAEKJOON -> "백준"
                ProblemSource.PROGRAMMERS -> "프로그래머스"
                ProblemSource.SWEA -> "SWEA"
                ProblemSource.LEETCODE -> "LeetCode"
            }
            else -> problem.source.displayName
        }

        val isDark = !JBColor.isBright()

        val html = buildString {
            append("<!DOCTYPE html>")
            append("<html><head>")
            append("<meta charset='utf-8'>")

            if (useCef) {
                // JCEF: 로컬 KaTeX (CefRequestHandler가 JAR에서 서빙) + 테마 기반 CSS
                append("<link rel='stylesheet' href='katex.min.css'>")
                append("<style>")
                if (isDark) {
                    append("body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 10px; line-height: 1.6; color: #bbb; background: #2b2d30; }")
                    append("h2 { color: #e0e0e0; margin: 0 0 8px 0; }")
                    append("table { border-collapse: collapse; margin: 8px 0; }")
                    append("th, td { padding: 6px 12px; border: 1px solid #555; font-family: monospace; }")
                    append("th { background: #3c3f41; color: #bbb; font-weight: bold; }")
                    append("img { max-width: 100%; }")
                    append("hr { border: none; border-top: 1px solid #444; margin: 8px 0; }")
                    append("pre { background: #1e1e1e; color: #a9b7c6; padding: 10px; border: 1px solid #555; font-family: monospace; border-radius: 4px; white-space: pre-wrap; }")
                    append("code { background: #1e1e1e; color: #a9b7c6; padding: 2px 5px; border-radius: 3px; }")
                    append("a { color: #589df6; pointer-events: none; cursor: default; text-decoration: none; }")
                    append(".bg-red, td.bg-red { background-color: rgba(255, 100, 100, 0.3); }")
                    append(".bg-green, td.bg-green { background-color: rgba(100, 200, 100, 0.3); }")
                    append(".bg-blue, td.bg-blue { background-color: rgba(100, 150, 255, 0.3); }")
                    append(".bg-yellow, td.bg-yellow { background-color: rgba(255, 220, 100, 0.3); }")
                    append(".katex { color: #e0e0e0; }")
                } else {
                    append("body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 10px; line-height: 1.6; color: #333; background: #fff; }")
                    append("h2 { color: #222; margin: 0 0 8px 0; }")
                    append("table { border-collapse: collapse; margin: 8px 0; }")
                    append("th, td { padding: 6px 12px; border: 1px solid #ccc; font-family: monospace; }")
                    append("th { background: #f0f0f0; color: #333; font-weight: bold; }")
                    append("img { max-width: 100%; }")
                    append("hr { border: none; border-top: 1px solid #ddd; margin: 8px 0; }")
                    append("pre { background: #f5f5f5; color: #333; padding: 10px; border: 1px solid #ddd; font-family: monospace; border-radius: 4px; white-space: pre-wrap; }")
                    append("code { background: #f5f5f5; color: #333; padding: 2px 5px; border-radius: 3px; }")
                    append("a { color: #0366d6; pointer-events: none; cursor: default; text-decoration: none; }")
                    append(".bg-red, td.bg-red { background-color: rgba(255, 200, 200, 0.6); }")
                    append(".bg-green, td.bg-green { background-color: rgba(200, 255, 200, 0.6); }")
                    append(".bg-blue, td.bg-blue { background-color: rgba(200, 220, 255, 0.6); }")
                    append(".bg-yellow, td.bg-yellow { background-color: rgba(255, 245, 200, 0.6); }")
                }
                append("</style>")
            } else {
                // JEditorPane: 기본 CSS
                append("<style>")
                append("table { border-collapse:collapse; margin:8px 0; }")
                append("th, td { padding:6px 12px; border:1px solid #555; font-family:monospace; }")
                append("th { background:#3c3f41; color:#bbb; font-weight:bold; }")
                append("img { max-width:100%; }")
                append("</style>")
            }

            append("</head>")

            if (useCef) {
                append("<body>")
            } else {
                append("<body style='font-family:-apple-system,sans-serif; padding:10px; line-height:1.6;'>")
            }

            if (useCef) {
                append("<h2>${problem.title}</h2>")
            } else {
                append("<h2 style='margin:0 0 8px 0;'>${problem.title}</h2>")
            }
            append("<div style='color:#888; font-size:12px; margin-bottom:12px;'>")
            append("$sourceName #${problem.id}")
            if (problem.timeLimit.isNotBlank()) {
                append(" &nbsp;|&nbsp; ${t("시간", "Time")}: ${problem.timeLimit} &nbsp;|&nbsp; ${t("메모리", "Memory")}: ${problem.memoryLimit}")
            }
            append("</div>")
            if (useCef) append("<hr>") else append("<hr style='border:none; border-top:1px solid #444; margin:8px 0;'>")

            // SWEA: 이미지 로컬 경로 변환 (img_X.png → file:///절대경로/img_X.png)
            var desc = problem.description
            if (problem.source == ProblemSource.SWEA && currentProblemFolder != null) {
                desc = desc.replace(Regex("""src="(img_\d+\.\w+)"""")) { match ->
                    val fileName = match.groupValues[1]
                    val localFile = java.io.File(currentProblemFolder, fileName)
                    "src=\"file://${localFile.absolutePath}\""
                }
            }

            if (useCef) {
                // Kotlin에서 LaTeX 구분자를 미리 찾아 <span> 마커로 변환
                // → JCEF에서 katex.render()로 각각 렌더링 (auto-render 대신)

                // 이중 백슬래시 → 단일 백슬래시 정규화 (일부 BOJ 문제)
                desc = desc.replace("\\\\(", "\\(")
                    .replace("\\\\)", "\\)")
                    .replace("\\\\[", "\\[")
                    .replace("\\\\]", "\\]")

                // \[...\] → display math
                desc = desc.replace(Regex("""\\\[(.+?)\\\]""", RegexOption.DOT_MATCHES_ALL)) { m ->
                    val expr = m.groupValues[1].replace("'", "&#39;")
                    "<span class='ktx-d' data-expr='$expr'></span>"
                }
                // \(...\) → inline math
                desc = desc.replace(Regex("""\\\((.+?)\\\)""")) { m ->
                    val expr = m.groupValues[1].replace("'", "&#39;")
                    "<span class='ktx' data-expr='$expr'></span>"
                }
                // $$...$$ → display math
                desc = desc.replace(Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)) { m ->
                    val expr = m.groupValues[1].replace("'", "&#39;")
                    "<span class='ktx-d' data-expr='$expr'></span>"
                }
                // $...$ → inline math ($$나 개행 포함 않음)
                desc = desc.replace(Regex("""\$([^$\n]+?)\$""")) { m ->
                    val expr = m.groupValues[1].replace("'", "&#39;")
                    "<span class='ktx' data-expr='$expr'></span>"
                }
            } else {
                // JEditorPane: 이미지를 <p>로 감싸고 LaTeX → italic 변환
                desc = desc.replace(Regex("""(<img\b[^>]*?/?>)""", RegexOption.IGNORE_CASE)) { m ->
                    "<p>${m.groupValues[1]}</p>"
                }
                desc = desc.replace(Regex("""\$([^$]+)\$""")) { m ->
                    "<i>${m.groupValues[1]}</i>"
                }
            }
            append(desc)

            // 예제 입출력 표시 (백준/프로그래머스는 description에 이미 포함)
            if (problem.testCases.isNotEmpty()
                && problem.source != ProblemSource.PROGRAMMERS
                && problem.source != ProblemSource.BAEKJOON) {
                for ((i, tc) in problem.testCases.withIndex()) {
                    val preStyle = if (useCef) {
                        if (isDark) "background:#1e1e1e; color:#a9b7c6; padding:10px; border:1px solid #555; font-family:monospace; white-space:pre-wrap; border-radius:4px;"
                        else "background:#f5f5f5; color:#333; padding:10px; border:1px solid #ddd; font-family:monospace; white-space:pre-wrap; border-radius:4px;"
                    } else {
                        "background:#2b2b2b; color:#a9b7c6; padding:10px; border:1px solid #555; font-family:monospace;"
                    }
                    if (problem.source == ProblemSource.SWEA) {
                        val inputPreview = truncatePreview(tc.input, 10)
                        val outputPreview = truncatePreview(tc.expectedOutput, 5)
                        val inputHtml = inputPreview.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                        val outputHtml = outputPreview.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                        append("<h2>${t("예제 입력", "Sample Input")} ${i + 1}</h2>")
                        append("<div style='$preStyle'>$inputHtml</div>")
                        append("<div style='color:#888; font-size:11px;'>${t("전체 데이터는 <code>input.txt</code> 참고", "See <code>input.txt</code> for full data")}</div>")
                        append("<h2>${t("예제 출력", "Sample Output")} ${i + 1}</h2>")
                        append("<div style='$preStyle'>$outputHtml</div>")
                        append("<div style='color:#888; font-size:11px;'>${t("전체 데이터는 <code>output.txt</code> 참고", "See <code>output.txt</code> for full data")}</div>")
                    } else {
                        val inputHtml = tc.input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                        val outputHtml = tc.expectedOutput.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                        append("<h2>${t("예제 입력", "Sample Input")} ${i + 1}</h2>")
                        append("<div style='$preStyle'>$inputHtml</div>")
                        append("<h2>${t("예제 출력", "Sample Output")} ${i + 1}</h2>")
                        append("<div style='$preStyle'>$outputHtml</div>")
                    }
                }
            }

            if (useCef) {
                // KaTeX: Kotlin에서 미리 추출한 <span class='ktx'> 마커를 katex.render()로 렌더링
                append("<script src='katex.min.js'></script>")
                append("<script>")
                append("if(typeof katex!=='undefined'){")
                append("  document.querySelectorAll('.ktx').forEach(function(el){")
                append("    try{katex.render(el.getAttribute('data-expr'),el,{throwOnError:false});}catch(e){}")
                append("  });")
                append("  document.querySelectorAll('.ktx-d').forEach(function(el){")
                append("    try{katex.render(el.getAttribute('data-expr'),el,{displayMode:true,throwOnError:false});}catch(e){}")
                append("  });")
                append("}")
                append("</script>")
            }

            append("</body></html>")
        }
        originalHtml = html
        setDisplayHtml(html)
    }

    private fun translateProblem() {
        val html = originalHtml ?: return
        val problem = currentProblem ?: return

        // 이미 번역된 상태면 원문으로 복원
        if (isTranslated) {
            isTranslated = false
            translateButton.text = I18n.t("번역", "Translate")
            setDisplayHtml(html)
            return
        }

        // 캐시된 번역이 있으면 바로 표시
        if (translatedHtml != null) {
            isTranslated = true
            translateButton.text = I18n.t("원문", "Original")
            setDisplayHtml(translatedHtml!!)
            return
        }

        // 번역 실행
        translateButton.isEnabled = false
        translateButton.text = I18n.t("번역 중...", "Translating...")

        val descLang = TranslateService.detectLanguage(problem.description)
        val targetLang = if (descLang == "ko") "en" else "ko"

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val translatedDesc = TranslateService.translate(problem.description, descLang, targetLang)
                val translatedTitle = TranslateService.translate(problem.title, descLang, targetLang)
                // 번역된 설명으로 HTML 재생성
                val translatedProblem = problem.copy(description = translatedDesc, title = translatedTitle)
                SwingUtilities.invokeLater {
                    // 번역된 HTML 빌드 (displayProblem과 동일한 로직이지만 originalHtml을 덮어쓰지 않음)
                    val prevOriginal = originalHtml
                    displayProblem(translatedProblem, overrideLang = targetLang)
                    translatedHtml = originalHtml
                    originalHtml = prevOriginal

                    isTranslated = true
                    translateButton.text = I18n.t("원문", "Original")
                    translateButton.isEnabled = true
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    translateButton.text = I18n.t("번역 실패", "Failed")
                    translateButton.isEnabled = true
                    // 2초 후 원래 텍스트 복원
                    Timer(2000) {
                        translateButton.text = I18n.t("번역", "Translate")
                    }.apply { isRepeats = false; start() }
                }
            }
        }
    }
}
