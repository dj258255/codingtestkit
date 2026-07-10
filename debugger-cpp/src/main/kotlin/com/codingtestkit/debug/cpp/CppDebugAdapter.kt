package com.codingtestkit.debug.cpp

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.execution.InputRedirectAware
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * C++ 디버그 어댑터 (이슈 #36 Tier 3) — CLion의 "C/C++ File" 실행 구성 사용.
 *
 * 이 구성 타입은 CMake 프로젝트 없이 단일 파일을 컴파일(-g 디버그 심볼 자동)하고
 * 디버거 아래에서 실행한다 — gutter의 Debug 버튼이 쓰는 바로 그 방식.
 *
 * 구성 타입을 클래스가 아닌 ID("CppFileRunConfiguration")로 레지스트리에서 찾고
 * 설정은 리플렉션으로 한다: 해당 클래스는 CLion의 V2 콘텐츠 모듈
 * (intellij.clion.runFile)에 있어 자체 클래스로더를 쓰므로, 우리 플러그인
 * 클래스로더에서 직접 참조하면 NoClassDefFoundError가 난다. 레지스트리의
 * 인스턴스를 통해 접근하면 클래스로더 격리를 우회할 수 있다.
 */
class CppDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(CppDebugAdapter::class.java)

    private fun findType(): ConfigurationType? = try {
        ConfigurationTypeUtil.findConfigurationType("CppFileRunConfiguration")
    } catch (e: Throwable) {
        log.warn("[CodingTestKit] C++ 'C/C++ File' configuration type lookup failed: ${e.message}")
        null
    }

    override fun supports(language: Language): Boolean = language == Language.CPP

    override fun ownsLaunch(): Boolean = true

    override fun isAvailable(): Boolean = findType() != null

    override fun launchDebug(
        project: Project, sessionName: String, sourceFile: File, input: String, workingDir: File, artifact: File?
    ): Boolean {
        return try {
            val type = findType() ?: return false
            val runManager = RunManager.getInstance(project)
            val settings = runManager.createConfiguration(sessionName, type.configurationFactories[0])
            val cfg = settings.configuration

            // options.sourceFile = 경로 → CLion이 -g로 컴파일 후 디버그 실행
            val options = cfg.javaClass.getMethod("getOptions").invoke(cfg)
            options.javaClass.getMethod("setSourceFile", String::class.java)
                .invoke(options, sourceFile.absolutePath)

            // 케이스 입력 — CidrRunConfiguration은 InputRedirectOptions(플랫폼 인터페이스)를 직접 구현
            if (input.isNotBlank()) {
                val inputFile = File(workingDir, "ctk_stdin.txt").apply { writeText(input, Charsets.UTF_8) }
                (cfg as? InputRedirectAware.InputRedirectOptions)?.let {
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
