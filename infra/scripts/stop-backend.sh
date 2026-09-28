#!/usr/bin/env bash
# run-backend.sh 로 띄운 서비스를 종료한다.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_env.sh"

for name in "${SERVICE_NAMES[@]}"; do
  pid_file="$RUN_DIR/${name}.pid"
  [[ -f "$pid_file" ]] || continue

  pid="$(cat "$pid_file")"
  # Gradle 데몬이 bootRun을 자식 프로세스로 띄우므로 프로세스 그룹째 정리한다.
  pkill -TERM -P "$pid" 2>/dev/null || true
  kill -TERM "$pid" 2>/dev/null || true
  rm -f "$pid_file"
  echo "종료 요청: $name (pid $pid)"
done

# bootRun은 Gradle 데몬 안에서 돌기 때문에 남은 애플리케이션 프로세스를 확인한다.
pkill -f 'com.team.wts..*Application' 2>/dev/null || true
echo "완료"
