package com.codingtestkit.service

import com.intellij.testFramework.TestApplicationManager
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * 스파이크(진단) 테스트 — CI(GitHub Actions ubuntu + Xvfb) 환경에서 JCEF가
 * 실제로 초기화되고 OSR로 페인트/rAF/추출이 되는지 확인만 한다.
 *
 * 절대 실패하지 않는다(진단 목적). 결과는 stdout에 "[OSR-SPIKE] ..." 로 찍어
 * CI 로그로 판독한다. 환경변수 CTK_OSR_SPIKE=true 일 때만 실동작(Gradle이 env를
 * 테스트 JVM에 자동 전달), 아니면 즉시 통과.
 *
 * 목적: A(코드포스 OSR CI 자가진단 잡)가 아예 가능한 경로인지 판별.
 * isSupported=false 로 나오면 이 CI 경로는 막힌 것이므로 A를 접는다.
 */
class JcefOsrSpikeTest {

    @Test
    fun `probe JCEF OSR availability and rendering in CI`() {
        if (System.getenv("CTK_OSR_SPIKE") != "true") {
            println("[OSR-SPIKE] skipped (set CTK_OSR_SPIKE=true to run)")
            return
        }

        println("[OSR-SPIKE] os.name=${System.getProperty("os.name")}")
        println("[OSR-SPIKE] java.awt.headless=${System.getProperty("java.awt.headless")}")
        println("[OSR-SPIKE] DISPLAY=${System.getenv("DISPLAY")}")

        // 1) IDE Application 부트스트랩 (JBCefApp가 ApplicationManager를 요구)
        try {
            TestApplicationManager.getInstance()
        } catch (e: Throwable) {
            println("[OSR-SPIKE] TestApplicationManager bootstrap FAILED: ${e.message}")
            return
        }
        println("[OSR-SPIKE] application bootstrapped=true")

        // 2) JBCefApp.isSupported() — CI에서 이게 true여야 A가 가능
        val supported = try {
            JBCefApp.isSupported()
        } catch (e: Throwable) {
            println("[OSR-SPIKE] JBCefApp.isSupported() threw: ${e.message}")
            false
        }
        println("[OSR-SPIKE] JBCefApp.isSupported()=$supported")
        if (!supported) {
            println("[OSR-SPIKE] RESULT: JCEF unavailable in CI → A(코드포스 OSR CI 검증) 경로 막힘")
            return
        }

        // 3) OSR 브라우저로 rAF 게이팅 로컬 HTML을 열어 추출되는지
        val extracted = tryOsrExtraction()
        println("[OSR-SPIKE] OSR rAF extraction succeeded=$extracted")
        println("[OSR-SPIKE] RESULT: ${if (extracted) "A 가능 — OSR이 CI 헤드리스에서 렌더/rAF 동작" else "OSR 생성은 되나 rAF/추출 실패 — setWindowVisibility 검증 대상"}")
    }

    /** requestAnimationFrame 5프레임 뒤 .problem-statement를 심는 HTML을 OSR로 열어 추출 */
    private fun tryOsrExtraction(): Boolean {
        val html = """
            <html><body>
            <script>
              var n = 0;
              function tick(){
                n++;
                if (n >= 5) {
                  var d = document.createElement('div');
                  d.className = 'problem-statement';
                  d.textContent = 'RAF_OK';
                  document.body.appendChild(d);
                } else { requestAnimationFrame(tick); }
              }
              requestAnimationFrame(tick);
            </script>
            </body></html>
        """.trimIndent()
        val dataUrl = "data:text/html;charset=utf-8," +
            java.net.URLEncoder.encode(html, "UTF-8").replace("+", "%20")

        val future = CompletableFuture<Boolean>()
        SwingUtilities.invokeLater {
            try {
                buildAndExtract(dataUrl, future)
            } catch (e: Throwable) {
                println("[OSR-SPIKE] build error: ${e.message}")
                if (!future.isDone) future.complete(false)
            }
        }
        return try {
            future.get(30, TimeUnit.SECONDS)
        } catch (e: Throwable) {
            println("[OSR-SPIKE] extraction timed out/error: ${e.message}")
            false
        }
    }

    private fun buildAndExtract(dataUrl: String, future: CompletableFuture<Boolean>) {
        // CodeforcesJcefFetcher와 동일한 구성: OSR + 핸들러 선등록 + create + setWindowVisibility
        val browser = JBCefBrowser.createBuilder()
            .setUrl(dataUrl)
            .setOffScreenRendering(true)
            .setCreateImmediately(false)
            .build()

        val jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
        jsQuery.addHandler { result ->
            println("[OSR-SPIKE] JSQuery callback fired, result='$result'")
            if (!future.isDone) future.complete(result.contains("RAF_OK"))
            JBCefJSQuery.Response("")
        }

        browser.createImmediately()
        val cb = browser.cefBrowser
        runCatching { cb.wasResized(1024, 768) }
        runCatching { cb.setWindowVisibility(true) }
        runCatching { cb.setFocus(true) }

        // 로드 + rAF가 돌 시간을 준 뒤 폴러 주입 (.problem-statement → JSQuery 신호)
        Timer(4000) {
            val queryJs = jsQuery.inject("html")
            val js = """
                (function(){
                  var el = document.querySelector('.problem-statement');
                  var html = el ? el.textContent : '__NONE__';
                  $queryJs
                })();
            """.trimIndent()
            cb.executeJavaScript(js, cb.url, 0)
        }.apply { isRepeats = false; start() }
    }
}
