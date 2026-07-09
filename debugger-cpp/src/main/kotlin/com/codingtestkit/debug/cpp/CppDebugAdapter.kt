package com.codingtestkit.debug.cpp

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.runfile.CppFileRunConfiguration
import com.jetbrains.cidr.cpp.runfile.CppFileRunConfigurationType
import java.io.File

/**
 * C++ 디버그 어댑터 (이슈 #36 Tier 3) — CLion의 "C/C++ File" 실행 구성 사용.
 *
 * 이 구성 타입은 CMake 프로젝트 없이 단일 파일을 컴파일(-g 디버그 심볼 자동 추가)하고
 * 디버거 아래에서 실행한다 — gutter의 Debug 버튼이 쓰는 바로 그 방식.
 * (Custom Build Application은 Custom Build Target을 강제해 실패했음)
 *
 * 소스 파일 경로만 넘기면 컴파일까지 CLion이 처리하므로, 우리가 g++로 미리 빌드할 필요가 없다.
 * 케이스 입력은 CidrRunConfiguration의 InputRedirectOptions로 전달한다.
 */
class CppDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(CppDebugAdapter::class.java)

    override fun supports(language: Language): Boolean = language == Language.CPP

    override fun ownsLaunch(): Boolean = true

    override fun isAvailable(): Boolean = try {
        ConfigurationTypeUtil.findConfigurationType(CppFileRunConfigurationType::class.java) != null
    } catch (e: Throwable) {
        log.warn("[CodingTestKit] C++ debug adapter unavailable: ${e.javaClass.simpleName}: ${e.message}")
        false
    }

    override fun launchDebug(
        project: Project, sessionName: String, sourceFile: File, input: String, workingDir: File, artifact: File?
    ): Boolean {
        return try {
            val type = ConfigurationTypeUtil.findConfigurationType(CppFileRunConfigurationType::class.java)
            val runManager = RunManager.getInstance(project)
            val settings = runManager.createConfiguration(sessionName, type.configurationFactories[0])
            val cfg = settings.configuration as CppFileRunConfiguration
            cfg.options.sourceFile = sourceFile.absolutePath   // CLion이 -g로 컴파일 후 디버그
            if (input.isNotBlank()) {
                val inputFile = File(workingDir, "ctk_stdin.txt").apply { writeText(input, Charsets.UTF_8) }
                cfg.isRedirectInput = true
                cfg.redirectInputPath = inputFile.absolutePath
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
