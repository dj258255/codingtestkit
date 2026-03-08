package com.codingtestkit.ui

import com.codingtestkit.service.GitHubService
import com.codingtestkit.service.I18n
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.*
import javax.swing.*

/**
 * JCEF 브라우저로 GitHub PAT 생성 페이지를 열어 토큰을 자동 캡처하는 다이얼로그.
 * 1. github.com/settings/tokens/new 로 이동 (미로그인 시 자동 로그인 리다이렉트)
 * 2. 유저가 "Generate token" 클릭
 * 3. 생성된 토큰(ghp_... 또는 github_pat_...)을 JS로 감지 → 자동 저장 & 닫기
 */
class GitHubLoginDialog(private val project: Project?) : DialogWrapper(project) {

    private val TOKEN_URL = "https://github.com/settings/tokens/new?description=CodingTestKit&scopes=repo"

    var capturedToken: String? = null
        private set

    private var browser: JBCefBrowser? = null
    private var pollTimer: Timer? = null
    private var captured = false

    private val statusLabel = JLabel(
        I18n.t("GitHub에 로그인한 후 'Generate token'을 클릭하세요.",
            "Log in to GitHub, then click 'Generate token'.")
    ).apply {
        foreground = Color.GRAY
        font = font.deriveFont(JBUI.scaleFontSize(12f).toFloat())
    }

    init {
        title = I18n.t("GitHub 로그인 & 토큰 생성", "GitHub Login & Token Generation")
        setCancelButtonText(I18n.t("닫기", "Close"))
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(cancelAction)

    override fun createCenterPanel(): JComponent {
        if (!JBCefApp.isSupported()) return createFallbackPanel()
        return createBrowserPanel()
    }

    private fun createBrowserPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(JBUI.scale(920), JBUI.scale(680))

        val jbBrowser = JBCefBrowser(TOKEN_URL)
        this.browser = jbBrowser

        // 상단 안내
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)))
        topPanel.add(JLabel(
            I18n.t("<html><b>GitHub</b>에 로그인 → 토큰 생성 → 자동으로 저장됩니다.</html>",
                "<html>Log in to <b>GitHub</b> → Generate token → Auto-saved.</html>")
        ).apply {
            font = font.deriveFont(JBUI.scaleFontSize(13f).toFloat())
        })
        panel.add(topPanel, BorderLayout.NORTH)

        // 브라우저
        panel.add(jbBrowser.component, BorderLayout.CENTER)

        // 하단 상태
        val bottomPanel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(8), JBUI.scale(6)))
        bottomPanel.add(statusLabel)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        // title 변경 감지 (토큰 캡처)
        jbBrowser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onTitleChange(cefBrowser: CefBrowser?, newTitle: String?) {
                if (newTitle != null && newTitle.startsWith("__CTK_GH_TOKEN__") && !captured) {
                    captured = true
                    val token = newTitle.removePrefix("__CTK_GH_TOKEN__")
                    SwingUtilities.invokeLater {
                        pollTimer?.stop()
                        capturedToken = token
                        saveToken(token)
                    }
                }
            }
        }, jbBrowser.cefBrowser)

        // 페이지 로드 완료 시 토큰 감지 JS 시작
        jbBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true) return
                val url = cefBrowser?.url ?: ""
                if (url.contains("github.com/settings/tokens")) {
                    startPolling(cefBrowser)
                }
            }
        }, jbBrowser.cefBrowser)

        return panel
    }

    private fun startPolling(cefBrowser: CefBrowser?) {
        pollTimer?.stop()
        pollTimer = Timer(1500) {
            if (captured || cefBrowser == null) return@Timer
            cefBrowser.executeJavaScript(TOKEN_DETECT_JS, cefBrowser.url, 0)
        }
        pollTimer?.start()
    }

    private fun saveToken(token: String) {
        val github = GitHubService.getInstance()
        github.setToken(token)

        statusLabel.text = I18n.t("✓ 토큰이 자동 저장되었습니다. 필요하면 토큰을 복사한 후 창을 닫으세요.",
            "✓ Token auto-saved. Copy the token if needed, then close this window.")
        statusLabel.foreground = Color(0, 130, 0)
    }

    private fun createFallbackPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.preferredSize = Dimension(JBUI.scale(500), JBUI.scale(300))
        panel.border = JBUI.Borders.empty(10)

        panel.add(JLabel(I18n.t(
            "<html><b>JCEF 브라우저를 사용할 수 없습니다.</b><br><br>" +
                    "GitHub에서 직접 토큰을 생성하세요:<br>" +
                    "Settings → Developer settings → Personal access tokens → Generate new token (classic)<br>" +
                    "scope: <b>repo</b> 선택</html>",
            "<html><b>JCEF browser not available.</b><br><br>" +
                    "Create a token directly on GitHub:<br>" +
                    "Settings → Developer settings → Personal access tokens → Generate new token (classic)<br>" +
                    "Select scope: <b>repo</b></html>"
        )), BorderLayout.CENTER)

        val openBtn = JButton(I18n.t("GitHub에서 토큰 생성", "Create token on GitHub"))
        openBtn.addActionListener {
            try { Desktop.getDesktop().browse(java.net.URI(TOKEN_URL)) }
            catch (_: Exception) {}
        }
        panel.add(openBtn, BorderLayout.SOUTH)

        return panel
    }

    override fun dispose() {
        pollTimer?.stop()
        browser?.dispose()
        super.dispose()
    }

    companion object {
        /**
         * GitHub 토큰 생성 페이지에서 새로 생성된 토큰을 감지하는 JS.
         * ghp_ (classic) 또는 github_pat_ (fine-grained) 패턴 매칭.
         */
        private val TOKEN_DETECT_JS = """
            (function() {
                if (document.title.startsWith('__CTK_GH_TOKEN__')) return;
                // Classic token: shown in a code/input element after generation
                var els = document.querySelectorAll(
                    '#new-oauth-token code, [data-clipboard-text], input[value^="ghp_"], input[value^="github_pat_"], .token code'
                );
                for (var i = 0; i < els.length; i++) {
                    var token = els[i].getAttribute('data-clipboard-text')
                        || els[i].value
                        || els[i].textContent;
                    if (token && (token.startsWith('ghp_') || token.startsWith('github_pat_'))) {
                        document.title = '__CTK_GH_TOKEN__' + token.trim();
                        return;
                    }
                }
                // Also check flash/alert messages
                var flashEls = document.querySelectorAll('.flash code, .flash-full code');
                for (var j = 0; j < flashEls.length; j++) {
                    var text = flashEls[j].textContent.trim();
                    if (text.startsWith('ghp_') || text.startsWith('github_pat_')) {
                        document.title = '__CTK_GH_TOKEN__' + text;
                        return;
                    }
                }
            })();
        """.trimIndent()

    }
}
