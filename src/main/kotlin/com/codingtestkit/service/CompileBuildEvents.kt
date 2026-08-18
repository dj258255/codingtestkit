package com.codingtestkit.service

import com.intellij.build.BuildDescriptor
import com.intellij.build.FileNavigatable
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.EventResult
import com.intellij.build.events.Failure
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.FileMessageEventResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEventResult
import com.intellij.build.events.StartBuildEvent
import com.intellij.build.events.SuccessResult
import com.intellij.build.events.Warning
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable

/**
 * Build 창 이벤트의 자체 구현 (이슈 #32).
 *
 * 플랫폼의 com.intellij.build.events.impl.*Impl 클래스들은 2026.x에서
 * @ApiStatus.Internal + 생성자 deprecated 로 표시돼 마켓플레이스 검증 경고를
 * 유발한다. 이벤트 타입 자체(BuildEvent, MessageEvent 등)는 전부 공개
 * 인터페이스이므로 여기서 직접 구현해 내부 API 의존을 없앤다.
 * 단 OutputBuildEvent는 @NonExtendable(구현 금지)이고 2026.x에서 추상 메서드가
 * 추가돼 직접 구현이 깨진다 — 폴백 원문 출력도 MessageEvent로 전달한다.
 */
internal open class CompileBuildEvent(
    private val id: Any,
    private val message: String,
    private val description: String? = null,
    private val parentId: Any? = null,
) : BuildEvent {
    private val eventTime = System.currentTimeMillis()
    override fun getId(): Any = id
    override fun getParentId(): Any? = parentId
    override fun getEventTime(): Long = eventTime
    override fun getMessage(): String = message
    override fun getHint(): String? = null
    override fun getDescription(): String? = description
}

/** 빌드 시작 — Build 창에 새 빌드 노드를 만든다. */
internal class CompileStartEvent(
    private val descriptor: BuildDescriptor,
    message: String,
) : CompileBuildEvent(descriptor.id, message), StartBuildEvent {
    override fun getBuildDescriptor(): BuildDescriptor = descriptor
}

/**
 * 빌드 종료. failed=true면 실패 결과로 마감해 빌드 노드가 빨간 실패 상태가 된다.
 *
 * 경고만 있는 빌드(컴파일 성공)를 실패로 마감하면 Build 창이 빨갛게 뜨고 창이
 * 강제로 열려, 정상 빌드를 실패로 오인하게 된다 — 그래서 성공 마감이 따로 필요하다
 * (이슈 #32).
 */
internal class CompileFinishEvent(
    id: Any,
    message: String,
    private val failed: Boolean = true,
) : CompileBuildEvent(id, message), FinishBuildEvent {
    override fun getResult(): EventResult =
        if (failed) object : FailureResult {
            override fun getFailures(): List<Failure> = emptyList()
        } else object : SuccessResult {
            override fun isUpToDate(): Boolean = false
            override fun getWarnings(): List<Warning> = emptyList()
        }
}

/**
 * 위치 정보 없는 진단 메시지.
 *
 * id/parentId 규약: 시작·종료 이벤트만 buildId를 자기 id로 쓰고, 메시지 이벤트는
 * 반드시 고유 id + parentId=buildId여야 한다. BuildTreeConsoleView가 id로 기존
 * 노드를 조회하는데, buildId를 id로 재사용하면 루트 노드와 충돌해 debug 로그만
 * 남기고 이벤트가 조용히 버려진다 (v1.7.0 회귀 — 이슈 #32 재보고 원인).
 */
internal open class CompileMessageEvent(
    parentId: Any,
    protected val msgKind: MessageEvent.Kind,
    private val group: String,
    message: String,
    protected val msgDetail: String?,
) : CompileBuildEvent(Any(), message, msgDetail, parentId), MessageEvent {
    override fun getKind(): MessageEvent.Kind = msgKind
    override fun getGroup(): String = group
    override fun getNavigatable(project: Project): Navigatable? = null
    override fun getResult(): MessageEventResult = object : MessageEventResult {
        override fun getKind(): MessageEvent.Kind = this@CompileMessageEvent.msgKind
        override fun getDetails(): String? = msgDetail
    }
}

/** 파일 위치가 붙은 진단 — 클릭하면 해당 소스 위치로 이동한다. */
internal class CompileFileMessageEvent(
    parentId: Any,
    kind: MessageEvent.Kind,
    group: String,
    message: String,
    detail: String?,
    private val position: FilePosition,
) : CompileMessageEvent(parentId, kind, group, message, detail), FileMessageEvent {
    override fun getFilePosition(): FilePosition = position
    override fun getNavigatable(project: Project): Navigatable = FileNavigatable(project, position)
    override fun getResult(): FileMessageEventResult = object : FileMessageEventResult {
        override fun getFilePosition(): FilePosition = position
        override fun getKind(): MessageEvent.Kind = this@CompileFileMessageEvent.msgKind
        override fun getDetails(): String? = msgDetail
    }
}
