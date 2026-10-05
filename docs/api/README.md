# REST API

모든 요청은 Gateway(`http://localhost:8080`)를 통한다.
계약의 원본은 CLAUDE.md §24, §25이며, 이 문서는 **현재 구현된 것**만 적는다.

## OpenAPI 명세

기계가 읽는 명세는 [`openapi.yaml`](openapi.yaml)에 있다. 컨트롤러와 DTO에서 생성한 것이라
코드와 어긋나지 않는다. Postman·Insomnia·클라이언트 코드 생성기에 그대로 넣을 수 있다.

같은 내용을 사람이 읽는 표로 옮긴 것:

| 파일 | 용도 |
|---|---|
| [`api-spec.md`](api-spec.md) | API 목록 · 요청/응답 필드 · 에러 코드. GitHub에서 바로 읽는다 |
| [`api-spec.xlsx`](api-spec.xlsx) | 같은 내용의 엑셀 (개요 · API 목록 · API 상세 · 에러 코드 시트) |

세 파일 모두 생성물이다. 설명을 고치려면 컨트롤러의 `@Operation`, DTO의 `@Schema`를 고친다.

서비스를 띄운 상태에서 **Swagger UI**로 직접 호출해 볼 수도 있다.

| 서비스 | Swagger UI | 명세 |
|---|---|---|
| market-service | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |
| trading-service | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| user-service | http://localhost:8083/swagger-ui.html | http://localhost:8083/v3/api-docs |

우상단 **Authorize** 에 `mock-login` 이 준 `accessToken` 을 넣으면 인증이 필요한 API도
브라우저에서 바로 호출된다.

`openapi.yaml` 을 다시 만들려면:

```bash
./infra/scripts/generate-openapi.sh
```

**이 파일들은 손으로 고치지 않는다.** 컨트롤러나 DTO를 고치고 스크립트를 다시 돌린다.
스크립트는 아래 경우에 멈춘다.

- §24의 엔드포인트가 하나라도 빠졌다
- API 요약(`@Operation`)이나 필드 설명(`@Schema`)이 비었다
- 명세의 공개 여부가 gateway `public-paths` 와 다르다
- `$ref` 가 끊겼다

`openapi.yaml` 만 있으면 표는 서비스 없이 다시 만들 수 있다.

```bash
python3 infra/scripts/render-api-spec.py docs/api/openapi.yaml
```

아래 문서는 OpenAPI가 표현하지 못하는 것 — 왜 그렇게 동작하는지, 체결 규칙, WebSocket,
Kafka 계약 — 을 설명한다. 두 문서는 같은 API를 다른 각도에서 본다.

## 공통

### 인증

**사용자별 데이터**를 다루는 경로만 토큰이 필요하다.
시세는 누가 조회하든 같은 값이므로 공개한다 ([ADR-0007](../decisions/0007-market-data-is-public.md)).

| 경로 | 인증 |
|---|---|
| `/api/market/**`, `/ws/market/**` | 불필요 |
| `POST /api/users/mock-login`, `/actuator/**` | 불필요 |
| 그 외 (`/api/trading/**`, `/api/users/me`) | 필요 |

```http
Authorization: Bearer <accessToken>
```

클라이언트가 보낸 `X-User-Id` 헤더는 Gateway가 무조건 제거한다.

### 추적

모든 요청/응답에 `X-Trace-Id` 헤더가 붙는다.
클라이언트가 보내면 그 값을 유지하고, 없으면 Gateway가 생성한다.

### 에러 응답 (CLAUDE.md §39)

```json
{
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다.",
  "traceId": "effc7d36-ebd7-46ae-9ad8-9ea215d500b5",
  "timestamp": "2026-09-21T08:12:56.511679Z"
}
```

| code | HTTP | 발생 조건 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 토큰이 없거나 서명/만료가 유효하지 않음 |
| `USER_NOT_FOUND` | 404 | 토큰의 사용자가 존재하지 않음 |
| `ACCOUNT_NOT_FOUND` | 404 | 계좌를 찾을 수 없음 |
| `VALIDATION_FAILED` | 400 | 요청 본문/파라미터 검증 실패 |
| `SYMBOL_NOT_FOUND` | 404 | 존재하지 않는 종목코드 |
| `MARKET_PRICE_UNAVAILABLE` | 503 | 아직 시세가 수신되지 않음. 잠시 후 해소된다 |
| `MARKET_PRICE_STALE` | 503 | 최신 시세가 `market.price.max-age`(5초)를 넘겼다. 시장가 주문을 거절한다 |
| `INSUFFICIENT_BALANCE` | 400 | 주문 가능 금액 부족 |
| `INSUFFICIENT_POSITION` | 400 | 매도 가능 수량 부족 |
| `INVALID_ORDER_STATE` | 409 | 현재 주문 상태에서 불가능한 요청 (예: 체결된 주문 취소) |
| `ORDER_NOT_FOUND` | 404 | 주문이 없거나 내 주문이 아님 |
| `DUPLICATE_ORDER_REQUEST` | 409 | 같은 `Idempotency-Key`로 내용이 다른 주문을 요청 |
| `INTERNAL_ERROR` | 500 | 처리하지 못한 예외 |

---

## Market

모두 인증이 필요 없다. 금액은 원 단위 정수, 시각은 UTC ISO-8601이다 (표시는 `Asia/Seoul`).

### `GET /api/market/stocks`

종목 목록. `keyword`를 주면 종목코드/종목명 부분일치로 검색한다.

```
GET /api/market/stocks
GET /api/market/stocks?keyword=삼성
GET /api/market/stocks?keyword=00593
```

**200 OK**

```json
[
  { "symbol": "005930", "name": "삼성전자",   "market": "KOSPI" },
  { "symbol": "000660", "name": "SK하이닉스", "market": "KOSPI" }
]
```

전일 종가는 여기 없다. 종목 마스터가 아니라 시세에 속한 값이라 `/price`가 준다.

### `GET /api/market/stocks/{symbol}`

**200 OK** — 위 배열의 원소 하나. 없는 종목이면 `404 SYMBOL_NOT_FOUND`.

### `GET /api/market/stocks/{symbol}/price`

**200 OK**

```json
{
  "symbol": "005930",
  "price": 79800,
  "previousClose": 80000,
  "change": -200,
  "changeRate": -0.25,
  "volume": 159379,
  "timestamp": "2026-09-21T08:50:01.123Z"
}
```

| 필드 | 의미 |
|---|---|
| `change` | `price - previousClose` |
| `changeRate` | 전일 대비 등락률(%), 소수 둘째 자리 |
| `volume` | 당일 **누적** 거래량. 틱 증분이 아니다 |

아직 시세가 한 번도 수신되지 않았으면 `503 MARKET_PRICE_UNAVAILABLE`.

### `GET /api/market/stocks/{symbol}/orderbook`

**200 OK** — `asks`는 최우선(가장 낮은 가격)부터, `bids`는 최우선(가장 높은 가격)부터 5단계.

```json
{
  "symbol": "005930",
  "asks": [{ "price": 79900, "quantity": 835 }, { "price": 80000, "quantity": 145 }],
  "bids": [{ "price": 79700, "quantity": 412 }, { "price": 79600, "quantity": 187 }],
  "timestamp": "2026-09-21T08:50:01.123Z"
}
```

### `GET /api/market/stocks/{symbol}/candles`

| 파라미터 | 기본값 | 비고 |
|---|---|---|
| `interval` | `1m` | 현재 `1m`만 지원. 다른 값은 `400 VALIDATION_FAILED` |
| `limit` | `120` | 1 ~ 1000 |

**200 OK** — `openTime` 오름차순. 마지막 원소는 아직 확정되지 않은 현재 봉일 수 있다.

```json
[
  { "openTime": "2026-09-21T08:49:00Z", "open": 80300, "high": 80500,
    "low": 79000, "close": 79800, "volume": 158222 }
]
```

---

## WebSocket

### `ws://localhost:8080/ws/market`

인증이 필요 없다. 연결 후 구독할 종목을 보낸다.

**클라이언트 → 서버**

```json
{ "type": "SUBSCRIBE",   "symbols": ["005930", "000660"] }
{ "type": "UNSUBSCRIBE", "symbols": ["005930"] }
```

**서버 → 클라이언트**

```json
{ "type": "SUBSCRIBED", "symbols": ["005930", "000660"] }

{ "type": "PRICE", "symbol": "005930", "price": 79800, "change": -200,
  "changeRate": -0.25, "volume": 159379, "timestamp": "2026-09-21T08:50:01.123Z" }

{ "type": "ORDERBOOK", "symbol": "005930",
  "asks": [{ "price": 79900, "quantity": 835 }],
  "bids": [{ "price": 79700, "quantity": 412 }],
  "timestamp": "2026-09-21T08:50:01.123Z" }

{ "type": "ERROR", "message": "메시지 형식이 올바르지 않습니다." }
```

- 구독한 종목만 내려온다.
- `SUBSCRIBE` 직후 캐시에 있는 최신 `PRICE`·`ORDERBOOK`을 한 번 즉시 보낸다.
  다음 틱까지 화면이 비어 있지 않도록 하기 위해서다.
- 연결이 끊기면 클라이언트가 재연결 후 `SUBSCRIBE`를 다시 보내야 한다.
  프론트엔드의 `MarketSocket`이 자동으로 처리한다.

---

## User

### `POST /api/users/mock-login`

인증 불필요. 이메일로 사용자를 찾고, 없으면 만든 뒤 토큰을 발급한다.
이메일은 소문자로 정규화되므로 대소문자가 달라도 같은 사용자다.

```json
{ "email": "demo@wts.local", "nickname": "데모투자자" }
```

| 필드 | 필수 | 비고 |
|---|---|---|
| `email` | O | 이메일 형식, 최대 255자 |
| `nickname` | X | 최대 50자. 비우면 이메일 앞부분을 쓴다. 기존 사용자의 닉네임은 바뀌지 않는다 |

**200 OK**

```json
{
  "userId": "59407a60-bc3b-4a55-a452-72d0bcd72142",
  "email": "demo@wts.local",
  "nickname": "데모투자자",
  "accessToken": "NTk0MDdhNjAt....kVNLRyNt1D6JOW",
  "expiresInSeconds": 86400
}
```

### `GET /api/users/me/watchlist`

관심종목. 담은 순서대로.

**200 OK**

```json
[
  { "symbol": "005930", "createdAt": "2026-09-22T08:12:43.644069Z" }
]
```

### `POST /api/users/me/watchlist/{symbol}`

관심종목에 담는다. **이미 담긴 종목이어도 성공이다** — 결과 상태가 같기 때문이다.

**200 OK** — 위 배열의 원소 하나.

| 상황 | 응답 |
|---|---|
| 종목코드가 6자리 숫자가 아님 | `400 VALIDATION_FAILED` |
| 상한(기본 50개) 초과 | `400 VALIDATION_FAILED` |

종목이 실제로 존재하는지는 확인하지 않는다. 종목 마스터는 market-service의 것이다.
화면이 `GET /api/market/stocks` 목록과 맞춰 보여준다.

### `DELETE /api/users/me/watchlist/{symbol}`

**204 No Content.** 담겨 있지 않은 종목을 빼도 성공이다.

### `GET /api/users/me`

**200 OK**

```json
{
  "userId": "59407a60-bc3b-4a55-a452-72d0bcd72142",
  "email": "demo@wts.local",
  "nickname": "데모투자자",
  "createdAt": "2026-09-21T08:12:43.644069Z"
}
```

---

## Trading

모든 경로가 인증을 요구한다.
금액은 JSON number로 내려간다. 정밀도 손실을 피하려면 클라이언트에서 `Number`가 아닌
10진 처리(예: `decimal.js`)로 다루는 것을 권장한다.

### `GET /api/trading/account`

사용자의 가상 계좌를 조회한다. 계좌가 없으면 초기 가상자금으로 개설한다
([ADR-0005](../decisions/0005-account-provisioned-on-first-access.md)).

**200 OK**

```json
{
  "accountId": 1,
  "userId": "59407a60-bc3b-4a55-a452-72d0bcd72142",
  "cashBalance": 99197000.0000,
  "reservedCash": 645000.0000,
  "availableCash": 98552000.0000,
  "updatedAt": "2026-09-22T08:12:43.983480Z"
}
```

| 필드 | 의미 |
|---|---|
| `cashBalance` | 예수금. **예약은 여기서 빠지지 않는다** |
| `reservedCash` | 미체결 매수 주문이 묶어둔 금액 |
| `availableCash` | 주문 가능 금액 = `cashBalance - reservedCash` (CLAUDE.md §9.2) |

### `POST /api/trading/orders`

```http
POST /api/trading/orders
Authorization: Bearer <accessToken>
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json
```

`Idempotency-Key`는 **필수**다. 없거나 65자 이상이면 `400 VALIDATION_FAILED`.
클라이언트가 주문마다 새로 만든다(`crypto.randomUUID()`).

```json
{ "symbol": "005930", "side": "BUY", "orderType": "LIMIT", "quantity": 10, "limitPrice": 80000 }
```

| 필드 | 필수 | 비고 |
|---|---|---|
| `symbol` | O | 6자리 숫자 |
| `side` | O | `BUY` \| `SELL` |
| `orderType` | O | `MARKET` \| `LIMIT` |
| `quantity` | O | 1 ~ 1,000,000 |
| `limitPrice` | 지정가만 | 0보다 큰 값. 시장가에 보내면 무시한다 |

**201 Created**

```json
{
  "orderId": 2,
  "symbol": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "limitPrice": 80000.0000,
  "filledQuantity": 0,
  "status": "ACCEPTED",
  "createdAt": "2026-09-22T08:12:44.101Z",
  "updatedAt": "2026-09-22T08:12:44.101Z"
}
```

응답의 `status`가 결과를 말해 준다.

| status | 의미 |
|---|---|
| `FILLED` | 즉시 전량 체결됐다. 예수금과 포지션이 이미 바뀌었다 |
| `ACCEPTED` | 미체결. 지정가 조건을 아직 만족하지 않는다. 예수금(또는 수량)은 묶인 상태다 |

**거절**되면 `4xx`/`5xx` 에러 응답이 온다. 거절된 주문도 주문 내역에는
`status: "REJECTED"`, `rejectReason: "<코드>"`로 남는다.

| 상황 | 응답 |
|---|---|
| 주문 가능 금액 부족 | `400 INSUFFICIENT_BALANCE` |
| 매도 가능 수량 부족 | `400 INSUFFICIENT_POSITION` |
| 종목 시세가 없음 | `503 MARKET_PRICE_UNAVAILABLE` |
| 시세가 5초 이상 낡음 (시장가만) | `503 MARKET_PRICE_STALE` |
| 같은 키로 내용이 다른 주문 | `409 DUPLICATE_ORDER_REQUEST` |

**같은 키로 같은 내용을 다시 보내면** 주문을 새로 만들지 않고 기존 주문을 그대로 돌려준다
(CLAUDE.md §14). 응답 코드도 `201`이다.

체결 규칙:

- **시장가** — 최신 현재가로 즉시 전량 체결 (§11)
- **지정가** — `BUY: 현재가 <= 지정가`, `SELL: 현재가 >= 지정가`일 때 체결 (§12).
  체결가는 지정가가 아니라 **현재가**다. 유리한 차액은 주문자 몫이다.
  조건을 만족하지 않으면 `ACCEPTED`로 남고, **가격이 나중에 조건에 도달하면 자동으로 체결된다.**
  자동 체결은 `market.price.updated` 이벤트를 받아 일어나므로 시세가 도달한 직후 몇 백 밀리초
  안에 반영된다. 클라이언트는 주문 목록을 다시 조회해 확인한다.

### `GET /api/trading/orders`

| 파라미터 | 비고 |
|---|---|
| `status` | `ACCEPTED`로 미체결 주문만. 생략하면 전체 |

**200 OK** — 최근 주문부터. 원소는 위 주문 응답과 같은 형태이며 `REJECTED` 주문에만
`rejectReason`이 붙는다.

### `GET /api/trading/orders/{orderId}`

**200 OK** — 주문 하나. 내 주문이 아니면 `404 ORDER_NOT_FOUND`.

### `DELETE /api/trading/orders/{orderId}`

미체결(`ACCEPTED`) 주문을 취소하고 묶인 예수금·수량을 푼다.

**200 OK** — `status: "CANCELLED"`가 된 주문.

이미 `FILLED` / `CANCELLED` / `REJECTED`인 주문은 `409 INVALID_ORDER_STATE`.

### `GET /api/trading/executions`

체결 내역. 최신순. 주문의 `filledQuantity`만으로는 **얼마에** 체결됐는지 알 수 없다.
체결가는 여기에만 있다.

**200 OK**

```json
[
  {
    "executionId": 12,
    "orderId": 8,
    "symbol": "005930",
    "side": "SELL",
    "price": 91000.0000,
    "quantity": 4,
    "amount": 364000.0000,
    "fee": 0.0000,
    "realizedProfit": 44000.0000,
    "executedAt": "2026-09-22T08:50:01.123Z"
  }
]
```

| 필드 | 의미 |
|---|---|
| `amount` | 체결 대금 = `price × quantity`. 수수료는 포함하지 않는다 |
| `fee` | 수수료 + 세금. MVP는 0이다 (CLAUDE.md §44) |
| `realizedProfit` | **매도 체결에만 있다.** `(체결가 - 체결 시점 평균단가) × 수량`. 매수 체결에는 이 필드가 없다 |

### `GET /api/trading/portfolio`

보유 종목을 현재가로 평가한다. 시세는 Valkey에서 한 번에 읽는다
([ADR-0008](../decisions/0008-trading-reads-price-from-valkey.md)).

**200 OK**

```json
{
  "accountId": 1,
  "cashBalance": 98831200.0000,
  "reservedCash": 0.0000,
  "availableCash": 98831200.0000,
  "totalPurchaseAmount": 1168800.0000,
  "totalEvaluationAmount": 1169000.0000,
  "valuationProfit": 200.0000,
  "valuationProfitRate": 0.02,
  "realizedProfit": 1600.0000,
  "totalAssets": 100000200.0000,
  "positions": [
    {
      "symbol": "005930",
      "quantity": 10,
      "reservedQuantity": 0,
      "availableQuantity": 10,
      "averagePrice": 80000.0000,
      "currentPrice": 80400.0000,
      "purchaseAmount": 800000.0000,
      "evaluationAmount": 804000.0000,
      "valuationProfit": 4000.0000,
      "valuationProfitRate": 0.50
    }
  ],
  "evaluatedAt": "2026-09-22T08:50:01.123Z"
}
```

| 필드 | 의미 |
|---|---|
| `valuationProfit` | **평가**손익. 아직 팔지 않은 이익 = 평가금액 - 매입금액 |
| `realizedProfit` | **실현**손익 누적. 이미 팔아서 확정된 이익 |
| `totalAssets` | 예수금 + 평가금액 |

`currentPrice`가 `null`이면 그 종목의 시세를 읽지 못한 것이다. 이때 평가금액은 매입금액과
같게 둔다(평가손익 0). 모르는 값을 0원으로 만들어 총자산을 왜곡하지 않기 위해서다.

한 시점의 스냅샷이다. 매초 갱신되는 값이 필요하면 WebSocket 시세와 `GET /api/trading/positions`
를 조합한다.

### `GET /api/trading/positions`

**200 OK** — 보유 중인 종목만. 전량 매도한 종목은 빠진다.

```json
[
  {
    "symbol": "005930",
    "quantity": 10,
    "reservedQuantity": 4,
    "availableQuantity": 6,
    "averagePrice": 80300.0000,
    "updatedAt": "2026-09-22T08:12:44.101Z"
  }
]
```

| 필드 | 의미 |
|---|---|
| `reservedQuantity` | 미체결 매도 주문이 묶어둔 수량 |
| `availableQuantity` | 매도 가능 수량 = `quantity - reservedQuantity` (CLAUDE.md §9.3) |
| `averagePrice` | 이동평균 매입단가. 매도는 이 값을 바꾸지 않는다 |

평가금액·평가손익은 없다. 현재가가 필요한 값이라 포트폴리오 화면에서 실시간 시세와 함께
계산한다 (Phase 5).

---

## 구현 현황

CLAUDE.md §24에 정의된 엔드포인트는 모두 구현됐다.

| 아직 없는 것 | 도입 시점 |
|---|---|
| 원장(`ledger_entries`) 조회 | §24에 엔드포인트가 없다. 사용자 관점의 답은 체결내역이다 |

### 시세 출처

응답 형식은 `MARKET_PROVIDER` 와 무관하게 같다.

| 값 | 내용 |
|---|---|
| `mock` | 설정된 출발 가격에서 시작하는 random walk (기본값) |
| `kis` | 한국투자증권 OpenAPI 실시간 체결가·호가 |

`kis` 모드에서는 **장 마감 후 시장가 주문이 `503 MARKET_PRICE_STALE` 로 거절된다.**
마지막 체결 시각이 15:30에 멈춰 `market.price.max-age`를 넘기기 때문이다 (§11).

---

## Kafka 이벤트 (CLAUDE.md §16)

REST는 아니지만 같은 계약이다. trading-service가 Transactional Outbox를 거쳐 발행한다
([ADR-0010](../decisions/0010-outbox-over-direct-publish.md)).

모든 메시지는 §16의 봉투를 쓴다. 토픽 이름과 `eventType`은 같은 값이고,
메시지 키는 애그리거트 ID다 (같은 주문·계좌의 순서가 보장된다).

```json
{
  "eventId": "8f14e45f-ceea-467a-9575-1b0b2e0b1a2c",
  "eventType": "execution.completed",
  "occurredAt": "2026-09-22T08:50:01.123Z",
  "traceId": "effc7d36-ebd7-46ae-9ad8-9ea215d500b5",
  "aggregateId": "12",
  "version": 1,
  "payload": { }
}
```

| 토픽 | 발행 시점 | 키 |
|---|---|---|
| `market.price.updated` | 시세 갱신 (market-service) | 종목코드 |
| `order.created` | 주문이 저장됐을 때. 거절된 주문도 포함 | orderId |
| `order.accepted` | 예수금/수량을 묶었을 때 | orderId |
| `order.cancelled` | 미체결 주문 취소 | orderId |
| `execution.completed` | 체결 | executionId |
| `account.balance.changed` | 예수금 변동 | accountId |
| `position.changed` | 체결로 보유수량 변동 | accountId:symbol |
| `ledger.created` | 원장 기록 | accountId |

**At-Least-Once다.** 같은 `eventId`의 메시지가 두 번 올 수 있다. 소비자는 중복을 전제로
설계해야 한다 (§17).

**traceId가 이어진다.** 시세 틱이 자동 체결을 일으키면 `execution.completed`와
`ledger.created`가 그 시세 이벤트의 traceId를 그대로 쓴다. 주문 접수로 생긴 이벤트는
Gateway가 만든 traceId를 쓴다 (§40).

**타입 헤더를 붙이지 않는다.** 소비자는 메시지를 문자열로 받아 자기 payload 클래스로
역직렬화한다. 발행 측 클래스 이름이 소비 측 계약이 되지 않게 하기 위해서다.
