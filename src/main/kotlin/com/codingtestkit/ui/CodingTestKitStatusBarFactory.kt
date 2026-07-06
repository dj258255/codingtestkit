package com.codingtestkit.ui

import com.codingtestkit.service.CodingTestKitActionService
import com.codingtestkit.service.TimerService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.status.TextPanel
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

class CodingTestKitStatusBarFactory : StatusBarWidgetFactory {
    override fun getId() = WIDGET_ID
    override fun getDisplayName() = "CodingTestKit"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project) = CodingTestKitStatusBarWidget(project)

    companion object {
        const val WIDGET_ID = "CodingTestKit.StatusBar"
    }
}

class CodingTestKitStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    private var statusBar: StatusBar? = null

    private val component = TextPanel.WithIconAndArrows().apply {
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                ToolWindowManager.getInstance(project).getToolWindow("CodingTestKit")?.show()
            }
        })
    }

    override fun ID() = CodingTestKitStatusBarFactory.WIDGET_ID

    // 타이머는 33ms 간격으로 틱하므로 표시 문자열이 바뀔 때만 위젯 갱신
    private var lastText = ""

    private val timerListener: () -> Unit = { updateText() }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        CodingTestKitActionService.getInstance(project).onStatusChanged = {
            updateText()
        }
        // 타이머 남은 시간을 어떤 탭/레이아웃에서든 볼 수 있게 상태바에 표시 (이슈 #16)
        TimerService.getInstance(project).addListener(timerListener)
        updateText()
    }

    override fun dispose() {
        TimerService.getInstance(project).removeListener(timerListener)
        statusBar = null
    }

    override fun getComponent(): JComponent = component

    private fun updateText() {
        val service = CodingTestKitActionService.getInstance(project)
        val platform = service.currentPlatform
        val id = service.currentProblemId

        val base = when {
            platform == null -> "CTK"
            id == null -> "CTK: $platform"
            else -> "CTK: $platform #$id"
        }

        // 실행 중이거나 중간에 멈춘/종료된 타이머만 표시 (초기 상태에서는 숨김)
        val timer = TimerService.getInstance(project)
        val timerText = if (timer.isRunning || (timer.totalMs > 0 && timer.remainingMs < timer.totalMs)) {
            " ⏱ ${timer.formatRemaining()}"
        } else ""

        val text = base + timerText
        if (text == lastText) return
        lastText = text

        component.text = text
        component.toolTipText = when {
            platform == null -> "CodingTestKit"
            id == null -> "CodingTestKit: $platform"
            else -> "CodingTestKit: $platform #$id"
        }
        statusBar?.updateWidget(ID())
    }
}
