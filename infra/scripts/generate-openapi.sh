#!/usr/bin/env bash
#
# 실행 중인 서비스에서 OpenAPI 3 명세를 받아 하나로 합친다.
#
#   docker compose up -d --wait
#   ./infra/scripts/run-backend.sh
#   ./infra/scripts/generate-openapi.sh
#
# 산출물: docs/api/openapi.yaml
#         docs/api/api-spec.md, .xlsx, .pdf  (openapi.yaml 에서 렌더링)
#
# 명세를 손으로 고치지 않는다. 컨트롤러와 DTO를 고치고 이 스크립트를 다시 돌린다.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_env.sh"

OUT="$REPO_ROOT/docs/api/openapi.yaml"

# gateway-service는 WebFlux 라우팅만 하므로 자체 명세가 없다. 경로는 뒤쪽 3개 서비스가 가진다.
for port in "$USER_PORT" "$MARKET_PORT" "$TRADING_PORT"; do
  if ! curl -sf -o /dev/null "http://localhost:${port}/v3/api-docs"; then
    echo "오류: localhost:${port} 에서 /v3/api-docs 를 읽을 수 없다. 서비스가 떠 있는지 확인한다." >&2
    exit 1
  fi
done

python3 - "$OUT" "$REPO_ROOT/backend/gateway-service/src/main/resources/application.yml" \
  "$USER_PORT" "$MARKET_PORT" "$TRADING_PORT" <<'PY'
import json, re, sys, urllib.request
import yaml

out, gateway_yml, *ports = sys.argv[1:]

merged = None
for port in ports:
    with urllib.request.urlopen(f"http://localhost:{port}/v3/api-docs") as r:
        doc = json.load(r)

    if merged is None:
        merged = doc
        merged["info"] = {
            "title": "WTS – 모의투자 Web Trading System API",
            "version": doc["info"]["version"],
            "description": doc["info"]["description"],
        }
        continue

    merged["paths"].update(doc["paths"])
    known = {t["name"] for t in merged.get("tags", [])}
    merged.setdefault("tags", []).extend(t for t in doc.get("tags", []) if t["name"] not in known)

    # 같은 이름의 스키마는 common 모듈에서 온 ErrorResponse뿐이고 내용이 같다.
    # 내용까지 다르면 조용히 덮어쓰지 않고 멈춘다.
    for name, schema in doc.get("components", {}).get("schemas", {}).items():
        existing = merged["components"]["schemas"].get(name)
        if existing is not None and existing != schema:
            raise SystemExit(f"스키마 이름 충돌: {name} — 서비스마다 내용이 다르다. @Schema(name=...)로 구분한다.")
        merged["components"]["schemas"][name] = schema

# CLAUDE.md §24가 정의한 엔드포인트. 컨트롤러가 사라지거나 경로가 바뀌면 여기서 멈춘다.
# 생성물이 조용히 비는 것을 막기 위한 유일한 검사다.
EXPECTED = {
    ("get", "/api/market/stocks"),
    ("get", "/api/market/stocks/{symbol}"),
    ("get", "/api/market/stocks/{symbol}/price"),
    ("get", "/api/market/stocks/{symbol}/orderbook"),
    ("get", "/api/market/stocks/{symbol}/candles"),
    ("post", "/api/users/mock-login"),
    ("get", "/api/users/me"),
    ("get", "/api/users/me/watchlist"),
    ("post", "/api/users/me/watchlist/{symbol}"),
    ("delete", "/api/users/me/watchlist/{symbol}"),
    ("post", "/api/trading/orders"),
    ("get", "/api/trading/orders"),
    ("get", "/api/trading/orders/{orderId}"),
    ("delete", "/api/trading/orders/{orderId}"),
    ("get", "/api/trading/account"),
    ("get", "/api/trading/positions"),
    ("get", "/api/trading/portfolio"),
    ("get", "/api/trading/executions"),
}
actual = {(m, p) for p, ops in merged["paths"].items() for m in ops}
missing = EXPECTED - actual
if missing:
    raise SystemExit("명세에 빠진 엔드포인트 (CLAUDE.md §24): "
                     + ", ".join(f"{m.upper()} {p}" for m, p in sorted(missing)))

# 표 형식 명세서에 빈 칸이 생기지 않게 한다. 설명은 컨트롤러(@Operation)와 DTO(@Schema)에 쓴다.
blank = [f"{m.upper()} {p} (summary)" for p, ops in merged["paths"].items()
         for m, op in ops.items() if not op.get("summary")]
blank += [f"{name}.{field}" for name, schema in merged["components"]["schemas"].items()
          for field, prop in (schema.get("properties") or {}).items() if not prop.get("description")]
if blank:
    raise SystemExit("설명이 빠졌다: " + ", ".join(blank))

# 명세의 공개 여부(security: [])가 실제 판단 주체인 gateway public-paths 와 같은지 대조한다.
with open(gateway_yml, encoding="utf-8") as f:
    public_paths = next(d for d in yaml.safe_load_all(f) if d and "wts" in d)["wts"]["gateway"]["auth"]["public-paths"]
def is_public_path(path):
    for pattern in public_paths:
        if pattern.endswith("/**"):
            base = pattern[:-3]
            if path == base or path.startswith(base + "/"):
                return True
        elif path == pattern:
            return True
    return False
mismatch = [f"{m.upper()} {p}" for p, ops in merged["paths"].items() for m, op in ops.items()
            if (op.get("security") == []) != is_public_path(p)]
if mismatch:
    raise SystemExit("공개 여부가 gateway public-paths 와 다르다 (@SecurityRequirements 확인): " + ", ".join(mismatch))

# 끊어진 $ref 가 있으면 명세를 읽는 도구가 전부 실패한다.
dangling = {r for r in re.findall(r"#/components/schemas/([A-Za-z0-9_]+)", json.dumps(merged))
            if r not in merged["components"]["schemas"]}
if dangling:
    raise SystemExit(f"끊어진 $ref: {sorted(dangling)}")

merged["paths"] = dict(sorted(merged["paths"].items()))
merged["components"]["schemas"] = dict(sorted(merged["components"]["schemas"].items()))

with open(out, "w", encoding="utf-8") as f:
    f.write("# 이 파일은 생성물이다. 직접 고치지 말고 infra/scripts/generate-openapi.sh 를 다시 돌린다.\n")
    yaml.safe_dump(merged, f, allow_unicode=True, sort_keys=False, width=100)

print(f"{out}")
print(f"  경로 {len(merged['paths'])}개 · operation {len(actual)}개 · 스키마 {len(merged['components']['schemas'])}개")
PY

# 표 형식 명세서 (md / xlsx). openapi.yaml 만 읽으므로 서비스 없이도 따로 돌릴 수 있다.
python3 "$REPO_ROOT/infra/scripts/render-api-spec.py" "$OUT"
