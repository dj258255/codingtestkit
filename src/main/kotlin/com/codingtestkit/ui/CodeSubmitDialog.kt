package com.codingtestkit.ui

import com.codingtestkit.model.Language
import com.codingtestkit.model.ProblemSource
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

class CodeSubmitDialog(
    project: Project,
    private val source: ProblemSource,
    private val problemId: String,
    private val code: String,
    private val language: Language
) : DialogWrapper(project) {

    var onAccepted: (() -> Unit)? = null
    private var submitted = false
    private var accepted = false
    private var codeInjected = false
    private var resultCheckTimer: Timer? = null
    private val statusLabel = JLabel("페이지 로딩 중...").apply {
        foreground = Color.GRAY
    }

    init {
        title = "${source.displayName} #$problemId 제출"
        setSize(950, 750)
        init()
    }

    override fun createActions(): Array<Action> {
        myCancelAction.putValue(Action.NAME, "닫기")
        return arrayOf(cancelAction)
    }

    override fun createCenterPanel(): JComponent {
        if (!JBCefApp.isSupported()) {
            return JLabel("<html>JCEF 브라우저를 사용할 수 없습니다.<br>사이트에서 직접 제출해주세요.</html>")
        }
        return createBrowserPanel()
    }

    private fun getSubmitUrl(): String = when (source) {
        ProblemSource.BAEKJOON -> "https://www.acmicpc.net/submit/$problemId"
        ProblemSource.PROGRAMMERS -> "https://school.programmers.co.kr/learn/courses/30/lessons/$problemId"
        ProblemSource.SWEA -> "https://swexpertacademy.com/main/solvingProblem/solvingProblem.do?contestProbId=$problemId"
        ProblemSource.LEETCODE -> "https://leetcode.com/problems/$problemId/"
    }

    private fun getGuideText(): String = when (source) {
        ProblemSource.BAEKJOON -> "코드와 언어가 자동 입력됩니다. Cloudflare 인증 후 <b>제출</b> 버튼을 클릭하세요."
        ProblemSource.PROGRAMMERS -> "코드가 자동 입력됩니다. 확인 후 <b>제출</b> 버튼을 클릭하세요."
        ProblemSource.SWEA -> "코드가 자동 입력됩니다. 확인 후 <b>제출</b> 버튼을 클릭하세요."
        ProblemSource.LEETCODE -> "코드가 자동 입력됩니다. 확인 후 <b>Submit</b> 버튼을 클릭하세요."
    }

    private fun createBrowserPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(930, 700)

        val browser = JBCefBrowser(getSubmitUrl())

        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        topPanel.add(JLabel("<html>${getGuideText()}</html>"))
        panel.add(topPanel, BorderLayout.NORTH)

        panel.add(browser.component, BorderLayout.CENTER)

        val injectButton = JButton("코드 붙여넣기").apply {
            toolTipText = "코드가 자동으로 입력되지 않았다면 이 버튼을 눌러보세요"
            addActionListener {
                when (source) {
                    ProblemSource.BAEKJOON -> injectBaekjoonCode(browser.cefBrowser)
                    ProblemSource.PROGRAMMERS -> injectProgrammersCode(browser.cefBrowser)
                    ProblemSource.SWEA -> injectSweaCode(browser.cefBrowser)
                    ProblemSource.LEETCODE -> injectLeetCodeCode(browser.cefBrowser)
                }
            }
        }

        val bottomPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 8))
        bottomPanel.add(injectButton)
        bottomPanel.add(statusLabel)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true) return
                val url = cefBrowser?.url ?: ""

                // 스크롤 성능 개선
                cefBrowser?.executeJavaScript("""
                    (function(){
                        var s=document.createElement('style');
                        s.textContent='html,body{scroll-behavior:smooth}*{-webkit-overflow-scrolling:touch}';
                        document.head.appendChild(s);
                        var ticking=false;
                        window.addEventListener('wheel',function(e){
                            if(ticking)return;
                            ticking=true;
                            requestAnimationFrame(function(){ticking=false;});
                        },{passive:true});
                    })();
                """.trimIndent(), url, 0)

                when (source) {
                    ProblemSource.BAEKJOON -> handleBaekjoonLoad(cefBrowser, url)
                    ProblemSource.PROGRAMMERS -> handleProgrammersLoad(cefBrowser, url)
                    ProblemSource.SWEA -> handleSweaLoad(cefBrowser, url)
                    ProblemSource.LEETCODE -> handleLeetCodeLoad(cefBrowser, url)
                }
            }
        }, browser.cefBrowser)

        // 제목 변경 감지 → 채점 결과 확인
        browser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onTitleChange(cefBrowser: CefBrowser?, title: String?) {
                if (title == null || accepted) return
                when {
                    title.startsWith("__CTK_ACCEPTED__") -> markAccepted()
                    title.startsWith("__CTK_REJECTED__") -> {
                        resultCheckTimer?.stop()
                        updateStatus("✗ 틀렸습니다. 결과를 확인하세요.", Color(200, 80, 80))
                    }
                }
            }
        }, browser.cefBrowser)

        return panel
    }

    // ─── 백준 ───

    private fun handleBaekjoonLoad(cefBrowser: CefBrowser?, url: String) {
        if ((url.contains("/submit/") || url.contains("/submit/$problemId")) && !codeInjected) {
            codeInjected = true
            injectBaekjoonCode(cefBrowser)
        } else if (url.contains("/status") && !submitted) {
            markSubmitted()
            checkResultFromPage(cefBrowser, url)
        }
    }

    private fun injectBaekjoonCode(cefBrowser: CefBrowser?) {
        if (cefBrowser == null) return
        val escaped = escapeForJs(code)
        val langId = language.baekjoonId

        val js = """
            (function() {
                var langSelect = document.getElementById('language');
                if (langSelect) {
                    langSelect.value = '$langId';
                    langSelect.dispatchEvent(new Event('change'));
                }
                var cmEl = document.querySelector('.CodeMirror');
                if (cmEl && cmEl.CodeMirror) {
                    cmEl.CodeMirror.setValue('$escaped');
                } else {
                    var sourceArea = document.getElementById('source');
                    if (sourceArea) sourceArea.value = '$escaped';
                }
            })();
        """.trimIndent()

        cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
        updateStatus("코드 입력 완료! Cloudflare 인증 후 '제출' 버튼을 클릭하세요.", Color(0, 100, 180))
    }

    // ─── 프로그래머스 ───

    private val programmersLangMap = mapOf(
        Language.JAVA to "java",
        Language.PYTHON to "python3",
        Language.CPP to "cpp",
        Language.KOTLIN to "kotlin",
        Language.JAVASCRIPT to "javascript"
    )

    private fun handleProgrammersLoad(cefBrowser: CefBrowser?, url: String) {
        if (url.contains("/lessons/$problemId") && !codeInjected) {
            codeInjected = true
            // 에디터가 로드될 시간을 줌 (3초 후 시도, 실패 시 5초 후 재시도)
            Timer(3000) { injectProgrammersCode(cefBrowser) }.apply {
                isRepeats = false
                start()
            }
            Timer(6000) { injectProgrammersCode(cefBrowser) }.apply {
                isRepeats = false
                start()
            }
        } else if (url.contains("/submissions") && !submitted) {
            markSubmitted()
            checkResultFromPage(cefBrowser, url)
        }
    }

    private fun injectProgrammersCode(cefBrowser: CefBrowser?) {
        if (cefBrowser == null) return
        val escaped = escapeForJs(code)

        // 프로그래머스는 CodeMirror, Monaco, 또는 Ace Editor 사용
        val js = """
            (function() {
                var injected = false;

                // 1. CodeMirror
                var cmEl = document.querySelector('.CodeMirror');
                if (cmEl && cmEl.CodeMirror) {
                    cmEl.CodeMirror.setValue('$escaped');
                    injected = true;
                }

                // 2. Monaco Editor
                if (!injected && typeof monaco !== 'undefined') {
                    try {
                        var editors = monaco.editor.getEditors();
                        if (editors && editors.length > 0) {
                            editors[0].setValue('$escaped');
                            injected = true;
                        }
                    } catch(e) {}
                }

                // 3. Ace Editor
                if (!injected && typeof ace !== 'undefined') {
                    var aceEls = document.querySelectorAll('.ace_editor');
                    if (aceEls.length > 0) {
                        var editor = ace.edit(aceEls[0]);
                        editor.setValue('$escaped', -1);
                        injected = true;
                    }
                }

                // 4. textarea fallback
                if (!injected) {
                    var ta = document.querySelector('textarea.code-editor, textarea[name=code], textarea');
                    if (ta) {
                        ta.value = '$escaped';
                        ta.dispatchEvent(new Event('input', {bubbles: true}));
                    }
                }
            })();
        """.trimIndent()

        cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
        updateStatus("코드 입력 완료! 확인 후 '제출 후 채점하기' 버튼을 클릭하세요.", Color(0, 100, 180))
    }

    // ─── SWEA ───

    private fun handleSweaLoad(cefBrowser: CefBrowser?, url: String) {
        if (url.contains("solvingProblem") && !codeInjected) {
            codeInjected = true
            // 페이지 로드 후 에디터가 초기화될 시간을 줌
            Timer(3000) { injectSweaCode(cefBrowser) }.apply {
                isRepeats = false
                start()
            }
            Timer(6000) { injectSweaCode(cefBrowser) }.apply {
                isRepeats = false
                start()
            }
        } else if ((url.contains("userSubmitList") || url.contains("problemSubmitHistory") || url.contains("submissionDetail")) && !submitted) {
            markSubmitted()
            checkResultFromPage(cefBrowser, url)
        }
    }

    private fun injectSweaCode(cefBrowser: CefBrowser?) {
        if (cefBrowser == null) return
        val escaped = escapeForJs(code)
        val langId = language.sweaId

        val js = """
            (function() {
                // 언어 선택 (select 요소 또는 AngularJS ng-model)
                var langSelects = document.querySelectorAll('select[ng-model*="language"], select[ng-model*="lang"], select#language, select[name="language"]');
                for (var i = 0; i < langSelects.length; i++) {
                    var sel = langSelects[i];
                    sel.value = '$langId';
                    sel.dispatchEvent(new Event('change', {bubbles: true}));
                    // AngularJS 바인딩 업데이트
                    if (typeof angular !== 'undefined') {
                        try {
                            var scope = angular.element(sel).scope();
                            var ngModel = sel.getAttribute('ng-model');
                            if (scope && ngModel) {
                                scope.${'$'}apply(function() {
                                    var parts = ngModel.split('.');
                                    var obj = scope;
                                    for (var k = 0; k < parts.length - 1; k++) {
                                        if (obj[parts[k]]) obj = obj[parts[k]];
                                    }
                                    obj[parts[parts.length - 1]] = '$langId';
                                });
                            }
                        } catch(e) { console.log('Angular lang select error:', e); }
                    }
                }

                var injected = false;

                // 1. Ace Editor (SWEA 주로 사용)
                if (typeof ace !== 'undefined') {
                    var editors = document.querySelectorAll('.ace_editor');
                    if (editors.length > 0) {
                        var editor = ace.edit(editors[0]);
                        editor.setValue('$escaped', -1);
                        injected = true;
                    }
                }

                // 2. CodeMirror
                if (!injected) {
                    var cmEl = document.querySelector('.CodeMirror');
                    if (cmEl && cmEl.CodeMirror) {
                        cmEl.CodeMirror.setValue('$escaped');
                        injected = true;
                    }
                }

                // 3. textarea fallback
                if (!injected) {
                    var textareas = document.querySelectorAll('textarea[name=source], textarea[ng-model*="source"], textarea.source, textarea');
                    for (var i = 0; i < textareas.length; i++) {
                        var ta = textareas[i];
                        if (ta.offsetHeight > 50) {
                            ta.value = '$escaped';
                            ta.dispatchEvent(new Event('input', {bubbles: true}));
                            injected = true;
                            break;
                        }
                    }
                }

                console.log('SWEA code injection:', injected ? 'success' : 'failed');
            })();
        """.trimIndent()

        cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
        updateStatus("코드 입력 완료! 확인 후 '제출' 버튼을 클릭하세요.", Color(0, 100, 180))
    }

    // ─── LeetCode ───

    private fun handleLeetCodeLoad(cefBrowser: CefBrowser?, url: String) {
        if (url.contains("/problems/") && !url.contains("/accounts/login") && !codeInjected) {
            codeInjected = true
            Timer(3000) { injectLeetCodeCode(cefBrowser) }.apply {
                isRepeats = false
                start()
            }
            Timer(6000) { injectLeetCodeCode(cefBrowser) }.apply {
                isRepeats = false
                start()
            }
        } else if (url.contains("/submissions/") && !submitted) {
            markSubmitted()
            checkResultFromPage(cefBrowser, url)
        }
    }

    private fun injectLeetCodeCode(cefBrowser: CefBrowser?) {
        if (cefBrowser == null) return
        val escaped = escapeForJs(code)

        val js = """
            (function() {
                var injected = false;

                // Monaco Editor (LeetCode uses Monaco)
                if (typeof monaco !== 'undefined') {
                    try {
                        var editors = monaco.editor.getEditors();
                        if (editors && editors.length > 0) {
                            editors[0].setValue('$escaped');
                            injected = true;
                        }
                    } catch(e) {}
                }

                // Fallback: CodeMirror
                if (!injected) {
                    var cmEl = document.querySelector('.CodeMirror');
                    if (cmEl && cmEl.CodeMirror) {
                        cmEl.CodeMirror.setValue('$escaped');
                        injected = true;
                    }
                }

                console.log('LeetCode code injection:', injected ? 'success' : 'failed');
            })();
        """.trimIndent()

        cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
        updateStatus("코드 입력 완료! 확인 후 'Submit' 버튼을 클릭하세요.", Color(0, 100, 180))
    }

    // ─── 공통 ───

    private fun escapeForJs(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")
    }

    private fun markSubmitted() {
        submitted = true
        SwingUtilities.invokeLater {
            statusLabel.text = "제출 완료! 채점 결과를 확인하는 중..."
            statusLabel.foreground = Color(0, 100, 180)
        }
    }

    private fun markAccepted() {
        if (accepted) return
        accepted = true
        resultCheckTimer?.stop()
        SwingUtilities.invokeLater {
            statusLabel.text = "✓ 맞았습니다! 닫기 버튼을 눌러주세요."
            statusLabel.foreground = Color(0, 130, 0)
            onAccepted?.invoke()
        }
    }

    /**
     * 채점 결과 페이지에서 "맞았습니다" / "Accepted" 등을 감지하는 JS를 주입.
     * 주기적으로 폴링하며 결과가 나오면 title에 마커를 삽입해서 Kotlin에서 감지.
     */
    private fun startResultPolling(cefBrowser: CefBrowser?) {
        if (cefBrowser == null || accepted) return

        val checkJs = when (source) {
            ProblemSource.BAEKJOON -> """
                (function() {
                    var rows = document.querySelectorAll('#status-table tbody tr, .table tbody tr');
                    for (var i = 0; i < rows.length; i++) {
                        var cells = rows[i].querySelectorAll('td');
                        for (var j = 0; j < cells.length; j++) {
                            var text = cells[j].innerText || '';
                            if (text.indexOf('맞았습니다') >= 0 || text.indexOf('Accepted') >= 0) {
                                document.title = '__CTK_ACCEPTED__';
                                return;
                            }
                            if (text.indexOf('틀렸습니다') >= 0 || text.indexOf('Wrong') >= 0 ||
                                text.indexOf('시간 초과') >= 0 || text.indexOf('Time Limit') >= 0 ||
                                text.indexOf('메모리 초과') >= 0 || text.indexOf('런타임 에러') >= 0 ||
                                text.indexOf('컴파일 에러') >= 0 || text.indexOf('출력 초과') >= 0) {
                                document.title = '__CTK_REJECTED__';
                                return;
                            }
                        }
                    }
                })();
            """.trimIndent()

            ProblemSource.PROGRAMMERS -> """
                (function() {
                    var el = document.querySelector('.result, .test-result, [class*=result]');
                    var body = document.body.innerText || '';
                    if (body.indexOf('정답입니다') >= 0 || body.indexOf('통과') >= 0) {
                        document.title = '__CTK_ACCEPTED__';
                    } else if (body.indexOf('실패') >= 0 || body.indexOf('오답') >= 0) {
                        document.title = '__CTK_REJECTED__';
                    }
                })();
            """.trimIndent()

            ProblemSource.SWEA -> """
                (function() {
                    var body = document.body.innerText || '';
                    if (body.indexOf('Pass') >= 0 || body.indexOf('PASS') >= 0) {
                        document.title = '__CTK_ACCEPTED__';
                    } else if (body.indexOf('Fail') >= 0 || body.indexOf('FAIL') >= 0) {
                        document.title = '__CTK_REJECTED__';
                    }
                })();
            """.trimIndent()

            ProblemSource.LEETCODE -> """
                (function() {
                    var body = document.body.innerText || '';
                    if (body.indexOf('Accepted') >= 0) {
                        document.title = '__CTK_ACCEPTED__';
                    } else if (body.indexOf('Wrong Answer') >= 0 || body.indexOf('Time Limit') >= 0 ||
                               body.indexOf('Runtime Error') >= 0 || body.indexOf('Memory Limit') >= 0 ||
                               body.indexOf('Compile Error') >= 0) {
                        document.title = '__CTK_REJECTED__';
                    }
                })();
            """.trimIndent()
        }

        // title 변경 감지 JS: 폴링으로 제목 확인
        val detectJs = """
            (function() {
                if (!window.__ctkResultInterval) {
                    window.__ctkResultInterval = setInterval(function() {
                        $checkJs
                    }, 2000);
                }
            })();
        """.trimIndent()

        cefBrowser.executeJavaScript(detectJs, cefBrowser.url, 0)

        // Kotlin 쪽에서도 주기적으로 title을 확인
        resultCheckTimer = Timer(2000) {
            if (accepted) {
                resultCheckTimer?.stop()
                return@Timer
            }
            val titleCheckJs = """
                (function() {
                    if (document.title === '__CTK_ACCEPTED__') {
                        document.title = 'Accepted';
                    } else if (document.title === '__CTK_REJECTED__') {
                        document.title = 'Rejected';
                    }
                    $checkJs
                })();
            """.trimIndent()
            cefBrowser.executeJavaScript(titleCheckJs, cefBrowser.url, 0)

            // title 체크는 executeJavaScript로는 값을 바로 못가져오므로
            // CefBrowser의 title property를 이용 (onTitleChange 대신 직접 확인 불가)
            // 대안: DOM에 hidden element를 생성해서 확인
        }.apply {
            isRepeats = true
            start()
        }
    }

    /**
     * 페이지 제목 변화로 결과 감지 (onLoadEnd에서 호출)
     */
    private fun checkResultFromPage(cefBrowser: CefBrowser?, url: String) {
        if (cefBrowser == null || accepted) return

        // 결과 감지 JS 주입 + 결과 감지 시 페이지 제목 변경으로 콜백
        val resultCallbackJs = """
            (function() {
                if (window.__ctkResultDone) return;
                var check = function() {
                    var accepted = false;
                    var rejected = false;
                    ${when (source) {
                        ProblemSource.BAEKJOON -> """
                            var rows = document.querySelectorAll('#status-table tbody tr, .table tbody tr');
                            for (var i = 0; i < rows.length; i++) {
                                var text = rows[i].innerText || '';
                                if (text.indexOf('맞았습니다') >= 0) { accepted = true; break; }
                                if (text.indexOf('틀렸습니다') >= 0 || text.indexOf('시간 초과') >= 0 ||
                                    text.indexOf('메모리 초과') >= 0 || text.indexOf('런타임 에러') >= 0 ||
                                    text.indexOf('컴파일 에러') >= 0 || text.indexOf('출력 초과') >= 0) { rejected = true; break; }
                            }
                        """.trimIndent()
                        ProblemSource.LEETCODE -> """
                            var body = document.body.innerText || '';
                            if (body.indexOf('Accepted') >= 0) accepted = true;
                            else if (body.indexOf('Wrong Answer') >= 0 || body.indexOf('Time Limit') >= 0 ||
                                     body.indexOf('Runtime Error') >= 0 || body.indexOf('Memory Limit') >= 0) rejected = true;
                        """.trimIndent()
                        ProblemSource.PROGRAMMERS -> """
                            var body = document.body.innerText || '';
                            if (body.indexOf('정답입니다') >= 0 || body.indexOf('테스트를 통과') >= 0) accepted = true;
                            else if (body.indexOf('실패') >= 0) rejected = true;
                        """.trimIndent()
                        ProblemSource.SWEA -> """
                            var body = document.body.innerText || '';
                            if (body.indexOf('Pass') >= 0 || body.indexOf('PASS') >= 0) accepted = true;
                            else if (body.indexOf('Fail') >= 0 || body.indexOf('FAIL') >= 0) rejected = true;
                        """.trimIndent()
                    }}
                    if (accepted) {
                        window.__ctkResultDone = true;
                        clearInterval(window.__ctkResultInterval);
                        document.title = '__CTK_ACCEPTED__' + Date.now();
                    } else if (rejected) {
                        window.__ctkResultDone = true;
                        clearInterval(window.__ctkResultInterval);
                        document.title = '__CTK_REJECTED__' + Date.now();
                    }
                };
                check();
                window.__ctkResultInterval = setInterval(check, 2000);
            })();
        """.trimIndent()

        cefBrowser.executeJavaScript(resultCallbackJs, url, 0)
    }

    private fun updateStatus(text: String, color: Color) {
        SwingUtilities.invokeLater {
            statusLabel.text = text
            statusLabel.foreground = color
        }
    }

    fun isSubmitted(): Boolean = submitted
    fun isAccepted(): Boolean = accepted
}
