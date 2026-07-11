package com.codingtestkit.ui

import com.codingtestkit.service.I18n
import com.codingtestkit.service.CodingTestKitActionService
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
import com.intellij.ui.jcef.JBCefJSQuery
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Color
import java.awt.Font
import javax.swing.*

class ProblemPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val sourceCombo = ComboBox(ProblemSource.entries.map { it.localizedName() }.toTypedArray()).apply {
        preferredSize = Dimension(JBUI.scale(88), preferredSize.height)
        renderer = createComboRenderer()
    }
    private val languageCombo = ComboBox(Language.entries.map { it.displayName }.toTypedArray()).apply {
        preferredSize = Dimension(JBUI.scale(90), preferredSize.height)
        renderer = createComboRenderer()
    }
    private val submitSupportLabel = JLabel().apply {
        font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
        foreground = JBColor(Color(0xB0, 0x6A, 0x00), Color(0xE0, 0xA0, 0x50))
        isVisible = false
    }
    private val problemIdField = JTextField().apply {
        toolTipText = I18n.t("문제 번호 또는 URL을 입력하세요", "Enter problem number or URL")
    }
    private val fetchButton = JButton(I18n.t("가져오기", "Fetch"), AllIcons.Actions.Download).apply {
        toolTipText = I18n.t("문제를 가져옵니다", "Fetch the problem")
    }
    private val randomButton = JButton(I18n.t("랜덤", "Random"), AllIcons.Actions.Refresh).apply {
        toolTipText = I18n.t("랜덤 문제를 뽑습니다", "Pick random problems")
    }
    private val searchButton2 = JButton(I18n.t("검색", "Search"), AllIcons.Actions.Search).apply {
        toolTipText = I18n.t("문제를 검색합니다", "Search problems")
    }
    private val mySolvedButton = JButton(I18n.t("내 풀이", "My Solved"), AllIcons.Actions.ListFiles).apply {
        toolTipText = I18n.t("내가 풀었던 문제 기록", "My solved problems history")
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
    // ─── 하단 타이머 바 (이슈 #16): 문제를 보면서 타이머·스톱워치 확인·조작 ───
    private val timerService = TimerService.getInstance(project)
    private val timerBarLabel = JLabel("30:00", AllIcons.Vcs.History, SwingConstants.LEFT).apply {
        font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(13))
        toolTipText = I18n.t("타이머", "Timer")
    }
    private val timerBarToggle = JButton(AllIcons.Actions.Execute).apply {
        toolTipText = I18n.t("타이머 시작/일시정지", "Start/pause timer")
        preferredSize = Dimension(JBUI.scale(26), JBUI.scale(24))
    }
    private val timerBarReset = JButton(AllIcons.Actions.Restart).apply {
        toolTipText = I18n.t("타이머 초기화", "Reset timer")
        preferredSize = Dimension(JBUI.scale(26), JBUI.scale(24))
    }
    // 남은 시간 비율을 보여주는 얇은 프로그레스 바 (바 상단에 배치)
    private val timerBarProgress = JProgressBar(0, 1000).apply {
        isStringPainted = false
        border = JBUI.Borders.empty()
        preferredSize = Dimension(0, JBUI.scale(3))
        value = 1000
    }
    private val swBarLabel = JLabel("00:00", AllIcons.Debugger.Db_muted_breakpoint, SwingConstants.LEFT).apply {
        font = Font("JetBrains Mono", Font.BOLD, JBUI.scale(13))
        toolTipText = I18n.t("스톱워치", "Stopwatch")
    }
    private val swBarToggle = JButton(AllIcons.Actions.Execute).apply {
        toolTipText = I18n.t("스톱워치 시작/일시정지", "Start/pause stopwatch")
        preferredSize = Dimension(JBUI.scale(26), JBUI.scale(24))
    }
    private val swBarReset = JButton(AllIcons.Actions.Restart).apply {
        toolTipText = I18n.t("스톱워치 초기화", "Reset stopwatch")
        preferredSize = Dimension(JBUI.scale(26), JBUI.scale(24))
    }
    private var timerBarLastText = ""
    private var timerBarLastRunning = false
    private var swBarLastText = ""
    private var swBarLastRunning = false

    // ─── 지문 최대화 토글 (이슈 #33) ───
    /** 최대화 시 탭 스트립 제거/복원은 MainPanel 담당 — 생성 직후 MainPanel이 연결 */
    var onMaximizeToggle: ((Boolean) -> Unit)? = null
    private var maximized = false
    /** 지문 우상단 얇은 바의 최대화/복원 토글 — 최대화 여부와 무관하게 항상 같은 자리에 고정 */
    private val viewToggleButton = JButton(AllIcons.General.ExpandComponent).apply {
        toolTipText = I18n.t("지문 최대화", "Maximize problem view")
        preferredSize = Dimension(JBUI.scale(26), JBUI.scale(24))
    }
    private lateinit var topPanel: JPanel
    private lateinit var timerBar: JPanel
    private lateinit var timerBarControls: JPanel
    /** 지문 HTML 안의 최대화 버튼 클릭을 Kotlin으로 넘기는 JS→Java 브릿지 (JCEF 전용) */
    private var maximizeQuery: JBCefJSQuery? = null
    private lateinit var viewHeaderBar: JPanel

    private val problemDisplay = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        border = JBUI.Borders.empty(8)
        text = "<html><body style='font-family:sans-serif; padding:8px; color:#999;'>" +
                "<p style='text-align:center; margin-top:40px;'>${I18n.t("문제 번호를 입력하고<br>'가져오기'를 클릭하세요", "Enter a problem number and<br>click 'Fetch'")}</p>" +
                "</body></html>"
    }

    private val useCef = try { JBCefApp.isSupported() } catch (_: Exception) { false }
    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")
    private var cefBrowser: JBCefBrowser? = null
    private var currentProblemHtml = ""

    var onProblemFetched: ((Problem) -> Unit)? = null

    /** 언어 선택 변경 알림 (Tests 탭과 양방향 동기화용) */
    var onLanguageChanged: ((Language) -> Unit)? = null
    private var currentProblem: Problem? = null
    private var currentProblemFolder: java.io.File? = null
    private var isTranslated = false
    private var originalHtml: String? = null
    private var translatedHtml: String? = null

    init {
        border = JBUI.Borders.empty()

        topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6, 8, 4, 8)
        }

        // Row 1: 플랫폼 + 언어 + 로그인 + GitHub (WrapLayout으로 반응형)
        val row1 = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        row1.add(createLabel(I18n.t("플랫폼", "Platform")))
        row1.add(sourceCombo)
        row1.add(createLabel(I18n.t("언어", "Lang")))
        row1.add(languageCombo)
        row1.add(loginButton)
        row1.add(githubLoginButton)
        row1.add(submitSupportLabel)
        topPanel.add(row1)

        // Row 2: 문제번호 입력 + 가져오기 (WrapLayout으로 반응형)
        val row2 = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        problemIdField.preferredSize = Dimension(JBUI.scale(150), problemIdField.preferredSize.height)
        row2.add(createLabel(I18n.t("번호", "ID")))
        row2.add(problemIdField)
        row2.add(fetchButton)
        row2.add(randomButton)
        row2.add(searchButton2)
        row2.add(mySolvedButton)
        topPanel.add(row2)

        // Row 3: 제출 + GitHub
        val row3 = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
            alignmentX = LEFT_ALIGNMENT
        }
        row3.add(submitButton)
        row3.add(githubPushButton)
        row3.add(translateButton)
        topPanel.add(row3)

        // 헤더(툴바) + 그 아래 얇은 뷰 바(지문 최대화 토글). BorderLayout이라 세로 스트레치 없음.
        // topPanel은 NORTH, viewHeaderBar는 SOUTH — 최대화로 topPanel을 숨겨도 토글은
        // 지문 바로 위 같은 자리에 남는다 (이슈 #33).
        // JCEF에선 지문 HTML 안에 hover 오버레이 버튼을 심으므로 이 얇은 바는 숨긴다.
        // 폴백(JEditorPane)에서만 이 바의 버튼으로 토글한다.
        viewHeaderBar = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), JBUI.scale(2))).apply {
            isOpaque = false
            isVisible = !useCef
            add(viewToggleButton)
        }
        add(JPanel(BorderLayout()).apply {
            add(topPanel, BorderLayout.NORTH)
            add(viewHeaderBar, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        viewToggleButton.addActionListener { setMaximized(!maximized) }

        // 문제 표시 영역 (JCEF 지원 시 KaTeX LaTeX 렌더링 가능)
        if (useCef) {
            cefBrowser = JBCefBrowser()

            // 지문 HTML의 hover 최대화 버튼 클릭 → Kotlin 토글 (이슈 #33)
            maximizeQuery = JBCefJSQuery.create(cefBrowser as com.intellij.ui.jcef.JBCefBrowserBase).also { q ->
                q.addHandler { _ ->
                    SwingUtilities.invokeLater { setMaximized(!maximized) }
                    null
                }
            }

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
                                path == "/problem.html" -> panel.createDynamicHtmlHandler()
                                path == "/katex.min.css" -> panel.createKatexResourceHandler("/katex/katex.min.css", "text/css")
                                path == "/katex.min.js" -> panel.createKatexResourceHandler("/katex/katex.min.js", "text/javascript")
                                path == "/auto-render.min.js" -> panel.createKatexResourceHandler("/katex/auto-render.min.js", "text/javascript")
                                path.startsWith("/fonts/") && path.endsWith(".woff2") ->
                                    panel.createKatexResourceHandler("/katex$path", "font/woff2")
                                path.startsWith("/img_") -> {
                                    val folder = currentProblemFolder
                                    if (folder != null) {
                                        val fileName = path.removePrefix("/")
                                        val file = java.io.File(folder, fileName)
                                        if (file.exists()) panel.createLocalImageHandler(file) else null
                                    } else null
                                }
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

            // JCEF 리사이즈 동기화
            // 1) 직접 리사이즈 시
            cefPanel.addComponentListener(object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent) {
                    SwingUtilities.invokeLater {
                        cefBrowser?.component?.revalidate()
                        cefBrowser?.component?.repaint()
                    }
                }
            })
            // 2) 다른 탭에서 돌아왔을 때 (탭 숨김 중 리사이즈된 경우)
            cefPanel.addHierarchyListener { e ->
                if ((e.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && cefPanel.isShowing) {
                    SwingUtilities.invokeLater {
                        cefBrowser?.component?.revalidate()
                        cefBrowser?.component?.repaint()
                    }
                }
            }

            // 초기 플레이스홀더 (임베드 테마 설정 연동, 이슈 #34)
            val isDark = CefDarkMode.isDarkEnabled()
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

        // 하단 타이머 바 — 타이머 탭·상태바와 같은 TimerService를 공유.
        // 상단에 얇은 프로그레스 바, 왼쪽에 타이머 그룹, 오른쪽에 스톱워치 그룹.
        // 컨트롤(라벨·버튼)은 timerBarControls로 분리 — 최대화 시 숨기되 프로그레스 바는 남긴다
        timerBarControls = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 8, 2, 4)
            add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0)).apply {
                isOpaque = false
                add(timerBarLabel)
                add(timerBarToggle)
                add(timerBarReset)
            }, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0)).apply {
                isOpaque = false
                add(swBarLabel)
                add(swBarToggle)
                add(swBarReset)
            }, BorderLayout.EAST)
        }
        timerBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
            add(timerBarProgress, BorderLayout.NORTH)
            add(timerBarControls, BorderLayout.CENTER)
        }
        add(timerBar, BorderLayout.SOUTH)

        timerBarToggle.addActionListener {
            if (timerService.isRunning) timerService.pause() else timerService.start()
        }
        timerBarReset.addActionListener { timerService.reset() }
        swBarToggle.addActionListener {
            if (timerService.isStopwatchRunning) timerService.stopwatchPause() else timerService.stopwatchStart()
        }
        swBarReset.addActionListener { timerService.stopwatchReset() }
        timerService.addListener { syncTimerBar() }
        syncTimerBar()

        // 임베드 테마 설정 변경 시 이미 로드된 지문을 새 테마로 즉시 다시 그림 (이슈 #34)
        com.codingtestkit.service.PluginSettingsService.getInstance().addEmbedThemeListener {
            SwingUtilities.invokeLater {
                currentProblem?.let { displayProblem(it) }
            }
        }

        submitButton.isEnabled = false
        githubPushButton.isEnabled = false

        // 첫 서비스 접근은 PasswordSafe(키체인)를 읽으므로 EDT 금지 —
        // 백그라운드에서 캐시를 예열한 뒤 버튼 상태를 갱신하고, 이어서 쿠키 유효성 검증
        ApplicationManager.getApplication().executeOnPooledThread {
            val auth = AuthService.getInstance()
            val github = GitHubService.getInstance()
            for (source in ProblemSource.entries) auth.getCookies(source)
            github.token
            SwingUtilities.invokeLater {
                updateLoginButton()
                updateGitHubButton()
            }

            for (source in ProblemSource.entries) {
                if (auth.isLoggedIn(source)) {
                    if (!auth.validateSession(source)) {
                        SwingUtilities.invokeLater { updateLoginButton() }
                    }
                }
            }
        }

        // 이벤트
        sourceCombo.addActionListener {
            updateLoginButton()
            updatePlaceholder()
            updateSubmitSupportHint()
        }
        languageCombo.addActionListener {
            updateSubmitSupportHint()
            onLanguageChanged?.invoke(getSelectedLanguage())
        }
        fetchButton.addActionListener { fetchProblem() }
        randomButton.addActionListener { openRandomDialog() }
        searchButton2.addActionListener { openSearchDialog() }
        mySolvedButton.addActionListener { openMySolvedDialog() }
        loginButton.addActionListener { handleLogin() }
        submitButton.addActionListener { submitSolution() }
        githubPushButton.addActionListener { pushToGitHub() }
        translateButton.addActionListener { translateProblem() }
        githubLoginButton.addActionListener { handleGitHubLogin() }
        problemIdField.addActionListener { fetchProblem() }
        updatePlaceholder()
        updateSubmitSupportHint()

        // 키보드 단축키 액션 등록
        CodingTestKitActionService.getInstance(project).apply {
            fetchAction = { fetchButton.doClick() }
            submitAction = { submitButton.doClick() }
            translateAction = { translateButton.doClick() }
        }
    }

    /**
     * 지문 최대화 토글 (이슈 #33). 헤더(플랫폼/가져오기/제출 툴바)와 타이머 컨트롤을 숨기고
     * MainPanel 콜백으로 탭 스트립까지 제거해 지문이 도구창 전체를 차지하게 한다.
     * 단, 하단 타이머 프로그레스 바는 남겨 시간 감각을 유지한다. 토글 버튼은 지문 우상단
     * 같은 자리에 고정돼 아이콘만 최대화↔복원으로 바뀐다.
     */
    private fun setMaximized(max: Boolean) {
        maximized = max
        topPanel.isVisible = !max
        timerBarControls.isVisible = !max   // 컨트롤만 숨김 — 프로그레스 바(NORTH)는 유지
        viewToggleButton.icon = if (max) AllIcons.General.CollapseComponent else AllIcons.General.ExpandComponent
        viewToggleButton.toolTipText = if (max) I18n.t("원래 화면으로", "Restore normal layout")
                                        else I18n.t("지문 최대화", "Maximize problem view")
        // JCEF 오버레이 버튼 글리프를 재로드 없이 즉시 갱신
        if (useCef && cefBrowser != null) {
            val glyph = if (max) "⤡" else "⤢"
            cefBrowser!!.cefBrowser.executeJavaScript(
                "var b=document.getElementById('ctk-max-btn'); if(b) b.textContent='$glyph';",
                cefBrowser!!.cefBrowser.url, 0
            )
        }
        onMaximizeToggle?.invoke(max)
        revalidate()
        repaint()
    }

    /**
     * 선택한 (플랫폼, 언어) 조합이 제출 미지원이면 콤보 옆에 즉시 경고 표시.
     * 문제를 다 푼 뒤 제출 시점에야 미지원을 알게 되는 상황을 방지한다.
     */
    private fun updateSubmitSupportHint() {
        val source = getSelectedSource()
        val language = getSelectedLanguage()
        val submittable = language.isSubmittable(source)
        submitSupportLabel.isVisible = !submittable
        if (!submittable) {
            submitSupportLabel.text = I18n.t(
                "⚠ ${language.displayName} — ${source.localizedName()} 제출 미지원 (로컬 테스트만 가능)",
                "⚠ ${language.displayName} — cannot submit to ${source.localizedName()} (local testing only)"
            )
        }
    }

    private fun updatePlaceholder() {
        val source = getSelectedSource()
        val (placeholder, tooltip) = when (source) {
            ProblemSource.PROGRAMMERS -> I18n.t("예: 12947 (URL의 lessons/뒤 숫자)", "e.g. 12947 (number after /lessons/)") to
                    I18n.t("프로그래머스: URL이 /lessons/12947 이면 12947 입력", "Programmers: Enter 12947 if URL is /lessons/12947")
            ProblemSource.SWEA -> I18n.t("예: 2001 또는 URL 붙여넣기", "e.g. 2001 or paste URL") to
                    I18n.t("SWEA: 문제 번호 또는 URL을 입력하세요", "SWEA: Enter problem number or URL")
            ProblemSource.LEETCODE -> I18n.t("예: 1 또는 two-sum", "e.g. 1 or two-sum") to
                    I18n.t("LeetCode: 문제 번호, slug, 또는 URL 입력", "LeetCode: Enter number, slug, or URL")
            ProblemSource.CODEFORCES -> I18n.t("예: 1234A", "e.g. 1234A") to
                    I18n.t("Codeforces: 콘테스트번호+문제번호 (예: 1234A) 또는 URL", "Codeforces: contestId+letter (e.g. 1234A) or URL")
        }
        problemIdField.putClientProperty("JTextField.placeholderText", placeholder)
        problemIdField.toolTipText = tooltip

        // 모든 플랫폼에서 검색·랜덤 지원
        randomButton.isVisible = true
        searchButton2.isVisible = true

        // 내 풀이 버튼: Codeforces, LeetCode만 지원
        mySolvedButton.isVisible = SolvedProblemsService.isSupported(source)

        randomButton.toolTipText = when (source) {
            ProblemSource.LEETCODE -> I18n.t("LeetCode에서 랜덤 문제를 뽑습니다", "Pick random problems from LeetCode")
            ProblemSource.CODEFORCES -> I18n.t("Codeforces에서 랜덤 문제를 뽑습니다", "Pick random problems from Codeforces")
            ProblemSource.PROGRAMMERS -> I18n.t("프로그래머스에서 랜덤 문제를 뽑습니다", "Pick random problems from Programmers")
            ProblemSource.SWEA -> I18n.t("SWEA에서 랜덤 문제를 뽑습니다", "Pick random problems from SWEA")
        }
        searchButton2.toolTipText = when (source) {
            ProblemSource.LEETCODE -> I18n.t("LeetCode에서 문제를 검색합니다", "Search problems on LeetCode")
            ProblemSource.CODEFORCES -> I18n.t("Codeforces에서 문제를 검색합니다", "Search problems on Codeforces")
            ProblemSource.PROGRAMMERS -> I18n.t("프로그래머스에서 문제를 검색합니다", "Search problems on Programmers")
            ProblemSource.SWEA -> I18n.t("SWEA에서 문제를 검색합니다", "Search problems on SWEA")
        }
    }

    /** 타이머 바 표시 동기화 — 33ms 틱마다 불리므로 표시 문자열이 바뀔 때만 갱신 */
    private fun syncTimerBar() {
        // 프로그레스는 int(0~1000) 단위라 같은 값이면 setValue가 no-op
        timerBarProgress.value = if (timerService.totalMs > 0)
            ((timerService.remainingMs.toDouble() / timerService.totalMs) * 1000).toInt() else 1000

        val running = timerService.isRunning
        val text = timerService.formatRemaining()
        if (text != timerBarLastText || running != timerBarLastRunning) {
            timerBarLastText = text
            timerBarLastRunning = running
            timerBarLabel.text = text
            timerBarToggle.icon = if (running) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
            timerBarLabel.foreground = if (timerService.remainingMs in 1..59999 && running)
                JBColor(Color.RED, Color(230, 80, 80)) else JBColor.foreground()
        }

        val swRunning = timerService.isStopwatchRunning
        val swText = timerService.formatStopwatch()
        if (swText != swBarLastText || swRunning != swBarLastRunning) {
            swBarLastText = swText
            swBarLastRunning = swRunning
            swBarLabel.text = swText
            swBarToggle.icon = if (swRunning) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
        }
    }

    private fun createComboRenderer(): ListCellRenderer<Any?> {
        return ListCellRenderer { list, value, _, isSelected, _ ->
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

    /** 외부(Tests 탭)에서 언어 동기화. 같은 값이면 무시해 콜백 루프를 끊는다. */
    fun setLanguage(language: Language) {
        val idx = Language.entries.indexOf(language)
        if (idx >= 0 && languageCombo.selectedIndex != idx) {
            languageCombo.selectedIndex = idx
        }
    }

    private fun updateLoginButton() {
        val source = getSelectedSource()
        val auth = AuthService.getInstance()
        if (auth.isLoggedIn(source)) {
            loginButton.text = I18n.t("로그아웃", "Logout")
            loginButton.icon = AllIcons.Actions.Cancel
            val username = auth.getUsername(source)
            if (username.isNotBlank()) {
                loginButton.toolTipText = "${source.localizedName()}: $username"
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
            Messages.showInfoMessage(project, I18n.t("${source.localizedName()} 로그아웃되었습니다.", "Logged out from ${source.localizedName()}."), "CodingTestKit")
            return
        }

        val dialog = LoginDialog(project, source)
        if (dialog.showAndGet()) {
            val cookies = dialog.getCookies()
            if (cookies.isNotBlank()) {
                auth.setCookies(source, cookies)
                loginButton.text = I18n.t("로그아웃", "Logout")
                loginButton.icon = AllIcons.Actions.Cancel

                // JCEF에서 추출한 유저네임 우선 사용, 없으면 백그라운드에서 재시도
                val dialogUsername = dialog.getUsername()
                if (dialogUsername.isNotBlank()) {
                    auth.setUsername(source, dialogUsername)
                    updateLoginButton()
                } else {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        var username = auth.fetchUsername(source)
                        if (username.isBlank()) {
                            Thread.sleep(1500)
                            username = auth.fetchUsername(source)
                        }
                        auth.setUsername(source, username)
                        SwingUtilities.invokeLater { updateLoginButton() }
                    }
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
        githubLoginButton.icon = AllIcons.Vcs.Vendors.Github
        if (github.token.isNotBlank()) {
            githubLoginButton.text = "GitHub ✓"
            githubLoginButton.foreground = JBColor(Color(80, 200, 120), Color(80, 200, 120))
            githubLoginButton.toolTipText = I18n.t("GitHub 연결됨 (클릭하여 재설정)", "GitHub connected (click to reconfigure)")
        } else {
            githubLoginButton.text = "GitHub"
            githubLoginButton.foreground = null
            githubLoginButton.toolTipText = I18n.t("GitHub 토큰 설정", "GitHub token setup")
        }
    }

    private fun openSearchDialog() {
        val source = getSelectedSource()
        when (source) {
            ProblemSource.LEETCODE -> {
                val dialog = LeetCodeSearchDialog(project)
                if (dialog.showAndGet()) {
                    val slug = dialog.selectedProblemSlug ?: return
                    problemIdField.text = slug
                    fetchProblem()
                }
            }
            ProblemSource.CODEFORCES -> {
                val dialog = CodeforcesSearchDialog(project)
                if (dialog.showAndGet()) {
                    val id = dialog.selectedProblemId ?: return
                    problemIdField.text = id
                    fetchProblem()
                }
            }
            ProblemSource.PROGRAMMERS -> {
                val dialog = ProgrammersSearchDialog(project)
                if (dialog.showAndGet()) {
                    val id = dialog.selectedProblemId ?: return
                    problemIdField.text = id
                    fetchProblem()
                }
            }
            ProblemSource.SWEA -> {
                val dialog = SwexpertSearchDialog(project)
                if (dialog.showAndGet()) {
                    val id = dialog.selectedProblemId ?: return
                    problemIdField.text = id
                    fetchProblem()
                }
            }
        }
    }

    private fun openRandomDialog() {
        val source = getSelectedSource()
        when (source) {
            ProblemSource.LEETCODE -> {
                val dialog = LeetCodeRandomDialog(project)
                if (dialog.showAndGet()) {
                    val slugs = dialog.selectedProblemSlugs
                    if (slugs.isEmpty()) return
                    fetchMultipleProblems(slugs)
                }
            }
            ProblemSource.CODEFORCES -> {
                val dialog = CodeforcesRandomDialog(project)
                if (dialog.showAndGet()) {
                    val ids = dialog.selectedProblemIds
                    if (ids.isEmpty()) return
                    fetchMultipleProblems(ids)
                }
            }
            ProblemSource.PROGRAMMERS -> {
                val dialog = ProgrammersRandomDialog(project)
                if (dialog.showAndGet()) {
                    val ids = dialog.selectedProblemIds
                    if (ids.isEmpty()) return
                    fetchMultipleProblems(ids)
                }
            }
            ProblemSource.SWEA -> {
                val dialog = SwexpertRandomDialog(project)
                if (dialog.showAndGet()) {
                    val ids = dialog.selectedProblemIds
                    if (ids.isEmpty()) return
                    fetchMultipleProblems(ids)
                }
            }
        }
    }

    private fun openMySolvedDialog() {
        val source = getSelectedSource()
        val auth = AuthService.getInstance()
        val username = auth.getUsername(source)
        if (username.isBlank()) {
            Messages.showWarningDialog(
                project,
                I18n.t("먼저 로그인해주세요.", "Please log in first."),
                I18n.t("로그인 필요", "Login Required")
            )
            return
        }
        val dialog = MySolvedDialog(project, source)
        if (dialog.showAndGet()) {
            val id = dialog.selectedProblemId ?: return
            problemIdField.text = id
            fetchProblem()
        }
    }

    private fun fetchMultipleProblems(problemIds: List<String>) {
        if (problemIds.size == 1) {
            problemIdField.text = problemIds[0]
            fetchProblem()
            return
        }

        val source = getSelectedSource()

        // SWEA: JCEF 오프스크린 브라우저로 병렬 fetch (최대 3개 동시)
        if (source == ProblemSource.SWEA) {
            fetchButton.isEnabled = false
            val sweaCookies = AuthService.getInstance().getCookies(ProblemSource.SWEA)
            val language = getSelectedLanguage()
            val total = problemIds.size
            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)
            val completedCount = AtomicInteger(0)

            SwingUtilities.invokeLater {
                fetchButton.text = I18n.t(
                    "가져오는 중... (0/$total)",
                    "Fetching... (0/$total)"
                )
            }

            val executor = Executors.newFixedThreadPool(3.coerceAtMost(total))
            val futures = problemIds.map { cpId ->
                CompletableFuture.supplyAsync({
                    try {
                        // HTTP 먼저 시도 (빠름), 실패 시 JCEF fallback
                        var problem = SweaHttpFetcher.fetchProblem(cpId, sweaCookies)
                        if (problem == null && SweaJcefFetcher.isAvailable()) {
                            problem = SweaJcefFetcher.fetchProblem(cpId)
                        }

                        if (problem != null) {
                            val finalProblem = if (problem.contestProbId.isNotBlank() && sweaCookies.isNotBlank()) {
                                val downloadResult = SweaTestDataDownloader.download(problem.contestProbId, sweaCookies)
                                if (downloadResult != null && downloadResult.testCases.isNotEmpty()) {
                                    problem.copy(testCases = downloadResult.testCases.toMutableList())
                                } else problem
                            } else problem

                            val files = ProblemFileManager.createProblemFiles(project, finalProblem, language)
                            SwingUtilities.invokeLater {
                                handleFetchSuccess(finalProblem, files)
                            }
                            successCount.incrementAndGet()
                        } else {
                            failCount.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        failCount.incrementAndGet()
                    } finally {
                        val done = completedCount.incrementAndGet()
                        SwingUtilities.invokeLater {
                            fetchButton.text = I18n.t(
                                "가져오는 중... ($done/$total)",
                                "Fetching... ($done/$total)"
                            )
                        }
                    }
                }, executor)
            }

            ApplicationManager.getApplication().executeOnPooledThread {
                CompletableFuture.allOf(*futures.toTypedArray()).join()
                executor.shutdown()

                SwingUtilities.invokeLater {
                    fetchButton.isEnabled = true
                    fetchButton.text = I18n.t("가져오기", "Fetch")
                    val sc = successCount.get()
                    val fc = failCount.get()
                    if (fc > 0 && sc > 0) {
                        Messages.showInfoMessage(project,
                            I18n.t(
                                "${sc}개 성공, ${fc}개 실패",
                                "$sc succeeded, $fc failed"
                            ),
                            "CodingTestKit")
                    } else if (sc == 0) {
                        val detail = SweaJcefFetcher.lastError
                        Messages.showWarningDialog(project,
                            I18n.t(
                                "SWEA 문제를 가져오지 못했습니다.\n원인: $detail",
                                "Failed to fetch SWEA problems.\nReason: $detail"
                            ),
                            "CodingTestKit")
                    }
                }
            }
            return
        }

        // 기본: 순차 fetch
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
            Messages.showWarningDialog(project, I18n.t("문제 번호를 입력하세요.", "Please enter a problem number or URL."), "CodingTestKit")
            return
        }

        // URL이면 플랫폼 자동 감지 + ID 추출
        val parsed = parseUrlOrId(problemId)
        if (parsed != null) {
            val (detectedSource, detectedId) = parsed
            // 콤보박스를 감지된 플랫폼으로 변경
            val sourceIdx = ProblemSource.entries.indexOfFirst { it == detectedSource }
            if (sourceIdx >= 0) sourceCombo.selectedIndex = sourceIdx
            problemIdField.text = detectedId
        }

        val id = extractProblemId(problemIdField.text.trim())
        val source = getSelectedSource()
        val language = getSelectedLanguage()
        val cookies = AuthService.getInstance().getCookies(source)

        // SWEA는 로그인 필요 + JS 렌더링이 필요하므로 JCEF 브라우저로 가져옴
        if (source == ProblemSource.SWEA) {
            if (!AuthService.getInstance().isLoggedIn(ProblemSource.SWEA)) {
                Messages.showWarningDialog(
                    project,
                    I18n.t("SWEA에 로그인이 필요합니다.\n위 '로그인' 버튼으로 먼저 로그인해주세요.",
                        "SWEA login required.\nPlease log in via the 'Login' button above."),
                    "CodingTestKit"
                )
                return
            }
            fetchSweaProblem(id, language)
            return
        }

        fetchButton.isEnabled = false
        fetchButton.text = I18n.t("가져오는 중...", "Fetching...")

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val problem = when (source) {
                    ProblemSource.PROGRAMMERS -> ProgrammersCrawler.fetchProblem(id, cookies)
                    ProblemSource.LEETCODE -> LeetCodeApi.fetchProblem(id, language.extension, cookies)
                    ProblemSource.CODEFORCES -> try {
                        CodeforcesCrawler.fetchProblem(id, cookies)
                    } catch (e: Exception) {
                        // Cloudflare가 Jsoup을 차단(403)하면 실브라우저(JCEF) 폴백.
                        if (!CodeforcesJcefFetcher.isAvailable()) throw e
                        // Linux는 OSR(오프스크린)에서 CEF OnPaint/requestAnimationFrame이 돌지 않아
                        // Cloudflare의 canvas 렌더 챌린지를 통과하지 못한다 — GitHub Actions(Xvfb) CI로
                        // 재현·확인한 CEF 레벨 버그(chromiumembedded/cef#2800, #4166). 따라서 Linux는
                        // OSR로 시간을 허비하지 않고 곧바로 보이는 다이얼로그(실제 창 렌더 → 통과)로 간다.
                        // macOS/Windows는 OSR이 정상이라 팝업 없이 자동 처리를 먼저 시도.
                        val osrProblem = if (isLinux) null else CodeforcesJcefFetcher.fetchProblem(id)
                        osrProblem
                            ?: fetchCodeforcesViaDialog(id)
                            ?: throw IllegalStateException(I18n.t(
                                "Codeforces 문제를 가져오지 못했습니다. Cloudflare 확인이 필요합니다.\n" +
                                    "다시 시도하면 뜨는 창에서 확인을 완료해 주세요.",
                                "Failed to fetch the Codeforces problem — Cloudflare verification is required.\n" +
                                    "Try again and complete the check in the window that appears."
                            ))
                    }
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

    /**
     * OSR 폴백까지 실패했을 때의 최종 폴백: 보이는 다이얼로그로 사용자가 직접
     * Cloudflare 챌린지를 통과하게 한다 (이슈 #30). 백그라운드 스레드에서 호출되므로
     * EDT에서 다이얼로그를 띄우고 결과를 기다린다.
     */
    private fun fetchCodeforcesViaDialog(id: String): Problem? {
        if (!JBCefApp.isSupported()) return null
        var result: Problem? = null
        SwingUtilities.invokeAndWait {
            val dialog = CodeforcesFetchDialog(project, id)
            if (dialog.showAndGet()) {
                result = dialog.getProblem()
            }
        }
        return result
    }

    private fun fetchSweaProblem(problemId: String, language: Language) {
        fetchButton.isEnabled = false
        fetchButton.text = I18n.t("가져오는 중...", "Fetching...")

        val sweaCookies = AuthService.getInstance().getCookies(ProblemSource.SWEA)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // 1) contestProbId 해석
                val contestProbId = if (problemId.any { it.isLetter() }) {
                    problemId
                } else {
                    val result = SwexpertApi.searchProblems(keyword = problemId, cookies = sweaCookies, pageSize = 10)
                    result.problems.firstOrNull { it.number == problemId }?.contestProbId
                }

                if (contestProbId == null) {
                    SwingUtilities.invokeLater {
                        handleFetchError(Exception(I18n.t(
                            "문제 번호를 찾을 수 없습니다. SWEA에 로그인했는지 확인하세요.",
                            "Could not resolve problem number. Please check SWEA login."
                        )))
                    }
                    return@executeOnPooledThread
                }

                // 2) HTTP 직접 fetch 시도 (빠름)
                var problem = SweaHttpFetcher.fetchProblem(contestProbId, sweaCookies)

                // 3) HTTP 실패 시 JCEF 오프스크린 시도
                if (problem == null && SweaJcefFetcher.isAvailable()) {
                    problem = SweaJcefFetcher.fetchProblem(contestProbId)
                }

                // 4) JCEF 오프스크린도 실패 시 다이얼로그 fallback
                if (problem == null) {
                    SwingUtilities.invokeAndWait {
                        val dialog = SweaFetchDialog(project, problemId)
                        if (dialog.showAndGet()) {
                            problem = dialog.getProblem()
                        }
                    }
                }

                if (problem == null) {
                    SwingUtilities.invokeLater {
                        fetchButton.isEnabled = true
                        fetchButton.text = I18n.t("가져오기", "Fetch")
                        Messages.showWarningDialog(project,
                            I18n.t("SWEA 문제를 가져오지 못했습니다.", "Failed to fetch SWEA problem."),
                            "CodingTestKit")
                    }
                    return@executeOnPooledThread
                }

                val prob = problem!!

                // 4) 테스트 데이터 다운로드
                val finalProblem = if (prob.contestProbId.isNotBlank() && sweaCookies.isNotBlank()) {
                    val downloadResult = SweaTestDataDownloader.download(prob.contestProbId, sweaCookies)
                    if (downloadResult != null && downloadResult.testCases.isNotEmpty()) {
                        println("[CodingTestKit] SWEA test data downloaded: input=${downloadResult.rawInput.length}, output=${downloadResult.rawOutput.length}")
                        prob.copy(testCases = downloadResult.testCases.toMutableList())
                    } else {
                        println("[CodingTestKit] SWEA test data download failed, using preview data")
                        prob
                    }
                } else {
                    prob
                }

                val files = ProblemFileManager.createProblemFiles(project, finalProblem, language)
                SwingUtilities.invokeLater {
                    handleFetchSuccess(finalProblem, files)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    handleFetchError(e)
                }
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
     * 문제 설명 초기화 (파일 닫힘 등)
     */
    fun clearProblem() {
        currentProblem = null
        currentProblemFolder = null
        isTranslated = false
        translatedHtml = null
        translateButton.text = I18n.t("번역", "Translate")
        translateButton.isEnabled = false
        submitButton.isEnabled = false
        githubPushButton.isEnabled = false
        setDisplayHtml("")
    }

    /**
     * 기존 문제 폴더에서 문제를 로드 (파일 열 때 자동 인식용)
     */
    fun loadExistingProblem(problem: Problem, folder: java.io.File) {
        currentProblem = problem
        currentProblemFolder = folder
        isTranslated = false
        translatedHtml = null
        translateButton.text = I18n.t("번역", "Translate")
        displayProblem(problem)
        submitButton.isEnabled = true
        githubPushButton.isEnabled = true
        translateButton.isEnabled = true

        // 플랫폼/번호 필드도 업데이트
        problemIdField.text = problem.id
        val sourceIdx = ProblemSource.entries.indexOf(problem.source)
        if (sourceIdx >= 0) sourceCombo.selectedIndex = sourceIdx
    }

    private fun handleFetchError(e: Exception) {
        val msg = e.message ?: ""
        val isLeetCode = getSelectedSource() == ProblemSource.LEETCODE
        val isLoggedIn = AuthService.getInstance().isLoggedIn(ProblemSource.LEETCODE)
        val userMsg = when {
            isLeetCode && !isLoggedIn && (msg.contains("400") || msg.contains("403") || msg.contains("499")) ->
                I18n.t(
                    "LeetCode에 로그인이 필요합니다.<br>위 '로그인' 버튼으로 LeetCode에 먼저 로그인해주세요.",
                    "LeetCode login required.<br>Please log in via the 'Login' button above."
                )
            else -> msg
        }
        setDisplayHtml("<!DOCTYPE html><html><head><meta charset='utf-8'></head>" +
                "<body style='font-family:sans-serif; padding:10px; color:#cc4444;'>" +
                "<h3>${I18n.t("오류 발생", "Error")}</h3><p>$userMsg</p></body></html>")
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
            Messages.showWarningDialog(project, I18n.t("먼저 ${source.localizedName()}에 로그인하세요.", "Please log in to ${source.localizedName()} first."), "CodingTestKit")
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            "[${source.localizedName()} #${problem.id}] ${problem.title}\n" +
                    "${I18n.t("언어", "Language")}: ${language.displayName}\n" +
                    "${I18n.t("파일", "File")}: $fileName (${code.lines().size}${I18n.t("줄", " lines")})\n" +
                    "${I18n.t("경로", "Path")}: $filePath\n\n" +
                    I18n.t("이 파일을 제출하시겠습니까?", "Submit this file?"),
            I18n.t("코드 제출", "Code Submission"),
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return

        // JCEF 브라우저로 제출 (코드 자동 입력, 사용자가 직접 제출 확인)
        // SWEA: contestProbId, LeetCode: titleSlug (contestProbId에 저장됨)
        val submitId = when {
            (source == ProblemSource.SWEA || source == ProblemSource.LEETCODE || source == ProblemSource.CODEFORCES)
                && problem.contestProbId.isNotBlank() -> problem.contestProbId
            // LeetCode: slug가 없으면 title에서 생성 (예: "Two Sum" → "two-sum")
            source == ProblemSource.LEETCODE -> problem.title.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-").trim('-')
            else -> problem.id
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

        val confirm = Messages.showYesNoDialog(project,
            I18n.t("GitHub에 코드를 푸시하시겠습니까?\n[${problem.source.localizedName()} #${problem.id}] ${problem.title}",
                "Push code to GitHub?\n[${problem.source.localizedName()} #${problem.id}] ${problem.title}"),
            "GitHub Push",
            Messages.getQuestionIcon())
        if (confirm != Messages.YES) return

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
                        I18n.t("[${problem.source.localizedName()} #${problem.id}] GitHub에 푸시 완료!",
                            "[${problem.source.localizedName()} #${problem.id}] Pushed to GitHub!"),
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

    /**
     * URL 또는 문제 번호에서 플랫폼과 ID를 감지
     * 반환: Pair(ProblemSource, ID) 또는 null (일반 번호 입력 시)
     */
    private fun parseUrlOrId(input: String): Pair<ProblemSource, String>? {
        val text = input.trim()
        if (!text.contains("://")) return null

        // 프로그래머스: https://school.programmers.co.kr/learn/courses/30/lessons/258705
        val progMatch = Regex("programmers\\.co\\.kr/learn/courses/\\d+/lessons/(\\d+)").find(text)
        if (progMatch != null) return ProblemSource.PROGRAMMERS to progMatch.groupValues[1]

        // SWEA: https://swexpertacademy.com/...?contestProbId=AZv-iZeqx2PHBIN6...
        val sweaMatch = Regex("contestProbId=([A-Za-z0-9_-]+)").find(text)
        if (sweaMatch != null) return ProblemSource.SWEA to sweaMatch.groupValues[1]

        // LeetCode: https://leetcode.com/problems/two-sum/description/
        val lcMatch = Regex("leetcode\\.com/problems/([\\w-]+)").find(text)
        if (lcMatch != null) return ProblemSource.LEETCODE to lcMatch.groupValues[1]

        // Codeforces: https://codeforces.com/problemset/problem/1234/A or /contest/1234/problem/A
        val cfMatch = Regex("codeforces\\.com/(?:problemset/problem|contest)/(\\d+)/(?:problem/)?([A-Za-z]\\d?)").find(text)
        if (cfMatch != null) return ProblemSource.CODEFORCES to "${cfMatch.groupValues[1]}${cfMatch.groupValues[2]}"

        return null
    }

    private fun extractProblemId(input: String): String {
        return input.trim()
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
     * 로컬 이미지 파일을 http://localhost/img_* 로 서빙하는 CEF 리소스 핸들러
     */
    // ─── CEF 리소스 핸들러 (버전 호환 Proxy) ───
    // JCEF의 CefResourceHandler 인터페이스는 IntelliJ 2026.2(빌드 262)에서
    // processRequest/readResponse → open/read/skip 으로 바뀐다. 정적 구현은
    // 한 버전에만 묶이므로, java.lang.reflect.Proxy 로 런타임 인터페이스에 맞춰
    // 메서드 이름 기준으로 디스패치한다. 이렇게 하면 옛/새 API 모두 호환된다.

    private fun createDynamicHtmlHandler(): CefResourceHandler =
        createCefResourceHandler(
            loadBytes = { currentProblemHtml.toByteArray(Charsets.UTF_8) },
            mimeType = { "text/html" },
            configureResponse = { it.setHeaderByName("Content-Type", "text/html; charset=utf-8", true) }
        )

    private fun createKatexResourceHandler(resourcePath: String, mimeType: String): CefResourceHandler =
        createCefResourceHandler(
            loadBytes = { ProblemPanel::class.java.getResourceAsStream(resourcePath)?.readBytes() },
            mimeType = { mimeType }
        )

    private fun createLocalImageHandler(file: java.io.File): CefResourceHandler =
        createCefResourceHandler(
            loadBytes = { file.readBytes() },
            mimeType = {
                when {
                    file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") -> "image/jpeg"
                    file.name.endsWith(".gif") -> "image/gif"
                    file.name.endsWith(".webp") -> "image/webp"
                    else -> "image/png"
                }
            }
        )

    /**
     * CefResourceHandler 를 reflection Proxy 로 생성한다. 런타임의 실제 인터페이스
     * (옛: processRequest/readResponse, 새: open/read/skip)에 맞춰 메서드가 생성되고
     * InvocationHandler 가 이름으로 디스패치한다. 새 타입(LongRef 등)은 reflection 처리.
     */
    private fun createCefResourceHandler(
        loadBytes: () -> ByteArray?,
        mimeType: () -> String,
        configureResponse: (CefResponse) -> Unit = {}
    ): CefResourceHandler {
        var stream: InputStream? = null
        var length = 0

        val handler = java.lang.reflect.InvocationHandler { proxy, method, args ->
            when (method.name) {
                // 옛: processRequest(CefRequest, CefCallback) / 새: open(CefRequest, BoolRef, CefCallback)
                "processRequest", "open" -> {
                    val bytes = loadBytes()
                    if (bytes == null) {
                        false
                    } else {
                        stream = ByteArrayInputStream(bytes)
                        length = bytes.size
                        if (method.name == "open") {
                            (args[1] as BoolRef).set(true) // 동기 처리
                        } else {
                            (args[1] as CefCallback).Continue()
                        }
                        true
                    }
                }
                "getResponseHeaders" -> {
                    val response = args[0] as CefResponse
                    val responseLength = args[1] as org.cef.misc.IntRef
                    response.mimeType = mimeType()
                    configureResponse(response)
                    response.status = 200
                    responseLength.set(length)
                    null
                }
                // 옛: readResponse(...) / 새: read(...) — 시그니처(byte[], int, IntRef, callback) 동일
                "readResponse", "read" -> {
                    val dataOut = args[0] as ByteArray
                    val bytesToRead = args[1] as Int
                    val bytesRead = args[2] as org.cef.misc.IntRef
                    val s = stream
                    if (s == null) {
                        false
                    } else {
                        val available = s.available()
                        if (available == 0) {
                            bytesRead.set(0)
                            false
                        } else {
                            val read = s.read(dataOut, 0, minOf(bytesToRead, available))
                            bytesRead.set(read)
                            true
                        }
                    }
                }
                // 새 API 전용: skip(long, LongRef, CefResourceSkipCallback)
                "skip" -> {
                    val toSkip = args[0] as Long
                    val skipped = stream?.skip(toSkip) ?: 0L
                    runCatching {
                        val ref = args[1]
                        ref.javaClass.getMethod("set", Long::class.javaPrimitiveType).invoke(ref, skipped)
                    }
                    true
                }
                "cancel" -> {
                    stream?.close()
                    stream = null
                    null
                }
                // Object 기본 메서드 (Proxy 가 위임)
                "toString" -> "CefResourceHandlerProxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                else -> null
            }
        }

        return java.lang.reflect.Proxy.newProxyInstance(
            CefResourceHandler::class.java.classLoader,
            arrayOf(CefResourceHandler::class.java),
            handler
        ) as CefResourceHandler
    }

    /**
     * JCEF 또는 JEditorPane에 HTML을 표시하는 헬퍼
     * JCEF: http://localhost/problem.html → CefResourceHandler로 동적 HTML 서빙
     */
    private fun setDisplayHtml(html: String) {
        if (useCef && cefBrowser != null) {
            currentProblemHtml = injectMaximizeOverlay(html)
            cefBrowser!!.loadURL("http://localhost/problem.html?t=${System.currentTimeMillis()}")
        } else {
            problemDisplay.text = html
            problemDisplay.caretPosition = 0
        }
    }

    /**
     * 지문 HTML 우상단에 hover로 나타나는 최대화/복원 버튼을 심는다 (이슈 #33, JCEF 전용).
     * 평소엔 반투명하게 숨었다가 우상단 코너에 마우스를 대면 또렷해진다. 클릭은 JBCefJSQuery
     * 브릿지로 Kotlin의 setMaximized를 호출한다.
     */
    private fun injectMaximizeOverlay(html: String): String {
        val q = maximizeQuery ?: return html
        val glyph = if (maximized) "⤡" else "⤢"   // 최대화 / 복원
        val onClick = q.inject("'toggle'")          // 클릭 시 브릿지 호출 JS
        val overlay = """
            <style>
              #ctk-max-btn{position:fixed;top:8px;right:10px;opacity:0;transition:opacity .16s ease;
                z-index:2147483647;border:none;border-radius:7px;padding:5px 9px;cursor:pointer;
                font-size:15px;line-height:1;background:rgba(128,128,128,.20);color:inherit;
                -webkit-user-select:none;user-select:none;box-shadow:0 1px 4px rgba(0,0,0,.18);}
              /* 지문 어디든 마우스가 올라오면 버튼이 나타남 (마우스가 창 밖으로 나가면 사라짐) */
              body:hover #ctk-max-btn,#ctk-max-btn:focus{opacity:1;}
              #ctk-max-btn:hover{background:rgba(128,128,128,.38);}
            </style>
            <button id="ctk-max-btn" title="maximize" onclick="$onClick">$glyph</button>
        """.trimIndent()
        // </body> 직전에 삽입 (없으면 그냥 뒤에 붙임)
        val idx = html.lastIndexOf("</body>")
        return if (idx >= 0) html.substring(0, idx) + overlay + html.substring(idx) else html + overlay
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
                ProblemSource.PROGRAMMERS -> "Programmers"
                ProblemSource.SWEA -> "SWEA"
                ProblemSource.LEETCODE -> "LeetCode"
                ProblemSource.CODEFORCES -> "Codeforces"
            }
            "ko" -> when (problem.source) {
                ProblemSource.PROGRAMMERS -> "프로그래머스"
                ProblemSource.SWEA -> "SWEA"
                ProblemSource.LEETCODE -> "LeetCode"
                ProblemSource.CODEFORCES -> "Codeforces"
            }
            else -> problem.source.localizedName()
        }

        // 임베드 페이지 테마 설정 연동 (이슈 #34) — IDE 따라감/항상 밝게/항상 어둡게
        val isDark = CefDarkMode.isDarkEnabled()

        val html = buildString {
            append("<!DOCTYPE html>")
            append("<html><head>")
            append("<meta charset='utf-8'>")

            if (useCef) {
                // JCEF: 로컬 KaTeX (CefRequestHandler가 JAR에서 서빙) + 테마 기반 CSS
                append("<link rel='stylesheet' href='katex.min.css'>")
                append("<style>")
                if (isDark) {
                    append("body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 10px; line-height: 1.7; color: #d4d4d4; background: #1e1f22; }")
                    append("h2 { color: #e8e8e8; margin: 0 0 8px 0; }")
                    append("h3, h4 { color: #e0e0e0; }")
                    append("b, strong { color: #e8e8e8; }")
                    append("table { border-collapse: collapse; margin: 8px 0; }")
                    append("th, td { padding: 6px 12px; border: 1px solid #444; font-family: monospace; }")
                    append("th { background: #2b2d30; color: #ccc; font-weight: bold; }")
                    append("img { max-width: 100%; height: auto; }")
                    append("img[src^='data:'] { filter: invert(1); }")
                    // 코드포스 구식 수식 이미지(espresso 호스트, 검은 글씨 + 투명 배경)는
                    // 다크 테마에서 배경에 묻히므로 본문 텍스트 밝기에 맞춰 반전 (이슈 #27).
                    // 일반 삽화(codeforces.com 호스트)는 반전 대상에서 제외.
                    append("img[src*='espresso.'] { filter: invert(0.85); }")
                    append("hr { border: none; border-top: 1px solid #3c3f41; margin: 12px 0; }")
                    append("pre { background: #1a1a1a; color: #c5c8c6; padding: 12px; border: 1px solid #3c3f41; font-family: 'JetBrains Mono', monospace; border-radius: 6px; white-space: pre-wrap; line-height: 1.5; }")
                    append("code { background: #2b2d30; color: #c5c8c6; padding: 2px 6px; border-radius: 4px; font-size: 0.9em; }")
                    append("a { color: #589df6; pointer-events: none; cursor: default; text-decoration: none; }")
                    append("li { margin: 3px 0; }")
                    append("p { margin: 8px 0; }")
                    append(".bg-red, td.bg-red { background-color: rgba(255, 100, 100, 0.3); }")
                    append(".bg-green, td.bg-green { background-color: rgba(100, 200, 100, 0.3); }")
                    append(".bg-blue, td.bg-blue { background-color: rgba(100, 150, 255, 0.3); }")
                    append(".bg-yellow, td.bg-yellow { background-color: rgba(255, 220, 100, 0.3); }")
                    append(".katex { color: #e0e0e0; }")
                } else {
                    append("body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 10px; line-height: 1.7; color: #1a1a1a; background: #fff; }")
                    append("h2 { color: #111; margin: 0 0 8px 0; }")
                    append("h3, h4 { color: #222; }")
                    append("table { border-collapse: collapse; margin: 8px 0; }")
                    append("th, td { padding: 6px 12px; border: 1px solid #d0d0d0; font-family: monospace; }")
                    append("th { background: #f0f0f0; color: #333; font-weight: bold; }")
                    append("img { max-width: 100%; height: auto; }")
                    append("hr { border: none; border-top: 1px solid #e0e0e0; margin: 12px 0; }")
                    append("pre { background: #f6f8fa; color: #24292e; padding: 12px; border: 1px solid #e1e4e8; font-family: 'JetBrains Mono', monospace; border-radius: 6px; white-space: pre-wrap; line-height: 1.5; }")
                    append("code { background: #f0f2f5; color: #24292e; padding: 2px 6px; border-radius: 4px; font-size: 0.9em; }")
                    append("a { color: #0366d6; pointer-events: none; cursor: default; text-decoration: none; }")
                    append("li { margin: 3px 0; }")
                    append("p { margin: 8px 0; }")
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
                append("img { max-width:100%; height:auto; }")
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
            val metaColor = if (isDark) "#888" else "#666"
            append("<div style='color:$metaColor; font-size:12px; margin-bottom:12px;'>")
            append("$sourceName #${problem.id}")
            if (problem.timeLimit.isNotBlank()) {
                val timeDisplay = if (overrideLang == "en" || (overrideLang == null && I18n.currentLang == I18n.Lang.EN))
                    problem.timeLimit.replace("초", "sec") else problem.timeLimit
                append(" &nbsp;|&nbsp; ${t("시간", "Time")}: $timeDisplay &nbsp;|&nbsp; ${t("메모리", "Memory")}: ${problem.memoryLimit}")
            }
            append("</div>")
            if (useCef) append("<hr>") else append("<hr style='border:none; border-top:1px solid #444; margin:8px 0;'>")

            // SWEA: 불필요 영역 제거 + 이미지 로컬 경로 변환 + 스타일 정리
            var desc = problem.description
            if (problem.source == ProblemSource.SWEA) {
                // 1) 다운로드 링크 태그 제거
                desc = desc.replace(Regex("""<a[^>]*>[^<]*다운로드[^<]*</a>"""), "")
                desc = desc.replace(Regex("""<a[^>]*>[^<]*sample_[^<]*</a>""", RegexOption.IGNORE_CASE), "")
                // 남은 다운로드/파일명 텍스트 제거
                desc = desc.replace(Regex("""\d*_?sample_(?:input|output)\S*\s*다운로드""", RegexOption.IGNORE_CASE), "")
                desc = desc.replace(Regex("""\d*_?sample_(?:input|output)\S*""", RegexOption.IGNORE_CASE), "")

                // 2) 하단 불필요 영역 잘라내기: 댓글, 추천, 무단복제 등
                for (cutText in listOf("댓글", "함께 풀면 도움", "이 Problem과 함께")) {
                    val cutIdx = desc.indexOf(cutText)
                    if (cutIdx > 0) {
                        val tagStart = desc.lastIndexOf('<', cutIdx)
                        desc = if (tagStart > 0 && cutIdx - tagStart < 100) desc.substring(0, tagStart) else desc.substring(0, cutIdx)
                        break
                    }
                }

                // 3) 인라인 샘플 데이터 영역 제거 (예제 입출력과 중복)
                // "다운로드" 텍스트가 남아있으면 그 앞의 "입력" 헤더부터 잘라냄
                val dlIdx = desc.indexOf("다운로드")
                if (dlIdx > 0) {
                    val before = desc.substring(0, dlIdx)
                    var sampleStart = before.lastIndexOf("입력")
                    while (sampleStart > 0 && desc.getOrNull(sampleStart - 1) == '[') {
                        sampleStart = before.lastIndexOf("입력", sampleStart - 1)
                    }
                    if (sampleStart > 0) {
                        val tagStart = desc.lastIndexOf('<', sampleStart)
                        desc = if (tagStart >= 0 && sampleStart - tagStart < 80) desc.substring(0, tagStart) else desc.substring(0, sampleStart)
                    } else {
                        desc = desc.substring(0, dlIdx)
                    }
                }

                // 4) 하이라이트 배경색 제거
                desc = desc.replace(Regex("""background-color\s*:\s*[^;"]+"""), "")
                desc = desc.replace(Regex("""background\s*:\s*(?:rgb\([^)]+\)|#[0-9a-fA-F]+|yellow|white|#fff\b)[^;"]*"""), "")
                // 5) 시간/메모리 제한 중복 제거 (헤더에 이미 표시됨)
                desc = desc.replace(Regex("""<ul>\s*<li>.*?시간.*?</li>\s*<li>.*?메모리.*?</li>\s*</ul>""", RegexOption.DOT_MATCHES_ALL), "")
                // 6) "무단 복제 금지" 경고문 제거
                desc = desc.replace(Regex("""<[^>]*>.*?무단\s*복제.*?</[^>]*>""", RegexOption.DOT_MATCHES_ALL), "")
                // 7) 빈 테이블/빈 요소 정리
                desc = desc.replace(Regex("""<table[^>]*>\s*</table>""", RegexOption.DOT_MATCHES_ALL), "")
                desc = desc.replace(Regex("""<(?:div|p|span)[^>]*>\s*</(?:div|p|span)>"""), "")
                // 시작/끝부분 빈 줄/공백 정리
                desc = desc.replace(Regex("""^(\s*<br\s*/?>|\s*<p>\s*</p>|\s*&nbsp;|\s*<div>\s*</div>)+"""), "")
                desc = desc.replace(Regex("""(\s*<br\s*/?>)+\s*$"""), "")
                // 인라인 color 스타일 제거 (CSS 테마 색상을 따르도록)
                desc = desc.replace(Regex("""(?:;\s*)?color\s*:\s*[^;"]+"""), "")
                // <pre> 블록 내부의 <br> 태그 정리 (이중 개행 방지)
                desc = desc.replace(Regex("""(<pre[^>]*>)(.*?)(</pre>)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) { m ->
                    val content = m.groupValues[2]
                        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                        .replace(Regex("""\n{2,}"""), "\n")
                    m.groupValues[1] + content + m.groupValues[3]
                }
                // 8) 이미지 로컬 경로 변환: JCEF에서는 http://localhost/로, JEditorPane에서는 file://로
                if (currentProblemFolder != null) {
                    desc = desc.replace(Regex("""src="(img_\d+\.\w+)"""")) { match ->
                        val fileName = match.groupValues[1]
                        if (useCef) {
                            "src=\"http://localhost/$fileName\""
                        } else {
                            val localFile = java.io.File(currentProblemFolder, fileName)
                            "src=\"file://${localFile.absolutePath}\""
                        }
                    }
                }
            }

            // <pre> 블록을 LaTeX 처리에서 보호 (테스트 케이스의 $, * 등이 수식으로 변환되는 것을 방지)
            val preBlocks = mutableListOf<String>()
            desc = desc.replace(Regex("""<pre[^>]*>.*?</pre>""", RegexOption.DOT_MATCHES_ALL)) { m ->
                preBlocks.add(m.value)
                "<!--PRE_PLACEHOLDER_${preBlocks.size - 1}-->"
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

            // <pre> 블록 복원
            for ((i, block) in preBlocks.withIndex()) {
                desc = desc.replace("<!--PRE_PLACEHOLDER_$i-->", block)
            }
            append(desc)

            // 예제 입출력 표시 (프로그래머스/리트코드는 description에 이미 포함)
            if (problem.testCases.isNotEmpty()
                && problem.source == ProblemSource.SWEA) {
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
                        val inputHtml = inputPreview.replace("\r", "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                        val outputHtml = outputPreview.replace("\r", "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                        append("<h2>${t("예제 입력", "Sample Input")} ${i + 1}</h2>")
                        append("<div style='$preStyle'>$inputHtml</div>")
                        append("<div style='color:#888; font-size:11px;'>${t("전체 데이터는 <code>input.txt</code> 참고", "See <code>input.txt</code> for full data")}</div>")
                        append("<h2>${t("예제 출력", "Sample Output")} ${i + 1}</h2>")
                        append("<div style='$preStyle'>$outputHtml</div>")
                        append("<div style='color:#888; font-size:11px;'>${t("전체 데이터는 <code>output.txt</code> 참고", "See <code>output.txt</code> for full data")}</div>")
                    } else {
                        val inputHtml = tc.input.replace("\r", "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                        val outputHtml = tc.expectedOutput.replace("\r", "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                        append("<h2>${t("예제 입력", "Sample Input")} ${i + 1}</h2>")
                        append("<div style='$preStyle'>$inputHtml</div>")
                        append("<h2>${t("예제 출력", "Sample Output")} ${i + 1}</h2>")
                        append("<div style='$preStyle'>$outputHtml</div>")
                    }
                }
            }

            // ─── 출처 (Source Attribution) ───
            val problemUrl = when (problem.source) {
                ProblemSource.PROGRAMMERS -> "https://school.programmers.co.kr/learn/courses/30/lessons/${problem.id}"
                ProblemSource.SWEA -> "https://swexpertacademy.com/main/code/problem/problemDetail.do?contestProbId=${problem.contestProbId.ifBlank { problem.id }}"
                ProblemSource.LEETCODE -> "https://leetcode.com/problems/${problem.id}/"
                ProblemSource.CODEFORCES -> "https://codeforces.com/problemset/problem/${problem.contestProbId.ifBlank { problem.id }}"
            }
            val sourceDisplay = when (problem.source) {
                ProblemSource.PROGRAMMERS -> t("프로그래머스 코딩 테스트 연습", "Programmers Coding Test Practice")
                ProblemSource.SWEA -> "SW Expert Academy"
                ProblemSource.LEETCODE -> "LeetCode"
                ProblemSource.CODEFORCES -> "Codeforces"
            }
            append("<hr style='border:none; border-top:1px solid ${if (isDark) "#3c3f41" else "#ddd"}; margin:20px 0 8px 0;'>")
            append("<div style='color:#888; font-size:11px;'>")
            append("${t("출처", "Source")}: $sourceDisplay<br>")
            append("<span style='color:#589df6;'>$problemUrl</span><br>")
            append("${t("이 문제의 저작권은 ${sourceDisplay}에 있습니다. 개인 학습 목적으로만 사용하세요.", "All rights reserved by ${sourceDisplay}. For personal study use only.")}")
            append("</div>")

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
