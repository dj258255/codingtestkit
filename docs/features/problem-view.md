# Problem View & Translation / 문제 보기 & 번역

<p align="center">
  <a href="../../README.md"><b>← Back to README</b></a>
</p>

---

## English

View the problem description, I/O format, and examples directly in the plugin panel — no browser needed.

### Problem Display

- Full problem description rendered as HTML
- Input/Output format and constraints
- Example test cases with expected I/O
- Programmers problems show I/O example tables

<p align="center">
  <img src="../screenshots/problem-view-1.png" width="420" alt="Problem View - Top"/>
  <img src="../screenshots/problem-view-2.png" width="420" alt="Problem View - Examples"/>
</p>

<p align="center">
  <img src="../screenshots/programmers-problem.png" width="700" alt="Programmers Problem"/>
</p>

### Problem Translation (KR ↔ EN)

Translate problem descriptions between Korean and English with one click.

| Feature | Description |
|---------|-------------|
| **Toggle Translation** | Click the Translate button to switch between original and translated text |
| **Auto Language Detection** | Automatically detects Korean/English and translates to the other |
| **Translation Caching** | Results are cached — no repeated API calls for the same problem |
| **Rate Limit Protection** | Built-in request throttling and exponential backoff retry |

- Uses the same Google Translate unofficial API used by popular IntelliJ translation plugins (e.g., YiiGuxing Translation Plugin)

### README Preview

When a problem is fetched, a README.md is optionally generated. You can preview it in the IDE.

<p align="center">
  <img src="../screenshots/readme-preview.png" width="500" alt="README Preview"/>
</p>

---

## 한국어

플러그인 패널에서 문제 설명, 입출력 형식, 예제를 바로 확인할 수 있습니다. 브라우저가 필요 없습니다.

### 문제 표시

- HTML로 렌더링된 전체 문제 설명
- 입출력 형식 및 제약 조건
- 예제 테스트 케이스와 기대 입출력
- 프로그래머스 문제는 입출력 예 테이블과 함께 표시

### 문제 번역 (한 ↔ 영)

문제 설명을 한국어 ↔ 영어로 한 클릭에 번역합니다.

| 기능 | 설명 |
|------|------|
| **토글 번역** | 번역 버튼 클릭으로 원문/번역 전환 |
| **자동 언어 감지** | 한국어/영어를 자동 감지하여 반대 언어로 번역 |
| **번역 캐싱** | 번역 결과를 캐시하여 같은 문제를 다시 번역하지 않음 |
| **Rate Limit 보호** | 요청 간 딜레이 및 exponential backoff 재시도로 IP 차단 방지 |

- IntelliJ 번역 플러그인(YiiGuxing Translation Plugin 등)과 동일한 Google Translate 비공식 API 사용

### README 프리뷰

문제를 가져오면 README.md가 선택적으로 생성됩니다. IDE에서 바로 미리보기할 수 있습니다.
