package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Dimension
import java.awt.Window
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * JCEF 오프스크린 브라우저를 사용한 Codeforces 문제 가져오기 폴백.
 *
 * Codeforces는 Cloudflare가 비브라우저 TLS 지문(Jsoup/Java)을 403으로 차단할 수 있다.
 * 실제 Chromium(JCEF)으로 페이지를 열면 챌린지를 통과하므로, 렌더링된 HTML을
 * 추출해 CodeforcesCrawler의 기존 파서에 넘긴다. (SweaJcefFetcher와 동일 패턴)
 */
object CodeforcesJcefFetcher {

    private val LOG = Logger.getInstance(CodeforcesJcefFetcher::class.java)

    var lastError: String = ""
        private set

    // Cloudflare 챌린지 통과 시간(수 초~수십 초)을 포함한 타임아웃
    private const val FETCH_TIMEOUT_MS = 60_000L

    fun isAvailable(): Boolean = try {
        JBCefApp.isSupported()
    } catch (_: Exception) {
        false
    }

    /**
     * 백그라운드 스레드에서 호출할 것 (EDT에서 호출하면 deadlock).
     */
    fun fetchProblem(problemId: String): Problem? {
        if (!isAvailable()) {
            lastError = "JCEF not available"
            return null
        }

        val future = CompletableFuture<Problem?>()

        SwingUtilities.invokeLater {
            try {
                FetchSession(problemId, future).start()
            } catch (e: Exception) {
                LOG.warn("[CodingTestKit] CodeforcesJcefFetcher init failed", e)
                lastError = "Init: ${e.message}"
                if (!future.isDone) future.complete(null)
            }
        }

        return try {
            future.get(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            lastError = "Timeout (${FETCH_TIMEOUT_MS / 1000}s)"
            LOG.info("[CodingTestKit] Codeforces JCEF fetch timeout: $problemId")
            null
        } catch (e: Exception) {
            lastError = "Error: ${e.message}"
            LOG.warn("[CodingTestKit] Codeforces JCEF fetch error", e)
            null
        }
    }

    private class FetchSession(
        private val problemId: String,
        private val future: CompletableFuture<Problem?>
    ) {
        private val done = AtomicBoolean(false)
        private var frame: JFrame? = null
        private var browser: JBCefBrowser? = null
        private var timeoutTimer: Timer? = null

        fun start() {
            val (contestId, letter) = CodeforcesCrawler.parseProblemId(problemId)
            val url = "https://codeforces.com/problemset/problem/$contestId/$letter?locale=en"

            frame = JFrame().apply {
                type = Window.Type.UTILITY
                isUndecorated = true
                size = Dimension(1024, 768)
                setLocation(-3000, -3000)
            }

            browser = JBCefBrowser(url)
            frame!!.contentPane.add(browser!!.component)
            frame!!.isVisible = true

            LOG.info("[CodingTestKit] Codeforces JCEF fetch started: $problemId")

            val jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
            jsQuery.addHandler { html ->
                handleHtml(html)
                JBCefJSQuery.Response("")
            }

            timeoutTimer = Timer((FETCH_TIMEOUT_MS - 5000).toInt()) {
                complete(null, "JCEF internal timeout")
            }.apply { isRepeats = false; start() }

            browser!!.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(cefBrowser: CefBrowser?, cefFrame: CefFrame?, httpStatusCode: Int) {
                    if (cefFrame?.isMain != true || done.get()) return
                    LOG.info("[CodingTestKit] Codeforces JCEF loaded: ${cefBrowser?.url} (status=$httpStatusCode)")
                    // Cloudflare 챌린지 페이지일 수 있으므로 .problem-statement가
                    // 나타날 때까지 폴링. 챌린지 통과 후 리다이렉트되면 onLoadEnd가
                    // 다시 불리고 새 문서에 폴러가 재주입된다.
                    startPolling(cefBrowser, jsQuery)
                }
            }, browser!!.cefBrowser)
        }

        private fun startPolling(cefBrowser: CefBrowser?, jsQuery: JBCefJSQuery) {
            if (cefBrowser == null || done.get()) return
            val queryJs = jsQuery.inject("html")
            val js = """
                (function() {
                    if (window.__ctkCfPoller) return;
                    var attempts = 0;
                    window.__ctkCfPoller = setInterval(function() {
                        attempts++;
                        if (document.querySelector('.problem-statement')) {
                            clearInterval(window.__ctkCfPoller);
                            var html = document.documentElement.outerHTML;
                            $queryJs
                        } else if (attempts >= 90) {
                            clearInterval(window.__ctkCfPoller);
                            var html = '';
                            $queryJs
                        }
                    }, 500);
                })();
            """.trimIndent()
            cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
        }

        private fun handleHtml(html: String) {
            if (html.isBlank() || !html.contains("problem-statement")) {
                complete(null, "Cloudflare challenge not passed or unexpected page")
                return
            }
            try {
                val problem = CodeforcesCrawler.parseProblemHtml(html, problemId)
                if (problem.title.isNotBlank() || problem.description.length > 50) {
                    complete(problem)
                } else {
                    complete(null, "Parsed content empty")
                }
            } catch (e: Exception) {
                complete(null, "Parse: ${e.message}")
            }
        }

        private fun complete(problem: Problem?, error: String? = null) {
            if (!done.compareAndSet(false, true)) return
            if (error != null) {
                lastError = error
                LOG.info("[CodingTestKit] Codeforces JCEF fetch error: $error")
            } else {
                LOG.info("[CodingTestKit] Codeforces JCEF fetch OK: ${problem?.title}")
            }
            timeoutTimer?.stop()

            SwingUtilities.invokeLater {
                try { browser?.dispose() } catch (_: Exception) {}
                try { frame?.dispose() } catch (_: Exception) {}
            }

            if (!future.isDone) future.complete(problem)
        }
    }
}
