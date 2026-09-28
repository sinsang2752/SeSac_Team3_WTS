# CLAUDE.md
# Mock Stock Trading WTS MVP – Claude Code Implementation Guide

> 목적: Claude Code가 이 문서만 읽고도 MVP를 단계적으로 구현할 수 있도록 한다.
> 프로젝트 유형: 실제 시장 데이터를 활용하는 **모의투자 WTS (Web Trading System)**
> 디자인: **Figma에서 별도 설계**한다. Claude Code는 디자인을 임의로 과도하게 만들지 말고, 기능 중심의 React 컴포넌트와 명확한 UI 구조를 구현한다.
> UI/UX 참고 방향: **토스증권 WTS**, **KB 계열 증권 WTS 스타일**
> 주의: 특정 서비스의 UI를 그대로 복제하지 말고, 정보 구조와 사용자 흐름만 참고한다.

---

# 0. 최우선 지침

이 프로젝트는 3인 팀의 MVP 프로젝트다.

아래 원칙을 반드시 지킨다.

1. **MVP가 먼저 동작하도록 구현한다.**
2. AWS/GCP 멀티클라우드, DR, Cognito MRR, 고가용성 Kafka 등은 MVP 완료 후 확장한다.
3. 로컬 개발 환경에서 `docker compose up` 후 주요 기능이 실행되어야 한다.
4. 외부 한국투자증권 API 자격증명이 없어도 **Mock Market Mode**로 전체 서비스가 동작해야 한다.
5. 비즈니스 로직을 Controller, Repository, Kafka Consumer에 흩뿌리지 않는다.
6. Trading Core는 데이터 정합성이 가장 중요하다.
7. Market Service와 Trading Service는 강하게 결합하지 않는다.
8. Market Service가 한국투자증권 OpenAPI와 직접 통신한다.
9. Spring Cloud Gateway는 **클라이언트의 Ingress** 용도다. KIS OpenAPI 호출을 중계하지 않는다.
10. MySQL은 거래 데이터의 Source of Truth이다.
11. Valkey는 최신 시세/호가 등 재생성 가능한 실시간 캐시 용도다.
12. Kafka는 이벤트 스트림 용도다.
13. 구현이 복잡해질 경우 “실서비스 수준 기능”보다 “정확하게 동작하는 MVP”를 우선한다.
14. 새로운 라이브러리를 추가하기 전에 기존 스택으로 해결할 수 있는지 확인한다.
15. 모든 주요 구현은 테스트 가능하게 작성한다.

---

# 1. 프로젝트 한 줄 설명

**실제 한국 주식시장 시세를 기반으로 사용자가 가상 자금을 이용해 시장가/지정가 주문을 연습할 수 있는 실시간 모의투자 WTS**

---

# 2. MVP 성공 기준

아래 시나리오가 처음부터 끝까지 동작하면 MVP가 완성된 것으로 본다.

```text
사용자 로그인(초기에는 Mock 가능)
        ↓
종목 검색
        ↓
실시간 현재가 조회
        ↓
차트 표시
        ↓
매수/매도 주문
        ↓
주문 검증
        ↓
모의 체결
        ↓
가상 예수금 / 보유수량 반영
        ↓
주문/체결 내역 조회
        ↓
포트폴리오/평가손익 확인
```

추가 성공 기준:

- 한국투자증권 API 연결이 없어도 Mock 시세로 위 전체 흐름이 동작한다.
- Kafka가 중단되었다가 복구되어도 주요 거래 DB 상태가 손상되지 않는다.
- 동일 주문 요청이 중복 생성되지 않는다.
- 사용자의 예수금은 음수가 될 수 없다.
- 보유한 수량보다 많이 매도할 수 없다.
- Market Service 장애가 Trading DB 정합성을 깨뜨리지 않는다.

---

# 3. MVP에서 제외할 범위

아래 기능은 1차 MVP에서 구현하지 않는다.

- 실제 주식 주문
- 실제 증권 계좌
- 실제 현금 입출금
- KRX 직접 연결
- 실제 청산/결제
- 신용거래 / 미수거래 / 대출
- 파생상품
- 공매도
- 완전한 호가 기반 Matching Engine
- 복잡한 부분체결
- Event Sourcing
- Saga 기반 분산 트랜잭션
- Active-Active Multi-Cloud
- AWS → GCP 자동 Failover
- RPO 0 보장
- Cognito Multi-Region Replication
- 운영 수준 Kafka Multi-Cluster DR
- 모바일 앱
- Figma 없이 임의로 복잡한 UI 디자인

---

# 4. 확정 기술 스택

## Frontend

```text
React
TypeScript
Vite
Zustand
TanStack Query
TradingView Lightweight Charts
WebSocket
Axios 또는 fetch
```

UI 스타일은 Figma 산출물에 맞춘다.

초기에는 최소한의 CSS 또는 디자인 토큰 placeholder만 사용한다.

## Backend

```text
Java 21
Spring Boot 3.x
Spring Cloud
Spring Cloud Gateway
Spring Security
Spring Data JPA
Spring Validation
Spring Kafka
Spring Data Redis
WebSocket
Resilience4j
Micrometer
Gradle
```

## Data / Messaging

```text
MySQL 8.x
Valkey
Apache Kafka
```

## Local Infra

```text
Docker
Docker Compose
```

## CI/CD

MVP 단계:

```text
Self-Managed GitLab
GitLab CI
GitLab Runner
```

MVP 이후:

```text
Argo CD
Kubernetes
AWS EKS
GCP GKE
Terraform
```

## Observability – MVP 이후 또는 MVP 후반

```text
OpenTelemetry
Prometheus
Grafana
Loki
Tempo
```

---

# 5. 전체 논리 아키텍처

```text
                       ┌─────────────────────┐
                       │     React WTS       │
                       └──────────┬──────────┘
                                  │
                           REST / WebSocket
                                  │
                                  ▼
                      Spring Cloud Gateway
                                  │
             ┌────────────────────┼────────────────────┐
             │                    │                    │
             ▼                    ▼                    ▼
      Market Service       Trading Service       User Service
             │                    │                    │
             │                    │                    │
             │                    ▼                    ▼
             │                  MySQL                MySQL
             │
             ├─────── Kafka ────────────────┐
             │                              │
             ▼                              ▼
           Valkey                    Trading Consumers
             │
             ▼
      Latest Market State


한국투자증권 OpenAPI
          │
          │ REST / WebSocket
          ▼
     Market Service

※ KIS OpenAPI는 Spring Cloud Gateway를 거치지 않는다.
```

---

# 6. 물리 서비스 구성

## 6.1 gateway-service

역할:

- API Routing
- 인증 필터
- CORS
- Rate Limiting은 구조만 마련
- 공통 Trace ID 생성
- REST 요청 라우팅
- WebSocket Route

경로 예:

```text
/api/market/**
→ market-service

/api/trading/**
→ trading-service

/api/users/**
→ user-service

/ws/market/**
→ market-service
```

## 6.2 market-service

역할:

- 한국투자증권 OpenAPI 연동
- 실시간 체결가 수신
- 실시간 호가 수신
- 다중 종목 Subscription 관리
- 최신 시세 Valkey 저장
- Kafka Market Event 발행
- 1분봉 생성
- WTS WebSocket 실시간 송신
- Mock Market Mode 제공

중요:

```text
Market Service ↔ Trading Service
```

직접 HTTP 호출을 기본 구조로 사용하지 않는다.

Trading Service가 시세가 필요하면:

1. Kafka `market.price.updated` 이벤트를 소비하거나
2. Valkey의 최신 가격을 조회한다.

## 6.3 trading-service

MVP 핵심 서비스다.

한 Spring Boot Application 내부에서 다음 모듈을 분리한다.

```text
trading-service
├── order
├── execution
├── account
├── position
├── portfolio
└── ledger
```

별도의 마이크로서비스로 쪼개지 않는다.

이유:

```text
주문 → 자금 예약 → 체결 → 잔고 변경 → 포지션 변경
```

이 흐름은 데이터 정합성이 강하게 요구되기 때문이다.

## 6.4 user-service

역할:

- 사용자 기본정보
- 프로필
- 관심종목
- 사용자 설정
- 인증 사용자 매핑

MVP 인증은 아래 중 하나를 선택한다.

우선순위:

```text
1. Mock Login
2. Spring Security 간단 인증
3. Amazon Cognito 연동
```

Cognito는 MVP 완료 후 붙여도 된다.

---

# 7. 서비스 내부 아키텍처

각 Spring Boot 서비스는 가능한 한 아래 원칙을 따른다.

```text
Hexagonal Architecture
+
DDD-lite
+
Spring MVC
```

예시:

```text
order/

├── domain/
│   ├── Order.java
│   ├── OrderId.java
│   ├── OrderStatus.java
│   ├── OrderSide.java
│   ├── OrderType.java
│   └── OrderRepository.java
│
├── application/
│   ├── command/
│   ├── query/
│   └── service/
│
└── adapter/
    ├── in/
    │   ├── web/
    │   └── kafka/
    │
    └── out/
        ├── persistence/
        ├── kafka/
        └── cache/
```

---

# 8. 패키지 설계 규칙

금지:

```text
controller/
service/
repository/
entity/
```

만으로 애플리케이션 전체를 수평 분리하지 않는다.

대신 **도메인 기준**으로 먼저 분리한다.

좋은 예:

```text
com.team.wts.trading

├── order/
├── execution/
├── account/
├── position/
├── ledger/
└── common/
```

---

# 9. Domain Rules

## 9.1 초기 가상자금

신규 사용자에게:

```text
100,000,000 KRW
```

의 가상 자금을 지급한다.

환경변수 또는 설정값으로 변경 가능하게 한다.

```yaml
trading:
  initial-cash: 100000000
```

## 9.2 예수금

계좌는 최소 다음 값을 가진다.

```text
cash_balance
reserved_cash
```

주문 가능 금액:

```text
available_cash
=
cash_balance - reserved_cash
```

## 9.3 포지션

종목별:

```text
quantity
reserved_quantity
average_price
```

매도 가능 수량:

```text
available_quantity
=
quantity - reserved_quantity
```

## 9.4 매수 주문

매수 주문 시:

```text
available_cash >= required_amount
```

이어야 한다.

지정가 주문:

```text
required_amount = limit_price * quantity
```

시장가 주문은 MVP에서 최신 시세 기준으로 계산한다.

## 9.5 매도 주문

```text
available_quantity >= order_quantity
```

이어야 한다.

그렇지 않으면 Reject한다.

## 9.6 주문 상태

```text
RECEIVED
VALIDATED
ACCEPTED
FILLED
CANCELLED
REJECTED
```

MVP에서는 `PARTIALLY_FILLED`는 선택 기능이다.

## 9.7 주문 종류

```text
MARKET
LIMIT
```

## 9.8 매수 / 매도

```text
BUY
SELL
```

---

# 10. Execution Strategy

Strategy Pattern을 사용한다.

```java
public interface ExecutionStrategy {

    boolean canExecute(
        Order order,
        MarketPrice price
    );

    ExecutionResult execute(
        Order order,
        MarketPrice price
    );
}
```

구현체:

```text
MarketOrderExecutionStrategy
LimitOrderExecutionStrategy
```

---

# 11. 시장가 체결 규칙

MVP:

```text
현재 최신 가격 = 체결가격
```

시장가 매수/매도 요청은 최신 가격이 존재하면 즉시 체결한다.

최신 시세가 TTL을 초과하여 stale 상태이면 주문을 거절한다.

예:

```text
market.price.max-age = 5 sec
```

---

# 12. 지정가 체결 규칙

BUY:

```text
current_price <= limit_price
```

이면 체결 가능.

SELL:

```text
current_price >= limit_price
```

이면 체결 가능.

미체결 주문은 `ACCEPTED` 상태로 유지한다.

새로운:

```text
market.price.updated
```

이벤트 수신 시 미체결 주문을 검사한다.

---

# 13. 락 / 동시성

거래 정합성을 위해 반드시 고려한다.

Account / Position 수정 시 Lock 순서를 통일한다.

```text
Account
→ Position
```

순서로 잠근다.

MVP에서는 다음 중 하나를 선택한다.

권장:

```text
Pessimistic Lock
```

또는 명확한 Version 관리가 가능하다면:

```text
Optimistic Lock
```

AI가 임의로 Redis Distributed Lock부터 도입하지 않는다.

---

# 14. Idempotency

주문 API는 중복 요청을 방지해야 한다.

Header 예:

```http
Idempotency-Key: UUID
```

동일 Key로 동일 사용자가 재요청하면:

- 주문을 새로 생성하지 않는다.
- 기존 주문 결과를 반환한다.

MySQL Unique Constraint 또는 별도 idempotency table 사용 가능.

---

# 15. Transactional Outbox

DB Transaction과 Kafka Publish 사이의 Dual Write 문제를 방지한다.

예:

```text
BEGIN

INSERT orders
UPDATE accounts
INSERT outbox_events

COMMIT
```

별도 Publisher가:

```text
outbox_events
↓
Kafka
```

로 전달한다.

MVP에서는 Polling Publisher를 사용해도 된다.

CDC/Debezium은 MVP 필수가 아니다.

---

# 16. Kafka Topic

최소 Topic:

```text
market.price.updated
order.created
order.accepted
order.cancelled
execution.completed
account.balance.changed
position.changed
ledger.created
audit.logged
```

모든 Event에 아래 Metadata를 넣는다.

```json
{
  "eventId": "uuid",
  "eventType": "execution.completed",
  "occurredAt": "ISO-8601",
  "traceId": "uuid",
  "aggregateId": "string",
  "version": 1,
  "payload": {}
}
```

---

# 17. Kafka Consumer 규칙

Consumer는 반드시 중복 이벤트 가능성을 고려한다.

```text
At-Least-Once
```

를 기본 가정한다.

Consumer 처리 시:

```text
eventId
```

기준으로 중복처리를 방지한다.

실패 이벤트는 MVP 후반에 DLQ를 적용한다.

예:

```text
execution.completed.dlq
```

---

# 18. Valkey 사용 원칙

Valkey는 다음 용도:

```text
최신 현재가
최신 호가
현재 1분봉 상태
종목 기본 메타 캐시
WebSocket fan-out 보조
```

예시 Key:

```text
market:price:005930
market:orderbook:005930
market:candle:1m:005930
market:meta:005930
```

Valkey를 다음 용도로 사용하지 않는다.

```text
계좌 원본 데이터
주문 원본 데이터
원장 원본 데이터
체결 원본 데이터
```

위 데이터의 Source of Truth는 MySQL이다.

---

# 19. KIS OpenAPI Adapter

도메인 계층에서 KIS 구현체를 직접 참조하지 않는다.

Port:

```java
public interface MarketDataProvider {

    MarketPrice getCurrentPrice(String symbol);

    void subscribe(String symbol);

    void unsubscribe(String symbol);
}
```

구현:

```text
KisMarketDataAdapter
MockMarketDataAdapter
```

Profile:

```text
spring.profiles.active=mock
```

또는:

```text
spring.profiles.active=kis
```

로 전환 가능하게 한다.

---

# 20. 한국투자증권 WebSocket

고려사항:

- Approval Key
- 실시간 체결 TR
- 실시간 호가 TR
- 다중 종목 구독
- reconnect
- heartbeat
- backoff
- subscription restore

구독 제한 숫자는 코드에 하드코딩하지 않는다.

설정값으로 둔다.

```yaml
kis:
  websocket:
    subscription-limit: 40
```

---

# 21. Subscription Manager

최종 설계는 동적 구독 가능하도록 한다.

종목을 구독하는 조건:

```text
active_viewer_count > 0
OR
pending_order_count > 0
OR
system_required = true
```

MVP에서는 처음에는 고정 종목 목록으로 시작해도 된다.

예:

```yaml
market:
  default-symbols:
    - "005930"
    - "000660"
    - "035420"
    - "035720"
```

이후 Dynamic Subscription으로 발전시킨다.

---

# 22. 1분봉

Kafka의 체결 이벤트를 이용한다.

```text
09:00:00 ~ 09:00:59

Open
High
Low
Close
Volume
```

을 계산한다.

최종 1분봉은 DB에 저장한다.

테이블 예:

```text
candles_1m
```

MVP에서 체결 데이터가 부족하면 Mock Price Stream으로 캔들 생성 가능.

---

# 23. Database Schema Outline

## users

```text
id
email
nickname
created_at
updated_at
```

## virtual_accounts

```text
id
user_id
cash_balance
reserved_cash
created_at
updated_at
version
```

## positions

```text
id
account_id
symbol
quantity
reserved_quantity
average_price
created_at
updated_at
version
```

Unique:

```text
(account_id, symbol)
```

## orders

```text
id
account_id
symbol
side
order_type
quantity
limit_price
filled_quantity
status
idempotency_key
created_at
updated_at
```

## executions

```text
id
order_id
symbol
side
price
quantity
executed_at
```

## ledger_entries

```text
id
account_id
order_id nullable
execution_id nullable
type
amount
before_balance
after_balance
created_at
```

Ledger Type 예:

```text
INITIAL_DEPOSIT
BUY
SELL
FEE
ADJUSTMENT
```

MVP 수수료는 `0`으로 둘 수 있다.

## watchlists

```text
id
user_id
symbol
created_at
```

## outbox_events

```text
id
aggregate_type
aggregate_id
event_type
payload_json
status
created_at
published_at
retry_count
```

## processed_events

```text
event_id
consumer_name
processed_at
```

Kafka Consumer idempotency용.

## candles_1m

```text
symbol
open_time
open
high
low
close
volume
```

Primary Key:

```text
(symbol, open_time)
```

---

# 24. REST API

## User

```http
POST /api/users/mock-login
GET /api/users/me
GET /api/users/me/watchlist
POST /api/users/me/watchlist/{symbol}
DELETE /api/users/me/watchlist/{symbol}
```

## Market

```http
GET /api/market/stocks
GET /api/market/stocks/{symbol}
GET /api/market/stocks/{symbol}/price
GET /api/market/stocks/{symbol}/orderbook
GET /api/market/stocks/{symbol}/candles?interval=1m
```

## Trading

```http
POST /api/trading/orders
GET /api/trading/orders
GET /api/trading/orders/{orderId}
DELETE /api/trading/orders/{orderId}
GET /api/trading/account
GET /api/trading/positions
GET /api/trading/portfolio
GET /api/trading/executions
```

주문 Request:

```json
{
  "symbol": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "limitPrice": 80000
}
```

Header:

```http
Idempotency-Key: UUID
```

---

# 25. WebSocket Contract

Endpoint 예:

```text
/ws/market
```

Client Subscribe:

```json
{
  "type": "SUBSCRIBE",
  "symbols": ["005930", "000660"]
}
```

Unsubscribe:

```json
{
  "type": "UNSUBSCRIBE",
  "symbols": ["005930"]
}
```

Server Price Update:

```json
{
  "type": "PRICE",
  "symbol": "005930",
  "price": 80000,
  "change": 1000,
  "changeRate": 1.27,
  "volume": 123456,
  "timestamp": "2026-09-21T10:30:00+09:00"
}
```

Server Orderbook:

```json
{
  "type": "ORDERBOOK",
  "symbol": "005930",
  "asks": [],
  "bids": [],
  "timestamp": "..."
}
```

---

# 26. Frontend Pages

Figma가 디자인한다.

Claude Code는 기능 기준으로 아래 Route와 Component 구조를 먼저 만든다.

```text
/login
/wts
/wts/:symbol
/orders
/portfolio
```

---

# 27. WTS 화면 필수 영역

대표 화면:

```text
┌───────────────────────────────────────┐
│ Search / User / Account Summary       │
├───────────────┬───────────────────────┤
│ Watchlist     │ Stock Header          │
│               │ Current Price         │
│               ├───────────────────────┤
│               │ Chart                 │
│               ├───────────┬───────────┤
│               │ Orderbook │ Order     │
│               │           │ Panel     │
├───────────────┴───────────┴───────────┤
│ Position / Open Orders / Executions   │
└───────────────────────────────────────┘
```

Figma 산출물이 나오기 전에는 이 구조를 유지하는 최소 UI만 구현한다.

---

# 28. Frontend Component 후보

```text
StockSearch
Watchlist
StockHeader
RealtimePrice
StockChart
OrderBook
OrderForm
OpenOrders
ExecutionHistory
PortfolioSummary
PositionTable
AccountSummary
ConnectionStatus
```

---

# 29. Frontend State

Zustand:

```text
userStore
marketStore
tradingStore
websocketStore
```

TanStack Query:

```text
종목 검색
계좌 조회
포트폴리오 조회
주문 조회
체결 조회
```

WebSocket:

```text
실시간 현재가
호가
체결 알림
```

---

# 30. 디자인 구현 원칙

Figma가 최종 디자인의 Source of Truth다.

Claude Code는:

- 임의의 화려한 색상 사용 금지
- 임의의 애니메이션 남발 금지
- 특정 증권사 UI 복제 금지
- 컴포넌트 재사용 가능하게 구현
- Design Token 적용 가능 구조 마련

예:

```css
:root {
  --color-primary: #16a34a;
  --color-background: #ffffff;
  --color-surface: #f8fafc;
  --color-text: #0f172a;
}
```

위 값은 Placeholder이며 Figma에 맞춰 교체 가능하게 한다.

---

# 31. Mock Market Mode

KIS API 없이 반드시 실행되어야 한다.

Mock Mode는:

```text
005930
000660
035420
035720
005380
```

등의 종목에 대해 가격을 생성한다.

예:

```text
삼성전자 80,000
± 0.1 ~ 0.5%
```

정도의 Random Walk를 사용할 수 있다.

하지만 테스트는 Random에 의존하지 않게 Seed 또는 Fixed Sequence 사용 가능하도록 한다.

---

# 32. Local Docker Compose

최소 구성:

```text
MySQL
Kafka
Valkey
Kafka UI optional
```

예상:

```yaml
services:
  mysql:
  kafka:
  valkey:
  kafka-ui:
```

백엔드와 Frontend는 개발 중 로컬 프로세스로 실행해도 된다.

후반에는 Docker Compose 전체 실행도 지원한다.

---

# 33. 환경변수

예:

```env
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=wts
MYSQL_USER=wts
MYSQL_PASSWORD=wts

KAFKA_BOOTSTRAP_SERVERS=localhost:9092

VALKEY_HOST=localhost
VALKEY_PORT=6379

KIS_APP_KEY=
KIS_APP_SECRET=
KIS_ACCOUNT_NO=

MARKET_PROVIDER=mock
```

Secret은 Git에 Commit하지 않는다.

---

# 34. Repository Structure

Monorepo 권장:

```text
wts-platform/

├── frontend/
│
├── backend/
│   ├── gateway-service/
│   ├── market-service/
│   ├── trading-service/
│   └── user-service/
│
├── infra/
│   ├── docker/
│   └── scripts/
│
├── docs/
│   ├── architecture/
│   ├── api/
│   └── decisions/
│
├── .gitlab-ci.yml
├── docker-compose.yml
└── CLAUDE.md
```

---

# 35. Testing

Backend 필수:

```text
JUnit 5
Mockito
Spring Boot Test
Testcontainers
```

특히 아래는 반드시 테스트한다.

```text
잔고 부족 매수 거절
보유수량 초과 매도 거절
동일 Idempotency Key 중복 주문 방지
시장가 체결
지정가 미체결
지정가 가격조건 충족 시 체결
체결 후 Cash Balance 변경
체결 후 Position 변경
평균단가 계산
매도 후 실현손익 계산
이미 체결된 주문 취소 거절
```

---

# 36. Integration Test

Testcontainers:

```text
MySQL
Kafka
Valkey
```

를 사용한다.

테스트 시나리오:

```text
market.price.updated 발생
→ 지정가 주문 체결
→ execution 생성
→ account 변경
→ position 변경
→ ledger 생성
```

전체 흐름을 검증한다.

---

# 37. CI – GitLab

MVP CI Pipeline:

```text
Push / Merge Request
      ↓
Frontend Lint
Frontend Build
      ↓
Backend Compile
Backend Unit Test
      ↓
Integration Test
      ↓
Docker Build
```

아직 Kubernetes Deploy는 필수가 아니다.

---

# 38. GitLab CI Stage 예

```yaml
stages:
  - lint
  - test
  - build
  - integration
  - docker
```

MVP 완료 후:

```text
SonarQube
Image Registry Push
GitOps Repository Update
Argo CD
```

를 추가한다.

---

# 39. Error Response

공통 형식:

```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "주문 가능한 예수금이 부족합니다.",
  "traceId": "uuid",
  "timestamp": "..."
}
```

Domain Error Code:

```text
INSUFFICIENT_BALANCE
INSUFFICIENT_POSITION
INVALID_ORDER_STATE
MARKET_PRICE_UNAVAILABLE
MARKET_PRICE_STALE
ORDER_NOT_FOUND
DUPLICATE_ORDER_REQUEST
```

---

# 40. Logging

모든 요청에:

```text
traceId
```

를 생성한다.

주요 거래 이벤트에는:

```text
traceId
userId
accountId
orderId
symbol
```

을 구조화 로그로 남긴다.

비밀번호, Secret, Token 전체값은 로그에 기록하지 않는다.

---

# 41. Audit Event

MVP 후반 적용:

```text
LOGIN_SUCCESS
ORDER_CREATED
ORDER_CANCELLED
EXECUTION_COMPLETED
BALANCE_CHANGED
POSITION_CHANGED
```

Kafka:

```text
audit.logged
```

로 발행 가능하게 만든다.

---

# 42. 금액 타입

금융 데이터에 `double`, `float` 사용 금지.

Java:

```text
BigDecimal
```

사용.

DB:

```text
DECIMAL
```

사용.

수량:

```text
BIGINT
```

또는 필요에 맞는 정수 타입.

---

# 43. 시간

Backend 저장:

```text
UTC
```

권장.

Frontend 표현:

```text
Asia/Seoul
```

한국 장 운영시간과 시세 Timestamp 변환을 명확히 한다.

---

# 44. 수수료

MVP:

```text
fee = 0
tax = 0
```

가능.

단, 향후 변경 가능하도록 Calculator Interface로 분리.

```java
TradingFeeCalculator
```

---

# 45. CQRS-lite

완전한 CQRS를 구현하지 않는다.

패키지와 Use Case 수준에서:

```text
Command
Query
```

를 분리한다.

예:

```text
command/
    PlaceOrderCommand
    CancelOrderCommand

query/
    GetOrdersQuery
    GetPortfolioQuery
```

별도 Read DB는 만들지 않는다.

---

# 46. 사용하지 않을 패턴

1차 MVP에서는 다음을 추가하지 않는다.

```text
Event Sourcing
Full CQRS
Saga
2PC
Distributed Transaction
Service Mesh
Multi-region Kafka
Complex Matching Engine
```

---

# 47. 구현 순서

Claude Code는 한 번에 전체 프로젝트를 생성하지 말고 아래 순서로 구현한다.

## Phase 0 – Bootstrap

- Monorepo 생성
- React + Vite 생성
- Spring Boot 서비스 생성
- Docker Compose
- MySQL
- Kafka
- Valkey
- Health Check

완료조건:

```text
모든 서비스가 실행되고 health endpoint가 200
```

## Phase 1 – User / Account Skeleton

- Mock Login
- User
- Virtual Account
- 1억원 초기 지급
- Account API

완료조건:

```text
GET /api/trading/account
```

에서 1억원 확인.

## Phase 2 – Mock Market

- MockMarketDataAdapter
- Kafka price event
- Valkey latest price
- Market REST API
- WebSocket
- Frontend 가격 표시

완료조건:

WTS 화면에서 가격이 실시간 변경.

## Phase 3 – Trading

- Order
- Account
- Position
- Market Order
- Limit Order
- Reserved Cash
- Reserved Quantity
- State Machine
- Idempotency

완료조건:

시장가 매수 후:

```text
cash 감소
position 증가
```

확인.

## Phase 4 – Execution + Ledger

- Execution
- Ledger
- Outbox
- Kafka Events
- Pending Limit Order

완료조건:

```text
지정가 주문
→ 가격조건 도달
→ 자동 체결
→ Ledger 생성
```

## Phase 5 – WTS MVP

- 종목 검색
- Watchlist
- 실시간 가격
- 차트
- 주문 Form
- 포지션
- 미체결 주문
- 체결내역
- 포트폴리오

Figma 디자인이 있으면 디자인 적용.

## Phase 6 – KIS Integration

- Approval Key
- WebSocket Connect
- Reconnect
- Subscribe
- Unsubscribe
- TR Parsing
- Adapter Switch

완료조건:

```text
MARKET_PROVIDER=kis
```

로 실제 데이터 수신.

## Phase 7 – CI

- GitLab CI
- Frontend Build
- Backend Test
- Integration Test
- Docker Build

---

# 48. Phase 8 이후 확장

MVP 이후 별도 작업:

```text
Amazon Cognito
AWS EKS
AWS RDS MySQL
AWS ElastiCache/Valkey
AWS MSK
AWS ECR
GCP GKE
GCP Cloud SQL
GCP Memorystore
Terraform
Argo CD
AWS → GCP DR
DB Replication
RPO / RTO 측정
OpenTelemetry
Prometheus
Grafana
Loki
Tempo
```

---

# 49. Definition of Done

MVP 최종 데모:

1. 사용자가 로그인한다.
2. 가상 자금 1억원을 확인한다.
3. 삼성전자를 검색한다.
4. 실시간 가격과 차트를 확인한다.
5. 시장가 매수 주문을 한다.
6. 즉시 체결된다.
7. 예수금이 감소한다.
8. 보유 삼성전자 수량이 증가한다.
9. 평가손익이 실시간 가격에 따라 변경된다.
10. 지정가 주문을 등록한다.
11. 가격이 조건에 도달하면 자동 체결된다.
12. 주문/체결 내역을 확인한다.
13. Ledger에서 자산변경을 확인할 수 있다.
14. 동일 Idempotency Key 요청은 중복 주문되지 않는다.
15. Mock/KIS Market Provider를 설정만으로 변경할 수 있다.

---

# 50. Claude Code 작업 규칙

Claude Code는 작업 시작 전:

1. 현재 Repository 구조를 확인한다.
2. 이미 구현된 기능을 확인한다.
3. 이 문서와 충돌하는 기존 코드가 있으면 먼저 보고한다.
4. 작업 계획을 짧게 제시한다.
5. 변경 파일 목록을 제시한다.
6. 구현한다.
7. 테스트한다.
8. 테스트 결과를 보고한다.

---

# 51. 한 번에 수정할 범위

좋은 요청:

```text
Phase 2의 Mock Market 기능을 구현해.
```

좋은 요청:

```text
Order Domain과 State Machine만 구현하고 테스트해.
```

나쁜 요청:

```text
전체 증권 시스템을 한 번에 만들어.
```

---

# 52. AI 코드 품질 규칙

자동 생성 코드라도 다음 기준을 지킨다.

- 의미 없는 추상화 금지
- 과도한 Interface 생성 금지
- 사용하지 않는 코드 생성 금지
- TODO를 숨기지 말 것
- Exception을 무시하지 말 것
- 빈 catch 금지
- 금융 계산에 floating point 금지
- 모든 Transaction Boundary를 명확히 할 것
- 테스트 없이 핵심 거래 로직을 완료 처리하지 말 것
- 환경변수에 없는 Secret을 코드에 넣지 말 것

---

# 53. 참고 UI 방향

UI/UX 참고:

```text
토스증권
KB 계열 증권 WTS
```

참고할 것:

- 빠른 종목 탐색
- 현재가 중심 정보 구조
- 차트와 주문 영역의 명확한 분리
- 보유자산과 손익의 높은 가시성
- 초보 사용자도 주문을 이해하기 쉬운 흐름

복제하지 말 것:

- 브랜드 로고
- 고유 색상 체계
- 고유 아이콘
- 고유 레이아웃의 픽셀 단위 복제
- 고유 문구

최종 시각 디자인은 Figma 결과를 따른다.

---

# 54. 최종 목표 아키텍처와 MVP의 관계

MVP:

```text
Local Docker
+
React
+
Spring Boot
+
Kafka
+
Valkey
+
MySQL
```

최종:

```text
React WTS
      ↓
Spring Cloud Gateway
      ↓
Spring Boot Services
      ↓
Kafka / Valkey / MySQL
      ↓
AWS EKS Primary
      ↓
GCP GKE DR
```

MVP에서 작성하는 코드는 향후 Kubernetes와 Multi-Cloud로 이전할 수 있도록:

- Stateless Application
- Externalized Configuration
- Container Ready
- Health Check
- Graceful Shutdown
- Structured Logging

을 적용한다.

---

# 55. 프로젝트 핵심 메시지

이 프로젝트의 핵심은 단순 CRUD WTS가 아니다.

```text
Real-Time Market Data
+
Consistent Trading Domain
+
Event-Driven Architecture
+
Cloud-Native Deployment
+
Multi-Cloud Disaster Recovery
```

를 단계적으로 구현하는 것이 목표다.

그러나 **MVP의 최우선 목표는 실제로 거래 흐름이 끝까지 동작하는 것**이다.

Cloud / DR / Observability는 그 이후 확장한다.
