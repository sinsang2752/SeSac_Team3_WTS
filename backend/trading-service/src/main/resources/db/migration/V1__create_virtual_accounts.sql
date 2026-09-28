-- 가상 계좌 (CLAUDE.md §23, §9.2)
--
-- MVP는 사용자당 계좌 1개다. GET /api/trading/account 가 계좌 ID 없이 조회하는 것도 그 전제다.
CREATE TABLE virtual_accounts (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       VARCHAR(36)   NOT NULL COMMENT 'user-service의 users.id. 서비스 간 FK는 두지 않는다',
    -- 금액은 DECIMAL을 쓴다. 부동소수는 금지 (CLAUDE.md §42)
    cash_balance  DECIMAL(19, 4) NOT NULL,
    reserved_cash DECIMAL(19, 4) NOT NULL DEFAULT 0 COMMENT '미체결 매수 주문이 묶어둔 금액',
    created_at    DATETIME(6)   NOT NULL COMMENT 'UTC',
    updated_at    DATETIME(6)   NOT NULL COMMENT 'UTC',
    version       BIGINT        NOT NULL DEFAULT 0 COMMENT 'JPA Optimistic Lock (CLAUDE.md §13)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_virtual_accounts_user (user_id),

    -- 애플리케이션 검증과 별개로 DB에서도 불변식을 강제한다.
    -- "예수금은 음수가 될 수 없다"는 MVP 성공 기준이다 (CLAUDE.md §2).
    CONSTRAINT ck_virtual_accounts_cash_non_negative CHECK (cash_balance >= 0),
    CONSTRAINT ck_virtual_accounts_reserved_non_negative CHECK (reserved_cash >= 0),
    CONSTRAINT ck_virtual_accounts_reserved_within_cash CHECK (reserved_cash <= cash_balance)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
