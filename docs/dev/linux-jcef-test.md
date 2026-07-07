# Linux JCEF 폴백 테스트 가이드 (이슈 #9 / #30)

## 배경 — 무엇을 확인하려는가

제보자 환경: **Linux x86_64 + RustRover** (JetBrains). 제보 GIF 분석 결과:

- 코드포스 문제를 가져오면 브라우저가 페이지를 **완벽히 로드**함 (Cloudflare 챌린지도 없음, status 200)
- 그런데 플러그인이 그 HTML을 추출하지 못하고 **"Fetching..."에서 멈춤**
- 즉 병목은 "Cloudflare 통과"가 아니라 "JBCefJSQuery 추출 콜백이 안 옴"
- **맥/윈도우에서는 정상** — Linux 특유의 JCEF 동작 차이로 추정

수정(#30): 브라우저가 페이지를 로드하면 cf_clearance 쿠키가 생기므로,
JS 추출 대신 **그 쿠키로 Jsoup 재시도**하는 우회 경로를 병행. 추출 콜백이
죽는 환경에서도 문제를 가져올 수 있어야 함. 이 가이드는 그게 실제로
동작하는지 Linux에서 확인하는 절차.

## 1. 환경 준비 — 반드시 x86_64

제보자가 x86_64이므로 아키텍처를 맞춘다:
- **권장**: x86_64 클라우드 VM (AWS/GCP 무료 티어 Ubuntu 등) — Apple Silicon Mac의
  UTM x86 에뮬레이션은 매우 느리고 JCEF가 불안정할 수 있음
- 데스크톱 환경 필요 (JCEF는 GUI). 헤드리스면 xvfb 필요
- 가능하면 **RustRover**로도 재현 (IDEA와 JCEF 빌드 차이 가능성)

```bash
sudo apt update && sudo apt install -y openjdk-21-jdk git
git clone https://github.com/dj258255/codingtestkit.git
cd codingtestkit
git checkout fix/codeforces-visible-fallback   # 수정 브랜치
```

## 2. 그냥 실행해서 재현 시도 (강제 패치 없이)

먼저 제보자와 동일 조건 그대로:

```bash
./gradlew runIde
```

문제 탭 → Codeforces → 아직 안 받아본 번호(예: 1A) → 가져오기.

- **문제가 표시되면** → 수정 성공 (쿠키 재시도가 추출 실패를 살림)
- **여전히 "Fetching..."에서 멈추면** → 로그 확인 (아래 4번)

## 3. 강제 폴백으로 우회 경로만 집중 테스트

Jsoup이 바로 성공해버리면 JCEF 경로를 안 타므로, 강제로 폴백을 태운다:

```bash
./scripts/force-cf-fallback.sh on
./gradlew runIde
# 테스트 후
./scripts/force-cf-fallback.sh off
```

## 4. 로그 진단

```bash
tail -f build/idea-sandbox/*/log/idea.log | grep --line-buffered -iE "Codeforces JCEF|cookie-retry"
```

핵심 라인:
- `JCEF loaded ... (status=200)` → 브라우저는 페이지 로드 성공
- `cookie-retry succeeded` → **우회 경로가 문제를 가져옴 (수정 동작 확인)**
- `fetch OK` → 기존 JS 추출 경로가 성공 (맥에서 보이는 것)
- 둘 다 없이 타임아웃 → 우회 경로도 실패 → 로그 전체를 이슈에 첨부해 추가 분석

## 5. 보이는 다이얼로그 폴백 확인

OSR·쿠키재시도 둘 다 실패하는 최악의 경우, 제목줄·닫기 버튼이 있는
"Codeforces 문제 가져오기" 다이얼로그가 떠야 한다. 거기서 페이지가 보이고
(필요하면 직접 Cloudflare 통과), 통과 후 자동으로 문제가 추출·표시되면 OK.
