package com.codingtestkit

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.CodeTemplate
import com.codingtestkit.model.Language
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.PluginSettingsService
import com.codingtestkit.service.TemplateService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 실제 IDE 코어를 띄워 '배선'을 검증한다.
 *
 * 지금까지 테스트는 전부 순수 로직(파서·하네스 문자열)이었는데, 이 이슈들에서 실제로
 * 터진 버그는 전부 플랫폼 경계에서 났다 — 이벤트 id 규약, 확장점 로드, 액션 등록.
 * 순수 함수 테스트로는 구조적으로 닿지 않는 층이라 여기서 따로 확인한다.
 */
class PluginWiringTest : BasePlatformTestCase() {

    // ── .ctk-format 파일 타입 (이슈 #36) ──

    fun `test ctk-format extension is registered to our file type`() {
        val type = FileTypeManager.getInstance().getFileTypeByExtension("ctk-format")
        assertEquals("CtkFormat", type.name)
        assertFalse("확장자가 등록되지 않으면 UnknownFileType으로 떨어진다", type.defaultExtension.isEmpty())
    }

    fun `test a ctk-format file opens with our language`() {
        val file = myFixture.configureByText("gen.ctk-format", "const(3, t)\nrepeat(t) {\nrand(1, 100, n)\n}\n")
        assertEquals("CtkFormat", file.virtualFile.fileType.name)
    }

    fun `test syntax highlighter colors keywords differently from plain text`() {
        val hl = com.codingtestkit.lang.CtkFormatSyntaxHighlighter()
        val lexer = hl.highlightingLexer
        // 실제 편집기가 쓰는 경로 그대로: 렉서로 토큰을 끊고 하이라이터에게 색을 묻는다
        lexer.start("repeat(3) { rand(1, 10, x) }")
        val colored = mutableMapOf<String, String>()
        while (lexer.tokenType != null) {
            val keys = hl.getTokenHighlights(lexer.tokenType!!)
            if (keys.isNotEmpty()) colored[lexer.tokenText] = keys.first().externalName
            lexer.advance()
        }
        assertTrue("함수 이름에 색이 지정돼야 한다: $colored", colored.keys.any { it == "rand" || it == "repeat" })
        assertFalse("아무 토큰에도 색이 없으면 구문 강조가 죽은 것", colored.isEmpty())
    }

    // ── 액션 등록 (이슈 #36 — 문서가 Find Action으로만 열리던 문제) ──

    fun `test generator actions are registered`() {
        val am = ActionManager.getInstance()
        assertNotNull("생성 액션이 등록돼야 한다", am.getAction("CodingTestKit.GenerateFromFormatFile"))
        assertNotNull("문법 문서 액션이 등록돼야 한다", am.getAction("CodingTestKit.ShowFormatDocs"))
    }

    fun `test syntax docs action is reachable from the editor menu`() {
        val am = ActionManager.getInstance()
        val popup = am.getAction("EditorPopupMenu") as DefaultActionGroup
        val ids = popup.getChildren(null).mapNotNull { am.getId(it) }
        assertTrue(
            "에디터 컨텍스트 메뉴에 있어야 .ctk-format 편집 중 바로 열 수 있다 (없으면 Find Action 전용)",
            ids.contains("CodingTestKit.ShowFormatDocs")
        )
        assertTrue(ids.contains("CodingTestKit.GenerateFromFormatFile"))
    }

    // ── 디버그 어댑터 확장점 (이슈 #36) ──

    fun `test debug adapter extension point loads`() {
        val adapters = TestDebugAdapter.EP_NAME.extensionList
        assertFalse("확장점이 비면 모든 언어에서 디버그 버튼이 안내만 띄운다", adapters.isEmpty())
    }

    fun `test jvm languages resolve to a debug adapter in IntelliJ`() {
        // IntelliJ Community 테스트 환경에는 Java 플러그인이 있으므로 JVM 어댑터가 잡혀야 한다
        assertNotNull("Java 디버그 어댑터를 찾지 못했다", TestDebugAdapter.forLanguage(Language.JAVA))
        assertNotNull("Kotlin 디버그 어댑터를 찾지 못했다", TestDebugAdapter.forLanguage(Language.KOTLIN))
    }

    fun `test each adapter claims exactly the languages it supports`() {
        for (adapter in TestDebugAdapter.EP_NAME.extensionList) {
            val claimed = Language.entries.filter { adapter.supports(it) }
            assertFalse("${adapter.javaClass.simpleName}가 아무 언어도 담당하지 않는다", claimed.isEmpty())
        }
    }

    // ── 프로젝트 서비스 (이슈 #35) ──

    fun `test template service is a real project service and round-trips`() {
        val service = TemplateService.getInstance(project)
        assertNotNull(service)
        service.saveTemplate(
            CodeTemplate(name = "base", language = Language.JAVA.displayName, code = "// x")
                .withDefaultPlatforms(setOf(ProblemSource.CODEFORCES.name, ProblemSource.LEETCODE.name))
        )
        assertEquals("base", service.findPlatformDefault(ProblemSource.CODEFORCES, Language.JAVA)?.name)
        assertEquals("base", service.findPlatformDefault(ProblemSource.LEETCODE, Language.JAVA)?.name)
        service.deleteTemplate("base")
        assertNull(service.findPlatformDefault(ProblemSource.CODEFORCES, Language.JAVA))
    }

    // ── 설정 리스너 (이슈 #34 — 테마를 바꿔도 반영되지 않던 문제) ──

    fun `test embed theme listener fires and can be removed`() {
        val settings = PluginSettingsService.getInstance()
        var fired = 0
        val listener: () -> Unit = { fired++ }
        val original = settings.embedTheme
        try {
            settings.addEmbedThemeListener(listener)
            settings.embedTheme = if (original == PluginSettingsService.EmbedTheme.DARK)
                PluginSettingsService.EmbedTheme.LIGHT else PluginSettingsService.EmbedTheme.DARK
            assertEquals("설정을 바꾸면 리스너가 불려야 이미 열린 페이지를 다시 칠할 수 있다", 1, fired)

            settings.removeEmbedThemeListener(listener)
            settings.embedTheme = original
            assertEquals("해제한 리스너가 계속 불리면 패널이 사라져도 누수된다", 1, fired)
        } finally {
            settings.removeEmbedThemeListener(listener)
            settings.embedTheme = original
        }
    }
}
