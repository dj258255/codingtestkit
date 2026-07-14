package com.codingtestkit.ui

import com.codingtestkit.service.CodingTestKitActionService
import com.codingtestkit.service.I18n
import com.codingtestkit.service.ProblemFileManager
import com.codingtestkit.service.TimerService
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
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.SwingUtilities
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

class MainPanel(
    private val project: Project,
    private val initialTabIndex: Int = 0
) : JPanel(BorderLayout()), Disposable {

    /**
     * 다른 툴윈도우(예: 프로젝트 뷰)와 같은 쪽에 세로 분할될 때,
     * IDE 분할선은 콘텐츠의 minimum size 아래로 내려가지 않는다.
     * 탭 콘텐츠에서 계산된 최소 높이(좁은 도크에서는 WrapLayout 줄바꿈으로 더 커짐)를
     * 그대로 보고하면 창을 줄일 수 없으므로, 작은 고정값을 보고해
     * 분할 비율 조절 권한을 사용자에게 둔다. (이슈 #12)
     */
    override fun getMinimumSize(): Dimension = Dimension(JBUI.scale(120), JBUI.scale(80))

    private val problemPanel = ProblemPanel(project)
    private val testPanel = TestPanel(project)
    private val templatePanel = TemplatePanel(project).also { Disposer.register(this, it) }
    private val timerPanel = TimerPanel(TimerService.getInstance(project))
    private val referencePanel = ReferencePanel()
    private val settingsPanel = SettingsPanel(project)
    private var lastLoadedFolder: String? = null
    private val tabbedPane = JBTabbedPane()

    // UI 언어가 바뀌면 도구 창 콘텐츠를 통째로 다시 생성해 즉시 반영한다.
    // 패널들이 생성 시점에 I18n.t()로 텍스트를 한 번만 읽으므로, 새로 만들어야 갱신됨.
    private val languageListener: () -> Unit = {
        SwingUtilities.invokeLater { rebuildToolWindow() }
    }

    init {
        border = JBUI.Borders.empty()
        I18n.addChangeListener(languageListener)

        tabbedPane.addTab(I18n.t("문제", "Problems"), AllIcons.Actions.Download, problemPanel)
        tabbedPane.addTab(I18n.t("테스트", "Tests"), AllIcons.Actions.Execute, testPanel)
        tabbedPane.addTab(I18n.t("템플릿", "Templates"), AllIcons.Actions.Copy, templatePanel)
        tabbedPane.addTab(I18n.t("타이머", "Timer"), AllIcons.Vcs.History, timerPanel)
        tabbedPane.addTab(I18n.t("레퍼런스", "Reference"), AllIcons.Actions.Preview, referencePanel)
        tabbedPane.addTab(I18n.t("설정", "Settings"), AllIcons.General.Settings, settingsPanel)
        // 언어 변경으로 재생성될 때 이전에 보던 탭을 유지 (첫 탭으로 튕기지 않도록)
        if (initialTabIndex in 0 until tabbedPane.tabCount) tabbedPane.selectedIndex = initialTabIndex

        add(tabbedPane, BorderLayout.CENTER)

        // Problems ↔ Tests 언어 선택 양방향 동기화
        // (setLanguage가 같은 값이면 무시하므로 콜백이 무한 순환하지 않음)
        problemPanel.onLanguageChanged = { testPanel.setLanguage(it) }
        testPanel.onLanguageChanged = { problemPanel.setLanguage(it) }

        // 지문 최대화 (이슈 #33): 탭 스트립까지 제거해 지문이 도구창 전체를 차지
        problemPanel.onMaximizeToggle = { maximized -> setProblemMaximized(maximized) }

        // 문제를 가져오면 테스트 패널에 테스트 케이스 전달
        problemPanel.onProblemFetched = { problem ->
            testPanel.setProblemSource(problem.source)
            testPanel.setParameterNames(problem.parameterNames)
            testPanel.setParameterTypes(problem.parameterTypes ?: emptyList())
            testPanel.setMultipleAnswersHint(TestPanel.detectsMultipleAnswers(problem.description))
            testPanel.setTestCases(problem.testCases)
            CodingTestKitActionService.getInstance(project).updateStatus(
                problem.source.localizedName(), problem.id
            )
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
                    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                        // 파일 닫은 후 현재 선택된 파일 기준으로 판단
                        val selected = source.selectedFiles.firstOrNull()
                        if (selected == null || !selected.path.contains("/problems/")) {
                            lastLoadedFolder = null
                            problemPanel.clearProblem()
                            testPanel.setTestCases(emptyList())
                            testPanel.setMultipleAnswersHint(false)
                            CodingTestKitActionService.getInstance(project).updateStatus(null, null)
                        } else {
                            detectAndLoadProblem(selected, basePath)
                        }
                    }
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        val file = event.newFile
                        if (file == null) {
                            lastLoadedFolder = null
                            problemPanel.clearProblem()
                            testPanel.setTestCases(emptyList())
                            testPanel.setMultipleAnswersHint(false)
                            CodingTestKitActionService.getInstance(project).updateStatus(null, null)
                            return
                        }
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
        testPanel.setParameterTypes(problem.parameterTypes ?: emptyList())
        testPanel.setMultipleAnswersHint(TestPanel.detectsMultipleAnswers(problem.description))
        testPanel.setTestCases(problem.testCases)
        CodingTestKitActionService.getInstance(project).updateStatus(
            problem.source.localizedName(), problem.id
        )

        // 현재 문제 폴더를 소스 루트로 전환 (자동완성 활성화)
        ApplicationManager.getApplication().invokeLater {
            val vfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            val folderVf = vfs.findFileByIoFile(folder)
            if (folderVf != null) {
                ProblemFileManager.markAsSourceRoot(project, folderVf)
            }
        }
    }

    /**
     * 도구 창의 콘텐츠(이 MainPanel)를 새 MainPanel로 교체한다.
     * 기존 콘텐츠를 dispose하면 이 인스턴스의 languageListener도 함께 해제된다.
     */
    /**
     * 지문 최대화/복원 (이슈 #33). 최대화 시 탭 컨테이너를 떼고 ProblemPanel을
     * 직접 중앙에 재부모화해 탭 스트립 공간까지 지문이 쓴다. 복원 시 ProblemPanel을
     * 문제 탭 자리(index 0)로 되돌린다 — Swing은 부모가 하나뿐이라 재부모화 시
     * 탭에서 자동으로 빠지므로 복원 때 setComponentAt으로 명시 복구가 필요하다.
     */
    private fun setProblemMaximized(maximized: Boolean) {
        if (maximized) {
            remove(tabbedPane)
            add(problemPanel, BorderLayout.CENTER)
        } else {
            remove(problemPanel)
            // 최대화 때 problemPanel을 재부모화하면서 "문제" 탭이 통째로 제거됐다
            // (JTabbedPane은 내용 컴포넌트가 빠지면 removeTabAt으로 탭 자체를 없앤다).
            // 따라서 setComponentAt으로 index 0을 덮으면 "테스트" 탭 내용을 망가뜨린다 —
            // insertTab으로 제목·아이콘까지 index 0에 새로 꽂아 "문제" 탭을 복원한다.
            tabbedPane.insertTab(I18n.t("문제", "Problems"), AllIcons.Actions.Download, problemPanel, null, 0)
            add(tabbedPane, BorderLayout.CENTER)
            tabbedPane.selectedIndex = 0
        }
        revalidate()
        repaint()
    }

    private fun rebuildToolWindow() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodingTestKit") ?: return
        val contentManager = toolWindow.contentManager
        val newPanel = MainPanel(project, tabbedPane.selectedIndex)
        val newContent = ContentFactory.getInstance().createContent(newPanel, "", false)
        newContent.setDisposer(newPanel)
        contentManager.removeAllContents(true) // 기존 콘텐츠 dispose → 이 MainPanel.dispose() 호출
        contentManager.addContent(newContent)
    }

    override fun dispose() {
        I18n.removeChangeListener(languageListener)
    }
}
