# ADR-0003. Gateway의 traceId는 GlobalFilter가 아니라 WebFilter로 구현한다

- 상태: 채택
- 일자: 2026-09-21
- 관련: CLAUDE.md §6.1 (공통 Trace ID 생성), §40 (Logging)

## 맥락

CLAUDE.md §40은 "모든 요청에 traceId를 생성한다"고 규정한다.
처음에는 Spring Cloud Gateway의 `GlobalFilter`로 구현했으나 테스트에서 실패했다.

`GlobalFilter`는 **라우트에 매칭된 요청에만** 동작한다.
`/actuator/health` 처럼 게이트웨이가 직접 처리하는 요청은 이 체인을 타지 않아
traceId가 부여되지 않았다.

## 결정

`WebFilter`를 `HIGHEST_PRECEDENCE`로 등록한다.
WebFilter는 게이트웨이를 통과하는 모든 요청에 대해 실행되며,
여기서 변형한 `ServerWebExchange`가 그대로 라우팅 체인으로 전달되므로
downstream 서비스도 같은 traceId를 헤더로 받는다.

MDC는 사용하지 않는다. 리액티브 체인은 스레드를 넘나들기 때문에
MDC 값이 누락되거나, 이벤트 루프 스레드를 공유하는 다른 요청으로 샐 수 있다.
대신 Reactor Context에 담는다.
Servlet 기반인 market / trading / user 서비스에서는 MDC가 정상 동작하므로
`TraceIdFilter`에서 MDC를 사용하고 `finally`에서 반드시 제거한다.
(이 servlet 필터는 Phase 1에서 `backend/common` 모듈로 옮겼다.)

## 부수 이슈

게이트웨이와 downstream이 각각 `X-Trace-Id`를 응답에 실어 헤더가 중복 출력됐다.
`DedupeResponseHeader` 기본 필터로 해결한다.

```yaml
filters:
  - DedupeResponseHeader=X-Trace-Id Vary Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE
```

> **Phase 2 수정**: 처음에는 이 필터를 `default-filters`에 두었는데,
> WebSocket 라우트에도 적용되어 연결이 끊기는 문제가 있었다.
> 업그레이드 핸드셰이크가 끝나면 응답 헤더가 읽기 전용이 되는데 필터가 이를 수정하려다
> `UnsupportedOperationException`이 발생한다.
> 지금은 HTTP 라우트에만 개별로 붙인다. `GatewayRouteConfigTest`가 재발을 막는다.

## 후속 작업

리액티브 구간의 로그 상관관계(gateway 로그에 traceId 출력)는 미완성이다.
MVP 이후 OpenTelemetry 도입 시점에 마무리한다 (CLAUDE.md §48).
