# ADR-0005. 가상 계좌는 최초 조회 시점에 개설한다 (Phase 4에서 교체)

- 상태: 채택 (임시 — Phase 4에서 재검토)
- 일자: 2026-09-21
- 관련: CLAUDE.md §9.1, §24, §47 (Phase 1 / Phase 4)

## 맥락

사용자는 user-service가, 가상 계좌는 trading-service가 소유한다.
사용자가 생기면 계좌도 생겨야 하는데, 두 서비스를 잇는 방법이 필요하다.

1. user-service → trading-service 동기 HTTP 호출.
   서비스 간 강결합이 생기고, trading-service가 죽으면 로그인도 실패한다.
2. `user.created` Kafka 이벤트를 trading-service가 소비.
   이벤트 기반 아키텍처(§55)에 맞지만 Kafka 연동은 Phase 4다 (§47).
3. trading-service가 계좌 조회 시 없으면 만든다.

## 결정

Phase 1에서는 3번을 쓴다. `AccountService.getOrOpen(userId)`가
계좌가 없으면 `trading.initial-cash`로 개설한다.

동시 요청은 `virtual_accounts`의 `UNIQUE (user_id)` 제약이 막고,
두 번째 요청은 제약 위반을 잡아 이미 만들어진 계좌를 돌려준다.

## 결과

**좋은 점**

- 서비스 간 결합이 없다. trading-service는 gateway가 심어준 `X-User-Id`만 안다.
- 멱등하다. 몇 번을 호출해도 계좌는 하나다.
- Phase 1 완료조건(`GET /api/trading/account`에서 1억 확인)을 그대로 만족한다.

**감수하는 점**

- `GET`이 부수효과를 갖는다. HTTP 의미론상 깔끔하지 않다.
- 계좌 개설 시점이 "사용자 생성"이 아니라 "첫 조회"다.
  원장(ledger)의 `INITIAL_DEPOSIT` 기록 시점도 함께 밀린다.

## 후속 작업

Phase 4에서 Kafka와 Outbox가 들어오면 2번으로 옮긴다.

- user-service가 `user.created`를 Outbox를 통해 발행
- trading-service가 소비해 계좌 개설 + `INITIAL_DEPOSIT` 원장 기록
- `getOrOpen`은 `get`으로 바꾸고, 계좌가 없으면 `ACCOUNT_NOT_FOUND`
