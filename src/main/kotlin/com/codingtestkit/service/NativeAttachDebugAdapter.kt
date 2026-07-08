package com.codingtestkit.service

import com.codingtestkit.debug.TestDebugAdapter
import com.codingtestkit.model.Language
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase

/**
 * Rust/C++ 네이티브 디버그 어댑터 (이슈 #36 Tier 3) — PID attach 방식.
 *
 * RustRover/CLion이 플랫폼 확장점(XAttachDebuggerProvider)에 등록해둔 LLDB/GDB
 * 디버거를 프로그램적으로 호출한다 ("Attach to Process" 메뉴와 같은 경로).
 *
 * 리플렉션을 쓰는 이유: attach API(LocalAttachHost 등)는 런타임에는 항상 로드되지만
 * app-client.jar 소속이라 Gradle 플러그인이 컴파일 클래스패스에서 제외한다.
 * 2017년부터 안정적인 API이고 호출 표면이 4개뿐이라 리플렉션 위험이 작다.
 *
 * 정지 시점: 네이티브는 JDWP suspend 같은 시작 전 정지가 없으므로, 프로그램이
 * 첫 stdin 읽기에서 블록된 동안 attach하고 입력은 그 후에 전달한다 (CodeRunner).
 */
class NativeAttachDebugAdapter : TestDebugAdapter {

    private val log = Logger.getInstance(NativeAttachDebugAdapter::class.java)

    override fun supports(language: Language): Boolean =
        language == Language.RUST || language == Language.CPP

    override fun attachesToPid(): Boolean = true

    override fun isAvailable(): Boolean =
        // RustRover/CLion의 네이티브 디버거 플러그인 존재 여부
        PluginManagerCore.getPlugin(PluginId.getId("com.intellij.nativeDebug")) != null ||
        PluginManagerCore.getPlugin(PluginId.getId("com.intellij.clion")) != null ||
        PluginManagerCore.getPlugin(PluginId.getId("com.intellij.cidr.debugger")) != null

    override fun attachToPort(project: Project, sessionName: String, port: Int): Boolean = false

    override fun attachToPid(project: Project, sessionName: String, pid: Long): Boolean {
        return try {
            val hostClass = Class.forName("com.intellij.xdebugger.attach.LocalAttachHost")
            val xHostClass = Class.forName("com.intellij.xdebugger.attach.XAttachHost")
            val host = hostClass.getField("INSTANCE").get(null)

            // 실행 중인 프로세스 목록에서 우리 PID 찾기
            val processList = hostClass.getMethod("getProcessList").invoke(host) as List<*>
            val info = processList.firstOrNull { p ->
                (p?.javaClass?.getMethod("getPid")?.invoke(p) as? Int)?.toLong() == pid
            }
            if (info == null) {
                log.warn("[CodingTestKit] native attach: pid $pid not found in process list")
                return false
            }
            val processInfoClass = Class.forName("com.intellij.execution.process.ProcessInfo")

            // 이 호스트(로컬)에 적용 가능한 provider들에서 이 프로세스용 디버거 수집
            val providerClass = Class.forName("com.intellij.xdebugger.attach.XAttachDebuggerProvider")
            @Suppress("UNCHECKED_CAST")
            val ep = providerClass.getField("EP").get(null) as ExtensionPointName<Any>
            val applicable = providerClass.getMethod("isAttachHostApplicable", xHostClass)
            val getDebuggers = providerClass.getMethod(
                "getAvailableDebuggers",
                Project::class.java, xHostClass, processInfoClass,
                Class.forName("com.intellij.openapi.util.UserDataHolder")
            )
            val holder = UserDataHolderBase()
            val debuggers = ep.extensionList
                .filter { applicable.invoke(it, host) as? Boolean == true }
                .flatMap { getDebuggers.invoke(it, project, host, info, holder) as List<*> }
                .filterNotNull()

            val debugger = debuggers.firstOrNull()
            if (debugger == null) {
                log.warn("[CodingTestKit] native attach: no debugger available for pid $pid")
                return false
            }
            val attach = Class.forName("com.intellij.xdebugger.attach.XAttachDebugger")
                .getMethod("attachDebugSession", Project::class.java, xHostClass, processInfoClass)

            // 디버그 세션 시작은 EDT에서
            var ok = true
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    attach.invoke(debugger, project, host, info)
                } catch (e: Throwable) {
                    log.warn("[CodingTestKit] native attach failed", e)
                    ok = false
                }
            }
            ok
        } catch (e: Throwable) {
            log.warn("[CodingTestKit] native attach failed", e)
            false
        }
    }
}
