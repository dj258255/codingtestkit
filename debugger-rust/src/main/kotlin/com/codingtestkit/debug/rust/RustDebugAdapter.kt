package com.codingtestkit.debug.rust

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import java.io.File

/**
 * Rust 디버그 어댑터 (이슈 #36 Tier 3) — IDE가 실행까지 소유하는 방식.
 *
 * PID attach는 attach 시 블로킹 read가 EINTR로 깨지고 attach 완료와 프로그램 종료가
 * 경쟁하는 구조적 결함이 있어 폐기했다. 대신 RustRover의 Cargo 실행 구성을 debug
 * 실행자로 돌린다 — 프로그램이 처음부터 디버거 아래에서 시작되고, 케이스 입력은
 * 구성에 내장된 입력 리다이렉션으로 안정적으로 전달된다.
 *
 * 브레이크포인트 경로 보존: 임시 Cargo 프로젝트의 [[bin]] path가 사용자 실제 파일을
 * 가리키므로, cargo가 그 파일을 직접 빌드해 디버그 정보에 실제 경로가 기록된다.
 * (cargo의 debug 프로파일은 기본이 최적화 없음 + 디버그 심볼 포함)
 */
class RustDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(RustDebugAdapter::class.java)

    override fun supports(language: Language): Boolean = language == Language.RUST

    override fun ownsLaunch(): Boolean = true

    override fun isAvailable(): Boolean = try {
        ConfigurationTypeUtil.findConfigurationType(CargoCommandConfigurationType::class.java) != null
    } catch (e: Throwable) {
        log.warn("[CodingTestKit] Rust debug adapter unavailable: ${e.javaClass.simpleName}: ${e.message}")
        false
    }

    override fun launchDebug(
        project: Project, sessionName: String, sourceFile: File, input: String, workingDir: File, artifact: File?
    ): Boolean {
        return try {
            // 임시 Cargo 프로젝트 — bin path가 사용자 파일을 직접 가리킨다
            val cargoDir = java.nio.file.Files.createTempDirectory("ctk-rust-debug").toFile()
            File(cargoDir, "Cargo.toml").writeText(
                """
                [package]
                name = "ctk-debug"
                version = "0.0.1"
                edition = "2021"

                [[bin]]
                name = "solution"
                path = "${sourceFile.absolutePath.replace("\\", "\\\\")}"
                """.trimIndent(), Charsets.UTF_8
            )
            val inputFile = File(cargoDir, "ctk_stdin.txt").apply { writeText(input, Charsets.UTF_8) }

            val type = ConfigurationTypeUtil.findConfigurationType(CargoCommandConfigurationType::class.java)
            val runManager = RunManager.getInstance(project)
            val settings = runManager.createConfiguration(sessionName, type.configurationFactories[0])
            val cfg = settings.configuration as CargoCommandConfiguration
            // command·workingDirectory는 setFromCmd가 정석 경로 (2026.1에서 직접 setter 없음)
            cfg.setFromCmd(org.rust.cargo.toolchain.CargoCommandLine(
                "run", cargoDir.toPath(), listOf("--bin", "solution")))
            if (input.isNotBlank()) {
                cfg.isRedirectInput = true
                cfg.redirectInputPath = inputFile.absolutePath
            }
            settings.isTemporary = true

            var ok = true
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
                } catch (e: Throwable) {
                    log.warn("[CodingTestKit] Rust debug launch failed", e)
                    ok = false
                }
            }
            ok
        } catch (e: Throwable) {
            log.warn("[CodingTestKit] Rust debug launch failed", e)
            false
        }
    }
}
