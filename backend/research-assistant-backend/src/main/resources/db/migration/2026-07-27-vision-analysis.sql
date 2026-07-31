ALTER TABLE paper_asset
    ADD COLUMN analysis_version VARCHAR(200) NULL COMMENT '视觉模型与提示Schema版本';

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
