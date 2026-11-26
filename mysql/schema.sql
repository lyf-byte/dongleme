-- 「动了么」MySQL 8 数据库结构
-- 在服务器上执行：sudo mysql < mysql/schema.sql

CREATE DATABASE IF NOT EXISTS donglemei
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE donglemei;

CREATE TABLE IF NOT EXISTS users (
  id CHAR(36) PRIMARY KEY,
  email VARCHAR(255) NULL,
  phone VARCHAR(20) NULL,
  username VARCHAR(32) NULL,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(12) NOT NULL DEFAULT '运动新人',
  gender VARCHAR(10) NOT NULL DEFAULT 'secret',
  weight DECIMAL(5, 2) NOT NULL DEFAULT 60,
  weekly_goal INT NOT NULL DEFAULT 150,
  next_weekly_goal INT NULL,
  next_goal_week DATE NULL,
  is_admin TINYINT(1) NOT NULL DEFAULT 0,
  onboarded TINYINT(1) NOT NULL DEFAULT 0,
  streak INT NOT NULL DEFAULT 0,
  last_check_date DATE NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_phone (phone),
  UNIQUE KEY uk_users_username (username),
  CONSTRAINT chk_users_nickname CHECK (CHAR_LENGTH(nickname) BETWEEN 1 AND 12),
  CONSTRAINT chk_users_weight CHECK (weight BETWEEN 20 AND 300),
  CONSTRAINT chk_users_goal CHECK (weekly_goal BETWEEN 1 AND 2000)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS records (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  date DATE NOT NULL,
  type VARCHAR(20) NOT NULL,
  duration INT NOT NULL,
  calories INT NOT NULL,
  source VARCHAR(16) NOT NULL DEFAULT 'manual',
  verified TINYINT NOT NULL DEFAULT 0,
  flagged TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_records_user_date_type (user_id, date, type),
  KEY idx_records_date (date),
  KEY idx_records_user_date (user_id, date),
  CONSTRAINT fk_records_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT chk_records_duration CHECK (duration BETWEEN 1 AND 600),
  CONSTRAINT chk_records_calories CHECK (calories >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS summaries (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  week_start DATE NOT NULL,
  content TEXT NOT NULL,
  plan_goal INT NULL,
  regen_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_summaries_user_week (user_id, week_start),
  CONSTRAINT fk_summaries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS custom_sports (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  name VARCHAR(20) NOT NULL,
  met DECIMAL(4, 1) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_custom_sports_user_name (user_id, name),
  CONSTRAINT fk_custom_sports_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT chk_custom_sports_met CHECK (met BETWEEN 1 AND 20)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS activity_routes (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  date DATE NOT NULL,
  type VARCHAR(20) NOT NULL,
  started_at DATETIME NOT NULL,
  ended_at DATETIME NOT NULL,
  duration_sec INT NOT NULL,
  distance_m INT NOT NULL,
  calories INT NOT NULL,
  point_count INT NOT NULL,
  points LONGTEXT NOT NULL,
  preview TEXT NULL,
  start_lat DECIMAL(10, 7) NULL,
  start_lng DECIMAL(10, 7) NULL,
  end_lat DECIMAL(10, 7) NULL,
  end_lng DECIMAL(10, 7) NULL,
  client_id CHAR(36) NOT NULL,
  source VARCHAR(16) NOT NULL DEFAULT 'web',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_routes_user_client (user_id, client_id),
  KEY idx_routes_user_date (user_id, date),
  KEY idx_routes_user_created (user_id, created_at),
  CONSTRAINT fk_routes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT chk_routes_duration CHECK (duration_sec BETWEEN 60 AND 21600),
  CONSTRAINT chk_routes_distance CHECK (distance_m >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS user_badges (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  badge_id VARCHAR(64) NOT NULL,
  unlocked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_badge (user_id, badge_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS makeup_cards (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  target_date DATE NOT NULL,
  used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_makeup_user_date (user_id, target_date),
  KEY idx_makeup_user_used (user_id, used_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS follows (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  follower_id CHAR(36) NOT NULL,
  followee_id CHAR(36) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_follow_pair (follower_id, followee_id),
  KEY idx_follow_followee (followee_id),
  CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS messages (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  sender_id CHAR(36) NOT NULL,
  receiver_id CHAR(36) NOT NULL,
  type VARCHAR(16) NOT NULL DEFAULT 'text',
  content VARCHAR(500) NOT NULL,
  media_url VARCHAR(255) NULL,
  payload_json TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at DATETIME NULL,
  recalled_at DATETIME NULL,
  sender_deleted TINYINT NOT NULL DEFAULT 0,
  receiver_deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_messages_pair_time (sender_id, receiver_id, created_at),
  KEY idx_messages_receiver_unread (receiver_id, read_at),
  CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_messages_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  actor_id CHAR(36) NULL,
  type VARCHAR(16) NOT NULL,
  content VARCHAR(200) NULL,
  read_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_notifications_user (user_id, read_at, created_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS contact_remarks (
  owner_id CHAR(36) NOT NULL,
  target_id CHAR(36) NOT NULL,
  remark VARCHAR(20) NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (owner_id, target_id),
  CONSTRAINT fk_remarks_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_remarks_target FOREIGN KEY (target_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS user_blocks (
  owner_id CHAR(36) NOT NULL,
  target_id CHAR(36) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (owner_id, target_id),
  CONSTRAINT fk_blocks_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_blocks_target FOREIGN KEY (target_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS user_reports (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  reporter_id CHAR(36) NOT NULL,
  target_id CHAR(36) NOT NULL,
  reason VARCHAR(200) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_reports_target (target_id, created_at),
  CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_reports_target FOREIGN KEY (target_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS conversation_settings (
  owner_id CHAR(36) NOT NULL,
  peer_id CHAR(36) NOT NULL,
  pinned TINYINT NOT NULL DEFAULT 0,
  muted TINYINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (owner_id, peer_id),
  CONSTRAINT fk_settings_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_settings_peer FOREIGN KEY (peer_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 迁移（已有库执行一次）：
-- ALTER TABLE records ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'manual', ADD COLUMN verified TINYINT NOT NULL DEFAULT 0, ADD COLUMN flagged TINYINT NOT NULL DEFAULT 0;
-- ALTER TABLE summaries ADD COLUMN plan_goal INT NULL;
-- ALTER TABLE users ADD COLUMN next_weekly_goal INT NULL, ADD COLUMN next_goal_week DATE NULL;
-- CREATE TABLE IF NOT EXISTS messages (见上方完整定义);
-- CREATE TABLE IF NOT EXISTS notifications (见上方完整定义);
-- CREATE TABLE IF NOT EXISTS contact_remarks (见上方完整定义);
-- ALTER TABLE messages ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'text', ADD COLUMN media_url VARCHAR(255) NULL, ADD COLUMN payload_json TEXT NULL, ADD COLUMN recalled_at DATETIME NULL;
-- CREATE TABLE IF NOT EXISTS user_blocks / user_reports / conversation_settings (见上方完整定义);
