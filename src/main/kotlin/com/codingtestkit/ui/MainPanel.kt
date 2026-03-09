package com.codingtestkit.ui

import com.codingtestkit.service.I18n
import com.codingtestkit.service.ProblemFileManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

class MainPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val problemPanel = ProblemPanel(project)
    private val testPanel = TestPanel(project)
    private val templatePanel = TemplatePanel(project).also { Disposer.register(this, it) }
    private val timerPanel = TimerPanel()
    private val referencePanel = ReferencePanel()
    private val settingsPanel = SettingsPanel(project)
    private var lastLoadedFolder: String? = null

    init {
        border = JBUI.Borders.empty()

        val tabbedPane = JBTabbedPane()
        tabbedPane.addTab(I18n.t("문제", "Problems"), AllIcons.Actions.Download, problemPanel)
        tabbedPane.addTab(I18n.t("테스트", "Tests"), AllIcons.Actions.Execute, testPanel)
        tabbedPane.addTab(I18n.t("템플릿", "Templates"), AllIcons.Actions.Copy, templatePanel)
        tabbedPane.addTab(I18n.t("타이머", "Timer"), AllIcons.Vcs.History, timerPanel)
        tabbedPane.addTab(I18n.t("레퍼런스", "Reference"), AllIcons.Actions.Preview, referencePanel)
        tabbedPane.addTab(I18n.t("설정", "Settings"), AllIcons.General.Settings, settingsPanel)

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
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        val file = event.newFile ?: return
                        detectAndLoadProblem(file, basePath)
                    }
                }
            )

            // IDE 시작 시 이전 세션 소스 루트 정리
            ApplicationManager.getApplication().invokeLater {
                ProblemFileManager.clearSourceRoots(project)
                val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
                if (currentFile != null) {
                    detectAndLoadProblem(currentFile, basePath)
                }
            }
        }
    }

    private var sourceRootsCleared = false

    private fun detectAndLoadProblem(file: VirtualFile, basePath: String) {
        val filePath = file.path
        if (!filePath.contains("/problems/")) {
            if (!sourceRootsCleared) {
                sourceRootsCleared = true
                lastLoadedFolder = null
                ApplicationManager.getApplication().invokeLater {
                    ProblemFileManager.clearSourceRoots(project)
                }
            }
            return
        }
        sourceRootsCleared = false

        val folder = ProblemFileManager.findProblemFolder(filePath, basePath) ?: return
        val folderPath = folder.absolutePath
        if (folderPath == lastLoadedFolder) return
        lastLoadedFolder = folderPath

        val problem = ProblemFileManager.loadProblemFromFolder(folder) ?: return
        problemPanel.loadExistingProblem(problem, folder)
        testPanel.setProblemSource(problem.source)
        testPanel.setParameterNames(problem.parameterNames)
        testPanel.setTestCases(problem.testCases)

        // 현재 문제 폴더를 소스 루트로 전환 (자동완성 활성화)
        ApplicationManager.getApplication().invokeLater {
            val vfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            val folderVf = vfs.findFileByIoFile(folder)
            if (folderVf != null) {
                ProblemFileManager.markAsSourceRoot(project, folderVf)
            }
        }
    }

    override fun dispose() {}
}
