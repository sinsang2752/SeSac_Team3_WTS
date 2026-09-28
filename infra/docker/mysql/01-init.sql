-- WTS MVP – 초기 스키마 부트스트랩
-- Phase 0에서는 데이터베이스와 계정만 준비한다.
-- 테이블은 Phase 1 이후 JPA/마이그레이션으로 생성한다 (CLAUDE.md §23).

CREATE DATABASE IF NOT EXISTS wts
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

-- 서비스별 논리 분리를 위한 스키마.
-- MVP 단계에서는 동일 MySQL 인스턴스를 공유하되 스키마로 경계를 나눈다.
CREATE DATABASE IF NOT EXISTS wts_user
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS wts_trading
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS wts_market
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON `wts`.*         TO 'wts'@'%';
GRANT ALL PRIVILEGES ON `wts_user`.*    TO 'wts'@'%';
GRANT ALL PRIVILEGES ON `wts_trading`.* TO 'wts'@'%';
GRANT ALL PRIVILEGES ON `wts_market`.*  TO 'wts'@'%';
FLUSH PRIVILEGES;
