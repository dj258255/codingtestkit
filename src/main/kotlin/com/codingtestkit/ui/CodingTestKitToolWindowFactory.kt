package com.codingtestkit.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class CodingTestKitToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = MainPanel(project)
        val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
        content.setDisposer(mainPanel)
        toolWindow.contentManager.addContent(content)
    }
}
