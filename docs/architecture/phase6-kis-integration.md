# Phase 6 – KIS Integration

> CLAUDE.md §47 완료조건: `MARKET_PROVIDER=kis` 로 실제 데이터 수신

Mock 시세를 한국투자증권 OpenAPI 실시간 시세로 갈아 끼운다. **trading-service는 한 줄도
바뀌지 않았다** — 두 서비스가 Valkey 키와 Kafka 토픽으로만 연결돼 있기 때문이다 (ADR-0008).

## 갈아 끼우는 지점

```
                     ┌─ MockMarketDataAdapter   @ConditionalOnProperty(mock)  ─┐
MarketDataProvider ──┤                                                         ├─> MarketDataListener
        (§19)        └─ KisMarketDataAdapter    @ConditionalOnProperty(kis)   ─┘          │
                                                                                          ▼
                                                               QuoteIngestService (양쪽 공통)
                                                                    │
                                        Valkey ──┬── Kafka ──┬── WebSocket
                                                 │           │
                                          거래 체결 판정    1분봉 집계
```

`.env` 의 `MARKET_PROVIDER` 한 줄이 전부다. 근거는 [ADR-0012](../decisions/0012-kis-adapter-switch.md).

## 인증 (§20)

두 가지가 필요하고 **서로 다르다.**

| | 용도 | 엔드포인트 | 특징 |
|---|---|---|---|
| 접근토큰 | REST 호출 | `/oauth2/tokenP` | 유효 24시간, **발급 1분당 1회 제한** |
| approval_key | WebSocket 등록 | `/oauth2/Approval` | 요청 필드명이 `appsecret`이 아니라 `secretkey` |

둘 다 캐시하되 **둘 다 만료된다.** 접근토큰은 응답의 `expires_in`을 보고 만료 10분 전에
갱신한다. 승인키는 KIS가 만료 시각을 주지 않으므로 `kis.auth.approval-key-ttl`
(기본 12시간)마다 다시 받는다. 만료된 승인키로 등록하면 오류 없이 시세만 조용히 끊긴다.

접근토큰은 재기동이 잦은 개발 중에 1분 제한에 걸릴 수 있다.

**approval_key는 연결 전에 미리 받는다.** 연결 콜백 안에서 받으면 REST 호출이 핸드셰이크
완료를 막아 연결이 실패한 것처럼 보이고, 재연결이 걸려 세션이 두 개가 된다
([ADR-0013](../decisions/0013-kis-realtime-connection.md)).

## 실시간 프레임

```
0|H0STCNT0|002|005930^140610^277750^2^3750^...^20260922^...
│ │        │   └ 본문. 레코드가 여러 개면 ^ 로 이어 붙는다
│ │        └ 레코드 건수 (체결이 몰리면 6건까지 봤다)
│ └ TR ID
└ 암호화 여부 (시세 TR은 0)
```

| TR | 내용 | 레코드당 필드 |
|---|---|---|
| `H0STCNT0` | 실시간 체결가 | 47 |
| `H0STASP0` | 실시간 호가 (10단계) | 63 |

**전일 종가는 직접 오지 않는다.** 현재가와 전일대비(절댓값 + 부호)로 되돌린다.
부호는 `1` 상한 / `2` 상승 / `3` 보합 / `4` 하한 / `5` 하락이다.

거래량은 `ACML_VOL`(당일 누적)을 쓴다. Mock과 같은 선택이고, 중복 수신에 안전하다는
성질(§17)도 그대로 유지된다.

시각은 한국 시각으로 온다. 체결가 TR에는 영업일자가 함께 있어 정확한 시점을 만들 수 있다.
호가 TR에는 시각만 있어 오늘 날짜로 본다. 저장은 UTC다 (§43).

`KisRealtimeDecoderTest`가 **실제 수신 프레임**으로 이 배치를 고정한다. 필드 위치가
계약의 전부라 KIS가 형식을 바꾸면 여기가 먼저 깨져야 한다.

## 연결 관리 (§20)

구현 중 실제로 세 가지 방식으로 끊겼고, 각각 고쳤다. 자세한 내용은
[ADR-0013](../decisions/0013-kis-realtime-connection.md).

| 증상 | 원인 | 대응 |
|---|---|---|
| `ALREADY IN USE appkey` | 같은 앱키로 세션 2개 | 연결 완료를 기다리지 않고, 열린 세션이 있으면 재연결 안 함 |
| `1009 text message too big` | 기본 버퍼 8KB | `max-text-message-size` 512KB |
| **아무 로그 없이 데이터만 멈춤** | close 이벤트 없는 죽은 연결 | `stale-timeout`(90초) 침묵 시 직접 끊어 재연결 |

세 번째가 가장 위험하다. close가 오지 않으면 재연결 로직이 아예 돌지 않아 화면만 멈춘다.
판단 기준을 "데이터"가 아니라 "메시지"로 잡았다 — 장 마감 후에는 시세가 없지만 서버의
PINGPONG은 계속 오기 때문이다.

재연결하면 `afterConnectionEstablished`에서 구독을 전부 재등록한다 (subscription restore).

## 유량 제한

KIS는 초당 거래건수를 제한한다. 기동 시 종목별 현재가를 연달아 조회하면 바로 걸린다
(`EGW00201`). `kis.rest.request-interval`(기본 500ms)로 간격을 둔다.

모의투자는 **초당 2건**이라 500ms 간격으로는 경계에 걸린다. 기본값을 1초로 두고,
그래도 실패한 종목은 한 번 더 시도한다. 그마저 실패하면 실시간이 채운다.

## 스트림 상태를 평소에 남긴다

연결이 끊긴 뒤 원인을 찾으려면 끊기기 전 기록이 필요하다. 1분마다 한 줄 남긴다.

```
KIS 스트림: 연결=true 구독=5종목 프레임=273건(+29) heartbeat=0건 마지막수신=0초전 마지막시세=0초전
```

구독 성공 응답은 DEBUG로 내렸다 — 종목 × TR 수만큼 쏟아져 정작 중요한 실패를 덮는다.
`rt_cd`가 0이 아닌 제어 메시지는 WARN이다.

## 장 시작 전 · 마감 후

실시간은 **장중에만** 흐른다. 그래서 기동 시 REST `inquire-price`로 마지막 시세를 채운다.
안 그러면 화면이 비고 주문이 전부 거절된다.

**다만 장 마감 후에는 시장가 주문이 거절된다.** 마지막 체결 시각이 15:30에 멈춰 있어
`market.price.max-age`(5초)를 넘기기 때문이다. 이건 §11이 의도한 동작이다.
언제든 거래 흐름을 연습하려면 `MARKET_PROVIDER=mock` 으로 되돌린다 (§0-4).

## 설정

```yaml
kis:
  environment: ${KIS_ENVIRONMENT:vts}   # vts 모의투자 / real 실전투자
  app-key: ${KIS_APP_KEY:}              # 기본값 없음. 비어 있으면 기동에서 막는다 (§52)
  app-secret: ${KIS_APP_SECRET:}
  rest:
    request-interval: 500ms             # 초당 거래건수 제한 회피
  websocket:
    subscription-limit: 40              # 종목 하나가 등록 2건을 쓴다 (§20)
    max-text-message-size: 512KB
    stale-timeout: 90s
    reconnect-initial-delay: 1s
    reconnect-max-delay: 30s
```

실전투자와 모의투자는 **도메인도 앱키도 다른 시스템**이다. 섞으면 인증이 실패한다.
`environment` 하나로 REST·WebSocket 주소가 함께 바뀐다.

## 곁들여 고친 것

`GET /api/market/stocks` 응답에서 `previousClose`를 뺐다. 그 값은 설정의
`market.stocks[].previous-close`에서 왔는데, 그건 **Mock 모드의 출발 가격**이지
시장 사실이 아니다. kis 모드에서는 실제와 전혀 다른 숫자가 나갔다
(삼성전자 80,000 vs 실제 274,000). 전일 종가는 `/price`가 준다.

Mock 모드의 출발 가격도 실제 수준으로 갱신했다. 가격대가 맞아야 KRX 호가단위 구간도
실제와 같아진다.

## 검증

| 항목 | 방법 |
|---|---|
| 프레임 파싱 | `KisRealtimeDecoderTest` — 실제 수신 프레임 고정 |
| 프레임 분해 | `KisRealtimeFrameTest` — 다중 레코드, 암호화 표시, 깨진 입력 |
| 실제 수신 | 5종목 실시간 체결가·호가, 1분봉 집계까지 확인 |
| 실제 거래 | 시장가 매수 275,500원 체결 → 지정가 매도 276,750원이 202초 뒤 **자동 체결**, 실현손익 1,500원 |

KIS에 실제로 붙는 통합 테스트는 만들지 않았다. 자격증명이 없는 CI에서 돌 수 없고,
장 운영시간에만 의미가 있다. 파서를 실제 데이터로 고정하는 것이 CI에서 할 수 있는 최선이다.

## Phase 6에서 하지 않은 것

| 항목 | 이유 |
|---|---|
| 암호화 프레임 복호화 | 체결통보(`H0STCNI0`) 용도다. 주문 체결은 우리가 모의로 처리한다 (§3) |
| 동적 구독 (§21) | 고정 5종목이면 충분하다. 조건부 구독은 종목이 늘어난 뒤 |
| KIS 주문 API | MVP 제외 범위다 (§3 – 실제 주식 주문) |
| 종목 마스터 API | 종목명은 설정에 둔다. 종목이 늘면 KIS 종목정보 API로 옮긴다 |
