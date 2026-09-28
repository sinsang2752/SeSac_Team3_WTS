package com.team.wts.trading;

import static com.team.wts.trading.support.TradingApiClient.order;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.wts.common.event.KafkaTopics;
import com.team.wts.trading.outbox.adapter.out.kafka.OutboxKafkaPublisher;
import com.team.wts.trading.outbox.adapter.out.kafka.OutboxPublishScheduler;
import com.team.wts.trading.outbox.adapter.out.persistence.OutboxEventJpaRepository;
import com.team.wts.trading.outbox.domain.OutboxEvent;
import com.team.wts.trading.outbox.domain.OutboxStatus;
import com.team.wts.trading.support.IntegrationTestBase;
import com.team.wts.trading.support.MarketPriceFixture;
import com.team.wts.trading.support.TradingApiClient;

/**
 * Transactional Outbox. (CLAUDE.md §15)
 *
 * <p>거래 트랜잭션은 Kafka를 모른다. 이벤트는 같은 커밋으로 테이블에 들어가고,
 * Publisher가 뒤이어 브로커로 옮긴다.
 */
class OutboxIntegrationTest extends IntegrationTestBase {

    private static final String SAMSUNG = "005930";

    @Autowired
    private TradingApiClient api;

    @Autowired
    private MarketPriceFixture marketPrice;

    @Autowired
    private OutboxEventJpaRepository outbox;

    @Autowired
    private OutboxKafkaPublisher publisher;

    @Autowired
    private OutboxPublishScheduler scheduler;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @DisplayName("주문이 커밋되면 이벤트가 PENDING으로 함께 남는다")
    void orderCommitWritesPendingOutboxRows() {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");

        long orderId = ((Number) api.place(user, order(SAMSUNG, "BUY", "MARKET", 10, null))
                .getBody().get("orderId")).longValue();

        List<OutboxEvent> events = pendingFor("Order", orderId);
        assertThat(events).extracting(OutboxEvent::eventType)
                .containsExactly(KafkaTopics.ORDER_CREATED, KafkaTopics.ORDER_ACCEPTED);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.retryCount()).isZero();
            assertThat(event.publishedAt()).isNull();
        });
    }

    @Test
    @DisplayName("체결 한 건이 execution / position / ledger / account 이벤트를 남긴다")
    void fillWritesSettlementEvents() {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");
        api.place(user, order(SAMSUNG, "BUY", "MARKET", 10, null));

        List<String> types = outbox.findAll().stream().map(OutboxEvent::eventType).toList();
        assertThat(types).contains(
                KafkaTopics.EXECUTION_COMPLETED,
                KafkaTopics.POSITION_CHANGED,
                KafkaTopics.LEDGER_CREATED,
                KafkaTopics.ACCOUNT_BALANCE_CHANGED);
    }

    @Test
    @DisplayName("Publisher가 PENDING을 Kafka로 옮기고 PUBLISHED로 표시한다")
    void publisherSendsAndMarksPublished() throws Exception {
        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");

        try (KafkaConsumer<String, String> consumer = subscribeTo(KafkaTopics.ORDER_CREATED)) {
            long orderId = ((Number) api.place(user, order(SAMSUNG, "BUY", "LIMIT", 1, "70000"))
                    .getBody().get("orderId")).longValue();

            int processed = publisher.publishBatch();
            assertThat(processed).isPositive();

            assertThat(pendingFor("Order", orderId)).isEmpty();
            assertThat(eventsFor("Order", orderId)).allSatisfy(event -> {
                assertThat(event.status()).isEqualTo(OutboxStatus.PUBLISHED);
                assertThat(event.publishedAt()).isNotNull();
            });

            JsonNode envelope = pollFor(consumer, String.valueOf(orderId));
            assertThat(envelope).isNotNull();
            // §16이 정한 봉투 형식 그대로 나가야 한다.
            assertThat(envelope.get("eventId").asText()).isNotBlank();
            assertThat(envelope.get("eventType").asText()).isEqualTo(KafkaTopics.ORDER_CREATED);
            assertThat(envelope.get("version").asInt()).isEqualTo(1);
            assertThat(envelope.get("traceId").asText()).isNotBlank();
            assertThat(envelope.get("payload").get("symbol").asText()).isEqualTo(SAMSUNG);
            assertThat(envelope.get("payload").get("status").asText()).isEqualTo("ACCEPTED");
        }
    }

    /**
     * aggregate_id만으로는 부족하다. 주문 ID와 계좌 ID는 각자의 시퀀스라 같은 숫자가 될 수 있다.
     * aggregate_type이 그 둘을 갈라 준다.
     */
    private List<OutboxEvent> pendingFor(String aggregateType, long aggregateId) {
        return eventsFor(aggregateType, aggregateId).stream()
                .filter(event -> event.status() == OutboxStatus.PENDING)
                .toList();
    }

    private List<OutboxEvent> eventsFor(String aggregateType, long aggregateId) {
        return outbox.findAll().stream()
                .filter(event -> event.aggregateType().equals(aggregateType))
                .filter(event -> event.aggregateId().equals(String.valueOf(aggregateId)))
                .sorted(java.util.Comparator.comparing(OutboxEvent::id))
                .toList();
    }

    private KafkaConsumer<String, String> subscribeTo(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        consumer.poll(Duration.ofSeconds(5)); // 파티션 배정
        return consumer;
    }

    private JsonNode pollFor(KafkaConsumer<String, String> consumer, String key) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, String> record : records) {
                if (key.equals(record.key())) {
                    return objectMapper.readTree(record.value());
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("주기 실행 경로도 실제로 발행한다")
    void scheduledTriggerPublishes() {
        // 앞선 테스트가 남긴 것을 먼저 비운다. 한 번의 폴링은 batch-size(100)까지만 가져가므로,
        // 밀린 행이 있으면 이 주문의 이벤트가 그 배치에 들어가지 못한다.
        while (publisher.publishBatch() > 0) {
            // drain
        }

        String user = UUID.randomUUID().toString();
        marketPrice.publish(SAMSUNG, "80000");
        long orderId = ((Number) api.place(user, order(SAMSUNG, "BUY", "LIMIT", 1, "70000"))
                .getBody().get("orderId")).longValue();
        assertThat(pendingFor("Order", orderId)).isNotEmpty();

        // 스케줄러 빈을 통해 부른다. 같은 빈 안에서 호출하면 트랜잭션 프록시를 거치지 않아
        // 비관적 락 조회가 실패한다. 그 경로가 바로 운영에서만 드러났던 결함이다.
        scheduler.publishPending();

        assertThat(pendingFor("Order", orderId)).isEmpty();
    }

    @Test
    @DisplayName("전부 내보내고 나면 더 할 일이 없다")
    void drainsUntilEmpty() {
        while (publisher.publishBatch() > 0) {
            // 앞선 테스트가 남긴 것까지 모두 비운다.
        }
        assertThat(publisher.publishBatch()).isZero();
    }
}
