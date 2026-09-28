# Phase 4 – Execution + Ledger

> CLAUDE.md §47 완료조건: **지정가 주문 → 가격조건 도달 → 자동 체결 → Ledger 생성**

Phase 3는 주문 시점에 조건을 만족할 때만 체결했다. 이제 가격이 나중에 도달해도 체결된다.
그리고 모든 체결과 자산 변동이 기록으로 남는다.

## 미체결 지정가가 깨어나는 길

```
market-service
     │ market.price.updated  (1초마다, 종목당 1건)
     ▼
MarketPriceUpdatedConsumer
     │  · 이벤트의 traceId를 MDC에 넣는다 (§40)
     │  · 소비가 밀려 시세가 stale이면 버린다 (§11)
     ▼
findCandidateOrderIds(symbol)          ← 락 없이 ACCEPTED + LIMIT 주문 ID만
     │
     └─ 주문마다 별도 트랜잭션 ───────────────────────┐
            │                                        │
            ▼                                        │
      계좌 행 SELECT ... FOR UPDATE   (ADR-0009)      │
            ▼                                        │
      주문 상태·가격조건 재확인        ← 락 전에 읽은 값은 낡았을 수 있다
            │  조건 미달 / 이미 체결·취소 ─→ 넘어간다  │
            ▼                                        │
      예약 해제 → ExecutionService.settle()          │
            └────────────────────────────────────────┘
```

주문 한 건마다 트랜잭션을 나눈다. 한 계좌의 체결 실패가 다른 계좌를 막지 않고, 계좌 락을
짧게 잡는다.

**체결가는 이벤트에 실린 가격이다.** Valkey의 최신값이 아니다. 가격 조건을 만족시킨 바로 그
가격으로 체결하는 것이 §12의 규칙에 맞고, 테스트도 결정적으로 만들 수 있다.

## 체결 정산은 한 곳뿐이다

주문 접수 시점의 즉시 체결과 시세 이벤트로 깨어난 체결이 **같은 `ExecutionService.settle()`**
을 쓴다. 두 벌로 나뉘면 한쪽만 고치는 일이 반드시 생긴다.

`settle()`이 한 트랜잭션에서 하는 일:

| 순서 | 내용 |
|---|---|
| 1 | 매도면 실현손익 계산 — **평균단가가 바뀌기 전에** |
| 2 | 예수금 출금/입금, 포지션 증감 |
| 3 | `order.fill()` — 상태 머신을 거친다 |
| 4 | `executions` 기록 (체결가·수수료·실현손익) |
| 5 | `ledger_entries` 기록 (before/after 잔고) |
| 6 | Outbox에 `execution.completed`, `position.changed`, `ledger.created`, `account.balance.changed` |

호출 전제는 두 가지다. **계좌 행이 잠겨 있을 것**, 그리고 **이 주문이 묶어둔 예약이 이미
해제돼 있을 것**. 예약 해제를 `settle()`이 하지 않는 이유는 해제할 금액을 아는 쪽이
호출자이기 때문이다 — 시장가는 접수 시점에 계산한 금액이고, 지정가는
`limitPrice × 미체결수량`이다.

## traceId가 이어진다 (§40)

시세는 HTTP 요청이 아니라 스케줄러 스레드에서 들어오므로 traceId가 없었다. Phase 2까지는
아무도 그 이벤트를 소비하지 않아 문제가 아니었지만, 이제 시세 한 틱이 체결을 일으킨다.

`QuoteIngestService`가 틱마다 traceId를 만들어 MDC에 넣고, 이벤트 봉투에 실린다.
trading-service의 소비자가 그것을 자기 MDC로 이어받는다. 결과:

```
execution.completed  traceId=bd72b128-…

trading-service.log
  [bd72b128-…] LedgerService              - 원장 기록: ledgerId=4 type=BUY amount=-79600
  [bd72b128-…] ExecutionService           - 체결: executionId=3 orderId=16 price=79600
  [bd72b128-…] PendingOrderExecutionService - 미체결 지정가 체결: orderId=16 limitPrice=79600
```

주문 접수 경로는 Gateway가 만든 traceId를 그대로 쓴다. 자동 체결 경로는 시세 틱의
traceId를 쓴다. 둘은 다른 사건이므로 다른 게 맞다.

## 화면

Phase 4의 결과는 두 곳에서 보인다.

- **체결내역 탭** — 체결가와 실현손익. 주문 내역에는 없는 값이다.
- **미체결 탭** — 서버가 자동 체결하면 목록에서 사라진다. 미체결 주문이 있을 때만 3초마다
  다시 읽고, 목록이 달라진 순간에만 계좌·포지션·체결내역을 함께 갱신한다
  (`useFillWatcher`). 거래 전용 WebSocket을 따로 만드는 것보다 MVP에 맞다.

## 원장 (§23)

모든 예수금 변동에 한 줄이 남는다. `amount`는 부호 있는 값이고

```
after_balance = before_balance + amount
```

가 **항상** 성립한다. DB CHECK 제약으로도 강제한다. 그래야 원장만으로 잔고를 재구성할 수 있다.

| 종류 | 언제 |
|---|---|
| `INITIAL_DEPOSIT` | 계좌 개설 시 초기 가상자금 (§9.1) |
| `BUY` | 매수 체결 대금 출금 |
| `SELL` | 매도 체결 대금 입금 |
| `FEE` | 수수료·세금. MVP는 요율이 0이라 **기록이 생기지 않는다** (§44) |
| `ADJUSTMENT` | 운영상 조정 |

원장 기록과 `account.balance.changed`는 같은 사건의 두 표현이라 `LedgerService`가 함께 낸다.

> 주의: Phase 3 이전에 만들어진 계좌에는 `INITIAL_DEPOSIT` 기록이 없다. 원장은 Phase 4부터
> 쌓인다. 개발 DB를 비우고 다시 시작하면 정합해진다.

## Transactional Outbox (§15)

거래 트랜잭션은 Kafka를 모른다. 이벤트를 같은 커밋으로 `outbox_events`에 적고, 폴링
Publisher가 브로커로 옮긴다. 근거와 한계는 [ADR-0010](../decisions/0010-outbox-over-direct-publish.md).

발행하는 이벤트 (§16):

| 토픽 | 시점 | 애그리거트 키 |
|---|---|---|
| `order.created` | 주문이 저장됐을 때. **거절된 주문도 포함** | orderId |
| `order.accepted` | 검증을 통과하고 예수금/수량을 묶었을 때 | orderId |
| `order.cancelled` | 미체결 주문 취소 | orderId |
| `execution.completed` | 체결 | executionId |
| `account.balance.changed` | 예수금 변동 (원장 기록마다) | accountId |
| `position.changed` | 체결로 보유수량이 변했을 때 | accountId:symbol |
| `ledger.created` | 원장 기록마다 | accountId |

메시지 키가 애그리거트 ID다. 같은 주문·계좌의 이벤트는 같은 파티션에 들어가 순서가 보장된다.

`audit.logged`(§41)는 MVP 후반이고, `market.price.updated`는 market-service의 것이다.

## 중복 이벤트 (§17)

`processed_events` 테이블을 만들지 않았다. 유일한 Consumer가 주문 상태 머신으로 이미
보호되기 때문이다. 근거와 이 결정이 뒤집히는 시점은
[ADR-0011](../decisions/0011-no-processed-events-table.md).

## 수수료 (§44)

`TradingFeeCalculator` 인터페이스로 분리했다. 구현체는 설정 요율을 읽고, 기본값이 0이라
MVP에서는 항상 0을 돌려준다. 매도에만 세율이 더해지는 것은 실제 한국 주식 거래 구조를 따른 것이다.

```yaml
trading:
  fee:
    rate: ${TRADING_FEE_RATE:0}       # 매수·매도 양쪽
    tax-rate: ${TRADING_TAX_RATE:0}   # 매도만
```

요율을 켜면 체결마다 `FEE` 원장 기록이 추가로 생긴다.

## §23 스키마 대비 추가 컬럼

| 컬럼 | 이유 |
|---|---|
| `orders.reject_reason` | (Phase 3) `REJECTED` 주문의 사유. 없으면 주문 내역이 아무것도 설명하지 못한다 |
| `executions.realized_profit` | 체결 시점 평균단가 기준이다. 이후 매수로 평균단가가 바뀌면 **재계산이 불가능**하다 |
| `executions.account_id` | 계좌별 체결 조회를 `orders` 조인 없이 끝낸다. `ledger_entries`와 같은 방식 |

## 테스트

| 검증 | 위치 |
|---|---|
| **§36 전체 흐름** — 시세 이벤트 → 지정가 체결 → execution → account → position → ledger | `PendingLimitOrderIntegrationTest` |
| 취소된 주문은 조건을 만족해도 체결되지 않는다 | `PendingLimitOrderIntegrationTest` |
| 매도 후 실현손익 계산 (§35) | `PendingLimitOrderIntegrationTest`, `ExecutionAndLedgerIntegrationTest`, `PositionTest` |
| Outbox가 커밋과 함께 기록된다 | `OutboxIntegrationTest` |
| Publisher가 §16 봉투 형식 그대로 Kafka에 보낸다 | `OutboxIntegrationTest` |
| 원장만으로 예수금 재구성 | `ExecutionAndLedgerIntegrationTest` |
| 수수료 계산 (§44) | `ZeroTradingFeeCalculatorTest` |
| `after = before + amount` 불변식 | `LedgerEntryTest` |

`PendingLimitOrderIntegrationTest`는 **실제 Kafka 브로커**를 거친다. 소비자 설정이나 JSON
형식이 어긋나면 여기서 드러난다.

## Phase 4에서 일부러 하지 않은 것

| 항목 | 이유 |
|---|---|
| DLQ (`execution.completed.dlq`) | §17이 "MVP 후반"으로 둔 항목 |
| `audit.logged` (§41) | §41이 "MVP 후반 적용" |
| CDC / Debezium | §15가 "MVP 필수가 아니다" |
| 부분체결 | §9.6이 선택 기능으로 둔 항목 |
| 원장 조회 API/화면 | 원장은 DB에만 쌓인다. §24에 엔드포인트가 없고, 체결내역이 사용자 관점의 답이다 |
| `GET /api/trading/portfolio` | 평가손익에 실시간 시세가 필요하다. Phase 5 |
