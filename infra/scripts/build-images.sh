#!/usr/bin/env bash
# 배포용 컨테이너 이미지를 만든다. (CLAUDE.md §47 Phase 7)
#
#   ./infra/scripts/build-images.sh [태그]
#
# jar를 먼저 만든 뒤 이미지에 담는다. 이미지 안에서 Gradle을 돌리지 않는다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TAG="${1:-local}"
SERVICES=(gateway-service market-service trading-service user-service)

echo "▸ 백엔드 jar 빌드"
(cd "$ROOT/backend" && ./gradlew assemble -q)

for service in "${SERVICES[@]}"; do
    echo "▸ wts/${service}:${TAG}"
    docker build -q \
        -f "$ROOT/backend/Dockerfile" \
        --build-arg "SERVICE=${service}" \
        -t "wts/${service}:${TAG}" \
        "$ROOT/backend" > /dev/null
done

echo "▸ wts/frontend:${TAG}"
docker build -q -t "wts/frontend:${TAG}" "$ROOT/frontend" > /dev/null

echo
docker images --format '{{.Repository}}:{{.Tag}}\t{{.Size}}' | grep "^wts/.*:${TAG}$" | sort
