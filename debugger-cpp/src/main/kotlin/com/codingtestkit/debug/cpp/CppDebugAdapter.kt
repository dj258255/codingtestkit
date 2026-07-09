package com.codingtestkit.debug.cpp

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.execution.InputRedirectAware
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfiguration
import com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfigurationType
import com.jetbrains.cidr.execution.ExecutableData
import java.io.File

/**
 * C++ 디버그 어댑터 (이슈 #36 Tier 3) — IDE가 실행까지 소유하는 방식.
 *
 * PID attach는 attach 시 블로킹 read가 EINTR로 깨져 cin이 죽는 구조적 결함이 있어
 * 폐기했다. 대신 CLion의 Custom Build Application 실행 구성으로, CodeRunner가
 * -g -O0로 미리 빌드해둔 바이너리(artifact)를 IDE가 처음부터 LLDB 아래에서 실행한다.
 * 케이스 입력은 구성의 입력 리다이렉션으로 전달한다.
 *
 * 바이너리는 사용자 실제 소스 파일로 빌드되므로 브레이크포인트가 경로로 바인딩된다.
 */
class CppDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(CppDebugAdapter::class.java)

    override fun supports(language: Language): Boolean = language == Language.CPP

    override fun ownsLaunch(): Boolean = true

    override fun isAvailable(): Boolean = try {
        ConfigurationTypeUtil.findConfigurationType(CLionExternalRunConfigurationType::class.java) != null
    } catch (e: Throwable) {
        log.warn("[CodingTestKit] C++ debug adapter unavailable: ${e.javaClass.simpleName}: ${e.message}")
        false
    }

    override fun launchDebug(
        project: Project, sessionName: String, sourceFile: File, input: String, workingDir: File, artifact: File?
    ): Boolean {
        if (artifact == null || !artifact.exists()) {
            log.warn("[CodingTestKit] C++ debug: compiled binary missing")
            return false
        }
        return try {
            val type = ConfigurationTypeUtil.findConfigurationType(CLionExternalRunConfigurationType::class.java)
            val runManager = RunManager.getInstance(project)
            val settings = runManager.createConfiguration(sessionName, type.configurationFactories[0])
            val cfg = settings.configuration as CLionExternalRunConfiguration
            cfg.executableData = ExecutableData(artifact.absolutePath)
            if (input.isNotBlank()) {
                val inputFile = File(artifact.parentFile, "ctk_stdin.txt").apply { writeText(input, Charsets.UTF_8) }
                (cfg as? InputRedirectAware)?.inputRedirectOptions?.let {
                    it.isRedirectInput = true
                    it.redirectInputPath = inputFile.absolutePath
                }
            }
            settings.isTemporary = true

            var ok = true
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
                } catch (e: Throwable) {
                    log.warn("[CodingTestKit] C++ debug launch failed", e)
                    ok = false
                }
            }
            ok
        } catch (e: Throwable) {
            log.warn("[CodingTestKit] C++ debug launch failed", e)
            false
        }
    }
}
