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
import com.intellij.build.events.OutputBuildEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable

/**
 * Build 창 이벤트의 자체 구현 (이슈 #32).
 *
 * 플랫폼의 com.intellij.build.events.impl.*Impl 클래스들은 2026.x에서
 * @ApiStatus.Internal + 생성자 deprecated 로 표시돼 마켓플레이스 검증 경고를
 * 유발한다. 이벤트 타입 자체(BuildEvent, MessageEvent 등)는 전부 공개
 * 인터페이스이므로 여기서 직접 구현해 내부 API 의존을 없앤다.
 */
internal open class CompileBuildEvent(
    private val id: Any,
    private val message: String,
    private val description: String? = null,
) : BuildEvent {
    private val eventTime = System.currentTimeMillis()
    override fun getId(): Any = id
    override fun getParentId(): Any? = null
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

/** 빌드 종료 — 실패 결과로 마감해 빌드 노드가 빨간 실패 상태로 표시된다. */
internal class CompileFinishEvent(
    id: Any,
    message: String,
) : CompileBuildEvent(id, message), FinishBuildEvent {
    override fun getResult(): EventResult = object : FailureResult {
        override fun getFailures(): List<Failure> = emptyList()
    }
}

/** 파싱 실패 시 컴파일러 출력 원문을 콘솔 영역에 그대로 흘린다. */
internal class CompileOutputEvent(
    id: Any,
    message: String,
    private val stdOut: Boolean,
) : CompileBuildEvent(id, message), OutputBuildEvent {
    override fun isStdOut(): Boolean = stdOut
}

/** 위치 정보 없는 진단 메시지. */
internal open class CompileMessageEvent(
    id: Any,
    protected val msgKind: MessageEvent.Kind,
    private val group: String,
    message: String,
    protected val msgDetail: String?,
) : CompileBuildEvent(id, message, msgDetail), MessageEvent {
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
    id: Any,
    kind: MessageEvent.Kind,
    group: String,
    message: String,
    detail: String?,
    private val position: FilePosition,
) : CompileMessageEvent(id, kind, group, message, detail), FileMessageEvent {
    override fun getFilePosition(): FilePosition = position
    override fun getNavigatable(project: Project): Navigatable = FileNavigatable(project, position)
    override fun getResult(): FileMessageEventResult = object : FileMessageEventResult {
        override fun getFilePosition(): FilePosition = position
        override fun getKind(): MessageEvent.Kind = this@CompileFileMessageEvent.msgKind
        override fun getDetails(): String? = msgDetail
    }
}
