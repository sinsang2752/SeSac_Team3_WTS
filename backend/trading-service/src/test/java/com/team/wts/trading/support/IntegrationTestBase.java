package com.team.wts.trading.support;

import java.math.BigDecimal;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * 실제 MySQL / Valkey 컨테이너 위에서 도는 통합 테스트의 베이스. (CLAUDE.md §35, §36)
 *
 * <p>Flyway 마이그레이션이 그대로 적용되고 {@code ddl-auto=validate}가 엔티티 매핑을 검증하므로,
 * 스키마와 코드가 어긋나면 이 테스트가 먼저 깨진다.
 *
 * <p>Valkey는 최신 시세를 읽는 데, Kafka는 Outbox 발행과 미체결 지정가 소비에 쓴다.
 *
 * <p>컨테이너는 클래스마다 새로 띄우지 않고 JVM 전체에서 하나만 공유한다.
 * (Testcontainers reuse가 아니라 static 필드 + 수동 start)
 */
@Tag("integration") // Docker가 필요하다. CI는 단위 테스트와 다른 단계에서 돌린다 (CLAUDE.md §37)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    @SuppressWarnings("resource") // JVM 종료 시 Ryuk이 정리한다. 테스트마다 닫지 않는다.
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("wts_trading")
            .withUsername("wts")
            .withPassword("wts")
            .withCommand("--default-time-zone=+00:00");

    @SuppressWarnings("resource")
    private static final GenericContainer<?> VALKEY = new GenericContainer<>("valkey/valkey:8-alpine")
            .withExposedPorts(6379);

    @SuppressWarnings("resource")
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        MYSQL.start();
        VALKEY.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // 테스트는 Outbox Publisher를 직접 호출한다. 폴링이 끼어들어 결과가 흔들리지 않게 늦춘다.
        registry.add("trading.outbox.poll-interval-ms", () -> "600000");
        // @KafkaListener를 기본적으로 띄우지 않는다.
        //
        // Spring이 테스트 컨텍스트를 캐시해 JVM이 끝날 때까지 살려두므로, 켜 두면 컨텍스트마다
        // 소비자가 하나씩 같은 컨슈머 그룹에 들어간다. 파티션이 1개라 한 소비자만 배정받고
        // 나머지는 영원히 놀게 된다. 필요한 테스트가 직접 켠다.
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    /** 초기 자금을 바꿔 검증하는 테스트를 위해 기본값을 상수로 노출한다. */
    protected static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("100000000");
}
