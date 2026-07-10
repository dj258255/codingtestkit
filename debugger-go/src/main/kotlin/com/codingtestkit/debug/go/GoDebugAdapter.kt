package com.codingtestkit.debug.go

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.goide.execution.GoRemoteDebugConfigurationType
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Go(dlv) 원격 디버그 attach 어댑터 (이슈 #36 Tier 3).
 *
 * CodeRunner가 `dlv exec --headless --listen=127.0.0.1:port`로 띄워둔 디버그 서버에
 * GoLand의 "Go Remote" 실행 구성을 만들어 attach한다 — JVM 어댑터의 JDWP attach와
 * 동일한 패턴 (dlv headless도 클라이언트가 붙을 때까지 프로그램을 정지시켜 둔다).
 *
 * org.jetbrains.plugins.go 플러그인이 있는 IDE(GoLand, IDEA Ultimate+Go)에서만
 * 로드된다 (plugin.xml의 optional 의존성 + codingtestkit-go.xml에 EP로 등록).
 */
class GoDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(GoDebugAdapter::class.java)

    override fun supports(language: Language): Boolean = language == Language.GO

    override fun isAvailable(): Boolean = try {
        ConfigurationTypeUtil.findConfigurationType(GoRemoteDebugConfigurationType::class.java) != null
    } catch (_: Throwable) {
        false
    }

    override fun attachToPort(project: Project, sessionName: String, port: Int): Boolean {
        return try {
            val type = ConfigurationTypeUtil.findConfigurationType(GoRemoteDebugConfigurationType::class.java)
            val runManager = RunManager.getInstance(project)
            val settings = runManager.createConfiguration(sessionName, type.configurationFactories[0])
            val cfg = settings.configuration as GoRemoteDebugConfigurationType.DlvRemoteDebugConfiguration
            cfg.host = "127.0.0.1"
            cfg.port = port
            settings.isTemporary = true

            // Debug 실행자로 실행 → GoLand 디버거가 dlv 서버에 attach
            ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
            true
        } catch (e: Throwable) {
            log.warn("[CodingTestKit] Go debug attach failed", e)
            false
        }
    }
}
