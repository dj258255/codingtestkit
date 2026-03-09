package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.codingtestkit.model.Problem
import com.codingtestkit.model.ProblemSource
import com.codingtestkit.model.TestCase
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object ProblemFileManager {

    data class CreatedFiles(
        val folder: File,
        val codeFile: File,
        val markdownFile: File?
    )

    /**
     * 문제 폴더 생성 + README.md + 코드 파일 생성
     * 구조: {project}/problems/{platform}/{problemId}/
     */
    fun createProblemFiles(
        project: Project,
        problem: Problem,
        language: Language,
        templateCode: String? = null
    ): CreatedFiles {
        val basePath = project.basePath ?: throw IllegalStateException("프로젝트 경로를 찾을 수 없습니다.")

        // 폴더 생성: problems/백준/Gold V/1000. A+B/
        val levelFolder = problem.difficulty.ifBlank { "Unrated" }
        val folderName = sanitizeFolderName("${problem.id}. ${problem.title}")
        val problemDir = File(basePath, "problems/${problem.source.displayName}/$levelFolder/$folderName")
        problemDir.mkdirs()

        // README.md 생성 (설정에서 켜진 경우만)
        val markdownFile = if (PluginSettingsService.getInstance().generateReadme) {
            File(problemDir, "README.md").also { it.writeText(generateMarkdown(problem)) }
        } else null

        // 코드 파일 생성
        val codeFileName = "${problem.source.mainClassName}.${language.extension}"
        val codeFile = File(problemDir, codeFileName)
        if (!codeFile.exists()) {
            val code = templateCode ?: problem.initialCode.ifBlank { language.defaultCode(problem.source) }
            codeFile.writeText(code)
        }

        // problem.json 저장 (메타데이터 + 테스트 케이스)
        saveProblemJson(problemDir, problem)

        // VFS 새로고침 + 소스 루트 등록 + 파일 열기
        ApplicationManager.getApplication().invokeLater {
            val vfs = LocalFileSystem.getInstance()
            vfs.refreshAndFindFileByIoFile(problemDir)

            // 각 문제 폴더를 개별 소스 루트로 등록 (자동완성 활성화)
            val problemVf = vfs.refreshAndFindFileByIoFile(problemDir)
            if (problemVf != null) {
                markAsSourceRoot(project, problemVf)
            }

            val virtualFile = vfs.refreshAndFindFileByIoFile(codeFile)
            if (virtualFile != null) {
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }
        }

        return CreatedFiles(problemDir, codeFile, markdownFile)
    }

    private const val CTK_MODULE_NAME = "CodingTestKit-Problem"

    /**
     * 전용 모듈에 현재 문제 폴더를 소스 루트로 등록 (자동완성 활성화)
     */
    fun markAsSourceRoot(project: Project, folder: VirtualFile) {
        val moduleManager = ModuleManager.getInstance(project)

        // 전용 모듈이 없으면 생성
        var module = moduleManager.findModuleByName(CTK_MODULE_NAME)
        if (module == null) {
            WriteAction.runAndWait<Throwable> {
                val imlPath = "${project.basePath}/.idea/$CTK_MODULE_NAME.iml"
                module = moduleManager.newModule(imlPath, ModuleTypeId.JAVA_MODULE)
            }
        }

        val mod = module ?: return

        // 소스 루트를 현재 문제 폴더로 교체
        ModuleRootModificationUtil.updateModel(mod) { model ->
            for (entry in model.contentEntries.toList()) {
                model.removeContentEntry(entry)
            }
            val entry = model.addContentEntry(folder)
            entry.addSourceFolder(folder, false)
            model.inheritSdk()
        }
    }

    fun clearSourceRoots(project: Project) {
        val moduleManager = ModuleManager.getInstance(project)
        val module = moduleManager.findModuleByName(CTK_MODULE_NAME) ?: return
        ModuleRootModificationUtil.updateModel(module) { model ->
            for (entry in model.contentEntries.toList()) {
                model.removeContentEntry(entry)
            }
        }
    }

    /**
     * 문제를 Markdown으로 변환
     */
    private fun generateMarkdown(problem: Problem): String {
        val sb = StringBuilder()

        sb.appendLine("# [${problem.source.displayName} #${problem.id}] ${problem.title}")
        sb.appendLine()

        if (problem.timeLimit.isNotBlank()) {
            sb.appendLine("- **시간 제한**: ${problem.timeLimit}")
            sb.appendLine("- **메모리 제한**: ${problem.memoryLimit}")
            sb.appendLine()
        }

        sb.appendLine("---")
        sb.appendLine()

        // HTML → Markdown 변환
        sb.appendLine(HtmlToMarkdown.convert(problem.description))

        // 테스트 케이스 (프로그래머스는 description에 이미 입출력 예 테이블 포함)
        if (problem.testCases.isNotEmpty() && problem.source != ProblemSource.PROGRAMMERS && problem.source != ProblemSource.LEETCODE) {
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("## 예제")
            sb.appendLine()

            for ((i, tc) in problem.testCases.withIndex()) {
                sb.appendLine("### 예제 ${i + 1}")
                if (problem.source == ProblemSource.PROGRAMMERS && problem.parameterNames.isNotEmpty()) {
                    val inputs = tc.input.split("\n")
                    for ((j, param) in problem.parameterNames.withIndex()) {
                        sb.appendLine("- **$param**: ${inputs.getOrElse(j) { "" }}")
                    }
                    sb.appendLine("- **result**: ${tc.expectedOutput}")
                } else if (problem.source == ProblemSource.SWEA) {
                    // SWEA: 테스트 데이터가 길 수 있으므로 미리보기만 표시
                    val inputPreview = truncatePreview(tc.input, 10)
                    val outputPreview = truncatePreview(tc.expectedOutput, 5)
                    sb.appendLine("**입력:** (전체 데이터는 `input.txt` 참고)")
                    sb.appendLine("```")
                    sb.appendLine(inputPreview)
                    sb.appendLine("```")
                    sb.appendLine("**출력:** (전체 데이터는 `output.txt` 참고)")
                    sb.appendLine("```")
                    sb.appendLine(outputPreview)
                    sb.appendLine("```")
                } else {
                    sb.appendLine("**입력:**")
                    sb.appendLine("```")
                    sb.appendLine(tc.input)
                    sb.appendLine("```")
                    sb.appendLine("**출력:**")
                    sb.appendLine("```")
                    sb.appendLine(tc.expectedOutput)
                    sb.appendLine("```")
                }
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    private fun truncatePreview(text: String, maxLines: Int): String {
        val lines = text.lines()
        return if (lines.size > maxLines) {
            lines.take(maxLines).joinToString("\n") + "\n..."
        } else {
            text
        }
    }

    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9가-힣_\\-() ]"), "_")
            .replace(Regex("_+"), "_")
            .trim()
            .take(60)
    }

    private val gson = Gson()

    private fun saveProblemJson(folder: File, problem: Problem) {
        val json = gson.toJson(mapOf(
            "source" to problem.source.name,
            "id" to problem.id,
            "title" to problem.title,
            "description" to problem.description,
            "difficulty" to problem.difficulty,
            "timeLimit" to problem.timeLimit,
            "memoryLimit" to problem.memoryLimit,
            "contestProbId" to problem.contestProbId,
            "parameterNames" to problem.parameterNames,
            "testCases" to problem.testCases.map { mapOf("input" to it.input, "expectedOutput" to it.expectedOutput) }
        ))
        File(folder, "problem.json").writeText(json)
    }

    /**
     * 문제 폴더에서 problem.json을 읽어 Problem 객체 복원
     */
    fun loadProblemFromFolder(folder: File): Problem? {
        val jsonFile = File(folder, "problem.json")
        if (!jsonFile.exists()) return null
        return try {
            val json = JsonParser.parseString(jsonFile.readText()).asJsonObject
            val source = ProblemSource.valueOf(json.get("source").asString)
            val testCases = mutableListOf<TestCase>()
            val tcArr = json.getAsJsonArray("testCases")
            if (tcArr != null) {
                for (tc in tcArr) {
                    val obj = tc.asJsonObject
                    testCases.add(TestCase(
                        input = obj.get("input")?.asString ?: "",
                        expectedOutput = obj.get("expectedOutput")?.asString ?: ""
                    ))
                }
            }
            val paramNames = json.getAsJsonArray("parameterNames")?.map { it.asString } ?: emptyList()
            Problem(
                source = source,
                id = json.get("id")?.asString ?: "",
                title = json.get("title")?.asString ?: "",
                description = json.get("description")?.asString ?: "",
                testCases = testCases,
                timeLimit = json.get("timeLimit")?.asString ?: "",
                memoryLimit = json.get("memoryLimit")?.asString ?: "",
                difficulty = json.get("difficulty")?.asString ?: "",
                parameterNames = paramNames,
                contestProbId = json.get("contestProbId")?.asString ?: ""
            )
        } catch (_: Exception) { null }
    }

    /**
     * 파일 경로에서 문제 폴더 찾기 (problems/ 하위 폴더 탐색)
     */
    fun findProblemFolder(filePath: String, projectBasePath: String): File? {
        val problemsDir = File(projectBasePath, "problems")
        if (!problemsDir.exists()) return null
        var dir = File(filePath).let { if (it.isFile) it.parentFile else it }
        while (dir != null && dir.absolutePath.startsWith(problemsDir.absolutePath)) {
            if (File(dir, "problem.json").exists()) return dir
            dir = dir.parentFile
        }
        return null
    }

    /**
     * 기존 문제 폴더에서 코드 파일 찾기
     */
    fun findCodeFile(project: Project, source: ProblemSource, problemId: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val problemsDir = File(basePath, "problems/${source.displayName}")
        if (!problemsDir.exists()) return null

        // 난이도 폴더 안에서 "번호. " 패턴으로 시작하는 폴더 찾기
        for (levelDir in problemsDir.listFiles().orEmpty()) {
            if (!levelDir.isDirectory) continue
            val matchingDir = levelDir.listFiles()?.firstOrNull {
                it.isDirectory && it.name.startsWith("$problemId.")
            }
            if (matchingDir != null) {
                val codeFile = matchingDir.listFiles()?.firstOrNull {
                    it.extension in listOf("java", "py", "cpp", "kt", "js")
                }
                if (codeFile != null) {
                    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(codeFile)
                }
            }
        }
        return null
    }
}
