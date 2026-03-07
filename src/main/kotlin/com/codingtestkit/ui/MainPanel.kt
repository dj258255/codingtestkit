package com.codingtestkit.ui

import com.codingtestkit.service.ProblemFileManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

class MainPanel(project: Project) : JPanel(BorderLayout()) {

    private val problemPanel = ProblemPanel(project)
    private val testPanel = TestPanel(project)
    private val templatePanel = TemplatePanel(project)
    private val timerPanel = TimerPanel()
    private val settingsPanel = SettingsPanel(project)
    private var lastLoadedFolder: String? = null

    init {
        border = JBUI.Borders.empty()

        val tabbedPane = JBTabbedPane()
        tabbedPane.addTab("문제", AllIcons.Actions.Download, problemPanel)
        tabbedPane.addTab("테스트", AllIcons.Actions.Execute, testPanel)
        tabbedPane.addTab("템플릿", AllIcons.Actions.Copy, templatePanel)
        tabbedPane.addTab("타이머", AllIcons.Vcs.History, timerPanel)
        tabbedPane.addTab("설정", AllIcons.General.Settings, settingsPanel)

        add(tabbedPane, BorderLayout.CENTER)

        // 문제를 가져오면 테스트 패널에 테스트 케이스 전달
        problemPanel.onProblemFetched = { problem ->
            testPanel.setProblemSource(problem.source)
            testPanel.setParameterNames(problem.parameterNames)
            testPanel.setTestCases(problem.testCases)
        }

        // 파일 열면 자동으로 문제 인식
        val basePath = project.basePath
        if (basePath != null) {
            project.messageBus.connect().subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                        detectAndLoadProblem(file, basePath)
                    }
                }
            )

            // 초기 로드: 현재 열려있는 파일 확인
            val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            if (currentFile != null) {
                detectAndLoadProblem(currentFile, basePath)
            }
        }
    }

    private fun detectAndLoadProblem(file: VirtualFile, basePath: String) {
        val filePath = file.path
        if (!filePath.contains("/problems/")) return

        val folder = ProblemFileManager.findProblemFolder(filePath, basePath) ?: return
        val folderPath = folder.absolutePath
        if (folderPath == lastLoadedFolder) return
        lastLoadedFolder = folderPath

        val problem = ProblemFileManager.loadProblemFromFolder(folder) ?: return
        problemPanel.loadExistingProblem(problem, folder)
        testPanel.setProblemSource(problem.source)
        testPanel.setParameterNames(problem.parameterNames)
        testPanel.setTestCases(problem.testCases)
    }
}
