# WTS – 모의투자 Web Trading System

실제 한국 주식시장 시세를 기반으로 가상 자금으로 시장가/지정가 주문을 연습하는 실시간 모의투자 WTS.

> **현재 진행 단계: Phase 7 – CI 완료 (MVP 전체 구현 완료)**
> Phase 0(모노레포 · 인프라 · 서비스 골격) → Phase 1(Mock Login · 가상 계좌 1억원)
> → Phase 2(Mock 시세 · Valkey · Kafka · 1분봉 · WebSocket · 실시간 가격 화면)
> → Phase 3(주문 · 시장가/지정가 · 예약 · 상태 머신 · Idempotency · 주문 화면)
> → Phase 4(체결 기록 · 원장 · Outbox · Kafka 이벤트 · 지정가 자동 체결)
> → Phase 5(종목 검색 · 관심종목 · 차트 · 포트폴리오 · 라우팅)
> → Phase 6(한국투자증권 OpenAPI 실시간 시세)에 이어
> GitLab CI 파이프라인과 컨테이너 이미지까지.
> CLAUDE.md §47의 Phase 0~7이 모두 끝났고, §2의 MVP 시나리오가
> 실제 시장 데이터로 처음부터 끝까지 동작한다.

---

## 사전 요구사항

| 도구 | 버전 | 비고 |
|---|---|---|
| Git | 최신 | |
| Docker Desktop | 최신 | MySQL / Kafka / Valkey 실행용. **실행 중이어야 한다** |
| Node.js | 20 이상 | 프론트엔드 |
| JDK | **설치 불필요** | Gradle Toolchain이 JDK 21을 자동으로 내려받는다 |

Gradle도 따로 설치하지 않는다. `./gradlew`가 알아서 처리한다.

---

## 빠른 시작

아래 6단계를 위에서 아래로 그대로 실행하면 된다.
**처음 실행은 이미지 내려받기와 JDK·의존성 다운로드 때문에 10분 정도 걸린다.** 두 번째부터는 1분 안쪽이다.

### 1. 클론

```bash
git clone https://github.com/sinsang2752/SeSac_Team3_WTS.git
cd SeSac_Team3_WTS
```

### 2. 환경변수 준비

```bash
cp .env.example .env
```

**그대로 두면 동작한다.** 수정이 필요한 경우는 포트가 겹칠 때뿐이다 → [문제 해결](#문제-해결) 참고.
`.env`는 git에 올라가지 않는다. 각자 로컬에서만 관리한다 (CLAUDE.md §33).

### 3. 인프라 기동 (MySQL · Kafka · Valkey)

```bash
docker compose up -d --wait
```

`--wait`는 세 컨테이너가 전부 healthy가 될 때까지 기다린다. 확인:

```bash
docker compose ps
# NAME         STATUS
# wts-kafka    Up (healthy)
# wts-mysql    Up (healthy)
# wts-valkey   Up (healthy)
```

### 4. 백엔드 4개 서비스 기동

```bash
./infra/scripts/run-backend.sh
```

백그라운드로 뜬다. **바로 끝난 것처럼 보이지만 실제 기동에는 시간이 걸린다.**
첫 실행은 JDK 21과 Gradle 의존성을 내려받느라 3~5분 정도 멈춘 것처럼 보인다. 정상이다.

진행 상황이 궁금하면 로그를 본다:

```bash
tail -f infra/.run/gateway-service.log   # Ctrl+C 로 빠져나온다
```

### 5. 기동 확인

```bash
./infra/scripts/health-check.sh
```

아래처럼 4개 모두 `HTTP 200 / UP`이 나와야 한다. 아직이면 1분 뒤 다시 실행한다.

```text
── 백엔드 서비스 health ───────────────────────────────
  gateway-service  :8080 HTTP 200  UP
  market-service   :8081 HTTP 200  UP
  trading-service  :8082 HTTP 200  UP
  user-service     :8083 HTTP 200  UP

Phase 0 완료조건 충족: 모든 서비스 health 200 / UP
```

### 6. 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

```text
  VITE v8.3.0  ready in 365 ms
  ➜  Local:   http://localhost:5173/
```

---

## 접속 주소

브라우저에서 **http://localhost:5173** 을 연다.

| 화면 | 주소 | 내용 |
|---|---|---|
| 트레이딩 | http://localhost:5173/wts/005930 | 실시간 시세 · 차트 · 호가 · 주문 |
| 거래내역 | http://localhost:5173/orders | 미체결 · 주문내역 · 체결내역 |
| 내 자산 | http://localhost:5173/portfolio | 예약금 · 평가손익 · 실현손익 |
| API Gateway | http://localhost:8080 | 백엔드 단일 진입점 |
| Kafka UI (선택) | http://localhost:8090 | `docker compose --profile tools up -d` 후 |

로그인은 **아무 이메일이나** 넣으면 된다. 처음 보는 이메일이면 계정과 가상 계좌 1억원이 함께 만들어진다.
가격은 1초마다 갱신되고, 로그인 후 바로 주문할 수 있다.

---

## 종료

```bash
# 프론트엔드: npm run dev 를 띄운 터미널에서 Ctrl+C
# 다른 터미널에서 띄웠다면
lsof -ti:5173 | xargs kill

# 백엔드
./infra/scripts/stop-backend.sh

# 인프라
docker compose down          # 데이터 유지
docker compose down -v       # 볼륨까지 삭제 (계좌·주문 기록 전부 초기화)
```

---

## 동작 확인

화면으로 확인하는 편이 빠르지만, API는 이렇게 생겼다.

**실시간 시세 (Phase 2)** — 인증이 필요 없다.

```bash
# 2초 간격으로 가격이 바뀐다
for i in 1 2 3; do curl -s localhost:8080/api/market/stocks/005930/price; echo; sleep 2; done

# Kafka를 거쳐 집계된 1분봉
curl -s "localhost:8080/api/market/stocks/005930/candles?interval=1m"
```

**가상 계좌 (Phase 1)** — 토큰이 필요하다.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/users/mock-login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@wts.local","nickname":"데모투자자"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')

curl -s http://localhost:8080/api/trading/account -H "Authorization: Bearer $TOKEN"
# {"accountId":1,...,"cashBalance":100000000.0000,"availableCash":100000000.0000}
```

**주문 (Phase 3)** — `Idempotency-Key`가 필수다.

```bash
# 시장가 매수 10주. 최신 시세로 즉시 체결된다.
curl -s -X POST http://localhost:8080/api/trading/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"symbol":"005930","side":"BUY","orderType":"MARKET","quantity":10}'
# {"orderId":1,...,"status":"FILLED","filledQuantity":10}

# 예수금이 줄고 포지션이 생겼는지 확인 (Phase 3 완료조건)
curl -s http://localhost:8080/api/trading/account   -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/trading/positions -H "Authorization: Bearer $TOKEN"

# 같은 키로 다시 보내도 주문은 하나다 (CLAUDE.md §14)
```

API 전체 명세는 [docs/api/README.md](docs/api/README.md) 참고.

---

## 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| `docker compose up` 이 포트 충돌로 실패 | 호스트에 이미 MySQL(3306) 또는 Redis(6379)가 떠 있다 | `.env` 에서 `MYSQL_PORT=3307`, `VALKEY_PORT=6380` 으로 바꾸고 다시 실행. 컨테이너 내부 포트는 그대로라 다른 설정은 건드릴 필요 없다 |
| health-check 가 `HTTP 000` 또는 `DOWN` | 아직 기동 중이다 | 1~2분 뒤 다시 실행. 그래도 안 되면 `tail -50 infra/.run/<service>.log` 로 원인 확인 |
| 백엔드 기동 시 `포트 이미 사용 중` 으로 건너뜀 | 이전에 띄운 프로세스가 남아 있다 | `./infra/scripts/stop-backend.sh` 후 다시 기동 |
| 주문이 `MARKET_PRICE_STALE` 로 거절 | `MARKET_PROVIDER=kis` 인데 장 시간(09:00~15:30 KST) 밖이다 | `.env` 를 `MARKET_PROVIDER=mock` 으로 바꾸고 백엔드 재기동 |
| 포트 5173 이 이미 사용 중 | 이전 Vite 가 살아 있다 | `lsof -ti:5173 \| xargs kill` |
| 화면은 뜨는데 가격이 안 나온다 | 백엔드가 아직 안 떴다 | `./infra/scripts/health-check.sh` 로 4개 UP 확인 |
| DB 를 처음 상태로 되돌리고 싶다 | | `docker compose down -v && docker compose up -d --wait` 후 백엔드 재기동 |

백엔드 재기동은 이 두 줄이다.

```bash
./infra/scripts/stop-backend.sh && ./infra/scripts/run-backend.sh
```
---

## 포트 구성

| 대상 | 기본 포트 | 환경변수 |
|---|---|---|
| gateway-service | 8080 | `GATEWAY_PORT` |
| market-service | 8081 | `MARKET_PORT` |
| trading-service | 8082 | `TRADING_PORT` |
| user-service | 8083 | `USER_PORT` |
| frontend (Vite) | 5173 | `FRONTEND_PORT` |
| MySQL | 3306 | `MYSQL_PORT` |
| Kafka | 9092 | `KAFKA_PORT` |
| Valkey | 6379 | `VALKEY_PORT` |
| Kafka UI (선택) | 8090 | `KAFKA_UI_PORT` |

Kafka UI는 프로파일로 분리되어 있다.

```bash
docker compose --profile tools up -d
```

---

## 저장소 구조

```text
.
├── frontend/                 React + TypeScript + Vite
│   ├── src/styles/           디자인 토큰 · 공통 스타일 (런스톡 시안)
│   ├── src/components/       Panel · Modal · Toast 등 공용 컴포넌트
│   ├── Dockerfile            빌드 → nginx 정적 서빙
│   └── nginx.conf            SPA 라우팅 + Gateway 프록시
├── backend/                  Gradle 멀티프로젝트
│   ├── Dockerfile            4개 서비스 공용 (SERVICE 빌드 인자)
│   ├── common/               인증 토큰 · 공통 헤더 · 에러 응답
│   ├── gateway-service/      클라이언트 단일 진입점 (WebFlux)
│   ├── market-service/       시세 수집 · 캐시 · 이벤트 발행 · 1분봉 · WebSocket
│   ├── trading-service/      주문 · 체결 · 계좌 · 포지션 · 원장
│   └── user-service/         사용자 · 관심종목
├── infra/
│   ├── docker/               컨테이너 초기화 스크립트
│   └── scripts/              로컬 실행 · 검증 스크립트
├── docs/
│   ├── architecture/
│   ├── api/
│   └── decisions/            ADR
├── docker-compose.yml        인프라 (MySQL · Kafka · Valkey)
├── docker-compose.app.yml    애플리케이션까지 컨테이너로
├── .gitlab-ci.yml            CI 파이프라인
└── CLAUDE.md
```

---

## 개발 명령

```bash
# 백엔드 전체 빌드 + 테스트
cd backend && ./gradlew build

# 특정 서비스만 테스트
cd backend && ./gradlew :trading-service:test

# 단일 서비스 실행
cd backend && ./gradlew :market-service:bootRun

# 프론트엔드
cd frontend && npm run dev      # 개발 서버
cd frontend && npm run build    # 타입체크 + 프로덕션 빌드
cd frontend && npm run lint
```

---

## 문서

| 문서 | 내용 |
|---|---|
| [docs/api/](docs/api/README.md) | REST API 명세 |
| [docs/architecture/phase0-bootstrap.md](docs/architecture/phase0-bootstrap.md) | 인프라 · 서비스 구성 |
| [docs/architecture/phase1-user-account.md](docs/architecture/phase1-user-account.md) | 인증 흐름 · 도메인 구조 · 스키마 |
| [docs/architecture/phase2-mock-market.md](docs/architecture/phase2-mock-market.md) | 시세 파이프라인 · 1분봉 · WebSocket · 프론트엔드 상태 |
| [docs/architecture/phase3-trading.md](docs/architecture/phase3-trading.md) | 주문 흐름 · 상태 머신 · 예약 · 동시성 |
| [docs/architecture/phase4-execution-ledger.md](docs/architecture/phase4-execution-ledger.md) | 체결 정산 · 원장 · Outbox · 자동 체결 |
| [docs/architecture/phase5-wts-mvp.md](docs/architecture/phase5-wts-mvp.md) | 라우팅 · 관심종목 · 차트 · 포트폴리오 |
| [docs/architecture/phase6-kis-integration.md](docs/architecture/phase6-kis-integration.md) | KIS 인증 · 실시간 프레임 · 연결 관리 |
| [docs/architecture/phase7-ci.md](docs/architecture/phase7-ci.md) | CI 파이프라인 · 컨테이너 이미지 |
| [docs/architecture/erd.md](docs/architecture/erd.md) | 논리 데이터 모델 (ERD) |
| [docs/decisions/](docs/decisions/) | ADR (설계 결정 기록) |

## 시세 출처 바꾸기

```env
MARKET_PROVIDER=mock   # 기본값. 자격증명 없이 언제든 동작한다 (CLAUDE.md §0-4)
MARKET_PROVIDER=kis    # 한국투자증권 OpenAPI 실시간 시세
```

`kis` 로 쓰려면 루트 `.env` 에 자격증명이 필요하다. `.env` 는 git에 올라가지 않는다 (§33).

```env
KIS_ENVIRONMENT=vts    # vts 모의투자 / real 실전투자 — 도메인도 앱키도 다르다
KIS_APP_KEY=
KIS_APP_SECRET=
```

**장 운영시간(09:00~15:30 KST) 밖에서는 `kis` 모드로 시장가 주문이 거절된다.**
마지막 체결 시각이 멈춰 시세가 stale이 되기 때문이다 (§11). 아무 때나 거래 흐름을
연습하려면 `mock` 으로 되돌린다.

## 컨테이너로 전체 실행

평소 개발은 인프라만 컨테이너로 띄우고 애플리케이션은 로컬 프로세스로 돌리는 편이 빠르다.
아래는 **배포 형태 그대로 도는지** 확인할 때 쓴다. CI가 만드는 것과 같은 이미지다.

```bash
./infra/scripts/build-images.sh
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d
open http://localhost:5173

docker compose -f docker-compose.yml -f docker-compose.app.yml down
```

## CI

`.gitlab-ci.yml` 이 §37의 파이프라인을 그대로 따른다.

```
lint → test → build → integration → docker
```

| 명령 | 개수 | 용도 |
|---|---|---|
| `./gradlew test` | 152 | 단위 테스트. Docker 불필요 |
| `./gradlew integrationTest` | 74 | Testcontainers 통합 테스트 |
| `./gradlew build` | 226 | 둘 다 (로컬 기본) |

통합 테스트와 이미지 빌드는 Docker-in-Docker를 쓴다. **GitLab 러너가 `privileged` 모드여야
한다.** 자세한 내용은 [docs/architecture/phase7-ci.md](docs/architecture/phase7-ci.md).

## 다음 단계

CLAUDE.md §47의 Phase 0~7이 모두 끝났다. 남은 것은 §48의 확장 범위다.

- 화면 디자인은 런스톡(learnstock) 시안을 적용했다.
  토큰은 `frontend/src/styles/tokens.css` 하나에 모여 있다 (§30).
  기준 문서는 [docs/learnstock/ui-requirements.md](docs/learnstock/ui-requirements.md).
- 이미지 레지스트리 푸시 · SonarQube · Argo CD (§38)
- Amazon Cognito · EKS/GKE · Terraform · OpenTelemetry (§48)
