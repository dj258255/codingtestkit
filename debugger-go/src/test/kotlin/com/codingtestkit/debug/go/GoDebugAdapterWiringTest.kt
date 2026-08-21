package com.codingtestkit.debug.go

import com.codingtestkit.model.Language
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** 실제 GoLand 플랫폼에서 어댑터 배선 검증 (이슈 #36) */
class GoDebugAdapterWiringTest : BasePlatformTestCase() {

    private val adapter = GoDebugAdapter()

    fun `test adapter claims go only`() {
        assertTrue(adapter.supports(Language.GO))
        for (other in Language.entries.filter { it != Language.GO }) {
            assertFalse("$other 를 담당하면 안 된다", adapter.supports(other))
        }
    }

    fun `test adapter reports available on a real GoLand platform`() {
        assertTrue("GoLand에서 false면 디버그 버튼이 안내만 띄운다", adapter.isAvailable())
    }
}
