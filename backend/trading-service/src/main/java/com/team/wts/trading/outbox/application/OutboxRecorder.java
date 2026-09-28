package com.team.wts.trading.outbox.application;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.wts.common.event.DomainEvent;
import com.team.wts.common.web.TraceIdFilter;
import com.team.wts.trading.outbox.domain.OutboxEvent;
import com.team.wts.trading.outbox.domain.OutboxEventRepository;

/**
 * 도메인 이벤트를 Outbox에 적는다. (CLAUDE.md §15, §16)
 *
 * <p>{@link Propagation#MANDATORY}다. 반드시 거래 트랜잭션 안에서 불려야 한다.
 * 별도 트랜잭션으로 적히면 "DB는 바뀌었는데 이벤트는 없다"가 다시 가능해져서
 * Outbox를 쓰는 의미가 없어진다.
 */
@Component
public class OutboxRecorder {

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxRecorder(OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String aggregateType, String aggregateId, String eventType, Object payload) {
        DomainEvent<Object> event = DomainEvent.of(eventType, aggregateId, currentTraceId(), payload);
        outbox.save(OutboxEvent.pending(aggregateType, aggregateId, eventType, serialize(event)));
    }

    private String serialize(DomainEvent<Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // 직렬화에 실패한 이벤트를 삼키면 거래는 커밋되고 이벤트만 사라진다.
            // 트랜잭션을 통째로 되돌리는 것이 맞다 (CLAUDE.md §52 – 예외를 무시하지 말 것).
            throw new IllegalStateException("이벤트 직렬화에 실패했다: " + event.eventType(), e);
        }
    }

    /**
     * 요청을 시작시킨 traceId. (CLAUDE.md §40)
     *
     * <p>HTTP 요청은 TraceIdFilter가, Kafka 소비는 소비자가 MDC에 넣는다.
     * 둘 다 아니면 null이고, 이벤트에는 traceId 없이 기록된다.
     */
    private static String currentTraceId() {
        return MDC.get(TraceIdFilter.MDC_KEY);
    }
}
