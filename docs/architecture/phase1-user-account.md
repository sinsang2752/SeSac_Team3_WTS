# Phase 1 – User / Account Skeleton

CLAUDE.md §47 Phase 1의 산출물 정리.
**완료조건: `GET /api/trading/account` 에서 1억원 확인.**

## 인증 흐름

```text
1. POST /api/users/mock-login  { email, nickname }
        │  (인증 불필요 — gateway의 public-paths)
        ▼
   user-service: 이메일로 사용자 조회, 없으면 생성
                 HMAC 서명 토큰 발급
        ▼
   { userId, email, nickname, accessToken, expiresInSeconds }

2. 이후 모든 요청: Authorization: Bearer <accessToken>
        │
        ▼
   gateway-service AuthenticationWebFilter
     ├── 클라이언트가 보낸 X-User-Id 제거      ← 사칭 차단
     ├── 토큰 서명 · 만료 검증
     ├── 실패 → 401 { code: "UNAUTHORIZED", ... }
     └── 성공 → X-User-Id 주입 후 downstream 전달
        ▼
   market / trading / user 서비스
     @CurrentUserId 로 주입받아 사용
```

토큰 형식과 한계는 [ADR-0006](../decisions/0006-mock-auth-token.md) 참고.

## backend/common 모듈

Phase 0에서 `TraceIdFilter`가 세 서비스에 복제돼 있었다.
Phase 1에서 인증 토큰이라는 두 번째 공통 관심사가 생겨 모듈로 추출했다.

| 위치 | 내용 | 사용처 |
|---|---|---|
| `auth/MockAuthToken` | HMAC 토큰 발급·검증 | gateway(검증), user(발급) |
| `auth/AuthProperties` | `wts.auth.*` 바인딩 | 위와 동일 |
| `web/WtsHeaders` | `X-Trace-Id`, `X-User-Id` 상수 | 전체 |
| `web/TraceIdFilter` | servlet MDC 필터 | market, trading, user |
| `web/@CurrentUserId` | 컨트롤러 파라미터 주입 | trading, user |
| `error/*` | 공통 에러 코드·응답·핸들러 (§39) | servlet 서비스 |

`WtsCommonAutoConfiguration`이 servlet 컴포넌트를 자동 등록한다.
`@ConditionalOnClass(jakarta.servlet.Filter)` 때문에 WebFlux인 gateway에서는 로드되지 않는다.

`MockAuthToken` 빈은 `wts.auth.secret`이 설정된 서비스에서만 만들어진다.
market / trading은 토큰을 다루지 않으므로 비밀키가 필요 없다.

## 도메인 구조 (CLAUDE.md §7, §8)

```text
user-service
└── user/
    ├── domain/        User, UserRepository(port)
    ├── application/
    │   ├── command/   MockLoginCommand
    │   └── service/   MockLoginService, UserQueryService
    └── adapter/
        ├── in/web/    UserController, dto/
        └── out/persistence/  UserJpaRepository

trading-service
└── account/
    ├── domain/        VirtualAccount, VirtualAccountRepository(port)
    ├── application/
    │   └── service/   AccountService
    └── adapter/
        ├── in/web/    AccountController, dto/
        └── out/persistence/  VirtualAccountJpaRepository
```

두 가지는 의도적으로 하지 않았다 (CLAUDE.md §52).

- **도메인 모델과 JPA 엔티티를 분리하지 않았다.** DDD-lite로 엔티티 하나에 매핑을 붙였다.
  매퍼 두 벌을 유지하는 비용이 MVP 단계의 이득보다 크다.
- **포트와 Spring Data 사이에 Adapter 클래스를 두지 않았다.**
  `UserJpaRepository extends UserRepository, JpaRepository<...>` 로 충분하다.

## 스키마

[ADR-0004](../decisions/0004-flyway-for-schema-migration.md)에 따라 Flyway로 관리한다.
`ddl-auto=validate`가 엔티티와 스키마 일치를 기동 시 검증한다.

| 스키마 | 테이블 | 마이그레이션 |
|---|---|---|
| `wts_user` | `users` | `V1__create_users.sql` |
| `wts_trading` | `virtual_accounts` | `V1__create_virtual_accounts.sql` |

`virtual_accounts`에는 애플리케이션 검증과 별개로 DB CHECK 제약을 걸었다.
"예수금은 음수가 될 수 없다"는 MVP 성공 기준(§2)이라 이중으로 막는다.

```sql
CHECK (cash_balance >= 0)
CHECK (reserved_cash >= 0)
CHECK (reserved_cash <= cash_balance)
```

금액은 `DECIMAL(19,4)`, 시각은 `DATETIME(6)` UTC다 (§42, §43).

## 계좌 개설 시점

최초 조회 시 개설한다. 자세한 배경과 Phase 4 교체 계획은
[ADR-0005](../decisions/0005-account-provisioned-on-first-access.md) 참고.

## Phase 1에 포함하지 않은 것

| 항목 | 이유 / 도입 시점 |
|---|---|
| 관심종목 API (§24) | Phase 5 (WTS MVP) |
| `ledger_entries` / `INITIAL_DEPOSIT` | Phase 4 (Execution + Ledger) |
| `user.created` Kafka 이벤트 | Phase 4 (ADR-0005) |
| Spring Security | Phase 11 (Cognito 연동 시, CLAUDE.md §62.4) |
| 프론트엔드 로그인 화면 | Phase 5 (§26 `/login`) |
| 거래 도메인 에러 코드 (§39) | Phase 3 (주문 로직과 함께) |

## 검증

```bash
docker compose up -d --wait
./infra/scripts/run-backend.sh
./infra/scripts/health-check.sh

TOKEN=$(curl -s -X POST http://localhost:8080/api/users/mock-login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@wts.local","nickname":"데모투자자"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')

curl -s http://localhost:8080/api/trading/account -H "Authorization: Bearer $TOKEN"
# -> cashBalance / availableCash 100000000
```
