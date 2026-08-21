package com.codingtestkit.ui

import com.codingtestkit.model.CodeTemplate
import com.codingtestkit.model.Language
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.service.TemplateService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * 패널 UI 배선 검증 (이슈 #34, #35).
 *
 * 스윙 컴포넌트가 실제로 만들어지는지, 메뉴 항목이 붙는지, 리스너가 해제되는지는
 * 순수 함수 테스트로 닿지 않는다. 헤드리스 플랫폼 테스트에서 EDT로 만들어 확인한다.
 * (화면에 그려지는 모습은 여전히 사람 눈이 필요하지만, '아예 안 뜨는' 종류는 여기서 걸린다.)
 */
class PanelWiringTest : BasePlatformTestCase() {

    private fun <T> onEdt(block: () -> T): T {
        var result: T? = null
        var error: Throwable? = null
        com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded(Runnable {
            try { result = block() } catch (t: Throwable) { error = t }
        })
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    fun `test template panel builds without error and disposes`() {
        val panel = onEdt { TemplatePanel(project) }
        assertNotNull(panel)
        onEdt { panel.dispose() }
    }

    fun `test template context menu offers load, platform default and delete`() {
        TemplateService.getInstance(project).saveTemplate(
            CodeTemplate(name = "base", language = Language.JAVA.displayName, code = "// x")
        )
        val panel = onEdt { TemplatePanel(project) }
        try {
            val template = TemplateService.getInstance(project).getTemplate("base")!!
            val menu = onEdt { panel.buildTemplateContextMenu(template) }
            val labels = menu.subElements.mapNotNull { (it as? JMenuItem)?.text }
            assertEquals("불러오기 / 기본 지정 / 삭제 세 항목이어야 한다", 3, labels.size)

            // 플랫폼 기본 서브메뉴에 플랫폼이 전부 올라와야 한다
            val submenu = menu.subElements.filterIsInstance<JMenu>().firstOrNull()
            assertNotNull("플랫폼 기본 서브메뉴가 있어야 한다", submenu)
            assertEquals(ProblemSource.entries.size, submenu!!.itemCount)
        } finally {
            onEdt { panel.dispose() }
            TemplateService.getInstance(project).deleteTemplate("base")
        }
    }

    fun `test problem panel registers and releases its theme listener`() {
        val settings = com.codingtestkit.service.PluginSettingsService.getInstance()
        val before = listenerCount(settings)
        val panel = onEdt { ProblemPanel(project) }
        assertEquals("패널이 테마 리스너를 등록해야 설정 변경이 반영된다", before + 1, listenerCount(settings))
        onEdt { panel.dispose() }
        assertEquals("dispose에서 해제하지 않으면 프로젝트를 닫아도 리스너가 쌓인다",
            before, listenerCount(settings))
    }

    private fun listenerCount(settings: com.codingtestkit.service.PluginSettingsService): Int {
        val f = settings.javaClass.getDeclaredField("embedThemeListeners")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (f.get(settings) as MutableList<*>).size
    }
}
