-- 文献分类文件夹功能数据库迁移脚本
-- 适用场景：已有 research_assistant 数据库，不想重建全部表。
-- 执行方式：在 MySQL 中选择 research_assistant 数据库后执行本脚本。

USE research_assistant;

-- 1. 新增文献分类表
CREATE TABLE IF NOT EXISTS paper_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    description VARCHAR(500) COMMENT '分类说明',
    sort_order INT DEFAULT 0 COMMENT '排序值',
    system_flag TINYINT(1) DEFAULT 0 COMMENT '是否系统分类：0否/1是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_paper_category_name (name)
) COMMENT='文献分类表';

-- 2. 确保默认“未分类”存在。
-- 如果历史数据中已有同名普通分类，将它升级为系统分类，避免后端按 system_flag 查找失败。
INSERT INTO paper_category (name, description, sort_order, system_flag)
SELECT '未分类', '系统默认分类，不能删除', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM paper_category WHERE name = '未分类');

UPDATE paper_category
SET description = COALESCE(description, '系统默认分类，不能删除'),
    sort_order = 0,
    system_flag = 1
WHERE name = '未分类';

-- 3. 给文献表增加分类字段。
-- MySQL 8 不同小版本对 ALTER TABLE ... IF NOT EXISTS 支持不完全一致，
-- 这里用 information_schema 判断后再动态执行，避免重复执行时报错。
SET @category_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'paper_reference'
      AND COLUMN_NAME = 'category_id'
);

SET @add_category_column_sql := IF(
    @category_column_exists = 0,
    'ALTER TABLE paper_reference ADD COLUMN category_id BIGINT COMMENT ''文献分类ID''',
    'SELECT ''paper_reference.category_id already exists'''
);

PREPARE add_category_column_stmt FROM @add_category_column_sql;
EXECUTE add_category_column_stmt;
DEALLOCATE PREPARE add_category_column_stmt;

-- 4. 给旧文献补默认分类；如果旧数据中已有无效 category_id，也转入“未分类”。
UPDATE paper_reference pr
LEFT JOIN paper_category pc ON pr.category_id = pc.id
SET pr.category_id = (SELECT id FROM paper_category WHERE name = '未分类' AND system_flag = 1 LIMIT 1)
WHERE pr.category_id IS NULL OR pc.id IS NULL;

-- 5. 增加分类索引，同样先判断，避免重复执行时报 Duplicate key name。
SET @category_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'paper_reference'
      AND INDEX_NAME = 'idx_paper_category_id'
);

SET @add_category_index_sql := IF(
    @category_index_exists = 0,
    'CREATE INDEX idx_paper_category_id ON paper_reference (category_id)',
    'SELECT ''idx_paper_category_id already exists'''
);

PREPARE add_category_index_stmt FROM @add_category_index_sql;
EXECUTE add_category_index_stmt;
DEALLOCATE PREPARE add_category_index_stmt;

-- 6. 将 category_id 固定为必填，确保一篇文献始终属于一个分类。
ALTER TABLE paper_reference
    MODIFY COLUMN category_id BIGINT NOT NULL COMMENT '文献分类ID';

-- 7. 增加外键，确保文献分类一定指向有效分类。
-- 删除分类时由后端先把文献移动到“未分类”，因此这里不使用 ON DELETE SET NULL。
SET @category_fk_exists := (
    SELECT COUNT(*)
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND CONSTRAINT_NAME = 'fk_paper_reference_category'
);

SET @add_category_fk_sql := IF(
    @category_fk_exists = 0,
    'ALTER TABLE paper_reference ADD CONSTRAINT fk_paper_reference_category FOREIGN KEY (category_id) REFERENCES paper_category(id)',
    'SELECT ''fk_paper_reference_category already exists'''
);

PREPARE add_category_fk_stmt FROM @add_category_fk_sql;
EXECUTE add_category_fk_stmt;
DEALLOCATE PREPARE add_category_fk_stmt;
