package com.codingtestkit.model

data class Problem(
    val source: ProblemSource,
    val id: String,
    val title: String,
    val description: String,
    val testCases: MutableList<TestCase>,
    val timeLimit: String = "",
    val memoryLimit: String = "",
    val difficulty: String = "",
    val parameterNames: List<String> = emptyList(),
    val initialCode: String = "",
    val contestProbId: String = ""
)

enum class ProblemSource(
    val displayName: String,
    val folderName: String,
    val mainClassName: String
) {
    BAEKJOON("백준", "baekjoon", "Main"),
    PROGRAMMERS("프로그래머스", "programmers", "Solution"),
    SWEA("SWEA", "swea", "Solution");

    companion object {
        fun fromDisplayName(name: String): ProblemSource {
            return entries.first { it.displayName == name }
        }
    }
}

data class TestCase(
    var input: String,
    var expectedOutput: String,
    var actualOutput: String = "",
    var passed: Boolean? = null
)

data class CodeTemplate(
    var name: String = "",
    var language: String = "java",
    var code: String = "",
    var inputTemplate: String = ""
)

enum class Language(
    val displayName: String,
    val extension: String,
    val baekjoonId: Int,
    val sweaId: Int
) {
    JAVA("Java", "java", 93, 0),
    PYTHON("Python", "py", 28, 5),
    CPP("C++", "cpp", 84, 1),
    KOTLIN("Kotlin", "kt", 69, -1);

    fun defaultCode(source: ProblemSource): String {
        return when (this) {
            JAVA -> when (source) {
                ProblemSource.BAEKJOON -> """
                    |import java.util.Scanner;
                    |
                    |public class Main {
                    |    public static void main(String[] args) {
                    |        Scanner sc = new Scanner(System.in);
                    |
                    |    }
                    |}
                """.trimMargin()
                ProblemSource.PROGRAMMERS -> """
                    |class Solution {
                    |    public int solution() {
                    |        int answer = 0;
                    |        return answer;
                    |    }
                    |}
                """.trimMargin()
                ProblemSource.SWEA -> """
                    |import java.util.Scanner;
                    |
                    |public class Solution {
                    |    public static void main(String[] args) {
                    |        Scanner sc = new Scanner(System.in);
                    |        int T = sc.nextInt();
                    |        for (int tc = 1; tc <= T; tc++) {
                    |            System.out.println("#" + tc + " ");
                    |        }
                    |    }
                    |}
                """.trimMargin()
            }
            PYTHON -> when (source) {
                ProblemSource.BAEKJOON -> ""
                ProblemSource.PROGRAMMERS -> """
                    |def solution():
                    |    answer = 0
                    |    return answer
                """.trimMargin()
                ProblemSource.SWEA -> """
                    |T = int(input())
                    |for tc in range(1, T + 1):
                    |    print(f"#{tc}")
                """.trimMargin()
            }
            CPP -> when (source) {
                ProblemSource.BAEKJOON -> """
                    |#include <iostream>
                    |using namespace std;
                    |
                    |int main() {
                    |
                    |    return 0;
                    |}
                """.trimMargin()
                ProblemSource.PROGRAMMERS -> """
                    |#include <string>
                    |#include <vector>
                    |using namespace std;
                    |
                    |int solution() {
                    |    int answer = 0;
                    |    return answer;
                    |}
                """.trimMargin()
                ProblemSource.SWEA -> """
                    |#include <iostream>
                    |using namespace std;
                    |
                    |int main() {
                    |    int T;
                    |    cin >> T;
                    |    for (int tc = 1; tc <= T; tc++) {
                    |        cout << "#" << tc << " " << endl;
                    |    }
                    |    return 0;
                    |}
                """.trimMargin()
            }
            KOTLIN -> when (source) {
                ProblemSource.BAEKJOON -> """
                    |fun main() {
                    |
                    |}
                """.trimMargin()
                ProblemSource.PROGRAMMERS -> """
                    |fun solution(): Int {
                    |    var answer = 0
                    |    return answer
                    |}
                """.trimMargin()
                ProblemSource.SWEA -> """
                    |fun main() {
                    |    val T = readLine()!!.trim().toInt()
                    |    for (tc in 1..T) {
                    |        println("#${'$'}tc")
                    |    }
                    |}
                """.trimMargin()
            }
        }
    }
}
