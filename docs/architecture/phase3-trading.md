# Phase 3 – Trading

> CLAUDE.md §47 완료조건: **시장가 매수 후 `cash` 감소, `position` 증가**

주문을 받아 검증하고, 자금과 수량을 묶고, 체결 가능하면 즉시 반영한다.
체결 기록(`executions`)과 원장(`ledger_entries`), Outbox, 미체결 지정가의 자동 체결은
Phase 4다.

## 주문 한 건이 지나가는 길

```
POST /api/trading/orders                      ← Idempotency-Key 필수
        │
        ▼
 계좌 행 SELECT ... FOR UPDATE                 ← 여기서부터 이 사용자의 주문은 직렬 (ADR-0009)
        │
        ▼
 (account_id, idempotency_key) 로 기존 주문 조회
        │  있으면 ─ 내용 같음 ─→ 기존 주문 그대로 반환 (§14)
        │          └ 내용 다름 ─→ 409 DUPLICATE_ORDER_REQUEST
        ▼
 Order.receive()                               RECEIVED
        │
        ▼
 Valkey market:price:{symbol} 조회 (ADR-0008)
        │  없음 ────────────────→ REJECTED / 503 MARKET_PRICE_UNAVAILABLE
        │  stale + 시장가 ───────→ REJECTED / 503 MARKET_PRICE_STALE   (§11)
        ▼
 자금·수량 검증
        │  매수: available_cash >= 기준가 × 수량   (§9.4)
        │  매도: available_quantity >= 수량        (§9.5)
        │  실패 ───────────────→ REJECTED / 400 INSUFFICIENT_BALANCE | INSUFFICIENT_POSITION
        ▼
 validate() → VALIDATED → 예약 → accept() → ACCEPTED
        │           매수: reserved_cash += 기준가 × 수량
        │           매도: reserved_quantity += 수량
        ▼
 ExecutionStrategy.canExecute()                (§10)
        │  MARKET ─ 항상 true
        │  LIMIT  ─ BUY: 현재가 <= 지정가 / SELL: 현재가 >= 지정가   (§12)
        │
        ├─ false ──→ ACCEPTED 로 남는다 (미체결). Phase 4가 이벤트로 깨운다.
        │
        └─ true ───→ 예약 해제 → 계좌 출납 → 포지션 변경 → fill() → FILLED
```

## 상태 머신 (§9.6)

```
RECEIVED ──validate──> VALIDATED ──accept──> ACCEPTED ──fill───> FILLED
   │                                             │
   └──reject──> REJECTED                         └──cancel─> CANCELLED
```

`RECEIVED`와 `VALIDATED`는 접수 트랜잭션 안에서만 스쳐 간다. 저장된 주문은 언제나
`ACCEPTED` / `FILLED` / `CANCELLED` / `REJECTED` 중 하나다.

허용되지 않은 전이는 `IllegalStateException`이다. 사용자 입력 오류가 아니라 서비스 코드의
버그이기 때문이다. 사용자가 유발할 수 있는 실패는 전부 `reject(ErrorCode)`로 기록된다.

`PARTIALLY_FILLED`는 넣지 않았다. §9.6이 선택 기능으로 두었고, 부분체결이 없으면 체결은
전량 체결뿐이라 `fill()`이 수량을 검사해 이를 강제한다.

## 예약이 하는 일 (§9.2, §9.3)

예약은 **예수금을 줄이지 않는다.** 묶어둘 뿐이다.

```
available_cash     = cash_balance - reserved_cash
available_quantity = quantity     - reserved_quantity
```

지정가 매수 10주 @64,500을 걸면 `cash_balance`는 그대로고 `reserved_cash`가 645,000
늘어난다. 취소하면 그대로 돌아온다.

체결될 때는 **예약을 풀고 실제 체결 대금만 출금**한다. 지정가 80,000으로 예약했는데
79,000에 체결되면 차액 10,000 × 수량이 계좌에 남는다. 체결가는 지정가가 아니라 현재가다.

예약액을 별도 컬럼으로 두지 않았다. 미체결로 남는 주문은 지정가뿐이고, 지정가 매수의
예약액은 언제나 `limit_price × 미체결수량`이라 주문에서 계산할 수 있다.

## 동시성 (§13)

잔고나 포지션을 바꾸는 모든 경로는 계좌 행의 비관적 락으로 시작한다. 포지션에는 별도
비관적 락을 걸지 않는다 — 계좌 락 하나가 그 계좌의 포지션 전부를 직렬화하기 때문이다.
자세한 근거는 [ADR-0009](../decisions/0009-pessimistic-lock-on-account.md).

같은 락이 Idempotency도 보장한다. `orders`의 `UNIQUE (account_id, idempotency_key)`는
마지막 방어선으로만 남는다.

## 시세를 얻는 방법 (§6.2)

Trading Service는 Market Service를 HTTP로 호출하지 않는다. Valkey의 `market:price:{symbol}`을
읽는다. 근거와 한계는 [ADR-0008](../decisions/0008-trading-reads-price-from-valkey.md).

두 서비스는 **Valkey 키와 JSON 형식**으로만 연결된다. `MarketPriceTest`가 market-service가
실제로 쓰는 JSON을 그대로 읽어 이 계약을 고정한다.

## 거절도 저장한다

예수금이 모자라 거절된 주문도 `orders`에 `REJECTED`로 남는다. 사용자가 주문 내역에서
"왜 안 됐는지"를 볼 수 있어야 하기 때문이다. `reject_reason` 컬럼은 CLAUDE.md §23 스키마에
없는 추가 컬럼이며, 값은 `ErrorCode` 이름이다.

이 때문에 `PlaceOrderService.place()`는 거절 시 **예외를 던지지 않고 REJECTED 주문을
반환한다.** 예외를 던지면 트랜잭션이 롤백되어 기록이 남지 않는다. HTTP 에러 응답으로
바꾸는 일은 컨트롤러가 트랜잭션 밖에서 한다.

## 화면 (CLAUDE.md §27, §28)

§47의 Phase 3에는 프론트엔드 항목이 없지만, 주문을 API로만 낼 수 있으면 아무도 써 보지
않는다. 주문을 낼 수 있는 최소 화면까지 만들었다.

```
┌─────────────────────────────────────────────┐
│ WTS 모의투자      계좌요약 · 사용자 · 연결상태 │
├───────────┬─────────────────────────────────┤
│ 관심종목  │ 현재가                           │
│           ├──────────────┬──────────────────┤
│           │ 호가          │ 주문 / 로그인     │
│           ├──────────────┴──────────────────┤
│           │ 보유종목 | 미체결 | 주문내역      │
└───────────┴─────────────────────────────────┘
```

| 컴포넌트 | 하는 일 |
|---|---|
| `LoginPanel` | Mock Login. 토큰이 없을 때 주문 패널 자리에 선다 |
| `AccountSummary` | 주문가능 / 예수금 / 주문예약 |
| `OrderForm` | 매수·매도 탭, 시장가·지정가, 수량, 최대 수량 계산, 예상 주문금액 |
| `PositionTable` | 보유종목 + 실시간 시세로 계산한 평가손익 |
| `OpenOrders` | 미체결 주문 + 취소 |
| `OrderHistory` | 전체 주문 내역. 거절 사유를 한글 문구로 보여준다 |

몇 가지 판단:

- **토큰은 localStorage에 둔다.** 새로고침으로 로그인이 풀리면 주문 화면을 쓸 수 없다.
  XSS가 있으면 새어 나가는 저장 위치지만, Mock 토큰은 가상 계좌에만 접근하고 실제 자산과
  무관하다. 실제 인증(§47 Phase 8)으로 바꿀 때 다시 볼 자리다.
- **`Idempotency-Key`는 서버가 답을 준 뒤에만 새로 만든다.** 응답을 받지 못한 요청을
  같은 키로 재시도하면 주문이 두 건 생기지 않는다 (§14). 거절 응답도 "서버가 답을 준" 것이라
  키를 새로 만든다 — 그러지 않으면 예수금을 채운 뒤 재시도해도 같은 거절이 반복된다.
- **평가손익은 화면에서 계산한다.** 서버의 `positions`에는 현재가가 없다.
  WebSocket으로 들어오는 시세와 곱해 화면에서 만든다. 서버 포트폴리오 API는 Phase 5다.
- **`tradingStore`는 만들지 않았다.** §29가 후보로 들었지만, 계좌·포지션·주문은 전부
  서버 상태라 TanStack Query가 들고 있고 같은 값을 Zustand에 복사할 이유가 없다.
  주문 폼 입력값은 컴포넌트 로컬 상태다.
- **401이 오면 세션을 버린다.** `QueryCache`/`MutationCache`의 `onError` 한 곳에서 처리해
  조회든 주문이든 같게 동작한다. 그러지 않으면 모든 요청이 401로 돌아오는 화면에 갇힌다.

## 패키지

```
com.team.wts.trading
├── order/
│   ├── domain/          Order, OrderStatus, OrderSide, OrderType,
│   │                    ExecutionStrategy, ExecutionResult, OrderRepository
│   ├── application/
│   │   ├── command/     PlaceOrderCommand, CancelOrderCommand        (§45)
│   │   ├── query/       OrderQueryService
│   │   ├── execution/   MarketOrderExecutionStrategy, LimitOrderExecutionStrategy
│   │   └── service/     PlaceOrderService, CancelOrderService
│   └── adapter/in|out/
├── position/            Position, PositionRepository, PositionQueryService
├── account/             VirtualAccount (+ reserve/release/withdraw/deposit)
└── market/              MarketPrice, MarketPriceQuery, ValkeyMarketPriceQuery
```

## 테스트 (§35)

CLAUDE.md §35가 "반드시 테스트한다"고 정한 항목 중 Phase 3 범위:

| 항목 | 위치 |
|---|---|
| 잔고 부족 매수 거절 | `OrderApiIntegrationTest` |
| 보유수량 초과 매도 거절 | `OrderApiIntegrationTest` |
| 동일 Idempotency Key 중복 주문 방지 | `OrderApiIntegrationTest`, `ConcurrentOrderIntegrationTest` |
| 시장가 체결 | `OrderApiIntegrationTest`, `ExecutionStrategyTest` |
| 지정가 미체결 | `OrderApiIntegrationTest`, `ExecutionStrategyTest` |
| 지정가 가격조건 충족 시 체결 | `OrderApiIntegrationTest`, `ExecutionStrategyTest` |
| 체결 후 Cash Balance 변경 | `OrderApiIntegrationTest`, `VirtualAccountTest` |
| 체결 후 Position 변경 | `OrderApiIntegrationTest`, `PositionTest` |
| 평균단가 계산 | `PositionTest`, `OrderApiIntegrationTest` |
| 이미 체결된 주문 취소 거절 | `OrderApiIntegrationTest`, `OrderTest` |
| **매도 후 실현손익 계산** | **Phase 4** (Ledger가 있어야 한다) |

`ConcurrentOrderIntegrationTest`는 §35 목록에 없지만 §2의 "예수금은 음수가 될 수 없다"를
단일 요청 테스트로는 증명할 수 없어 추가했다. 6개 스레드로 동시 주문을 낸다.

## Phase 3에서 일부러 하지 않은 것

| 항목 | 이유 |
|---|---|
| `executions` 테이블 | Phase 4. 지금은 체결가가 `positions.average_price`와 `cash_balance`에만 반영된다 |
| `ledger_entries`, 실현손익 | Phase 4 |
| Outbox, `order.created` 등 Kafka 발행 | Phase 4 (§15, §16) |
| 미체결 지정가의 자동 체결 | Phase 4. 지금은 주문 시점에 조건을 만족하면 바로 체결되고, 아니면 계속 `ACCEPTED`다 |
| `GET /api/trading/portfolio` | 평가손익에 현재가가 필요하다. 포트폴리오 화면과 함께 Phase 5 |
| 차트, 라우팅(§26), 종목 검색, Watchlist CRUD | Phase 5 |
| 수수료 / 세금 | §44대로 MVP는 0. `TradingFeeCalculator` 분리는 필요해질 때 |
