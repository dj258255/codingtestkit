#!/usr/bin/env bash
# 코드포스 Jsoup(HTTP) 경로를 일부러 실패시켜 JCEF 폴백을 강제로 태우는 개발용 토글.
# 이슈 #9/#30 Linux 테스트 전용. 커밋하지 말 것.
#   ./scripts/force-cf-fallback.sh on   # 패치 적용
#   ./scripts/force-cf-fallback.sh off  # 되돌림
set -euo pipefail
FILE="src/main/kotlin/com/codingtestkit/service/CodeforcesCrawler.kt"
MARK="// FORCE_CF_FALLBACK_TEST"
ANCHOR='        val (contestId, letter) = parseProblemId(problemId)'

cd "$(dirname "$0")/.."

case "${1:-}" in
  on)
    if grep -q "$MARK" "$FILE"; then echo "이미 적용됨"; exit 0; fi
    # fetchProblem 본문 첫 줄 앞에 강제 throw 삽입
    perl -0pi -e "s|\Q$ANCHOR\E|        throw RuntimeException(\"$MARK: forced 403\")\n$ANCHOR|" "$FILE"
    echo "패치 적용됨 — Jsoup 경로가 항상 실패해 JCEF 폴백을 탑니다."
    ;;
  off)
    perl -0pi -e "s|        throw RuntimeException\(\"\Q$MARK\E: forced 403\"\)\n||" "$FILE"
    echo "패치 되돌림."
    git diff --stat "$FILE"
    ;;
  *)
    echo "사용법: $0 {on|off}"; exit 1;;
esac
