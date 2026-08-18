package com.codingtestkit.debug.cpp

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.execution.InputRedirectAware
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
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
                // CLion의 CppFileCreateOrSelectConfigurationHelper가 하는 것 그대로:
                // producer(CppFileTargetRunConfigurationProducer)는 ALLOW_CPP_FILE_CONFIGURATION
                // 게이트 키가 true인 DataContext에서만 매칭된다 (DataKey는 이름으로 전역 캐시라
                // 같은 문자열 ID로 만들면 동일 키). 바이트코드 분석으로 확인한 게이트.
                val allowKey = com.intellij.openapi.actionSystem.DataKey.create<Boolean>(
                    "CppFileTargetRunConfigurationProducer.ALLOW_CPP_FILE_CONFIGURATION")
                val location: Location<*> = PsiLocation.fromPsiElement(psiFile)
                val dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                    .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                    .add(Location.DATA_KEY, location)
                    .add(allowKey, true)
                    .build()
                val matchedContext = ConfigurationContext.getFromContext(
                    dataContext, com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN)
                val fromContext = matchedContext.configurationsFromContext?.firstOrNull()
                if (fromContext == null) {
                    log.warn("[CodingTestKit] C++ debug: no run configuration producer matched the file")
                    return@invokeAndWait
                }
                val settings = fromContext.configurationSettings
                settings.name = sessionName

                if (input.isNotBlank()) {
                    // 사용자 문제 폴더에 남기지 않는다 — 임시 파일 + 종료 시 삭제 (이슈 #36)
                    val inputFile = File.createTempFile("ctk_stdin", ".txt")
                        .apply { deleteOnExit(); writeText(input, Charsets.UTF_8) }
                    (settings.configuration as? InputRedirectAware.InputRedirectOptions)?.let {
                        it.isRedirectInput = true
                        it.redirectInputPath = inputFile.absolutePath
                    }
                }
                settings.isTemporary = true
                // gutter와 동일: 임시 구성으로 등록 후 onFirstRun 콜백 안에서 실행 —
                // producer가 onFirstRun에서 빌드 타깃 등록 등 비동기 마무리를 한다
                RunManager.getInstance(project).setTemporaryConfiguration(settings)
                fromContext.onFirstRun(matchedContext) {
                    ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
                }
                ok = true
            } catch (e: Throwable) {
                log.warn("[CodingTestKit] C++ debug launch failed", e)
            }
        }
        return ok
    }
}
