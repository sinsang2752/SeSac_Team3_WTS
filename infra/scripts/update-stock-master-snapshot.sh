#!/usr/bin/env bash
#
# 종목 마스터 스냅샷을 최신 KIS 종목정보 파일로 바꾼다. (CLAUDE.md §57.1, ADR-0014)
#
#   ./infra/scripts/update-stock-master-snapshot.sh
#
# 스냅샷은 mock 모드와 오프라인 기동, 테스트가 쓴다. 바꾼 뒤 테스트를 돌리고 커밋한다.
#   cd backend && ./gradlew :market-service:test
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_env.sh"

BASE_URL="${MARKET_MASTER_DOWNLOAD_BASE_URL:-https://new.real.download.dws.co.kr/common/master/}"
DEST="$REPO_ROOT/backend/market-service/src/main/resources/master"

for file in kospi_code.mst.zip kosdaq_code.mst.zip; do
  tmp="$(mktemp)"
  curl -fsSL --max-time 60 -o "$tmp" "$BASE_URL$file"
  # 오류 페이지가 zip 자리에 들어가지 않게 한다.
  unzip -tq "$tmp" >/dev/null
  mv "$tmp" "$DEST/$file"
  echo "갱신: $DEST/$file ($(wc -c < "$DEST/$file" | tr -d ' ') bytes)"
done
