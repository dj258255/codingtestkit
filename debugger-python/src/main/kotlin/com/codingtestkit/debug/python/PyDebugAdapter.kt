package com.codingtestkit.debug.python

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.python.run.PythonConfigurationType
import com.jetbrains.python.run.PythonRunConfiguration
import java.io.File

/**
 * Python 로컬 디버그 어댑터 (이슈 #36 Tier 2) — IDE가 실행까지 소유하는 방식.
 *
 * pydevd 역방향 attach는 PyCharm Professional(Pythonid, 유료) 전용이라 Community를
 * 커버하지 못한다. 대신 PythonCore(Community에도 있는 기반 플러그인)의 일반 Python
 * 실행 구성을 debug 실행자로 돌려, 무료 PyCharm까지 지원한다.
 *
 * 브레이크포인트는 소스 파일 경로로 바인딩되고, 케이스 입력은 파일 리다이렉션(<)으로
 * 전달한다. Python SDK(인터프리터)는 프로젝트에 설정된 것을 그대로 사용한다.
 */
class PyDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(PyDebugAdapter::class.java)

    override fun supports(language: Language): Boolean = language == Language.PYTHON

    override fun ownsLaunch(): Boolean = true

    override fun isAvailable(): Boolean = try {
        ConfigurationTypeUtil.findConfigurationType(PythonConfigurationType::class.java) != null
    } catch (e: Throwable) {
        log.warn("[CodingTestKit] Python debug adapter unavailable: ${e.javaClass.simpleName}: ${e.message}")
        false
    }

    override fun launchDebug(project: Project, sessionName: String, sourceFile: File, input: String, workingDir: File): Boolean {
        return try {
            val type = ConfigurationTypeUtil.findConfigurationType(PythonConfigurationType::class.java)
            val runManager = RunManager.getInstance(project)
            val settings = runManager.createConfiguration(sessionName, type.configurationFactories[0])
            val cfg = settings.configuration as PythonRunConfiguration
            cfg.scriptName = sourceFile.absolutePath
            cfg.workingDirectory = workingDir.absolutePath
            if (input.isNotBlank()) {
                val inputFile = File(workingDir, "ctk_stdin.txt")
                inputFile.writeText(input, Charsets.UTF_8)
                cfg.inputFile = inputFile.absolutePath
                cfg.isRedirectInput = true
            }
            settings.isTemporary = true

            var ok = true
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
                } catch (e: Throwable) {
                    log.warn("[CodingTestKit] Python debug launch failed", e)
                    ok = false
                }
            }
            ok
        } catch (e: Throwable) {
            log.warn("[CodingTestKit] Python debug launch failed", e)
            false
        }
    }
}
