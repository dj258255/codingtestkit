package com.codingtestkit.ui

import com.codingtestkit.model.Problem
import com.codingtestkit.service.CodeforcesCrawler
import com.codingtestkit.service.I18n
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.*

/**
 * Codeforces 문제 가져오기 다이얼로그 (보이는 브라우저 폴백, 이슈 #30).
 *
 * 오프스크린(OSR) 폴백이 실패하는 환경 — 예: Linux/Wayland에서 OSR 렌더링이
 * 안 되거나, Cloudflare가 클릭이 필요한 인터랙티브 챌린지를 내는 경우 — 을 위한
 * 최종 폴백. 제목줄·닫기 버튼이 있는 정식 다이얼로그에 실브라우저를 띄워
 * 사용자가 직접 챌린지를 통과하게 하고, .problem-statement가 나타나면 자동으로
 * HTML을 추출해 CodeforcesCrawler 파서에 넘기고 닫는다. (SweaFetchDialog와 동일 패턴)
 */
class CodeforcesFetchDialog(
    project: Project,
    private val problemId: String
) : DialogWrapper(project) {

    private var problem: Problem? = null
    private var extracted = false

    private val statusLabel = JLabel(I18n.t(
        "페이지를 여는 중입니다. Cloudflare 확인 화면이 뜨면 완료해 주세요.",
        "Opening the page. If a Cloudflare check appears, please complete it."
    )).apply { foreground = Color.GRAY }

    init {
        title = I18n.t("Codeforces 문제 가져오기", "Fetch Codeforces Problem")
        setSize(900, 700)
        init()
    }

    override fun createActions(): Array<Action> {
        myCancelAction.putValue(Action.NAME, I18n.t("취소", "Cancel"))
        return arrayOf(cancelAction)
    }

    override fun createCenterPanel(): JComponent {
        if (!JBCefApp.isSupported()) {
            return JLabel("<html>${I18n.t(
                "JCEF를 사용할 수 없어 Codeforces 문제를 가져올 수 없습니다.",
                "Cannot fetch Codeforces problems: JCEF is not available."
            )}</html>")
        }
        return createBrowserPanel()
    }

    private fun createBrowserPanel(): JComponent {
        val (contestId, letter) = CodeforcesCrawler.parseProblemId(problemId)
        val url = "https://codeforces.com/problemset/problem/$contestId/$letter?locale=en"

        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(880, 660)

        val header = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 12, 8, 12)
        }
        header.add(JLabel("<html><b>${I18n.t("Codeforces #$problemId", "Codeforces #$problemId")}</b></html>"), BorderLayout.NORTH)
        header.add(statusLabel, BorderLayout.CENTER)
        panel.add(header, BorderLayout.NORTH)

        // 사용자에게 보이는 브라우저 (챌린지 직접 통과용)
        val browser = JBCefBrowser(url)
        val bWrapper = browser.component
        bWrapper.preferredSize = Dimension(860, 580)
        panel.add(bWrapper, BorderLayout.CENTER)

        val jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
        jsQuery.addHandler { html ->
            handleHtml(html)
            JBCefJSQuery.Response("")
        }

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true || extracted) return
                val url = cefBrowser?.url ?: ""
                // 챌린지 통과 후 리다이렉트되면 onLoadEnd가 다시 불리고 폴러가 재주입됨
                startPolling(cefBrowser, jsQuery)
                // 사용자가 챌린지를 통과해 문제 페이지가 로드되면 cf_clearance가 생기므로,
                // JS 추출이 안 되는 환경을 대비해 쿠키로 Jsoup 재시도를 병행 (이슈 #30)
                if (httpStatusCode == 200 && url.contains("/problem")) {
                    tryCookieRetry()
                }
            }
        }, browser.cefBrowser)

        return panel
    }

    @Volatile private var cookieRetryStarted = false

    /** 페이지 로드 후 cf_clearance 쿠키로 Jsoup 재시도 (JS 추출 우회, 이슈 #30) */
    private fun tryCookieRetry() {
        if (cookieRetryStarted) return
        cookieRetryStarted = true
        Thread {
            try {
                Thread.sleep(1500)
                if (extracted) return@Thread
                val parsed = CodeforcesCrawler.fetchProblem(problemId)
                if (parsed.title.isNotBlank() || parsed.description.length > 50) {
                    SwingUtilities.invokeLater {
                        if (extracted) return@invokeLater
                        extracted = true
                        problem = parsed
                        statusLabel.text = I18n.t("문제를 가져왔습니다.", "Problem fetched.")
                        statusLabel.foreground = Color(0, 120, 0)
                        doOKAction()
                    }
                }
            } catch (_: Exception) { /* 폴러가 아직 성공할 수 있으므로 무시 */ }
        }.apply { isDaemon = true; start() }
    }

    private fun startPolling(cefBrowser: CefBrowser?, jsQuery: JBCefJSQuery) {
        if (cefBrowser == null || extracted) return
        val queryJs = jsQuery.inject("html")
        // .problem-statement가 나타날 때까지 폴링 (사용자가 챌린지를 통과하는 시간 포함, 최대 5분).
        // 다이얼로그가 열려 있는 동안 계속 대기하므로 OSR 폴백보다 넉넉하게 잡는다.
        val js = """
            (function() {
                if (window.__ctkCfDlgPoller) return;
                var attempts = 0;
                window.__ctkCfDlgPoller = setInterval(function() {
                    attempts++;
                    if (document.querySelector('.problem-statement')) {
                        clearInterval(window.__ctkCfDlgPoller);
                        window.__ctkCfDlgPoller = null;
                        var html = document.documentElement.outerHTML;
                        $queryJs
                    } else if (attempts >= 600) {
                        clearInterval(window.__ctkCfDlgPoller);
                        window.__ctkCfDlgPoller = null;
                    }
                }, 500);
            })();
        """.trimIndent()
        cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
    }

    private fun handleHtml(html: String) {
        if (extracted) return
        if (html.isBlank() || !html.contains("problem-statement")) return
        extracted = true
        SwingUtilities.invokeLater {
            try {
                val parsed = CodeforcesCrawler.parseProblemHtml(html, problemId)
                if (parsed.title.isNotBlank() || parsed.description.length > 50) {
                    problem = parsed
                    statusLabel.text = I18n.t("문제를 가져왔습니다.", "Problem fetched.")
                    statusLabel.foreground = Color(0, 120, 0)
                    doOKAction()
                } else {
                    extracted = false
                }
            } catch (_: Exception) {
                extracted = false
            }
        }
    }

    fun getProblem(): Problem? = problem
}
