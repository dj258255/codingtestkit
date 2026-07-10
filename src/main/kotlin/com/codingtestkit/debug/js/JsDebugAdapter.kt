package com.codingtestkit.debug.js

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.execution.Location
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.PsiLocation
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import java.io.File

/**
 * JavaScript(Node.js) 디버그 어댑터 (이슈 #36) — IDE가 실행까지 소유하는 방식.
 *
 * gutter와 동일한 producer 경로로 Node.js 실행 구성을 생성하고(인터프리터 등
 * 프로젝트 기본값이 자동 세팅됨), 케이스 입력은 Node 구성에 내장된
 * 입력 리다이렉션(setInputPath)으로 전달한 뒤 debug 실행자로 돌린다.
 *
 * NodeJS 플러그인 클래스는 참조하지 않는다(설정은 리플렉션) — 이 어댑터는
 * 메인 모듈 소속이고, 로드만 optional 의존성(NodeJS)이 제어한다.
 */
class JsDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(JsDebugAdapter::class.java)

    private companion object {
        const val NODE_TYPE_ID = "NodeJSConfigurationType"
    }

    override fun supports(language: Language): Boolean = language == Language.JAVASCRIPT

    override fun ownsLaunch(): Boolean = true

    override fun isAvailable(): Boolean = try {
        ConfigurationTypeUtil.findConfigurationType(NODE_TYPE_ID) != null
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
                    log.warn("[CodingTestKit] JS debug: PSI file not found for ${sourceFile.path}")
                    return@invokeAndWait
                }
                val location: Location<*> = PsiLocation.fromPsiElement(psiFile)
                val dataContext = SimpleDataContext.builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(Location.DATA_KEY, location)
                    .build()
                val context = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN)
                val fromContext = context.configurationsFromContext
                    ?.firstOrNull { it.configurationSettings.type.id == NODE_TYPE_ID }
                if (fromContext == null) {
                    log.warn("[CodingTestKit] JS debug: no Node.js run configuration producer matched the file")
                    return@invokeAndWait
                }
                val settings = fromContext.configurationSettings
                settings.name = sessionName
                if (input.isNotBlank()) {
                    val inputFile = File(workingDir, "ctk_stdin.txt").apply { writeText(input, Charsets.UTF_8) }
                    val cfg = settings.configuration
                    cfg.javaClass.getMethod("setInputPath", String::class.java)
                        .invoke(cfg, inputFile.absolutePath)
                }
                settings.isTemporary = true
                RunManager.getInstance(project).setTemporaryConfiguration(settings)
                fromContext.onFirstRun(context) {
                    ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
                }
                ok = true
            } catch (e: Throwable) {
                log.warn("[CodingTestKit] JS debug launch failed", e)
            }
        }
        return ok
    }
}
