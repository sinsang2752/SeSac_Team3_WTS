-- 체결 / 원장 / Outbox (CLAUDE.md §15, §23, §44)

CREATE TABLE executions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_id        BIGINT       NOT NULL,
    -- §23 스키마에 없는 컬럼. 계좌별 체결 조회(GET /api/trading/executions)를
    -- orders와 조인하지 않고 끝내기 위해 둔다. ledger_entries도 같은 방식이다.
    account_id      BIGINT       NOT NULL,
    symbol          VARCHAR(20)  NOT NULL,
    side            VARCHAR(4)   NOT NULL COMMENT 'BUY / SELL',
    price           DECIMAL(19, 4) NOT NULL COMMENT '체결가',
    quantity        BIGINT       NOT NULL,
    fee             DECIMAL(19, 4) NOT NULL DEFAULT 0 COMMENT 'MVP는 0 (CLAUDE.md §44)',
    -- §23 스키마에 없는 컬럼. 매도 체결에만 값이 있다.
    -- (체결가 - 체결 시점 평균단가) × 수량. 평균단가는 이후 매수로 바뀌므로 나중에 재계산할 수 없다.
    realized_profit DECIMAL(19, 4) NULL,
    executed_at     DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    KEY ix_executions_account_id_desc (account_id, id DESC),
    KEY ix_executions_order (order_id),

    CONSTRAINT ck_executions_price_positive CHECK (price > 0),
    CONSTRAINT ck_executions_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_executions_fee_non_negative CHECK (fee >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ledger_entries (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    account_id     BIGINT       NOT NULL,
    order_id       BIGINT       NULL,
    execution_id   BIGINT       NULL,
    type           VARCHAR(20)  NOT NULL COMMENT 'INITIAL_DEPOSIT/BUY/SELL/FEE/ADJUSTMENT',
    -- 부호 있는 금액이다. 입금은 양수, 출금은 음수.
    amount         DECIMAL(19, 4) NOT NULL,
    before_balance DECIMAL(19, 4) NOT NULL,
    after_balance  DECIMAL(19, 4) NOT NULL,
    created_at     DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    KEY ix_ledger_account_id_desc (account_id, id DESC),

    -- 원장의 핵심 불변식. 이게 깨지면 잔고를 원장으로 재구성할 수 없다.
    CONSTRAINT ck_ledger_balance_arithmetic CHECK (after_balance = before_balance + amount),
    CONSTRAINT ck_ledger_balance_non_negative CHECK (after_balance >= 0),
    CONSTRAINT ck_ledger_amount_not_zero CHECK (amount <> 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Transactional Outbox (CLAUDE.md §15).
-- 거래 트랜잭션이 이 테이블에 함께 INSERT하고, 별도 Publisher가 Kafka로 옮긴다.
CREATE TABLE outbox_events (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(30)  NOT NULL COMMENT 'Order / Execution / Account / Position / Ledger',
    aggregate_id   VARCHAR(64)  NOT NULL COMMENT 'Kafka 메시지 키. 같은 애그리거트의 순서를 보장한다',
    event_type     VARCHAR(60)  NOT NULL COMMENT '토픽 이름과 같다',
    -- DomainEvent 봉투 전체(eventId 포함)를 담는다 (§16).
    -- 발행 재시도에도 eventId가 그대로여야 소비자가 중복을 걸러낼 수 있다 (§17).
    payload_json   JSON         NOT NULL,
    status         VARCHAR(12)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PUBLISHED/FAILED',
    created_at     DATETIME(6)  NOT NULL COMMENT 'UTC',
    published_at   DATETIME(6)  NULL COMMENT 'UTC',
    retry_count    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- Publisher가 PENDING만 id 순서로 집는다.
    KEY ix_outbox_status_id (status, id),

    CONSTRAINT ck_outbox_retry_non_negative CHECK (retry_count >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
