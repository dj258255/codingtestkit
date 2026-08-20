package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.project.Project
import java.io.File

/**
 * 컴파일 진단을 IDE Build Output 도구 창으로 전달 (이슈 #32).
 *
 * 테스트 패널의 텍스트 영역과 달리 Build 창은 진단 전용 UI라 가독성이 좋고,
 * FilePosition을 붙이면 클릭으로 소스 위치 이동이 된다.
 *
 * 줄 번호 매핑 주의: 플러그인은 임시 디렉토리에 소스를 써서 컴파일하므로
 * 진단의 파일·줄은 임시 파일 기준이다.
 * - stdin형 실행(코드포스/SWEA 등)은 사용자 코드를 원형 그대로 쓰므로 1:1 → 클릭 이동 가능
 * - 래퍼형(리트코드/프로그래머스)은 두 경우에 이동시킨다:
 *   (a) 진단 파일명이 사용자 파일명과 일치 (Java는 Solution.java가 별도 파일로 원형 유지)
 *   (b) 하네스를 사용자 코드 '아래'에만 붙이는 줄 보존 언어
 *       (python/kotlin/js/rust/ruby — 임시 파일명이 달라도 줄 번호는 1:1)
 *   그 밖(Go의 package 프리펜드 등)은 이동 없이 메시지만 남긴다.
 * - 어느 경우든 진단의 줄 번호가 사용자 파일 줄 수를 넘으면 하네스가 낸 것이므로
 *   이동을 붙이지 않는다.
 */
object BuildOutputPublisher {

    fun publishCompileError(
        project: Project,
        language: Language,
        rawError: String,
        userFile: File?,
        wrapperStyle: Boolean,
        /** false면 컴파일은 성공했고 경고만 전달하는 것 — 빌드 노드를 실패로 물들이지 않는다 */
        failed: Boolean = true
    ) {
        val viewManager = project.getService(BuildViewManager::class.java) ?: return
        val buildId = Any()
        for (event in buildEvents(buildId, project.basePath ?: "", language, rawError, userFile, wrapperStyle, failed)) {
            viewManager.onEvent(buildId, event)
        }
    }

    /**
     * 게시할 이벤트 열을 순서대로 만든다 — 순수 함수라 IDE 없이 검증할 수 있다.
     *
     * 발행을 이 함수로 분리한 이유는 v1.7.0 회귀(이슈 #32 재보고)가 이벤트 '객체'가
     * 아니라 '호출부가 넘긴 인자'에서 났기 때문이다. 객체를 직접 만들어 보는 테스트로는
     * 그 실수를 잡을 수 없어, 실제 발행 순서·id·parentId·이동 대상까지 여기서 검증한다.
     */
    internal fun buildEvents(
        buildId: Any,
        basePath: String,
        language: Language,
        rawError: String,
        userFile: File?,
        wrapperStyle: Boolean,
        failed: Boolean
    ): List<BuildEvent> {
        val events = mutableListOf<BuildEvent>()
        val title = "CodingTestKit: ${language.displayName} " + I18n.t("컴파일", "compile")
        val descriptor = DefaultBuildDescriptor(buildId, title, basePath, System.currentTimeMillis()).apply {
            // 실패했을 때만 Build 창을 자동으로 띄운다. 경고까지 창을 열면
            // 정상 실행마다 포커스를 빼앗는다.
            isActivateToolWindowWhenFailed = failed
        }
        events.add(CompileStartEvent(descriptor, I18n.t("컴파일 중...", "Compiling...")))

        // "컴파일 에러:\n" 프리픽스 제거 후 파싱
        val compilerOutput = rawError
            .removePrefix(I18n.t("컴파일 에러", "Compile error") + ":\n")
            .removePrefix("컴파일 에러:\n").removePrefix("Compile error:\n")

        // 래퍼가 사용자 코드 줄번호를 보존하는 언어 (하네스가 코드 아래에만 붙음)
        // — 래퍼형 실행이어도 클릭 이동이 정확하다. C++도 하네스 헤더를 사용자 코드
        // 아래로 내리면서 프리펜드가 사라져 합류했다. Java는 별도 파일 이름 일치
        // 규칙으로 커버하고, Go만 package 프리펜드 때문에 남아 있다.
        val linePreservedWrapper = language in setOf(
            Language.PYTHON, Language.KOTLIN, Language.JAVASCRIPT,
            Language.RUST, Language.RUBY, Language.CPP
        )

        val diags = parse(language, compilerOutput)
        if (diags.isEmpty()) {
            // 파싱 실패 시 원문 전체를 detail로 전달 (여전히 Build 창에서 보는 게 낫다).
            // OutputBuildEvent는 @NonExtendable이라 직접 구현할 수 없어 MessageEvent를 쓴다.
            val headline = compilerOutput.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                ?: if (failed) I18n.t("컴파일 실패", "Compilation failed")
                   else I18n.t("컴파일 경고", "Compiler warnings")
            events.add(CompileMessageEvent(
                buildId,
                if (failed) MessageEvent.Kind.ERROR else MessageEvent.Kind.WARNING,
                "Compiler", headline, compilerOutput
            ))
        } else {
            // 사용자 파일 줄 수 — 래퍼/하네스가 만든 진단은 이 범위를 넘어간다.
            // 범위를 넘는 줄에 이동을 붙이면 엉뚱한 위치로 점프하므로 메시지만 남긴다 (이슈 #32).
            val userFileLines = userFile?.takeIf { it.isFile }
                ?.let { runCatching { it.readLines().size }.getOrNull() }

            for (d in diags) {
                // 임시 파일 진단을 실제 소스로 매핑 가능한 경우에만 클릭 이동 부여
                val target = when {
                    userFile == null -> null
                    !wrapperStyle -> userFile                       // stdin형: 원형 그대로 → 1:1
                    // 래퍼형 + 파일명 일치: Java만. Java는 사용자 클래스를 별도 파일로
                    // 원형 유지하지만, 다른 언어는 임시 파일명이 solution.go처럼 고정이라
                    // 사용자 파일 이름과 우연히 같아도 줄 보존을 뜻하지 않는다.
                    language == Language.JAVA && d.fileName == userFile.name -> userFile
                    linePreservedWrapper -> userFile                 // 래퍼형: 줄 보존 언어 (하네스가 아래)
                    else -> null
                }
                val inRange = userFileLines == null || d.line <= userFileLines
                val position = if (target != null && d.line > 0 && inRange)
                    createFilePosition(target, d.line - 1, (d.column - 1).coerceAtLeast(0)) else null
                if (position != null) {
                    events.add(CompileFileMessageEvent(
                        buildId, d.severity, "Compiler", d.message, d.detail, position
                    ))
                } else {
                    events.add(CompileMessageEvent(
                        buildId, d.severity, "Compiler", d.message, d.detail
                    ))
                }
            }
        }

        events.add(CompileFinishEvent(
            buildId,
            if (failed) I18n.t("컴파일 실패", "Compilation failed")
            else I18n.t("컴파일 성공 (경고 있음)", "Compiled with warnings"),
            failed
        ))
        return events
    }

    /**
     * FilePosition 생성 (리플렉션 이중 전략).
     * File 기반 생성자는 2026.2에서 제거 예정이고 공식 대체재는 Path 기반 생성자인데,
     * Path 생성자는 우리 지원 하한인 2024.3 SDK에 존재하지 않는다. 그래서:
     * 1) Path 생성자(2026.2+ 신 API)를 먼저 시도 — File 생성자가 실제로 제거된
     *    미래 IDE에서도 클릭 이동이 계속 동작한다.
     * 2) 없으면 File 생성자(2024.3~2026.1의 유일한 API)로 폴백.
     * 두 경로 모두 리플렉션이라 마켓플레이스 검증 경고도 발생하지 않는다.
     */
    private fun createFilePosition(file: File, line: Int, column: Int): FilePosition? {
        try {
            return FilePosition::class.java.getConstructor(
                java.nio.file.Path::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            ).newInstance(file.toPath(), line, column, line, column)
        } catch (_: Throwable) {
            // 2026.1 이하: Path 생성자 없음 → File 생성자로
        }
        return try {
            FilePosition::class.java.getConstructor(
                File::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            ).newInstance(file, line, column, line, column)
        } catch (_: Throwable) {
            null
        }
    }

    // ─── 언어별 컴파일러 출력 파싱 ───

    data class Diagnostic(
        val fileName: String?,     // 진단의 파일명 (경로 제외 basename)
        val line: Int,             // 1-based, 불명이면 -1
        val column: Int,           // 1-based, 불명이면 -1
        val severity: MessageEvent.Kind,
        val message: String,
        val detail: String? = null
    )

    // g++의 note:는 직전 진단의 부연 설명이라 오류로 세면 오류 개수가 부풀려진다
    private fun kindOf(word: String): MessageEvent.Kind = when {
        word.equals("note", ignoreCase = true) -> MessageEvent.Kind.INFO
        word.contains("warn", ignoreCase = true) -> MessageEvent.Kind.WARNING
        else -> MessageEvent.Kind.ERROR
    }

    internal fun parse(language: Language, output: String): List<Diagnostic> = when (language) {
        Language.JAVA -> parseByPattern(output, Regex("""^(.+\.java):(\d+):\s*(error|warning):\s*(.+)$"""), colGroup = null)
        Language.KOTLIN -> parseByPattern(output, Regex("""^(.+\.kt):(\d+):(\d+):\s*(error|warning):\s*(.+)$"""), colGroup = 3)
        Language.CPP -> parseByPattern(output, Regex("""^(.+\.(?:cpp|cc|cxx|h|hpp)):(\d+):(\d+):\s*(error|warning|note):\s*(.+)$"""), colGroup = 3)
        Language.GO -> parseGo(output)
        Language.RUST -> parseRustc(output)
        // 인터프리터 언어: 컴파일 단계는 없지만 문법 에러(시작 실패)는 컴파일 에러의
        // 대응물이므로 파싱해 Build 창에 게시 (케이스별 런타임 예외는 테스트 패널 담당)
        Language.PYTHON -> parsePython(output)
        Language.JAVASCRIPT -> parseNode(output)
        Language.RUBY -> output.lineSequence().mapNotNull { line ->
            Regex("""^(.+\.rb):(\d+):\s*(syntax error.*)$""").find(line.trim())?.let { m ->
                Diagnostic(File(m.groupValues[1]).name, m.groupValues[2].toIntOrNull() ?: -1, -1,
                    MessageEvent.Kind.ERROR, m.groupValues[3].trim())
            }
        }.toList()
    }

    /**
     * 인터프리터 언어에서 '프로그램이 시작조차 못 한' 문법 에러인가 (이슈 #32).
     * true면 Build 창에 게시할 가치가 있음. 케이스별 런타임 예외는 제외.
     */
    fun isStartupError(language: Language, stderr: String): Boolean = when (language) {
        Language.PYTHON -> listOf("SyntaxError", "IndentationError", "TabError").any { it in stderr }
        Language.JAVASCRIPT -> "SyntaxError" in stderr
        Language.RUBY -> "syntax error" in stderr
        else -> false
    }

    /**
     * python:
     *   File "/tmp/.../solution.py", line 3
     *     def foo(:
     *   SyntaxError: invalid syntax
     */
    private fun parsePython(output: String): List<Diagnostic> {
        val fileLine = Regex("""^\s*File "(.+)", line (\d+)""")
        var lastFile: String? = null
        var lastLine = -1
        var message = ""
        for (line in output.lineSequence()) {
            fileLine.find(line)?.let { m ->
                lastFile = File(m.groupValues[1]).name
                lastLine = m.groupValues[2].toIntOrNull() ?: -1
            }
            if (Regex("""^\w*(Error|Warning):\s*.+""").matches(line.trim())) message = line.trim()
        }
        if (lastFile == null) return emptyList()
        return listOf(Diagnostic(lastFile, lastLine, -1, MessageEvent.Kind.ERROR,
            message.ifBlank { I18n.t("문법 에러", "Syntax error") }))
    }

    /**
     * node:
     *   /tmp/.../solution.js:3
     *   def foo(:
     *   SyntaxError: Unexpected identifier
     */
    private fun parseNode(output: String): List<Diagnostic> {
        val header = Regex("""^(.+\.(?:js|mjs|cjs)):(\d+)\s*$""")
        var file: String? = null
        var lineNo = -1
        var message = ""
        for (line in output.lineSequence()) {
            if (file == null) header.find(line.trim())?.let { m ->
                file = File(m.groupValues[1]).name
                lineNo = m.groupValues[2].toIntOrNull() ?: -1
            }
            if (message.isBlank() && Regex("""^\w*Error:\s*.+""").matches(line.trim())) message = line.trim()
        }
        if (file == null) return emptyList()
        return listOf(Diagnostic(file, lineNo, -1, MessageEvent.Kind.ERROR,
            message.ifBlank { I18n.t("문법 에러", "Syntax error") }))
    }

    /** "file:line[:col]: severity: message" 꼴 공통 파서 (javac/kotlinc/g++) */
    /**
     * 헤드라인 다음에 오는 설명 줄들을 detail로 함께 담는다 (이슈 #32).
     *
     * javac는 `symbol:` / `location:` / 캐럿 줄을, g++·rustc는 소스 스니펫을
     * 헤드라인 아래에 붙인다. 헤드라인만 담으면 Build 창 상세 콘솔이 비어
     * 이슈가 요구한 '가독성'의 절반이 사라진다.
     */
    private fun parseByPattern(output: String, pattern: Regex, colGroup: Int?): List<Diagnostic> {
        val diags = mutableListOf<Diagnostic>()
        val pending = mutableListOf<String>()   // 직전 진단에 붙일 설명 줄
        val lines = output.lines()

        fun flush() {
            if (pending.isEmpty()) return
            val detail = pending.joinToString("\n").trimEnd()
            if (detail.isNotBlank() && diags.isNotEmpty()) {
                val last = diags.removeAt(diags.size - 1)
                diags.add(last.copy(detail = last.message + "\n" + detail))
            }
            pending.clear()
        }

        for (line in lines) {
            val m = pattern.find(line.trim())
            if (m == null) {
                if (diags.isNotEmpty() && line.isNotBlank()) pending.add(line)
                continue
            }
            flush()
            val g = m.groupValues
            val sevIdx = if (colGroup != null) 4 else 3
            diags.add(Diagnostic(
                fileName = File(g[1]).name,
                line = g[2].toIntOrNull() ?: -1,
                column = colGroup?.let { g[it].toIntOrNull() } ?: -1,
                severity = kindOf(g[sevIdx]),
                message = g[sevIdx + 1].trim()
            ))
        }
        flush()
        return diags
    }

    /** go: "./solution.go:5:2: message" (severity 표기 없음 — 전부 에러) */
    private fun parseGo(output: String): List<Diagnostic> {
        val pattern = Regex("""^(.+\.go):(\d+):(\d+):\s*(.+)$""")
        return output.lineSequence().mapNotNull { line ->
            val m = pattern.find(line.trim()) ?: return@mapNotNull null
            val g = m.groupValues
            Diagnostic(File(g[1]).name, g[2].toIntOrNull() ?: -1, g[3].toIntOrNull() ?: -1,
                MessageEvent.Kind.ERROR, g[4].trim())
        }.toList()
    }

    /**
     * rustc: 메시지와 위치가 두 줄로 분리됨
     *   error[E0308]: mismatched types
     *     --> solution.rs:5:9
     */
    private fun parseRustc(output: String): List<Diagnostic> {
        val head = Regex("""^(error|warning)(?:\[\w+])?:\s*(.+)$""")
        val loc = Regex("""^\s*-->\s*(.+\.rs):(\d+):(\d+)""")
        val diags = mutableListOf<Diagnostic>()
        var pending: Pair<MessageEvent.Kind, String>? = null
        for (line in output.lineSequence()) {
            head.find(line.trim())?.let { m ->
                pending = kindOf(m.groupValues[1]) to m.groupValues[2].trim()
                return@let
            }
            val lm = loc.find(line) ?: continue
            val p = pending ?: continue
            diags.add(Diagnostic(File(lm.groupValues[1]).name,
                lm.groupValues[2].toIntOrNull() ?: -1, lm.groupValues[3].toIntOrNull() ?: -1,
                p.first, p.second))
            pending = null
        }
        return diags
    }
}
