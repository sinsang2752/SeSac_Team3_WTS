# Phase 5 – WTS MVP

> CLAUDE.md §47 완료조건: 종목 검색 · Watchlist · 실시간 가격 · 차트 · 주문 Form ·
> 포지션 · 미체결 주문 · 체결내역 · 포트폴리오

실시간 가격(Phase 2), 주문 Form·포지션·미체결(Phase 3), 체결내역(Phase 4)은 이미 있었다.
이번에 채운 것은 **종목 검색, 관심종목, 차트, 포트폴리오, 그리고 라우팅(§26)** 이다.

## 라우트 (§26)

```
/                → /wts 로 이동
/login             로그인          공개
/wts               거래 화면       공개 — 시세와 차트는 로그인 없이 본다 (ADR-0007)
/wts/:symbol       종목 선택       공개
/orders            주문/체결       로그인 필요
/portfolio         자산            로그인 필요
```

**선택한 종목이 URL에 있다.** 새로고침해도 같은 종목이 열리고 링크를 공유할 수 있다.
`marketStore.selectedSymbol`은 URL을 따라가기만 한다 — 상태의 원본은 URL이다.

`RequireSession`이 토큰 없는 접근을 `/login`으로 보낸다. **진짜 보호는 Gateway가 한다.**
이건 토큰 없이 호출해 401만 잔뜩 받는 화면을 보여주지 않기 위한 것이다 (§6.1).

배포할 때 정적 서버는 모든 경로를 `index.html`로 넘겨야 한다. Vite 개발 서버는 기본으로 한다.

## 관심종목 (§23 watchlists, §24)

user-service에 `watchlist/` 모듈이 생겼다.

```http
GET    /api/users/me/watchlist
POST   /api/users/me/watchlist/{symbol}
DELETE /api/users/me/watchlist/{symbol}
```

**둘 다 멱등하다.** 이미 담긴 종목을 다시 담아도, 없는 종목을 빼도 오류가 아니다.
사용자가 원한 결과 상태가 이미 만족됐기 때문이다. 화면의 ★ 토글은 이 성질에 기댄다.

**종목이 실제로 존재하는지는 확인하지 않는다.** 종목 마스터는 market-service의 것이고,
확인하려면 서비스 간 동기 호출이 필요하다 (§6.2가 막는 방향이다).
여기서는 **형식**(6자리 숫자)만 본다. 화면이 market-service의 종목 목록과 맞춰 보여준다.

사용자당 개수 상한은 `user.watchlist.max-size`(기본 50)로 둔다. 코드에 박지 않는다.

`watchlists`는 `users`를 FK로 참조한다. 같은 스키마에 있으므로 서비스 경계를 넘지 않는다.

## 차트 (§4, §22)

TradingView Lightweight Charts로 1분봉을 그린다.

- 과거 봉은 `GET /api/market/stocks/{symbol}/candles` 로 한 번 읽고 1분마다 갱신한다.
- **진행 중인 봉은 WebSocket 현재가로 덧그린다.** 봉 전체를 매초 다시 내려받지 않기 위해서다.
- 색은 tokens.css의 등락 색과 맞춘다 (상승 적색 / 하락 청색, §30).

차트는 DOM에 직접 그리는 외부 라이브러리다. 마운트 시 한 번 만들고 언마운트에서 `remove()`
한다. 데이터 갱신만 effect로 따로 다룬다.

## 포트폴리오 (§24, §49)

```http
GET /api/trading/portfolio
```

평가손익에는 현재가가 필요하다. trading-service가 Valkey에서 보유 종목 시세를 **한 번에**
읽어 계산한다 (`MGET`, ADR-0008).

| 값 | 뜻 |
|---|---|
| 평가손익 | 아직 팔지 않은 이익 = 평가금액 - 매입금액 |
| 실현손익 | 이미 팔아서 확정된 이익. `executions.realized_profit` 누적 |
| 총자산 | 예수금 + 평가금액 |

**시세를 못 읽은 종목은 매입가로 평가한다.** `currentPrice`는 null로 내려가지만 평가금액은
매입금액과 같게 둔다. 모르는 값을 0원으로 만들어 총자산을 왜곡하지 않기 위해서다.

### 왜 화면에 두 가지 평가 경로가 있나

거래 화면의 보유종목 표는 이 API를 쓰지 않는다. WebSocket 시세로 직접 계산한다.
매초 바뀌는 값을 매초 조회할 수는 없기 때문이다.

포트폴리오 화면은 **한 시점의 일관된 스냅샷**이 필요해서 API를 쓴다. 10초마다 다시 읽는다.
둘은 용도가 다르다.

## 종목 검색 (§28 StockSearch)

검색어를 치면 전체 종목에서 찾고, 비우면 관심종목만 보여준다. 관심종목이 비어 있으면
전체를 보여준다 — 빈 화면에서 시작하지 않게 한다.

**서버 검색(`?keyword=`)을 쓰지 않는다.** 종목 목록이 이미 클라이언트에 있어서다.
종목 수가 늘어나면 서버 검색으로 바꾼다. 백엔드 엔드포인트는 Phase 2부터 있다.

## 고친 것 — 조회가 계좌를 못 만들던 문제

`GET /api/trading/{positions,orders,executions,portfolio}` 를 **계좌가 없는 사용자가
처음 호출하면 500**이 났다. Phase 3부터 있던 문제다.

계좌는 최초 조회 시점에 만들어진다(ADR-0005). 그런데 조회 서비스들이
`@Transactional(readOnly = true)` 였고, Spring은 읽기 전용 트랜잭션에서 JDBC 커넥션을
read-only로 연다. 그 안의 INSERT가 실패한다.

기존 테스트는 전부 주문을 먼저 내서(계좌가 이미 생긴 뒤) 조회했기 때문에 놓쳤다.
`readOnly`를 떼고, **네 경로 각각을 신규 사용자로 먼저 호출하는 테스트**를 넣었다.

## 패키지 정리

user-service의 파일들이 `com/team/wts/user/user/…` 에 있으면서 패키지는
`com.team.wts.user.…` 로 선언돼 있었다. Gradle은 통과시키지만 IDE가 경고하고,
§8의 도메인 우선 구조가 깨진 상태였다. `watchlist/`를 옆에 붙이기 전에 맞췄다.

```
com.team.wts.user
├── user/        사용자 · 프로필 · 인증 매핑
└── watchlist/   관심종목
```

trading-service(`order/ execution/ account/ position/ ledger/ portfolio/ outbox/`)와
market-service(`stock/ quote/ candle/`)가 쓰는 규칙과 같아졌다.

## 새 의존성

| 패키지 | 근거 |
|---|---|
| `react-router` | §26의 라우트를 직접 만들 이유가 없다 |
| `lightweight-charts` | §4가 지정한 차트 라이브러리 |

## Phase 5에서 하지 않은 것

| 항목 | 이유 |
|---|---|
| Figma 디자인 적용 | 산출물이 아직 없다. tokens.css의 자리표시자를 그대로 쓴다 (§30) |
| 원장 조회 화면 | §24에 엔드포인트가 없다. 사용자 관점의 답은 체결내역이다 |
| 동적 종목 구독 (§21) | 고정 5종목이면 충분하다. 종목이 늘면 그때 |
| KIS 연동 | Phase 6 |
