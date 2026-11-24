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

-- 迁移（已有库执行一次）：
-- ALTER TABLE records ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'manual', ADD COLUMN verified TINYINT NOT NULL DEFAULT 0, ADD COLUMN flagged TINYINT NOT NULL DEFAULT 0;
-- ALTER TABLE summaries ADD COLUMN plan_goal INT NULL;
