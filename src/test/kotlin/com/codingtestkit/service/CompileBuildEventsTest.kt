package com.codingtestkit.service

import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.events.MessageEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Build 창 이벤트 id/parentId 규약 검증 (이슈 #32 회귀 방지).
 *
 * BuildTreeConsoleView는 이벤트 id로 기존 노드를 조회하므로, 메시지 이벤트가
 * buildId를 자기 id로 재사용하면 루트 노드와 충돌해 조용히 버려진다.
 * 시작·종료 이벤트만 id=buildId, 메시지는 고유 id + parentId=buildId여야 한다.
 */
class CompileBuildEventsTest {

    private val buildId = Any()

    @Test
    fun `start and finish events reuse buildId as their own id`() {
        val descriptor = DefaultBuildDescriptor(buildId, "title", "", 0L)
        val start = CompileStartEvent(descriptor, "compiling")
        val finish = CompileFinishEvent(buildId, "failed")
        assertSame(buildId, start.id)
        assertSame(buildId, finish.id)
    }

    @Test
    fun `message events get unique ids with buildId as parent`() {
        val a = CompileMessageEvent(buildId, MessageEvent.Kind.ERROR, "Compiler", "m1", null)
        val b = CompileMessageEvent(buildId, MessageEvent.Kind.WARNING, "Compiler", "m2", "detail")
        assertNotSame(buildId, a.id)
        assertNotSame(buildId, b.id)
        assertNotSame(a.id, b.id)
        assertSame(buildId, a.parentId)
        assertSame(buildId, b.parentId)
    }

    @Test
    fun `file message event follows same contract and keeps position`() {
        val pos = FilePosition(File("Main.rs"), 5, 2)
        val e = CompileFileMessageEvent(buildId, MessageEvent.Kind.ERROR, "Compiler", "m", null, pos)
        assertNotSame(buildId, e.id)
        assertSame(buildId, e.parentId)
        assertSame(pos, e.filePosition)
    }
}
