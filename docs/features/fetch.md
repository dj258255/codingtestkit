# Fetch Problems / 문제 가져오기

<p align="center">
  <a href="../../README.md"><b>← Back to README</b></a>
</p>

---

## English

Select the platform and language, enter a problem number, and the problem description and test cases are automatically extracted.

### How to Fetch

| Platform | Input | Example |
|----------|-------|---------|
| **Programmers** | Number after `/lessons/` in URL | `12947` |
| **SWEA** | Problem number or full URL | `1204` or paste URL |
| **LeetCode** | Number, slug, or full URL | `1`, `two-sum`, or full URL |
| **Codeforces** | contestId+letter, or full URL | `1234A` or full URL |

### What Happens After Fetch

1. A **folder** is created under your project: `[Platform] ProblemNumber - Title/`
2. A **code file** with boilerplate is generated (e.g., `Main.java`, `solution.py`)
3. A **README.md** with the problem description is optionally created (toggle in Settings)
4. **Test cases** are automatically extracted and ready to run

<p align="center">
  <img src="../screenshots/main-panel.png" width="500" alt="Main Panel"/>
</p>

<p align="center">
  <img src="../screenshots/fetch-notification.png" width="700" alt="Fetch Complete"/>
</p>

<p align="center">
  <img src="../screenshots/folder-structure.png" width="250" alt="Folder Structure"/>
</p>

### Platform-Specific Details

#### Programmers
- Fetches from `programmers.co.kr`
- Extracts solution function signature and I/O example table
- Auto-generates function wrapper for local testing

#### SWEA
- Fetches via built-in JCEF browser (login required for some problems)
- Some problems (e.g., mock exam problems) may require special permissions
- Images are served locally with proper Referer headers

#### LeetCode
- Fetches via GraphQL API
- Extracts code snippets, example test cases, and metadata
- Supports number (`1`), slug (`two-sum`), and full URL input

---

## 한국어

플랫폼과 언어를 선택하고 문제 번호만 입력하면 문제 설명, 테스트 케이스가 자동으로 추출됩니다.

### 문제 가져오는 방법

| 플랫폼 | 입력 방식 | 예시 |
|--------|----------|------|
| **프로그래머스** | URL의 `/lessons/` 뒤 숫자 | `12947` |
| **SWEA** | 문제 번호 또는 URL | `1204` 또는 URL 붙여넣기 |
| **LeetCode** | 번호, slug, 또는 URL | `1`, `two-sum`, 또는 URL |
| **Codeforces** | 콘테스트번호+문제번호 또는 URL | `1234A` 또는 URL |

### 가져오기 후 결과

1. 프로젝트 내에 **폴더**가 생성됩니다: `[플랫폼] 문제번호 - 제목/`
2. 보일러플레이트가 포함된 **코드 파일**이 생성됩니다 (예: `Main.java`, `solution.py`)
3. 문제 설명이 담긴 **README.md**가 선택적으로 생성됩니다 (설정에서 토글 가능)
4. **테스트 케이스**가 자동 추출되어 바로 실행 가능합니다

### 플랫폼별 상세

#### 프로그래머스
- `programmers.co.kr`에서 가져옴
- solution 함수 시그니처와 입출력 예 테이블 추출
- 로컬 테스트를 위한 함수 래퍼 자동 생성

#### SWEA
- 내장 JCEF 브라우저로 가져옴 (일부 문제는 로그인 필요)
- 모의 역량테스트 등 일부 문제는 특별 권한 필요
- 이미지는 Referer 헤더와 함께 로컬 서빙

#### LeetCode
- GraphQL API로 가져옴
- 코드 스니펫, 예제 테스트 케이스, 메타데이터 추출
- 번호(`1`), slug(`two-sum`), 전체 URL 입력 지원
