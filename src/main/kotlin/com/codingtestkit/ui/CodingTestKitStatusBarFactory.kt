package com.codingtestkit.ui

import com.codingtestkit.service.CodingTestKitActionService
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

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        CodingTestKitActionService.getInstance(project).onStatusChanged = {
            updateText()
            statusBar.updateWidget(ID())
        }
        updateText()
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getComponent(): JComponent = component

    private fun updateText() {
        val service = CodingTestKitActionService.getInstance(project)
        val platform = service.currentPlatform
        val id = service.currentProblemId

        component.text = when {
            platform == null -> "CTK"
            id == null -> "CTK: $platform"
            else -> "CTK: $platform #$id"
        }
        component.toolTipText = when {
            platform == null -> "CodingTestKit"
            id == null -> "CodingTestKit: $platform"
            else -> "CodingTestKit: $platform #$id"
        }
    }
}
