-- 论文 PDF 预处理与结构化分块数据库迁移脚本
-- 适用场景：已有 research_assistant 数据库，不想重建全部表。
-- 执行方式：mysql --default-character-set=utf8mb4 -uroot -p123456 research_assistant < docs/paper-structured-chunking-migration.sql

USE research_assistant;

CREATE TABLE IF NOT EXISTS paper_section (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '章节ID',
    paper_id BIGINT NOT NULL COMMENT '所属文献ID',
    section_title VARCHAR(500) COMMENT '章节标题',
    section_type VARCHAR(50) DEFAULT 'UNKNOWN' COMMENT '标准章节类型',
    section_index INT NOT NULL COMMENT '章节顺序',
    page_start INT COMMENT '起始页',
    page_end INT COMMENT '结束页',
    content_preview TEXT COMMENT '章节内容预览',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_paper_section_paper_id (paper_id),
    INDEX idx_paper_section_type (section_type),
    CONSTRAINT fk_paper_section_paper
        FOREIGN KEY (paper_id) REFERENCES paper_reference(id)
        ON DELETE CASCADE
) COMMENT='论文章节表';

SET @section_id_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'section_id'
);
SET @add_section_id_sql := IF(@section_id_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN section_id BIGINT COMMENT ''所属章节ID'' AFTER paper_id',
    'SELECT ''paper_chunk.section_id already exists''');
PREPARE add_section_id_stmt FROM @add_section_id_sql;
EXECUTE add_section_id_stmt;
DEALLOCATE PREPARE add_section_id_stmt;

SET @section_title_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'section_title'
);
SET @add_section_title_sql := IF(@section_title_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN section_title VARCHAR(500) COMMENT ''章节标题冗余'' AFTER section_id',
    'SELECT ''paper_chunk.section_title already exists''');
PREPARE add_section_title_stmt FROM @add_section_title_sql;
EXECUTE add_section_title_stmt;
DEALLOCATE PREPARE add_section_title_stmt;

SET @section_type_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'section_type'
);
SET @add_section_type_sql := IF(@section_type_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN section_type VARCHAR(50) DEFAULT ''UNKNOWN'' COMMENT ''标准章节类型'' AFTER section_title',
    'SELECT ''paper_chunk.section_type already exists''');
PREPARE add_section_type_stmt FROM @add_section_type_sql;
EXECUTE add_section_type_stmt;
DEALLOCATE PREPARE add_section_type_stmt;

SET @index_text_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'index_text'
);
SET @add_index_text_sql := IF(@index_text_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN index_text LONGTEXT COMMENT ''用于向量化检索的文本'' AFTER content',
    'SELECT ''paper_chunk.index_text already exists''');
PREPARE add_index_text_stmt FROM @add_index_text_sql;
EXECUTE add_index_text_stmt;
DEALLOCATE PREPARE add_index_text_stmt;

SET @page_start_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'page_start'
);
SET @add_page_start_sql := IF(@page_start_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN page_start INT COMMENT ''起始页'' AFTER page_number',
    'SELECT ''paper_chunk.page_start already exists''');
PREPARE add_page_start_stmt FROM @add_page_start_sql;
EXECUTE add_page_start_stmt;
DEALLOCATE PREPARE add_page_start_stmt;

SET @page_end_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'page_end'
);
SET @add_page_end_sql := IF(@page_end_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN page_end INT COMMENT ''结束页'' AFTER page_start',
    'SELECT ''paper_chunk.page_end already exists''');
PREPARE add_page_end_stmt FROM @add_page_end_sql;
EXECUTE add_page_end_stmt;
DEALLOCATE PREPARE add_page_end_stmt;

SET @token_count_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'token_count'
);
SET @add_token_count_sql := IF(@token_count_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN token_count INT DEFAULT 0 COMMENT ''估算token数'' AFTER chunk_index',
    'SELECT ''paper_chunk.token_count already exists''');
PREPARE add_token_count_stmt FROM @add_token_count_sql;
EXECUTE add_token_count_stmt;
DEALLOCATE PREPARE add_token_count_stmt;

SET @char_count_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'char_count'
);
SET @add_char_count_sql := IF(@char_count_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN char_count INT DEFAULT 0 COMMENT ''字符数'' AFTER token_count',
    'SELECT ''paper_chunk.char_count already exists''');
PREPARE add_char_count_stmt FROM @add_char_count_sql;
EXECUTE add_char_count_stmt;
DEALLOCATE PREPARE add_char_count_stmt;

SET @is_reference_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'is_reference'
);
SET @add_is_reference_sql := IF(@is_reference_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN is_reference TINYINT(1) DEFAULT 0 COMMENT ''是否参考文献片段'' AFTER char_count',
    'SELECT ''paper_chunk.is_reference already exists''');
PREPARE add_is_reference_stmt FROM @add_is_reference_sql;
EXECUTE add_is_reference_stmt;
DEALLOCATE PREPARE add_is_reference_stmt;

SET @is_noise_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'is_noise'
);
SET @add_is_noise_sql := IF(@is_noise_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN is_noise TINYINT(1) DEFAULT 0 COMMENT ''是否噪声片段'' AFTER is_reference',
    'SELECT ''paper_chunk.is_noise already exists''');
PREPARE add_is_noise_stmt FROM @add_is_noise_sql;
EXECUTE add_is_noise_stmt;
DEALLOCATE PREPARE add_is_noise_stmt;

SET @quality_score_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'quality_score'
);
SET @add_quality_score_sql := IF(@quality_score_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN quality_score DOUBLE DEFAULT 1.0 COMMENT ''片段质量分'' AFTER is_noise',
    'SELECT ''paper_chunk.quality_score already exists''');
PREPARE add_quality_score_stmt FROM @add_quality_score_sql;
EXECUTE add_quality_score_stmt;
DEALLOCATE PREPARE add_quality_score_stmt;

SET @strategy_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND COLUMN_NAME = 'chunk_strategy_version'
);
SET @add_strategy_sql := IF(@strategy_column_exists = 0,
    'ALTER TABLE paper_chunk ADD COLUMN chunk_strategy_version VARCHAR(100) DEFAULT ''fixed-window-v1'' COMMENT ''分块策略版本'' AFTER quality_score',
    'SELECT ''paper_chunk.chunk_strategy_version already exists''');
PREPARE add_strategy_stmt FROM @add_strategy_sql;
EXECUTE add_strategy_stmt;
DEALLOCATE PREPARE add_strategy_stmt;

UPDATE paper_chunk
SET section_type = COALESCE(section_type, 'UNKNOWN'),
    index_text = COALESCE(index_text, content),
    token_count = COALESCE(token_count, CEIL(CHAR_LENGTH(content) / 4)),
    char_count = COALESCE(char_count, CHAR_LENGTH(content)),
    is_reference = COALESCE(is_reference, 0),
    is_noise = COALESCE(is_noise, 0),
    quality_score = COALESCE(quality_score, 1.0),
    chunk_strategy_version = COALESCE(chunk_strategy_version, 'fixed-window-v1');

SET @section_id_index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND INDEX_NAME = 'idx_paper_chunk_section_id'
);
SET @add_section_id_index_sql := IF(@section_id_index_exists = 0,
    'CREATE INDEX idx_paper_chunk_section_id ON paper_chunk (section_id)',
    'SELECT ''idx_paper_chunk_section_id already exists''');
PREPARE add_section_id_index_stmt FROM @add_section_id_index_sql;
EXECUTE add_section_id_index_stmt;
DEALLOCATE PREPARE add_section_id_index_stmt;

SET @section_type_index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND INDEX_NAME = 'idx_paper_chunk_section_type'
);
SET @add_section_type_index_sql := IF(@section_type_index_exists = 0,
    'CREATE INDEX idx_paper_chunk_section_type ON paper_chunk (section_type)',
    'SELECT ''idx_paper_chunk_section_type already exists''');
PREPARE add_section_type_index_stmt FROM @add_section_type_index_sql;
EXECUTE add_section_type_index_stmt;
DEALLOCATE PREPARE add_section_type_index_stmt;

SET @reference_noise_index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_chunk' AND INDEX_NAME = 'idx_paper_chunk_reference_noise'
);
SET @add_reference_noise_index_sql := IF(@reference_noise_index_exists = 0,
    'CREATE INDEX idx_paper_chunk_reference_noise ON paper_chunk (is_reference, is_noise)',
    'SELECT ''idx_paper_chunk_reference_noise already exists''');
PREPARE add_reference_noise_index_stmt FROM @add_reference_noise_index_sql;
EXECUTE add_reference_noise_index_stmt;
DEALLOCATE PREPARE add_reference_noise_index_stmt;

SET @chunk_section_fk_exists := (
    SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_paper_chunk_section'
);
SET @add_chunk_section_fk_sql := IF(@chunk_section_fk_exists = 0,
    'ALTER TABLE paper_chunk ADD CONSTRAINT fk_paper_chunk_section FOREIGN KEY (section_id) REFERENCES paper_section(id) ON DELETE SET NULL',
    'SELECT ''fk_paper_chunk_section already exists''');
PREPARE add_chunk_section_fk_stmt FROM @add_chunk_section_fk_sql;
EXECUTE add_chunk_section_fk_stmt;
DEALLOCATE PREPARE add_chunk_section_fk_stmt;
