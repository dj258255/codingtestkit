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
    // nullable: 기존 problem.json에 없는 필드라 Gson이 null을 넣음 (하위호환, 이슈 #36)
    val parameterTypes: List<String>? = null,
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
    JAVASCRIPT("JavaScript", "js", -1),
    RUST("Rust", "rs", -1),
    GO("Go", "go", -1),
    RUBY("Ruby", "rb", -1);

    /**
     * 해당 플랫폼에 코드를 제출할 수 있는 언어인지.
     * 로컬 테스트 실행은 언어·플랫폼 무관 항상 가능하며, 제출만 플랫폼 제약을 받는다.
     */
    fun isSubmittable(source: ProblemSource): Boolean = when (source) {
        ProblemSource.PROGRAMMERS -> this != RUST // 프로그래머스는 Rust 채점 미지원
        ProblemSource.SWEA -> sweaId >= 0
        ProblemSource.LEETCODE -> true
        ProblemSource.CODEFORCES -> true
    }

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
            RUST -> when (source) {
                ProblemSource.PROGRAMMERS -> """
                    |fn solution() -> i32 {
                    |    let answer = 0;
                    |    answer
                    |}
                """.trimMargin()
                ProblemSource.SWEA -> """
                    |use std::io::{self, BufRead};
                    |
                    |fn main() {
                    |    let stdin = io::stdin();
                    |    let mut lines = stdin.lock().lines();
                    |    let t: usize = lines.next().unwrap().unwrap().trim().parse().unwrap();
                    |    for tc in 1..=t {
                    |        println!("#{} ", tc);
                    |    }
                    |}
                """.trimMargin()
                ProblemSource.LEETCODE -> ""
                ProblemSource.CODEFORCES -> """
                    |use std::io::{self, Read};
                    |
                    |fn main() {
                    |    let mut input = String::new();
                    |    io::stdin().read_to_string(&mut input).unwrap();
                    |    let mut it = input.split_whitespace();
                    |
                    |}
                """.trimMargin()
            }
            GO -> when (source) {
                ProblemSource.PROGRAMMERS -> """
                    |func solution() int {
                    |    answer := 0
                    |    return answer
                    |}
                """.trimMargin()
                ProblemSource.SWEA -> """
                    |package main
                    |
                    |import (
                    |    "bufio"
                    |    "fmt"
                    |    "os"
                    |)
                    |
                    |func main() {
                    |    reader := bufio.NewReader(os.Stdin)
                    |    var T int
                    |    fmt.Fscan(reader, &T)
                    |    for tc := 1; tc <= T; tc++ {
                    |        fmt.Printf("#%d \n", tc)
                    |    }
                    |}
                """.trimMargin()
                ProblemSource.LEETCODE -> ""
                ProblemSource.CODEFORCES -> """
                    |package main
                    |
                    |import (
                    |    "bufio"
                    |    "fmt"
                    |    "os"
                    |)
                    |
                    |func main() {
                    |    reader := bufio.NewReader(os.Stdin)
                    |    writer := bufio.NewWriter(os.Stdout)
                    |    defer writer.Flush()
                    |
                    |    var n int
                    |    fmt.Fscan(reader, &n)
                    |    fmt.Fprintln(writer, n)
                    |}
                """.trimMargin()
            }
            RUBY -> when (source) {
                ProblemSource.PROGRAMMERS -> """
                    |def solution()
                    |    answer = 0
                    |    answer
                    |end
                """.trimMargin()
                ProblemSource.SWEA -> """
                    |T = gets.to_i
                    |(1..T).each do |tc|
                    |    puts "##{tc} "
                    |end
                """.trimMargin()
                ProblemSource.LEETCODE -> ""
                ProblemSource.CODEFORCES -> """
                    |lines = STDIN.read.split("\n")
                    |
                """.trimMargin()
            }
        }
    }
}
