# ADR-0002. Phase 0에서는 인프라만 컨테이너로 띄운다

- 상태: 채택 (MVP 후반 재검토)
- 일자: 2026-09-21
- 관련: CLAUDE.md §32 (Local Docker Compose), §0-3

## 맥락

CLAUDE.md §0-3은 "`docker compose up` 후 주요 기능이 실행되어야 한다"고 못 박는다.
동시에 §32는 "백엔드와 Frontend는 개발 중 로컬 프로세스로 실행해도 된다.
후반에는 Docker Compose 전체 실행도 지원한다"고 단계를 나눈다.

## 결정

`docker-compose.yml`에는 MySQL · Kafka · Valkey · Kafka UI만 둔다.
백엔드 4개 서비스와 프론트엔드는 로컬 프로세스로 실행하고,
`infra/scripts/run-backend.sh` 와 `health-check.sh` 로 기동·검증을 자동화한다.

## 결과

**좋은 점**

- 코드 변경 → 재기동 사이클이 짧다. 이미지 재빌드가 없다.
- 디버거 연결과 로그 확인이 단순하다.
- Phase 0에서 확인할 것(인프라 연결, health 200)은 이 구성으로 충분히 검증된다.

**감수하는 점**

- 지금은 `docker compose up` 하나로 전체 서비스가 뜨지 않는다.
  실행 절차가 3단계(compose → run-backend → frontend)로 나뉜다.
- 서비스 Dockerfile이 아직 없다. Phase 7(CI의 Docker Build)에서 추가해야 한다.

## 후속 작업

Phase 7에서 서비스별 Dockerfile과 `docker-compose.full.yml`을 추가해
`docker compose up` 단일 명령으로 전체 스택이 뜨도록 만든다.
