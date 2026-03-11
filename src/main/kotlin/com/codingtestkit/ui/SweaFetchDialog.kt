package com.codingtestkit.ui

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import com.codingtestkit.service.I18n
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

class SweaFetchDialog(
    project: Project,
    private val problemId: String
) : DialogWrapper(project) {

    private enum class FetchState {
        LOADING_LIST,       // 문제 목록 페이지 로딩 중
        SEARCHING,          // 문제 검색 중
        LOADING_DETAIL,     // 문제 상세 페이지 로딩 중
        EXTRACTING          // 콘텐츠 추출 중
    }

    private var problem: Problem? = null
    var inputFileContent: String = ""
        private set
    var outputFileContent: String = ""
        private set
    /** 이미지 URL 리스트 (Java에서 다운로드) */
    var imageUrls: List<String> = emptyList()
        private set
    private var extracted = false
    private var fetchState: FetchState
    private var contestProbId: String? = null
    private val statusLabel = JLabel(I18n.t("SWEA 문제 페이지 로딩 중...", "Loading SWEA problem page...")).apply {
        foreground = Color.GRAY
    }
    private var browserPanel: JPanel? = null
    private var browserWrapper: JComponent? = null
    private var showBrowserBtn: JButton? = null

    init {
        // 입력이 알파벳을 포함하면 contestProbId로 직접 접근
        fetchState = if (problemId.any { it.isLetter() }) {
            contestProbId = problemId
            FetchState.LOADING_DETAIL
        } else {
            FetchState.LOADING_LIST
        }
        title = I18n.t("SWEA 문제 가져오기", "Fetch SWEA Problem")
        setSize(500, 150)
        init()
    }

    override fun createActions(): Array<Action> {
        myCancelAction.putValue(Action.NAME, I18n.t("취소", "Cancel"))
        return arrayOf(cancelAction)
    }

    override fun createCenterPanel(): JComponent {
        if (!JBCefApp.isSupported()) {
            return JLabel("<html>${I18n.t("JCEF를 사용할 수 없어 SWEA 문제를 가져올 수 없습니다.", "Cannot fetch SWEA problems: JCEF is not available.")}</html>")
        }
        return createBrowserPanel()
    }

    private fun getInitialUrl(): String {
        return if (fetchState == FetchState.LOADING_DETAIL) {
            "https://swexpertacademy.com/main/code/problem/problemDetail.do?contestProbId=$contestProbId"
        } else {
            // URL 파라미터로 검색 시도
            "https://swexpertacademy.com/main/code/problem/problemList.do?contestProbId=&categoryId=&categoryType=CODE&problemTitle=$problemId&orderBy=FIRST_REG_DATETIME&select=-1&pageSize=10&pageIndex=1"
        }
    }

    private fun createBrowserPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(480, 100)
        browserPanel = panel

        val browser = JBCefBrowser(getInitialUrl())

        // 브라우저는 보이지 않지만 렌더링을 위해 컴포넌트 계층에 포함
        val bWrapper = browser.component
        bWrapper.preferredSize = Dimension(1024, 768)
        bWrapper.minimumSize = Dimension(0, 0)
        browserWrapper = bWrapper

        // 로딩 표시만 보이는 패널
        val loadingPanel = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
        }
        loadingPanel.add(JLabel("<html><b>${I18n.t("SWEA 문제 #$problemId", "SWEA Problem #$problemId")}</b> ${I18n.t("를 가져오고 있습니다...", "is being fetched...")}</html>"), BorderLayout.NORTH)
        loadingPanel.add(statusLabel, BorderLayout.CENTER)

        // 브라우저 보기 토글 버튼
        val bBtn = JButton(I18n.t("브라우저 보기", "Show Browser"))
        showBrowserBtn = bBtn
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

        // 브라우저는 기본적으로 숨김
        bWrapper.isVisible = false
        panel.add(bWrapper, BorderLayout.CENTER)

        val jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
        jsQuery.addHandler { result ->
            handleJsCallback(result, browser, jsQuery)
            JBCefJSQuery.Response("")
        }

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true || extracted) return
                handlePageLoaded(cefBrowser, jsQuery)
            }
        }, browser.cefBrowser)

        return panel
    }

    /**
     * 자동 이동 실패 시: 브라우저를 표시하고 수동 클릭 안내
     */
    private fun showManualFallback() {
        statusLabel.text = I18n.t("자동 이동에 실패했습니다. 브라우저에서 문제를 직접 클릭해주세요.", "Auto-navigation failed. Please click the problem in the browser.")
        statusLabel.foreground = Color(200, 120, 0)
        // 브라우저 자동 표시
        val bw = browserWrapper ?: return
        val bp = browserPanel ?: return
        if (!bw.isVisible) {
            bw.isVisible = true
            bp.preferredSize = Dimension(880, 650)
            showBrowserBtn?.text = I18n.t("브라우저 숨기기", "Hide Browser")
            setSize(900, 700)
            bp.revalidate()
        }
    }

    private fun handlePageLoaded(cefBrowser: CefBrowser?, jsQuery: JBCefJSQuery) {
        val url = cefBrowser?.url ?: ""

        // URL 기반으로 현재 페이지 판단
        when {
            // 상세 페이지에 도착
            url.contains("problemDetail") && url.contains("contestProbId") -> {
                // URL에서 contestProbId 추출하여 필드에 저장
                val cpMatch = Regex("contestProbId=([A-Za-z0-9_]+)").find(url)
                if (cpMatch != null && contestProbId.isNullOrBlank()) {
                    contestProbId = cpMatch.groupValues[1]
                }
                fetchState = FetchState.EXTRACTING
                SwingUtilities.invokeLater {
                    statusLabel.text = I18n.t("문제 상세 페이지 로딩 완료, 콘텐츠 추출 중...", "Detail page loaded, extracting content...")
                    statusLabel.foreground = Color(0, 120, 0)
                }
                Timer(5000) {
                    if (!extracted) extractContent(cefBrowser, jsQuery, false)
                }.apply { isRepeats = false; start() }

                Timer(9000) {
                    if (!extracted) extractContent(cefBrowser, jsQuery, false)
                }.apply { isRepeats = false; start() }

                Timer(14000) {
                    if (!extracted) extractContent(cefBrowser, jsQuery, true)
                }.apply { isRepeats = false; start() }
            }
            // 목록 페이지 (검색 필요)
            url.contains("problemList") || fetchState == FetchState.LOADING_LIST -> {
                fetchState = FetchState.SEARCHING
                SwingUtilities.invokeLater {
                    statusLabel.text = I18n.t("문제 목록에서 #$problemId 검색 중...", "Searching for #$problemId in problem list...")
                }
                // 검색 결과 렌더링 대기 후 여러 번 시도 (AngularJS 비동기 렌더링 고려)
                Timer(2000) {
                    if (!extracted && fetchState == FetchState.SEARCHING) searchForProblem(cefBrowser, jsQuery)
                }.apply { isRepeats = false; start() }

                Timer(4000) {
                    if (!extracted && fetchState == FetchState.SEARCHING) searchForProblem(cefBrowser, jsQuery)
                }.apply { isRepeats = false; start() }

                Timer(7000) {
                    if (!extracted && fetchState == FetchState.SEARCHING) searchForProblem(cefBrowser, jsQuery)
                }.apply { isRepeats = false; start() }

                Timer(11000) {
                    if (!extracted && fetchState == FetchState.SEARCHING) searchForProblem(cefBrowser, jsQuery)
                }.apply { isRepeats = false; start() }

                // 최종 폴백: 자동 이동 실패 시 브라우저를 보여주고 안내 메시지
                Timer(15000) {
                    if (!extracted && fetchState == FetchState.SEARCHING) {
                        SwingUtilities.invokeLater { showManualFallback() }
                    }
                }.apply { isRepeats = false; start() }
            }
            // 직접 contestProbId로 접근한 경우 (알파벳 ID)
            fetchState == FetchState.LOADING_DETAIL -> {
                fetchState = FetchState.EXTRACTING
                SwingUtilities.invokeLater {
                    statusLabel.text = I18n.t("문제 상세 페이지 로딩 완료, 콘텐츠 추출 중...", "Detail page loaded, extracting content...")
                    statusLabel.foreground = Color(0, 120, 0)
                }
                Timer(5000) {
                    if (!extracted) extractContent(cefBrowser, jsQuery, false)
                }.apply { isRepeats = false; start() }

                Timer(9000) {
                    if (!extracted) extractContent(cefBrowser, jsQuery, false)
                }.apply { isRepeats = false; start() }

                Timer(14000) {
                    if (!extracted) extractContent(cefBrowser, jsQuery, true)
                }.apply { isRepeats = false; start() }
            }
        }
    }

    private fun handleJsCallback(result: String, browser: JBCefBrowser, jsQuery: JBCefJSQuery) {
        when {
            // 검색 결과: contestProbId를 찾았음
            result.startsWith("FOUND:") -> {
                val foundId = result.removePrefix("FOUND:")
                if (foundId.isNotBlank()) {
                    contestProbId = foundId
                    fetchState = FetchState.LOADING_DETAIL
                    SwingUtilities.invokeLater {
                        statusLabel.text = I18n.t("문제를 찾았습니다! 상세 페이지로 이동 중...", "Problem found! Navigating to detail page...")
                        statusLabel.foreground = Color(0, 120, 0)
                    }
                    val detailUrl = "https://swexpertacademy.com/main/code/problem/problemDetail.do?contestProbId=$foundId"
                    browser.cefBrowser.executeJavaScript("window.location.href='$detailUrl';", "", 0)
                } else {
                    SwingUtilities.invokeLater {
                        statusLabel.text = I18n.t("문제 #$problemId 를 찾을 수 없습니다. 직접 문제를 찾아주세요.", "Problem #$problemId not found. Please find it manually.")
                        statusLabel.foreground = Color.RED
                    }
                }
            }
            // 검색 결과: 못 찾음
            result == "NOT_FOUND" -> {
                SwingUtilities.invokeLater {
                    statusLabel.text = I18n.t("문제 #$problemId 를 찾을 수 없습니다. 로그인 후 직접 문제를 찾아주세요.", "Problem #$problemId not found. Please log in and find it manually.")
                    statusLabel.foreground = Color.RED
                }
            }
            // 콘텐츠 추출 결과
            else -> {
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
        }
    }

    /**
     * 문제 목록 페이지에서 contestProbId를 추출하거나 클릭하여 상세 페이지로 이동
     */
    private fun searchForProblem(cefBrowser: CefBrowser?, jsQuery: JBCefJSQuery) {
        if (cefBrowser == null || extracted) return

        val callbackJs = jsQuery.inject("result")

        val js = """
            (function() {
                var problemNum = '$problemId';
                console.log('SWEA search: looking for problem', problemNum);

                // 문제번호가 포함된 행(row)를 찾는 헬퍼
                function findProblemRow() {
                    // body 텍스트에 문제번호가 없으면 아직 렌더링 안 된 것
                    if (document.body.textContent.indexOf(problemNum + '.') < 0 &&
                        document.body.textContent.indexOf(problemNum + ' ') < 0) {
                        console.log('SWEA search: problem number not in DOM yet');
                        return null;
                    }

                    // 모든 클릭 가능 요소(a, [ng-click])에서 문제번호 근처 것 찾기
                    var clickables = document.querySelectorAll('a, [ng-click]');
                    for (var i = 0; i < clickables.length; i++) {
                        var el = clickables[i];
                        // 가장 가까운 행 컨테이너 찾기 (다양한 HTML 구조 대응)
                        var row = el.closest('tr, li, [ng-repeat], .list_item, .problem_item') ||
                                  el.parentElement && el.parentElement.closest('tr, li, [ng-repeat], div') ||
                                  el.parentElement;
                        if (!row) continue;
                        var rowText = row.textContent || '';
                        if (rowText.indexOf(problemNum + '.') >= 0 || rowText.indexOf(problemNum + ' ') >= 0) {
                            // ng-click이 있는 요소만 반환 (클릭 가능한 것)
                            if (el.hasAttribute('ng-click') || el.tagName === 'A') {
                                return el;
                            }
                        }
                    }
                    return null;
                }

                // 전략 1: AngularJS scope에서 contestProbId 직접 추출 (가장 빠름)
                var targetEl = findProblemRow();
                if (targetEl && typeof angular !== 'undefined') {
                    try {
                        var sc = angular.element(targetEl).scope();
                        if (sc) {
                            // scope에서 contestProbId를 가진 객체 찾기
                            var cpId = null;
                            // ng-repeat item 변수들을 직접 탐색
                            var commonNames = ['item', 'problem', 'prob', 'p', 'row', 'data'];
                            for (var ci = 0; ci < commonNames.length; ci++) {
                                var obj = sc[commonNames[ci]];
                                if (obj && typeof obj === 'object' && obj.contestProbId) {
                                    cpId = String(obj.contestProbId);
                                    break;
                                }
                            }
                            // scope 1단계 탐색 (object이면서 contestProbId 속성이 있는 것)
                            if (!cpId) {
                                for (var key in sc) {
                                    if (key.charAt(0) === '$') continue;
                                    try {
                                        var v = sc[key];
                                        if (v && typeof v === 'object' && !Array.isArray(v) && v.contestProbId) {
                                            cpId = String(v.contestProbId);
                                            break;
                                        }
                                    } catch(e) {}
                                }
                            }
                            // ng-click 표현식에서 인자 resolve
                            if (!cpId) {
                                var ngExpr = targetEl.getAttribute('ng-click') || '';
                                var exprMatch = ngExpr.match(/\(([^)]+)\)/);
                                if (exprMatch) {
                                    var args = exprMatch[1].split(',');
                                    for (var ai = 0; ai < args.length; ai++) {
                                        var parts = args[ai].trim().split('.');
                                        var val = sc;
                                        for (var pi = 0; pi < parts.length; pi++) {
                                            if (val != null) val = val[parts[pi]];
                                        }
                                        if (val && typeof val === 'string' && val.length >= 8) {
                                            cpId = val;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (cpId) {
                                console.log('SWEA search: found contestProbId from scope:', cpId);
                                var result = 'FOUND:' + cpId;
                                $callbackJs
                                return;
                            }
                        }
                    } catch(e) { console.log('SWEA search scope err:', e); }
                }

                // 전략 2: HTML에서 contestProbId 패턴 추출
                var bodyHtml = document.body.innerHTML;
                var numIdx = bodyHtml.indexOf('>' + problemNum + '.');
                if (numIdx < 0) numIdx = bodyHtml.indexOf(problemNum + '.');
                if (numIdx >= 0) {
                    var slice = bodyHtml.substring(Math.max(0, numIdx - 3000), Math.min(bodyHtml.length, numIdx + 3000));
                    var m = slice.match(/contestProbId[=:'"\\s]+([A-Za-z0-9_]{10,30})/);
                    if (m) {
                        console.log('SWEA search: found contestProbId from HTML:', m[1]);
                        var result = 'FOUND:' + m[1];
                        $callbackJs
                        return;
                    }
                }

                // 전략 3: 제목 링크를 직접 클릭하고 URL 변화 감시
                if (targetEl) {
                    console.log('SWEA search: clicking element', targetEl.tagName, (targetEl.textContent||'').substring(0,40));
                    var startUrl = window.location.href;

                    // 네이티브 click() 호출 (AngularJS ng-click 핸들러도 작동)
                    targetEl.click();

                    // URL 변화 모니터링 (SPA 네비게이션 감지)
                    var checkCnt = 0;
                    var monitor = setInterval(function() {
                        checkCnt++;
                        var curUrl = window.location.href;
                        if (curUrl !== startUrl) {
                            clearInterval(monitor);
                            var m = curUrl.match(/contestProbId=([A-Za-z0-9_]+)/);
                            if (m) {
                                console.log('SWEA search: navigated, id=', m[1]);
                                var result = 'FOUND:' + m[1];
                                $callbackJs
                            }
                            return;
                        }
                        if (checkCnt > 20) {
                            clearInterval(monitor);
                            // 최종 폴백: HTML에서 contestProbId 찾아서 직접 이동
                            var html = document.body.innerHTML;
                            var p = html.match(/contestProbId[=:'"\\s]+([A-Za-z0-9_]{10,30})/);
                            if (p) {
                                console.log('SWEA search fallback:', p[1]);
                                window.location.href = 'https://swexpertacademy.com/main/code/problem/problemDetail.do?contestProbId=' + p[1];
                            }
                        }
                    }, 500);
                } else {
                    console.log('SWEA search: no clickable element found yet');
                }
            })();
        """.trimIndent()

        cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
    }

    private fun extractContent(cefBrowser: CefBrowser?, jsQuery: JBCefJSQuery, force: Boolean) {
        if (cefBrowser == null || extracted) return

        val queryJs = jsQuery.inject("jsonStr")
        val forceFlag = if (force) "true" else "false"

        val js = """
            (function() {
                var result = {};
                var force = $forceFlag;

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
                // 문제번호 fallback: document.title 또는 h2/h3에서 추출
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

                // Description 추출 - SWEA Problem 탭 콘텐츠
                result.description = '';

                // 방법 1: ng-bind-html 속성이 있는 요소 (SWEA가 문제 설명을 바인딩하는 방식)
                var bindHtmlEls = document.querySelectorAll('[ng-bind-html]');
                for (var i = 0; i < bindHtmlEls.length; i++) {
                    var el = bindHtmlEls[i];
                    var html = el.innerHTML;
                    // 문제 본문은 보통 100자 이상이고 "무단 복제" 또는 문제 관련 텍스트 포함
                    if (html.length > 100) {
                        result.description = html;
                        break;
                    }
                }

                // 방법 2: 활성 탭 패널 (Problem 탭) 내의 콘텐츠
                if (!result.description || result.description.length < 100) {
                    var tabSelectors = [
                        '.tab-pane.active',
                        '.tab-content > .active',
                        '[role="tabpanel"]:not([hidden])',
                        '.panel.active',
                        '.tab_content.active'
                    ];
                    for (var i = 0; i < tabSelectors.length; i++) {
                        var el = document.querySelector(tabSelectors[i]);
                        if (el && el.innerHTML.length > 200) {
                            result.description = el.innerHTML;
                            break;
                        }
                    }
                }

                // 방법 3: "무단 복제" 또는 "[입력]"/"[출력]" 텍스트가 포함된 요소 찾기
                if (!result.description || result.description.length < 100) {
                    var allEls = document.querySelectorAll('div, section');
                    for (var i = 0; i < allEls.length; i++) {
                        var el = allEls[i];
                        var text = el.textContent || '';
                        var html = el.innerHTML;
                        // 문제 본문 특징: "무단 복제" 경고문 또는 "[입력]"/"[출력]" 섹션 포함
                        if ((text.indexOf('무단 복제') >= 0 || text.indexOf('[입력]') >= 0 || text.indexOf('[출력]') >= 0) &&
                            html.length > 200 && html.length < 50000) {
                            // 이 요소가 통계 영역이 아닌지 확인
                            if (!text.match(/참여자.*제출.*정답/)) {
                                result.description = html;
                                break;
                            }
                        }
                    }
                }

                // 방법 4: AngularJS scope에서 문제 설명 추출
                if ((!result.description || result.description.length < 100) && typeof angular !== 'undefined') {
                    try {
                        var ctrl = document.querySelector('[ng-controller]');
                        if (ctrl) {
                            var scope = angular.element(ctrl).scope();
                            if (scope) {
                                var findDesc = function(obj, depth) {
                                    if (depth > 3 || !obj) return null;
                                    if (typeof obj === 'object' && !Array.isArray(obj)) {
                                        var descKeys = ['problemContent', 'content', 'problemDesc', 'description', 'html', 'problemHtml', 'htmlContent'];
                                        for (var k = 0; k < descKeys.length; k++) {
                                            var val = obj[descKeys[k]];
                                            if (typeof val === 'string' && val.length > 100 && (val.indexOf('<') >= 0 || val.indexOf('\n') >= 0)) {
                                                return val;
                                            }
                                        }
                                        for (var key in obj) {
                                            if (key.charAt(0) === '$' || key === 'this' || key === 'constructor') continue;
                                            try {
                                                var found = findDesc(obj[key], depth + 1);
                                                if (found) return found;
                                            } catch(e) {}
                                        }
                                    }
                                    return null;
                                };
                                var desc = findDesc(scope, 0);
                                if (desc) result.description = desc;
                            }
                        }
                    } catch(e) {}
                }

                // 방법 5: 알려진 SWEA 셀렉터
                if (!result.description || result.description.length < 100) {
                    var descSelectors = [
                        '#problemContent', '.problem_description', '.problemContent',
                        '.problem_txt', '.desc_box', '.problem_content',
                        '.view_content', '.view_area .cont'
                    ];
                    for (var i = 0; i < descSelectors.length; i++) {
                        var el = document.querySelector(descSelectors[i]);
                        if (el && el.innerHTML.length > 100) {
                            result.description = el.innerHTML;
                            break;
                        }
                    }
                }

                // 이미지 URL 수집 (다운로드는 Java 쪽에서 수행)
                result.imageUrls = [];
                if (result.description) {
                    // &amp; → & 변환 (HTML entity)
                    result.description = result.description.replace(/&amp;/g, '&');
                    // ng-src, data-src → src 변환
                    result.description = result.description.replace(/ng-src="/g, 'src="');
                    result.description = result.description.replace(/data-src="/g, 'src="');
                    // 상대경로 → 절대경로
                    result.description = result.description.replace(/src="\/([^"]*?)"/g, 'src="https://swexpertacademy.com/$1"');
                    result.description = result.description.replace(/src="(?!http|img_)([^"]*?)"/g, 'src="https://swexpertacademy.com/$1"');

                    // 1) src 속성에서 URL 추출 (regex)
                    var imgRegex = /src="(https?:\/\/[^"]+)"/g;
                    var imgMatch;
                    while ((imgMatch = imgRegex.exec(result.description)) !== null) {
                        result.imageUrls.push(imgMatch[1]);
                    }

                    // 2) DOM에서 직접 img 요소 찾기 (regex로 못 잡는 경우 대비)
                    var descContainers = document.querySelectorAll('[ng-bind-html], .tab-pane.active, .problem_description');
                    for (var ci = 0; ci < descContainers.length; ci++) {
                        var imgs = descContainers[ci].querySelectorAll('img');
                        for (var ii = 0; ii < imgs.length; ii++) {
                            var imgSrc = imgs[ii].src || imgs[ii].getAttribute('ng-src') || imgs[ii].getAttribute('data-src') || '';
                            // src 속성은 브라우저가 절대경로로 resolve해줌
                            if (imgSrc && imgSrc.indexOf('http') === 0) {
                                // 중복 체크
                                var alreadyFound = false;
                                for (var ai = 0; ai < result.imageUrls.length; ai++) {
                                    if (result.imageUrls[ai] === imgSrc) { alreadyFound = true; break; }
                                }
                                if (!alreadyFound) {
                                    result.imageUrls.push(imgSrc);
                                    // description에 이 URL이 없으면 img 태그 추가
                                    if (result.description.indexOf(imgSrc) < 0) {
                                        result.description += '<br><img src="' + imgSrc + '">';
                                    }
                                }
                            }
                        }
                    }

                    console.log('SWEA Image URLs found:', result.imageUrls.length, JSON.stringify(result.imageUrls));

                    // description에서 이미지 URL을 로컬 파일명으로 교체
                    for (var idx = 0; idx < result.imageUrls.length; idx++) {
                        var url = result.imageUrls[idx];
                        var ext = 'png';
                        if (url.indexOf('.jpg') >= 0 || url.indexOf('.jpeg') >= 0) ext = 'jpg';
                        else if (url.indexOf('.gif') >= 0) ext = 'gif';
                        var fileName = 'img_' + (idx + 1) + '.' + ext;
                        result.description = result.description.split(url).join(fileName);
                    }
                }

                // Difficulty (D1~D8)
                result.difficulty = '';
                var badgeEl = document.querySelector('.badge, [class*="badge"], [class*="difficulty"]');
                if (badgeEl) {
                    var badgeText = badgeEl.textContent.trim();
                    if (badgeText.match(/D\d/)) result.difficulty = badgeText;
                }
                // 타이틀에서 D 레벨 추출 시도
                if (!result.difficulty) {
                    var titleEl = document.querySelector('.problem_title, h2, h3');
                    if (titleEl) {
                        var dMatch = titleEl.textContent.match(/D(\d)/);
                        if (dMatch) result.difficulty = 'D' + dMatch[1];
                    }
                }

                // Time/Memory limits - 모든 li 요소에서 검색
                result.timeLimit = '';
                result.memoryLimit = '';
                var allLi = document.querySelectorAll('li');
                for (var i = 0; i < allLi.length; i++) {
                    var text = allLi[i].textContent.replace(/\s+/g, ' ').trim();
                    if (text.match(/시간|Time/i) && text.match(/초|sec/i)) {
                        result.timeLimit = text.replace(/^[·•\s]*시간\s*:?\s*/, '').trim();
                    }
                    if (text.match(/메모리|Memory/i) && text.match(/MB|KB/i)) {
                        result.memoryLimit = text.replace(/^[·•\s]*메모리\s*:?\s*/, '').trim();
                    }
                }

                // Test cases - input.txt / output.txt 다운로드
                result.testCases = [];
                result.inputFileContent = '';
                result.outputFileContent = '';

                // 콜백 래퍼
                var sendResult = function() {
                    var jsonStr = JSON.stringify(result);
                    $queryJs
                };

                // 콘텐츠가 충분하지 않으면 force가 아닌 한 콜백하지 않음
                if (!force && result.description.length < 50 && result.title.length < 3) {
                    return;
                }

                // URL에서 contestProbId 추출하여 result에 포함
                var cpId = '';
                var urlMatch = window.location.href.match(/contestProbId=([A-Za-z0-9_]+)/);
                if (urlMatch) cpId = urlMatch[1];
                result.contestProbId = cpId;

                // 1단계: 다운로드 링크/버튼의 ng-click, onclick, href 속성 수집
                var inputDownloadInfo = null;
                var outputDownloadInfo = null;

                var allElemsDl = document.querySelectorAll('a, button');
                for (var i = 0; i < allElemsDl.length; i++) {
                    var el = allElemsDl[i];
                    var text = el.textContent.trim();
                    var href = el.getAttribute('href') || '';
                    var ngClick = el.getAttribute('ng-click') || '';
                    var onClick = el.getAttribute('onclick') || '';

                    // "다운로드" 버튼 또는 input/output 관련 링크
                    var textLower = text.toLowerCase();
                    var isDownload = (text === '다운로드' || textLower === 'download');
                    var isInput = (textLower === 'input.txt' || textLower === 'input' || textLower.match(/sample_input/));
                    var isOutput = (textLower === 'output.txt' || textLower === 'output' || textLower.match(/sample_output/));

                    // href에서도 input/output 패턴 감지
                    if (!isInput && !isOutput && !isDownload) {
                        var hrefLower = href.toLowerCase();
                        if (hrefLower.match(/sample_input|type=input|fileName=input/i)) isInput = true;
                        if (hrefLower.match(/sample_output|type=output|fileName=output/i)) isOutput = true;
                    }

                    if (isInput || isOutput || isDownload) {
                        var info = { href: href, ngClick: ngClick, onClick: onClick, text: text };
                        console.log('SWEA DL element:', JSON.stringify(info));

                        // 다운로드 버튼인 경우, 부모에서 input/output 판단
                        var isForInput = isInput;
                        var isForOutput = isOutput;
                        if (isDownload && !isForInput && !isForOutput) {
                            var p = el.parentElement;
                            for (var d = 0; d < 5 && p; d++) {
                                var pt = (p.textContent || '').toLowerCase();
                                if ((pt.indexOf('입력') >= 0 || pt.indexOf('input') >= 0) && pt.indexOf('output') < 0 && pt.indexOf('출력') < 0) { isForInput = true; break; }
                                if ((pt.indexOf('출력') >= 0 || pt.indexOf('output') >= 0) && pt.indexOf('input') < 0 && pt.indexOf('입력') < 0) { isForOutput = true; break; }
                                p = p.parentElement;
                            }
                        }

                        if (isForInput && !inputDownloadInfo) inputDownloadInfo = info;
                        if (isForOutput && !outputDownloadInfo) outputDownloadInfo = info;
                    }
                }

                console.log('SWEA Input DL:', JSON.stringify(inputDownloadInfo));
                console.log('SWEA Output DL:', JSON.stringify(outputDownloadInfo));

                // 2단계: 화면에서 보이는 테스트 데이터 미리보기 추출
                var inputText = '';
                var outputText = '';

                // "입력"/"출력" 헤더가 있는 섹션에서 데이터 추출
                var allBoxes = document.querySelectorAll('div, section, td');
                for (var i = 0; i < allBoxes.length; i++) {
                    var box = allBoxes[i];
                    var children = box.children;
                    if (children.length < 1) continue;
                    var headerText = (children[0].textContent || '').trim();
                    // "입력" 또는 "출력" 헤더를 가진 박스
                    if (headerText === '입력' || headerText === '출력') {
                        // 헤더 제외한 데이터 영역에서 텍스트 추출
                        var dataText = '';
                        for (var j = 1; j < children.length; j++) {
                            var child = children[j];
                            var ct = (child.textContent || '').trim();
                            // 파일명(sample_input.txt 등)은 제외
                            if (ct && !ct.match(/sample_(input|output)/i) && !ct.match(/^\d+_sample/i)
                                && ct !== '다운로드' && ct !== 'download') {
                                dataText += (dataText ? '\n' : '') + ct;
                            }
                        }
                        if (headerText === '입력' && dataText && !inputText) inputText = dataText;
                        if (headerText === '출력' && dataText && !outputText) outputText = dataText;
                    }
                }

                // fallback: pre, textarea, 코드 블록에서
                if (!inputText || !outputText) {
                    var preEls = document.querySelectorAll('pre, textarea, .CodeMirror, code');
                    if (preEls.length >= 2) {
                        if (!inputText) inputText = preEls[0].textContent.trim();
                        if (!outputText) outputText = preEls[1].textContent.trim();
                    }
                }

                // fallback: sample 클래스 섹션
                if (!inputText || !outputText) {
                    var sections = document.querySelectorAll('.problem_sample, .sample_data, [class*="sample"], [class*="test"]');
                    for (var i = 0; i < sections.length; i++) {
                        var sec = sections[i];
                        var secText = sec.textContent || '';
                        if (secText.indexOf('input') >= 0 && !inputText) {
                            inputText = secText.replace(/\d*_?sample_input\.txt/gi, '').replace(/input\.txt/gi, '').replace(/다운로드/g, '').replace(/입력/g, '').trim();
                        }
                        if (secText.indexOf('output') >= 0 && !outputText) {
                            outputText = secText.replace(/\d*_?sample_output\.txt/gi, '').replace(/output\.txt/gi, '').replace(/다운로드/g, '').replace(/출력/g, '').trim();
                        }
                    }
                }

                // 3단계: ng-click에서 다운로드 함수 직접 호출 시도
                var downloadViaAngular = function(dlInfo) {
                    if (!dlInfo) return null;
                    // ng-click 파싱 → Angular scope 함수 직접 호출
                    if (dlInfo.ngClick && typeof angular !== 'undefined') {
                        try {
                            var ctrl = document.querySelector('[ng-controller]');
                            if (ctrl) {
                                var scope = angular.element(ctrl).scope();
                                // ng-click에서 함수명과 인자 추출 (예: fnDown('input') 또는 sampleDown(contestProbId, 'input'))
                                var fnMatch = dlInfo.ngClick.match(/([a-zA-Z_$][a-zA-Z0-9_$]*)\s*\(/);
                                if (fnMatch && scope) {
                                    console.log('SWEA: Found Angular function:', fnMatch[1]);
                                }
                            }
                        } catch(e) {}
                    }
                    // href가 유효한 다운로드 URL인 경우
                    if (dlInfo.href && dlInfo.href !== '#' && dlInfo.href !== 'javascript:void(0)' && !dlInfo.href.startsWith('javascript:')) {
                        var url = dlInfo.href;
                        if (url.startsWith('/')) url = 'https://swexpertacademy.com' + url;
                        return url;
                    }
                    return null;
                };

                var inputUrl = downloadViaAngular(inputDownloadInfo);
                var outputUrl = downloadViaAngular(outputDownloadInfo);

                // 4단계: URL이 없으면 알려진 패턴 시도
                var downloadUrls = [];
                if (inputUrl && outputUrl) {
                    downloadUrls.push({ input: inputUrl, output: outputUrl });
                }
                if (cpId) {
                    // SWEA 알려진 다운로드 패턴들
                    downloadUrls.push({
                        input: '/main/code/problem/problemSampleDown.do?contestProbId=' + cpId + '&type=input',
                        output: '/main/code/problem/problemSampleDown.do?contestProbId=' + cpId + '&type=output'
                    });
                    downloadUrls.push({
                        input: '/main/code/problem/problemSampleDownload.do?contestProbId=' + cpId + '&type=input',
                        output: '/main/code/problem/problemSampleDownload.do?contestProbId=' + cpId + '&type=output'
                    });
                    downloadUrls.push({
                        input: '/main/sampledata/download?contestProbId=' + cpId + '&fileName=input.txt',
                        output: '/main/sampledata/download?contestProbId=' + cpId + '&fileName=output.txt'
                    });
                    downloadUrls.push({
                        input: '/main/code/problem/fileDownload.do?contestProbId=' + cpId + '&fileName=input.txt',
                        output: '/main/code/problem/fileDownload.do?contestProbId=' + cpId + '&fileName=output.txt'
                    });
                }

                // 5단계: 다운로드 시도 (유효한 텍스트 파일인지 검증)
                var tryDownload = function(urls, idx) {
                    if (idx >= urls.length) {
                        sendResult();
                        return;
                    }
                    console.log('SWEA: Trying download URL pair', idx, urls[idx].input, urls[idx].output);
                    Promise.all([
                        fetch(urls[idx].input, { credentials: 'include' }).then(function(r) {
                            console.log('SWEA input fetch status:', r.status, r.headers.get('content-type'));
                            return r.text();
                        }).catch(function(e) { console.log('Input fetch error:', e); return ''; }),
                        fetch(urls[idx].output, { credentials: 'include' }).then(function(r) {
                            console.log('SWEA output fetch status:', r.status, r.headers.get('content-type'));
                            return r.text();
                        }).catch(function(e) { console.log('Output fetch error:', e); return ''; })
                    ]).then(function(files) {
                        var inp = (files[0] || '').trim();
                        var out = (files[1] || '').trim();
                        console.log('SWEA download result - input len:', inp.length, 'output len:', out.length);
                        console.log('SWEA input preview:', inp.substring(0, 200));
                        console.log('SWEA output preview:', out.substring(0, 200));
                        // HTML이 아닌 실제 데이터인지 검증
                        var isHtml = function(s) {
                            return s.startsWith('<!') || s.startsWith('<html') || s.startsWith('<HTML') ||
                                   s.includes('<!DOCTYPE') || s.includes('<link') || s.includes('<script') ||
                                   (s.includes('<head>') && s.includes('<body'));
                        };
                        if (inp && out && !isHtml(inp) && !isHtml(out) && inp.length < 10000000) {
                            result.inputFileContent = inp;
                            result.outputFileContent = out;
                            console.log('SWEA: Download SUCCESS from URL pair', idx);
                            sendResult();
                        } else {
                            console.log('SWEA: Download failed (HTML or empty), trying next...');
                            tryDownload(urls, idx + 1);
                        }
                    }).catch(function(e) {
                        console.log('SWEA download error:', e);
                        tryDownload(urls, idx + 1);
                    });
                };

                // 화면 미리보기 텍스트를 테스트 케이스에 넣기
                if (inputText && outputText) {
                    result.testCases.push({ input: inputText, output: outputText });
                }

                // input.txt/output.txt 다운로드 시도
                if (downloadUrls.length > 0) {
                    tryDownload(downloadUrls, 0);
                } else {
                    sendResult();
                }
            })();
        """.trimIndent()

        cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
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
                            input = obj.get("input")?.asString ?: "",
                            expectedOutput = obj.get("output")?.asString ?: ""
                        )
                    )
                }
            }

            // input.txt / output.txt 파일 내용 저장
            inputFileContent = json.get("inputFileContent")?.asString ?: ""
            outputFileContent = json.get("outputFileContent")?.asString ?: ""

            // 이미지 URL 파싱
            val urls = mutableListOf<String>()
            val urlsArr = json.getAsJsonArray("imageUrls")
            if (urlsArr != null) {
                for (urlEl in urlsArr) {
                    val url = urlEl.asString
                    if (url.isNotBlank()) urls.add(url)
                }
            }
            imageUrls = urls


            val actualId = json.get("problemNumber")?.asString?.ifBlank { null } ?: problemId
            // contestProbId: JS에서 URL로 추출한 값 > Kotlin 필드 값 > 빈 문자열
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

    fun getProblem(): Problem? = problem
}
