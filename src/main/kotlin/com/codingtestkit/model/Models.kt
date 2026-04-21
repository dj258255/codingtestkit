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
    val englishName: String,
    val folderName: String,
    val mainClassName: String
) {
    PROGRAMMERS("프로그래머스", "Programmers", "programmers", "Solution"),
    SWEA("SWEA", "SWEA", "swea", "Solution"),
    LEETCODE("LeetCode", "LeetCode", "leetcode", "Solution"),
    CODEFORCES("Codeforces", "Codeforces", "codeforces", "Main");

    /** 현재 i18n 설정에 따른 표시 이름 */
    fun localizedName(): String = com.codingtestkit.service.I18n.t(displayName, englishName)

    companion object {
        fun fromDisplayName(name: String): ProblemSource {
            return entries.first { it.displayName == name || it.englishName == name }
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
    val sweaId: Int
) {
    JAVA("Java", "java", 0),
    PYTHON("Python", "py", 5),
    CPP("C++", "cpp", 1),
    KOTLIN("Kotlin", "kt", -1),
    JAVASCRIPT("JavaScript", "js", -1);

    fun defaultCode(source: ProblemSource): String {
        return when (this) {
            JAVA -> when (source) {
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
                ProblemSource.LEETCODE -> ""
                ProblemSource.CODEFORCES -> """
                    |import java.util.Scanner;
                    |
                    |public class Main {
                    |    public static void main(String[] args) {
                    |        Scanner sc = new Scanner(System.in);
                    |
                    |    }
                    |}
                """.trimMargin()
            }
            PYTHON -> when (source) {
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
                ProblemSource.LEETCODE -> ""
                ProblemSource.CODEFORCES -> ""
            }
            CPP -> when (source) {
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
                ProblemSource.LEETCODE -> ""
                ProblemSource.CODEFORCES -> """
                    |#include <iostream>
                    |using namespace std;
                    |
                    |int main() {
                    |
                    |    return 0;
                    |}
                """.trimMargin()
            }
            KOTLIN -> when (source) {
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
                ProblemSource.LEETCODE -> ""
                ProblemSource.CODEFORCES -> """
                    |fun main() {
                    |
                    |}
                """.trimMargin()
            }
            JAVASCRIPT -> when (source) {
                ProblemSource.PROGRAMMERS -> """
                    |function solution() {
                    |    var answer = 0;
                    |    return answer;
                    |}
                """.trimMargin()
                ProblemSource.SWEA -> ""
                ProblemSource.LEETCODE -> ""
                ProblemSource.CODEFORCES -> """
                    |const readline = require('readline');
                    |const rl = readline.createInterface({ input: process.stdin });
                    |const lines = [];
                    |rl.on('line', (line) => lines.push(line));
                    |rl.on('close', () => {
                    |
                    |});
                """.trimMargin()
            }
        }
    }
}
