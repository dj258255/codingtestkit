package com.codingtestkit.debug.rust

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** RustRover 테스트 픽스처에 Rust 플러그인·Cargo 실행 구성이 실제로 있는지 진단 */
class RustPlatformDiagnosticTest : BasePlatformTestCase() {

    fun `test dump rust plugin and cargo configuration types`() {
        val ids = listOf("com.jetbrains.rust", "org.rust.lang", "com.intellij.modules.rustrover")
        for (id in ids) {
            val p = PluginManagerCore.getPlugin(PluginId.getId(id))
            println("[diag] plugin $id -> ${if (p == null) "없음" else "loaded=${p.isEnabled}"}")
        }
        val types = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
        println("[diag] 총 실행 구성 타입 ${types.size}개")
        types.filter { it.id.contains("Cargo", true) || it.displayName.contains("Cargo", true) }
            .forEach { println("[diag] cargo-ish: id=${it.id} name=${it.displayName}") }
        types.take(25).forEach { println("[diag] type: ${it.id}") }
    }
}
