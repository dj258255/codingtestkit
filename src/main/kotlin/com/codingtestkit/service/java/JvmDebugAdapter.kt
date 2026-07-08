package com.codingtestkit.service.java

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.execution.remote.RemoteConfigurationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * JVM(JDWP) 원격 디버그 attach 어댑터 (이슈 #36 Tier 1).
 *
 * com.intellij.modules.java가 있는 IDE(IntelliJ IDEA 등)에서만 로드된다
 * (plugin.xml의 optional 의존성 + codingtestkit-java.xml에 EP로 등록).
 */
class JvmDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(JvmDebugAdapter::class.java)

    override fun supports(language: Language): Boolean =
        language == Language.JAVA || language == Language.KOTLIN

    override fun isAvailable(): Boolean = try {
        RemoteConfigurationType.getInstance() != null
    } catch (_: Throwable) {
        false
    }

    override fun attachToPort(project: Project, sessionName: String, port: Int): Boolean {
        return try {
            val type = RemoteConfigurationType.getInstance()
            val runManager = RunManager.getInstance(project)
            val settings = runManager.createConfiguration(sessionName, type.configurationFactories[0])
            val cfg = settings.configuration as RemoteConfiguration
            cfg.HOST = "localhost"
            cfg.PORT = port.toString()
            cfg.SERVER_MODE = false      // 우리 JVM이 서버(리스너), IDE가 붙는다
            cfg.USE_SOCKET_TRANSPORT = true
            settings.isTemporary = true

            // Debug 실행자로 실행 → IDE 디버거가 해당 포트에 attach
            ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
            true
        } catch (e: Throwable) {
            log.warn("[CodingTestKit] JVM debug attach failed", e)
            false
        }
    }
}
