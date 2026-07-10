package com.codingtestkit.debug.cpp

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.execution.InputRedirectAware
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import java.io.File

/**
 * C++ 디버그 어댑터 (이슈 #36 Tier 3) — CLion gutter의 Debug와 동일한 경로.
 *
 * 소스 파일의 PSI 위치로 ConfigurationContext를 만들어 CLion의 실행 구성
 * producer("C/C++ File")가 구성을 생성하게 한다. producer가 빌드 타깃 등록
 * (CppFileBuildTargetsService)까지 처리하므로, 콘텐츠 모듈의 내부 클래스를
 * 직접 만질 필요가 없다 — 전부 플랫폼 공통 API라 클래스로더 격리 문제도 없다.
 *
 * (직접 구성 생성 시도는 타깃 미등록으로 NoSuchElementException — gutter가
 * 쓰는 producer 경로가 유일하게 완전한 배선이다)
 */
class CppDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(CppDebugAdapter::class.java)

    override fun supports(language: Language): Boolean = language == Language.CPP

    override fun ownsLaunch(): Boolean = true

    override fun isAvailable(): Boolean = try {
        // "C/C++ File" 구성 타입이 레지스트리에 있는지 (CLion에서만 등록됨)
        ConfigurationTypeUtil.findConfigurationType("CppFileRunConfiguration") != null
    } catch (_: Throwable) {
        false
    }

    override fun launchDebug(
        project: Project, sessionName: String, sourceFile: File, input: String, workingDir: File, artifact: File?
    ): Boolean {
        var ok = false
        ApplicationManager.getApplication().invokeAndWait {
            try {
                val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceFile)
                val psiFile = vFile?.let { PsiManager.getInstance(project).findFile(it) }
                if (psiFile == null) {
                    log.warn("[CodingTestKit] C++ debug: PSI file not found for ${sourceFile.path}")
                    return@invokeAndWait
                }
                // gutter와 동일: producer는 main 함수 요소 위치를 요구한다 (파일 전체는 매칭 안 됨)
                val mainOffset = psiFile.text.indexOf("int main").takeIf { it >= 0 }
                    ?: psiFile.text.indexOf("main").takeIf { it >= 0 } ?: 0
                val element = psiFile.findElementAt(mainOffset) ?: psiFile
                val location: Location<*> = PsiLocation.fromPsiElement(element)
                val context = ConfigurationContext.createEmptyContextForLocation(location)
                val fromContext = context.configurationsFromContext?.firstOrNull()
                if (fromContext == null) {
                    log.warn("[CodingTestKit] C++ debug: no run configuration producer matched the file")
                    return@invokeAndWait
                }
                val settings = fromContext.configurationSettings
                settings.name = sessionName

                if (input.isNotBlank()) {
                    val inputFile = File(workingDir, "ctk_stdin.txt").apply { writeText(input, Charsets.UTF_8) }
                    (settings.configuration as? InputRedirectAware.InputRedirectOptions)?.let {
                        it.isRedirectInput = true
                        it.redirectInputPath = inputFile.absolutePath
                    }
                }
                settings.isTemporary = true

                ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
                ok = true
            } catch (e: Throwable) {
                log.warn("[CodingTestKit] C++ debug launch failed", e)
            }
        }
        return ok
    }
}
