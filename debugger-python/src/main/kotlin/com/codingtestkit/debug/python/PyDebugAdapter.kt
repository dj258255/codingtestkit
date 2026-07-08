package com.codingtestkit.debug.python

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.python.pro.debugger.remote.PyRemoteDebugConfiguration
import com.intellij.python.pro.debugger.remote.PyRemoteDebugConfigurationType

/**
 * Python(pydevd) 역방향 디버그 어댑터 (이슈 #36 Tier 2).
 *
 * JVM/Go와 반대로 IDE가 서버다: "Python Debug Server" 실행 구성을 만들어 지정 포트로
 * listen시키고, CodeRunner가 pydevd.py를 클라이언트로 실행해 여기에 접속시킨다.
 * (pydevd는 PyCharm에 번들된 헬퍼 — 사용자 설치 불필요, CodeRunner가 경로를 찾는다)
 *
 * Pythonid 플러그인이 있는 IDE(PyCharm, IDEA Ultimate+Python)에서만 로드된다.
 * 2025.x 이전 PyCharm은 클래스 위치가 달라 isAvailable=false로 미지원 처리된다.
 */
class PyDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(PyDebugAdapter::class.java)

    override fun supports(language: Language): Boolean = language == Language.PYTHON

    override fun isReverseConnection(): Boolean = true

    override fun isAvailable(): Boolean = try {
        ConfigurationTypeUtil.findConfigurationType(PyRemoteDebugConfigurationType::class.java) != null
    } catch (_: Throwable) {
        false
    }

    override fun attachToPort(project: Project, sessionName: String, port: Int): Boolean {
        return try {
            val type = ConfigurationTypeUtil.findConfigurationType(PyRemoteDebugConfigurationType::class.java)
            val runManager = RunManager.getInstance(project)
            val settings = runManager.createConfiguration(sessionName, type.configurationFactories[0])
            val cfg = settings.configuration as PyRemoteDebugConfiguration
            cfg.host = "127.0.0.1"
            cfg.setPort(port)
            cfg.isSuspendAfterConnect = false   // 접속 즉시 정지 대신 브레이크포인트에서만 정지
            settings.isTemporary = true

            // Debug 실행자로 실행 → IDE가 해당 포트에서 pydevd 접속을 listen
            ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
            true
        } catch (e: Throwable) {
            log.warn("[CodingTestKit] Python debug server start failed", e)
            false
        }
    }
}
