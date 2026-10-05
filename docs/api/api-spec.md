# WTS 모의투자 API 명세서

> **생성물이다. 직접 고치지 않는다.** 원본은 컨트롤러(`@Operation`)와 DTO(`@Schema`)이고, [`openapi.yaml`](openapi.yaml)을 거쳐 `./infra/scripts/generate-openapi.sh` 가 만든다. 같은 내용의 엑셀은 [`api-spec.xlsx`](api-spec.xlsx).
>
> 실시간 시세 WebSocket(`/ws/market`)과 Kafka 이벤트 계약은 OpenAPI로 표현할 수 없어 [README.md](README.md)에 있다.

버전 `0.0.1-SNAPSHOT` · API 18개

## 1. 공통

| 항목 | 내용 |
|---|---|
| Base URL | `http://localhost:8080` (Gateway). 각 서비스를 직접 호출하지 않는다 |
| 인증 | `Authorization: Bearer <accessToken>`. 토큰은 `POST /api/users/mock-login` 으로 발급한다. 인증이 필요 없는 API는 목록의 인증 칸에 표시 |
| 데이터 형식 | JSON (UTF-8) |
| 시각 | UTC, ISO-8601 (예: `2026-09-22T08:12:44.101Z`). 화면 표시는 Asia/Seoul |
| 금액 | 원(KRW), JSON number. 소수점이 붙어 올 수 있다 (예: `80000.0000`) |

### 공통 헤더

| 헤더 | 방향 | 설명 |
|---|---|---|
| `Authorization` | 요청 | 인증이 필요한 API에 `Bearer <accessToken>` |
| `Idempotency-Key` | 요청 | 주문 접수에만 필수. 같은 키로 같은 내용을 다시 보내면 기존 주문이 돌아온다 |
| `X-Trace-Id` | 응답 | 모든 응답에 붙는다. 에러 본문의 `traceId`와 같은 값이다 |

### 에러 응답

성공하지 못한 요청은 모두 같은 형식의 본문을 돌려준다.

```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "주문 가능한 예수금이 부족합니다.",
  "traceId": "effc7d36-ebd7-46ae-9ad8-9ea215d500b5",
  "timestamp": "2026-09-22T08:12:44.101Z"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `code` | string | 에러 코드 |
| `message` | string | 사람이 읽을 수 있는 메시지 |
| `traceId` | string | 추적 ID. 응답 헤더 X-Trace-Id와 같다 |
| `timestamp` | string (date-time) | 발생 시각 (UTC) |

### 에러 코드

| 코드 | HTTP | 메시지 | 발생 API |
|---|---|---|---|
| `UNAUTHORIZED` | 401 | 인증이 필요합니다. | 2, 3, 4, 5, 11, 12, 13, 14, 15, 16, 17, 18 |
| `USER_NOT_FOUND` | 404 | 사용자를 찾을 수 없습니다. | 2 |
| `ACCOUNT_NOT_FOUND` | 404 | 계좌를 찾을 수 없습니다. | — |
| `VALIDATION_FAILED` | 400 | 요청 값이 올바르지 않습니다. | 1, 4, 10, 12, 13, 14, 15 |
| `SYMBOL_NOT_FOUND` | 404 | 종목을 찾을 수 없습니다. | 7, 8, 9, 10 |
| `MARKET_PRICE_UNAVAILABLE` | 503 | 현재가를 조회할 수 없습니다. | 8, 9, 12 |
| `MARKET_PRICE_STALE` | 503 | 현재가가 오래되어 주문을 처리할 수 없습니다. | 12 |
| `INSUFFICIENT_BALANCE` | 400 | 주문 가능한 예수금이 부족합니다. | 12 |
| `INSUFFICIENT_POSITION` | 400 | 매도 가능한 수량이 부족합니다. | 12 |
| `INVALID_ORDER_STATE` | 409 | 현재 주문 상태에서는 처리할 수 없습니다. | 15 |
| `ORDER_NOT_FOUND` | 404 | 주문을 찾을 수 없습니다. | 14, 15 |
| `DUPLICATE_ORDER_REQUEST` | 409 | 같은 Idempotency-Key로 다른 주문을 요청했습니다. | 12 |
| `INTERNAL_ERROR` | 500 | 일시적인 오류가 발생했습니다. | 모든 API (처리하지 못한 예외) |

## 2. API 목록

| No | 분류 | API | Method | URI | 인증 |
|---|---|---|---|---|---|
| 1 | 사용자 | [모의 로그인](#api-1) | `POST` | `/api/users/mock-login` | 불필요 |
| 2 | 사용자 | [내 정보 조회](#api-2) | `GET` | `/api/users/me` | 필요 |
| 3 | 관심종목 | [관심종목 목록](#api-3) | `GET` | `/api/users/me/watchlist` | 필요 |
| 4 | 관심종목 | [관심종목 추가](#api-4) | `POST` | `/api/users/me/watchlist/{symbol}` | 필요 |
| 5 | 관심종목 | [관심종목 삭제](#api-5) | `DELETE` | `/api/users/me/watchlist/{symbol}` | 필요 |
| 6 | 종목 | [종목 목록·검색](#api-6) | `GET` | `/api/market/stocks` | 불필요 |
| 7 | 종목 | [종목 상세](#api-7) | `GET` | `/api/market/stocks/{symbol}` | 불필요 |
| 8 | 시세 | [호가 조회](#api-8) | `GET` | `/api/market/stocks/{symbol}/orderbook` | 불필요 |
| 9 | 시세 | [현재가 조회](#api-9) | `GET` | `/api/market/stocks/{symbol}/price` | 불필요 |
| 10 | 차트 | [분봉 조회](#api-10) | `GET` | `/api/market/stocks/{symbol}/candles` | 불필요 |
| 11 | 계좌 | [계좌 조회](#api-11) | `GET` | `/api/trading/account` | 필요 |
| 12 | 주문 | [주문 접수](#api-12) | `POST` | `/api/trading/orders` | 필요 |
| 13 | 주문 | [주문 목록](#api-13) | `GET` | `/api/trading/orders` | 필요 |
| 14 | 주문 | [주문 상세](#api-14) | `GET` | `/api/trading/orders/{orderId}` | 필요 |
| 15 | 주문 | [주문 취소](#api-15) | `DELETE` | `/api/trading/orders/{orderId}` | 필요 |
| 16 | 체결 | [체결 내역 조회](#api-16) | `GET` | `/api/trading/executions` | 필요 |
| 17 | 보유종목 | [보유종목 조회](#api-17) | `GET` | `/api/trading/positions` | 필요 |
| 18 | 포트폴리오 | [포트폴리오 조회](#api-18) | `GET` | `/api/trading/portfolio` | 필요 |

## 3. API 상세

### 사용자

모의 로그인과 내 정보

<a id="api-1"></a>

#### 1. 모의 로그인

`POST /api/users/mock-login` · 인증 불필요

이메일로 사용자를 찾고, 없으면 만든 뒤 토큰을 발급한다. 이메일은 소문자로 정규화하므로 대소문자가 달라도 같은 사용자다. 인증 없이 호출한다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `email` | body | string | O | 이메일. 소문자로 정규화한다. 최대 255자 | `demo@wts.local` |
| `nickname` | body | string |  | 닉네임. 선택, 최대 50자. 비우면 이메일 앞부분을 쓴다. 기존 사용자의 닉네임은 바뀌지 않는다 | `데모투자자` |

```json
{
  "email": "demo@wts.local",
  "nickname": "데모투자자"
}
```

**응답** — 200 OK · MockLoginResponse

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `userId` | string | 사용자 ID (UUID) | `59407a60-bc3b-4a55-a452-72d0bcd72142` |
| `email` | string | 이메일 (소문자) | `demo@wts.local` |
| `nickname` | string | 닉네임 | `데모투자자` |
| `accessToken` | string | 인증 토큰. 이후 요청의 Authorization: Bearer 헤더에 넣는다 | `NTk0MDdhNjAt....kVNLRyNt1D6JOW` |
| `expiresInSeconds` | integer | 토큰 유효시간 (초) | `86400` |

```json
{
  "userId": "59407a60-bc3b-4a55-a452-72d0bcd72142",
  "email": "demo@wts.local",
  "nickname": "데모투자자",
  "accessToken": "NTk0MDdhNjAt....kVNLRyNt1D6JOW",
  "expiresInSeconds": 86400
}
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 요청 값이 올바르지 않습니다. |

<a id="api-2"></a>

#### 2. 내 정보 조회

`GET /api/users/me` · 인증 필요

토큰의 사용자 정보를 돌려준다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |

**응답** — 200 OK · UserResponse

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `userId` | string | 사용자 ID (UUID) | `59407a60-bc3b-4a55-a452-72d0bcd72142` |
| `email` | string | 이메일 (소문자) | `demo@wts.local` |
| `nickname` | string | 닉네임 | `데모투자자` |
| `createdAt` | string (date-time) | 가입 시각 (UTC) | `2026-09-22T08:12:44.101Z` |

```json
{
  "userId": "59407a60-bc3b-4a55-a452-72d0bcd72142",
  "email": "demo@wts.local",
  "nickname": "데모투자자",
  "createdAt": "2026-09-22T08:12:44.101Z"
}
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |
| 404 | `USER_NOT_FOUND` | 사용자를 찾을 수 없습니다. |

### 관심종목

사용자별 관심종목

<a id="api-3"></a>

#### 3. 관심종목 목록

`GET /api/users/me/watchlist` · 인증 필요

담은 순서대로 돌려준다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |

**응답** — 200 OK · WatchlistItemResponse 배열

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `symbol` | string | 종목코드 | `005930` |
| `createdAt` | string (date-time) | 담은 시각 (UTC) | `2026-09-22T08:12:44.101Z` |

```json
[
  {
    "symbol": "005930",
    "createdAt": "2026-09-22T08:12:44.101Z"
  }
]
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |

<a id="api-4"></a>

#### 4. 관심종목 추가

`POST /api/users/me/watchlist/{symbol}` · 인증 필요

이미 담긴 종목이어도 성공이다(같은 요청을 여러 번 보내도 결과가 같다). 종목이 실제로 존재하는지는 확인하지 않는다. 최대 개수(기본 50개)를 넘으면 거절한다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |
| `symbol` | path | string | O | 종목코드 (6자리) | `005930` |

**응답** — 200 OK · WatchlistItemResponse

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `symbol` | string | 종목코드 | `005930` |
| `createdAt` | string (date-time) | 담은 시각 (UTC) | `2026-09-22T08:12:44.101Z` |

```json
{
  "symbol": "005930",
  "createdAt": "2026-09-22T08:12:44.101Z"
}
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 요청 값이 올바르지 않습니다. |
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |

<a id="api-5"></a>

#### 5. 관심종목 삭제

`DELETE /api/users/me/watchlist/{symbol}` · 인증 필요

담겨 있지 않은 종목을 빼도 성공이다. 본문 없이 204를 돌려준다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |
| `symbol` | path | string | O | 종목코드 (6자리) | `005930` |

**응답** — 204 No Content · 본문 없음

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |

### 종목

종목 마스터 조회와 검색. 인증 불필요

<a id="api-6"></a>

#### 6. 종목 목록·검색

`GET /api/market/stocks` · 인증 불필요

keyword를 주면 종목코드 또는 종목명 부분일치로 검색한다. 생략하면 전체 목록이다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `keyword` | query | string |  | 종목코드 또는 종목명 일부. 생략하면 전체 | `삼성` |

**응답** — 200 OK · StockResponse 배열

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `symbol` | string | 종목코드 (6자리) | `005930` |
| `name` | string | 종목명 | `삼성전자` |
| `market` | string | 시장 구분 | `KOSPI` |

```json
[
  {
    "symbol": "005930",
    "name": "삼성전자",
    "market": "KOSPI"
  }
]
```

<a id="api-7"></a>

#### 7. 종목 상세

`GET /api/market/stocks/{symbol}` · 인증 불필요

종목코드로 종목 하나를 조회한다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `symbol` | path | string | O | 종목코드 (6자리) | `005930` |

**응답** — 200 OK · StockResponse

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `symbol` | string | 종목코드 (6자리) | `005930` |
| `name` | string | 종목명 | `삼성전자` |
| `market` | string | 시장 구분 | `KOSPI` |

```json
{
  "symbol": "005930",
  "name": "삼성전자",
  "market": "KOSPI"
}
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 404 | `SYMBOL_NOT_FOUND` | 종목을 찾을 수 없습니다. |

### 시세

현재가와 호가. 인증 불필요

<a id="api-8"></a>

#### 8. 호가 조회

`GET /api/market/stocks/{symbol}/orderbook` · 인증 불필요

최신 매도·매수 호가를 돌려준다. 매도호가는 낮은 가격부터, 매수호가는 높은 가격부터 정렬한다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `symbol` | path | string | O | 종목코드 (6자리) | `005930` |

**응답** — 200 OK · OrderBookResponse

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `symbol` | string | 종목코드 | `005930` |
| `asks` | array&lt;object&gt; | 매도호가. 최우선(가장 낮은 가격)부터 |  |
| `asks[].price` | number | 호가 (원) | `79900` |
| `asks[].quantity` | integer | 잔량 (주) | `835` |
| `bids` | array&lt;object&gt; | 매수호가. 최우선(가장 높은 가격)부터 |  |
| `bids[].price` | number | 호가 (원) | `79900` |
| `bids[].quantity` | integer | 잔량 (주) | `835` |
| `timestamp` | string (date-time) | 호가 시각 (UTC, ISO-8601) | `2026-09-22T08:12:44.101Z` |

```json
{
  "symbol": "005930",
  "asks": [
    {
      "price": 79900,
      "quantity": 835
    }
  ],
  "bids": [
    {
      "price": 79900,
      "quantity": 835
    }
  ],
  "timestamp": "2026-09-22T08:12:44.101Z"
}
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 404 | `SYMBOL_NOT_FOUND` | 종목을 찾을 수 없습니다. |
| 503 | `MARKET_PRICE_UNAVAILABLE` | 현재가를 조회할 수 없습니다. |

<a id="api-9"></a>

#### 9. 현재가 조회

`GET /api/market/stocks/{symbol}/price` · 인증 불필요

최신 현재가를 돌려준다. 필드 구성은 WebSocket PRICE 메시지와 같다. 실시간 갱신이 필요하면 WebSocket(/ws/market)을 구독한다. 아직 시세가 한 번도 들어오지 않았으면 503이다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `symbol` | path | string | O | 종목코드 (6자리) | `005930` |

**응답** — 200 OK · PriceResponse

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `symbol` | string | 종목코드 | `005930` |
| `price` | number | 현재가 (원) | `79800` |
| `previousClose` | number | 전일 종가 (원) | `80000` |
| `change` | number | 전일 대비 (원) = price - previousClose | `-200` |
| `changeRate` | number | 전일 대비 등락률 (%), 소수 둘째 자리 | `-0.25` |
| `volume` | integer | 당일 누적 거래량 (주). 틱 증분이 아니다 | `159379` |
| `timestamp` | string (date-time) | 시세 시각 (UTC, ISO-8601) | `2026-09-22T08:12:44.101Z` |

```json
{
  "symbol": "005930",
  "price": 79800,
  "previousClose": 80000,
  "change": -200,
  "changeRate": -0.25,
  "volume": 159379,
  "timestamp": "2026-09-22T08:12:44.101Z"
}
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 404 | `SYMBOL_NOT_FOUND` | 종목을 찾을 수 없습니다. |
| 503 | `MARKET_PRICE_UNAVAILABLE` | 현재가를 조회할 수 없습니다. |

### 차트

분봉 조회. 인증 불필요

<a id="api-10"></a>

#### 10. 분봉 조회

`GET /api/market/stocks/{symbol}/candles` · 인증 불필요

최근 분봉을 openTime 오름차순으로 돌려준다. 마지막 원소는 아직 확정되지 않은 현재 봉일 수 있다. 현재 1분봉만 지원한다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `symbol` | path | string | O | 종목코드 (6자리) | `005930` |
| `interval` | query | string |  | 봉 주기. 현재 1m만 지원 (기본값 1m) | `1m` |
| `limit` | query | integer |  | 최근 몇 개를 받을지. 1 ~ 1000 (기본값 120) | `120` |

**응답** — 200 OK · CandleResponse 배열

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `openTime` | string (date-time) | 봉 시작 시각 (UTC). 해당 분의 0초 | `2026-09-22T00:01:00Z` |
| `open` | number | 시가 (원) | `80300` |
| `high` | number | 고가 (원) | `80500` |
| `low` | number | 저가 (원) | `79000` |
| `close` | number | 종가 (원) | `79800` |
| `volume` | integer | 해당 1분 동안의 거래량 (주) | `158222` |

```json
[
  {
    "openTime": "2026-09-22T00:01:00Z",
    "open": 80300,
    "high": 80500,
    "low": 79000,
    "close": 79800,
    "volume": 158222
  }
]
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 요청 값이 올바르지 않습니다. |
| 404 | `SYMBOL_NOT_FOUND` | 종목을 찾을 수 없습니다. |

### 계좌

가상 계좌

<a id="api-11"></a>

#### 11. 계좌 조회

`GET /api/trading/account` · 인증 필요

예수금·예약금·주문 가능 금액을 돌려준다. 계좌가 없으면 초기 가상자금(기본 1억원)으로 개설한다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |

**응답** — 200 OK · AccountResponse

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `accountId` | integer | 가상 계좌 ID | `1` |
| `userId` | string | 사용자 ID (UUID) | `59407a60-bc3b-4a55-a452-72d0bcd72142` |
| `cashBalance` | number | 예수금 (원). 미체결 주문의 예약금이 빠지지 않은 값이다 | `99197000` |
| `reservedCash` | number | 미체결 매수 주문이 묶어둔 금액 (원) | `645000` |
| `availableCash` | number | 주문 가능 금액 (원) = cashBalance - reservedCash | `98552000` |
| `updatedAt` | string (date-time) | 마지막 변경 시각 (UTC) | `2026-09-22T08:12:44.101Z` |

```json
{
  "accountId": 1,
  "userId": "59407a60-bc3b-4a55-a452-72d0bcd72142",
  "cashBalance": 99197000,
  "reservedCash": 645000,
  "availableCash": 98552000,
  "updatedAt": "2026-09-22T08:12:44.101Z"
}
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |

### 주문

주문 접수·조회·취소

<a id="api-12"></a>

#### 12. 주문 접수

`POST /api/trading/orders` · 인증 필요

시장가(MARKET)는 최신 현재가로 즉시 전량 체결되어 FILLED로 돌아온다. 지정가(LIMIT)는 BUY면 현재가 ≤ 지정가, SELL이면 현재가 ≥ 지정가일 때 즉시 체결되고, 아니면 ACCEPTED로 남아 시세가 조건에 도달하면 자동 체결된다. 체결가는 지정가가 아니라 체결 시점 현재가다. 거절되면 에러 응답이 오고, 거절된 주문도 REJECTED로 주문 내역에 남는다. 같은 Idempotency-Key로 같은 내용을 다시 보내면 주문을 새로 만들지 않고 기존 주문을 그대로 201로 돌려준다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |
| `Idempotency-Key` | header | string | O | 주문마다 새로 만든 고유 키 (UUID 권장, 1~64자). 같은 키 재요청은 기존 주문을 돌려준다 | `550e8400-e29b-41d4-a716-446655440000` |
| `symbol` | body | string | O | 종목코드. 6자리 숫자 | `005930` |
| `side` | body | enum (BUY / SELL) | O | BUY 매수 / SELL 매도 | `BUY` |
| `orderType` | body | enum (MARKET / LIMIT) | O | MARKET 시장가 / LIMIT 지정가 | `LIMIT` |
| `quantity` | body | integer | O | 주문 수량 (주). 1 ~ 1,000,000 | `10` |
| `limitPrice` | body | number |  | 지정가 (원). LIMIT 주문에 필수, 0보다 커야 한다. MARKET 주문이면 무시한다 | `80000` |

```json
{
  "symbol": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "limitPrice": 80000
}
```

**응답** — 201 Created · OrderResponse

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `orderId` | integer | 주문 ID | `2` |
| `symbol` | string | 종목코드 | `005930` |
| `side` | enum (BUY / SELL) | BUY 매수 / SELL 매도 | `BUY` |
| `orderType` | enum (MARKET / LIMIT) | MARKET 시장가 / LIMIT 지정가 | `LIMIT` |
| `quantity` | integer | 주문 수량 (주) | `10` |
| `limitPrice` | number | 지정가 (원). 시장가 주문이면 필드가 없다 | `80000` |
| `filledQuantity` | integer | 체결된 수량 (주) | `0` |
| `status` | enum (RECEIVED / VALIDATED / ACCEPTED / FILLED / CANCELLED / REJECTED) | 주문 상태. FILLED 전량 체결 / ACCEPTED 미체결(예수금·수량 묶임) / CANCELLED 취소 / REJECTED 거절 | `ACCEPTED` |
| `rejectReason` | string | 거절 사유 에러 코드 (예: INSUFFICIENT_BALANCE). REJECTED 주문에만 있다 |  |
| `createdAt` | string (date-time) | 주문 시각 (UTC) | `2026-09-22T08:12:44.101Z` |
| `updatedAt` | string (date-time) | 마지막 상태 변경 시각 (UTC) | `2026-09-22T08:12:44.101Z` |

```json
{
  "orderId": 2,
  "symbol": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "limitPrice": 80000,
  "filledQuantity": 0,
  "status": "ACCEPTED",
  "createdAt": "2026-09-22T08:12:44.101Z",
  "updatedAt": "2026-09-22T08:12:44.101Z"
}
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 요청 값이 올바르지 않습니다. |
| 400 | `INSUFFICIENT_BALANCE` | 주문 가능한 예수금이 부족합니다. |
| 400 | `INSUFFICIENT_POSITION` | 매도 가능한 수량이 부족합니다. |
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |
| 409 | `DUPLICATE_ORDER_REQUEST` | 같은 Idempotency-Key로 다른 주문을 요청했습니다. |
| 503 | `MARKET_PRICE_UNAVAILABLE` | 현재가를 조회할 수 없습니다. |
| 503 | `MARKET_PRICE_STALE` | 현재가가 오래되어 주문을 처리할 수 없습니다. |

<a id="api-13"></a>

#### 13. 주문 목록

`GET /api/trading/orders` · 인증 필요

최근 주문부터 돌려준다. status=ACCEPTED로 미체결 주문만 볼 수 있다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |
| `status` | query | enum (RECEIVED / VALIDATED / ACCEPTED / FILLED / CANCELLED / REJECTED) |  | 주문 상태로 거르기. ACCEPTED면 미체결만. 생략하면 전체 |  |

**응답** — 200 OK · OrderResponse 배열

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `orderId` | integer | 주문 ID | `2` |
| `symbol` | string | 종목코드 | `005930` |
| `side` | enum (BUY / SELL) | BUY 매수 / SELL 매도 | `BUY` |
| `orderType` | enum (MARKET / LIMIT) | MARKET 시장가 / LIMIT 지정가 | `LIMIT` |
| `quantity` | integer | 주문 수량 (주) | `10` |
| `limitPrice` | number | 지정가 (원). 시장가 주문이면 필드가 없다 | `80000` |
| `filledQuantity` | integer | 체결된 수량 (주) | `0` |
| `status` | enum (RECEIVED / VALIDATED / ACCEPTED / FILLED / CANCELLED / REJECTED) | 주문 상태. FILLED 전량 체결 / ACCEPTED 미체결(예수금·수량 묶임) / CANCELLED 취소 / REJECTED 거절 | `ACCEPTED` |
| `rejectReason` | string | 거절 사유 에러 코드 (예: INSUFFICIENT_BALANCE). REJECTED 주문에만 있다 |  |
| `createdAt` | string (date-time) | 주문 시각 (UTC) | `2026-09-22T08:12:44.101Z` |
| `updatedAt` | string (date-time) | 마지막 상태 변경 시각 (UTC) | `2026-09-22T08:12:44.101Z` |

```json
[
  {
    "orderId": 2,
    "symbol": "005930",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 10,
    "limitPrice": 80000,
    "filledQuantity": 0,
    "status": "ACCEPTED",
    "createdAt": "2026-09-22T08:12:44.101Z",
    "updatedAt": "2026-09-22T08:12:44.101Z"
  }
]
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 요청 값이 올바르지 않습니다. |
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |

<a id="api-14"></a>

#### 14. 주문 상세

`GET /api/trading/orders/{orderId}` · 인증 필요

주문 하나를 조회한다. 내 주문이 아니면 404다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |
| `orderId` | path | integer | O | 주문 ID | `2` |

**응답** — 200 OK · OrderResponse

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `orderId` | integer | 주문 ID | `2` |
| `symbol` | string | 종목코드 | `005930` |
| `side` | enum (BUY / SELL) | BUY 매수 / SELL 매도 | `BUY` |
| `orderType` | enum (MARKET / LIMIT) | MARKET 시장가 / LIMIT 지정가 | `LIMIT` |
| `quantity` | integer | 주문 수량 (주) | `10` |
| `limitPrice` | number | 지정가 (원). 시장가 주문이면 필드가 없다 | `80000` |
| `filledQuantity` | integer | 체결된 수량 (주) | `0` |
| `status` | enum (RECEIVED / VALIDATED / ACCEPTED / FILLED / CANCELLED / REJECTED) | 주문 상태. FILLED 전량 체결 / ACCEPTED 미체결(예수금·수량 묶임) / CANCELLED 취소 / REJECTED 거절 | `ACCEPTED` |
| `rejectReason` | string | 거절 사유 에러 코드 (예: INSUFFICIENT_BALANCE). REJECTED 주문에만 있다 |  |
| `createdAt` | string (date-time) | 주문 시각 (UTC) | `2026-09-22T08:12:44.101Z` |
| `updatedAt` | string (date-time) | 마지막 상태 변경 시각 (UTC) | `2026-09-22T08:12:44.101Z` |

```json
{
  "orderId": 2,
  "symbol": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "limitPrice": 80000,
  "filledQuantity": 0,
  "status": "ACCEPTED",
  "createdAt": "2026-09-22T08:12:44.101Z",
  "updatedAt": "2026-09-22T08:12:44.101Z"
}
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 요청 값이 올바르지 않습니다. |
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |
| 404 | `ORDER_NOT_FOUND` | 주문을 찾을 수 없습니다. |

<a id="api-15"></a>

#### 15. 주문 취소

`DELETE /api/trading/orders/{orderId}` · 인증 필요

미체결(ACCEPTED) 주문을 취소하고 묶인 예수금·수량을 푼다. 취소된 주문(status=CANCELLED)을 돌려준다. 이미 FILLED / CANCELLED / REJECTED인 주문은 409다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |
| `orderId` | path | integer | O | 주문 ID | `2` |

**응답** — 200 OK · OrderResponse

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `orderId` | integer | 주문 ID | `2` |
| `symbol` | string | 종목코드 | `005930` |
| `side` | enum (BUY / SELL) | BUY 매수 / SELL 매도 | `BUY` |
| `orderType` | enum (MARKET / LIMIT) | MARKET 시장가 / LIMIT 지정가 | `LIMIT` |
| `quantity` | integer | 주문 수량 (주) | `10` |
| `limitPrice` | number | 지정가 (원). 시장가 주문이면 필드가 없다 | `80000` |
| `filledQuantity` | integer | 체결된 수량 (주) | `0` |
| `status` | enum (RECEIVED / VALIDATED / ACCEPTED / FILLED / CANCELLED / REJECTED) | 주문 상태. FILLED 전량 체결 / ACCEPTED 미체결(예수금·수량 묶임) / CANCELLED 취소 / REJECTED 거절 | `ACCEPTED` |
| `rejectReason` | string | 거절 사유 에러 코드 (예: INSUFFICIENT_BALANCE). REJECTED 주문에만 있다 |  |
| `createdAt` | string (date-time) | 주문 시각 (UTC) | `2026-09-22T08:12:44.101Z` |
| `updatedAt` | string (date-time) | 마지막 상태 변경 시각 (UTC) | `2026-09-22T08:12:44.101Z` |

```json
{
  "orderId": 2,
  "symbol": "005930",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "limitPrice": 80000,
  "filledQuantity": 0,
  "status": "ACCEPTED",
  "createdAt": "2026-09-22T08:12:44.101Z",
  "updatedAt": "2026-09-22T08:12:44.101Z"
}
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 요청 값이 올바르지 않습니다. |
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |
| 404 | `ORDER_NOT_FOUND` | 주문을 찾을 수 없습니다. |
| 409 | `INVALID_ORDER_STATE` | 현재 주문 상태에서는 처리할 수 없습니다. |

### 체결

체결 내역

<a id="api-16"></a>

#### 16. 체결 내역 조회

`GET /api/trading/executions` · 인증 필요

최신순으로 돌려준다. 주문의 filledQuantity만으로는 얼마에 체결됐는지 알 수 없다. 체결가는 여기에만 있다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |

**응답** — 200 OK · ExecutionResponse 배열

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `executionId` | integer | 체결 ID | `12` |
| `orderId` | integer | 주문 ID | `8` |
| `symbol` | string | 종목코드 | `005930` |
| `side` | enum (BUY / SELL) | BUY 매수 / SELL 매도 | `SELL` |
| `price` | number | 체결가 (원). 지정가 주문도 지정가가 아니라 체결 시점 현재가다 | `91000` |
| `quantity` | integer | 체결 수량 (주) | `4` |
| `amount` | number | 체결 대금 (원) = price × quantity. 수수료 미포함 | `364000` |
| `fee` | number | 수수료 + 세금 (원). MVP는 0 | `0` |
| `realizedProfit` | number | 실현손익 (원) = (체결가 - 체결 시점 평균단가) × 수량. 매도 체결에만 있다 | `44000` |
| `executedAt` | string (date-time) | 체결 시각 (UTC) | `2026-09-22T08:12:44.101Z` |

```json
[
  {
    "executionId": 12,
    "orderId": 8,
    "symbol": "005930",
    "side": "SELL",
    "price": 91000,
    "quantity": 4,
    "amount": 364000,
    "fee": 0,
    "realizedProfit": 44000,
    "executedAt": "2026-09-22T08:12:44.101Z"
  }
]
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |

### 보유종목

종목별 보유 수량과 평균단가

<a id="api-17"></a>

#### 17. 보유종목 조회

`GET /api/trading/positions` · 인증 필요

보유 중인 종목만 돌려준다. 전량 매도한 종목은 빠진다. 평가금액·평가손익은 포트폴리오 조회에 있다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |

**응답** — 200 OK · PositionResponse 배열

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `symbol` | string | 종목코드 | `005930` |
| `quantity` | integer | 보유 수량 (주) | `10` |
| `reservedQuantity` | integer | 미체결 매도 주문이 묶어둔 수량 (주) | `4` |
| `availableQuantity` | integer | 매도 가능 수량 (주) = quantity - reservedQuantity | `6` |
| `averagePrice` | number | 평균 매입단가 (원). 이동평균이며 매도는 이 값을 바꾸지 않는다 | `80300` |
| `updatedAt` | string (date-time) | 마지막 변경 시각 (UTC) | `2026-09-22T08:12:44.101Z` |

```json
[
  {
    "symbol": "005930",
    "quantity": 10,
    "reservedQuantity": 4,
    "availableQuantity": 6,
    "averagePrice": 80300,
    "updatedAt": "2026-09-22T08:12:44.101Z"
  }
]
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |

### 포트폴리오

현재가 기준 평가

<a id="api-18"></a>

#### 18. 포트폴리오 조회

`GET /api/trading/portfolio` · 인증 필요

보유 종목을 현재가로 평가한 한 시점의 스냅샷이다. 평가손익(아직 팔지 않은 이익)과 실현손익(이미 판 이익)을 함께 준다.

**요청**

| 이름 | 위치 | 타입 | 필수 | 설명 | 예시 |
|---|---|---|---|---|---|
| `Authorization` | header | string | O | Bearer {accessToken} — mock-login 이 발급한 토큰 |  |

**응답** — 200 OK · PortfolioResponse

| 필드 | 타입 | 설명 | 예시 |
|---|---|---|---|
| `accountId` | integer | 가상 계좌 ID | `1` |
| `cashBalance` | number | 예수금 (원) | `98831200` |
| `reservedCash` | number | 미체결 매수 주문이 묶어둔 금액 (원) | `0` |
| `availableCash` | number | 주문 가능 금액 (원) = cashBalance - reservedCash | `98831200` |
| `totalPurchaseAmount` | number | 매입금액 합계 (원) = Σ(평균단가 × 수량) | `1168800` |
| `totalEvaluationAmount` | number | 평가금액 합계 (원) = Σ(현재가 × 수량) | `1169000` |
| `valuationProfit` | number | 평가손익 (원) = 평가금액 - 매입금액. 아직 팔지 않은 이익 | `200` |
| `valuationProfitRate` | number | 평가수익률 (%) | `0.02` |
| `realizedProfit` | number | 실현손익 누적 (원). 이미 팔아서 확정된 이익 | `1600` |
| `totalAssets` | number | 총자산 (원) = 예수금 + 평가금액 | `100000200` |
| `positions` | array&lt;object&gt; | 보유 종목별 평가 |  |
| `positions[].symbol` | string | 종목코드 | `005930` |
| `positions[].quantity` | integer | 보유 수량 (주) | `10` |
| `positions[].reservedQuantity` | integer | 미체결 매도 주문이 묶어둔 수량 (주) | `0` |
| `positions[].availableQuantity` | integer | 매도 가능 수량 (주) | `10` |
| `positions[].averagePrice` | number | 평균 매입단가 (원) | `80000` |
| `positions[].currentPrice` | number | 현재가 (원). 시세를 읽지 못하면 null이고, 이때 평가금액은 매입금액과 같게 둔다 | `80400` |
| `positions[].purchaseAmount` | number | 매입금액 (원) = 평균단가 × 수량 | `800000` |
| `positions[].evaluationAmount` | number | 평가금액 (원) = 현재가 × 수량 | `804000` |
| `positions[].valuationProfit` | number | 평가손익 (원) | `4000` |
| `positions[].valuationProfitRate` | number | 평가수익률 (%) | `0.5` |
| `evaluatedAt` | string (date-time) | 평가 시각 (UTC). 이 시점의 스냅샷이다 | `2026-09-22T08:12:44.101Z` |

```json
{
  "accountId": 1,
  "cashBalance": 98831200,
  "reservedCash": 0,
  "availableCash": 98831200,
  "totalPurchaseAmount": 1168800,
  "totalEvaluationAmount": 1169000,
  "valuationProfit": 200,
  "valuationProfitRate": 0.02,
  "realizedProfit": 1600,
  "totalAssets": 100000200,
  "positions": [
    {
      "symbol": "005930",
      "quantity": 10,
      "reservedQuantity": 0,
      "availableQuantity": 10,
      "averagePrice": 80000,
      "currentPrice": 80400,
      "purchaseAmount": 800000,
      "evaluationAmount": 804000,
      "valuationProfit": 4000,
      "valuationProfitRate": 0.5
    }
  ],
  "evaluatedAt": "2026-09-22T08:12:44.101Z"
}
```

**에러**

| HTTP | 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. |
