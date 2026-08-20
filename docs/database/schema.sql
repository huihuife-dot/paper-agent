-- MyAgent 数据库设计初稿
-- 数据库：MySQL 8
-- 说明：MySQL 保存业务数据和 chunk 原文；Qdrant 保存 embedding 向量和检索元数据。

CREATE DATABASE IF NOT EXISTS research_assistant
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE research_assistant;

-- 文献分类表
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

-- 默认分类：上传文献未选择分类时使用；删除普通分类后，文献也会转入这里
INSERT INTO paper_category (name, description, sort_order, system_flag)
SELECT '未分类', '系统默认分类，不能删除', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM paper_category WHERE name = '未分类');

-- 文献信息表
CREATE TABLE IF NOT EXISTS paper_reference (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文献ID',
    category_id BIGINT NOT NULL COMMENT '文献分类ID',
    title VARCHAR(500) COMMENT '文献标题',
    authors TEXT COMMENT '作者',
    publish_year INT COMMENT '发表年份',
    journal VARCHAR(500) COMMENT '期刊或会议',
    keywords TEXT COMMENT '关键词',
    abstract_text TEXT COMMENT '摘要',
    file_name VARCHAR(500) COMMENT '原始文件名',
    file_path TEXT COMMENT '本地文件相对路径',
    file_type VARCHAR(50) COMMENT '文件类型',
    file_size BIGINT COMMENT '文件大小，单位字节',
    upload_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    parse_status VARCHAR(50) DEFAULT 'PENDING' COMMENT '解析状态：PENDING/PROCESSING/COMPLETED/FAILED',
    vector_status VARCHAR(50) DEFAULT 'PENDING' COMMENT '向量化状态：PENDING/PROCESSING/COMPLETED/FAILED',
    remark TEXT COMMENT '备注',
    INDEX idx_paper_category_id (category_id),
    CONSTRAINT fk_paper_reference_category
        FOREIGN KEY (category_id) REFERENCES paper_category(id)
) COMMENT='文献信息表';

-- 论文章节表：保存论文的半结构化章节父级
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

-- 文献切片表：保存 chunk 原文，向量保存到 Qdrant
CREATE TABLE IF NOT EXISTS paper_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '切片ID',
    paper_id BIGINT NOT NULL COMMENT '文献ID',
    section_id BIGINT COMMENT '所属章节ID',
    section_title VARCHAR(500) COMMENT '章节标题冗余',
    section_type VARCHAR(50) DEFAULT 'UNKNOWN' COMMENT '标准章节类型',
    chunk_type VARCHAR(50) DEFAULT 'text' COMMENT '切片类型：text/table/figure_description/formula',
    content LONGTEXT NOT NULL COMMENT '切片原文内容',
    index_text LONGTEXT COMMENT '用于向量化检索的文本',
    page_number INT COMMENT '页码',
    page_start INT COMMENT '起始页',
    page_end INT COMMENT '结束页',
    chunk_index INT COMMENT '切片序号',
    token_count INT DEFAULT 0 COMMENT '估算token数',
    char_count INT DEFAULT 0 COMMENT '字符数',
    is_reference TINYINT(1) DEFAULT 0 COMMENT '是否参考文献片段',
    is_noise TINYINT(1) DEFAULT 0 COMMENT '是否噪声片段',
    quality_score DOUBLE DEFAULT 1.0 COMMENT '片段质量分',
    chunk_strategy_version VARCHAR(100) DEFAULT 'fixed-window-v1' COMMENT '分块策略版本',
    qdrant_point_id VARCHAR(100) COMMENT 'Qdrant 向量点ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_paper_id (paper_id),
    INDEX idx_paper_chunk_section_id (section_id),
    INDEX idx_paper_chunk_section_type (section_type),
    INDEX idx_paper_chunk_reference_noise (is_reference, is_noise),
    INDEX idx_qdrant_point_id (qdrant_point_id),
    CONSTRAINT fk_paper_chunk_paper
        FOREIGN KEY (paper_id) REFERENCES paper_reference(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_paper_chunk_section
        FOREIGN KEY (section_id) REFERENCES paper_section(id)
        ON DELETE SET NULL
) COMMENT='文献切片表';

-- 论文页面、图表、公式和算法资产。文件保存到受控本地目录，数据库只保存相对路径。
CREATE TABLE IF NOT EXISTS paper_asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    asset_label VARCHAR(255),
    caption LONGTEXT,
    page_start INT NOT NULL,
    page_end INT NOT NULL,
    bounding_box_json TEXT,
    raw_asset_path VARCHAR(1000) NOT NULL,
    raw_text LONGTEXT,
    structured_content_json LONGTEXT,
    semantic_description LONGTEXT,
    extraction_method VARCHAR(50) NOT NULL,
    extraction_confidence DOUBLE NOT NULL DEFAULT 1.0,
    verification_status VARCHAR(50) NOT NULL DEFAULT 'EXTRACTED',
    content_hash VARCHAR(64) NOT NULL,
    parser_version VARCHAR(100) NOT NULL,
    analysis_version VARCHAR(200),
    source_revision VARCHAR(64) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_paper_asset_revision (paper_id, page_start, asset_type, content_hash, parser_version),
    INDEX idx_paper_asset_paper (paper_id),
    INDEX idx_paper_asset_type (paper_id, asset_type),
    CONSTRAINT fk_paper_asset_paper FOREIGN KEY (paper_id) REFERENCES paper_reference(id) ON DELETE CASCADE
) COMMENT='论文多模态资产';

CREATE TABLE IF NOT EXISTS paper_multimodal_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_items INT NOT NULL DEFAULT 0,
    processed_items INT NOT NULL DEFAULT 0,
    failed_items INT NOT NULL DEFAULT 0,
    provider VARCHAR(100),
    model VARCHAR(200),
    error_message LONGTEXT,
    started_at DATETIME,
    finished_at DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_multimodal_job_paper (paper_id, create_time),
    CONSTRAINT fk_multimodal_job_paper FOREIGN KEY (paper_id) REFERENCES paper_reference(id) ON DELETE CASCADE
) COMMENT='论文多模态处理任务';

CREATE TABLE IF NOT EXISTS paper_reproduction_fact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    fact_type VARCHAR(80) NOT NULL,
    fact_key VARCHAR(500) NOT NULL,
    fact_value LONGTEXT NOT NULL,
    unit VARCHAR(100),
    conditions_json LONGTEXT,
    source_kind VARCHAR(50) NOT NULL,
    source_id BIGINT NOT NULL,
    page_number INT,
    evidence_excerpt LONGTEXT NOT NULL,
    confidence DECIMAL(6,5) NOT NULL,
    verification_status VARCHAR(50) NOT NULL,
    conflict_group VARCHAR(100),
    supporting_sources_json LONGTEXT,
    extractor_version VARCHAR(100) NOT NULL,
    source_revision VARCHAR(100),
    qdrant_point_id VARCHAR(100),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_reproduction_fact_paper (paper_id, verification_status),
    INDEX idx_reproduction_fact_source (source_kind, source_id),
    INDEX idx_reproduction_fact_conflict (paper_id, conflict_group),
    CONSTRAINT fk_reproduction_fact_paper FOREIGN KEY (paper_id) REFERENCES paper_reference(id) ON DELETE CASCADE
) COMMENT='可追溯的论文复现原子事实';

CREATE TABLE IF NOT EXISTS paper_reproduction_spec (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    spec_json LONGTEXT NOT NULL,
    source_revision VARCHAR(100) NOT NULL,
    spec_version VARCHAR(100) NOT NULL,
    critical_coverage DECIMAL(6,5) NOT NULL,
    provenance_coverage DECIMAL(6,5) NOT NULL,
    unresolved_conflict_count INT NOT NULL DEFAULT 0,
    missing_critical_count INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reproduction_spec_paper_version (paper_id, spec_version),
    INDEX idx_reproduction_spec_status (status),
    CONSTRAINT fk_reproduction_spec_paper FOREIGN KEY (paper_id)
        REFERENCES paper_reference(id) ON DELETE CASCADE
) COMMENT='论文复现规格及准备度';

-- 论文章节摘要表：保存每篇论文每个章节的可复用摘要资产
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

-- 文献画像表：保存整篇论文的研究问题、方法、贡献和面向 RAG 的画像文本
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

-- 文献画像异步生成任务表：记录画像生成进度，避免前端长请求超时
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

-- 对话会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
    title VARCHAR(500) COMMENT '会话标题',
    chat_type VARCHAR(50) DEFAULT 'normal' COMMENT '对话类型：normal/global_rag/selected_paper_rag',
    selected_paper_ids TEXT COMMENT '指定文献ID列表，JSON数组字符串',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='对话会话表';

-- 对话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(50) COMMENT '角色：user/assistant/system',
    content LONGTEXT COMMENT '消息内容',
    sources_json JSON COMMENT 'RAG 来源信息 JSON',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id) REFERENCES chat_session(id)
        ON DELETE CASCADE
) COMMENT='对话消息表';

-- 科研 Idea 表
CREATE TABLE IF NOT EXISTS research_idea (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Idea ID',
    title VARCHAR(500) COMMENT 'Idea 标题',
    original_content LONGTEXT COMMENT '原始内容',
    refined_content LONGTEXT COMMENT '大模型精简后的内容',
    innovation_points TEXT COMMENT '创新点',
    research_question TEXT COMMENT '研究问题',
    possible_method TEXT COMMENT '可能方法',
    tags VARCHAR(1000) COMMENT '标签，逗号分隔或 JSON 字符串',
    source_type VARCHAR(50) COMMENT '来源类型：chat/manual/rag',
    save_type VARCHAR(50) COMMENT '保存方式：manual/auto',
    source_session_id BIGINT COMMENT '来源会话ID',
    source_message_id BIGINT COMMENT '来源消息ID',
    related_paper_ids TEXT COMMENT '关联文献ID列表，JSON数组字符串',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_source_session_id (source_session_id),
    INDEX idx_source_message_id (source_message_id)
) COMMENT='科研Idea表';

-- 论文写作项目：保存用户选择的论文范围、大纲、正文和引用核查结果
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

-- 写作版本：每次保存、生成、AI修改和恢复都保留可回退快照
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

-- 论文结构化目录：保存高层主题标签、低成本候选发现文本和知识构建状态
CREATE TABLE IF NOT EXISTS paper_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_BUILT',
    research_domains JSON,
    research_tasks JSON,
    method_tags JSON,
    dataset_tags JSON,
    metric_tags JSON,
    catalog_text LONGTEXT,
    knowledge_count INT NOT NULL DEFAULT 0,
    verified_count INT NOT NULL DEFAULT 0,
    extraction_version VARCHAR(100),
    model_provider VARCHAR(100),
    model_name VARCHAR(200),
    error_message LONGTEXT,
    build_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_paper_catalog_paper (paper_id),
    INDEX idx_paper_catalog_status (status),
    CONSTRAINT fk_paper_catalog_paper FOREIGN KEY (paper_id)
        REFERENCES paper_reference(id) ON DELETE CASCADE
) COMMENT='论文结构化目录与知识索引状态';

-- 通用学术知识单元：保存研究任务、方法、数据、指标、结果、贡献和局限
CREATE TABLE IF NOT EXISTS paper_knowledge_unit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    section_id BIGINT,
    chunk_id BIGINT,
    knowledge_type VARCHAR(80) NOT NULL,
    subject_text VARCHAR(1000),
    predicate_text VARCHAR(500),
    object_value LONGTEXT NOT NULL,
    value_unit VARCHAR(100),
    applicable_condition TEXT,
    normalized_key VARCHAR(512) NOT NULL,
    conflict_group_key VARCHAR(512),
    has_conflict TINYINT(1) NOT NULL DEFAULT 0,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT,
    page_number INT,
    evidence_text LONGTEXT,
    confidence_level VARCHAR(30) NOT NULL DEFAULT 'BRONZE',
    verification_status VARCHAR(30) NOT NULL DEFAULT 'AUTO',
    extraction_method VARCHAR(50) NOT NULL,
    extraction_version VARCHAR(100) NOT NULL,
    model_provider VARCHAR(100),
    model_name VARCHAR(200),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
) COMMENT='可追溯学术知识单元';

-- 用户反馈：模型派生知识重建后仍保留确认、纠正或拒绝的历史快照
CREATE TABLE IF NOT EXISTS paper_knowledge_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    unit_id BIGINT,
    paper_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    before_snapshot LONGTEXT,
    after_snapshot LONGTEXT,
    comment_text TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_knowledge_feedback_unit (unit_id),
    INDEX idx_knowledge_feedback_paper (paper_id),
    CONSTRAINT fk_knowledge_feedback_unit FOREIGN KEY (unit_id)
        REFERENCES paper_knowledge_unit(id) ON DELETE SET NULL,
    CONSTRAINT fk_knowledge_feedback_paper FOREIGN KEY (paper_id)
        REFERENCES paper_reference(id) ON DELETE CASCADE
) COMMENT='知识确认、纠正和拒绝记录';
