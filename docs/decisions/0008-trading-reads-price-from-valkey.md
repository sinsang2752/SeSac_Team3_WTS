# ADR-0008: Trading Service는 최신 시세를 Valkey에서 읽는다

- 상태: 채택
- 날짜: 2026-09-22
- 관련: CLAUDE.md §0-7, §6.2, §11, §18 / Phase 3

## 맥락

시장가 주문을 체결하려면 주문 처리 트랜잭션 안에서 현재가를 **동기적으로** 알아야 한다
(§11 – `현재 최신 가격 = 체결가격`). Trading Service가 시세를 얻는 방법은 셋이다.

1. Market Service REST 호출
2. Kafka `market.price.updated` 소비 후 자체 캐시 유지
3. Valkey의 최신 가격 조회

## 결정

**3번.** `market:price:{symbol}` 키를 읽는다.

## 이유

1번은 §6.2가 명시적으로 막는다 — "Market Service ↔ Trading Service 직접 HTTP 호출을 기본
구조로 사용하지 않는다". 주문 경로가 Market Service의 가용성에 묶이면 §0-7("Market Service
장애가 Trading DB 정합성을 깨뜨리지 않는다")을 지키기 어려워진다.

2번은 Trading Service가 종목별 최신가를 메모리에 들고 있어야 한다. 인스턴스를 두 개 띄우면
각자 다른 오프셋에서 출발해 서로 다른 가격을 본다. 재시작하면 토픽을 되감기 전까지 아무
주문도 받을 수 없다. Phase 4에서 미체결 지정가 주문을 이벤트로 깨우는 소비자는 필요하지만,
그건 "가격이 움직였다는 신호"를 받는 것이지 "지금 가격이 얼마인지"를 아는 것과 다르다.

3번은 §18이 정의한 Valkey의 원래 용도 그대로다. 두 서비스 모두 재시작해도 마지막 값이 남아
있고, 인스턴스가 늘어도 같은 값을 본다.

## 결과

- 두 서비스는 **Valkey 키 형식**으로 연결된다. `market:price:{symbol}` 값 구조를 바꾸면
  양쪽을 함께 고쳐야 한다. `MarketPriceTest`가 market-service가 실제로 쓰는 JSON을
  그대로 읽어 이 계약을 고정한다.
- Trading Service는 시세 모델 전체가 아니라 **체결에 필요한 필드만** 받는다
  (`symbol`, `price`, `timestamp`). 같은 이름의 클래스를 공유 모듈로 빼지 않았다.
  공유하면 시세 표현이 바뀔 때마다 거래 서비스가 함께 흔들린다.
- Valkey가 죽으면 주문을 받을 수 없다. 낡은 가격으로 체결하는 것보다 거절이 낫다는 판단이다.
  이미 들어간 주문과 계좌 잔고는 영향을 받지 않는다 (MySQL이 Source of Truth, §10).
- **시세 없음과 없는 종목코드를 구분하지 못한다.** 둘 다 `MARKET_PRICE_UNAVAILABLE`이다.
  Trading Service가 종목 마스터를 따로 들고 있지 않기 때문이다. 종목코드 형식(6자리 숫자)
  검증으로 오타의 상당수는 걸러진다. 구분이 필요해지면 `market:meta:{symbol}`(§18)을
  Market Service가 채우게 하는 것이 다음 수순이다.

## 대안을 다시 볼 시점

지정가 주문을 가격 변동으로 자동 체결하는 Phase 4에서는 Kafka 소비가 필요하다.
그때도 "체결 판정 시점의 가격"은 이벤트 payload를 쓰면 된다. Valkey 조회는 주문 접수 경로에
남는다.
