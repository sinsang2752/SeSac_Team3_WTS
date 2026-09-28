# ADR-0006. Mock 인증은 HMAC 서명 토큰으로 구현한다

- 상태: 채택 (Phase 8에서 Cognito로 교체)
- 일자: 2026-09-21
- 관련: CLAUDE.md §6.4 (인증 우선순위), §24, §52, §48

## 맥락

CLAUDE.md §6.4는 MVP 인증 우선순위를 `1. Mock Login → 2. Spring Security 간단 인증 → 3. Cognito`로 둔다.
Phase 1에서 필요한 것은 "누가 요청했는지"를 서비스들이 합의하는 방법이다.

가장 단순한 구현은 토큰을 그냥 userId로 두는 것이다.
하지만 그러면 userId만 알면 누구든 사칭할 수 있고, 이 코드는 결국 EKS에 배포된다.

## 결정

JDK 내장 `javax.crypto.Mac`으로 HMAC-SHA256 서명 토큰을 만든다.
JWT 라이브러리를 추가하지 않는다 (CLAUDE.md §14).

```text
base64url(userId:expiresAtEpochSecond) . base64url(HMAC-SHA256)
```

- **발급**: user-service `POST /api/users/mock-login`
- **검증**: gateway-service `AuthenticationWebFilter`
- 검증에 성공하면 downstream 요청에 `X-User-Id`를 심는다.
- **클라이언트가 보낸 `X-User-Id`는 항상 제거한다.** downstream이 이 헤더를 신뢰하기 때문에,
  제거하지 않으면 헤더 하나로 사칭이 가능해진다.
- 비밀키는 환경변수 `AUTH_TOKEN_SECRET`로만 주입한다. 코드에 기본값을 두지 않아서,
  설정이 없으면 기동 단계에서 실패한다 (CLAUDE.md §52).

## 결과

**좋은 점**

- 서버에 세션이 없다. Stateless 요건을 만족한다 (§54).
- 비밀키 없이 토큰을 위조할 수 없다.
- 교체 지점이 한 곳(`AuthenticationWebFilter`)으로 모인다.

**감수하는 점 — 운영에 이대로 올리면 안 된다**

- 비밀번호가 없다. 이메일만 알면 누구나 그 계정으로 로그인된다.
- 폐기(revocation) 수단이 없다. 발급된 토큰은 만료 전까지 유효하다.
- 비밀키가 유출되면 임의의 사용자를 위조할 수 있다. 키 교체 절차도 없다.
- downstream 서비스 포트(8081~8083)를 외부에 노출하면 `X-User-Id`를 직접 넣어 우회할 수 있다.
  Gateway만 외부에 노출해야 한다.

## 후속 작업

Phase 8에서 Amazon Cognito로 교체한다 (§48).
그때 `AuthenticationWebFilter`가 Cognito JWK로 JWT를 검증하도록 바꾸고,
`MockAuthToken`과 `POST /api/users/mock-login`은 제거한다.
