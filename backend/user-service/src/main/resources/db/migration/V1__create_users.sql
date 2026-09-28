-- 사용자 기본정보 (CLAUDE.md §23)
CREATE TABLE users (
    id         VARCHAR(36)  NOT NULL COMMENT 'UUID. 서비스 경계를 넘나들므로 전역 유일해야 한다',
    email      VARCHAR(255) NOT NULL,
    nickname   VARCHAR(50)  NOT NULL,
    created_at DATETIME(6)  NOT NULL COMMENT 'UTC (CLAUDE.md §43)',
    updated_at DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    -- Mock Login은 같은 이메일로 재로그인하면 기존 사용자를 돌려준다.
    -- 동시 요청으로 중복 생성되는 것을 DB 차원에서 막는다.
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
