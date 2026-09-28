-- 주문 / 포지션 (CLAUDE.md §23, §9.3, §9.6, §14)

CREATE TABLE orders (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    account_id      BIGINT       NOT NULL,
    symbol          VARCHAR(20)  NOT NULL,
    side            VARCHAR(4)   NOT NULL COMMENT 'BUY / SELL',
    order_type      VARCHAR(8)   NOT NULL COMMENT 'MARKET / LIMIT',
    quantity        BIGINT       NOT NULL,
    -- 금액은 DECIMAL을 쓴다. 부동소수는 금지 (CLAUDE.md §42)
    limit_price     DECIMAL(19, 4) NULL COMMENT '지정가 주문에만 있다',
    filled_quantity BIGINT       NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL COMMENT 'RECEIVED/VALIDATED/ACCEPTED/FILLED/CANCELLED/REJECTED',
    -- §23 스키마에는 없는 컬럼이다. REJECTED 주문의 이유를 남기지 않으면
    -- 주문 내역에서 "왜 안 됐는지"를 설명할 수 없어 추가했다. 값은 ErrorCode 이름이다.
    reject_reason   VARCHAR(40)  NULL,
    idempotency_key VARCHAR(64)  NOT NULL COMMENT '클라이언트 생성 중복 방지 키 (CLAUDE.md §14)',
    created_at      DATETIME(6)  NOT NULL COMMENT 'UTC',
    updated_at      DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),

    -- 같은 계좌에서 같은 키로 두 번 주문할 수 없다.
    -- 실제 직렬화는 계좌 행의 비관적 락이 담당하고(ADR-0009), 이 제약은 마지막 방어선이다.
    UNIQUE KEY uk_orders_idempotency (account_id, idempotency_key),
    KEY ix_orders_account_id_desc (account_id, id DESC),

    CONSTRAINT ck_orders_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_orders_filled_within_quantity CHECK (filled_quantity >= 0 AND filled_quantity <= quantity),
    CONSTRAINT ck_orders_limit_price_positive CHECK (limit_price IS NULL OR limit_price > 0),
    -- 지정가 주문에는 지정가가 반드시 있어야 한다 (CLAUDE.md §9.4)
    CONSTRAINT ck_orders_limit_price_required CHECK (order_type <> 'LIMIT' OR limit_price IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE positions (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    account_id        BIGINT       NOT NULL,
    symbol            VARCHAR(20)  NOT NULL,
    quantity          BIGINT       NOT NULL DEFAULT 0,
    reserved_quantity BIGINT       NOT NULL DEFAULT 0 COMMENT '미체결 매도 주문이 묶어둔 수량',
    average_price     DECIMAL(19, 4) NOT NULL DEFAULT 0 COMMENT '이동평균 매입단가',
    created_at        DATETIME(6)  NOT NULL COMMENT 'UTC',
    updated_at        DATETIME(6)  NOT NULL COMMENT 'UTC',
    version           BIGINT       NOT NULL DEFAULT 0 COMMENT 'JPA Optimistic Lock (CLAUDE.md §13)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_positions_account_symbol (account_id, symbol),

    -- "보유한 수량보다 많이 매도할 수 없다"는 MVP 성공 기준이다 (CLAUDE.md §2).
    CONSTRAINT ck_positions_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT ck_positions_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_positions_reserved_within_quantity CHECK (reserved_quantity <= quantity),
    CONSTRAINT ck_positions_average_price_non_negative CHECK (average_price >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
