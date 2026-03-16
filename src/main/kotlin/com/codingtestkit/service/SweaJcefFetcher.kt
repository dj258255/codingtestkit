package com.codingtestkit.service

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import com.google.gson.JsonParser
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
 * JCEF 오프스크린 브라우저를 사용한 SWEA 문제 가져오기.
 * 다이얼로그 없이 백그라운드에서 자동 fetch.
 *
 * SweaFetchDialog와 동일한 JCEF + JS 추출 로직을 숨겨진 프레임에서 실행.
 * IntelliJ 내장 JCEF를 사용하므로 추가 의존성 없이 동작.
 */
object SweaJcefFetcher {

    private val LOG = Logger.getInstance(SweaJcefFetcher::class.java)

    var lastError: String = ""
        private set

    private const val SWEA_BASE = "https://swexpertacademy.com"
    private const val DETAIL_URL = "$SWEA_BASE/main/code/problem/problemDetail.do"
    private const val LIST_URL = "$SWEA_BASE/main/code/problem/problemList.do"
    private const val FETCH_TIMEOUT_MS = 45_000L

    fun isAvailable(): Boolean = try {
        JBCefApp.isSupported()
    } catch (_: Exception) {
        false
    }

    /**
     * 백그라운드 스레드에서 호출. JCEF 오프스크린 브라우저로 SWEA 문제를 가져옴.
     * EDT에서 호출하면 deadlock 발생하므로 반드시 백그라운드 스레드에서 호출할 것.
     */
    fun fetchProblem(contestProbId: String): Problem? {
        if (!isAvailable()) {
            lastError = "JCEF not available"
            return null
        }

        val future = CompletableFuture<Problem?>()

        SwingUtilities.invokeLater {
            try {
                FetchSession(contestProbId, future).start()
            } catch (e: Exception) {
                LOG.warn("[CodingTestKit] SweaJcefFetcher init failed", e)
                lastError = "Init: ${e.message}"
                if (!future.isDone) future.complete(null)
            }
        }

        return try {
            future.get(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            lastError = "Timeout (${FETCH_TIMEOUT_MS / 1000}s)"
            LOG.info("[CodingTestKit] JCEF fetch timeout: $contestProbId")
            null
        } catch (e: Exception) {
            lastError = "Error: ${e.message}"
            LOG.warn("[CodingTestKit] JCEF fetch error", e)
            null
        }
    }

    // ─── 내부 fetch 세션 ───

    private class FetchSession(
        private val contestProbId: String,
        private val future: CompletableFuture<Problem?>
    ) {
        private val done = AtomicBoolean(false)
        private var frame: JFrame? = null
        private var browser: JBCefBrowser? = null
        private var timeoutTimer: Timer? = null

        fun start() {
            frame = JFrame().apply {
                type = Window.Type.UTILITY
                isUndecorated = true
                size = Dimension(1024, 768)
                setLocation(-3000, -3000)
            }

            browser = JBCefBrowser(LIST_URL)
            frame!!.contentPane.add(browser!!.component)
            frame!!.isVisible = true

            LOG.info("[CodingTestKit] JCEF fetch session started: $contestProbId")

            val jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
            jsQuery.addHandler { result ->
                handleExtractResult(result)
                JBCefJSQuery.Response("")
            }

            timeoutTimer = Timer((FETCH_TIMEOUT_MS - 5000).toInt()) {
                complete(null, "JCEF internal timeout")
            }.apply { isRepeats = false; start() }

            var listPageLoaded = false
            browser!!.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(cefBrowser: CefBrowser?, cefFrame: CefFrame?, httpStatusCode: Int) {
                    if (cefFrame?.isMain != true || done.get()) return
                    val url = cefBrowser?.url ?: ""
                    LOG.info("[CodingTestKit] JCEF loaded: $url (status=$httpStatusCode)")

                    when {
                        !listPageLoaded && url.contains("problemList") -> {
                            listPageLoaded = true
                            submitFormToDetail(cefBrowser)
                        }
                        url.contains("login") || url.contains("signUp") -> {
                            complete(null, "SWEA login required")
                        }
                        url.contains("problemDetail") || listPageLoaded -> {
                            waitAndExtract(cefBrowser, jsQuery)
                        }
                    }
                }
            }, browser!!.cefBrowser)
        }

        private fun complete(problem: Problem?, error: String? = null) {
            if (!done.compareAndSet(false, true)) return
            if (error != null) {
                lastError = error
                LOG.info("[CodingTestKit] JCEF fetch error: $error")
            } else {
                LOG.info("[CodingTestKit] JCEF fetch OK: ${problem?.title}")
            }
            timeoutTimer?.stop()

            SwingUtilities.invokeLater {
                try { browser?.dispose() } catch (_: Exception) {}
                try { frame?.dispose() } catch (_: Exception) {}
            }

            if (!future.isDone) future.complete(problem)
        }

        private fun handleExtractResult(jsonStr: String) {
            val problem = parseResult(jsonStr, contestProbId)
            if (problem != null && problem.description.length > 30) {
                complete(problem)
            } else {
                complete(null, "Content too short (desc=${problem?.description?.length ?: 0})")
            }
        }

        private fun submitFormToDetail(cefBrowser: CefBrowser?) {
            val js = """
                (function() {
                    var form = document.createElement('form');
                    form.method = 'POST';
                    form.action = '$DETAIL_URL';
                    var fields = {
                        contestProbId: '$contestProbId',
                        categoryId: '$contestProbId',
                        categoryType: 'CODE'
                    };
                    for (var key in fields) {
                        var input = document.createElement('input');
                        input.type = 'hidden';
                        input.name = key;
                        input.value = fields[key];
                        form.appendChild(input);
                    }
                    document.body.appendChild(form);
                    form.submit();
                })();
            """.trimIndent()
            cefBrowser?.executeJavaScript(js, cefBrowser.url, 0)
        }

        private fun waitAndExtract(cefBrowser: CefBrowser?, jsQuery: JBCefJSQuery) {
            if (cefBrowser == null || done.get()) return
            val queryJs = jsQuery.inject("jsonStr")
            val js = buildExtractionJs(queryJs)
            cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
        }
    }

    // ─── JS 추출 코드 (SweaFetchDialog와 동일) ───

    private fun buildExtractionJs(queryJs: String): String = """
        (function() {
            var attempts = 0;
            var maxAttempts = 30;
            var poller = setInterval(function() {
                attempts++;
                var ready = false;
                var ngBindEls = document.querySelectorAll('[ng-bind-html]');
                for (var i = 0; i < ngBindEls.length; i++) {
                    if (ngBindEls[i].innerHTML && ngBindEls[i].innerHTML.trim().length > 30) {
                        ready = true;
                        break;
                    }
                }
                if (!ready) {
                    var bodyText = document.body ? (document.body.innerText || '') : '';
                    ready = (bodyText.indexOf('\uc2dc\uac04') >= 0 || bodyText.indexOf('\uba54\ubaa8\ub9ac') >= 0) && bodyText.length > 3000;
                }
                if (ready || attempts >= maxAttempts) {
                    clearInterval(poller);
                    extractAndSend(attempts >= maxAttempts);
                }
            }, 500);

            function extractAndSend(force) {
                var result = {};

                var titleSelectors = [
                    'div.problem_box > p.problem_title',
                    '.problem_title',
                    '#problem_title',
                    'div.problem_box > h3'
                ];
                result.title = '';
                result.problemNumber = '';
                for (var i = 0; i < titleSelectors.length; i++) {
                    var el = document.querySelector(titleSelectors[i]);
                    if (el) {
                        var clone = el.cloneNode(true);
                        var badge = clone.querySelector('.badge');
                        if (badge) badge.remove();
                        var text = clone.textContent.trim();
                        var numMatch = text.match(/^([0-9]+)\.\s*/);
                        if (numMatch) result.problemNumber = numMatch[1];
                        text = text.replace(/^[0-9]+\.\s*/, '');
                        if (text) {
                            result.title = text;
                            break;
                        }
                    }
                }
                if (!result.title) {
                    result.title = document.title || '';
                }
                if (!result.problemNumber) {
                    var docTitle = document.title || '';
                    var dtMatch = docTitle.match(/(\d{4,6})\./);
                    if (dtMatch) result.problemNumber = dtMatch[1];
                }
                if (!result.problemNumber) {
                    var headers = document.querySelectorAll('h2, h3, h4');
                    for (var hi = 0; hi < headers.length; hi++) {
                        var hText = (headers[hi].textContent || '').trim();
                        var hMatch = hText.match(/^(\d{4,6})\.\s/);
                        if (hMatch) { result.problemNumber = hMatch[1]; break; }
                    }
                }

                result.description = '';

                var isCommentContent = function(html) {
                    var text = html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
                    if (text.match(/\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}/) && text.indexOf('\ub313\uae00') >= 0) return true;
                    if (text.match(/\d+\s*\/\s*\d+\uc790/)) return true;
                    if (text.indexOf('\ub4f1\ub85d') >= 0 && text.match(/@\S+/)) return true;
                    return false;
                };

                var bindHtmlEls = document.querySelectorAll('[ng-bind-html]');
                var contentParts = [];
                for (var i = 0; i < bindHtmlEls.length; i++) {
                    var el = bindHtmlEls[i];
                    var html = el.innerHTML;
                    if (!html || html.trim().length < 15) continue;
                    var text = el.textContent ? el.textContent.trim() : '';
                    if (/\u203b.*\ubb34\ub2e8\s*\ubcf5\uc81c/.test(text)) continue;
                    if (isCommentContent(html)) continue;
                    var isDup = false;
                    for (var ci = 0; ci < contentParts.length; ci++) {
                        if (contentParts[ci] === html || contentParts[ci].indexOf(html) >= 0) { isDup = true; break; }
                    }
                    if (!isDup) contentParts.push(html);
                }
                if (contentParts.length > 0) {
                    result.description = contentParts.join('<hr>');
                }

                if (!result.description || result.description.length < 100) {
                    var box4 = document.querySelector('div.box4');
                    if (box4 && box4.innerHTML.length > 100) {
                        result.description = box4.innerHTML;
                    }
                }

                if (!result.description || result.description.length < 100) {
                    var ptxt = document.querySelector('p.txt');
                    if (ptxt && ptxt.innerHTML.length > 100) {
                        result.description = ptxt.innerHTML;
                    }
                }

                if (!result.description || result.description.length < 100) {
                    var allEls = document.querySelectorAll('div, section, p');
                    var bestEl = null;
                    var bestLen = Infinity;
                    for (var i = 0; i < allEls.length; i++) {
                        var el = allEls[i];
                        var text = el.textContent || '';
                        var html = el.innerHTML;
                        if ((text.indexOf('[\uc785\ub825]') >= 0 || text.indexOf('[\ucd9c\ub825]') >= 0) &&
                            html.length > 200 && html.length < 50000) {
                            if (!text.match(/\ucc38\uc5ec\uc790.*\uc81c\ucd9c.*\uc815\ub2f5/) && !isCommentContent(html)) {
                                if (html.length < bestLen) { bestEl = el; bestLen = html.length; }
                            }
                        }
                    }
                    if (bestEl) result.description = bestEl.innerHTML;
                }

                if ((!result.description || result.description.length < 100) && typeof angular !== 'undefined') {
                    try {
                        var ctrls = document.querySelectorAll('[ng-controller]');
                        for (var ci = 0; ci < ctrls.length; ci++) {
                            try {
                                var sc = angular.element(ctrls[ci]).scope();
                                for (var s = sc; s; s = s.${'$'}parent) {
                                    if (s.contestProb && s.contestProb.problemCont) {
                                        var pc = s.contestProb.problemCont;
                                        if (typeof pc === 'string' && pc.length > 100) {
                                            result.description = pc;
                                            break;
                                        }
                                    }
                                }
                                if (result.description && result.description.length >= 100) break;
                            } catch(e) {}
                        }
                    } catch(e) {}
                }

                if (!result.description || result.description.length < 100) {
                    var descSelectors = ['#problemContent', '.problem_description', '.problemContent',
                        '.problem_txt', '.desc_box', '.problem_content', '.view_content'];
                    for (var i = 0; i < descSelectors.length; i++) {
                        var el = document.querySelector(descSelectors[i]);
                        if (el && el.innerHTML.length > 100) { result.description = el.innerHTML; break; }
                    }
                }

                if (result.description) {
                    result.description = result.description.replace(/&amp;/g, '&');
                    result.description = result.description.replace(/ng-src="/g, 'src="');
                    result.description = result.description.replace(/data-src="/g, 'src="');
                    result.description = result.description.replace(/src="\/([^"]*?)"/g, 'src="https://swexpertacademy.com/${'$'}1"');
                    result.description = result.description.replace(/src="(?!https?:|data:|file:)([^"]*?)"/g, 'src="https://swexpertacademy.com/${'$'}1"');
                    result.description = result.description.replace(/<a[^>]*>[^<]*\ub2e4\uc6b4\ub85c\ub4dc[^<]*<\/a>/gi, '');
                    result.description = result.description.replace(/<a[^>]*>[^<]*download[^<]*<\/a>/gi, '');
                    result.description = result.description.replace(/<button[^>]*>[^<]*\ub2e4\uc6b4\ub85c\ub4dc[^<]*<\/button>/gi, '');
                    result.description = result.description.replace(/\S*input\S*\.txt/gi, '');
                    result.description = result.description.replace(/\S*output\S*\.txt/gi, '');
                }

                result.difficulty = '';
                var badgeEl = document.querySelector('.badge, [class*="badge"], [class*="difficulty"]');
                if (badgeEl) {
                    var badgeText = badgeEl.textContent.trim();
                    if (badgeText.match(/D\d/)) result.difficulty = badgeText;
                }
                if (!result.difficulty) {
                    var titleEl = document.querySelector('.problem_title, h2, h3');
                    if (titleEl) {
                        var dMatch = titleEl.textContent.match(/D(\d)/);
                        if (dMatch) result.difficulty = 'D' + dMatch[1];
                    }
                }

                result.timeLimit = '';
                result.memoryLimit = '';
                var allLi = document.querySelectorAll('li');
                for (var i = 0; i < allLi.length; i++) {
                    var text = allLi[i].textContent.replace(/\s+/g, ' ').trim();
                    if (text.length > 80) continue;
                    if (text.match(/\uc2dc\uac04|Time/i) && text.match(/\d+\s*\ucd08|\d+\s*sec/i)) {
                        result.timeLimit = text.replace(/^[\u00b7\u2022\s]*(\uc2dc\uac04|Time\s*Limit)\s*:?\s*/i, '').trim();
                    }
                    if (text.match(/\uba54\ubaa8\ub9ac|Memory/i) && text.match(/\d+\s*MB|\d+\s*KB/i)) {
                        result.memoryLimit = text.replace(/^[\u00b7\u2022\s]*(\uba54\ubaa8\ub9ac|Memory\s*Limit)\s*:?\s*/i, '').trim();
                    }
                }

                var cpId = '';
                var urlMatch = window.location.href.match(/contestProbId=([A-Za-z0-9_-]+)/);
                if (urlMatch) cpId = urlMatch[1];
                result.contestProbId = cpId;

                result.testCases = [];
                var inputText = '';
                var outputText = '';

                var cleanTestData = function(text) {
                    return text.split('\n')
                        .filter(function(line) { return !line.trim().match(/^\/\//); })
                        .join('\n').replace(/\n{2,}/g, '\n').trim();
                };

                var box5s = document.querySelectorAll('div.box5');
                for (var i = 0; i < box5s.length; i++) {
                    var box = box5s[i];
                    var header = box.querySelector('span.title1');
                    var firstTd = box.querySelector('table td:first-child');
                    if (!header || !firstTd) continue;
                    var headerText = header.textContent.trim();
                    var data = cleanTestData(firstTd.innerText || firstTd.textContent || '');
                    if (headerText === '\uc785\ub825' && data && !inputText) inputText = data;
                    if (headerText === '\ucd9c\ub825' && data && !outputText) outputText = data;
                }

                if (!inputText || !outputText) {
                    var preEls = document.querySelectorAll('pre, code');
                    if (preEls.length >= 2) {
                        if (!inputText) inputText = cleanTestData(preEls[0].innerText || preEls[0].textContent || '');
                        if (!outputText) outputText = cleanTestData(preEls[1].innerText || preEls[1].textContent || '');
                    }
                }

                if (inputText && outputText) {
                    result.testCases.push({ input: inputText, output: outputText });
                }

                if (!force && result.description.length < 50 && result.title.length < 3) {
                    return;
                }

                var imgSrcPattern = /src="(https?:\/\/[^"]+)"/g;
                var imgUrls = [];
                var matched;
                while ((matched = imgSrcPattern.exec(result.description)) !== null) {
                    if (imgUrls.indexOf(matched[1]) < 0) imgUrls.push(matched[1]);
                }
                var descContainers = document.querySelectorAll('[ng-bind-html], .tab-pane.active, .problem_description');
                for (var ci = 0; ci < descContainers.length; ci++) {
                    var dimgs = descContainers[ci].querySelectorAll('img');
                    for (var di = 0; di < dimgs.length; di++) {
                        var dsrc = dimgs[di].src || dimgs[di].getAttribute('ng-src') || dimgs[di].getAttribute('data-src') || '';
                        if (dsrc && dsrc.indexOf('http') === 0 && imgUrls.indexOf(dsrc) < 0) {
                            imgUrls.push(dsrc);
                            if (result.description.indexOf(dsrc) < 0) {
                                result.description += '<br><img src="' + dsrc + '">';
                            }
                        }
                    }
                }

                var sendResult = function() {
                    var jsonStr = JSON.stringify(result);
                    $queryJs
                };

                if (imgUrls.length === 0) {
                    sendResult();
                } else {
                    var imgDone = 0;
                    for (var ii = 0; ii < imgUrls.length; ii++) {
                        (function(url) {
                            fetch(url, { credentials: 'include' })
                                .then(function(resp) { return resp.blob(); })
                                .then(function(blob) {
                                    return new Promise(function(resolve) {
                                        var reader = new FileReader();
                                        reader.onloadend = function() { resolve(reader.result); };
                                        reader.readAsDataURL(blob);
                                    });
                                })
                                .then(function(dataUrl) {
                                    result.description = result.description.split(url).join(dataUrl);
                                })
                                .catch(function(e) {})
                                .finally(function() {
                                    imgDone++;
                                    if (imgDone >= imgUrls.length) sendResult();
                                });
                        })(imgUrls[ii]);
                    }
                }
            }
        })();
    """.trimIndent()

    // ─── 결과 파싱 ───

    private fun parseResult(jsonStr: String, fallbackCpId: String): Problem? {
        try {
            val json = JsonParser.parseString(jsonStr).asJsonObject
            val title = json.get("title")?.asString ?: ""
            val description = json.get("description")?.asString ?: ""
            val timeLimit = json.get("timeLimit")?.asString ?: ""
            val memoryLimit = json.get("memoryLimit")?.asString ?: ""
            val difficulty = json.get("difficulty")?.asString ?: ""

            val testCases = mutableListOf<TestCase>()
            val testCasesArr = json.getAsJsonArray("testCases")
            if (testCasesArr != null) {
                for (tc in testCasesArr) {
                    val obj = tc.asJsonObject
                    testCases.add(
                        TestCase(
                            input = cleanTestText(obj.get("input")?.asString ?: ""),
                            expectedOutput = cleanTestText(obj.get("output")?.asString ?: "")
                        )
                    )
                }
            }

            val actualId = json.get("problemNumber")?.asString?.ifBlank { null }
            val cpId = json.get("contestProbId")?.asString?.ifBlank { null }
                ?: fallbackCpId

            if (title.isBlank() && description.length < 50) {
                LOG.info("[CodingTestKit] Parse: title blank, desc too short (${description.length})")
                return null
            }

            return Problem(
                source = ProblemSource.SWEA,
                id = actualId ?: "",
                title = title.ifBlank { "SWEA" },
                description = description,
                testCases = testCases,
                timeLimit = timeLimit,
                memoryLimit = memoryLimit,
                difficulty = difficulty.ifBlank { "Unrated" },
                contestProbId = cpId
            )
        } catch (e: Exception) {
            LOG.info("[CodingTestKit] Parse failed: ${e.message}")
            return null
        }
    }

    private fun cleanTestText(text: String): String =
        text.replace("\r", "")
            .lines()
            .filter { !it.trimStart().startsWith("//") }
            .joinToString("\n")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()
}
