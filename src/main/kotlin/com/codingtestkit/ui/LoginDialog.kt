package com.codingtestkit.ui

import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.AuthService
import com.codingtestkit.service.I18n
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCookieVisitor
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

class LoginDialog(project: Project, private val source: ProblemSource) : DialogWrapper(project) {

    private var cookies = ""
    private var username = ""
    private var extracted = false
    private val statusLabel = JLabel(I18n.t("로그인하면 자동으로 완료됩니다...", "Login will complete automatically...")).apply {
        foreground = Color.GRAY
    }

    init {
        title = I18n.t("${source.localizedName()} 로그인", "${source.localizedName()} Login")
        setSize(900, 700)
        setOKButtonText(I18n.t("완료", "Done"))
        init()
    }

    // OK 버튼 숨기고 Cancel만 표시 — 엔터 키로 창이 닫히는 것을 방지
    // 로그인 성공 시 자동으로 doOKAction() 호출됨
    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }

    override fun createCenterPanel(): JComponent {
        if (!JBCefApp.isSupported()) {
            return createFallbackPanel()
        }
        return createBrowserPanel()
    }

    private fun createBrowserPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(880, 650)

        val loginUrl = AuthService.getInstance().getLoginUrl(source)
        val browser = JBCefBrowser(loginUrl)

        // JS → Java 브릿지: 유저네임 추출용
        val usernameQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
        usernameQuery.addHandler { result ->
            username = result.trim()
            null
        }

        // 상단 안내
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        topPanel.add(JLabel("<html>${I18n.t(
            "<b>${source.localizedName()}</b>에 로그인하세요. 로그인하면 자동으로 닫힙니다.",
            "Log in to <b>${source.localizedName()}</b>. The dialog will close automatically after login."
        )}</html>"))
        panel.add(topPanel, BorderLayout.NORTH)

        // 브라우저
        panel.add(browser.component, BorderLayout.CENTER)

        // 하단 상태만 표시 (버튼 없음)
        val bottomPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 8))
        bottomPanel.add(statusLabel)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        // 페이지 로드 완료 시마다 로그인 여부 확인
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true) return

                // 스크롤 성능 개선
                cefBrowser?.executeJavaScript(
                    "(function(){var s=document.createElement('style');" +
                    "s.textContent='html,body{scroll-behavior:smooth}*{-webkit-overflow-scrolling:touch}';" +
                    "document.head.appendChild(s);})();",
                    cefBrowser.url, 0
                )

                if (extracted) return
                val url = cefBrowser?.url ?: ""

                if (isLoggedInUrl(url)) {
                    SwingUtilities.invokeLater {
                        statusLabel.text = I18n.t("로그인 감지! 쿠키 추출 중...", "Login detected! Extracting cookies...")
                        statusLabel.foreground = Color(0, 120, 0)
                    }
                    // DOM에서 유저네임 추출 시도 (결과는 usernameQuery 핸들러로 전달됨)
                    val js = getUsernameJS()
                    if (js.isNotBlank()) {
                        cefBrowser?.executeJavaScript(
                            "setTimeout(function(){var __u=(function(){try{return ($js).trim();}catch(e){return '';}})(); ${usernameQuery.inject("__u")}},300);",
                            cefBrowser.url, 0
                        )
                    }
                    // 쿠키 추출은 약간 딜레이 후 시작 (유저네임 추출 완료 대기)
                    javax.swing.Timer(500) {
                        extractCookiesAndClose(browser)
                    }.apply { isRepeats = false; start() }
                }
            }
        }, browser.cefBrowser)

        return panel
    }

    /**
     * 플랫폼별 유저네임 추출 JS 표현식
     */
    private fun getUsernameJS(): String = when (source) {
        ProblemSource.BAEKJOON ->
            "(document.querySelector('a.username')||document.querySelector('a[href*=\"/user/\"]')||{}).textContent||''"
        ProblemSource.LEETCODE ->
            "(document.querySelector('a[href*=\"/u/\"]')||{}).textContent||''"
        ProblemSource.PROGRAMMERS ->
            "(document.querySelector('.header-user-name')||document.querySelector('[class*=user] [class*=name]')||{}).textContent||''"
        ProblemSource.SWEA ->
            "(document.querySelector('.user_name')||document.querySelector('#userName')||{}).textContent||''"
        ProblemSource.CODEFORCES ->
            "(document.querySelector('a[href*=\"/profile/\"]')||{}).textContent||''"
    }

    private fun isLoggedInUrl(url: String): Boolean {
        return when (source) {
            ProblemSource.BAEKJOON -> url.contains("acmicpc.net") &&
                    !url.contains("/login") && !url.contains("/signin")
            ProblemSource.PROGRAMMERS -> url.contains("programmers.co.kr") &&
                    !url.contains("/sign_in") && !url.contains("/login")
            ProblemSource.SWEA -> url.contains("swexpertacademy") &&
                    !url.contains("login") && !url.contains("signUp")
            ProblemSource.LEETCODE -> url.contains("leetcode.com") &&
                    !url.contains("/accounts/login") && !url.contains("/accounts/signup")
            ProblemSource.CODEFORCES -> url.contains("codeforces.com") &&
                    !url.contains("/enter") && !url.contains("/register")
        }
    }

    /**
     * 쿠키 추출 후 자동으로 다이얼로그 닫기
     */
    private fun extractCookiesAndClose(browser: JBCefBrowser) {
        if (extracted) return
        extracted = true

        val domain = getDomain()
        val cookieManager = browser.jbCefCookieManager?.cefCookieManager
            ?: CefCookieManager.getGlobalManager()
        val cookieBuilder = StringBuilder()
        var callbackFired = false

        // SWEA는 /main 경로에 쿠키가 설정되므로 경로 포함
        val cookieUrl = if (source == ProblemSource.SWEA) "https://$domain/main" else "https://$domain"
        cookieManager.visitUrlCookies(cookieUrl, true, object : CefCookieVisitor {
            override fun visit(cookie: CefCookie, count: Int, total: Int, delete: org.cef.misc.BoolRef): Boolean {
                callbackFired = true
                if (cookieBuilder.isNotEmpty()) cookieBuilder.append("; ")
                cookieBuilder.append("${cookie.name}=${cookie.value}")

                if (count >= total - 1) {
                    SwingUtilities.invokeLater {
                        val result = cookieBuilder.toString()
                        if (result.isNotBlank()) {
                            cookies = result
                            doOKAction()
                        } else {
                            extracted = false
                            statusLabel.text = I18n.t("쿠키를 가져오지 못했습니다. 다시 로그인해주세요.", "Failed to get cookies. Please login again.")
                            statusLabel.foreground = Color.RED
                        }
                    }
                }
                return true
            }
        })

        // 폴백: 3초 후에도 콜백이 안 오면 visitAllCookies로 전체에서 필터링
        javax.swing.Timer(3000) {
            if (!callbackFired && extracted) {
                val allCookieBuilder = StringBuilder()
                cookieManager.visitAllCookies(object : CefCookieVisitor {
                    override fun visit(cookie: CefCookie, count: Int, total: Int, delete: org.cef.misc.BoolRef): Boolean {
                        if (cookie.domain != null && cookie.domain.contains(domain.removePrefix("www."))) {
                            if (allCookieBuilder.isNotEmpty()) allCookieBuilder.append("; ")
                            allCookieBuilder.append("${cookie.name}=${cookie.value}")
                        }
                        if (count >= total - 1) {
                            SwingUtilities.invokeLater {
                                val result = allCookieBuilder.toString()
                                if (result.isNotBlank()) {
                                    cookies = result
                                    doOKAction()
                                } else {
                                    extracted = false
                                    statusLabel.text = I18n.t("쿠키를 가져오지 못했습니다. 다시 시도해주세요.", "Failed to get cookies. Please try again.")
                                    statusLabel.foreground = Color.RED
                                }
                            }
                        }
                        return true
                    }
                })
            }
        }.apply { isRepeats = false; start() }
    }

    /**
     * JCEF 미지원 시 쿠키 수동 입력 (fallback)
     */
    private fun createFallbackPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.preferredSize = Dimension(500, 350)
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val auth = AuthService.getInstance()

        panel.add(JLabel("<html>${I18n.t(
                "<b>JCEF 브라우저를 사용할 수 없습니다.</b><br><br>" +
                "1. 브라우저에서 <b>${getDomain()}</b>에 로그인<br>" +
                "2. F12 > Console > <code>document.cookie</code> 입력<br>" +
                "3. 결과를 아래에 붙여넣기",
                "<b>JCEF browser is not available.</b><br><br>" +
                "1. Log in to <b>${getDomain()}</b> in your browser<br>" +
                "2. F12 > Console > type <code>document.cookie</code><br>" +
                "3. Paste the result below"
        )}</html>"), BorderLayout.NORTH)

        val cookieField = JTextArea(6, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createTitledBorder(I18n.t("쿠키 값", "Cookie Value"))
        }
        cookieField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { cookies = cookieField.text.trim() }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { cookies = cookieField.text.trim() }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { cookies = cookieField.text.trim() }
        })
        panel.add(JScrollPane(cookieField), BorderLayout.CENTER)

        val openBtn = JButton(I18n.t("브라우저에서 로그인 페이지 열기", "Open login page in browser"))
        openBtn.addActionListener {
            try { java.awt.Desktop.getDesktop().browse(java.net.URI(auth.getLoginUrl(source))) }
            catch (_: Exception) {}
        }
        panel.add(openBtn, BorderLayout.SOUTH)

        return panel
    }

    private fun getDomain(): String = when (source) {
        ProblemSource.BAEKJOON -> "www.acmicpc.net"
        ProblemSource.PROGRAMMERS -> "school.programmers.co.kr"
        ProblemSource.SWEA -> "swexpertacademy.com"
        ProblemSource.LEETCODE -> "leetcode.com"
        ProblemSource.CODEFORCES -> "codeforces.com"
    }

    fun getCookies(): String = cookies
    fun getUsername(): String = username
}
