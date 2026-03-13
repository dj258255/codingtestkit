package com.codingtestkit.ui

import com.codingtestkit.service.CodingTestKitActionService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

class CodingTestKitStatusBarFactory : StatusBarWidgetFactory {
    override fun getId() = WIDGET_ID
    override fun getDisplayName() = "CodingTestKit"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project) = CodingTestKitStatusBarWidget(project)

    companion object {
        const val WIDGET_ID = "CodingTestKit.StatusBar"
    }
}

class CodingTestKitStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null

    override fun ID() = CodingTestKitStatusBarFactory.WIDGET_ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        CodingTestKitActionService.getInstance(project).onStatusChanged = {
            statusBar.updateWidget(ID())
        }
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        val service = CodingTestKitActionService.getInstance(project)
        val platform = service.currentPlatform ?: return "CTK"
        val id = service.currentProblemId ?: return "CTK: $platform"
        return "CTK: $platform #$id"
    }

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getTooltipText(): String {
        val service = CodingTestKitActionService.getInstance(project)
        val platform = service.currentPlatform ?: return "CodingTestKit"
        val id = service.currentProblemId ?: return "CodingTestKit: $platform"
        return "CodingTestKit: $platform #$id"
    }

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        ToolWindowManager.getInstance(project).getToolWindow("CodingTestKit")?.show()
    }
}
