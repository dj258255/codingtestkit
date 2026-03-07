package com.codingtestkit.ui

import com.codingtestkit.model.Language
import com.codingtestkit.model.ProblemSource
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
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

    private var submitted = false
    private var codeInjected = false
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
    }

    private fun getGuideText(): String = when (source) {
        ProblemSource.BAEKJOON -> "코드와 언어가 자동 입력됩니다. Cloudflare 인증 후 <b>제출</b> 버튼을 클릭하세요."
        ProblemSource.PROGRAMMERS -> "코드가 자동 입력됩니다. 확인 후 <b>제출</b> 버튼을 클릭하세요."
        ProblemSource.SWEA -> "코드가 자동 입력됩니다. 확인 후 <b>제출</b> 버튼을 클릭하세요."
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

                when (source) {
                    ProblemSource.BAEKJOON -> handleBaekjoonLoad(cefBrowser, url)
                    ProblemSource.PROGRAMMERS -> handleProgrammersLoad(cefBrowser, url)
                    ProblemSource.SWEA -> handleSweaLoad(cefBrowser, url)
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
        Language.KOTLIN to "kotlin"
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
            statusLabel.text = "제출 완료! 결과를 확인한 후 닫기 버튼을 눌러주세요."
            statusLabel.foreground = Color(0, 130, 0)
        }
    }

    private fun updateStatus(text: String, color: Color) {
        SwingUtilities.invokeLater {
            statusLabel.text = text
            statusLabel.foreground = color
        }
    }

    fun isSubmitted(): Boolean = submitted
}
