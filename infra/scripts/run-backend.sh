#!/usr/bin/env bash
#
# 백엔드 4개 서비스를 로컬 프로세스로 기동한다. (CLAUDE.md §32)
# 인프라는 먼저 'docker compose up -d' 로 띄워 두어야 한다.
#
#   ./infra/scripts/run-backend.sh
#   ./infra/scripts/health-check.sh
#   ./infra/scripts/stop-backend.sh

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_env.sh"

mkdir -p "$RUN_DIR"

for i in "${!SERVICE_NAMES[@]}"; do
  name="${SERVICE_NAMES[$i]}"
  port="${SERVICE_PORTS[$i]}"

  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "건너뜀: $name (포트 $port 이미 사용 중)"
    continue
  fi

  echo "기동: $name -> :$port"
  (
    cd "$REPO_ROOT/backend"
    nohup ./gradlew ":${name}:bootRun" --quiet --console=plain \
      > "$RUN_DIR/${name}.log" 2>&1 &
    echo $! > "$RUN_DIR/${name}.pid"
  )
done

echo
echo "로그: $RUN_DIR/<service>.log"
echo "기동 확인: ./infra/scripts/health-check.sh"
