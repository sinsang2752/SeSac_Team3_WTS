package com.team.wts.user.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 실제 MySQL 컨테이너 위에서 도는 통합 테스트의 베이스. (CLAUDE.md §35, §36)
 *
 * <p>Flyway 마이그레이션이 그대로 적용되고 {@code ddl-auto=validate}가 엔티티 매핑을 검증하므로,
 * 스키마와 코드가 어긋나면 이 테스트가 먼저 깨진다.
 *
 * <p>컨테이너는 클래스마다 새로 띄우지 않고 JVM 전체에서 하나만 공유한다.
 * (Testcontainers reuse가 아니라 static 필드 + 수동 start)
 */
@Tag("integration") // Docker가 필요하다. CI는 단위 테스트와 다른 단계에서 돌린다 (CLAUDE.md §37)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    @SuppressWarnings("resource") // JVM 종료 시 Ryuk이 정리한다. 테스트마다 닫지 않는다.
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("wts_user")
            .withUsername("wts")
            .withPassword("wts")
            .withCommand("--default-time-zone=+00:00");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // 운영 설정은 AUTH_TOKEN_SECRET 환경변수를 요구한다. 테스트는 고정값을 주입한다.
        registry.add("wts.auth.secret", () -> "integration-test-secret-not-used-anywhere-else");
    }
}
