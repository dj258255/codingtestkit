package com.codingtestkit.debug.ruby

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Ruby 디버그 어댑터 (이슈 #36) — IDE가 실행까지 소유하는 방식.
 *
 * Ruby 실행 구성에는 입력 리다이렉션이 없어, 임시 runner.rb가
 * `$stdin.reopen(입력파일)` 후 사용자 파일을 `load`하는 방식으로 우회한다.
 * load는 사용자 실제 파일을 실행하므로 브레이크포인트가 그 파일 경로로
 * 정상 바인딩된다.
 *
 * Ruby 플러그인 클래스는 참조하지 않는다(구성 타입은 레지스트리 ID로 조회,
 * 설정은 리플렉션 — Ruby 클래스는 V2 콘텐츠 모듈이라 클래스로더가 격리됨).
 * 로드는 optional 의존성(org.jetbrains.plugins.ruby)이 제어한다.
 */
class RubyDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(RubyDebugAdapter::class.java)

    private companion object {
        const val RUBY_TYPE_ID = "RubyRunConfigurationType"
    }

    override fun supports(language: Language): Boolean = language == Language.RUBY

    override fun ownsLaunch(): Boolean = true

    override fun isAvailable(): Boolean = try {
        ConfigurationTypeUtil.findConfigurationType(RUBY_TYPE_ID) != null
    } catch (_: Throwable) {
        false
    }

    override fun launchDebug(
        project: Project, sessionName: String, sourceFile: File, input: String, workingDir: File, artifact: File?
    ): Boolean {
        return try {
            // stdin 재지정 + 사용자 파일 load 하는 러너 (경로의 작은따옴표는 이스케이프)
            val runnerDir = java.nio.file.Files.createTempDirectory("ctk-ruby-debug").toFile()
            val inputFile = File(runnerDir, "ctk_stdin.txt").apply { writeText(input, Charsets.UTF_8) }
            fun rb(path: String) = path.replace("\\", "\\\\").replace("'", "\\'")
            val runner = File(runnerDir, "ctk_runner.rb").apply {
                writeText(
                    "\$stdin.reopen(File.open('${rb(inputFile.absolutePath)}', 'r'))\n" +
                    "load '${rb(sourceFile.absolutePath)}'\n", Charsets.UTF_8
                )
            }

            val type = ConfigurationTypeUtil.findConfigurationType(RUBY_TYPE_ID)
            if (type == null) {
                log.warn("[CodingTestKit] Ruby debug: configuration type not found")
                return false
            }
            val runManager = RunManager.getInstance(project)
            val settings = runManager.createConfiguration(sessionName, type.configurationFactories[0])
            val cfg = settings.configuration
            // Ruby 클래스는 격리된 모듈 클래스로더 소속 — 리플렉션으로 설정
            cfg.javaClass.getMethod("setScriptPath", String::class.java).invoke(cfg, runner.absolutePath)
            cfg.javaClass.getMethod("setWorkingDirectory", String::class.java).invoke(cfg, workingDir.absolutePath)
            settings.isTemporary = true

            var ok = true
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
                } catch (e: Throwable) {
                    log.warn("[CodingTestKit] Ruby debug launch failed", e)
                    ok = false
                }
            }
            ok
        } catch (e: Throwable) {
            log.warn("[CodingTestKit] Ruby debug launch failed", e)
            false
        }
    }
}
