-- 文献画像与章节摘要基础层数据库迁移脚本
-- 执行方式：mysql --default-character-set=utf8mb4 -uroot -p123456 research_assistant < docs/paper-profile-section-summary-migration.sql

USE research_assistant;

CREATE TABLE IF NOT EXISTS paper_section_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '章节摘要ID',
    paper_id BIGINT NOT NULL COMMENT '所属文献ID',
    section_id BIGINT NOT NULL COMMENT '所属章节ID',
    section_type VARCHAR(50) DEFAULT 'UNKNOWN' COMMENT '标准章节类型',
    section_title VARCHAR(500) COMMENT '章节标题',
    summary LONGTEXT COMMENT '章节摘要',
    key_points LONGTEXT COMMENT '章节关键点',
    source_chunk_ids TEXT COMMENT '参与摘要的chunk ID列表',
    source_token_count INT DEFAULT 0 COMMENT '参与摘要的估算token数',
    summary_version VARCHAR(100) NOT NULL COMMENT '摘要策略版本',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_section_summary_version (paper_id, section_id, summary_version),
    INDEX idx_section_summary_paper_id (paper_id),
    INDEX idx_section_summary_section_id (section_id),
    CONSTRAINT fk_section_summary_paper
        FOREIGN KEY (paper_id) REFERENCES paper_reference(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_section_summary_section
        FOREIGN KEY (section_id) REFERENCES paper_section(id)
        ON DELETE CASCADE
) COMMENT='论文章节摘要表';

CREATE TABLE IF NOT EXISTS paper_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文献画像ID',
    paper_id BIGINT NOT NULL COMMENT '所属文献ID',
    title VARCHAR(500) COMMENT '论文标题冗余',
    research_problem LONGTEXT COMMENT '研究问题',
    method_summary LONGTEXT COMMENT '方法概述',
    experiment_summary LONGTEXT COMMENT '实验与评估概述',
    key_contributions LONGTEXT COMMENT '主要贡献',
    limitations LONGTEXT COMMENT '局限性',
    keywords LONGTEXT COMMENT '关键词',
    profile_text LONGTEXT COMMENT '面向RAG的完整画像文本',
    source_section_summary_ids TEXT COMMENT '参与生成的章节摘要ID列表',
    profile_version VARCHAR(100) NOT NULL COMMENT '画像策略版本',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_paper_profile_version (paper_id, profile_version),
    INDEX idx_paper_profile_paper_id (paper_id),
    CONSTRAINT fk_paper_profile_paper
        FOREIGN KEY (paper_id) REFERENCES paper_reference(id)
        ON DELETE CASCADE
) COMMENT='文献画像表';
