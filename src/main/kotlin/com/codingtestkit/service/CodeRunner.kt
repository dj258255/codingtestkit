package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.codingtestkit.model.TestCase
import java.io.File
import java.util.concurrent.TimeUnit

object CodeRunner {

    data class RunResult(
        val output: String,
        val error: String,
        val exitCode: Int,
        val timedOut: Boolean = false
    )

    /**
     * 백준 방식: stdin으로 입력을 넣고 stdout 결과를 비교
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
        }

        val tempDir = createTempDir()
        return try {
            when (language) {
                Language.JAVA -> runJava(wrappedCode, "", tempDir, timeoutSeconds)
                Language.PYTHON -> runPython(wrappedCode, "", tempDir, timeoutSeconds)
                Language.CPP -> runCpp(wrappedCode, "", tempDir, timeoutSeconds)
                Language.KOTLIN -> runKotlin(wrappedCode, "", tempDir, timeoutSeconds)
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
        }
    }

    /**
     * Java 클래스명 감지 우선순위:
     * 1. public class 선언 (백준: Main, SWEA: Solution)
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

    private fun wrapJava(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ")
        // Solution 클래스에서 solution 메서드 호출
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
${importBlock}class Main {
    public static void main(String[] args) {
        Solution sol = new Solution();
        System.out.println(sol.solution($args));
    }
}
""".trimIndent()
    }

    private fun wrapPython(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ")
        return """
$code

print(solution($args))
""".trimIndent()
    }

    private fun wrapCpp(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ")
        val hasInclude = code.contains("#include")
        val includes = if (hasInclude) "" else """
#include <iostream>
#include <vector>
#include <string>
using namespace std;
""".trimIndent()

        return """
$includes
$code

int main() {
    auto result = solution($args);
    cout << result << endl;
    return 0;
}
""".trimIndent()
    }

    private fun wrapKotlin(code: String, inputValues: List<String>, @Suppress("UNUSED_PARAMETER") paramNames: List<String>): String {
        val args = inputValues.joinToString(", ")
        return """
$code

fun main() {
    println(solution($args))
}
""".trimIndent()
    }

    // ─── 도구 경로 자동 감지 ───

    private val javaHome: String by lazy { detectJavaHome() }
    private val javacPath: String by lazy { findExecutable("javac", "$javaHome/bin/javac") }
    private val javaPath: String by lazy { findExecutable("java", "$javaHome/bin/java") }
    private val pythonPath: String by lazy { detectPython() }
    private val gppPath: String by lazy { findExecutable("g++", "/usr/bin/g++", "/usr/local/bin/g++", "/opt/homebrew/bin/g++") }
    private val kotlincPath: String by lazy { detectKotlinc() }

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
        return findExecutable("python3", "/usr/bin/python3", "/usr/local/bin/python3",
            "/opt/homebrew/bin/python3", "python")
    }

    private fun detectKotlinc(): String {
        // 1. PATH나 일반적인 설치 경로
        val found = findExecutable("kotlinc", "/usr/local/bin/kotlinc", "/opt/homebrew/bin/kotlinc")
        if (found != "kotlinc" && File(found).exists()) return found

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
        } catch (_: Exception) {}

        return ""
    }

    private fun findExecutable(vararg candidates: String): String {
        for (candidate in candidates) {
            // 절대 경로면 파일 존재 확인
            if (candidate.startsWith("/") && File(candidate).exists()) return candidate

            // PATH에서 찾기
            try {
                val proc = ProcessBuilder("which", candidate).start()
                val result = proc.inputStream.bufferedReader().readText().trim()
                if (proc.waitFor() == 0 && result.isNotBlank()) return result
            } catch (_: Exception) {}
        }
        return candidates.first() // fallback: 원래 이름 그대로
    }

    fun getDetectedPaths(): Map<String, String> = mapOf(
        "JAVA_HOME" to javaHome,
        "javac" to javacPath,
        "java" to javaPath,
        "python" to pythonPath,
        "g++" to gppPath,
        "kotlinc" to kotlincPath
    )

    // ─── 언어별 컴파일 & 실행 ───

    private fun createTempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "ctk_run_${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun runJava(code: String, input: String, dir: File, timeout: Long): RunResult {
        if (javacPath.isBlank() || javaPath.isBlank()) {
            return RunResult(output = "", error = "JDK를 찾을 수 없습니다.\nJAVA_HOME을 설정하거나 JDK를 설치하세요.", exitCode = -1)
        }

        // SEPARATOR로 분리된 경우 (프로그래머스 래퍼)
        if (code.contains("///MAIN_SEPARATOR///")) {
            val parts = code.split("///MAIN_SEPARATOR///")
            val solutionCode = parts[0].trim()
            val mainCode = parts[1].trim()

            File(dir, "Solution.java").writeText(solutionCode)
            File(dir, "Main.java").writeText(mainCode)

            val compile = executeProcess(
                listOf(javacPath, File(dir, "Solution.java").absolutePath, File(dir, "Main.java").absolutePath),
                dir, "", timeout
            )
            if (compile.exitCode != 0) {
                return RunResult(output = "", error = "컴파일 에러:\n${compile.error}", exitCode = compile.exitCode)
            }
            return executeProcess(listOf(javaPath, "-cp", dir.absolutePath, "Main"), dir, input, timeout)
        }

        val className = detectJavaClassName(code)
        val classNames = Regex("class\\s+(\\w+)").findAll(code).map { it.groupValues[1] }.toList()

        if (classNames.size > 1 && classNames.contains("Main") && classNames.contains("Solution")) {
            val solutionCode = extractJavaClass(code, "Solution")
            val mainCode = extractJavaClass(code, "Main")

            File(dir, "Solution.java").writeText(solutionCode)
            File(dir, "Main.java").writeText(mainCode)

            val compile = executeProcess(
                listOf(javacPath, File(dir, "Solution.java").absolutePath, File(dir, "Main.java").absolutePath),
                dir, "", timeout
            )
            if (compile.exitCode != 0) {
                return RunResult(output = "", error = "컴파일 에러:\n${compile.error}", exitCode = compile.exitCode)
            }
            return executeProcess(listOf(javaPath, "-cp", dir.absolutePath, "Main"), dir, input, timeout)
        }

        val sourceFile = File(dir, "$className.java")
        sourceFile.writeText(code)

        val compile = executeProcess(listOf(javacPath, sourceFile.absolutePath), dir, "", timeout)
        if (compile.exitCode != 0) {
            return RunResult(output = "", error = "컴파일 에러:\n${compile.error}", exitCode = compile.exitCode)
        }

        return executeProcess(listOf(javaPath, "-cp", dir.absolutePath, className), dir, input, timeout)
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
            return RunResult(output = "", error = "Python을 찾을 수 없습니다.\npython3를 설치하세요.", exitCode = -1)
        }
        val sourceFile = File(dir, "solution.py")
        sourceFile.writeText(code)
        return executeProcess(listOf(pythonPath, sourceFile.absolutePath), dir, input, timeout)
    }

    private fun runCpp(code: String, input: String, dir: File, timeout: Long): RunResult {
        if (gppPath.isBlank()) {
            return RunResult(output = "", error = "g++를 찾을 수 없습니다.\nXcode Command Line Tools를 설치하세요:\nxcode-select --install", exitCode = -1)
        }
        val sourceFile = File(dir, "solution.cpp")
        val outputFile = File(dir, "solution")
        sourceFile.writeText(code)

        val compile = executeProcess(
            listOf(gppPath, "-std=c++17", "-O2", "-o", outputFile.absolutePath, sourceFile.absolutePath),
            dir, "", timeout
        )
        if (compile.exitCode != 0) {
            return RunResult(output = "", error = "컴파일 에러:\n${compile.error}", exitCode = compile.exitCode)
        }

        return executeProcess(listOf(outputFile.absolutePath), dir, input, timeout)
    }

    private fun runKotlin(code: String, input: String, dir: File, timeout: Long): RunResult {
        if (kotlincPath.isBlank()) {
            return RunResult(output = "", error = "kotlinc를 찾을 수 없습니다.\nbrew install kotlin 으로 설치하세요.", exitCode = -1)
        }
        val sourceFile = File(dir, "Solution.kt")
        sourceFile.writeText(code)

        val jarFile = File(dir, "solution.jar")
        val compile = executeProcess(
            listOf(kotlincPath, sourceFile.absolutePath, "-include-runtime", "-d", jarFile.absolutePath),
            dir, "", timeout * 2
        )
        if (compile.exitCode != 0) {
            return RunResult(output = "", error = "컴파일 에러:\n${compile.error}", exitCode = compile.exitCode)
        }

        return executeProcess(listOf(javaPath, "-jar", jarFile.absolutePath), dir, input, timeout)
    }

    private fun executeProcess(
        command: List<String>,
        dir: File,
        input: String,
        timeout: Long
    ): RunResult {
        val process = ProcessBuilder(command)
            .directory(dir)
            .redirectErrorStream(false)
            .start()

        if (input.isNotBlank()) {
            process.outputStream.bufferedWriter().use { it.write(input) }
        }

        val completed = process.waitFor(timeout, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return RunResult(output = "", error = "시간 초과 (${timeout}초)", exitCode = -1, timedOut = true)
        }

        val output = process.inputStream.bufferedReader().readText().trimEnd()
        val error = process.errorStream.bufferedReader().readText().trimEnd()

        return RunResult(output = output, error = error, exitCode = process.exitValue())
    }
}
