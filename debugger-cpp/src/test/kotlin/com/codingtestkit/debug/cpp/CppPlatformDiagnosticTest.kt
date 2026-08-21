package com.codingtestkit.debug.cpp

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** CLion 테스트 픽스처에 C/C++ 플러그인·실행 구성이 실제로 있는지 진단 */
class CppPlatformDiagnosticTest : BasePlatformTestCase() {

    fun `test dump clion plugin and configuration types`() {
        for (id in listOf("com.intellij.clion", "com.intellij.cidr.lang", "com.intellij.cidr.base")) {
            val p = PluginManagerCore.getPlugin(PluginId.getId(id))
            println("[diag] plugin $id -> ${if (p == null) "없음" else "loaded=${p.isEnabled}"}")
        }
        val types = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
        println("[diag] 총 실행 구성 타입 ${types.size}개")
        types.forEach { println("[diag] type: ${it.id}") }
    }
}
