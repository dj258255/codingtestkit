package com.codingtestkit.debug

import com.codingtestkit.model.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * 언어별 IDE 디버거 attach 어댑터 (이슈 #36).
 *
 * 각 구현은 해당 언어의 디버거를 가진 IDE 모듈이 있을 때만 로드된다:
 * - JVM(Java/Kotlin) → com.intellij.modules.java (IntelliJ IDEA)
 * - Go → GoLand의 Go 플러그인, Python → PyCharm … (후속 모듈)
 *
 * projectService가 아니라 확장점(EP)인 이유: IDEA Ultimate처럼 Java+Go+Python
 * 플러그인이 공존하는 IDE에서는 어댑터 여러 개가 동시에 로드되는데, 서비스는
 * 구현이 하나뿐이라 충돌한다. EP는 목록으로 받아 언어에 맞는 것을 고른다.
 *
 * 이 인터페이스는 :core 모듈 소속 — 플랫폼 공통 API만 사용하고, 특정 IDE의
 * 클래스를 절대 import하지 않는다. 그래야 모든 IDE 모듈이 참조할 수 있다.
 */
interface TestDebugAdapter {

    /** 이 어댑터가 해당 언어의 디버깅을 담당하는지 */
    fun supports(language: Language): Boolean

    /** 이 IDE/환경에서 실제로 attach 가능한지 (필요한 플러그인 클래스 존재 여부 등) */
    fun isAvailable(): Boolean = true

    /**
     * 역방향 연결 여부. JVM/Go는 프로세스가 서버(디버그 포트 listen)이고 IDE가 붙지만,
     * Python(pydevd)은 반대로 IDE가 서버로 listen하고 스크립트가 접속한다.
     * true면 호출부는 attachToPort(IDE listen 시작)를 먼저 부른 뒤 프로세스를 실행해야 한다.
     */
    fun isReverseConnection(): Boolean = false

    /**
     * PID attach 방식 여부 (Rust/C++ 네이티브). 디버그 서버 없이 프로세스를 먼저 실행하고
     * 실행 중인 PID에 IDE 디버거(LLDB 등)를 붙인다. 프로그램이 첫 stdin 읽기에서 블록된
     * 동안 attach하고, 완료 후 입력을 흘려보내는 방식으로 정지 시점을 확보한다.
     */
    fun attachesToPid(): Boolean = false

    /** PID attach 방식 어댑터 전용. 백그라운드 스레드에서 호출해도 된다. */
    fun attachToPid(project: Project, sessionName: String, pid: Long): Boolean = false

    /**
     * 디버그 서버(JDWP/dlv 등)가 127.0.0.1의 지정 포트에서 대기 중인 프로세스에
     * IDE 디버거를 attach한다. 프로세스 시작은 호출부(CodeRunner) 책임이고,
     * 디버그 세션의 수명·정리는 IDE가 담당한다.
     *
     * @param sessionName 디버그 세션 표시 이름 (예: "CodingTestKit #1")
     * @return attach 시도 성공 여부
     */
    fun attachToPort(project: Project, sessionName: String, port: Int): Boolean

    companion object {
        val EP_NAME: ExtensionPointName<TestDebugAdapter> =
            ExtensionPointName.create("com.codingtestkit.testDebugAdapter")

        /** 현재 IDE에 로드된 어댑터 중 이 언어를 지원하는 첫 번째 것 */
        fun forLanguage(language: Language): TestDebugAdapter? =
            EP_NAME.extensionList.firstOrNull { it.supports(language) && it.isAvailable() }
    }
}
