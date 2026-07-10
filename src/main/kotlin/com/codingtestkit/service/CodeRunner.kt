package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.codingtestkit.model.TestCase
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object CodeRunner {

    /**
     * 컴파일 단계 전용 타임아웃.
     * 컴파일 시간은 문제의 실행 시간 제한과 무관하며, 저사양·콜드 캐시 환경(CI 등)에서는
     * rustc/go/kotlinc가 10초를 훌쩍 넘길 수 있어 넉넉하게 잡는다.
     * 60초로도 느린 Windows CI 러너에서 rustc -O가 간헐적으로 초과함 (이슈 #18) —
     * 상한일 뿐이라 정상 컴파일 속도에는 영향 없음.
     */
    private const val COMPILE_TIMEOUT_SECONDS = 120L

    data class RunResult(
        val output: String,
        val error: String,
        val exitCode: Int,
        val timedOut: Boolean = false,
        val executionTimeMs: Long = 0,
        val peakMemoryKB: Long = 0,
        /** 컴파일 단계 실패 여부 (Build Output 창 게시용, 이슈 #32) */
        val compileError: Boolean = false
    )

    /**
     * 디버그 세션 시작 결과 (이슈 #36 Tier 1).
     * ok=true면 port로 IDE 디버거를 attach하고, 세션이 끝나면 cleanup()을 호출해야 한다.
     */
    data class DebugHandle(
        val ok: Boolean,
        val port: Int = -1,
        val errorMessage: String? = null,
        val process: Process? = null,
        val workDir: File? = null
    ) {
        fun cleanup() {
            try { process?.destroyForcibly() } catch (_: Exception) {}
            try { workDir?.deleteRecursively() } catch (_: Exception) {}
        }
    }

    /** OS가 비워둔 TCP 포트 하나 확보 (loopback 기준 — 디버그 포트도 127.0.0.1에 바인딩) */
    fun findFreePort(): Int =
        java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }

    /**
     * 언어별 디버그 실행 진입점 (이슈 #36). 케이스 입력으로 디버그 서버를 띄우고,
     * 반환된 port에 TestDebugAdapter가 IDE 디버거를 attach한다.
     *
     * @param userFile 에디터에 열린 실제 파일. Go 등 네이티브 디버깅은 브레이크포인트를
     *                 소스 파일 경로로 바인딩하므로, 임시 사본이 아닌 실제 파일로 빌드해야 한다.
     */
    fun startDebug(
        code: String,
        language: Language,
        testCase: TestCase,
        parameterNames: List<String>,
        source: com.codingtestkit.model.ProblemSource,
        userFile: File? = null
    ): DebugHandle = when (language) {
        Language.JAVA, Language.KOTLIN -> startJvmDebug(code, language, testCase, parameterNames, source, userFile)
        Language.GO -> startGoDebug(code, testCase.input, userFile)
        else -> DebugHandle(false, errorMessage = I18n.t(
            "${language.displayName} 디버깅은 아직 지원되지 않습니다.",
            "${language.displayName} debugging is not supported yet."
        ))
    }


    /**
     * Go 코드를 dlv headless 서버로 실행해 디버거 attach를 대기시킨다 (이슈 #36 Tier 3).
     * dlv도 JDWP suspend=y처럼 클라이언트가 붙기 전까지 프로그램을 정지시켜 두므로
     * attach 전에 끝나버리는 레이스가 없다.
     *
     * 브레이크포인트는 바이너리에 기록된 소스 파일 경로로 바인딩되므로, 에디터의 실제
     * 파일(userFile)로 빌드한다 — 임시 사본으로 빌드하면 브레이크포인트가 잡히지 않는다.
     */
    private fun startGoDebug(code: String, input: String, userFile: File?): DebugHandle {
        if (goPath.isBlank()) {
            return DebugHandle(false, errorMessage = I18n.t(
                "Go를 찾을 수 없습니다.\nhttps://go.dev/dl 에서 Go를 설치하세요.",
                "Go not found.\nPlease install Go via https://go.dev/dl"
            ))
        }
        val dlv = findDlv() ?: return DebugHandle(false, errorMessage = I18n.t(
            "dlv(Delve 디버거)를 찾을 수 없습니다.\nGoLand에서 실행 중인지 확인하거나 `go install github.com/go-delve/delve/cmd/dlv@latest`로 설치하세요.",
            "dlv (Delve debugger) not found.\nMake sure you are running GoLand, or install it: `go install github.com/go-delve/delve/cmd/dlv@latest`"
        ))
        if (!code.contains("func main(")) {
            return DebugHandle(false, errorMessage = I18n.t(
                "Go 디버깅은 main 함수가 있는 코드(Codeforces/SWEA 스타일)에서 지원됩니다.\nLeetCode/프로그래머스 래퍼 스타일은 추후 지원 예정입니다.",
                "Go debugging supports code with a main function (Codeforces/SWEA style).\nLeetCode/Programmers wrapper style is planned."
            ))
        }

        val dir = createTempDir()
        return try {
            // 실제 파일 경로로 빌드해야 브레이크포인트가 바인딩된다 (에디터 저장은 호출부 책임)
            val sourceFile = if (userFile != null && userFile.exists()) userFile
                             else File(dir, "solution.go").apply { writeText(code, StandardCharsets.UTF_8) }
            val bin = File(dir, if (isWindows) "solution.exe" else "solution")
            // -gcflags all=-N -l: 최적화·인라이닝 비활성화 (디버그 정보 보존, dlv 표준 플래그)
            val compile = executeProcess(
                listOf(goPath, "build", "-gcflags", "all=-N -l", "-o", bin.absolutePath, sourceFile.absolutePath),
                dir, "", COMPILE_TIMEOUT_SECONDS
            )
            if (compile.exitCode != 0) {
                dir.deleteRecursively()
                return DebugHandle(false, errorMessage = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}")
            }

            val port = findFreePort()
            val process = ProcessBuilder(
                listOf(dlv, "--listen=127.0.0.1:$port", "--headless=true", "--api-version=2", "exec", bin.absolutePath)
            ).directory(dir).start()
            // 케이스 입력 전달 — dlv headless는 자기 stdin을 안 읽고 대상 프로그램에 물려준다
            Thread {
                try {
                    process.outputStream.bufferedWriter(Charsets.UTF_8).use { if (input.isNotBlank()) it.write(input) }
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }
            drainToVoid(process.inputStream)
            drainToVoid(process.errorStream)

            DebugHandle(true, port = port, process = process, workDir = dir)
        } catch (e: Exception) {
            dir.deleteRecursively()
            DebugHandle(false, errorMessage = e.message ?: "debug start failed")
        }
    }

    /**
     * dlv 탐색: 1) Go 플러그인(GoLand)에 번들된 dlv → 2) PATH/GOPATH의 dlv.
     * 번들 경로: <go-plugin>/lib/dlv/<os><arch>/dlv
     */
    private fun findDlv(): String? {
        try {
            val plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(
                com.intellij.openapi.extensions.PluginId.getId("org.jetbrains.plugins.go"))
            val base = plugin?.pluginPath?.toFile()
            if (base != null) {
                val os = System.getProperty("os.name").lowercase()
                val arm = System.getProperty("os.arch").lowercase().let { it.contains("aarch64") || it.contains("arm") }
                val osDir = when {
                    os.contains("win") -> if (arm) "windowsarm" else "windows"
                    os.contains("mac") -> if (arm) "macarm" else "mac"
                    else -> if (arm) "linuxarm" else "linux"
                }
                val f = File(base, "lib/dlv/$osDir/" + if (os.contains("win")) "dlv.exe" else "dlv")
                if (f.exists()) return f.absolutePath
            }
        } catch (_: Throwable) {}
        val fallback = findExecutable("dlv", "${System.getProperty("user.home")}/go/bin/dlv")
        return fallback.ifBlank { null }
    }

    /**
     * Java/Kotlin 코드를 JDWP suspend=y 로 실행해 디버거 attach를 대기시킨다 (이슈 #36 Tier 1).
     * suspend=y라 디버거가 붙기 전까지 사용자 코드가 실행되지 않아, attach 전에 끝나버리는
     * 레이스가 없다. 반환된 process는 디버그 세션이 잡고 있으므로 호출부가 정리 시점을 관리.
     *
     * 지원: JAVA, KOTLIN. 그 외 언어는 ok=false + 안내 메시지.
     * 컴파일 에러 시에도 ok=false + 에러 메시지.
     */
    private fun startJvmDebug(
        code: String,
        language: Language,
        testCase: TestCase,
        parameterNames: List<String>,
        source: com.codingtestkit.model.ProblemSource,
        userFile: File? = null
    ): DebugHandle {
        if (language != Language.JAVA && language != Language.KOTLIN) {
            return DebugHandle(false, errorMessage = I18n.t(
                "현재 디버깅은 IntelliJ IDEA의 Java/Kotlin만 지원합니다.\n" +
                    "(C++/Rust/Go/Ruby는 CLion/RustRover/GoLand/RubyMine에서 지원 예정)",
                "Debugging currently supports Java/Kotlin in IntelliJ IDEA only.\n" +
                    "(C++/Rust/Go/Ruby will be supported in CLion/RustRover/GoLand/RubyMine)"
            ))
        }

        // 래퍼가 필요한 경우(프로그래머스/리트코드, main 없음) 래핑
        val wrapperStyle = source == com.codingtestkit.model.ProblemSource.PROGRAMMERS ||
            source == com.codingtestkit.model.ProblemSource.LEETCODE
        val runCode: String
        val stdin: String
        if (wrapperStyle && !hasMainFunction(code, language)) {
            val inputValues = testCase.input.split("\n").map { it.trim() }
            runCode = if (language == Language.JAVA) wrapJava(code, inputValues, parameterNames)
                      else wrapKotlin(code, inputValues, parameterNames)
            stdin = ""
        } else {
            runCode = code
            stdin = testCase.input
        }

        val dir = createTempDir()
        return try {
            val port = findFreePort()
            // 127.0.0.1로 바인딩해 로컬 IDE만 attach 가능하게 한다.
            // address=*:port 또는 bare port는 모든 인터페이스에 노출돼 원격 코드 실행 위험.
            val jdwp = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:$port"
            val command = if (language == Language.JAVA) {
                compileJavaForDebug(runCode, dir) ?: return DebugHandle(false, errorMessage =
                    I18n.t("컴파일 에러로 디버깅을 시작할 수 없습니다.", "Cannot start debugging due to a compile error.")).also { dir.deleteRecursively() }
            } else {
                compileKotlinForDebug(runCode, dir, userFile?.name) ?: return DebugHandle(false, errorMessage =
                    I18n.t("컴파일 에러로 디버깅을 시작할 수 없습니다.", "Cannot start debugging due to a compile error.")).also { dir.deleteRecursively() }
            }.let { entry -> javaCommand(jdwp) + entry }

            val process = ProcessBuilder(command).directory(dir).redirectErrorStream(false).start()
            // stdin 전달 (suspend 상태여도 파이프에 미리 써둘 수 있음)
            Thread {
                try {
                    process.outputStream.bufferedWriter(Charsets.UTF_8).use { if (stdin.isNotBlank()) it.write(stdin) }
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }
            drainToVoid(process.inputStream)

            // JDWP 에이전트가 소켓을 열기 전에 IDE가 attach하면 "Connection refused"가 난다.
            // 에이전트는 준비되면 stderr에 "Listening for transport..."를 출력하므로 그 줄을 기다린다.
            val listening = java.util.concurrent.CountDownLatch(1)
            Thread {
                try {
                    process.errorStream.bufferedReader(Charsets.UTF_8).use { r ->
                        var line = r.readLine()
                        while (line != null) {
                            if (line.contains("Listening for transport")) listening.countDown()
                            line = r.readLine()
                        }
                    }
                } catch (_: Exception) {} finally { listening.countDown() }
            }.apply { isDaemon = true; start() }
            // 최대 10초 대기 (컴파일은 이미 끝났고 JVM 부팅만 남음)
            listening.await(10, java.util.concurrent.TimeUnit.SECONDS)

            DebugHandle(true, port = port, process = process, workDir = dir)
        } catch (e: Exception) {
            dir.deleteRecursively()
            DebugHandle(false, errorMessage = e.message ?: "debug start failed")
        }
    }

    /** 디버그용 javac 커맨드 — -g로 지역변수 이름까지 디버그 정보에 포함 */
    private fun javacDebugCommand(vararg sourceFiles: File): List<String> =
        listOf(javacPath, "-g", "-encoding", "UTF-8") + sourceFiles.map { it.absolutePath }

    /** Java 디버그용 컴파일 → 실행 인자(-cp DIR MainClass) 반환, 실패 시 null */
    private fun compileJavaForDebug(code: String, dir: File): List<String>? {
        val sep = "///MAIN_SEPARATOR///"
        if (code.contains(sep)) {
            val parts = code.split(sep)
            File(dir, "Solution.java").writeText(parts[0].trim(), StandardCharsets.UTF_8)
            File(dir, "Main.java").writeText(parts[1].trim(), StandardCharsets.UTF_8)
            val c = executeProcess(javacDebugCommand(File(dir, "Solution.java"), File(dir, "Main.java")), dir, "", COMPILE_TIMEOUT_SECONDS)
            if (c.exitCode != 0) return null
            return listOf("-cp", dir.absolutePath, "Main")
        }
        val className = detectJavaClassName(code)
        val src = File(dir, "$className.java")
        src.writeText(code, StandardCharsets.UTF_8)
        val c = executeProcess(javacDebugCommand(src), dir, "", COMPILE_TIMEOUT_SECONDS)
        if (c.exitCode != 0) return null
        return listOf("-cp", dir.absolutePath, className)
    }

    /**
     * Kotlin 디버그용 컴파일 → 실행 인자(-jar solution.jar) 반환, 실패 시 null.
     * 임시 소스 파일명은 사용자 파일명과 같아야 한다 — Kotlin 브레이크포인트는
     * 파일명에서 파생된 파사드 클래스(Main.kt → MainKt)로 바인딩되기 때문.
     */
    private fun compileKotlinForDebug(code: String, dir: File, userFileName: String? = null): List<String>? {
        if (kotlincPath.isBlank()) return null
        val srcName = userFileName?.takeIf { it.endsWith(".kt") } ?: "Main.kt"
        val src = File(dir, srcName)
        src.writeText(code, StandardCharsets.UTF_8)
        val jar = File(dir, "solution.jar")
        val c = executeProcess(
            listOf(kotlincPath, "-J-Dfile.encoding=UTF-8", src.absolutePath, "-include-runtime", "-d", jar.absolutePath),
            dir, "", COMPILE_TIMEOUT_SECONDS
        )
        if (c.exitCode != 0) return null
        return listOf("-jar", jar.absolutePath)
    }

    private fun drainToVoid(stream: java.io.InputStream) {
        Thread {
            try { stream.bufferedReader(Charsets.UTF_8).use { while (it.readLine() != null) {} } } catch (_: Exception) {}
        }.apply { isDaemon = true; start() }
    }

    /**
     * stdin 방식: 입력을 표준입력으로 전달하고 stdout 결과를 비교
     */
    fun run(
        code: String,
        language: Language,
        testCase: TestCase,
        timeoutSeconds: Long = 5
    ): RunResult {
        val tempDir = createTempDir()
        return try {
            when (language) {
                Language.JAVA -> runJava(code, testCase.input, tempDir, timeoutSeconds)
                Language.PYTHON -> runPython(code, testCase.input, tempDir, timeoutSeconds)
                Language.CPP -> runCpp(code, testCase.input, tempDir, timeoutSeconds)
                Language.KOTLIN -> runKotlin(code, testCase.input, tempDir, timeoutSeconds)
                Language.JAVASCRIPT -> runJavaScript(code, testCase.input, tempDir, timeoutSeconds)
                Language.RUST -> runRust(code, testCase.input, tempDir, timeoutSeconds)
                Language.GO -> runGo(code, testCase.input, tempDir, timeoutSeconds)
                Language.RUBY -> runRuby(code, testCase.input, tempDir, timeoutSeconds)
            }
        } catch (e: Exception) {
            RunResult(output = "", error = e.message ?: "Unknown error", exitCode = -1)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * 프로그래머스 방식: solution 함수를 호출하는 테스트 래퍼를 생성하여 실행
     * 사용자 코드에 solution 함수가 있으면 래퍼로 감싸고,
     * 이미 main이 있으면 그대로 stdin 방식으로 실행
     */
    fun runProgrammers(
        code: String,
        language: Language,
        testCase: TestCase,
        parameterNames: List<String>,
        timeoutSeconds: Long = 5
    ): RunResult {
        // 이미 main 함수가 있으면 stdin 방식으로 실행
        if (hasMainFunction(code, language)) {
            return run(code, language, testCase, timeoutSeconds)
        }

        // 입력값을 줄 단위로 파싱 (파라미터별)
        val inputValues = testCase.input.split("\n").map { it.trim() }

        val wrappedCode = when (language) {
            Language.JAVA -> wrapJava(code, inputValues, parameterNames)
            Language.PYTHON -> wrapPython(code, inputValues, parameterNames)
            Language.CPP -> wrapCpp(code, inputValues, parameterNames)
            Language.KOTLIN -> wrapKotlin(code, inputValues, parameterNames)
            Language.JAVASCRIPT -> wrapJavaScript(code, inputValues, parameterNames)
            Language.RUST -> wrapRust(code, inputValues, parameterNames)
            Language.GO -> wrapGo(code, inputValues, parameterNames)
            Language.RUBY -> wrapRuby(code, inputValues, parameterNames)
        }

        val tempDir = createTempDir()
        return try {
            when (language) {
                Language.JAVA -> runJava(wrappedCode, "", tempDir, timeoutSeconds)
                Language.PYTHON -> runPython(wrappedCode, "", tempDir, timeoutSeconds)
                Language.CPP -> runCpp(wrappedCode, "", tempDir, timeoutSeconds)
                Language.KOTLIN -> runKotlin(wrappedCode, "", tempDir, timeoutSeconds)
                Language.JAVASCRIPT -> runJavaScript(wrappedCode, "", tempDir, timeoutSeconds)
                Language.RUST -> runRust(wrappedCode, "", tempDir, timeoutSeconds)
                Language.GO -> runGo(wrappedCode, "", tempDir, timeoutSeconds)
                Language.RUBY -> runRuby(wrappedCode, "", tempDir, timeoutSeconds)
            }
        } catch (e: Exception) {
            RunResult(output = "", error = e.message ?: "Unknown error", exitCode = -1)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun hasMainFunction(code: String, language: Language): Boolean {
        return when (language) {
            Language.JAVA -> code.contains("public static void main")
            Language.PYTHON -> code.contains("if __name__")
            Language.CPP -> code.contains("int main")
            Language.KOTLIN -> code.contains("fun main")
            Language.JAVASCRIPT -> code.contains("readline") || code.contains("process.stdin")
            Language.RUST -> code.contains("fn main")
            Language.GO -> code.contains("func main")
            // Ruby는 main이 없으므로 stdin 사용 여부로 스크립트/함수 스타일을 구분
            Language.RUBY -> code.contains("gets") || code.contains("STDIN")
        }
    }

    /**
     * Java 클래스명 감지 우선순위:
     * 1. public class 선언 (SWEA: Solution 등)
     * 2. main 메서드가 포함된 class
     * 3. 첫 번째 class 선언
     * 4. 기본값 "Main"
     */
    private fun detectJavaClassName(code: String): String {
        // 1. public class
        Regex("public\\s+class\\s+(\\w+)").find(code)?.groupValues?.get(1)?.let { return it }

        // 2. main 메서드가 있는 클래스 찾기
        val classes = Regex("class\\s+(\\w+)\\s*\\{").findAll(code).toList()
        for (match in classes) {
            val startIdx = match.range.last
            var braceCount = 1
            for (i in (startIdx + 1) until code.length) {
                if (code[i] == '{') braceCount++
                if (code[i] == '}') braceCount--
                if (braceCount == 0) {
                    val classBody = code.substring(startIdx, i)
                    if (classBody.contains("public static void main")) {
                        return match.groupValues[1]
                    }
                    break
                }
            }
        }

        // 3. 첫 번째 class
        classes.firstOrNull()?.groupValues?.get(1)?.let { return it }

        // 4. 기본값
        return "Main"
    }

    // ─── 프로그래머스 테스트 래퍼 생성 ───

    /**
     * 프로그래머스 입력값을 Java 리터럴로 변환
     * [1, 2, 3] → new int[]{1, 2, 3}
     * [[1,2],[3,4]] → new int[][]{{1,2},{3,4}}
     * ["a","b"] → new String[]{"a","b"}
     * "hello" → "hello" (그대로)
     * 123 → 123 (그대로)
     */
    private fun toJavaLiteral(value: String): String {
        val v = value.trim()
        if (!v.startsWith("[")) return v

        // 2차원 배열: [[1,2],[3,4]]
        if (v.startsWith("[[")) {
            val inner = v.removePrefix("[").removeSuffix("]")
            // 내부 배열들을 분리
            val arrays = mutableListOf<String>()
            var depth = 0
            var current = StringBuilder()
            for (c in inner) {
                if (c == '[') depth++
                if (c == ']') depth--
                current.append(c)
                if (depth == 0 && current.isNotBlank()) {
                    val arr = current.toString().trim().removePrefix(",").trim()
                    if (arr.isNotBlank()) arrays.add(arr)
                    current = StringBuilder()
                }
            }
            val converted = arrays.joinToString(", ") { toJavaLiteral(it) }
            // 내부 타입 감지
            val firstInner = arrays.firstOrNull() ?: ""
            val innerContent = firstInner.removePrefix("[").removeSuffix("]").trim()
            val type = detectJavaArrayType(innerContent)
            return "new ${type}[]{$converted}"
        }

        // 1차원 배열: [1, 2, 3]
        val content = v.removePrefix("[").removeSuffix("]").trim()
        if (content.isEmpty()) return "new int[]{}"
        val type = detectJavaArrayType(content)
        // 내부 배열 표기를 {} 로 변환
        return "new ${type}{${content}}"
    }

    private fun detectJavaArrayType(content: String): String {
        val first = content.split(",").firstOrNull()?.trim() ?: ""
        return when {
            first.startsWith("\"") -> "String[]"
            first == "true" || first == "false" -> "boolean[]"
            first.contains(".") -> "double[]"
            first.toLongOrNull() != null && (first.toLong() > Int.MAX_VALUE || first.toLong() < Int.MIN_VALUE) -> "long[]"
            else -> "int[]"
        }
    }

    /**
     * C++ 배열 변환: [1,2,3] → {1,2,3} (vector 초기화)
     */
    private fun toCppLiteral(value: String): String {
        val v = value.trim()
        if (!v.startsWith("[")) return v
        return v.replace('[', '{').replace(']', '}')
    }

    /**
     * Kotlin 배열 변환: [1,2,3] → intArrayOf(1,2,3)
     */
    private fun toKotlinLiteral(value: String): String {
        val v = value.trim()
        if (!v.startsWith("[")) return v

        if (v.startsWith("[[")) {
            val inner = v.removePrefix("[").removeSuffix("]")
            val arrays = mutableListOf<String>()
            var depth = 0
            var current = StringBuilder()
            for (c in inner) {
                if (c == '[') depth++
                if (c == ']') depth--
                current.append(c)
                if (depth == 0 && current.isNotBlank()) {
                    val arr = current.toString().trim().removePrefix(",").trim()
                    if (arr.isNotBlank()) arrays.add(arr)
                    current = StringBuilder()
                }
            }
            val converted = arrays.joinToString(", ") { toKotlinLiteral(it) }
            return "arrayOf($converted)"
        }

        val content = v.removePrefix("[").removeSuffix("]").trim()
        if (content.isEmpty()) return "intArrayOf()"
        val first = content.split(",").firstOrNull()?.trim() ?: ""
        return when {
            first.startsWith("\"") -> "arrayOf($content)"
            first == "true" || first == "false" -> "booleanArrayOf($content)"
            first.contains(".") -> "doubleArrayOf($content)"
            else -> "intArrayOf($content)"
        }
    }

    private fun wrapJava(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ") { toJavaLiteral(it) }

        // Solution 클래스에서 메서드 이름 추출 (solution 우선, 없으면 마지막 public 메서드)
        val javaMethods = Regex("""public\s+\S+\s+(\w+)\s*\(""").findAll(code)
            .map { it.groupValues[1] }
            .filter { it != "main" && it != "Solution" }.toList()
        val methodName = javaMethods.find { it == "solution" } ?: javaMethods.lastOrNull() ?: "solution"

        val solutionClass = if (code.contains("class Solution")) {
            code
        } else {
            "class Solution {\n$code\n}"
        }

        // import문 추출
        val imports = code.lines()
            .filter { it.trimStart().startsWith("import ") }
            .joinToString("\n")
        val importBlock = if (imports.isNotBlank()) "$imports\n\n" else ""

        // Solution과 Main을 SEPARATOR로 구분 (runJava에서 분리)
        return """
$solutionClass
///MAIN_SEPARATOR///
${importBlock}import java.util.Arrays;
import java.io.PrintStream;
class Main {
    public static void main(String[] args) {
        Solution sol = new Solution();
        // 사용자 debug 출력(System.out.println)을 stderr로 리다이렉트
        PrintStream origOut = System.out;
        System.setOut(System.err);
        Object _result = sol.$methodName($args);
        // stdout 복원 후 리턴값만 출력
        System.setOut(origOut);
        printResult(_result);
    }
    static String compact(String s) { return s.replace(", ", ","); }
    static void printResult(Object o) {
        if (o instanceof String) System.out.println("\"" + o + "\"");
        else if (o instanceof int[]) System.out.println(compact(Arrays.toString((int[])o)));
        else if (o instanceof long[]) System.out.println(compact(Arrays.toString((long[])o)));
        else if (o instanceof double[]) System.out.println(compact(Arrays.toString((double[])o)));
        else if (o instanceof boolean[]) System.out.println(compact(Arrays.toString((boolean[])o)));
        else if (o instanceof Object[]) System.out.println(compact(Arrays.deepToString((Object[])o)));
        else System.out.println(o);
    }
}
""".trimIndent()
    }

    private fun wrapPython(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ")
        val hasClass = code.contains("class Solution")

        // 클래스 기반(LeetCode): def 메서드명(self, ...) 에서 추출
        // 함수 기반(Programmers): def 메서드명(...) 에서 추출 (solution 우선)
        val methodName = if (hasClass) {
            val methods = Regex("""def\s+(\w+)\s*\(\s*self""").findAll(code)
                .map { it.groupValues[1] }
                .filter { it != "__init__" }.toList()
            methods.lastOrNull() ?: "solution"
        } else {
            val funcs = Regex("""def\s+(\w+)\s*\(""").findAll(code)
                .map { it.groupValues[1] }
                .filter { it != "__init__" }.toList()
            funcs.find { it == "solution" } ?: funcs.lastOrNull() ?: "solution"
        }

        val callExpr = if (hasClass) {
            "_sol = Solution()\n_result = _sol.$methodName($args)"
        } else {
            "_result = $methodName($args)"
        }

        // 사용자 코드가 1번 줄부터 원형 그대로 시작해야 브레이크포인트·진단 줄번호가
        // 1:1로 맞는다 (이슈 #36 디버깅 / #32 소스 이동). import는 하단 하네스로.
        return """
$code

# 사용자 print()를 stderr로 리다이렉트
import sys as _sys
_orig_stdout = _sys.stdout
_sys.stdout = _sys.stderr
$callExpr
# stdout 복원 후 리턴값만 출력
_sys.stdout = _orig_stdout
if isinstance(_result, str):
    print(f'"{_result}"')
elif isinstance(_result, list):
    import json as _json
    print(_json.dumps(_result, separators=(',', ':')))
else:
    print(_result)
""".trimIndent()
    }

    private fun wrapCpp(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ") { toCppLiteral(it) }
        val hasClass = code.contains("class Solution")

        // C++: 함수/메서드명 추출 (solution 이름 우선, 없으면 마지막 매칭)
        val excluded = setOf("Solution", "main")
        val cppMethods = Regex("""\b(\w+)\s*\([^)]*\)\s*\{""").findAll(code)
            .map { it.groupValues[1] }
            .filter { it !in excluded && !it.startsWith("~") }.toList()
        val methodName = cppMethods.find { it == "solution" } ?: cppMethods.lastOrNull() ?: "solution"
        // 사용자 코드가 1번 줄부터 원형 유지되도록, include가 이미 있으면(일반적)
        // 아무것도 프리펜드하지 않는다. include가 없을 때만 보충 (줄이 밀리지만 드묾)
        val hasInclude = code.contains("#include")
        val prefix = if (hasInclude) "" else """
#include <iostream>
#include <vector>
#include <string>
using namespace std;

""".trimStart('\n')

        val callExpr = if (hasClass) {
            "    Solution sol;\n    auto _result = sol.$methodName($args);"
        } else {
            "    auto _result = $methodName($args);"
        }

        return prefix + """
$code

template<typename T> void _print(T r) { cout << r; }
void _print(string r) { cout << "\"" << r << "\""; }
void _print(bool r) { cout << (r ? "true" : "false"); }
template<typename T> void _print(vector<T> v) {
    cout << "[";
    for (size_t i = 0; i < v.size(); i++) { if (i) cout << ","; _print(v[i]); }
    cout << "]";
}
template<typename T> void printResult(T r) { _print(r); cout << endl; }

int main() {
    // 사용자 cout을 stderr로 리다이렉트
    auto* _origBuf = cout.rdbuf();
    cout.rdbuf(cerr.rdbuf());
$callExpr
    // stdout 복원 후 리턴값만 출력
    cout.rdbuf(_origBuf);
    printResult(_result);
    return 0;
}
""".trimIndent()
    }

    private fun wrapKotlin(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ") { toKotlinLiteral(it) }
        val hasClass = code.contains("class Solution")

        // Kotlin: fun 메서드명(...) 에서 메서드명 추출 (solution 우선, 없으면 마지막)
        val ktMethods = Regex("""fun\s+(\w+)\s*\(""").findAll(code)
            .map { it.groupValues[1] }
            .filter { it != "main" }.toList()
        val methodName = ktMethods.find { it == "solution" } ?: ktMethods.lastOrNull() ?: "solution"

        val callExpr = if (hasClass) {
            "    val sol = Solution()\n    val result = sol.$methodName($args)"
        } else {
            "    val result = $methodName($args)"
        }

        return """
$code

fun main() {
    // 사용자 println을 stderr로 리다이렉트
    val _origOut = System.out
    System.setOut(System.err)
$callExpr
    // stdout 복원 후 리턴값만 출력
    System.setOut(_origOut)
    fun compact(s: String) = s.replace(", ", ",")
    when (result) {
        is String -> println("\"${'$'}result\"")
        is IntArray -> println(compact(result.contentToString()))
        is LongArray -> println(compact(result.contentToString()))
        is DoubleArray -> println(compact(result.contentToString()))
        is BooleanArray -> println(compact(result.contentToString()))
        is Array<*> -> println(compact(result.contentDeepToString()))
        else -> println(result)
    }
}
""".trimIndent()
    }

    private fun wrapJavaScript(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ")
        // JS: var/function/arrow 메서드명 또는 prototype.메서드명 추출 (solution 우선)
        val jsFuncs = Regex("""(?:var|const|let)\s+(\w+)\s*=\s*(?:function|\([^)]*\)\s*=>|\w+\s*=>)|\.prototype\.(\w+)\s*=|function\s+(\w+)""").findAll(code)
            .mapNotNull { it.groupValues.drop(1).firstOrNull { g -> g.isNotBlank() } }
            .filter { it != "main" }.toList()
        val methodName = jsFuncs.find { it == "solution" } ?: jsFuncs.lastOrNull() ?: "solution"
        return """
$code

// 사용자 console.log를 stderr로 리다이렉트
const _origLog = console.log;
console.log = (...a) => process.stderr.write(a.join(' ') + '\n');
const _result = $methodName($args);
// stdout 복원 후 리턴값만 출력
console.log = _origLog;
if (typeof _result === 'string') console.log('"' + _result + '"');
else if (Array.isArray(_result)) console.log(JSON.stringify(_result));
else console.log(_result);
""".trimIndent()
    }

    /**
     * Rust 배열 변환: [1,2,3] → vec![1,2,3], 문자열 → String::from("...")
     */
    private fun toRustLiteral(value: String): String {
        val v = value.trim()
        if (v.startsWith("\"")) return "String::from($v)"
        if (!v.startsWith("[")) return v

        if (v.startsWith("[[")) {
            val inner = v.removePrefix("[").removeSuffix("]")
            val arrays = mutableListOf<String>()
            var depth = 0
            var current = StringBuilder()
            for (c in inner) {
                if (c == '[') depth++
                if (c == ']') depth--
                current.append(c)
                if (depth == 0 && current.isNotBlank()) {
                    val arr = current.toString().trim().removePrefix(",").trim()
                    if (arr.isNotBlank()) arrays.add(arr)
                    current = StringBuilder()
                }
            }
            val converted = arrays.joinToString(", ") { toRustLiteral(it) }
            return "vec![$converted]"
        }

        val content = v.removePrefix("[").removeSuffix("]").trim()
        if (content.isEmpty()) return "vec![]"
        val first = content.split(",").firstOrNull()?.trim() ?: ""
        return if (first.startsWith("\"")) {
            val items = content.split(",").joinToString(", ") { "String::from(${it.trim()})" }
            "vec![$items]"
        } else {
            "vec![$content]"
        }
    }

    /**
     * Go 배열 변환: [1,2,3] → []int{1,2,3}, [[1,2],[3,4]] → [][]int{{1,2},{3,4}}
     */
    private fun toGoLiteral(value: String): String {
        val v = value.trim()
        if (!v.startsWith("[")) return v

        if (v.startsWith("[[")) {
            // 첫 내부 배열의 원소로 타입 감지: [[1,2],[3,4]] → [][]int{{1,2},{3,4}}
            val firstInner = v.removePrefix("[").substringAfter("[").substringBefore("]")
            val elemType = detectGoElementType(firstInner)
            return "[][]$elemType" + v.replace('[', '{').replace(']', '}')
        }

        val content = v.removePrefix("[").removeSuffix("]").trim()
        if (content.isEmpty()) return "[]int{}"
        val elemType = detectGoElementType(content)
        return "[]$elemType{$content}"
    }

    private fun detectGoElementType(content: String): String {
        val first = content.split(",").firstOrNull()?.trim() ?: ""
        return when {
            first.startsWith("\"") -> "string"
            first == "true" || first == "false" -> "bool"
            first.contains(".") -> "float64"
            first.toLongOrNull() != null && (first.toLong() > Int.MAX_VALUE || first.toLong() < Int.MIN_VALUE) -> "int64"
            else -> "int"
        }
    }

    private fun wrapRust(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ") { toRustLiteral(it) }

        // fn 메서드명 추출 (solution 우선, 없으면 마지막)
        val rsFns = Regex("""fn\s+(\w+)\s*\(""").findAll(code)
            .map { it.groupValues[1] }
            .filter { it != "main" }.toList()
        val methodName = rsFns.find { it == "solution" } ?: rsFns.lastOrNull() ?: "solution"

        // LeetCode 스타일(impl Solution)이면 struct 선언 보충 후 연관 함수로 호출.
        // Rust는 모듈 아이템 선언 순서가 무관하므로 struct를 코드 '아래'에 둬서
        // 사용자 코드 줄번호를 원형 그대로 보존한다 (이슈 #36 디버깅 / #32 소스 이동)
        val hasImpl = code.contains("impl Solution")
        val structDecl = if (hasImpl && !code.contains("struct Solution")) "\nstruct Solution;\n" else ""
        val callExpr = if (hasImpl) "Solution::$methodName($args)" else "$methodName($args)"

        return """
$code
$structDecl
fn main() {
    let _result = $callExpr;
    // {:?}는 문자열을 "따옴표 포함"으로, 벡터를 [a, b]로 출력 → 공백 제거로 [a,b] 형태 통일
    let _s = format!("{:?}", _result).replace(", ", ",");
    println!("{}", _s);
}
""".trimIndent()
    }

    private fun wrapGo(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ") { toGoLiteral(it) }

        val goFuncs = Regex("""func\s+(\w+)\s*\(""").findAll(code)
            .map { it.groupValues[1] }
            .filter { it != "main" }.toList()
        val methodName = goFuncs.find { it == "solution" } ?: goFuncs.lastOrNull() ?: "solution"

        // 사용자 코드에 package 선언이 있으면 제거 (래퍼가 package main을 제공)
        val body = code.lines().filterNot { it.trim().startsWith("package ") }.joinToString("\n")

        // fmt 대신 json/os만 import: 사용자 코드의 import "fmt"와 충돌 방지
        return """
package main

import (
	"encoding/json"
	"os"
)

$body

func main() {
	_result := $methodName($args)
	_b, _ := json.Marshal(_result)
	os.Stdout.Write(append(_b, '\n'))
}
""".trimIndent()
    }

    private fun wrapRuby(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ")

        val rbMethods = Regex("""def\s+(\w+)""").findAll(code)
            .map { it.groupValues[1] }
            .filter { it != "initialize" }.toList()
        val methodName = rbMethods.find { it == "solution" } ?: rbMethods.lastOrNull() ?: "solution"

        // require는 하단 하네스로 — 사용자 코드 줄번호 원형 보존 (이슈 #36/#32)
        return """
$code

# 사용자 puts를 stderr로 리다이렉트
require 'json'
_orig_stdout = ${'$'}stdout
${'$'}stdout = ${'$'}stderr
_result = $methodName($args)
# stdout 복원 후 리턴값만 출력
${'$'}stdout = _orig_stdout
if _result.is_a?(String)
  puts "\"#{_result}\""
elsif _result.is_a?(Array)
  puts _result.to_json
else
  puts _result
end
""".trimIndent()
    }

    // ─── 도구 경로 자동 감지 ───

    private val javaHome: String by lazy { detectJavaHome() }
    private val javacPath: String by lazy { findExecutable("javac", "$javaHome/bin/javac") }
    private val javaPath: String by lazy { findExecutable("java", "$javaHome/bin/java") }
    private val pythonPath: String by lazy { detectPython() }
    private val gppPath: String by lazy {
        findExecutable("g++",
            "/usr/bin/g++", "/usr/local/bin/g++", "/opt/homebrew/bin/g++",
            // Windows: MSYS2(UCRT64/MINGW64), MinGW, TDM-GCC, Chocolatey
            "C:\\msys64\\ucrt64\\bin\\g++", "C:\\msys64\\mingw64\\bin\\g++",
            "C:\\MinGW\\bin\\g++", "C:\\TDM-GCC-64\\bin\\g++",
            "C:\\ProgramData\\chocolatey\\bin\\g++")
    }
    private val kotlincPath: String by lazy { detectKotlinc() }
    private val nodePath: String by lazy { detectNode() }
    private val rustcPath: String by lazy {
        findExecutable("rustc",
            "${System.getProperty("user.home")}/.cargo/bin/rustc",
            "/usr/local/bin/rustc", "/opt/homebrew/bin/rustc")
    }
    private val goPath: String by lazy {
        findExecutable("go",
            "/usr/local/go/bin/go", "/opt/homebrew/bin/go", "/usr/local/bin/go",
            "${System.getProperty("user.home")}/go/bin/go")
    }
    private val rubyPath: String by lazy {
        findExecutable("ruby",
            "${System.getProperty("user.home")}/.rbenv/shims/ruby",
            "/opt/homebrew/opt/ruby/bin/ruby", "/usr/local/opt/ruby/bin/ruby",
            "/usr/bin/ruby")
    }

    private fun detectJavaHome(): String {
        // 1. JAVA_HOME 환경변수
        System.getenv("JAVA_HOME")?.let { if (File(it).exists()) return it }

        // 2. java.home 시스템 프로퍼티 (현재 JVM)
        System.getProperty("java.home")?.let { if (File(it).exists()) return it }

        // 3. macOS: /usr/libexec/java_home
        try {
            val proc = ProcessBuilder("/usr/libexec/java_home").start()
            val result = proc.inputStream.bufferedReader().readText().trim()
            if (proc.waitFor() == 0 && result.isNotBlank()) return result
        } catch (_: Exception) {}

        // 4. 일반적인 경로들
        val commonPaths = listOf(
            "/Library/Java/JavaVirtualMachines",
            "/usr/lib/jvm",
            "/opt/homebrew/opt/openjdk"
        )
        for (path in commonPaths) {
            val dir = File(path)
            if (dir.exists()) {
                val jdk = dir.listFiles()?.filter { it.isDirectory }
                    ?.sortedDescending()?.firstOrNull()
                if (jdk != null) {
                    val home = File(jdk, "Contents/Home")
                    if (home.exists()) return home.absolutePath
                    return jdk.absolutePath
                }
            }
        }

        return ""
    }

    private fun detectPython(): String {
        // Windows는 python3가 아니라 python 명령이 일반적
        return if (isWindows) {
            findExecutable("python", "python3")
        } else {
            findExecutable("python3", "/usr/bin/python3", "/usr/local/bin/python3",
                "/opt/homebrew/bin/python3", "python")
        }
    }

    private fun detectKotlinc(): String {
        // 0. 지금 실행 중인 IDE에 번들된 Kotlin 플러그인의 kotlinc — 가장 신뢰할 수 있는 소스.
        //    (사용자 디렉토리의 IntelliJIdea* 잔재는 구버전 찌꺼기로 깨진 경우가 있음)
        try {
            val kotlinPlugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(
                com.intellij.openapi.extensions.PluginId.getId("org.jetbrains.kotlin"))
            val bin = kotlinPlugin?.pluginPath?.toFile()
                ?.let { File(it, "kotlinc/bin/" + if (isWindows) "kotlinc.bat" else "kotlinc") }
            if (bin != null && bin.exists()) return bin.absolutePath
        } catch (_: Throwable) {}

        // 1. PATH나 일반적인 설치 경로
        val found = findExecutable("kotlinc", "/usr/local/bin/kotlinc", "/opt/homebrew/bin/kotlinc")
        if (found.isNotBlank()) return found

        // 2. SDKMAN
        val sdkmanPath = "${System.getProperty("user.home")}/.sdkman/candidates/kotlin/current/bin/kotlinc"
        if (File(sdkmanPath).exists()) return sdkmanPath

        // 3. IntelliJ 번들 Kotlin 플러그인의 kotlinc
        try {
            val intellijPaths = listOf(
                "/Applications/IntelliJ IDEA.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc",
                "/Applications/IntelliJ IDEA CE.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc",
                "${System.getProperty("user.home")}/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0",
                "${System.getProperty("user.home")}/Library/Application Support/JetBrains/Toolbox/apps/IDEA-C/ch-0"
            )
            for (path in intellijPaths) {
                if (path.contains("Toolbox")) {
                    val toolboxDir = File(path)
                    if (toolboxDir.exists()) {
                        val kotlinc = toolboxDir.listFiles()
                            ?.filter { it.isDirectory }
                            ?.sortedDescending()
                            ?.map { File(it, "IntelliJ IDEA.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc") }
                            ?.firstOrNull { it.exists() }
                        if (kotlinc != null) return kotlinc.absolutePath
                    }
                } else if (File(path).exists()) {
                    return path
                }
            }

            // 4. 사용자별 JetBrains 플러그인 디렉토리 (~/Library/Application Support/JetBrains/IntelliJIdea*)
            val jetbrainsDir = File("${System.getProperty("user.home")}/Library/Application Support/JetBrains")
            if (jetbrainsDir.exists()) {
                val kotlinc = jetbrainsDir.listFiles()
                    ?.filter { it.isDirectory && it.name.startsWith("IntelliJIdea") }
                    ?.sortedDescending()
                    ?.map { File(it, "plugins/Kotlin/kotlinc/bin/kotlinc") }
                    ?.firstOrNull { it.exists() }
                if (kotlinc != null) return kotlinc.absolutePath
            }
        } catch (_: Exception) {}

        return ""
    }

    private fun detectNode(): String {
        // nvm 환경 지원
        val nvmDir = "${System.getProperty("user.home")}/.nvm/versions/node"
        if (File(nvmDir).exists()) {
            val latest = File(nvmDir).listFiles()?.filter { it.isDirectory }
                ?.sortedDescending()?.firstOrNull()
            if (latest != null) {
                val nodeBin = File(latest, "bin/node")
                if (nodeBin.exists()) return nodeBin.absolutePath
            }
        }
        return findExecutable("node", "/usr/local/bin/node", "/opt/homebrew/bin/node")
    }

    private val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")

    private fun findExecutable(vararg candidates: String): String {
        for (candidate in candidates) {
            // 절대 경로 후보 (Unix: /usr/..., Windows: C:\...)
            if (candidate.startsWith("/") || candidate.matches(Regex("^[A-Za-z]:[\\\\/].*"))) {
                if (File(candidate).exists()) return candidate
                // Windows 실행파일은 .exe 확장자 보정
                if (isWindows && !candidate.endsWith(".exe")) {
                    val withExe = File("$candidate.exe")
                    if (withExe.exists()) return withExe.absolutePath
                }
                continue
            }

            // PATH에서 찾기: Windows는 where, 그 외는 which.
            // (Git Bash의 which는 ProcessBuilder가 실행 못 하는 MSYS 경로 /c/... 를 반환하므로 where 사용)
            try {
                val locator = if (isWindows) "where" else "which"
                val proc = ProcessBuilder(locator, candidate).start()
                val output = proc.inputStream.bufferedReader().readText()
                val ok = proc.waitFor() == 0
                // where는 여러 줄을 반환할 수 있으므로 실제로 존재하는 첫 경로를 선택
                val resolved = output.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() && File(it).exists() }
                if (ok && resolved != null) return resolved
            } catch (_: Exception) {}
        }
        // 못 찾으면 빈 문자열: 호출부의 not-found 분기가 도구별 설치 안내를 띄울 수 있게 함.
        // (이름 그대로 반환하면 ProcessBuilder가 CreateProcess error=2 같은 원시 오류를 노출)
        return ""
    }

    fun getDetectedPaths(): Map<String, String> = mapOf(
        "JAVA_HOME" to javaHome,
        "javac" to javacPath,
        "java" to javaPath,
        "python" to pythonPath,
        "node" to nodePath,
        "g++" to gppPath,
        "kotlinc" to kotlincPath,
        "rustc" to rustcPath,
        "go" to goPath,
        "ruby" to rubyPath
    )

    // ─── 언어별 컴파일 & 실행 ───

    private fun createTempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "ctk_run_${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun runJava(code: String, input: String, dir: File, timeout: Long): RunResult {
        if (javacPath.isBlank() || javaPath.isBlank()) {
            return RunResult(output = "", error = I18n.t(
                "JDK를 찾을 수 없습니다.\nJAVA_HOME을 설정하거나 JDK를 설치하세요.",
                "JDK not found.\nPlease set JAVA_HOME or install JDK."
            ), exitCode = -1)
        }

        // SEPARATOR로 분리된 경우 (프로그래머스 래퍼)
        if (code.contains("///MAIN_SEPARATOR///")) {
            val parts = code.split("///MAIN_SEPARATOR///")
            val solutionCode = parts[0].trim()
            val mainCode = parts[1].trim()

            val solutionFile = File(dir, "Solution.java")
            val mainFile = File(dir, "Main.java")
            solutionFile.writeText(solutionCode, StandardCharsets.UTF_8)
            mainFile.writeText(mainCode, StandardCharsets.UTF_8)

            val compile = executeProcess(
                javacCommand(solutionFile, mainFile),
                dir, "", COMPILE_TIMEOUT_SECONDS
            )
            if (compile.exitCode != 0) {
                return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode, compileError = true)
            }
            return executeProcess(javaCommand("-cp", dir.absolutePath, "Main"), dir, input, timeout)
        }

        val className = detectJavaClassName(code)
        val classNames = Regex("class\\s+(\\w+)").findAll(code).map { it.groupValues[1] }.toList()

        if (classNames.size > 1 && classNames.contains("Main") && classNames.contains("Solution")) {
            val solutionCode = extractJavaClass(code, "Solution")
            val mainCode = extractJavaClass(code, "Main")

            val solutionFile = File(dir, "Solution.java")
            val mainFile = File(dir, "Main.java")
            solutionFile.writeText(solutionCode, StandardCharsets.UTF_8)
            mainFile.writeText(mainCode, StandardCharsets.UTF_8)

            val compile = executeProcess(
                javacCommand(solutionFile, mainFile),
                dir, "", COMPILE_TIMEOUT_SECONDS
            )
            if (compile.exitCode != 0) {
                return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode, compileError = true)
            }
            return executeProcess(javaCommand("-cp", dir.absolutePath, "Main"), dir, input, timeout)
        }

        val sourceFile = File(dir, "$className.java")
        sourceFile.writeText(code, StandardCharsets.UTF_8)

        val compile = executeProcess(javacCommand(sourceFile), dir, "", COMPILE_TIMEOUT_SECONDS)
        if (compile.exitCode != 0) {
            return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode, compileError = true)
        }

        return executeProcess(javaCommand("-cp", dir.absolutePath, className), dir, input, timeout)
    }

    private fun javacCommand(vararg sourceFiles: File): List<String> {
        return listOf(javacPath, "-encoding", "UTF-8") + sourceFiles.map { it.absolutePath }
    }

    /**
     * java 실행 커맨드. 자식 JVM의 표준 입출력 인코딩을 UTF-8로 강제한다.
     * Windows 기본 코드페이지(MS949 등)로 인해 한글 결과가 깨지는 것을 방지 (이슈 #4).
     * stdout/stderr.encoding은 JDK 18+에서 동작하고, 하위 버전에서는 무시되어 무해하다.
     */
    private fun javaCommand(vararg args: String): List<String> {
        return listOf(
            javaPath,
            "-Dfile.encoding=UTF-8",
            "-Dstdout.encoding=UTF-8",
            "-Dstderr.encoding=UTF-8"
        ) + args
    }

    private fun extractJavaClass(code: String, className: String): String {
        val pattern = Regex("(class\\s+$className\\s*\\{)", RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(code) ?: return code

        val start = match.range.first
        var braceCount = 0
        var end = start

        for (i in match.range.first until code.length) {
            if (code[i] == '{') braceCount++
            if (code[i] == '}') braceCount--
            if (braceCount == 0) {
                end = i + 1
                break
            }
        }

        // import 문도 포함
        val imports = code.lines()
            .filter { it.trimStart().startsWith("import ") }
            .joinToString("\n")

        return if (imports.isNotBlank()) {
            "$imports\n\n${code.substring(start, end)}"
        } else {
            code.substring(start, end)
        }
    }

    private fun runPython(code: String, input: String, dir: File, timeout: Long): RunResult {
        if (pythonPath.isBlank()) {
            return RunResult(output = "", error = I18n.t(
                "Python을 찾을 수 없습니다.\npython3를 설치하세요.",
                "Python not found.\nPlease install python3."
            ), exitCode = -1)
        }
        val sourceFile = File(dir, "solution.py")
        sourceFile.writeText(code)
        return executeProcess(listOf(pythonPath, sourceFile.absolutePath), dir, input, timeout)
    }

    private fun runCpp(code: String, input: String, dir: File, timeout: Long): RunResult {
        if (gppPath.isBlank()) {
            val message = if (isWindows) I18n.t(
                "g++를 찾을 수 없습니다.\nMinGW-w64(또는 MSYS2)를 설치하고 bin 폴더를 PATH에 등록하세요.\n" +
                    "이미 설치했다면 IDE를 완전히 재시작하세요.\n" +
                    "(PATH 변경은 재시작 전의 IDE에 반영되지 않으며, JetBrains Toolbox 사용 시 Toolbox도 재시작 필요)",
                "g++ not found.\nInstall MinGW-w64 (or MSYS2) and add its bin folder to PATH.\n" +
                    "If already installed, fully restart the IDE.\n" +
                    "(PATH changes are not picked up by an already-running IDE; restart JetBrains Toolbox too if you use it)"
            ) else I18n.t(
                "g++를 찾을 수 없습니다.\nXcode Command Line Tools를 설치하세요:\nxcode-select --install",
                "g++ not found.\nPlease install Xcode Command Line Tools:\nxcode-select --install"
            )
            return RunResult(output = "", error = message, exitCode = -1)
        }
        val sourceFile = File(dir, "solution.cpp")
        val outputFile = File(dir, "solution")
        sourceFile.writeText(code)

        val compile = executeProcess(
            listOf(gppPath, "-std=c++17", "-O2", "-o", outputFile.absolutePath, sourceFile.absolutePath),
            dir, "", COMPILE_TIMEOUT_SECONDS
        )
        if (compile.exitCode != 0) {
            return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode, compileError = true)
        }

        return executeProcess(listOf(outputFile.absolutePath), dir, input, timeout)
    }

    private fun runKotlin(code: String, input: String, dir: File, timeout: Long): RunResult {
        if (kotlincPath.isBlank()) {
            return RunResult(output = "", error = I18n.t(
                "kotlinc를 찾을 수 없습니다.\nbrew install kotlin 으로 설치하세요.",
                "kotlinc not found.\nPlease install via: brew install kotlin"
            ), exitCode = -1)
        }
        val sourceFile = File(dir, "Solution.kt")
        sourceFile.writeText(code)

        val jarFile = File(dir, "solution.jar")
        // -J-Dfile.encoding: javac의 -encoding UTF-8과 동일한 목적 (이슈 #2).
        // Windows에서 kotlinc가 구버전 JDK(<18)로 실행되면 MS949로 소스를 읽어 한글이 깨지는 것을 방지.
        val compile = executeProcess(
            listOf(kotlincPath, "-J-Dfile.encoding=UTF-8", sourceFile.absolutePath, "-include-runtime", "-d", jarFile.absolutePath),
            dir, "", COMPILE_TIMEOUT_SECONDS
        )
        if (compile.exitCode != 0) {
            return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode, compileError = true)
        }

        return executeProcess(javaCommand("-jar", jarFile.absolutePath), dir, input, timeout)
    }

    private fun runJavaScript(code: String, input: String, dir: File, timeout: Long): RunResult {
        if (nodePath.isBlank()) {
            return RunResult(output = "", error = I18n.t(
                "Node.js를 찾을 수 없습니다.\nbrew install node 또는 nvm으로 설치하세요.",
                "Node.js not found.\nPlease install via: brew install node"
            ), exitCode = -1)
        }
        val sourceFile = File(dir, "solution.js")
        sourceFile.writeText(code)
        return executeProcess(listOf(nodePath, sourceFile.absolutePath), dir, input, timeout)
    }

    private fun runRust(code: String, input: String, dir: File, timeout: Long): RunResult {
        if (rustcPath.isBlank()) {
            return RunResult(output = "", error = I18n.t(
                "rustc를 찾을 수 없습니다.\nhttps://rustup.rs 에서 Rust를 설치하세요.",
                "rustc not found.\nPlease install Rust via https://rustup.rs"
            ), exitCode = -1)
        }
        val sourceFile = File(dir, "solution.rs")
        // rustc/go build는 g++(MinGW)와 달리 Windows에서 .exe를 자동으로 붙이지 않음
        val outputFile = File(dir, if (isWindows) "solution.exe" else "solution")
        sourceFile.writeText(code, StandardCharsets.UTF_8)

        val compile = executeProcess(
            listOf(rustcPath, "-O", "--edition", "2021", "-o", outputFile.absolutePath, sourceFile.absolutePath),
            dir, "", COMPILE_TIMEOUT_SECONDS
        )
        if (compile.exitCode != 0) {
            return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode, compileError = true)
        }

        return executeProcess(listOf(outputFile.absolutePath), dir, input, timeout)
    }

    private fun runGo(code: String, input: String, dir: File, timeout: Long): RunResult {
        if (goPath.isBlank()) {
            return RunResult(output = "", error = I18n.t(
                "Go를 찾을 수 없습니다.\nhttps://go.dev/dl 에서 Go를 설치하세요.",
                "Go not found.\nPlease install Go via https://go.dev/dl"
            ), exitCode = -1)
        }
        val sourceFile = File(dir, "solution.go")
        val outputFile = File(dir, if (isWindows) "solution.exe" else "solution")
        sourceFile.writeText(code, StandardCharsets.UTF_8)

        val compile = executeProcess(
            listOf(goPath, "build", "-o", outputFile.absolutePath, sourceFile.absolutePath),
            dir, "", COMPILE_TIMEOUT_SECONDS
        )
        if (compile.exitCode != 0) {
            return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode, compileError = true)
        }

        return executeProcess(listOf(outputFile.absolutePath), dir, input, timeout)
    }

    private fun runRuby(code: String, input: String, dir: File, timeout: Long): RunResult {
        if (rubyPath.isBlank()) {
            return RunResult(output = "", error = I18n.t(
                "Ruby를 찾을 수 없습니다.\nbrew install ruby 또는 rbenv로 설치하세요.",
                "Ruby not found.\nPlease install via: brew install ruby"
            ), exitCode = -1)
        }
        val sourceFile = File(dir, "solution.rb")
        sourceFile.writeText(code, StandardCharsets.UTF_8)
        return executeProcess(listOf(rubyPath, sourceFile.absolutePath), dir, input, timeout)
    }

    private fun executeProcess(
        command: List<String>,
        dir: File,
        input: String,
        timeout: Long
    ): RunResult {
        val process = try {
            ProcessBuilder(command)
                .directory(dir)
                .redirectErrorStream(false)
                .start()
        } catch (e: java.io.IOException) {
            // 실행 파일이 PATH에 없거나 접근 불가 (예: CreateProcess error=2)
            return RunResult(output = "", error = I18n.t(
                "'${command.first()}' 을(를) 실행할 수 없습니다.\n설치 후 PATH에 등록하고 IDE를 완전히 재시작하세요.",
                "Failed to run '${command.first()}'.\nInstall it, add it to PATH, then fully restart the IDE."
            ), exitCode = -1)
        }

        // 입력이 없어도 stdin을 닫아 EOF를 전달해야 함.
        // 닫지 않으면 stdin을 읽는 코드가 타임아웃까지 블로킹되어 허위 시간 초과가 발생하고 파이프 FD가 누수됨.
        //
        // stdin 쓰기는 별도 스레드로 (대량 입력 대응, 이슈 #36):
        // - 자식이 입력을 다 읽지 않고 종료하면(예: 첫 토큰만 읽는 프로그램) Broken pipe가
        //   나는데, 이는 정상 시나리오이므로 실행을 실패로 만들지 않는다.
        // - 자식이 입력을 안 읽으면 파이프 버퍼(~64KB)가 차서 쓰기가 블로킹되는데,
        //   메인 흐름에서 쓰면 waitFor 타임아웃 자체가 무력화된다.
        if (input.isNotBlank()) {
            Thread {
                try {
                    process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(input) }
                } catch (_: java.io.IOException) {
                    // 자식이 stdin을 다 읽지 않고 종료 (Broken pipe) — 무시
                }
            }.apply { isDaemon = true; start() }
        } else {
            process.outputStream.close()
        }

        // stdout/stderr는 실행과 동시에 수집 (대량 출력 대응, 이슈 #36):
        // 종료 후에 읽으면 자식이 파이프 버퍼를 채우고 블로킹되어
        // 대량 출력 프로그램이 허위 시간 초과가 된다.
        val stdoutRef = java.util.concurrent.atomic.AtomicReference("")
        val stderrRef = java.util.concurrent.atomic.AtomicReference("")
        val stdoutReader = Thread {
            runCatching { stdoutRef.set(process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }) }
        }.apply { isDaemon = true; start() }
        val stderrReader = Thread {
            runCatching { stderrRef.set(process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }) }
        }.apply { isDaemon = true; start() }

        // 메모리 폴링 스레드 시작
        val peakMemory = AtomicLong(0)
        val pid = process.pid()
        val isLinux = System.getProperty("os.name").lowercase().contains("linux")
        val memoryPoller = Thread {
            try {
                while (process.isAlive) {
                    val mem = getProcessMemoryKB(pid)
                    if (mem > 0) {
                        peakMemory.updateAndGet { prev -> maxOf(prev, mem) }
                    }
                    Thread.sleep(50)
                }
                // Linux: 프로세스 종료 직전 VmHWM (커널이 기록한 peak RSS) 읽기
                if (isLinux) {
                    val hwm = getLinuxVmHWM(pid)
                    if (hwm > 0) peakMemory.updateAndGet { prev -> maxOf(prev, hwm) }
                }
            } catch (_: InterruptedException) {}
        }.apply {
            isDaemon = true
            start()
        }

        val startTime = System.nanoTime()
        val completed = process.waitFor(timeout, TimeUnit.SECONDS)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        // 프로세스 종료 후 OS별 peak 메모리 조회
        if (isLinux) {
            val hwm = getLinuxVmHWM(pid)
            if (hwm > 0) peakMemory.updateAndGet { prev -> maxOf(prev, hwm) }
        } else if (System.getProperty("os.name").lowercase().contains("win")) {
            val peak = getWindowsPeakWorkingSetKB(pid)
            if (peak > 0) peakMemory.updateAndGet { prev -> maxOf(prev, peak) }
        }

        memoryPoller.interrupt()

        if (!completed) {
            process.destroyForcibly()
            return RunResult(output = "", error = I18n.t("시간 초과 (${timeout}초)", "Time Limit Exceeded (${timeout}s)"), exitCode = -1, timedOut = true, executionTimeMs = elapsedMs, peakMemoryKB = peakMemory.get())
        }

        // 리더 스레드가 남은 버퍼를 마저 읽을 시간을 줌 (프로세스는 이미 종료됨)
        stdoutReader.join(5000)
        stderrReader.join(5000)
        val output = stdoutRef.get().trimEnd()
        val error = stderrRef.get().trimEnd()

        return RunResult(output = output, error = error, exitCode = process.exitValue(), executionTimeMs = elapsedMs, peakMemoryKB = peakMemory.get())
    }

    private fun getLinuxVmHWM(pid: Long): Long {
        return try {
            val statusFile = File("/proc/$pid/status")
            if (statusFile.exists()) {
                val line = statusFile.readLines().find { it.startsWith("VmHWM:") }
                line?.replace("VmHWM:", "")?.replace("kB", "")?.trim()?.toLongOrNull() ?: 0
            } else 0
        } catch (_: Exception) { 0 }
    }

    private fun getWindowsPeakWorkingSetKB(pid: Long): Long {
        return try {
            // PowerShell로 OS가 기록한 PeakWorkingSet64 (bytes) 조회
            val proc = ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "(Get-Process -Id $pid -ErrorAction SilentlyContinue).PeakWorkingSet64"
            ).start()
            val result = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor(2, TimeUnit.SECONDS)
            val bytes = result.toLongOrNull() ?: 0
            bytes / 1024 // bytes → KB
        } catch (_: Exception) { 0 }
    }

    private fun getProcessMemoryKB(pid: Long): Long {
        val os = System.getProperty("os.name").lowercase()
        return try {
            when {
                os.contains("mac") || os.contains("darwin") -> {
                    // macOS: ps -o rss= -p <pid>
                    val proc = ProcessBuilder("ps", "-o", "rss=", "-p", pid.toString()).start()
                    val result = proc.inputStream.bufferedReader().readText().trim()
                    proc.waitFor(1, TimeUnit.SECONDS)
                    result.toLongOrNull() ?: 0
                }
                os.contains("linux") -> {
                    // Linux: /proc/<pid>/status → VmRSS
                    val statusFile = File("/proc/$pid/status")
                    if (statusFile.exists()) {
                        val line = statusFile.readLines().find { it.startsWith("VmRSS:") }
                        line?.replace("VmRSS:", "")?.replace("kB", "")?.trim()?.toLongOrNull() ?: 0
                    } else {
                        // fallback: ps
                        val proc = ProcessBuilder("ps", "-o", "rss=", "-p", pid.toString()).start()
                        val result = proc.inputStream.bufferedReader().readText().trim()
                        proc.waitFor(1, TimeUnit.SECONDS)
                        result.toLongOrNull() ?: 0
                    }
                }
                os.contains("win") -> {
                    // Windows: PowerShell로 PeakWorkingSet64 조회 (OS가 기록한 peak, bytes)
                    val proc = ProcessBuilder(
                        "powershell", "-NoProfile", "-Command",
                        "(Get-Process -Id $pid -ErrorAction SilentlyContinue).PeakWorkingSet64"
                    ).start()
                    val result = proc.inputStream.bufferedReader().readText().trim()
                    proc.waitFor(1, TimeUnit.SECONDS)
                    (result.toLongOrNull() ?: 0) / 1024 // bytes → KB
                }
                else -> 0
            }
        } catch (_: Exception) { 0 }
    }
}
