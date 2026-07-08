package com.codingtestkit.ui

import com.codingtestkit.model.Language
import com.codingtestkit.service.I18n
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * 커스텀 체커 설정 다이얼로그 (이슈 #36).
 *
 * 복수 정답이 허용되는 문제를 위해, 문자열 비교 대신 사용자가 작성한
 * 체커 프로그램이 판정한다. 체커는 stdin으로 아래 3개 섹션을 받는다:
 *
 *   <테스트 입력>
 *   ===CTK===
 *   <사용자 출력>
 *   ===CTK===
 *   <예상 출력 (없으면 빈 값)>
 *
 * 체커가 stdout 첫 줄에 "OK"(또는 "AC")를 출력하면 통과, 그 외는 실패.
 * 체커는 main이 있는 완전한 stdin 프로그램이어야 한다.
 */
class CheckerDialog(
    initialLanguage: Language,
    initialCode: String
) : DialogWrapper(true) {

    private val languageCombo = ComboBox(Language.entries.map { it.displayName }.toTypedArray()).apply {
        selectedIndex = Language.entries.indexOf(initialLanguage).coerceAtLeast(0)
    }

    private val codeArea = JTextArea(
        initialCode.ifBlank { templateFor(initialLanguage) }, 18, 70
    ).apply {
        font = Font("JetBrains Mono", Font.PLAIN, JBUI.scale(12))
        border = JBUI.Borders.empty(6)
    }

    init {
        title = I18n.t("커스텀 체커 (복수 정답 판정)", "Custom Checker (multiple valid answers)")
        // 언어를 바꾸면, 코드가 아직 (어느 언어든) 기본 템플릿 그대로일 때만
        // 새 언어의 템플릿으로 교체 — 사용자가 수정한 코드는 절대 덮어쓰지 않음
        languageCombo.addActionListener {
            val current = codeArea.text.trim()
            val isUntouchedTemplate = current.isBlank() ||
                Language.entries.any { templateFor(it).trim() == current }
            if (isUntouchedTemplate) {
                codeArea.text = templateFor(getLanguage())
                codeArea.caretPosition = 0
            }
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(6)))
        panel.preferredSize = Dimension(JBUI.scale(640), JBUI.scale(440))

        val top = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
        top.add(JLabel(I18n.t("체커 언어:", "Checker language:")))
        top.add(languageCombo)
        top.add(JLabel("<html><i>${I18n.t(
            "코드를 비우고 저장하면 체커가 해제됩니다",
            "Save with empty code to disable the checker"
        )}</i></html>").apply { foreground = com.intellij.ui.JBColor.GRAY })
        panel.add(top, BorderLayout.NORTH)

        panel.add(JBScrollPane(codeArea), BorderLayout.CENTER)

        panel.add(JLabel("<html>${I18n.t(
            "stdin: 입력 → <b>===CTK===</b> → 사용자 출력 → <b>===CTK===</b> → 예상 출력. " +
                "첫 줄에 <b>OK</b> 출력 시 통과.",
            "stdin: input → <b>===CTK===</b> → user output → <b>===CTK===</b> → expected output. " +
                "Print <b>OK</b> on the first line to pass."
        )}</html>").apply {
            foreground = com.intellij.ui.JBColor.GRAY
            font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
        }, BorderLayout.SOUTH)

        return panel
    }

    fun getLanguage(): Language = Language.entries[languageCombo.selectedIndex]

    /** 저장된 체커 코드 (기본 템플릿 그대로면 미설정으로 간주하지 않음 — 사용자가 수정해서 쓰는 출발점) */
    fun getCode(): String = codeArea.text

    /** 언어별 기본 템플릿: stdin 3분할 + 순서 무관 비교 예제 (판정 로직만 고쳐 쓰면 됨) */
    private fun templateFor(language: Language): String = when (language) {
        Language.PYTHON -> """
            |import sys
            |
            |sections = sys.stdin.read().split("===CTK===")
            |inp = sections[0].strip() if len(sections) > 0 else ""
            |user_out = sections[1].strip() if len(sections) > 1 else ""
            |expected = sections[2].strip() if len(sections) > 2 else ""
            |
            |# 여기에 판정 로직 작성 — 예: 순서 무관 비교
            |ok = sorted(user_out.split()) == sorted(expected.split())
            |
            |print("OK" if ok else "WRONG")
        """.trimMargin()

        Language.JAVA -> """
            |import java.util.*;
            |
            |public class Main {
            |    public static void main(String[] args) {
            |        Scanner sc = new Scanner(System.in);
            |        StringBuilder sb = new StringBuilder();
            |        while (sc.hasNextLine()) sb.append(sc.nextLine()).append('\n');
            |        String[] sec = sb.toString().split("===CTK===");
            |        String input = sec.length > 0 ? sec[0].trim() : "";
            |        String userOut = sec.length > 1 ? sec[1].trim() : "";
            |        String expected = sec.length > 2 ? sec[2].trim() : "";
            |
            |        // 여기에 판정 로직 작성 — 예: 순서 무관 비교
            |        String[] a = userOut.split("\\s+"); Arrays.sort(a);
            |        String[] b = expected.split("\\s+"); Arrays.sort(b);
            |        boolean ok = Arrays.equals(a, b);
            |
            |        System.out.println(ok ? "OK" : "WRONG");
            |    }
            |}
        """.trimMargin()

        Language.CPP -> """
            |#include <bits/stdc++.h>
            |using namespace std;
            |
            |int main() {
            |    string all((istreambuf_iterator<char>(cin)), istreambuf_iterator<char>());
            |    vector<string> sec;
            |    size_t pos = 0, p;
            |    while ((p = all.find("===CTK===", pos)) != string::npos) {
            |        sec.push_back(all.substr(pos, p - pos));
            |        pos = p + 9;
            |    }
            |    sec.push_back(all.substr(pos));
            |    string input = sec.size() > 0 ? sec[0] : "";
            |    string userOut = sec.size() > 1 ? sec[1] : "";
            |    string expected = sec.size() > 2 ? sec[2] : "";
            |
            |    // 여기에 판정 로직 작성 — 예: 순서 무관 토큰 비교
            |    auto tokens = [](const string& s) {
            |        vector<string> v; string t; stringstream ss(s);
            |        while (ss >> t) v.push_back(t);
            |        sort(v.begin(), v.end());
            |        return v;
            |    };
            |    bool ok = tokens(userOut) == tokens(expected);
            |
            |    cout << (ok ? "OK" : "WRONG") << endl;
            |    return 0;
            |}
        """.trimMargin()

        Language.KOTLIN -> """
            |fun main() {
            |    val sec = generateSequence(::readLine).joinToString("\n").split("===CTK===")
            |    val input = sec.getOrElse(0) { "" }.trim()
            |    val userOut = sec.getOrElse(1) { "" }.trim()
            |    val expected = sec.getOrElse(2) { "" }.trim()
            |
            |    // 여기에 판정 로직 작성 — 예: 순서 무관 비교
            |    val ok = userOut.split(Regex("\\s+")).sorted() == expected.split(Regex("\\s+")).sorted()
            |
            |    println(if (ok) "OK" else "WRONG")
            |}
        """.trimMargin()

        Language.JAVASCRIPT -> """
            |const sec = require('fs').readFileSync(0, 'utf8').split('===CTK===');
            |const input = (sec[0] || '').trim();
            |const userOut = (sec[1] || '').trim();
            |const expected = (sec[2] || '').trim();
            |
            |// 여기에 판정 로직 작성 — 예: 순서 무관 비교
            |const norm = s => s.split(/\s+/).filter(Boolean).sort().join(' ');
            |const ok = norm(userOut) === norm(expected);
            |
            |console.log(ok ? 'OK' : 'WRONG');
        """.trimMargin()

        Language.RUST -> """
            |use std::io::Read;
            |
            |fn main() {
            |    let mut all = String::new();
            |    std::io::stdin().read_to_string(&mut all).unwrap();
            |    let sec: Vec<&str> = all.split("===CTK===").collect();
            |    let _input = sec.get(0).unwrap_or(&"").trim();
            |    let user_out = sec.get(1).unwrap_or(&"").trim();
            |    let expected = sec.get(2).unwrap_or(&"").trim();
            |
            |    // 여기에 판정 로직 작성 — 예: 순서 무관 비교
            |    let mut a: Vec<&str> = user_out.split_whitespace().collect();
            |    let mut b: Vec<&str> = expected.split_whitespace().collect();
            |    a.sort();
            |    b.sort();
            |    let ok = a == b;
            |
            |    println!("{}", if ok { "OK" } else { "WRONG" });
            |}
        """.trimMargin()

        Language.GO -> """
            |package main
            |
            |import (
            |    "fmt"
            |    "io"
            |    "os"
            |    "sort"
            |    "strings"
            |)
            |
            |func main() {
            |    data, _ := io.ReadAll(os.Stdin)
            |    sec := strings.Split(string(data), "===CTK===")
            |    get := func(i int) string {
            |        if i < len(sec) {
            |            return strings.TrimSpace(sec[i])
            |        }
            |        return ""
            |    }
            |    userOut, expected := get(1), get(2)
            |
            |    // 여기에 판정 로직 작성 — 예: 순서 무관 비교
            |    a, b := strings.Fields(userOut), strings.Fields(expected)
            |    sort.Strings(a)
            |    sort.Strings(b)
            |    ok := strings.Join(a, " ") == strings.Join(b, " ")
            |
            |    if ok {
            |        fmt.Println("OK")
            |    } else {
            |        fmt.Println("WRONG")
            |    }
            |}
        """.trimMargin()

        Language.RUBY -> """
            |sec = STDIN.read.split("===CTK===")
            |input = (sec[0] || "").strip
            |user_out = (sec[1] || "").strip
            |expected = (sec[2] || "").strip
            |
            |# 여기에 판정 로직 작성 — 예: 순서 무관 비교
            |ok = user_out.split.sort == expected.split.sort
            |
            |puts(ok ? "OK" : "WRONG")
        """.trimMargin()
    }
}
