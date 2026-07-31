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
    CONSTRAINT fk_reproduction_spec_paper FOREIGN KEY (paper_id) REFERENCES paper_reference(id) ON DELETE CASCADE
) COMMENT='论文复现规格及准备度';
