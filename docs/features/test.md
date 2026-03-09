# Local Test Execution / 로컬 테스트 실행

<p align="center">
  <a href="../../README.md"><b>← Back to README</b></a>
</p>

---

## English

Write your code and click **Run All** to execute all test cases and see PASS/FAIL results instantly.

### Performance Metrics

Each test case shows:
- **Execution time** in milliseconds (ms)
- **Memory usage** in KB or MB

### Test Results

<p align="center">
  <img src="../screenshots/test-cases-list.png" width="700" alt="Test Cases List"/>
</p>

<p align="center">
  <img src="../screenshots/test-all-pass.png" width="700" alt="All Pass"/>
</p>

Failed cases are highlighted in red and auto-expanded for easy debugging.

<p align="center">
  <img src="../screenshots/test-all-fail.png" width="700" alt="Test Fail"/>
</p>

<p align="center">
  <img src="../screenshots/test-detail.png" width="600" alt="Test Detail"/>
</p>

### Platform-Specific Wrapping

- **BOJ / SWEA**: Standard stdin/stdout — code runs as-is
- **Programmers / LeetCode**: Solution functions are automatically wrapped with test harness code
  - Detects class-based (LeetCode) vs. bare function (Programmers) patterns
  - Generates appropriate driver code for each language

<p align="center">
  <img src="../screenshots/programmers-test.png" width="700" alt="Programmers Test"/>
</p>

### Supported Languages

| Language | Compiler/Runtime | Notes |
|----------|-----------------|-------|
| Java | `javac` + `java` | JDK 17+ required |
| Python | `python3` | Auto-detects `python3` or `python` |
| C++ | `g++` | C++17 standard |
| Kotlin | `kotlinc` + `kotlin` | JVM-based execution |
| JavaScript | `node` | Node.js runtime |

### Comparison Logic

- Trailing whitespace is normalized
- Array spacing differences are handled (`[0, 1]` vs `[0,1]`)
- Output format matches each platform's convention

---

## 한국어

코드를 작성하고 **전체 실행**을 누르면 모든 테스트 케이스가 실행되어 PASS/FAIL 결과를 바로 확인할 수 있습니다.

### 성능 측정

각 테스트 케이스별로 표시:
- **실행 시간** (밀리초, ms)
- **메모리 사용량** (KB 또는 MB)

### 테스트 결과

- PASS: 초록색으로 표시
- FAIL: 빨간색으로 강조, 자동 펼침으로 디버깅 용이

### 플랫폼별 래핑

- **백준 / SWEA**: 표준 stdin/stdout — 코드 그대로 실행
- **프로그래머스 / LeetCode**: solution 함수를 자동으로 테스트 하네스로 래핑
  - 클래스 기반(LeetCode)과 순수 함수(프로그래머스) 패턴 자동 감지
  - 각 언어에 맞는 드라이버 코드 자동 생성

### 지원 언어

| 언어 | 컴파일러/런타임 | 비고 |
|------|---------------|------|
| Java | `javac` + `java` | JDK 17 이상 필요 |
| Python | `python3` | `python3` 또는 `python` 자동 감지 |
| C++ | `g++` | C++17 표준 |
| Kotlin | `kotlinc` + `kotlin` | JVM 기반 실행 |
| JavaScript | `node` | Node.js 런타임 |

### 비교 로직

- 후행 공백 정규화
- 배열 공백 차이 처리 (`[0, 1]` vs `[0,1]`)
- 각 플랫폼 출력 규약에 맞춤
