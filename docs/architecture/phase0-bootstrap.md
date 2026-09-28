# Phase 0 – Bootstrap

CLAUDE.md §47 Phase 0의 산출물 정리.
**완료조건: 모든 서비스가 실행되고 health endpoint가 200.**

> 이 문서는 Phase 0 시점의 기록이다. 이후 바뀐 내용은
> [phase1-user-account.md](phase1-user-account.md)를 참고한다.
> 특히 세 서비스에 복제돼 있던 `TraceIdFilter`는 Phase 1에서 `backend/common`으로 옮겼고,
> Testcontainers는 Phase 1부터 쓰고 있다.

## 구성도

```text
                  브라우저 (localhost:5173)
                          │
                   Vite Dev Server
                   ├── /api/**    → Gateway
                   ├── /ws/**     → Gateway
                   └── /health/*  → 각 서비스 actuator (개발 전용)
                          │
                  gateway-service :8080   (WebFlux)
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
 market-service    trading-service    user-service
      :8081             :8082             :8083
        │                 │                 │
        ▼                 ▼                 ▼
  Valkey :6379      MySQL :3306        MySQL :3306
  Kafka  :9092      Kafka :9092
```

`/health/*` 프록시는 Vite 개발 서버에만 존재한다.
actuator를 Gateway 라우트로 외부에 노출하지 않기 위한 선택이다.

## 서비스별 현재 상태

| 서비스 | 포트 | 의존 인프라 | Phase 0 구현 범위 |
|---|---|---|---|
| gateway-service | 8080 | 없음 | 라우팅 4건, CORS, traceId 생성, 응답 헤더 중복 제거 |
| market-service | 8081 | Valkey, Kafka | 애플리케이션 골격, traceId MDC, `market.provider=mock` 설정 |
| trading-service | 8082 | MySQL, Kafka | 애플리케이션 골격, traceId MDC, `trading.initial-cash` 설정 |
| user-service | 8083 | MySQL | 애플리케이션 골격, traceId MDC |

도메인 로직(주문 / 체결 / 계좌 / 포지션 / 원장)은 아직 없다.
패키지도 만들지 않았다. Phase 1 이후 도메인 기준으로 생성한다 (CLAUDE.md §8).

## 데이터베이스

`infra/docker/mysql/01-init.sql`이 스키마 4개를 만든다.

| 스키마 | 용도 |
|---|---|
| `wts` | 공용 / 예비 |
| `wts_user` | user-service |
| `wts_trading` | trading-service |
| `wts_market` | market-service (1분봉 등, Phase 4 이후) |

MVP 단계에서는 하나의 MySQL 인스턴스를 공유하되 스키마로 경계를 나눈다.
테이블은 아직 없다. `spring.jpa.hibernate.ddl-auto=none`으로 자동 DDL을 막아 두었다.

## Kafka

KRaft 단일 노드. 토픽은 아직 만들지 않았다.
`KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`이므로 Phase 2에서 발행을 시작하면 자동 생성된다.
운영 수준 토픽 정의는 CLAUDE.md §16 참고.

리스너를 둘로 나눠 두었다.

- `INTERNAL` (`kafka:19092`) – 컨테이너 간 통신 (Kafka UI 등)
- `EXTERNAL` (`localhost:9092`) – 호스트에서 실행하는 백엔드

## 적용된 Cloud-Native 기본기 (CLAUDE.md §54)

| 항목 | 상태 |
|---|---|
| Health Check | 완료 – actuator `health`, liveness/readiness probe 활성화 |
| Graceful Shutdown | 완료 – `server.shutdown=graceful`, 종료 대기 20s |
| Externalized Configuration | 완료 – 모든 설정이 환경변수 치환 가능 |
| Structured Logging | 부분 – servlet 서비스는 traceId MDC 적용. 게이트웨이는 ADR-0003 참고 |
| Stateless Application | 완료 – 세션/로컬 상태 없음 |
| Container Ready | 미완 – 서비스 Dockerfile 없음 (ADR-0002) |

## 검증 방법

```bash
docker compose up -d --wait
./infra/scripts/run-backend.sh
./infra/scripts/health-check.sh
```

`health-check.sh`는 compose 상태, Kafka 브로커 응답, 4개 서비스의 health를 확인하고
하나라도 실패하면 종료 코드 1을 반환한다. Phase 7에서 CI 파이프라인이 그대로 사용한다.

## Phase 0에 포함하지 않은 것

의도적으로 뺐다. 필요해지는 단계에서 추가한다.

| 항목 | 도입 시점 |
|---|---|
| Spring Security | Phase 1 (Mock Login) |
| Resilience4j | Phase 6 (KIS 연동) |
| Testcontainers | Phase 3 (거래 로직 통합 테스트) |
| Zustand / TanStack Query / Lightweight Charts | Phase 2·5 |
| 서비스 Dockerfile | Phase 7 |
| Rate Limiting 실제 동작 | MVP 후반 |
