# 论文 PDF 预处理与结构化分块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前固定长度 PDF chunk 切分升级为“文本清洗 + 章节识别 + 章节内分块 + 结构化元数据 + 可量化评测”的论文预处理链路。

**Architecture:** MySQL 继续作为事实主库，新增 `paper_section` 保存章节父级结构，增强 `paper_chunk` 保存 `sectionType/isReference/indexText/chunkStrategyVersion` 等字段。PDF 解析后先进入结构化分块服务，向量化时使用 `indexText` 生成 embedding，Qdrant payload 同步章节元数据；RAG 检索先扩大召回再在 MySQL 回查后过滤 `isReference/isNoise`，兼容旧向量数据。

**Tech Stack:** Spring Boot 3.5.14, Java 21, MyBatis-Plus 3.5.7, MySQL 8, PDFBox 3.0.3, Qdrant REST API, JUnit 5, Mockito, AssertJ。

## Global Constraints

- 每个实质阶段开始和完成后都要更新 `docs/status/current.md`，但只记录项目实质状态、关键验证和下一步。
- 第一阶段先处理新解析论文，不强制迁移旧论文；旧论文后续通过重新解析 / 重新向量化入口处理。
- MySQL 保存真实论文内容；Qdrant 只保存 embedding、业务 ID 和检索 payload。
- `paper_chunk.content` 保存真实原文；`paper_chunk.index_text` 用于 embedding 检索。
- 普通 RAG 默认排除 `is_reference = true` 和 `is_noise = true` 的 chunk。
- Qdrant 过滤需要兼容旧 points：不要因为旧 payload 缺少 `isReference/isNoise` 字段导致历史文献完全检索不到。
- 不引入 GROBID / MinerU / Nougat 等外部解析器；不实现复杂双栏重排、图表/公式理解、文献画像、章节摘要、claim cards、完整父子召回和直接 PDF LLM 阅读。
- 后端测试命令使用 JDK 21：`JAVA_HOME=/path/to/jdk-21 ./mvnw test`。
- 计划依据：`docs/archive/specs/2026-07-06-paper-preprocessing-structured-chunking-design.md`。

---

## File Structure

### Database / docs

- Modify: `docs/database/schema.sql`
  - 新增 `paper_section` 表。
  - 增强 `paper_chunk` 字段。
- Create: `docs/database/migrations/paper-structured-chunking.sql`
  - 现有数据库迁移脚本；为旧 chunk 补默认结构字段。
- Create: `docs/evaluation/cases.md`
  - 轻量 RAG 评测集和基线记录模板。
- Modify: `docs/status/current.md`
  - 每个实质阶段开始/完成时同步当前状态。

### Backend domain model

- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperSection.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/mapper/PaperSectionMapper.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperChunk.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/SectionType.java`

### Backend preprocessing and chunking

- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/DetectedSection.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/StructuredChunk.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperTextCleaner.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperSectionDetector.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/StructuredChunkingService.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperReferenceServiceImpl.java`

### Backend vectorization / RAG

- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/qdrant/service/QdrantService.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/dto/RagSource.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagRetrievalServiceImpl.java`

### Tests

- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperTextCleanerTest.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/StructuredChunkingServiceTest.java`
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImplTest.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/qdrant/service/QdrantServiceTest.java`
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/RagRetrievalServiceImplTest.java`

---

## Task 1: Database and Entity Foundation

**Files:**
- Modify: `docs/status/current.md`
- Modify: `docs/database/schema.sql`
- Create: `docs/database/migrations/paper-structured-chunking.sql`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperSection.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/mapper/PaperSectionMapper.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperChunk.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/SectionType.java`

**Interfaces:**
- Produces: `PaperSection` entity mapped to `paper_section`.
- Produces: `PaperSectionMapper extends BaseMapper<PaperSection>`.
- Produces: `SectionType` enum with `fromTitle(String title)`.
- Produces: enhanced `PaperChunk` fields used by parser, vectorization, retrieval, and source display.

- [ ] **Step 1: Record stage start in progress doc**

Append a concise line to `docs/status/current.md` under the current task status block:

```text
论文 PDF 预处理与结构化分块优化进入阶段 1：扩展数据库结构和后端实体，新增 paper_section 章节层，并增强 paper_chunk 的章节、参考文献、索引文本和分块策略字段。
```

- [ ] **Step 2: Update `docs/database/schema.sql` with `paper_section`**

Add this table between `paper_reference` and `paper_chunk`:

```sql
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
```

- [ ] **Step 3: Update `docs/database/schema.sql` `paper_chunk` columns**

Modify the `paper_chunk` definition so it contains these additional columns after `paper_id` and before `chunk_type`:

```sql
    section_id BIGINT COMMENT '所属章节ID',
    section_title VARCHAR(500) COMMENT '章节标题冗余',
    section_type VARCHAR(50) DEFAULT 'UNKNOWN' COMMENT '标准章节类型',
```

Add these columns after `content`:

```sql
    index_text LONGTEXT COMMENT '用于向量化检索的文本',
```

Add these columns after `page_number`:

```sql
    page_start INT COMMENT '起始页',
    page_end INT COMMENT '结束页',
```

Add these columns after `chunk_index`:

```sql
    token_count INT DEFAULT 0 COMMENT '估算token数',
    char_count INT DEFAULT 0 COMMENT '字符数',
    is_reference TINYINT(1) DEFAULT 0 COMMENT '是否参考文献片段',
    is_noise TINYINT(1) DEFAULT 0 COMMENT '是否噪声片段',
    quality_score DOUBLE DEFAULT 1.0 COMMENT '片段质量分',
    chunk_strategy_version VARCHAR(100) DEFAULT 'fixed-window-v1' COMMENT '分块策略版本',
```

Add indexes and FK:

```sql
    INDEX idx_paper_chunk_section_id (section_id),
    INDEX idx_paper_chunk_section_type (section_type),
    INDEX idx_paper_chunk_reference_noise (is_reference, is_noise),
    CONSTRAINT fk_paper_chunk_section
        FOREIGN KEY (section_id) REFERENCES paper_section(id)
        ON DELETE SET NULL,
```

Keep the existing `fk_paper_chunk_paper` constraint.

- [ ] **Step 4: Create migration script**

Create `docs/database/migrations/paper-structured-chunking.sql`:

```sql
-- 论文 PDF 预处理与结构化分块数据库迁移脚本
-- 适用场景：已有 research_assistant 数据库，不想重建全部表。
-- 执行方式：mysql --default-character-set=utf8mb4 -uroot -p123456 research_assistant < docs/database/migrations/paper-structured-chunking.sql

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
```

- [ ] **Step 5: Create `PaperSection` entity**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperSection.java`:

```java
package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论文章节实体。
 *
 * 作为 paper_chunk 的父级结构，用于保存论文中的摘要、方法、实验、结论等章节信息。
 */
@Data
@TableName("paper_section")
public class PaperSection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;
    private String sectionTitle;
    private String sectionType;
    private Integer sectionIndex;
    private Integer pageStart;
    private Integer pageEnd;
    private String contentPreview;
    private LocalDateTime createTime;
}
```

- [ ] **Step 6: Create `PaperSectionMapper`**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/mapper/PaperSectionMapper.java`:

```java
package com.myagent.assistant.paper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myagent.assistant.paper.entity.PaperSection;

/**
 * 论文章节 Mapper。
 */
public interface PaperSectionMapper extends BaseMapper<PaperSection> {
}
```

- [ ] **Step 7: Create `SectionType` enum**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/SectionType.java`:

```java
package com.myagent.assistant.paper.structure;

import java.util.Locale;

/**
 * 论文标准章节类型。
 */
public enum SectionType {
    TITLE,
    ABSTRACT,
    INTRODUCTION,
    RELATED_WORK,
    METHOD,
    EXPERIMENT,
    RESULT,
    DISCUSSION,
    CONCLUSION,
    REFERENCES,
    APPENDIX,
    BACK_MATTER,
    UNKNOWN;

    public static SectionType fromTitle(String title) {
        if (title == null || title.isBlank()) {
            return UNKNOWN;
        }

        String normalized = title.toLowerCase(Locale.ROOT)
                .replaceAll("^[\\s\\d.ivxlcdm]+[.)、:-]*\\s*", "")
                .trim();

        if (normalized.matches("^(abstract|摘要)$")) {
            return ABSTRACT;
        }
        if (normalized.matches("^(introduction|引言|绪论)$")) {
            return INTRODUCTION;
        }
        if (normalized.contains("related work")
                || normalized.contains("literature review")
                || normalized.contains("background")) {
            return RELATED_WORK;
        }
        if (normalized.contains("method")
                || normalized.contains("methodology")
                || normalized.contains("approach")
                || normalized.contains("framework")
                || normalized.contains("proposed")) {
            return METHOD;
        }
        if (normalized.contains("experiment")
                || normalized.contains("evaluation")
                || normalized.contains("implementation details")) {
            return EXPERIMENT;
        }
        if (normalized.matches(".*\\bresults?\\b.*")
                || normalized.contains("analysis")) {
            return RESULT;
        }
        if (normalized.contains("discussion")) {
            return DISCUSSION;
        }
        if (normalized.contains("conclusion")
                || normalized.contains("future work")) {
            return CONCLUSION;
        }
        if (normalized.matches("^(references|bibliography|参考文献|参考资料|参考书目)$")) {
            return REFERENCES;
        }
        if (normalized.contains("appendix")
                || normalized.contains("supplementary")) {
            return APPENDIX;
        }

        return UNKNOWN;
    }
}
```

- [ ] **Step 8: Enhance `PaperChunk` entity**

Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperChunk.java` and add fields:

```java
    /**
     * 所属章节 ID，对应 paper_section.id。
     */
    private Long sectionId;

    /**
     * 章节标题冗余字段，便于 sources 展示和调试。
     */
    private String sectionTitle;

    /**
     * 标准章节类型，例如 METHOD、EXPERIMENT、REFERENCES。
     */
    private String sectionType;
```

Add after `content`:

```java
    /**
     * 用于向量化检索的文本。
     * 可以包含论文标题、章节类型、章节标题和清洗后的 chunk 内容。
     */
    private String indexText;
```

Add after `pageNumber`:

```java
    private Integer pageStart;
    private Integer pageEnd;
```

Add after `chunkIndex`:

```java
    private Integer tokenCount;
    private Integer charCount;
    private Boolean isReference;
    private Boolean isNoise;
    private Double qualityScore;
    private String chunkStrategyVersion;
```

- [ ] **Step 9: Run compile check for entity/mapper changes**

Run from `backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile
```

Expected: build success.

- [ ] **Step 10: Record stage completion**

Update `docs/status/current.md` with:

```text
论文 PDF 预处理与结构化分块优化阶段 1 已完成：新增 paper_section 数据模型和 Mapper，增强 paper_chunk 章节、参考文献、索引文本和分块策略字段，数据库初始化脚本和迁移脚本已补充；后端编译通过。
```

---

## Task 2: Text Cleaning and Section Detection

**Files:**
- Modify: `docs/status/current.md`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/DetectedSection.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperTextCleaner.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperSectionDetector.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperTextCleanerTest.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`

**Interfaces:**
- Consumes: `SectionType.fromTitle(String title)` from Task 1.
- Produces: `PaperTextCleaner.clean(String rawText): String`.
- Produces: `PaperSectionDetector.detect(String cleanedText): List<DetectedSection>`.
- Produces: `DetectedSection` with `title`, `type`, `content`, `sectionIndex`, `reference`.

- [ ] **Step 1: Record stage start**

Add to `docs/status/current.md`:

```text
论文 PDF 预处理与结构化分块优化进入阶段 2：实现 PDF 文本清洗、章节标题识别和 References 区域标记，为后续按章节分块做准备。
```

- [ ] **Step 2: Write `PaperTextCleanerTest`**

Create `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperTextCleanerTest.java`:

```java
package com.myagent.assistant.paper.structure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaperTextCleanerTest {

    @Test
    void cleanNormalizesLineBreaksAndBlankLines() {
        PaperTextCleaner cleaner = new PaperTextCleaner();

        String cleaned = cleaner.clean("Abstract\r\n\r\n\r\nThis   is   a paper.\r\n\r\nMethod");

        assertThat(cleaned).isEqualTo("Abstract\n\nThis is a paper.\n\nMethod");
    }

    @Test
    void cleanRepairsEnglishHyphenLineBreaks() {
        PaperTextCleaner cleaner = new PaperTextCleaner();

        String cleaned = cleaner.clean("wind predic-\ntion model");

        assertThat(cleaned).isEqualTo("wind prediction model");
    }

    @Test
    void cleanRemovesStandalonePageNumbers() {
        PaperTextCleaner cleaner = new PaperTextCleaner();

        String cleaned = cleaner.clean("Introduction\n1\nThis paper studies wind prediction.\n23\nConclusion");

        assertThat(cleaned).doesNotContain("\n1\n");
        assertThat(cleaned).doesNotContain("\n23\n");
        assertThat(cleaned).contains("Introduction");
        assertThat(cleaned).contains("Conclusion");
    }
}
```

- [ ] **Step 3: Run cleaner tests and verify they fail**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperTextCleanerTest test
```

Expected: fail because `PaperTextCleaner` does not exist.

- [ ] **Step 4: Implement `PaperTextCleaner`**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperTextCleaner.java`:

```java
package com.myagent.assistant.paper.structure;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * PDF 文本清洗器。
 *
 * 第一阶段只做轻量清洗：换行规整、断词修复、页码行过滤和多余空白压缩。
 */
@Component
public class PaperTextCleaner {

    public String clean(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String text = rawText.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("([A-Za-z])-\\n([A-Za-z])", "$1$2");

        text = Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(line -> !line.matches("^\\d{1,4}$"))
                .map(line -> line.replaceAll("[ \\t]+", " "))
                .collect(Collectors.joining("\n"));

        return text.replaceAll("\\n{3,}", "\n\n").trim();
    }
}
```

- [ ] **Step 5: Re-run cleaner tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperTextCleanerTest test
```

Expected: 3 tests pass.

- [ ] **Step 6: Create `DetectedSection`**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/DetectedSection.java`:

```java
package com.myagent.assistant.paper.structure;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 章节识别结果。
 */
@Data
@AllArgsConstructor
public class DetectedSection {
    private String title;
    private SectionType type;
    private String content;
    private Integer sectionIndex;

    public boolean isReference() {
        return SectionType.REFERENCES.equals(type);
    }
}
```

- [ ] **Step 7: Write `PaperSectionDetectorTest`**

Create `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`:

```java
package com.myagent.assistant.paper.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaperSectionDetectorTest {

    @Test
    void detectRecognizesCommonPaperSections() {
        PaperSectionDetector detector = new PaperSectionDetector();

        List<DetectedSection> sections = detector.detect("""
                Abstract
                This paper proposes a model.

                1 Introduction
                Wind prediction is important.

                2 Proposed Method
                We propose a dual branch network.

                3 Experiments
                We evaluate on several datasets.

                References
                [1] A referenced paper.
                """);

        assertThat(sections).extracting(DetectedSection::getType)
                .containsExactly(
                        SectionType.ABSTRACT,
                        SectionType.INTRODUCTION,
                        SectionType.METHOD,
                        SectionType.EXPERIMENT,
                        SectionType.REFERENCES
                );
    }

    @Test
    void detectUsesUnknownWhenNoHeadingExists() {
        PaperSectionDetector detector = new PaperSectionDetector();

        List<DetectedSection> sections = detector.detect("This is a plain parsed PDF text without headings.");

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).getType()).isEqualTo(SectionType.UNKNOWN);
        assertThat(sections.get(0).getTitle()).isEqualTo("Unknown");
    }

    @Test
    void detectMarksEverythingAfterReferencesAsReferences() {
        PaperSectionDetector detector = new PaperSectionDetector();

        List<DetectedSection> sections = detector.detect("""
                Method
                Model content.

                References
                [1] First reference.
                [2] Second reference.
                """);

        DetectedSection references = sections.get(1);
        assertThat(references.getType()).isEqualTo(SectionType.REFERENCES);
        assertThat(references.isReference()).isTrue();
        assertThat(references.getContent()).contains("[1] First reference");
    }
}
```

- [ ] **Step 8: Run detector tests and verify they fail**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest test
```

Expected: fail because `PaperSectionDetector` does not exist.

- [ ] **Step 9: Implement `PaperSectionDetector`**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperSectionDetector.java`:

```java
package com.myagent.assistant.paper.structure;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于规则的论文章节识别器。
 */
@Component
public class PaperSectionDetector {

    public List<DetectedSection> detect(String cleanedText) {
        if (cleanedText == null || cleanedText.isBlank()) {
            return List.of();
        }

        List<DetectedSection> sections = new ArrayList<>();
        String currentTitle = "Unknown";
        SectionType currentType = SectionType.UNKNOWN;
        StringBuilder currentContent = new StringBuilder();
        boolean seenHeading = false;
        boolean inReferences = false;

        for (String rawLine : cleanedText.split("\n")) {
            String line = rawLine.trim();

            if (line.isBlank()) {
                appendLine(currentContent, "");
                continue;
            }

            SectionType headingType = detectHeadingType(line);
            boolean isHeading = headingType != SectionType.UNKNOWN;

            if (isHeading && !inReferences) {
                if (seenHeading || !currentContent.toString().isBlank()) {
                    addSection(sections, currentTitle, currentType, currentContent.toString());
                    currentContent.setLength(0);
                }

                currentTitle = normalizeHeadingTitle(line);
                currentType = headingType;
                seenHeading = true;
                inReferences = SectionType.REFERENCES.equals(headingType);
                continue;
            }

            appendLine(currentContent, line);
        }

        if (!currentContent.toString().isBlank() || sections.isEmpty()) {
            addSection(sections, currentTitle, currentType, currentContent.toString());
        }

        return sections;
    }

    private SectionType detectHeadingType(String line) {
        if (!looksLikeHeading(line)) {
            return SectionType.UNKNOWN;
        }
        return SectionType.fromTitle(line);
    }

    private boolean looksLikeHeading(String line) {
        if (line.length() > 120) {
            return false;
        }
        if (line.endsWith(".")) {
            return false;
        }
        SectionType type = SectionType.fromTitle(line);
        return type != SectionType.UNKNOWN;
    }

    private String normalizeHeadingTitle(String line) {
        return line.replaceAll("^[\\s\\d.ivxlcdmIVXLCDM]+[.)、:-]*\\s*", "").trim();
    }

    private void appendLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append("\n");
        }
        builder.append(line);
    }

    private void addSection(List<DetectedSection> sections, String title, SectionType type, String content) {
        sections.add(new DetectedSection(
                title,
                type,
                content == null ? "" : content.trim(),
                sections.size()
        ));
    }
}
```

- [ ] **Step 10: Run text structure tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperTextCleanerTest,PaperSectionDetectorTest test
```

Expected: all tests pass.

- [ ] **Step 11: Record stage completion**

Update `docs/status/current.md`:

```text
论文 PDF 预处理与结构化分块优化阶段 2 已完成：新增 PDF 文本清洗器和规则化章节识别器，可识别 Abstract、Introduction、Method、Experiment、References 等章节，并能将 References 区域标记为参考文献；相关单元测试通过。
```

---

## Task 3: Structured Chunking and Parse Integration

**Files:**
- Modify: `docs/status/current.md`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/StructuredChunk.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/StructuredChunkingService.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/StructuredChunkingServiceTest.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperReferenceServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImplTest.java`

**Interfaces:**
- Consumes: `PaperTextCleaner.clean(String)` and `PaperSectionDetector.detect(String)` from Task 2.
- Produces: `StructuredChunkingService.chunk(String paperTitle, String rawText): List<StructuredChunk>`.
- Updates: `PaperReferenceServiceImpl.parsePaper(Long id)` writes `paper_section` and enhanced `paper_chunk` records.

- [ ] **Step 1: Record stage start**

Update `docs/status/current.md`:

```text
论文 PDF 预处理与结构化分块优化进入阶段 3：实现章节内结构化分块，并把文献解析流程从固定长度切片切换为 paper_section + paper_chunk 入库。
```

- [ ] **Step 2: Create `StructuredChunk`**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/StructuredChunk.java`:

```java
package com.myagent.assistant.paper.structure;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 结构化 chunk 结果。
 */
@Data
@AllArgsConstructor
public class StructuredChunk {
    private String sectionTitle;
    private SectionType sectionType;
    private Integer sectionIndex;
    private String content;
    private String indexText;
    private Integer chunkIndex;
    private Integer tokenCount;
    private Integer charCount;
    private Boolean reference;
    private Boolean noise;
    private Double qualityScore;
    private String chunkStrategyVersion;
}
```

- [ ] **Step 3: Write `StructuredChunkingServiceTest`**

Create `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/StructuredChunkingServiceTest.java`:

```java
package com.myagent.assistant.paper.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredChunkingServiceTest {

    @Test
    void chunkCreatesStructuredChunksBySection() {
        StructuredChunkingService service = new StructuredChunkingService(
                new PaperTextCleaner(),
                new PaperSectionDetector()
        );

        List<StructuredChunk> chunks = service.chunk("Wind Prediction Paper", """
                Abstract
                This paper studies wind prediction.

                Proposed Method
                We propose a dual branch neural network for wind prediction. The model captures temporal features.

                Experiments
                The method is evaluated on three datasets and outperforms baselines.
                """);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).extracting(StructuredChunk::getSectionType)
                .contains(SectionType.ABSTRACT, SectionType.METHOD, SectionType.EXPERIMENT);
        assertThat(chunks.get(0).getIndexText()).contains("Paper Title: Wind Prediction Paper");
        assertThat(chunks.get(0).getChunkStrategyVersion()).isEqualTo("paper-structure-v1");
    }

    @Test
    void chunkMarksReferenceChunks() {
        StructuredChunkingService service = new StructuredChunkingService(
                new PaperTextCleaner(),
                new PaperSectionDetector()
        );

        List<StructuredChunk> chunks = service.chunk("Paper", """
                Method
                Model content.

                References
                [1] Some referenced paper.
                """);

        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.getSectionType()).isEqualTo(SectionType.REFERENCES);
            assertThat(chunk.getReference()).isTrue();
        });
    }

    @Test
    void chunkSplitsLongSectionIntoMultipleChunks() {
        StructuredChunkingService service = new StructuredChunkingService(
                new PaperTextCleaner(),
                new PaperSectionDetector()
        );

        String repeated = "This method paragraph explains the proposed neural architecture and experimental design. ".repeat(80);
        List<StructuredChunk> chunks = service.chunk("Long Paper", "Method\n" + repeated);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getCharCount()).isLessThanOrEqualTo(2400));
    }
}
```

- [ ] **Step 4: Run structured chunking tests and verify they fail**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=StructuredChunkingServiceTest test
```

Expected: fail because `StructuredChunkingService` does not exist.

- [ ] **Step 5: Implement `StructuredChunkingService`**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/StructuredChunkingService.java`:

```java
package com.myagent.assistant.paper.structure;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化分块服务。
 *
 * 先按章节识别，再在章节内按段落和长度生成 chunk。
 */
@Service
public class StructuredChunkingService {

    public static final String STRATEGY_VERSION = "paper-structure-v1";
    private static final int TARGET_CHARS = 1600;
    private static final int MAX_CHARS = 2200;
    private static final int OVERLAP_CHARS = 200;

    private final PaperTextCleaner textCleaner;
    private final PaperSectionDetector sectionDetector;

    public StructuredChunkingService(PaperTextCleaner textCleaner,
                                     PaperSectionDetector sectionDetector) {
        this.textCleaner = textCleaner;
        this.sectionDetector = sectionDetector;
    }

    public List<StructuredChunk> chunk(String paperTitle, String rawText) {
        String cleanedText = textCleaner.clean(rawText);
        List<DetectedSection> sections = sectionDetector.detect(cleanedText);
        List<StructuredChunk> chunks = new ArrayList<>();

        for (DetectedSection section : sections) {
            List<String> sectionChunks = splitSectionContent(section.getContent());

            for (String content : sectionChunks) {
                if (content.isBlank()) {
                    continue;
                }

                chunks.add(new StructuredChunk(
                        section.getTitle(),
                        section.getType(),
                        section.getSectionIndex(),
                        content,
                        buildIndexText(paperTitle, section, content),
                        chunks.size(),
                        estimateTokens(content),
                        content.length(),
                        section.isReference(),
                        isNoise(content),
                        qualityScore(content),
                        STRATEGY_VERSION
                ));
            }
        }

        return chunks;
    }

    private List<String> splitSectionContent(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : content.split("\\n\\s*\\n")) {
            String normalized = paragraph.replaceAll("\\s+", " ").trim();
            if (normalized.isBlank()) {
                continue;
            }

            if (current.length() + normalized.length() + 2 <= TARGET_CHARS) {
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
                current.append(normalized);
                continue;
            }

            if (!current.isEmpty()) {
                addWithLongSplit(result, current.toString());
                current.setLength(0);
            }

            addWithLongSplit(result, normalized);
        }

        if (!current.isEmpty()) {
            addWithLongSplit(result, current.toString());
        }

        return result;
    }

    private void addWithLongSplit(List<String> result, String text) {
        if (text.length() <= MAX_CHARS) {
            result.add(text.trim());
            return;
        }

        int step = MAX_CHARS - OVERLAP_CHARS;
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + MAX_CHARS, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                result.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
        }
    }

    private String buildIndexText(String paperTitle, DetectedSection section, String content) {
        return "Paper Title: " + safe(paperTitle) + "\n"
                + "Section Type: " + section.getType().name() + "\n"
                + "Section Title: " + safe(section.getTitle()) + "\n"
                + "Content:\n" + content;
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private boolean isNoise(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        String normalized = content.trim();
        return normalized.length() < 30;
    }

    private double qualityScore(String content) {
        return isNoise(content) ? 0.2 : 1.0;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
```

- [ ] **Step 6: Run structure service tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=StructuredChunkingServiceTest test
```

Expected: all tests pass.

- [ ] **Step 7: Update `PaperReferenceServiceImpl` constructor dependencies**

Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperReferenceServiceImpl.java` imports:

```java
import com.myagent.assistant.paper.entity.PaperSection;
import com.myagent.assistant.paper.mapper.PaperSectionMapper;
import com.myagent.assistant.paper.structure.StructuredChunk;
import com.myagent.assistant.paper.structure.StructuredChunkingService;
import org.springframework.transaction.annotation.Transactional;
```

Add field:

```java
    private final PaperSectionMapper paperSectionMapper;
    private final StructuredChunkingService structuredChunkingService;
```

Update constructor signature:

```java
    public PaperReferenceServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                     PaperChunkMapper paperChunkMapper,
                                     PdfParseService pdfParseService,
                                     QdrantService qdrantService,
                                     PaperCategoryService paperCategoryService,
                                     PaperSectionMapper paperSectionMapper,
                                     StructuredChunkingService structuredChunkingService) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperChunkMapper = paperChunkMapper;
        this.pdfParseService = pdfParseService;
        this.qdrantService = qdrantService;
        this.paperCategoryService = paperCategoryService;
        this.paperSectionMapper = paperSectionMapper;
        this.structuredChunkingService = structuredChunkingService;
    }
```

- [ ] **Step 8: Replace fixed-window parsing with structured parsing**

In `parsePaper(Long id)`, replace the current text cleaning and fixed loop with this logic:

```java
            // 4. 为避免重复解析导致 section/chunk 重复，先删除旧 chunk，再删除旧 section。
            paperChunkMapper.delete(
                    new QueryWrapper<PaperChunk>().eq("paper_id", id)
            );
            paperSectionMapper.delete(
                    new QueryWrapper<PaperSection>().eq("paper_id", id)
            );

            // 5. 将 PDF 文本转换为带章节语义的结构化 chunk。
            List<StructuredChunk> structuredChunks = structuredChunkingService.chunk(paper.getTitle(), text);

            if (structuredChunks.isEmpty()) {
                throw new RuntimeException("未能从 PDF 中生成有效 chunk");
            }

            Map<Integer, PaperSection> sectionsByIndex = new java.util.LinkedHashMap<>();

            for (StructuredChunk structuredChunk : structuredChunks) {
                PaperSection section = sectionsByIndex.get(structuredChunk.getSectionIndex());

                if (section == null) {
                    section = new PaperSection();
                    section.setPaperId(id);
                    section.setSectionTitle(structuredChunk.getSectionTitle());
                    section.setSectionType(structuredChunk.getSectionType().name());
                    section.setSectionIndex(structuredChunk.getSectionIndex());
                    section.setContentPreview(previewText(structuredChunk.getContent(), 500));
                    paperSectionMapper.insert(section);
                    sectionsByIndex.put(structuredChunk.getSectionIndex(), section);
                }

                PaperChunk chunk = new PaperChunk();
                chunk.setPaperId(id);
                chunk.setSectionId(section.getId());
                chunk.setSectionTitle(section.getSectionTitle());
                chunk.setSectionType(section.getSectionType());
                chunk.setChunkType("text");
                chunk.setContent(structuredChunk.getContent());
                chunk.setIndexText(structuredChunk.getIndexText());
                chunk.setPageNumber(null);
                chunk.setPageStart(null);
                chunk.setPageEnd(null);
                chunk.setChunkIndex(structuredChunk.getChunkIndex());
                chunk.setTokenCount(structuredChunk.getTokenCount());
                chunk.setCharCount(structuredChunk.getCharCount());
                chunk.setIsReference(structuredChunk.getReference());
                chunk.setIsNoise(structuredChunk.getNoise());
                chunk.setQualityScore(structuredChunk.getQualityScore());
                chunk.setChunkStrategyVersion(structuredChunk.getChunkStrategyVersion());
                chunk.setQdrantPointId(null);

                paperChunkMapper.insert(chunk);
            }

            int chunkIndex = structuredChunks.size();
```

Add helper method near the bottom of the class:

```java
    private String previewText(String content, int maxLength) {
        if (content == null) {
            return "";
        }

        String text = content.replaceAll("\\s+", " ").trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
```

- [ ] **Step 9: Update `PaperReferenceServiceImplTest` constructor helper**

Modify `createService` helper to pass new mocks:

```java
    private PaperReferenceServiceImpl createService(PaperReferenceMapper paperReferenceMapper,
                                                    PaperCategoryService paperCategoryService) {
        PaperReferenceServiceImpl service = new PaperReferenceServiceImpl(
                paperReferenceMapper,
                mock(PaperChunkMapper.class),
                mock(PdfParseService.class),
                mock(QdrantService.class),
                paperCategoryService,
                mock(PaperSectionMapper.class),
                mock(StructuredChunkingService.class)
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        return service;
    }
```

Add imports:

```java
import com.myagent.assistant.paper.mapper.PaperSectionMapper;
import com.myagent.assistant.paper.structure.StructuredChunkingService;
```

- [ ] **Step 10: Add parse integration test**

Add a test to `PaperReferenceServiceImplTest`:

```java
    @Test
    void parsePaperWritesStructuredSectionsAndChunks() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PdfParseService pdfParseService = mock(PdfParseService.class);
        QdrantService qdrantService = mock(QdrantService.class);
        PaperCategoryService paperCategoryService = mock(PaperCategoryService.class);
        StructuredChunkingService structuredChunkingService = mock(StructuredChunkingService.class);

        PaperReference paper = new PaperReference();
        paper.setId(10L);
        paper.setTitle("Wind Paper");
        paper.setFilePath(tempDir.resolve("paper.pdf").toString());
        when(paperReferenceMapper.selectById(10L)).thenReturn(paper);
        when(pdfParseService.parseText(paper.getFilePath())).thenReturn("Method\ncontent");

        StructuredChunk structuredChunk = new StructuredChunk(
                "Method",
                SectionType.METHOD,
                0,
                "method content",
                "Section Type: METHOD\nContent:\nmethod content",
                0,
                4,
                14,
                false,
                false,
                1.0,
                "paper-structure-v1"
        );
        when(structuredChunkingService.chunk("Wind Paper", "Method\ncontent"))
                .thenReturn(List.of(structuredChunk));

        PaperReferenceServiceImpl service = new PaperReferenceServiceImpl(
                paperReferenceMapper,
                paperChunkMapper,
                pdfParseService,
                qdrantService,
                paperCategoryService,
                paperSectionMapper,
                structuredChunkingService
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        int count = service.parsePaper(10L);

        assertThat(count).isEqualTo(1);
        verify(paperSectionMapper).insert(any(PaperSection.class));
        verify(paperChunkMapper).insert(any(PaperChunk.class));
    }
```

Add imports:

```java
import com.myagent.assistant.paper.entity.PaperSection;
import com.myagent.assistant.paper.service.PdfParseService;
import com.myagent.assistant.paper.structure.SectionType;
import com.myagent.assistant.paper.structure.StructuredChunk;
import java.util.List;
```

- [ ] **Step 11: Run parse and chunking tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=StructuredChunkingServiceTest,PaperReferenceServiceImplTest test
```

Expected: all tests pass.

- [ ] **Step 12: Record stage completion**

Update `docs/status/current.md`:

```text
论文 PDF 预处理与结构化分块优化阶段 3 已完成：文献解析流程已由固定长度切片升级为结构化分块，解析时会生成 paper_section 章节记录，并写入带 sectionType、isReference、indexText、tokenCount 和 chunkStrategyVersion 的 paper_chunk；相关单元测试通过。
```

---

## Task 4: Vectorization Payload and RAG Filtering

**Files:**
- Modify: `docs/status/current.md`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/qdrant/service/QdrantService.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/qdrant/service/QdrantServiceTest.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/dto/RagSource.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/RagRetrievalServiceImplTest.java`

**Interfaces:**
- Consumes: enhanced `PaperChunk.indexText`, `sectionId`, `sectionType`, `isReference`, `isNoise`, `chunkStrategyVersion`.
- Updates: Qdrant point vector uses `indexText` when available, falling back to `content`.
- Updates: Qdrant payload includes structured metadata.
- Updates: RAG filters `isReference/isNoise` after MySQL lookup to remain compatible with old Qdrant payloads.
- Produces: `RagSource` exposes section metadata.

- [ ] **Step 1: Record stage start**

Update `docs/status/current.md`:

```text
论文 PDF 预处理与结构化分块优化进入阶段 4：向量化改用 indexText，Qdrant payload 写入章节元数据，RAG 检索默认过滤参考文献和噪声 chunk。
```

- [ ] **Step 2: Refactor Qdrant point construction into package-visible method**

Modify `QdrantService` and add method near `upsertPaperChunks`:

```java
    Map<String, Object> buildPoint(Long paperId, PaperChunk chunk) {
        String embeddingText = embeddingText(chunk);

        return Map.of(
                "id", chunk.getId(),
                "vector", embeddingService.embed(embeddingText),
                "payload", buildPayload(paperId, chunk)
        );
    }

    private Map<String, Object> buildPayload(Long paperId, PaperChunk chunk) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("paperId", paperId);
        payload.put("chunkId", chunk.getId());
        payload.put("sectionId", chunk.getSectionId());
        payload.put("sectionType", defaultString(chunk.getSectionType(), "UNKNOWN"));
        payload.put("sectionTitle", defaultString(chunk.getSectionTitle(), ""));
        payload.put("chunkIndex", chunk.getChunkIndex());
        payload.put("isReference", Boolean.TRUE.equals(chunk.getIsReference()));
        payload.put("isNoise", Boolean.TRUE.equals(chunk.getIsNoise()));
        payload.put("contentType", "RAW_CHUNK");
        payload.put("chunkStrategyVersion", defaultString(chunk.getChunkStrategyVersion(), "fixed-window-v1"));
        payload.put("text", previewText(chunk.getContent()));
        return payload;
    }

    private String embeddingText(PaperChunk chunk) {
        if (chunk.getIndexText() != null && !chunk.getIndexText().isBlank()) {
            return chunk.getIndexText();
        }
        return chunk.getContent() != null ? chunk.getContent() : "";
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
```

Then change `upsertPaperChunks` points construction to:

```java
            List<Map<String, Object>> points = chunks.stream()
                    .map(chunk -> buildPoint(paperId, chunk))
                    .toList();
```

- [ ] **Step 3: Write `QdrantServiceTest`**

Create `backend/research-assistant-backend/src/test/java/com/myagent/assistant/qdrant/service/QdrantServiceTest.java`:

```java
package com.myagent.assistant.qdrant.service;

import com.myagent.assistant.embedding.EmbeddingService;
import com.myagent.assistant.paper.entity.PaperChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QdrantServiceTest {

    @Test
    void buildPointUsesIndexTextAndStructuredPayload() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embed("index text for retrieval")).thenReturn(List.of(0.1, 0.2, 0.3));

        QdrantService service = new QdrantService(embeddingService);

        PaperChunk chunk = new PaperChunk();
        chunk.setId(100L);
        chunk.setSectionId(7L);
        chunk.setSectionType("METHOD");
        chunk.setSectionTitle("Proposed Method");
        chunk.setChunkIndex(2);
        chunk.setContent("real chunk content");
        chunk.setIndexText("index text for retrieval");
        chunk.setIsReference(false);
        chunk.setIsNoise(false);
        chunk.setChunkStrategyVersion("paper-structure-v1");

        Map<String, Object> point = service.buildPoint(3L, chunk);
        Map<String, Object> payload = (Map<String, Object>) point.get("payload");

        assertThat(point.get("vector")).isEqualTo(List.of(0.1, 0.2, 0.3));
        assertThat(payload.get("paperId")).isEqualTo(3L);
        assertThat(payload.get("chunkId")).isEqualTo(100L);
        assertThat(payload.get("sectionId")).isEqualTo(7L);
        assertThat(payload.get("sectionType")).isEqualTo("METHOD");
        assertThat(payload.get("isReference")).isEqualTo(false);
        assertThat(payload.get("contentType")).isEqualTo("RAW_CHUNK");
        assertThat(payload.get("chunkStrategyVersion")).isEqualTo("paper-structure-v1");
    }
}
```

- [ ] **Step 4: Run Qdrant unit test**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=QdrantServiceTest test
```

Expected: test passes.

- [ ] **Step 5: Enhance `RagSource`**

Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/dto/RagSource.java` and add fields after `chunkIndex`:

```java
    /**
     * 所属章节 ID。
     */
    private Long sectionId;

    /**
     * 章节标题。
     */
    private String sectionTitle;

    /**
     * 标准章节类型。
     */
    private String sectionType;

    /**
     * 是否参考文献片段。
     */
    private Boolean isReference;

    /**
     * 是否噪声片段。
     */
    private Boolean isNoise;

    /**
     * 分块策略版本。
     */
    private String chunkStrategyVersion;
```

- [ ] **Step 6: Update RAG retrieval to over-fetch and filter references/noise**

Modify `retrieveSources` in `RagRetrievalServiceImpl`:

```java
            int limit = topK != null && topK > 0 ? topK : 5;
            int retrievalLimit = Math.min(limit * 3, 30);
            List<Long> scopedPaperIds = normalizePaperIds(paperIds);

            List<RagSource> originalSources = searchSources(question, retrievalLimit, "original", scopedPaperIds);
```

Also use `retrievalLimit` for rewritten search:

```java
                rewrittenSources = searchSources(retrievalQuestion, retrievalLimit, "rewritten", scopedPaperIds);
```

Keep final `.limit(limit)`.

In `searchSources`, after selecting `PaperChunk chunk`, skip reference/noise:

```java
            if (Boolean.TRUE.equals(chunk.getIsReference()) || Boolean.TRUE.equals(chunk.getIsNoise())) {
                continue;
            }
```

Set new source fields:

```java
            source.setSectionId(chunk.getSectionId());
            source.setSectionTitle(chunk.getSectionTitle());
            source.setSectionType(chunk.getSectionType());
            source.setIsReference(chunk.getIsReference());
            source.setIsNoise(chunk.getIsNoise());
            source.setChunkStrategyVersion(chunk.getChunkStrategyVersion());
```

- [ ] **Step 7: Add RAG filtering test**

Add to `RagRetrievalServiceImplTest`:

```java
    @Test
    void retrieveSourcesFiltersReferenceChunksAfterMysqlLookup() throws Exception {
        QdrantService qdrantService = mock(QdrantService.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        QueryRewriteService queryRewriteService = mock(QueryRewriteService.class);

        when(queryRewriteService.rewriteForRetrieval("method question")).thenReturn("method question");
        when(qdrantService.searchSimilarChunksRaw("method question", 6, List.of())).thenReturn("""
                {"result":[
                  {"score":0.9,"payload":{"chunkId":1}},
                  {"score":0.8,"payload":{"chunkId":2}}
                ]}
                """);

        PaperChunk referenceChunk = new PaperChunk();
        referenceChunk.setId(1L);
        referenceChunk.setPaperId(10L);
        referenceChunk.setChunkIndex(1);
        referenceChunk.setContent("[1] reference item");
        referenceChunk.setIsReference(true);
        referenceChunk.setIsNoise(false);

        PaperChunk methodChunk = new PaperChunk();
        methodChunk.setId(2L);
        methodChunk.setPaperId(10L);
        methodChunk.setChunkIndex(2);
        methodChunk.setContent("method content");
        methodChunk.setSectionId(5L);
        methodChunk.setSectionTitle("Proposed Method");
        methodChunk.setSectionType("METHOD");
        methodChunk.setIsReference(false);
        methodChunk.setIsNoise(false);
        methodChunk.setChunkStrategyVersion("paper-structure-v1");

        PaperReference paper = new PaperReference();
        paper.setId(10L);
        paper.setTitle("Paper");

        when(paperChunkMapper.selectById(1L)).thenReturn(referenceChunk);
        when(paperChunkMapper.selectById(2L)).thenReturn(methodChunk);
        when(paperReferenceMapper.selectById(10L)).thenReturn(paper);

        RagRetrievalServiceImpl service = new RagRetrievalServiceImpl(
                qdrantService,
                paperChunkMapper,
                paperReferenceMapper,
                new ObjectMapper(),
                queryRewriteService
        );

        List<RagSource> sources = service.retrieveSources("method question", 2);

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getChunkId()).isEqualTo(2L);
        assertThat(sources.get(0).getSectionType()).isEqualTo("METHOD");
        assertThat(sources.get(0).getSectionTitle()).isEqualTo("Proposed Method");
    }
```

- [ ] **Step 8: Update existing RAG scope test expected Qdrant topK**

The existing `retrieveSourcesPassesPaperScopeToOriginalAndRewrittenSearches` uses `topK=5`. After over-fetching, Qdrant receives `15`.

Change stubs and verifies from:

```java
when(qdrantService.searchSimilarChunksRaw("中文问题", 5, List.of(3L, 5L)))
verify(qdrantService).searchSimilarChunksRaw("中文问题", 5, List.of(3L, 5L));
```

to:

```java
when(qdrantService.searchSimilarChunksRaw("中文问题", 15, List.of(3L, 5L)))
verify(qdrantService).searchSimilarChunksRaw("中文问题", 15, List.of(3L, 5L));
```

Apply the same change for `english query`.

- [ ] **Step 9: Run Qdrant and RAG retrieval tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=QdrantServiceTest,RagRetrievalServiceImplTest test
```

Expected: all tests pass.

- [ ] **Step 10: Record stage completion**

Update `docs/status/current.md`:

```text
论文 PDF 预处理与结构化分块优化阶段 4 已完成：向量化写入 Qdrant 时优先使用 indexText，并在 payload 中写入 sectionId、sectionType、sectionTitle、isReference、isNoise 和 chunkStrategyVersion；RAG 检索改为扩大召回后回查 MySQL 过滤参考文献/噪声 chunk，兼容旧 Qdrant payload；sources 已可返回章节信息。
```

---

## Task 5: Lightweight RAG Evaluation Baseline

**Files:**
- Modify: `docs/status/current.md`
- Create: `docs/evaluation/cases.md`

**Interfaces:**
- Produces: a stable manual evaluation checklist for current and future RAG changes.
- Consumes: existing `/api/rag/sources` and `/api/rag/chat` endpoints.

- [ ] **Step 1: Record stage start**

Update `docs/status/current.md`:

```text
论文 PDF 预处理与结构化分块优化进入阶段 5：建立轻量 RAG 评测基线，用固定问题和指标量化后续召回与回答质量改进。
```

- [ ] **Step 2: Create evaluation document**

Create `docs/evaluation/cases.md`:

```markdown
# RAG 评测用例与指标记录

更新时间：2026-07-06

本文用于记录固定 RAG 评测问题和每次优化前后的量化指标。后续每次 RAG 优化都应尽量使用同一批问题，避免只凭主观感觉判断效果。

## 1. 指标定义

| 指标 | 含义 | 趋势 |
| --- | --- | --- |
| referenceHitRate | sources 中 `isReference=true` 的比例 | 越低越好 |
| expectedSectionHitRate | sources 命中期望 sectionType 的比例 | 越高越好 |
| paperHitRate | sources 来自目标 paperIds 的比例 | 越高越好 |
| topKUsefulRate | 人工判断 topK sources 中有用片段比例 | 越高越好 |
| keyPointCoverage | 回答覆盖 expectedKeyPoints 的比例 | 越高越好 |
| citationSupportRate | 回答主要结论能被 sources 支撑的比例 | 越高越好 |
| answerCompletenessScore | 人工 1~5 分，评价回答完整性 | 越高越好 |
| answerNoiseScore | 人工 1~5 分，评价无关内容/参考文献污染 | 越低越好 |

## 2. 固定评测用例

### Case 1：单篇方法问题

```json
{
  "caseId": "single-paper-method-001",
  "question": "这篇论文的核心方法是什么？",
  "paperIds": [1],
  "questionType": "METHOD",
  "expectedSectionTypes": ["METHOD"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "说明模型或方法框架",
    "说明关键模块",
    "说明方法解决的问题"
  ]
}
```

### Case 2：单篇实验问题

```json
{
  "caseId": "single-paper-experiment-001",
  "question": "这篇论文做了哪些实验，实验结果说明了什么？",
  "paperIds": [1],
  "questionType": "EXPERIMENT",
  "expectedSectionTypes": ["EXPERIMENT", "RESULT"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "说明实验设置或数据集",
    "说明对比基线",
    "说明主要实验结果"
  ]
}
```

### Case 3：单篇创新点问题

```json
{
  "caseId": "single-paper-contribution-001",
  "question": "这篇论文的主要创新点是什么？",
  "paperIds": [1],
  "questionType": "SUMMARY",
  "expectedSectionTypes": ["ABSTRACT", "INTRODUCTION", "METHOD", "CONCLUSION"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "总结研究问题",
    "总结核心贡献",
    "说明相对已有工作的改进"
  ]
}
```

### Case 4：单篇局限问题

```json
{
  "caseId": "single-paper-limitation-001",
  "question": "这篇论文有什么不足或局限？",
  "paperIds": [1],
  "questionType": "LIMITATION",
  "expectedSectionTypes": ["DISCUSSION", "CONCLUSION", "RESULT"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "说明方法或实验限制",
    "说明适用场景限制",
    "说明未来工作方向"
  ]
}
```

### Case 5：参考文献干扰测试

```json
{
  "caseId": "single-paper-reference-noise-001",
  "question": "这篇论文的方法是否基于 Transformer？",
  "paperIds": [1],
  "questionType": "FACT",
  "expectedSectionTypes": ["METHOD", "EXPERIMENT", "RESULT"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "根据正文判断是否使用 Transformer",
    "不能只因为参考文献出现 Transformer 就判断使用了 Transformer"
  ]
}
```

### Case 6：多篇比较问题

```json
{
  "caseId": "multi-paper-method-compare-001",
  "question": "这几篇论文的方法路线有什么区别？",
  "paperIds": [1, 2],
  "questionType": "COMPARISON",
  "expectedSectionTypes": ["METHOD", "EXPERIMENT", "RESULT"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "分别说明每篇论文的方法",
    "比较方法差异",
    "指出适用场景或优缺点"
  ]
}
```

## 3. 手工记录模板

| 日期 | 版本/commit | caseId | referenceHitRate | expectedSectionHitRate | paperHitRate | topKUsefulRate | keyPointCoverage | citationSupportRate | answerCompletenessScore | answerNoiseScore | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-07-06 | baseline-before-structured-chunking | single-paper-method-001 |  |  |  |  |  |  |  |  |  |

## 4. Apifox 验证建议

### sources 检索

```http
GET /api/rag/sources?question=这篇论文的核心方法是什么？&topK=8&paperIds=1
```

记录：

```text
sources 总数
isReference=true 数量
sectionType 命中 METHOD 数量
是否来自目标 paperIds
人工判断有用片段数量
```

### RAG 问答

```http
POST /api/rag/chat
Content-Type: application/json

{
  "question": "这篇论文的核心方法是什么？",
  "topK": 8,
  "paperIds": [1]
}
```

记录：

```text
回答是否覆盖 expectedKeyPoints
回答结论是否能被 sources 支撑
回答完整性 1~5 分
回答噪声 1~5 分
```
```

- [ ] **Step 3: Record stage completion**

Update `docs/status/current.md`:

```text
论文 PDF 预处理与结构化分块优化阶段 5 已完成：已建立轻量 RAG 评测文档 docs/evaluation/cases.md，固定单篇方法、实验、创新点、局限、参考文献干扰和多篇比较等用例，并定义 referenceHitRate、expectedSectionHitRate、paperHitRate、keyPointCoverage 等指标，后续 RAG 改进可按同一批问题量化对比。
```

---

## Task 6: Final Verification and Commit

**Files:**
- Modify: `docs/status/current.md`
- All files changed by Tasks 1-5.

**Interfaces:**
- Verifies all backend unit tests and schema/docs consistency.
- Produces final commit for this stage.

- [ ] **Step 1: Record final verification start**

Update `docs/status/current.md`:

```text
论文 PDF 预处理与结构化分块优化进入阶段 6：进行后端整体测试、数据库脚本检查和功能状态汇总，准备提交阶段版本。
```

- [ ] **Step 2: Run full backend test suite**

Run from `backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

Expected: all tests pass.

- [ ] **Step 3: Optionally apply migration to local MySQL**

Only run this when local backend needs immediate manual verification against the existing database:

```bash
mysql --default-character-set=utf8mb4 -uroot -p123456 research_assistant < docs/database/migrations/paper-structured-chunking.sql
```

Expected:

```text
paper_section table exists
paper_chunk has section_id / section_type / index_text / is_reference / chunk_strategy_version columns
```

- [ ] **Step 4: Manual API verification with Apifox-style steps**

Use one PDF that can be safely re-parsed.

Parse:

```http
POST /api/papers/{paperId}/parse
```

Expected:

```text
Result.code = 200
返回 chunk 数量 > 0
paper_section 有章节记录
paper_chunk 有 sectionType / isReference / indexText
```

Vectorize:

```http
POST /api/papers/{paperId}/vectorize
```

Expected:

```text
Result.code = 200
Qdrant 写入成功
paper_chunk.qdrant_point_id 已回写
```

Sources:

```http
GET /api/rag/sources?question=这篇论文的核心方法是什么？&topK=8&paperIds={paperId}
```

Expected:

```text
sources 默认不包含 isReference=true
sources 返回 sectionType / sectionTitle / chunkStrategyVersion
METHOD 或相关章节命中比例高于旧固定切片版本
```

- [ ] **Step 5: Update final progress**

Update `docs/status/current.md` with actual verification numbers after commands finish. Use this shape and replace counts with real results:

```text
论文 PDF 预处理与结构化分块优化已完成：新增 paper_section 章节层，paper_chunk 已增强 sectionType、isReference、isNoise、indexText、tokenCount、chunkStrategyVersion 等字段；PDF 解析流程已升级为文本清洗、章节识别、References 标记和章节内结构化分块；向量化使用 indexText，Qdrant payload 携带章节元数据；RAG 检索默认过滤参考文献/噪声 chunk；轻量 RAG 评测文档已建立。最终验证：后端 ./mvnw test 通过（X 个测试，0 失败）；本地数据库迁移和 Apifox 验证结果为 XXX。
```

- [ ] **Step 6: Review git diff**

Run:

```bash
git status --short
git diff --stat
```

Expected: only planned files changed.

- [ ] **Step 7: Commit stage version**

Run:

```bash
git add docs/database/schema.sql docs/database/migrations/paper-structured-chunking.sql docs/evaluation/cases.md docs/status/current.md backend/research-assistant-backend/src/main/java backend/research-assistant-backend/src/test/java
git commit -m "Add structured paper chunking foundation"
```

Commit body must include:

```text
Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## Plan Self-Review

### Spec coverage

- `paper_section` design: Task 1.
- `paper_chunk` structural fields: Task 1.
- text cleaning: Task 2.
- section detection and References marking: Task 2.
- structured section/paragraph chunking: Task 3.
- `indexText` for embedding: Task 3 and Task 4.
- Qdrant payload metadata: Task 4.
- RAG default References/noise filtering: Task 4.
- lightweight evaluation mechanism: Task 5.
- verification and progress recording: each task plus Task 6.

### Scope deliberately excluded

- External paper parsers, OCR, table/formula/image understanding, paper profile, section summary, claim cards, complete parent-child retrieval, direct PDF LLM reading, and forced migration of old papers remain outside this plan.

### Important implementation notes

- RAG filters References after MySQL lookup rather than relying only on Qdrant payload filters. This preserves compatibility with old Qdrant points that do not yet contain `isReference/isNoise`.
- Existing old chunks get default `section_type='UNKNOWN'`, `index_text=content`, `is_reference=0`, and `chunk_strategy_version='fixed-window-v1'` from the migration script, but old Qdrant payloads are only refreshed after re-vectorization.
- The first evaluation mechanism is documentation-based and manual by design; it creates stable cases and metrics before introducing a heavier automated scoring service.
