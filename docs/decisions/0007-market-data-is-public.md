# ADR-0007. 시세 API와 시세 WebSocket은 인증 없이 공개한다

- 상태: 채택
- 일자: 2026-09-21
- 관련: CLAUDE.md §6.1 (Gateway 인증 필터), §24, §25

## 맥락

Phase 1에서 Gateway는 `POST /api/users/mock-login`과 `/actuator/**`를 뺀 모든 경로에
`Authorization: Bearer` 토큰을 요구하도록 만들었다.

Phase 2에서 시세 WebSocket(`/ws/market`)을 붙이면서 두 가지가 걸렸다.

1. **브라우저 WebSocket 핸드셰이크에는 임의 헤더를 실을 수 없다.**
   `new WebSocket(url)` API에 헤더 옵션이 없다. 우회하려면 토큰을 쿼리 파라미터에 넣거나
   `Sec-WebSocket-Protocol`에 끼워 넣어야 하는데, 둘 다 토큰이 URL 로그나 프록시 로그에
   남을 수 있어 Mock 토큰이라도 좋은 습관이 아니다.
2. **시세는 사용자별 데이터가 아니다.** 현재가·호가·1분봉은 누가 조회하든 같은 값이다.
   계좌·주문·포지션과 성격이 다르다.

## 결정

Gateway의 인증 경계를 "사용자별 데이터"로 긋는다.

| 경로 | 인증 |
|---|---|
| `/api/market/**`, `/ws/market/**` | 불필요 |
| `/api/users/mock-login` | 불필요 |
| `/actuator/**` | 불필요 |
| 그 외 (`/api/trading/**`, `/api/users/me` 등) | 필요 |

## 결과

**좋은 점**

- WebSocket에 토큰을 끼워 넣는 우회책이 필요 없다.
- Phase 2 프론트엔드가 로그인 없이 실시간 시세를 띄울 수 있다.
  로그인 화면은 주문이 필요한 Phase 3·5에서 만든다.
- "사용자별 데이터만 인증"이라는 기준이 단순해서 새 엔드포인트를 추가할 때 판단이 쉽다.

**감수하는 점**

- 시세 API에 인증이 없으므로 호출량 제한이 없다.
  Rate Limiting은 Gateway에 구조만 잡혀 있고(§6.1) 아직 동작하지 않는다.
  공개 배포 전에 IP 기준 제한을 켜야 한다.
- 실제 증권사라면 시세도 계약·권한 대상이다. 이 프로젝트가 모의투자라서 가능한 선택이다.

## 후속 작업

- MVP 후반에 Gateway Rate Limiting을 시세 경로부터 활성화한다.
- 주문 체결 알림처럼 사용자별 실시간 데이터가 생기면 별도 WebSocket 엔드포인트를 두고,
  그쪽은 연결 직후 첫 메시지로 토큰을 보내 인증하는 방식을 쓴다.
