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
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(12) NOT NULL DEFAULT '运动新人',
  weight DECIMAL(5, 2) NOT NULL DEFAULT 60,
  weekly_goal INT NOT NULL DEFAULT 150,
  is_admin TINYINT(1) NOT NULL DEFAULT 0,
  onboarded TINYINT(1) NOT NULL DEFAULT 0,
  streak INT NOT NULL DEFAULT 0,
  last_check_date DATE NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_phone (phone),
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
  regen_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_summaries_user_week (user_id, week_start),
  CONSTRAINT fk_summaries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
