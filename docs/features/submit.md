# Login & Submit / 로그인 & 제출

<p align="center">
  <a href="../../README.md"><b>← Back to README</b></a>
</p>

---

## English

Log in to each platform via the built-in JCEF browser and submit your code directly from the IDE.

### Login

Click the **Login** button for any platform. A built-in browser opens the platform's login page.

<p align="center">
  <img src="../screenshots/boj-login.png" width="600" alt="BOJ Login"/>
</p>

### Submit Flow

1. Click **Submit** after your code passes local tests
2. A confirmation dialog appears with the submit page
3. **Code** is auto-injected into the editor
4. **Language** dropdown is automatically selected to match your code
5. Verify and click the platform's submit button

<p align="center">
  <img src="../screenshots/boj-submit-confirm.png" width="700" alt="Submit Confirmation"/>
</p>

<p align="center">
  <img src="../screenshots/boj-submit-code.png" width="700" alt="Code Auto-Fill"/>
</p>

### Auto Language Selection

The language dropdown is automatically set on all platforms:

| Platform | Method |
|----------|--------|
| **BOJ** | `<select>` element value set via `baekjoonId` |
| **Programmers** | `<select>` or custom dropdown button click |
| **SWEA** | `<select>` + AngularJS scope binding |
| **LeetCode** | Dropdown button click + menu item text matching |

### Result Detection

After submitting, the plugin monitors the page for verdict:
- **Accepted** → Green status message + optional GitHub push
- **Wrong Answer / TLE / etc.** → Red status message

### Platform Notes

- **BOJ**: Cloudflare verification may be required before submitting
- **SWEA**: Some problems (mock exam, etc.) may not open due to permission restrictions
- **LeetCode**: Uses titleSlug for the submit URL

---

## 한국어

내장 JCEF 브라우저를 통해 각 플랫폼에 로그인하고, 코드를 직접 제출할 수 있습니다.

### 로그인

각 플랫폼의 **로그인** 버튼을 클릭하면 내장 브라우저에서 로그인 페이지가 열립니다.

### 제출 흐름

1. 로컬 테스트 통과 후 **제출** 버튼 클릭
2. 제출 확인 다이얼로그에 제출 페이지 표시
3. **코드**가 에디터에 자동 입력
4. **언어** 드롭다운이 코드 작성 언어에 맞춰 자동 선택
5. 확인 후 플랫폼의 제출 버튼 클릭

### 언어 자동 선택

모든 플랫폼에서 언어 드롭다운이 자동 설정됩니다:

| 플랫폼 | 방식 |
|--------|------|
| **백준** | `<select>` 요소에 `baekjoonId` 값 설정 |
| **프로그래머스** | `<select>` 또는 커스텀 드롭다운 버튼 클릭 |
| **SWEA** | `<select>` + AngularJS scope 바인딩 |
| **LeetCode** | 드롭다운 버튼 클릭 + 메뉴 아이템 텍스트 매칭 |

### 결과 감지

제출 후 페이지에서 채점 결과를 모니터링합니다:
- **맞았습니다** → 초록색 상태 메시지 + GitHub 자동 푸시 (설정 시)
- **틀렸습니다 / 시간 초과 등** → 빨간색 상태 메시지

### 플랫폼 참고사항

- **백준**: 제출 전 Cloudflare 인증이 필요할 수 있음
- **SWEA**: 모의 역량테스트 등 일부 문제는 권한 문제로 제출 페이지가 안 열릴 수 있음
- **LeetCode**: titleSlug를 사용하여 제출 URL 구성
