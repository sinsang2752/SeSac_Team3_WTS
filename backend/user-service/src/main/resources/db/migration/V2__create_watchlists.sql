-- 관심종목 (CLAUDE.md §23 watchlists, §6.4)
CREATE TABLE watchlists (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    VARCHAR(36) NOT NULL,
    symbol     VARCHAR(20) NOT NULL COMMENT '종목코드. 종목 마스터는 market-service가 갖는다',
    created_at DATETIME(6) NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    -- 같은 종목을 두 번 담을 수 없다. 중복 추가 요청은 이 제약이 막는다.
    UNIQUE KEY uk_watchlists_user_symbol (user_id, symbol),
    -- users와 같은 스키마에 있으므로 FK를 건다. 사용자가 지워지면 관심종목도 같이 지워진다.
    CONSTRAINT fk_watchlists_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
