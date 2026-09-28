# ADR-0004. 스키마는 Flyway 마이그레이션으로 관리한다

- 상태: 채택
- 일자: 2026-09-21
- 관련: CLAUDE.md §23 (Database Schema Outline), §14 (라이브러리 추가 전 검토), §42

## 맥락

Phase 0에서 `spring.jpa.hibernate.ddl-auto=none`으로 자동 DDL을 막아 두었지만,
그 대신 스키마를 만들 수단이 없었다. CLAUDE.md §23의 테이블은 Phase 1~4에 걸쳐 단계적으로 늘어난다.

세 가지를 검토했다.

1. `ddl-auto=update` — 엔티티에서 DDL을 역산한다. 컬럼 삭제/타입 변경을 처리하지 못하고,
   `DECIMAL(19,4)`나 CHECK 제약 같은 것을 표현할 수 없다. 거래 데이터에 쓸 수 없다.
2. `infra/docker/mysql/*.sql` — 컨테이너 최초 기동에만 실행된다.
   스키마가 바뀔 때마다 볼륨을 지워야 해서 개발 중 반복 비용이 크다.
3. Flyway — 버전 관리되는 마이그레이션.

## 결정

Flyway를 쓴다. `org.flywaydb:flyway-core` + `flyway-mysql`이며
버전은 Spring Boot BOM이 관리하므로 별도 고정하지 않는다.

각 서비스가 자기 스키마의 마이그레이션을 소유한다.

```text
user-service/src/main/resources/db/migration/V1__create_users.sql
trading-service/src/main/resources/db/migration/V1__create_virtual_accounts.sql
```

함께 `ddl-auto=validate`로 바꿨다. 엔티티 매핑과 실제 스키마가 어긋나면
런타임이 아니라 기동 단계에서 실패한다.

## 결과

**좋은 점**

- DDL이 코드 리뷰 대상이 된다. `DECIMAL(19,4)`, CHECK 제약, 인덱스를 의도대로 쓴다.
- `validate`가 드리프트를 잡는다. 실제로 이 조합이 Phase 1 구현 중
  `CHAR(36)` vs Hibernate 기본 `VARCHAR(36)` 불일치를 기동 단계에서 잡아냈다.
- 통합 테스트(Testcontainers)가 운영과 같은 마이그레이션을 실행한다.

**감수하는 점**

- 라이브러리가 하나 늘었다. §14에 따라 대안을 먼저 검토했고, 기존 스택으로는
  거래 스키마에 필요한 제약을 표현할 수 없다고 판단했다.
- 적용된 마이그레이션 파일은 수정하면 안 된다. 변경은 항상 새 버전으로 추가한다.

## 규칙

- 이미 적용된 `V*.sql`은 절대 수정하지 않는다. Flyway 체크섬이 깨진다.
- 롤백 스크립트는 두지 않는다. 문제가 생기면 보정 마이그레이션을 새로 추가한다.
