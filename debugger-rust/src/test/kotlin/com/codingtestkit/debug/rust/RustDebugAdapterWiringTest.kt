package com.codingtestkit.debug.rust

import com.codingtestkit.model.Language
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 실제 RustRover 플랫폼에서 어댑터 배선 검증 (이슈 #36).
 *
 * 다른 어댑터와 달리 isAvailable()이 true인지는 여기서 단언하지 않는다.
 * 헤드리스 픽스처는 번들 Rust 플러그인(com.jetbrains.rust)을 비활성 상태로 두어
 * Cargo 실행 구성이 등록되지 않는다(RustPlatformDiagnosticTest가 그 사실을 찍는다).
 * 실제 RustRover에서는 번들 플러그인이 기본 활성이라 상황이 다르다.
 *
 * 대신 여기서 확인할 수 있는 것은 확인한다 — 어댑터가 묶여 있는 API가 이 플랫폼에
 * 실제로 존재하는지, 그리고 게이트가 예외 없이 판정하는지.
 */
class RustDebugAdapterWiringTest : BasePlatformTestCase() {

    private val adapter = RustDebugAdapter()

    fun `test adapter claims rust only`() {
        assertTrue(adapter.supports(Language.RUST))
        for (other in Language.entries.filter { it != Language.RUST }) {
            assertFalse("$other 를 담당하면 안 된다", adapter.supports(other))
        }
    }

    fun `test adapter owns the launch`() {
        assertTrue(adapter.ownsLaunch())
    }

    fun `test the cargo API the adapter binds to exists on this platform`() {
        // 클래스가 사라지면 어댑터는 컴파일조차 안 되지만, 패키지 이동은 런타임에만 드러난다
        val cls = Class.forName("org.rust.cargo.runconfig.command.CargoCommandConfigurationType")
        assertNotNull(cls)
    }

    fun `test availability gate never throws`() {
        // 플러그인이 없거나 비활성이어도 예외가 아니라 false로 떨어져야 한다 —
        // 예외가 새면 디버그 버튼이 IDE 오류 리포트를 띄운다
        val available = adapter.isAvailable()
        assertTrue("게이트는 true/false 중 하나로 판정해야 한다", available || !available)
    }
}
