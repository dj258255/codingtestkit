package com.codingtestkit.debug.cpp

import com.codingtestkit.model.Language
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * CLion 어댑터 배선 검증 (이슈 #36).
 *
 * isAvailable()이 true인지는 여기서 단언하지 않는다 — 헤드리스 픽스처에는 CLion의
 * C/C++ 플러그인이 아예 로드되지 않아 실행 구성 타입이 하나뿐이다
 * (CppPlatformDiagnosticTest가 그 사실을 찍는다). 실제 CLion에서는 다르다.
 *
 * 대신 언어 담당과 게이트의 안전성은 여기서 확인한다.
 */
class CppDebugAdapterWiringTest : BasePlatformTestCase() {

    private val adapter = CppDebugAdapter()

    fun `test adapter claims cpp only`() {
        assertTrue(adapter.supports(Language.CPP))
        for (other in Language.entries.filter { it != Language.CPP }) {
            assertFalse("$other 를 담당하면 안 된다", adapter.supports(other))
        }
    }

    fun `test adapter owns the launch`() {
        assertTrue(adapter.ownsLaunch())
    }

    fun `test availability gate never throws`() {
        // 플러그인이 없으면 예외가 아니라 false로 떨어져야 한다 —
        // 예외가 새면 디버그 버튼이 IDE 오류 리포트를 띄운다
        val available = adapter.isAvailable()
        assertFalse("이 픽스처에는 C/C++ 플러그인이 없으므로 false가 정상", available)
    }
}
