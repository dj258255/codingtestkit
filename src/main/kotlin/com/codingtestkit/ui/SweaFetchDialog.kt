package com.codingtestkit.ui

import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
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
    private val statusLabel = JLabel("SWEA 문제 페이지 로딩 중...").apply {
        foreground = Color.GRAY
    }

    init {
        // 입력이 알파벳을 포함하면 contestProbId로 직접 접근
        fetchState = if (problemId.any { it.isLetter() }) {
            contestProbId = problemId
            FetchState.LOADING_DETAIL
        } else {
            FetchState.LOADING_LIST
        }
        title = "SWEA 문제 가져오기"
        setSize(500, 150)
        init()
    }

    override fun createActions(): Array<Action> {
        myCancelAction.putValue(Action.NAME, "취소")
        return arrayOf(cancelAction)
    }

    override fun createCenterPanel(): JComponent {
        if (!JBCefApp.isSupported()) {
            return JLabel("<html>JCEF를 사용할 수 없어 SWEA 문제를 가져올 수 없습니다.</html>")
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

        val browser = JBCefBrowser(getInitialUrl())

        // 브라우저는 보이지 않지만 렌더링을 위해 컴포넌트 계층에 포함
        val browserWrapper = browser.component
        browserWrapper.preferredSize = Dimension(1024, 768)
        browserWrapper.minimumSize = Dimension(0, 0)

        // 로딩 표시만 보이는 패널
        val loadingPanel = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
        }
        loadingPanel.add(JLabel("<html><b>SWEA 문제 #$problemId</b>를 가져오고 있습니다...</html>"), BorderLayout.NORTH)
        loadingPanel.add(statusLabel, BorderLayout.CENTER)

        // 브라우저 보기 토글 버튼
        val showBrowserBtn = JButton("브라우저 보기")
        showBrowserBtn.addActionListener {
            if (browserWrapper.isVisible) {
                browserWrapper.isVisible = false
                panel.preferredSize = Dimension(480, 100)
                showBrowserBtn.text = "브라우저 보기"
                setSize(500, 150)
            } else {
                browserWrapper.isVisible = true
                panel.preferredSize = Dimension(880, 650)
                showBrowserBtn.text = "브라우저 숨기기"
                setSize(900, 700)
            }
            panel.revalidate()
        }
        val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        btnPanel.add(showBrowserBtn)
        loadingPanel.add(btnPanel, BorderLayout.SOUTH)

        panel.add(loadingPanel, BorderLayout.NORTH)

        // 브라우저는 기본적으로 숨김
        browserWrapper.isVisible = false
        panel.add(browserWrapper, BorderLayout.CENTER)

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

    private fun handlePageLoaded(cefBrowser: CefBrowser?, jsQuery: JBCefJSQuery) {
        val url = cefBrowser?.url ?: ""

        // URL 기반으로 현재 페이지 판단
        when {
            // 상세 페이지에 도착
            url.contains("problemDetail") && url.contains("contestProbId") -> {
                fetchState = FetchState.EXTRACTING
                SwingUtilities.invokeLater {
                    statusLabel.text = "문제 상세 페이지 로딩 완료, 콘텐츠 추출 중..."
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
                    statusLabel.text = "문제 목록에서 #$problemId 검색 중..."
                }
                // 검색 후 contestProbId 추출 시도를 여러 번 반복
                Timer(2000) {
                    if (!extracted && fetchState == FetchState.SEARCHING) searchForProblem(cefBrowser, jsQuery)
                }.apply { isRepeats = false; start() }

                Timer(5000) {
                    if (!extracted && fetchState == FetchState.SEARCHING) searchForProblem(cefBrowser, jsQuery)
                }.apply { isRepeats = false; start() }

                Timer(8000) {
                    if (!extracted && fetchState == FetchState.SEARCHING) searchForProblem(cefBrowser, jsQuery)
                }.apply { isRepeats = false; start() }
            }
            // 직접 contestProbId로 접근한 경우 (알파벳 ID)
            fetchState == FetchState.LOADING_DETAIL -> {
                fetchState = FetchState.EXTRACTING
                SwingUtilities.invokeLater {
                    statusLabel.text = "문제 상세 페이지 로딩 완료, 콘텐츠 추출 중..."
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
                        statusLabel.text = "문제를 찾았습니다! 상세 페이지로 이동 중..."
                        statusLabel.foreground = Color(0, 120, 0)
                    }
                    val detailUrl = "https://swexpertacademy.com/main/code/problem/problemDetail.do?contestProbId=$foundId"
                    browser.cefBrowser.executeJavaScript("window.location.href='$detailUrl';", "", 0)
                } else {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "문제 #$problemId 를 찾을 수 없습니다. 직접 문제를 찾아주세요."
                        statusLabel.foreground = Color.RED
                    }
                }
            }
            // 검색 결과: 못 찾음
            result == "NOT_FOUND" -> {
                SwingUtilities.invokeLater {
                    statusLabel.text = "문제 #$problemId 를 찾을 수 없습니다. 로그인 후 직접 문제를 찾아주세요."
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
                            statusLabel.text = "콘텐츠가 아직 로드되지 않았습니다. 잠시만 기다려주세요..."
                            statusLabel.foreground = Color.ORANGE
                        }
                    }
                }
            }
        }
    }

    /**
     * 문제 목록 페이지에서 contestProbId를 추출하여 상세 페이지로 직접 이동
     */
    private fun searchForProblem(cefBrowser: CefBrowser?, jsQuery: JBCefJSQuery) {
        if (cefBrowser == null || extracted) return

        val callbackJs = jsQuery.inject("result")

        val js = """
            (function() {
                var problemNum = '$problemId';

                // 전략 1: AngularJS scope에서 문제 리스트 데이터 추출
                if (typeof angular !== 'undefined') {
                    try {
                        var ctrl = document.querySelector('[ng-controller]');
                        if (ctrl) {
                            var scope = angular.element(ctrl).scope();
                            if (scope) {
                                // scope 내 배열에서 문제 데이터 찾기
                                var searchInObj = function(obj, depth) {
                                    if (depth > 3 || !obj) return null;
                                    if (Array.isArray(obj)) {
                                        for (var i = 0; i < obj.length; i++) {
                                            var item = obj[i];
                                            if (item && typeof item === 'object') {
                                                // contestProbId가 있고, 문제 번호가 일치하는 항목 찾기
                                                var id = item.contestProbId || item.problemId || item.probId;
                                                var num = item.problemNumber || item.probNo || item.no || item.problemNo;
                                                var title = item.problemTitle || item.title || item.problemName || '';
                                                if (id && (String(num) === problemNum || title.indexOf(problemNum) === 0)) {
                                                    return String(id);
                                                }
                                            }
                                        }
                                    } else if (typeof obj === 'object') {
                                        for (var key in obj) {
                                            if (key.charAt(0) === '$' || key === 'this' || key === 'constructor') continue;
                                            try {
                                                var found = searchInObj(obj[key], depth + 1);
                                                if (found) return found;
                                            } catch(e) {}
                                        }
                                    }
                                    return null;
                                };
                                var found = searchInObj(scope, 0);
                                if (found) {
                                    var result = 'FOUND:' + found;
                                    $callbackJs
                                    return;
                                }
                            }
                        }
                    } catch(e) {}
                }

                // 전략 2: ng-click 또는 href에서 contestProbId 추출
                var allElements = document.querySelectorAll('[ng-click], a[href*="contestProbId"]');
                for (var i = 0; i < allElements.length; i++) {
                    var el = allElements[i];
                    var context = el.closest('tr, li, div') || el;
                    var text = context.textContent || '';
                    // 이 요소 근처에 문제 번호가 있는지 확인
                    if (text.indexOf(problemNum + '.') >= 0 || text.match(new RegExp('\\b' + problemNum + '\\b'))) {
                        var ngClick = el.getAttribute('ng-click') || '';
                        var href = el.getAttribute('href') || '';
                        var combined = ngClick + ' ' + href;
                        var idMatch = combined.match(/contestProbId[=:'"\\s]+([A-Za-z0-9_]+)/);
                        if (idMatch) {
                            var result = 'FOUND:' + idMatch[1];
                            $callbackJs
                            return;
                        }
                    }
                }

                // 전략 3: 검색 실행 후 재시도를 위해 AngularJS 검색 트리거
                var inp = document.querySelector('input[placeholder*="번호"], input[placeholder*="키워드"], input[ng-model*="problemTitle"], input[ng-model*="search"]');
                if (inp) {
                    if (typeof angular !== 'undefined') {
                        try {
                            var scope = angular.element(inp).scope();
                            var ngModel = inp.getAttribute('ng-model');
                            if (scope && ngModel) {
                                scope.${'$'}apply(function() {
                                    var parts = ngModel.split('.');
                                    var obj = scope;
                                    for (var k = 0; k < parts.length - 1; k++) {
                                        if (obj[parts[k]]) obj = obj[parts[k]];
                                    }
                                    obj[parts[parts.length - 1]] = problemNum;
                                });
                            }
                            // 검색 함수 호출
                            var ctrlScope = angular.element(document.querySelector('[ng-controller]')).scope();
                            if (ctrlScope) {
                                var fnNames = ['search', 'fnSearch', 'searchProblem', 'goSearch', 'doSearch', 'fnSelectList', 'selectList'];
                                for (var j = 0; j < fnNames.length; j++) {
                                    if (typeof ctrlScope[fnNames[j]] === 'function') {
                                        ctrlScope.${'$'}apply(function() { ctrlScope[fnNames[j]](); });
                                        break;
                                    }
                                }
                            }
                        } catch(e) {}
                    }
                    // 값 직접 설정 + 이벤트
                    inp.focus();
                    inp.value = problemNum;
                    ['input', 'change', 'keyup'].forEach(function(evt) {
                        inp.dispatchEvent(new Event(evt, {bubbles: true}));
                    });
                    // Enter 키
                    setTimeout(function() {
                        inp.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
                        inp.dispatchEvent(new KeyboardEvent('keypress', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
                    }, 300);
                }

                // 전략 4: 모든 ng-click, onclick, href 속성에서 contestProbId 추출
                var allWithAction = document.querySelectorAll('[ng-click], [onclick], a[href]');
                for (var a = 0; a < allWithAction.length; a++) {
                    var el = allWithAction[a];
                    var attr = (el.getAttribute('ng-click') || '') + ' ' +
                               (el.getAttribute('onclick') || '') + ' ' +
                               (el.getAttribute('href') || '');
                    var idMatch = attr.match(/contestProbId[=:'"\\s]+([A-Za-z0-9_]{10,30})/);
                    if (!idMatch) idMatch = attr.match(/['"]([A-Za-z0-9_]{14,25})['"]/);
                    if (idMatch) {
                        // 이 요소 또는 근처에 문제 번호가 있는지 확인
                        var container = el.closest('tr, li, div, section') || el.parentElement;
                        var nearby = (container ? container.textContent : el.textContent) || '';
                        if (nearby.indexOf(problemNum + '.') >= 0 || nearby.match(new RegExp('\\b' + problemNum + '\\b'))) {
                            var result = 'FOUND:' + idMatch[1];
                            $callbackJs
                            return;
                        }
                    }
                }

                // 전략 5: 페이지 HTML 전체에서 문제 번호 근처의 contestProbId 패턴 검색
                var bodyHtml = document.body.innerHTML;
                // 문제 번호 주변 500자 범위에서 contestProbId 찾기
                var numIdx = bodyHtml.indexOf('>' + problemNum + '.');
                if (numIdx < 0) numIdx = bodyHtml.indexOf(problemNum + '. ');
                if (numIdx >= 0) {
                    var startIdx = Math.max(0, numIdx - 2000);
                    var endIdx = Math.min(bodyHtml.length, numIdx + 2000);
                    var slice = bodyHtml.substring(startIdx, endIdx);
                    var sliceMatch = slice.match(/contestProbId[=:'"\\s]+([A-Za-z0-9_]{10,30})/);
                    if (sliceMatch) {
                        var result = 'FOUND:' + sliceMatch[1];
                        $callbackJs
                        return;
                    }
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
                for (var i = 0; i < titleSelectors.length; i++) {
                    var el = document.querySelector(titleSelectors[i]);
                    if (el) {
                        var clone = el.cloneNode(true);
                        var badge = clone.querySelector('.badge');
                        if (badge) badge.remove();
                        var text = clone.textContent.trim();
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

                // input.txt / output.txt 다운로드 - ng-click에서 다운로드 함수 추출
                var cpId = '';
                var urlMatch = window.location.href.match(/contestProbId=([A-Za-z0-9_]+)/);
                if (urlMatch) cpId = urlMatch[1];

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

                    // "다운로드" 버튼 또는 "input.txt"/"output.txt" 링크
                    var isDownload = (text === '다운로드' || text.toLowerCase() === 'download');
                    var isInput = (text.toLowerCase() === 'input.txt' || text.toLowerCase() === 'input');
                    var isOutput = (text.toLowerCase() === 'output.txt' || text.toLowerCase() === 'output');

                    if (isInput || isOutput || isDownload) {
                        var info = { href: href, ngClick: ngClick, onClick: onClick, text: text };
                        console.log('SWEA DL element:', JSON.stringify(info));

                        // 다운로드 버튼인 경우, 부모에서 input/output 판단
                        var isForInput = isInput;
                        var isForOutput = isOutput;
                        if (isDownload) {
                            var p = el.parentElement;
                            for (var d = 0; d < 5 && p; d++) {
                                var pt = p.textContent || '';
                                if (pt.indexOf('input') >= 0 && pt.indexOf('output') < 0) { isForInput = true; break; }
                                if (pt.indexOf('output') >= 0 && pt.indexOf('input') < 0) { isForOutput = true; break; }
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

                // pre, textarea, 또는 코드 블록에서 데이터 찾기
                var preEls = document.querySelectorAll('pre, textarea, .CodeMirror, code');
                if (preEls.length >= 2) {
                    inputText = preEls[0].textContent.trim();
                    outputText = preEls[1].textContent.trim();
                }

                // pre가 없으면 input.txt/output.txt 근처의 텍스트 박스에서 추출
                if (!inputText || !outputText) {
                    var sections = document.querySelectorAll('.problem_sample, .sample_data, [class*="sample"], [class*="test"]');
                    for (var i = 0; i < sections.length; i++) {
                        var sec = sections[i];
                        var secText = sec.textContent || '';
                        if (secText.indexOf('input') >= 0 && !inputText) {
                            inputText = secText.replace(/input\.txt/gi, '').replace(/다운로드/g, '').replace(/입력/g, '').trim();
                        }
                        if (secText.indexOf('output') >= 0 && !outputText) {
                            outputText = secText.replace(/output\.txt/gi, '').replace(/다운로드/g, '').replace(/출력/g, '').trim();
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


            problem = Problem(
                source = ProblemSource.SWEA,
                id = problemId,
                title = title.ifBlank { "SWEA #$problemId" },
                description = description,
                testCases = testCases,
                timeLimit = timeLimit,
                memoryLimit = memoryLimit,
                difficulty = difficulty.ifBlank { "Unrated" },
                contestProbId = contestProbId ?: ""
            )
        } catch (e: Exception) {
            problem = null
        }
    }

    fun getProblem(): Problem? = problem
}
