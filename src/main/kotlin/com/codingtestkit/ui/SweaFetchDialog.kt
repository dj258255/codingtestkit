package com.codingtestkit.ui

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import com.codingtestkit.service.AuthService
import com.codingtestkit.service.I18n
import com.codingtestkit.service.SwexpertApi
import com.google.gson.JsonParser
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
import java.awt.FlowLayout
import javax.swing.*

/**
 * SWEA 문제 가져오기 다이얼로그.
 *
 * 흐름:
 * 1) contestProbId 해석: Jsoup HTTP POST (브라우저 불필요)
 * 2) JCEF로 목록 페이지 로드 → form submit으로 상세 페이지 이동
 * 3) AngularJS 렌더링 완료 감지 (JS polling, 500ms 간격)
 * 4) DOM에서 콘텐츠 추출 + 이미지 base64 변환
 */
class SweaFetchDialog(
    project: Project,
    private val problemId: String
) : DialogWrapper(project) {

    private var problem: Problem? = null
    private var extracted = false
    private var contestProbId: String? = null
    private val statusLabel = JLabel(I18n.t("SWEA 문제 페이지 로딩 중...", "Loading SWEA problem page...")).apply {
        foreground = Color.GRAY
    }

    init {
        // contestProbId 해석 (Jsoup HTTP, 브라우저 불필요)
        contestProbId = if (problemId.any { it.isLetter() }) {
            // 알파벳 포함 → 이미 contestProbId
            problemId
        } else {
            resolveContestProbId(problemId)
        }
        title = I18n.t("SWEA 문제 가져오기", "Fetch SWEA Problem")
        setSize(500, 150)
        init()
    }

    /**
     * 문제번호 → contestProbId 변환 (Jsoup HTTP POST, 최대 5초)
     * 브라우저 없이 직접 HTTP 요청으로 해석
     */
    private fun resolveContestProbId(number: String): String? {
        return try {
            val cookies = AuthService.getInstance().getCookies(ProblemSource.SWEA)
            if (cookies.isBlank()) return null
            val future = java.util.concurrent.CompletableFuture.supplyAsync {
                try {
                    val result = SwexpertApi.searchProblems(keyword = number, cookies = cookies, pageSize = 10)
                    result.problems.firstOrNull { it.number == number }?.contestProbId
                } catch (_: Exception) { null }
            }
            future.get(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {
            null
        }
    }

    override fun createActions(): Array<Action> {
        myCancelAction.putValue(Action.NAME, I18n.t("취소", "Cancel"))
        return arrayOf(cancelAction)
    }

    override fun createCenterPanel(): JComponent {
        if (!JBCefApp.isSupported()) {
            return JLabel("<html>${I18n.t("JCEF를 사용할 수 없어 SWEA 문제를 가져올 수 없습니다.", "Cannot fetch SWEA problems: JCEF is not available.")}</html>")
        }
        if (contestProbId == null) {
            return JLabel("<html>${I18n.t(
                "문제 번호를 찾을 수 없습니다. SWEA에 로그인했는지 확인하세요.",
                "Could not resolve problem number. Please check SWEA login."
            )}</html>")
        }
        return createBrowserPanel()
    }

    private fun createBrowserPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(480, 100)

        // JCEF 브라우저: 목록 페이지로 시작 (세션 수립용)
        val listUrl = "https://swexpertacademy.com/main/code/problem/problemList.do"
        val browser = JBCefBrowser(listUrl)

        val bWrapper = browser.component
        bWrapper.preferredSize = Dimension(1024, 768)
        bWrapper.minimumSize = Dimension(0, 0)

        // 로딩 표시 패널
        val loadingPanel = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
        }
        loadingPanel.add(JLabel("<html><b>${I18n.t("SWEA 문제 #$problemId", "SWEA Problem #$problemId")}</b> ${I18n.t("를 가져오고 있습니다...", "is being fetched...")}</html>"), BorderLayout.NORTH)
        loadingPanel.add(statusLabel, BorderLayout.CENTER)

        // 브라우저 보기 토글 (디버그용)
        val bBtn = JButton(I18n.t("브라우저 보기", "Show Browser"))
        bBtn.addActionListener {
            if (bWrapper.isVisible) {
                bWrapper.isVisible = false
                panel.preferredSize = Dimension(480, 100)
                bBtn.text = I18n.t("브라우저 보기", "Show Browser")
                setSize(500, 150)
            } else {
                bWrapper.isVisible = true
                panel.preferredSize = Dimension(880, 650)
                bBtn.text = I18n.t("브라우저 숨기기", "Hide Browser")
                setSize(900, 700)
            }
            panel.revalidate()
        }
        val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        btnPanel.add(bBtn)
        loadingPanel.add(btnPanel, BorderLayout.SOUTH)

        panel.add(loadingPanel, BorderLayout.NORTH)
        bWrapper.isVisible = false
        panel.add(bWrapper, BorderLayout.CENTER)

        val jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
        jsQuery.addHandler { result ->
            handleJsCallback(result)
            JBCefJSQuery.Response("")
        }

        var listPageLoaded = false
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true || extracted) return
                val url = cefBrowser?.url ?: ""

                if (!listPageLoaded && url.contains("problemList")) {
                    // 목록 페이지 로드 완료 → form submit으로 상세 페이지 이동
                    listPageLoaded = true
                    SwingUtilities.invokeLater {
                        statusLabel.text = I18n.t("상세 페이지로 이동 중...", "Navigating to detail page...")
                        statusLabel.foreground = Color(0, 120, 0)
                    }
                    navigateToDetail(cefBrowser)
                } else if (url.contains("login") || url.contains("signUp")) {
                    // 로그인 페이지로 리다이렉트됨
                    SwingUtilities.invokeLater {
                        statusLabel.text = I18n.t("SWEA 로그인이 필요합니다.", "SWEA login required.")
                        statusLabel.foreground = Color.RED
                    }
                } else if (url.contains("problemDetail") || listPageLoaded) {
                    // 상세 페이지 도착 → AngularJS 렌더링 대기 후 콘텐츠 추출
                    val cpMatch = Regex("contestProbId=([A-Za-z0-9_-]+)").find(url)
                    if (cpMatch != null) contestProbId = cpMatch.groupValues[1]

                    SwingUtilities.invokeLater {
                        statusLabel.text = I18n.t("콘텐츠 추출 중...", "Extracting content...")
                        statusLabel.foreground = Color(0, 120, 0)
                    }
                    waitForAngularAndExtract(cefBrowser, jsQuery)
                }
            }
        }, browser.cefBrowser)

        return panel
    }

    /**
     * VSCode 방식: form submit으로 상세 페이지 이동 (POST)
     */
    private fun navigateToDetail(cefBrowser: CefBrowser?) {
        val cpId = contestProbId ?: return
        val js = """
            (function() {
                var form = document.createElement('form');
                form.method = 'POST';
                form.action = 'https://swexpertacademy.com/main/code/problem/problemDetail.do';
                var fields = {
                    contestProbId: '$cpId',
                    categoryId: '$cpId',
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

    /**
     * AngularJS 렌더링 완료를 JS polling으로 감지한 후 콘텐츠 추출.
     * 고정 타이머(5s/9s/14s) 대신 500ms 간격 polling → 렌더링 즉시 감지.
     */
    private fun waitForAngularAndExtract(cefBrowser: CefBrowser?, jsQuery: JBCefJSQuery) {
        if (cefBrowser == null || extracted) return

        val queryJs = jsQuery.inject("jsonStr")

        val js = """
            (function() {
                var attempts = 0;
                var maxAttempts = 30; // 15초 (500ms * 30)
                var poller = setInterval(function() {
                    attempts++;
                    // AngularJS 렌더링 완료 감지: ng-bind-html에 콘텐츠가 있는지 확인
                    var ready = false;
                    var ngBindEls = document.querySelectorAll('[ng-bind-html]');
                    for (var i = 0; i < ngBindEls.length; i++) {
                        if (ngBindEls[i].innerHTML && ngBindEls[i].innerHTML.trim().length > 30) {
                            ready = true;
                            break;
                        }
                    }
                    // fallback: body에 "시간" 또는 "메모리" 키워드가 있으면 렌더링 완료로 판단
                    if (!ready) {
                        var bodyText = document.body ? (document.body.innerText || '') : '';
                        ready = (bodyText.indexOf('시간') >= 0 || bodyText.indexOf('메모리') >= 0) && bodyText.length > 3000;
                    }
                    if (ready || attempts >= maxAttempts) {
                        clearInterval(poller);
                        // 콘텐츠 추출 시작
                        extractAndSend(attempts >= maxAttempts);
                    }
                }, 500);

                function extractAndSend(force) {
                    var result = {};

                    // Title
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

                    // Description 추출
                    result.description = '';

                    var isCommentContent = function(html) {
                        var text = html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
                        if (text.match(/\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}/) && text.indexOf('댓글') >= 0) return true;
                        if (text.match(/\d+\s*\/\s*\d+자/)) return true;
                        if (text.indexOf('등록') >= 0 && text.match(/@\S+/)) return true;
                        return false;
                    };

                    // 방법 1: ng-bind-html (VSCode와 동일 — AngularJS 렌더링된 콘텐츠 우선)
                    var bindHtmlEls = document.querySelectorAll('[ng-bind-html]');
                    var contentParts = [];
                    for (var i = 0; i < bindHtmlEls.length; i++) {
                        var el = bindHtmlEls[i];
                        var html = el.innerHTML;
                        if (!html || html.trim().length < 15) continue;
                        var text = el.textContent ? el.textContent.trim() : '';
                        if (/※.*무단\s*복제/.test(text)) continue;
                        if (isCommentContent(html)) continue;
                        // 중복 방지
                        var isDup = false;
                        for (var ci = 0; ci < contentParts.length; ci++) {
                            if (contentParts[ci] === html || contentParts[ci].indexOf(html) >= 0) { isDup = true; break; }
                        }
                        if (!isDup) contentParts.push(html);
                    }
                    if (contentParts.length > 0) {
                        result.description = contentParts.join('<hr>');
                        console.log('SWEA desc: ng-bind-html, parts=' + contentParts.length);
                    }

                    // 방법 2: div.box4
                    if (!result.description || result.description.length < 100) {
                        var box4 = document.querySelector('div.box4');
                        if (box4 && box4.innerHTML.length > 100) {
                            result.description = box4.innerHTML;
                        }
                    }

                    // 방법 3: p.txt
                    if (!result.description || result.description.length < 100) {
                        var ptxt = document.querySelector('p.txt');
                        if (ptxt && ptxt.innerHTML.length > 100) {
                            result.description = ptxt.innerHTML;
                        }
                    }

                    // 방법 4: 키워드 검색
                    if (!result.description || result.description.length < 100) {
                        var allEls = document.querySelectorAll('div, section, p');
                        var bestEl = null;
                        var bestLen = Infinity;
                        for (var i = 0; i < allEls.length; i++) {
                            var el = allEls[i];
                            var text = el.textContent || '';
                            var html = el.innerHTML;
                            if ((text.indexOf('[입력]') >= 0 || text.indexOf('[출력]') >= 0) &&
                                html.length > 200 && html.length < 50000) {
                                if (!text.match(/참여자.*제출.*정답/) && !isCommentContent(html)) {
                                    if (html.length < bestLen) { bestEl = el; bestLen = html.length; }
                                }
                            }
                        }
                        if (bestEl) result.description = bestEl.innerHTML;
                    }

                    // 방법 5: AngularJS scope
                    if ((!result.description || result.description.length < 100) && typeof angular !== 'undefined') {
                        try {
                            var ctrls = document.querySelectorAll('[ng-controller]');
                            for (var ci = 0; ci < ctrls.length; ci++) {
                                try {
                                    var sc = angular.element(ctrls[ci]).scope();
                                    for (var s = sc; s; s = s['${'$'}parent']) {
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

                    // 방법 6: CSS 셀렉터
                    if (!result.description || result.description.length < 100) {
                        var descSelectors = ['#problemContent', '.problem_description', '.problemContent',
                            '.problem_txt', '.desc_box', '.problem_content', '.view_content'];
                        for (var i = 0; i < descSelectors.length; i++) {
                            var el = document.querySelector(descSelectors[i]);
                            if (el && el.innerHTML.length > 100) { result.description = el.innerHTML; break; }
                        }
                    }

                    // 이미지 처리: base64 data URI 인라인 변환
                    if (result.description) {
                        result.description = result.description.replace(/&amp;/g, '&');
                        result.description = result.description.replace(/ng-src="/g, 'src="');
                        result.description = result.description.replace(/data-src="/g, 'src="');
                        result.description = result.description.replace(/src="\/([^"]*?)"/g, 'src="https://swexpertacademy.com/$1"');
                        result.description = result.description.replace(/src="(?!https?:|data:|file:)([^"]*?)"/g, 'src="https://swexpertacademy.com/$1"');
                        result.description = result.description.replace(/<a[^>]*>[^<]*다운로드[^<]*<\/a>/gi, '');
                        result.description = result.description.replace(/<a[^>]*>[^<]*download[^<]*<\/a>/gi, '');
                        result.description = result.description.replace(/<button[^>]*>[^<]*다운로드[^<]*<\/button>/gi, '');
                        result.description = result.description.replace(/\S*input\S*\.txt/gi, '');
                        result.description = result.description.replace(/\S*output\S*\.txt/gi, '');
                    }

                    // Difficulty
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

                    // Time/Memory limits
                    result.timeLimit = '';
                    result.memoryLimit = '';
                    var allLi = document.querySelectorAll('li');
                    for (var i = 0; i < allLi.length; i++) {
                        var text = allLi[i].textContent.replace(/\s+/g, ' ').trim();
                        if (text.length > 80) continue;
                        if (text.match(/시간|Time/i) && text.match(/\d+\s*초|\d+\s*sec/i)) {
                            result.timeLimit = text.replace(/^[·•\s]*(시간|Time\s*Limit)\s*:?\s*/i, '').trim();
                        }
                        if (text.match(/메모리|Memory/i) && text.match(/\d+\s*MB|\d+\s*KB/i)) {
                            result.memoryLimit = text.replace(/^[·•\s]*(메모리|Memory\s*Limit)\s*:?\s*/i, '').trim();
                        }
                    }

                    // contestProbId
                    var cpId = '';
                    var urlMatch = window.location.href.match(/contestProbId=([A-Za-z0-9_-]+)/);
                    if (urlMatch) cpId = urlMatch[1];
                    result.contestProbId = cpId;

                    // 화면 미리보기 테스트 케이스 추출
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
                        if (headerText === '입력' && data && !inputText) inputText = data;
                        if (headerText === '출력' && data && !outputText) outputText = data;
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

                    // 콘텐츠 검증
                    if (!force && result.description.length < 50 && result.title.length < 3) {
                        return; // polling이 다시 시도
                    }

                    // 이미지 base64 변환 후 콜백
                    var imgSrcPattern = /src="(https?:\/\/[^"]+)"/g;
                    var imgUrls = [];
                    var matched;
                    while ((matched = imgSrcPattern.exec(result.description)) !== null) {
                        if (imgUrls.indexOf(matched[1]) < 0) imgUrls.push(matched[1]);
                    }
                    // DOM img 요소에서도 수집
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
                        console.log('SWEA: Converting ' + imgUrls.length + ' images to base64');
                        var done = 0;
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
                                    .catch(function(e) {
                                        console.log('SWEA: Image conversion failed:', url, e);
                                    })
                                    .finally(function() {
                                        done++;
                                        if (done >= imgUrls.length) sendResult();
                                    });
                            })(imgUrls[ii]);
                        }
                    }
                }
            })();
        """.trimIndent()

        cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
    }

    private fun handleJsCallback(result: String) {
        if (!extracted) {
            extracted = true
            SwingUtilities.invokeLater {
                parseResult(result)
                if (problem != null && problem!!.description.length > 30) {
                    doOKAction()
                } else {
                    extracted = false
                    statusLabel.text = I18n.t("콘텐츠가 아직 로드되지 않았습니다. 잠시만 기다려주세요...", "Content not loaded yet. Please wait...")
                    statusLabel.foreground = Color.ORANGE
                }
            }
        }
    }

    private fun parseResult(jsonStr: String) {
        try {
            val json = JsonParser.parseString(jsonStr).asJsonObject
            val title = json.get("title")?.asString ?: "SWEA #$problemId"
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

            val actualId = json.get("problemNumber")?.asString?.ifBlank { null } ?: problemId
            val cpId = json.get("contestProbId")?.asString?.ifBlank { null }
                ?: contestProbId
                ?: ""
            if (cpId.isNotBlank()) contestProbId = cpId
            problem = Problem(
                source = ProblemSource.SWEA,
                id = actualId,
                title = title.ifBlank { "SWEA #$actualId" },
                description = description,
                testCases = testCases,
                timeLimit = timeLimit,
                memoryLimit = memoryLimit,
                difficulty = difficulty.ifBlank { "Unrated" },
                contestProbId = cpId
            )
        } catch (e: Exception) {
            problem = null
        }
    }

    /** 테스트 데이터 정리: \r 제거, // 주석 줄 제거, 연속 빈 줄 합치기 */
    private fun cleanTestText(text: String): String =
        text.replace("\r", "")
            .lines()
            .filter { !it.trimStart().startsWith("//") }
            .joinToString("\n")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()

    fun getProblem(): Problem? = problem
}
