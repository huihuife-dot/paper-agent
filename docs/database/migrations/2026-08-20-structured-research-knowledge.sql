-- 结构化科研知识引擎：论文目录、知识单元与用户反馈

CREATE TABLE IF NOT EXISTS paper_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '目录记录ID',
    paper_id BIGINT NOT NULL COMMENT '论文ID',
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_BUILT' COMMENT 'NOT_BUILT/BUILDING/COMPLETED/PARTIAL/FAILED/STALE',
    research_domains JSON COMMENT '研究领域列表',
    research_tasks JSON COMMENT '研究任务列表',
    method_tags JSON COMMENT '方法与模型标签',
    dataset_tags JSON COMMENT '数据集标签',
    metric_tags JSON COMMENT '评价指标标签',
    catalog_text LONGTEXT COMMENT '用于低成本候选论文发现的目录文本',
    knowledge_count INT NOT NULL DEFAULT 0 COMMENT '知识单元总数',
    verified_count INT NOT NULL DEFAULT 0 COMMENT '人工确认知识单元数',
    extraction_version VARCHAR(100) COMMENT '抽取策略版本',
    model_provider VARCHAR(100) COMMENT '抽取模型供应商',
    model_name VARCHAR(200) COMMENT '抽取模型名称',
    error_message LONGTEXT COMMENT '最近构建失败或部分失败原因',
    build_time DATETIME COMMENT '最近完成构建时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_paper_catalog_paper (paper_id),
    INDEX idx_paper_catalog_status (status),
    CONSTRAINT fk_paper_catalog_paper FOREIGN KEY (paper_id)
        REFERENCES paper_reference(id) ON DELETE CASCADE
) COMMENT='论文结构化目录与知识索引状态';

CREATE TABLE IF NOT EXISTS paper_knowledge_unit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识单元ID',
    paper_id BIGINT NOT NULL COMMENT '论文ID',
    section_id BIGINT COMMENT '来源章节ID',
    chunk_id BIGINT COMMENT '来源原文块ID',
    knowledge_type VARCHAR(80) NOT NULL COMMENT 'RESEARCH_TASK/METHOD/DATASET/EXPERIMENT_SETTING/METRIC/RESULT/CONTRIBUTION/LIMITATION等',
    subject_text VARCHAR(1000) COMMENT '知识主语',
    predicate_text VARCHAR(500) COMMENT '知识关系',
    object_value LONGTEXT NOT NULL COMMENT '知识内容或数值',
    value_unit VARCHAR(100) COMMENT '数值单位',
    applicable_condition TEXT COMMENT '适用条件',
    normalized_key VARCHAR(512) NOT NULL COMMENT '论文内去重键',
    conflict_group_key VARCHAR(512) COMMENT '同一问题不同取值的冲突分组键',
    has_conflict TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否存在冲突',
    source_type VARCHAR(50) NOT NULL COMMENT 'PROFILE/SECTION_SUMMARY/RAW_CHUNK/TABLE/FIGURE/FORMULA/ALGORITHM',
    source_id BIGINT COMMENT '来源记录ID',
    page_number INT COMMENT 'PDF页码',
    evidence_text LONGTEXT COMMENT '可回看的原始证据摘录',
    confidence_level VARCHAR(30) NOT NULL DEFAULT 'BRONZE' COMMENT 'GOLD/SILVER/BRONZE',
    verification_status VARCHAR(30) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO/CONFIRMED/CORRECTED/REJECTED',
    extraction_method VARCHAR(50) NOT NULL COMMENT 'PROFILE/SECTION_SUMMARY/LLM/DETERMINISTIC/USER',
    extraction_version VARCHAR(100) NOT NULL COMMENT '抽取策略版本',
    model_provider VARCHAR(100) COMMENT '抽取模型供应商',
    model_name VARCHAR(200) COMMENT '抽取模型名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_paper_knowledge_key (paper_id, normalized_key),
    INDEX idx_knowledge_paper_type (paper_id, knowledge_type),
    INDEX idx_knowledge_type_status (knowledge_type, verification_status),
    INDEX idx_knowledge_section (section_id),
    INDEX idx_knowledge_conflict (conflict_group_key),
    CONSTRAINT fk_knowledge_paper FOREIGN KEY (paper_id)
        REFERENCES paper_reference(id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_section FOREIGN KEY (section_id)
        REFERENCES paper_section(id) ON DELETE SET NULL,
    CONSTRAINT fk_knowledge_chunk FOREIGN KEY (chunk_id)
        REFERENCES paper_chunk(id) ON DELETE SET NULL
) COMMENT='带来源、可信等级和冲突信息的通用学术知识单元';

CREATE TABLE IF NOT EXISTS paper_knowledge_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '反馈ID',
    unit_id BIGINT COMMENT '知识单元ID；单元重建删除后仍保留反馈快照',
    paper_id BIGINT NOT NULL COMMENT '论文ID',
    action_type VARCHAR(30) NOT NULL COMMENT 'CONFIRM/CORRECT/REJECT',
    before_snapshot LONGTEXT COMMENT '修改前知识JSON快照',
    after_snapshot LONGTEXT COMMENT '修改后知识JSON快照',
    comment_text TEXT COMMENT '用户备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_knowledge_feedback_unit (unit_id),
    INDEX idx_knowledge_feedback_paper (paper_id),
    CONSTRAINT fk_knowledge_feedback_unit FOREIGN KEY (unit_id)
        REFERENCES paper_knowledge_unit(id) ON DELETE SET NULL,
    CONSTRAINT fk_knowledge_feedback_paper FOREIGN KEY (paper_id)
        REFERENCES paper_reference(id) ON DELETE CASCADE
) COMMENT='知识确认、纠正和拒绝记录，可作为后续评测与训练数据资产';
