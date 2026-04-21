<p align="center">
  <img src="../ctk-pixel.svg" width="128" alt="CodingTestKit Icon"/>
</p>

<h1 align="center">CodingTestKit</h1>
<p align="center">
  An all-in-one IntelliJ plugin to <b>fetch</b>, <b>test</b>, and <b>submit</b> algorithm problems — all without leaving your IDE.
</p>
<p align="center">
  <b>Programmers</b> · <b>SWEA</b> · <b>LeetCode</b> · <b>Codeforces</b> &nbsp;|&nbsp; 🌐 Korean / English
</p>

---

## Why?

Preparing for coding tests has always been cumbersome — read the problem in a browser, write code in the IDE, switch back to the browser to submit, and repeat.

CodingTestKit was built to **replicate the real exam environment inside your IDE**:

- **Exam Mode**: Disable autocomplete & inspections, block external paste, detect focus loss — practice under real test conditions
- **Performance Metrics**: Show execution time (ms) and memory usage per test case
- **Timer**: Stopwatch, circular dial countdown timer, progress bar, digital clock
- **All-in-One**: Fetch, code, test, and submit without leaving the IDE
- **Problem Translation**: One-click Korean ↔ English translation with caching and rate limit protection
- **GitHub Push**: Auto-push accepted solutions to GitHub

## Supported Platforms

| Platform | Fetch | Test | Submit | Search | Random |
|----------|:-----:|:----:|:------:|:------:|:------:|
| **Programmers** | O | O | O | O | O |
| **SWEA** | O | O | O | O | O |
| **LeetCode** | O | O | O | O | O |
| **Codeforces** | O | O | O | O | O |

## Supported Languages

| Language | Programmers | SWEA | LeetCode | Codeforces |
|----------|:-----------:|:----:|:--------:|:----------:|
| Java | O | O | O | O |
| Python | O | O | O | O |
| C++ | O | O | O | O |
| Kotlin | O | X | O | O |

---

## Features

### Internationalization (i18n)

Switch between **Korean / English** in settings. All UI text is displayed in the selected language. The language change note is shown in both languages simultaneously.

### Fetch Problems

Select the platform and language, enter a problem number, and the problem description and test cases are automatically extracted.

- **Programmers**: Number after `/lessons/` in URL (e.g., `12947`)
- **SWEA**: Enter problem number or paste URL
- **LeetCode**: Enter number, slug, or URL (e.g., `1`, `two-sum`, full URL)
- **Codeforces**: contestId+letter (e.g., `1234A`) or URL

<p align="center">
  <img src="screenshots/main-panel.png" width="500" alt="Main Panel"/>
</p>

When a problem is fetched, a folder is automatically created with a code file and README.md (problem description).

<p align="center">
  <img src="screenshots/fetch-notification.png" width="700" alt="Fetch Complete"/>
</p>

<p align="center">
  <img src="screenshots/folder-structure.png" width="250" alt="Folder Structure"/>
</p>

- Programmers: `problems/Programmers/Level1/12937. 짝수와 홀수/`
- SWEA: `problems/SWEA/D3/1204. 최빈수 구하기/`
- LeetCode: `problems/LeetCode/Medium/1. Two Sum/`
- Codeforces: `problems/Codeforces/800/1000A. Codeforces/`

### Problem View & Translation

<p align="center">
  <img src="screenshots/boj-submit-success.png" width="700" alt="Fetch Example"/>
</p>

View the problem description, I/O format, and examples directly in the plugin panel.

#### Problem Translation (KR ↔ EN)

Translate problem descriptions between Korean and English with one click.

- **Toggle Translation**: Click the Translate button to switch between original and translated text
- **Auto Language Detection**: Automatically detects Korean/English and translates to the other language
- **Translation Caching**: Translated results are cached — no repeated API calls for the same problem
- **Rate Limit Protection**: Built-in request throttling and exponential backoff retry to prevent IP blocking
- Uses the same Google Translate unofficial API used by popular IntelliJ translation plugins (e.g., YiiGuxing Translation Plugin)

<p align="center">
  <img src="screenshots/problem-view-1.png" width="420" alt="Problem View - Top"/>
  <img src="screenshots/problem-view-2.png" width="420" alt="Problem View - Examples"/>
</p>

<p align="center">
  <img src="screenshots/readme-preview.png" width="500" alt="README Preview"/>
</p>

Programmers problems are also displayed with I/O example tables.

<p align="center">
  <img src="screenshots/programmers-problem.png" width="700" alt="Programmers Problem"/>
</p>

### Problem Search

#### LeetCode

Search LeetCode problems by keyword, difficulty, and algorithm tags.

- **Keyword Search**: Search by title or keyword
- **Difficulty Filter**: Filter by Easy, Medium, Hard
- **Tag Filter**: Filter by algorithm tags (Array, DP, Graph, etc.)
- **Auto Search**: Debounced auto-search while typing

#### Codeforces / Programmers / SWEA

Search via each platform's official API or listing pages by keyword, tag, and difficulty.

### Random Problem Picker

#### LeetCode

- **Difficulty Checkboxes**: Select multiple difficulties (Easy, Medium, Hard)
- **Tag Chips**: Select/remove tags as chips (Array, DP, Graph, etc.)
- **Count**: Set number of problems to pick (1–20)

#### Codeforces / Programmers / SWEA

Each platform supports random picking with rating/level ranges and tag chips.

### Login & Submit

Log in to each platform via the built-in JCEF browser and submit your code directly.

Click **Submit**, confirm the dialog, and your code & language are auto-filled.

<p align="center">
  <img src="screenshots/boj-submit-confirm.png" width="700" alt="Submit Confirmation"/>
</p>

<p align="center">
  <img src="screenshots/boj-submit-code.png" width="700" alt="Code Auto-Fill"/>
</p>

Login and submission work the same way for Programmers, SWEA, LeetCode, and Codeforces.

### Local Test Execution

Write your code and click **Run All** to execute all test cases and see PASS/FAIL results instantly. Each test case shows **execution time (ms)** and **memory usage (KB/MB)**.

<p align="center">
  <img src="screenshots/test-cases-list.png" width="700" alt="Test Cases List"/>
</p>

<p align="center">
  <img src="screenshots/test-all-pass.png" width="700" alt="All Pass"/>
</p>

Failed cases are highlighted in red and auto-expanded.

<p align="center">
  <img src="screenshots/test-all-fail.png" width="700" alt="Test Fail"/>
</p>

Expand each test case to compare input, expected output, and actual output.

<p align="center">
  <img src="screenshots/test-detail.png" width="600" alt="Test Detail"/>
</p>

Programmers and LeetCode solution functions are automatically wrapped for testing.

<p align="center">
  <img src="screenshots/programmers-test.png" width="700" alt="Programmers Test"/>
</p>

### Code Editor

When a problem is fetched, boilerplate code is auto-generated so you can start coding immediately.

<p align="center">
  <img src="screenshots/code-editor.png" width="500" alt="Code Editor"/>
</p>

### Code Templates

Save frequently used boilerplate as templates for quick access. Syntax-highlighted preview included.

<p align="center">
  <img src="screenshots/template-panel.png" width="700" alt="Template Panel"/>
</p>

### Timer

Provides a **Stopwatch** and a **Countdown Timer**.

- **Stopwatch**: Lap records with memo
- **Countdown**: 3 display modes selectable via checkboxes
  - **Circular Dial Timer**: Remaining time shown as a red circle, elapsed time as a white gap growing clockwise
  - **Digital Clock**: Large numerical time display
  - **Progress Bar**: Linear progress indicator
- Preset buttons for 30min, 1hr, 2hr, 3hr
- Notification when time's up

<p align="center">
  <img src="screenshots/stopwatch.png" width="700" alt="Stopwatch"/>
</p>

<p align="center">
  <img src="screenshots/countdown-running.png" width="500" alt="Countdown Running"/>
</p>

### Settings & Exam Mode

Practice under conditions identical to real coding tests.

- **Auto Complete ON/OFF**: Toggle code auto-completion popups
- **Inspections ON/OFF**: Enable power save mode to stop background analysis
- **Paste Block**: Block pasting text copied from external programs (internal copy/paste works normally)
- **Focus Alert**: Show alert when IDE window loses focus (same as cheating detection in real tests)
- **Language**: Switch between Korean / English (bilingual note)

One-click **Exam Mode** enables all 4 restrictions; **Normal Mode** disables them all.

<p align="center">
  <img src="screenshots/settings.png" width="600" alt="Settings"/>
</p>

#### Exam Mode (Auto Complete OFF)

In exam mode, code auto-completion and inspections are disabled.

<p align="center">
  <img src="screenshots/exam-mode-editor.png" width="700" alt="Exam Mode - Editor"/>
</p>

<p align="center">
  <img src="screenshots/exam-mode-no-autocomplete.png" width="400" alt="Exam Mode - No Autocomplete"/>
</p>

#### Normal Mode (Auto Complete ON)

<p align="center">
  <img src="screenshots/normal-mode-editor.png" width="700" alt="Normal Mode - Editor"/>
</p>

<p align="center">
  <img src="screenshots/normal-mode-autocomplete.png" width="600" alt="Normal Mode - Autocomplete"/>
</p>

### GitHub Integration

Push your accepted solutions to GitHub automatically.

- **One-Click Login**: Log in to GitHub via built-in browser — token is auto-generated and saved
- **Repo Selector**: Choose from your repositories via dropdown after login
- **Auto Push**: Automatically push code to GitHub when your submission is accepted
- **Manual Push**: Click the GitHub button to push anytime
- **Smart Detection**: Only pushes on "Accepted" — wrong answers are never pushed
- **All Platforms**: Works with Programmers, SWEA, LeetCode, and Codeforces
- **Structured Commits**: `[Platform #ID] Problem Title (Language)` format with README

Setup: Settings > GitHub Integration > Click "GitHub Login" and select your repository.

---

## Installation

### JetBrains Marketplace
1. IntelliJ IDEA > Settings > Plugins > Marketplace
2. Search "CodingTestKit" and install

### Manual Install
1. Download `.zip` from [Releases](https://github.com/dj258255/codingtestkit/releases)
2. IntelliJ IDEA > Settings > Plugins > ⚙ > Install Plugin from Disk

---

## Usage

### 1. Fetch a Problem
1. Open **CodingTestKit** from the right sidebar
2. Select platform and language
3. Enter problem ID and click **Fetch**
   - Programmers: `12947`
   - SWEA: `1204`
   - LeetCode: `1` or `two-sum` or URL
   - Codeforces: `1234A` or URL

### 2. Search & Random
- **Search**: Search by keyword, tag, difficulty on each platform
- **Random**: Set rating/level range, tags, options, and pick random problems

### 3. Login
1. Click **Login**
2. Log in via built-in browser
3. Cookies saved automatically when login is detected

### 4. Run Tests
1. Go to the **Test** tab
2. Write code and click **Run All**
3. Check PASS/FAIL results (failed cases auto-expand)

### 5. Submit Code
1. Click **Submit**
2. Verify code auto-fill in built-in browser
3. Click submit and check results

---

## Requirements

- IntelliJ IDEA 2024.1+
- JDK 17+ (for Java execution)
- Language compilers (for respective language tests)

## Build

```bash
./gradlew buildPlugin
```

The built plugin will be in `build/distributions/`.

## License

MIT License

## Author

- **dj258255** - [GitHub](https://github.com/dj258255)
