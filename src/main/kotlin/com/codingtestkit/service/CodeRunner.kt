package com.codingtestkit.service

import com.codingtestkit.model.Language
import com.codingtestkit.model.TestCase
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object CodeRunner {

    data class RunResult(
        val output: String,
        val error: String,
        val exitCode: Int,
        val timedOut: Boolean = false,
        val executionTimeMs: Long = 0,
        val peakMemoryKB: Long = 0
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
                Language.JAVASCRIPT -> runJavaScript(code, testCase.input, tempDir, timeoutSeconds)
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
        }

        val tempDir = createTempDir()
        return try {
            when (language) {
                Language.JAVA -> runJava(wrappedCode, "", tempDir, timeoutSeconds)
                Language.PYTHON -> runPython(wrappedCode, "", tempDir, timeoutSeconds)
                Language.CPP -> runCpp(wrappedCode, "", tempDir, timeoutSeconds)
                Language.KOTLIN -> runKotlin(wrappedCode, "", tempDir, timeoutSeconds)
                Language.JAVASCRIPT -> runJavaScript(wrappedCode, "", tempDir, timeoutSeconds)
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

        return """
import sys as _sys

$code

# 사용자 print()를 stderr로 리다이렉트
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
        val hasInclude = code.contains("#include")
        val includes = if (hasInclude) "" else """
#include <iostream>
#include <vector>
#include <string>
using namespace std;
""".trimIndent()

        val callExpr = if (hasClass) {
            "    Solution sol;\n    auto _result = sol.$methodName($args);"
        } else {
            "    auto _result = $methodName($args);"
        }

        return """
$includes
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

    // ─── 도구 경로 자동 감지 ───

    private val javaHome: String by lazy { detectJavaHome() }
    private val javacPath: String by lazy { findExecutable("javac", "$javaHome/bin/javac") }
    private val javaPath: String by lazy { findExecutable("java", "$javaHome/bin/java") }
    private val pythonPath: String by lazy { detectPython() }
    private val gppPath: String by lazy { findExecutable("g++", "/usr/bin/g++", "/usr/local/bin/g++", "/opt/homebrew/bin/g++") }
    private val kotlincPath: String by lazy { detectKotlinc() }
    private val nodePath: String by lazy { detectNode() }

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
        "node" to nodePath,
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

            File(dir, "Solution.java").writeText(solutionCode)
            File(dir, "Main.java").writeText(mainCode)

            val compile = executeProcess(
                listOf(javacPath, File(dir, "Solution.java").absolutePath, File(dir, "Main.java").absolutePath),
                dir, "", timeout
            )
            if (compile.exitCode != 0) {
                return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode)
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
                return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode)
            }
            return executeProcess(listOf(javaPath, "-cp", dir.absolutePath, "Main"), dir, input, timeout)
        }

        val sourceFile = File(dir, "$className.java")
        sourceFile.writeText(code)

        val compile = executeProcess(listOf(javacPath, sourceFile.absolutePath), dir, "", timeout)
        if (compile.exitCode != 0) {
            return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode)
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
            return RunResult(output = "", error = I18n.t(
                "g++를 찾을 수 없습니다.\nXcode Command Line Tools를 설치하세요:\nxcode-select --install",
                "g++ not found.\nPlease install Xcode Command Line Tools:\nxcode-select --install"
            ), exitCode = -1)
        }
        val sourceFile = File(dir, "solution.cpp")
        val outputFile = File(dir, "solution")
        sourceFile.writeText(code)

        val compile = executeProcess(
            listOf(gppPath, "-std=c++17", "-O2", "-o", outputFile.absolutePath, sourceFile.absolutePath),
            dir, "", timeout
        )
        if (compile.exitCode != 0) {
            return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode)
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
        val compile = executeProcess(
            listOf(kotlincPath, sourceFile.absolutePath, "-include-runtime", "-d", jarFile.absolutePath),
            dir, "", timeout * 2
        )
        if (compile.exitCode != 0) {
            return RunResult(output = "", error = I18n.t("컴파일 에러", "Compile error") + ":\n${compile.error}", exitCode = compile.exitCode)
        }

        return executeProcess(listOf(javaPath, "-jar", jarFile.absolutePath), dir, input, timeout)
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

        val output = process.inputStream.bufferedReader().readText().trimEnd()
        val error = process.errorStream.bufferedReader().readText().trimEnd()

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
