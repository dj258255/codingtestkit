package com.codingtestkit.service

import com.intellij.openapi.project.Project

/**
 * 테스트 케이스 하나를 IDE 디버거로 실행하는 서비스 (이슈 #36).
 *
 * 구현은 IDE에 Java 플러그인 모듈이 있을 때만 로드된다 (JVM 디버거가 거기 있음).
 * 비-Java IDE(RustRover/GoLand 등)에서는 이 서비스가 등록되지 않아 null이 되고,
 * 호출부는 "이 IDE에서는 미지원" 안내를 표시한다.
 *
 * 메인 모듈이므로 Java 플러그인 클래스를 절대 import하지 않는다 — 그래야 어떤 IDE에서도
 * 이 인터페이스 자체는 안전하게 로드된다.
 */
interface TestDebugService {

    /** 이 IDE/환경에서 디버깅이 가능한지 */
    fun isAvailable(): Boolean

    /**
     * 지정 포트에 뜬 JVM(JDWP, suspend=y)에 IDE 디버거를 attach한다.
     * 프로세스는 호출부가 이미 시작한 상태여야 하며, 세션 종료·정리는 IDE가 담당.
     *
     * @param sessionName 디버그 세션 표시 이름 (예: "CodingTestKit #1")
     * @param port 자식 JVM이 대기 중인 JDWP 소켓 포트
     * @return attach 시도 성공 여부
     */
    fun attachJvm(project: Project, sessionName: String, port: Int): Boolean

    companion object {
        fun getInstance(project: Project): TestDebugService? =
            project.getService(TestDebugService::class.java)
    }
}
