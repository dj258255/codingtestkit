# Linux JCEF 폴백 테스트 가이드 (이슈 #9 / #30)

macOS/Windows에는 없는 Linux 전용 JCEF 렌더링 문제를 재현·검증하기 위한 절차.
Apple Silicon Mac 기준. 목적: "Linux(특히 Wayland)에서 OSR 폴백이 실제로
페이지를 가져오는가"를 확인하고, 실패 시 보이는 다이얼로그 폴백(#30)이 동작하는지 본다.

## 1. Ubuntu VM 준비 (UTM)

```bash
brew install --cask utm
```

- Ubuntu 24.04 **ARM64 Desktop** ISO 다운로드 (ubuntu.com/download/desktop 의 ARM 빌드)
- UTM에서 새 VM 생성: Virtualize → Linux → ISO 지정, RAM 8GB, 디스크 40GB
- 설치 완료 후 VM 부팅

## 2. VM 안에서 빌드 도구 설치

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk git
git clone https://github.com/dj258255/codingtestkit.git
cd codingtestkit
git checkout fix/codeforces-visible-fallback   # 테스트 대상 브랜치
```

## 3. 강제 폴백 패치 적용 (Jsoup을 일부러 실패시켜 JCEF 경로 검증)

평소엔 Jsoup(HTTP)이 성공해서 JCEF 폴백을 안 타므로, 폴백을 강제로 태운다.

```bash
./scripts/force-cf-fallback.sh on    # 패치 적용
./gradlew runIde                     # 샌드박스 IDE 실행
```

IDE에서: 아무 프로젝트 열기 → CodingTestKit → 플랫폼 Codeforces → 번호 2241E → 가져오기

## 4. 로그로 진단

다른 터미널에서:

```bash
tail -f build/idea-sandbox/IC-2024.3/log/idea.log | grep --line-buffered "Codeforces JCEF"
```

- `fetch started` 다음 `loaded (status=200)` → `fetch OK` 가 뜨면 → **OSR 정상**
- `fetch started` 만 뜨고 `loaded` 가 없으면 → **OSR이 페이지 로드 실패** (제보자 증상 재현).
  이 경우 잠시 후 보이는 다이얼로그(#30)가 자동으로 떠야 하고, 거기서 문제가 표시되면 폴백 성공.

## 5. Wayland / X11 둘 다 테스트

로그인 화면 우하단 톱니에서 세션을 전환:
- **Ubuntu on Wayland** (기본, 제보자 의심 환경 — 본게임)
- **Ubuntu on Xorg**

각 세션에서 3~4단계 반복.

## 6. 정리

```bash
./scripts/force-cf-fallback.sh off   # 패치 되돌림 (커밋 금지)
```
