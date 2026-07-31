-- 文献画像异步任务数据库迁移脚本
-- 执行方式：mysql --default-character-set=utf8mb4 -uroot -p123456 research_assistant < docs/paper-profile-job-migration.sql

USE research_assistant;

CREATE TABLE IF NOT EXISTS paper_profile_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '画像任务ID',
    paper_id BIGINT NOT NULL COMMENT '所属文献ID',
    status VARCHAR(50) NOT NULL COMMENT '任务状态：PROCESSING/COMPLETED/FAILED',
    current_step VARCHAR(50) DEFAULT 'WAITING' COMMENT '当前步骤：WAITING/PREPARING/GENERATING_SECTIONS/GENERATING_PROFILE/SAVING_RESULT/COMPLETED/FAILED',
    progress_percent INT DEFAULT 0 COMMENT '进度百分比，0-100',
    processed_sections INT DEFAULT 0 COMMENT '已处理章节数',
    total_sections INT DEFAULT 0 COMMENT '总章节数',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    error_message LONGTEXT COMMENT '失败原因',
    start_time DATETIME COMMENT '开始时间',
    finish_time DATETIME COMMENT '结束时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_profile_job_paper_id (paper_id),
    INDEX idx_profile_job_status (status),
    INDEX idx_profile_job_create_time (create_time),
    CONSTRAINT fk_profile_job_paper
        FOREIGN KEY (paper_id) REFERENCES paper_reference(id)
        ON DELETE CASCADE
) COMMENT='文献画像异步生成任务表';

-- 兼容旧 MySQL：部分版本不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS，
-- 因此用 information_schema + PREPARE 做幂等字段补齐。
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'paper_profile_job'
      AND COLUMN_NAME = 'current_step'
);
SET @sql := IF(
    @column_exists = 0,
    "ALTER TABLE paper_profile_job ADD COLUMN current_step VARCHAR(50) DEFAULT 'WAITING' COMMENT '当前步骤：WAITING/PREPARING/GENERATING_SECTIONS/GENERATING_PROFILE/SAVING_RESULT/COMPLETED/FAILED' AFTER status",
    "SELECT 'current_step already exists'"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'paper_profile_job'
      AND COLUMN_NAME = 'progress_percent'
);
SET @sql := IF(
    @column_exists = 0,
    "ALTER TABLE paper_profile_job ADD COLUMN progress_percent INT DEFAULT 0 COMMENT '进度百分比，0-100' AFTER current_step",
    "SELECT 'progress_percent already exists'"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'paper_profile_job'
      AND COLUMN_NAME = 'processed_sections'
);
SET @sql := IF(
    @column_exists = 0,
    "ALTER TABLE paper_profile_job ADD COLUMN processed_sections INT DEFAULT 0 COMMENT '已处理章节数' AFTER progress_percent",
    "SELECT 'processed_sections already exists'"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'paper_profile_job'
      AND COLUMN_NAME = 'total_sections'
);
SET @sql := IF(
    @column_exists = 0,
    "ALTER TABLE paper_profile_job ADD COLUMN total_sections INT DEFAULT 0 COMMENT '总章节数' AFTER processed_sections",
    "SELECT 'total_sections already exists'"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'paper_profile_job'
      AND COLUMN_NAME = 'retry_count'
);
SET @sql := IF(
    @column_exists = 0,
    "ALTER TABLE paper_profile_job ADD COLUMN retry_count INT DEFAULT 0 COMMENT '已重试次数' AFTER total_sections",
    "SELECT 'retry_count already exists'"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
