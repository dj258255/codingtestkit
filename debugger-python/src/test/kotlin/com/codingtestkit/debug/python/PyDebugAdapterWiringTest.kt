package com.codingtestkit.debug.python

import com.codingtestkit.model.Language
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 실제 PyCharm 플랫폼에서 어댑터 배선을 검증한다 (이슈 #36).
 *
 * 이 어댑터는 PyCharm의 실행 구성 API에 붙는데, 그 API가 개편되면 컴파일은 통과해도
 * 런타임에 조용히 실패한다(캐치 후 일반 경고). 그래서 IDE를 띄우지 않고도
 * "붙을 대상이 실제로 거기 있는가"를 여기서 확인한다.
 */
class PyDebugAdapterWiringTest : BasePlatformTestCase() {

    private val adapter = PyDebugAdapter()

    fun `test adapter claims python only`() {
        assertTrue(adapter.supports(Language.PYTHON))
        for (other in Language.entries.filter { it != Language.PYTHON }) {
            assertFalse("$other 를 담당하면 안 된다", adapter.supports(other))
        }
    }

    fun `test adapter reports available on a real PyCharm platform`() {
        assertTrue(
            "PyCharm 플랫폼에서 isAvailable이 false면 디버그 버튼이 안내만 띄운다",
            adapter.isAvailable()
        )
    }

    fun `test adapter owns the launch`() {
        assertTrue("IDE가 실행까지 소유해야 케이스 입력을 리다이렉트로 넘길 수 있다", adapter.ownsLaunch())
    }

    fun `test python run configuration type exists`() {
        val type = ConfigurationTypeUtil.findConfigurationType("PythonConfigurationType")
        assertNotNull("실행 구성 타입이 없으면 어댑터가 구성을 만들 수 없다", type)
        assertFalse(type!!.configurationFactories.isEmpty())
    }

    fun `test the configuration exposes the fields the adapter sets`() {
        val type = ConfigurationTypeUtil.findConfigurationType("PythonConfigurationType")!!
        val factory = type.configurationFactories.first()
        val cfg = factory.createTemplateConfiguration(project)
        val cls = cfg.javaClass

        // 어댑터가 케이스 입력을 물리는 경로 — 이름이 바뀌면 여기서 먼저 깨진다
        for (name in listOf("setScriptName", "setWorkingDirectory", "setInputFile", "setRedirectInput")) {
            val found = cls.methods.any { it.name == name }
            assertTrue("$name 이(가) 없으면 케이스 입력 리다이렉션이 조용히 실패한다", found)
        }
    }
}
