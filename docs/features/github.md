# GitHub Integration / GitHub 연동

<p align="center">
  <a href="../../README.md"><b>← Back to README</b></a>
</p>

---

## English

Push your accepted solutions to GitHub automatically.

### Features

| Feature | Description |
|---------|-------------|
| **One-Click Login** | Log in to GitHub via built-in browser — token is auto-generated and saved |
| **Repo Selector** | Choose from your repositories via dropdown after login |
| **Auto Push** | Automatically push code to GitHub when submission is accepted |
| **Manual Push** | Click the GitHub button to push anytime |
| **Smart Detection** | Only pushes on "Accepted" — wrong answers are never pushed |
| **All Platforms** | Works with Programmers, SWEA, LeetCode, and Codeforces |
| **Structured Commits** | `[Platform #ID] Problem Title (Language)` format |

### Commit Structure

```
[LeetCode #two-sum] Two Sum (Java)
```

Each push includes:
- The solution code file
- A README.md with the problem description (if enabled in settings)

### Setup

1. Go to **Settings > GitHub Integration**
2. Click **"GitHub Login"**
3. Authorize in the built-in browser
4. Select your target repository from the dropdown

---

## 한국어

맞은 문제를 자동으로 GitHub에 푸시합니다.

### 기능

| 기능 | 설명 |
|------|------|
| **원클릭 로그인** | 내장 브라우저에서 GitHub에 로그인 → 토큰 자동 생성 및 저장 |
| **레포 선택** | 로그인 후 드롭다운에서 레포 선택 |
| **자동 푸시** | 채점 결과가 "맞았습니다"일 때만 자동으로 커밋 |
| **수동 푸시** | GitHub 버튼을 눌러서 원할 때 직접 푸시 |
| **스마트 감지** | 틀린 코드는 절대 푸시하지 않음 |
| **전 플랫폼 지원** | 프로그래머스, SWEA, LeetCode, Codeforces 모두 지원 |
| **구조화된 커밋** | `[플랫폼 #번호] 문제 제목 (언어)` 형식 |

### 커밋 구조

```
[LeetCode #two-sum] Two Sum (Java)
```

각 푸시에 포함:
- 풀이 코드 파일
- 문제 설명이 담긴 README.md (설정에서 활성화 시)

### 설정 방법

1. **설정 > GitHub 연동** 이동
2. **"GitHub 로그인"** 클릭
3. 내장 브라우저에서 인증
4. 드롭다운에서 대상 레포지토리 선택
