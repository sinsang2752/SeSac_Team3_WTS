package com.team.wts.common.web;

/** 서비스 간 약속된 HTTP 헤더 이름. */
public final class WtsHeaders {

    /** Gateway가 생성해 downstream으로 전달하는 요청 추적 ID. (CLAUDE.md §40) */
    public static final String TRACE_ID = "X-Trace-Id";

    /**
     * Gateway의 인증 필터가 토큰을 검증한 뒤 심어주는 사용자 ID.
     *
     * <p>downstream 서비스는 이 헤더를 신뢰한다. 따라서 서비스 포트는 외부에 노출하지 않고
     * 반드시 Gateway를 통해서만 접근해야 한다. (CLAUDE.md §6.1)
     */
    public static final String USER_ID = "X-User-Id";

    /**
     * 주문 중복 생성을 막는 클라이언트 생성 키. (CLAUDE.md §14, §24)
     *
     * <p>같은 사용자가 같은 키로 재요청하면 주문을 새로 만들지 않고 기존 결과를 돌려준다.
     */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private WtsHeaders() {
    }
}
