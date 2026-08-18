package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.build.events.SuccessResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Build 창 발행 경로 검증 (이슈 #32).
 *
 * 기존 CompileBuildEventsTest는 이벤트 '객체'를 직접 만들어 id를 봤는데,
 * v1.7.0 회귀는 객체가 아니라 '호출부가 buildId를 id로 넘긴 것'이었다.
 * 그래서 여기서는 실제 발행 함수가 만든 이벤트 열을 그대로 검증한다 —
 * 회귀를 되돌리면 이 파일이 깨진다.
 */
class BuildOutputRoutingTest {

    private val buildId = Any()

    private fun events(
        language: Language = Language.JAVA,
        raw: String,
        userFile: File? = null,
        wrapperStyle: Boolean = false,
        failed: Boolean = true,
    ) = BuildOutputPublisher.buildEvents(buildId, "", language, raw, userFile, wrapperStyle, failed)

    @Test
    fun `message events never reuse buildId as their own id`() {
        val raw = """
            Main.java:9: error: cannot find symbol
            Main.java:12: warning: [deprecation] foo() has been deprecated
        """.trimIndent()
        val ev = events(raw = raw)

        val start = ev.first()
        val finish = ev.last()
        assertTrue(start is StartBuildEvent)
        assertTrue(finish is FinishBuildEvent)
        assertSame(buildId, start.id)
        assertSame(buildId, finish.id)
        assertNull(start.parentId)

        val messages = ev.filterIsInstance<MessageEvent>()
        assertEquals(2, messages.size)
        for (m in messages) {
            // 이 두 줄이 v1.7.0 회귀를 잡는다: id가 buildId면 BuildTreeConsoleView가
            // 루트 노드와 충돌로 보고 조용히 버린다.
            assertNotSame(buildId, m.id, "메시지 이벤트는 고유 id를 가져야 한다")
            assertSame(buildId, m.parentId, "메시지 이벤트의 parentId는 buildId여야 한다")
        }
        assertEquals(setOf(false), setOf(messages[0].id === messages[1].id))
    }

    @Test
    fun `warning-only build finishes successfully and does not activate the tool window`() {
        val raw = "Main.java:12: warning: [deprecation] foo() has been deprecated"
        val ev = events(raw = raw, failed = false)

        val start = ev.first() as StartBuildEvent
        assertFalse((start.buildDescriptor as DefaultBuildDescriptor).isActivateToolWindowWhenFailed)

        val finish = ev.last() as FinishBuildEvent
        assertTrue(finish.result is SuccessResult, "경고만 있는 빌드를 실패로 마감하면 안 된다")

        val kinds = ev.filterIsInstance<MessageEvent>().map { it.kind }
        assertEquals(listOf(MessageEvent.Kind.WARNING), kinds)
    }

    @Test
    fun `compile failure finishes as failure and activates the tool window`() {
        val ev = events(raw = "Main.java:9: error: cannot find symbol", failed = true)
        val start = ev.first() as StartBuildEvent
        assertTrue((start.buildDescriptor as DefaultBuildDescriptor).isActivateToolWindowWhenFailed)
        assertFalse((ev.last() as FinishBuildEvent).result is SuccessResult)
    }

    @Test
    fun `stdin-style diagnostics navigate to the user file`(@TempDir dir: Path) {
        val user = dir.resolve("Main.java").toFile()
        user.writeText((1..30).joinToString("\n") { "// line $it" })

        val ev = events(raw = "Main.java:9: error: cannot find symbol", userFile = user)
        val fileMsg = ev.filterIsInstance<FileMessageEvent>()
        assertEquals(1, fileMsg.size)
        assertEquals(8, fileMsg[0].filePosition.startLine) // 0-based
    }

    @Test
    fun `diagnostics past the end of the user file get no navigation`(@TempDir dir: Path) {
        val user = dir.resolve("Main.java").toFile()
        user.writeText((1..10).joinToString("\n") { "// line $it" })

        // 하네스가 만든 줄(사용자 파일 10줄을 넘김) → 이동을 붙이면 엉뚱한 곳으로 점프
        val ev = events(raw = "Main.java:57: error: cannot find symbol: solution", userFile = user)
        assertTrue(ev.filterIsInstance<FileMessageEvent>().isEmpty(),
            "사용자 파일 범위를 넘는 진단에 이동을 붙이면 안 된다")
        assertEquals(1, ev.filterIsInstance<MessageEvent>().size)
    }

    @Test
    fun `wrapper-style go diagnostics are not navigable`(@TempDir dir: Path) {
        val user = dir.resolve("solution.go").toFile()
        user.writeText((1..50).joinToString("\n") { "// line $it" })

        // Go 래퍼는 package 프리펜드로 줄이 밀린다 → 이동 금지
        val ev = events(Language.GO, "./solution.go:12:2: undefined: foo", user, wrapperStyle = true)
        assertTrue(ev.filterIsInstance<FileMessageEvent>().isEmpty())
    }

    @Test
    fun `unparsed output still reaches the build window`() {
        val ev = events(raw = "some totally unparseable compiler output")
        val messages = ev.filterIsInstance<MessageEvent>()
        assertEquals(1, messages.size)
        assertNotSame(buildId, messages[0].id)
        assertSame(buildId, messages[0].parentId)
    }
}
