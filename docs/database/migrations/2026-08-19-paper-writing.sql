-- 论文写作模式：项目与版本快照
CREATE TABLE IF NOT EXISTS writing_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '写作项目ID',
    title VARCHAR(500) NOT NULL COMMENT '项目标题',
    topic VARCHAR(1000) NOT NULL COMMENT '写作主题',
    document_type VARCHAR(50) NOT NULL DEFAULT 'CHAPTER_ONE' COMMENT 'INTRODUCTION/LITERATURE_REVIEW/RESEARCH_STATUS/CHAPTER_ONE',
    target_language VARCHAR(20) NOT NULL DEFAULT 'zh-CN' COMMENT '输出语言',
    target_word_count INT NOT NULL DEFAULT 2500 COMMENT '目标字数',
    citation_style VARCHAR(50) NOT NULL DEFAULT 'GB_T_7714' COMMENT '引用格式偏好',
    selected_paper_ids JSON NOT NULL COMMENT '后端验证过的论文ID数组',
    outline_json LONGTEXT COMMENT '结构化写作大纲',
    content LONGTEXT COMMENT 'Markdown正文',
    citations_json LONGTEXT COMMENT '正文实际引用及原文定位快照',
    citation_audit_json LONGTEXT COMMENT '程序引用核查结果',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/OUTLINE_READY/CONTENT_READY',
    model_provider VARCHAR(100) COMMENT '最近生成使用的模型供应商',
    model_name VARCHAR(200) COMMENT '最近生成使用的模型名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_writing_project_update_time (update_time),
    INDEX idx_writing_project_status (status)
) COMMENT='基于所选论文的第一章写作项目';

CREATE TABLE IF NOT EXISTS writing_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '版本ID',
    project_id BIGINT NOT NULL COMMENT '写作项目ID',
    revision_type VARCHAR(50) NOT NULL COMMENT '版本来源',
    instruction TEXT COMMENT '本次修改要求',
    outline_snapshot LONGTEXT COMMENT '大纲快照',
    content_snapshot LONGTEXT COMMENT '正文快照',
    citations_json LONGTEXT COMMENT '引用快照',
    model_provider VARCHAR(100) COMMENT '模型供应商',
    model_name VARCHAR(200) COMMENT '模型名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_writing_revision_project_time (project_id, create_time),
    CONSTRAINT fk_writing_revision_project FOREIGN KEY (project_id)
        REFERENCES writing_project(id) ON DELETE CASCADE
) COMMENT='论文写作内容版本快照';
