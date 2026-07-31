# 文献画像与章节摘要基础层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为已解析论文生成并保存章节摘要 `paper_section_summary` 和整篇文献画像 `paper_profile`，并提供手动生成与查询接口。

**Architecture:** 在现有 Spring Boot + MyBatis-Plus 后端中新增 `paper.profile` 子模块。生成流程读取 `paper_reference`、`paper_section`、`paper_chunk`，过滤 REFERENCES / BACK_MATTER / reference / noise chunk，调用现有 provider-neutral `LlmService` 生成章节摘要和文献画像，再用同版本 upsert 方式保存到 MySQL。

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus, Lombok, MySQL 8, JUnit 5, Mockito, AssertJ.

## Global Constraints

- 先实现后端基础层，不改写 `/api/rag/chat` 主链路。
- 不实现完整 `HYBRID_RAG`，不把 `paper_profile` / `paper_section_summary` 写入 Qdrant。
- 不做异步任务队列；第一版由 `POST /api/papers/{id}/profile` 手动触发。
- 第一版复用当前 `LlmService`，不绑定 DeepSeek/Qwen/Zhipu。
- 重复生成同一版本默认覆盖更新，避免重复数据堆积。
- 默认排除 `REFERENCES`、`BACK_MATTER`、`isReference=true`、`isNoise=true` 的 chunk。
- 固定版本：`section-summary-v1`、`paper-profile-v1`。
- 编译和测试命令需使用 JDK 21：`JAVA_HOME=/path/to/jdk-21 ./mvnw ...`。
- 按项目规则，每个实质阶段完成后更新 `docs/status/current.md`。
- 按当前 Claude Code 安全规则，除非用户明确要求，不执行 `git commit`；本计划中的“提交”步骤改为“检查 git diff 并记录可提交点”。

---

## File Structure

### Create

- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperSectionSummary.java`
  保存单个章节摘要记录，对应 `paper_section_summary`。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperProfile.java`
  保存整篇文献画像记录，对应 `paper_profile`。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/mapper/PaperSectionSummaryMapper.java`
  MyBatis-Plus mapper。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/mapper/PaperProfileMapper.java`
  MyBatis-Plus mapper。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperSectionSummaryResponse.java`
  返回章节摘要给接口调用方。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperProfileResponse.java`
  返回文献画像及其章节摘要。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperProfileResult.java`
  Service 层返回对象，包含 profile 与 section summaries。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperProfileService.java`
  定义生成和查询画像接口。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/impl/PaperProfileServiceImpl.java`
  实现校验、过滤、prompt 构造、LLM 调用、轻量解析和 upsert。
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileServiceImplTest.java`
  核心单元测试。
- `docs/database/migrations/paper-profile-section-summary.sql`
  既有数据库增量迁移脚本。

### Modify

- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperController.java`
  注入 `PaperProfileService`，新增 `POST /api/papers/{id}/profile` 和 `GET /api/papers/{id}/profile`。
- `docs/database/schema.sql`
  补充全量建库脚本中的两张新表。
- `docs/status/current.md`
  每个阶段记录实质进展、验证结果、当前状态和下一步任务。

---

### Task 1: 数据库表、实体、Mapper 和 DTO 基础

**Files:**
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperSectionSummary.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperProfile.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/mapper/PaperSectionSummaryMapper.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/mapper/PaperProfileMapper.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperSectionSummaryResponse.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperProfileResponse.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperProfileResult.java`
- Create: `docs/database/migrations/paper-profile-section-summary.sql`
- Modify: `docs/database/schema.sql`
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: existing `PaperReference`, `PaperSection`, `PaperChunk`, MyBatis-Plus `BaseMapper`.
- Produces:
  - `PaperSectionSummary` fields: `id`, `paperId`, `sectionId`, `sectionType`, `sectionTitle`, `summary`, `keyPoints`, `sourceChunkIds`, `sourceTokenCount`, `summaryVersion`, `createTime`, `updateTime`.
  - `PaperProfile` fields: `id`, `paperId`, `title`, `researchProblem`, `methodSummary`, `experimentSummary`, `keyContributions`, `limitations`, `keywords`, `profileText`, `sourceSectionSummaryIds`, `profileVersion`, `createTime`, `updateTime`.
  - `PaperProfileResult(PaperProfileResponse profile, List<PaperSectionSummaryResponse> sectionSummaries)`.

- [ ] **Step 1: Create entities**

Create `PaperSectionSummary.java`:

```java
package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论文章节摘要实体。
 */
@Data
@TableName("paper_section_summary")
public class PaperSectionSummary {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long sectionId;
    private String sectionType;
    private String sectionTitle;
    private String summary;
    private String keyPoints;
    private String sourceChunkIds;
    private Integer sourceTokenCount;
    private String summaryVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

Create `PaperProfile.java`:

```java
package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 整篇文献画像实体。
 */
@Data
@TableName("paper_profile")
public class PaperProfile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private String title;
    private String researchProblem;
    private String methodSummary;
    private String experimentSummary;
    private String keyContributions;
    private String limitations;
    private String keywords;
    private String profileText;
    private String sourceSectionSummaryIds;
    private String profileVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 2: Create mappers**

Create `PaperSectionSummaryMapper.java`:

```java
package com.myagent.assistant.paper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 论文章节摘要 Mapper。
 */
@Mapper
public interface PaperSectionSummaryMapper extends BaseMapper<PaperSectionSummary> {
}
```

Create `PaperProfileMapper.java`:

```java
package com.myagent.assistant.paper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myagent.assistant.paper.entity.PaperProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文献画像 Mapper。
 */
@Mapper
public interface PaperProfileMapper extends BaseMapper<PaperProfile> {
}
```

- [ ] **Step 3: Create DTOs**

Create `PaperSectionSummaryResponse.java`:

```java
package com.myagent.assistant.paper.dto;

import lombok.Data;

/**
 * 章节摘要接口响应。
 */
@Data
public class PaperSectionSummaryResponse {
    private Long id;
    private Long paperId;
    private Long sectionId;
    private String sectionType;
    private String sectionTitle;
    private String summary;
    private String keyPoints;
    private Integer sourceTokenCount;
    private String summaryVersion;
}
```

Create `PaperProfileResponse.java`:

```java
package com.myagent.assistant.paper.dto;

import lombok.Data;

/**
 * 文献画像接口响应。
 */
@Data
public class PaperProfileResponse {
    private Long id;
    private Long paperId;
    private String title;
    private String researchProblem;
    private String methodSummary;
    private String experimentSummary;
    private String keyContributions;
    private String limitations;
    private String keywords;
    private String profileText;
    private String profileVersion;
}
```

Create `PaperProfileResult.java`:

```java
package com.myagent.assistant.paper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文献画像聚合结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperProfileResult {
    private PaperProfileResponse profile;
    private List<PaperSectionSummaryResponse> sectionSummaries;
}
```

- [ ] **Step 4: Add migration SQL**

Create `docs/database/migrations/paper-profile-section-summary.sql`:

```sql
-- 文献画像与章节摘要基础层数据库迁移脚本
-- 执行方式：mysql --default-character-set=utf8mb4 -uroot -p123456 research_assistant < docs/database/migrations/paper-profile-section-summary.sql

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
```

- [ ] **Step 5: Update full database script**

In `docs/database/schema.sql`, insert the same two `CREATE TABLE IF NOT EXISTS` blocks after `paper_chunk` and before `chat_session` so a fresh database contains the new tables.

- [ ] **Step 6: Compile to verify mappings**

Run:

```bash
cd <project-root>/backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Update progress document**

Append concise progress to `docs/status/current.md` under “下一步任务”:

```text
文献画像与章节摘要基础层进入阶段 1：新增 paper_section_summary 和 paper_profile 数据表、实体、Mapper 和响应 DTO，为手动生成章节摘要和整篇画像打基础。
文献画像与章节摘要基础层阶段 1 已完成：数据库全量脚本与迁移脚本已补充，后端实体、Mapper 和 DTO 已新增；JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile 编译通过。
文献画像与章节摘要基础层进入阶段 2：实现 PaperProfileService，完成有效 chunk 过滤、章节摘要生成、文献画像生成和同版本 upsert。
```

- [ ] **Step 8: Check diff checkpoint**

Run:

```bash
git status --short
```

Expected: shows new entity/mapper/dto/sql files and modified docs. Do not commit unless the user explicitly asks.

---

### Task 2: 章节摘要与文献画像生成服务

**Files:**
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperProfileService.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/impl/PaperProfileServiceImpl.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileServiceImplTest.java`
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes:
  - `PaperReferenceMapper.selectById(Long)`
  - `PaperSectionMapper.selectList(Wrapper<PaperSection>)`
  - `PaperChunkMapper.selectList(Wrapper<PaperChunk>)`
  - `PaperSectionSummaryMapper.selectOne/update/insert/selectList`
  - `PaperProfileMapper.selectOne/update/insert`
  - `LlmService.generateAnswer(String)`
- Produces:
  - `PaperProfileService.generateProfile(Long paperId): PaperProfileResult`
  - `PaperProfileService.getProfile(Long paperId): PaperProfileResult`

- [ ] **Step 1: Write failing tests**

Create `PaperProfileServiceImplTest.java` with these core tests:

```java
package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.paper.dto.PaperProfileResult;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSection;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperProfileServiceImplTest {

    @Test
    void generateProfileRejectsUnparsedPaper() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperReference paper = paper(7L, "Wind Paper", "PENDING");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);
        PaperProfileServiceImpl service = createService(paperReferenceMapper);

        assertThatThrownBy(() -> service.generateProfile(7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("请先解析 PDF 后再生成文献画像");
    }

    @Test
    void generateProfileFiltersReferenceAndNoiseChunks() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        LlmService llmService = mock(LlmService.class);

        PaperReference paper = paper(7L, "Wind Paper", "COMPLETED");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);
        PaperSection method = section(10L, 7L, "METHOD", "Method", 0);
        when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(method));
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                chunk(1L, 7L, 10L, "METHOD", "Method", 0, "method content explains the proposed model", false, false, 10),
                chunk(2L, 7L, 10L, "REFERENCES", "References", 1, "reference list", true, false, 5),
                chunk(3L, 7L, 10L, "BACK_MATTER", "Funding", 2, "funding text", false, true, 5)
        ));
        when(sectionSummaryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(llmService.generateAnswer(any(String.class)))
                .thenReturn("摘要：方法摘要\n关键点：\n- 方法关键点")
                .thenReturn("研究问题：研究问题\n方法概述：方法概述\n实验与评估：信息不足\n主要贡献：贡献\n局限性：信息不足\n关键词：RAG\n完整画像：完整画像文本");

        PaperProfileServiceImpl service = new PaperProfileServiceImpl(
                paperReferenceMapper,
                paperSectionMapper,
                paperChunkMapper,
                sectionSummaryMapper,
                paperProfileMapper,
                llmService
        );

        PaperProfileResult result = service.generateProfile(7L);

        assertThat(result.getProfile().getPaperId()).isEqualTo(7L);
        assertThat(result.getProfile().getResearchProblem()).isEqualTo("研究问题");
        assertThat(result.getSectionSummaries()).hasSize(1);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, org.mockito.Mockito.times(2)).generateAnswer(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(0)).contains("method content explains the proposed model");
        assertThat(promptCaptor.getAllValues().get(0)).doesNotContain("reference list");
        assertThat(promptCaptor.getAllValues().get(0)).doesNotContain("funding text");
    }

    @Test
    void getProfileReturnsNullProfileWhenMissing() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(sectionSummaryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        PaperProfileServiceImpl service = new PaperProfileServiceImpl(
                paperReferenceMapper,
                mock(PaperSectionMapper.class),
                mock(PaperChunkMapper.class),
                sectionSummaryMapper,
                paperProfileMapper,
                mock(LlmService.class)
        );

        PaperProfileResult result = service.getProfile(7L);

        assertThat(result.getProfile()).isNull();
        assertThat(result.getSectionSummaries()).isEmpty();
    }

    @Test
    void generateProfileRejectsPaperWithoutUsableChunks() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        LlmService llmService = mock(LlmService.class);
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
        when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(section(10L, 7L, "REFERENCES", "References", 0)));
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                chunk(1L, 7L, 10L, "REFERENCES", "References", 0, "reference list", true, false, 5)
        ));

        PaperProfileServiceImpl service = new PaperProfileServiceImpl(
                paperReferenceMapper,
                paperSectionMapper,
                paperChunkMapper,
                mock(PaperSectionSummaryMapper.class),
                mock(PaperProfileMapper.class),
                llmService
        );

        assertThatThrownBy(() -> service.generateProfile(7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("当前论文没有可用于生成画像的正文内容");
        verify(llmService, never()).generateAnswer(any(String.class));
    }

    private PaperProfileServiceImpl createService(PaperReferenceMapper paperReferenceMapper) {
        return new PaperProfileServiceImpl(
                paperReferenceMapper,
                mock(PaperSectionMapper.class),
                mock(PaperChunkMapper.class),
                mock(PaperSectionSummaryMapper.class),
                mock(PaperProfileMapper.class),
                mock(LlmService.class)
        );
    }

    private PaperReference paper(Long id, String title, String parseStatus) {
        PaperReference paper = new PaperReference();
        paper.setId(id);
        paper.setTitle(title);
        paper.setParseStatus(parseStatus);
        return paper;
    }

    private PaperSection section(Long id, Long paperId, String sectionType, String sectionTitle, Integer sectionIndex) {
        PaperSection section = new PaperSection();
        section.setId(id);
        section.setPaperId(paperId);
        section.setSectionType(sectionType);
        section.setSectionTitle(sectionTitle);
        section.setSectionIndex(sectionIndex);
        return section;
    }

    private PaperChunk chunk(Long id,
                             Long paperId,
                             Long sectionId,
                             String sectionType,
                             String sectionTitle,
                             Integer chunkIndex,
                             String content,
                             boolean reference,
                             boolean noise,
                             int tokenCount) {
        PaperChunk chunk = new PaperChunk();
        chunk.setId(id);
        chunk.setPaperId(paperId);
        chunk.setSectionId(sectionId);
        chunk.setSectionType(sectionType);
        chunk.setSectionTitle(sectionTitle);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setIsReference(reference);
        chunk.setIsNoise(noise);
        chunk.setTokenCount(tokenCount);
        return chunk;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd <project-root>/backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileServiceImplTest test
```

Expected: compilation failure because `PaperProfileServiceImpl` does not exist yet.

- [ ] **Step 3: Create service interface**

Create `PaperProfileService.java`:

```java
package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.dto.PaperProfileResult;

/**
 * 文献画像服务。
 */
public interface PaperProfileService {

    /**
     * 为指定已解析论文生成或更新文献画像。
     */
    PaperProfileResult generateProfile(Long paperId);

    /**
     * 查询指定论文已生成的文献画像。
     */
    PaperProfileResult getProfile(Long paperId);
}
```

- [ ] **Step 4: Implement service**

Create `PaperProfileServiceImpl.java` with this structure:

```java
package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.paper.dto.PaperProfileResponse;
import com.myagent.assistant.paper.dto.PaperProfileResult;
import com.myagent.assistant.paper.dto.PaperSectionSummaryResponse;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSection;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.paper.service.PaperProfileService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文献画像服务实现。
 */
@Service
public class PaperProfileServiceImpl implements PaperProfileService {

    private static final String SECTION_SUMMARY_VERSION = "section-summary-v1";
    private static final String PAPER_PROFILE_VERSION = "paper-profile-v1";
    private static final int MAX_SECTION_CONTEXT_CHARS = 8000;

    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperSectionMapper paperSectionMapper;
    private final PaperChunkMapper paperChunkMapper;
    private final PaperSectionSummaryMapper sectionSummaryMapper;
    private final PaperProfileMapper paperProfileMapper;
    private final LlmService llmService;

    public PaperProfileServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                   PaperSectionMapper paperSectionMapper,
                                   PaperChunkMapper paperChunkMapper,
                                   PaperSectionSummaryMapper sectionSummaryMapper,
                                   PaperProfileMapper paperProfileMapper,
                                   LlmService llmService) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperSectionMapper = paperSectionMapper;
        this.paperChunkMapper = paperChunkMapper;
        this.sectionSummaryMapper = sectionSummaryMapper;
        this.paperProfileMapper = paperProfileMapper;
        this.llmService = llmService;
    }

    @Override
    public PaperProfileResult generateProfile(Long paperId) {
        PaperReference paper = requireParsedPaper(paperId);
        List<PaperSection> sections = listSections(paperId);
        List<PaperChunk> usableChunks = listUsableChunks(paperId);

        if (usableChunks.isEmpty()) {
            throw new RuntimeException("当前论文没有可用于生成画像的正文内容");
        }

        Map<Long, PaperSection> sectionsById = sections.stream()
                .filter(section -> section.getId() != null)
                .collect(Collectors.toMap(PaperSection::getId, section -> section, (a, b) -> a, LinkedHashMap::new));
        Map<Long, List<PaperChunk>> chunksBySection = groupChunksBySection(usableChunks);
        List<PaperSectionSummary> savedSummaries = new ArrayList<>();

        for (Map.Entry<Long, List<PaperChunk>> entry : chunksBySection.entrySet()) {
            PaperSection section = sectionsById.get(entry.getKey());
            PaperSectionSummary summary = generateAndUpsertSectionSummary(paper, section, entry.getKey(), entry.getValue());
            savedSummaries.add(summary);
        }

        PaperProfile profile = generateAndUpsertProfile(paper, savedSummaries);
        return new PaperProfileResult(toProfileResponse(profile), savedSummaries.stream().map(this::toSummaryResponse).toList());
    }

    @Override
    public PaperProfileResult getProfile(Long paperId) {
        requirePaper(paperId);
        PaperProfile profile = paperProfileMapper.selectOne(new QueryWrapper<PaperProfile>()
                .eq("paper_id", paperId)
                .eq("profile_version", PAPER_PROFILE_VERSION));
        List<PaperSectionSummary> summaries = sectionSummaryMapper.selectList(new QueryWrapper<PaperSectionSummary>()
                .eq("paper_id", paperId)
                .eq("summary_version", SECTION_SUMMARY_VERSION)
                .orderByAsc("section_id"));
        return new PaperProfileResult(
                profile == null ? null : toProfileResponse(profile),
                summaries.stream().map(this::toSummaryResponse).toList()
        );
    }

    private PaperReference requireParsedPaper(Long paperId) {
        PaperReference paper = requirePaper(paperId);
        if (!"COMPLETED".equals(paper.getParseStatus())) {
            throw new RuntimeException("请先解析 PDF 后再生成文献画像");
        }
        return paper;
    }

    private PaperReference requirePaper(Long paperId) {
        if (paperId == null || paperId <= 0) {
            throw new RuntimeException("paperId 不能为空");
        }
        PaperReference paper = paperReferenceMapper.selectById(paperId);
        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }
        return paper;
    }

    private List<PaperSection> listSections(Long paperId) {
        return paperSectionMapper.selectList(new QueryWrapper<PaperSection>()
                .eq("paper_id", paperId)
                .orderByAsc("section_index"));
    }

    private List<PaperChunk> listUsableChunks(Long paperId) {
        return paperChunkMapper.selectList(new QueryWrapper<PaperChunk>()
                        .eq("paper_id", paperId)
                        .orderByAsc("chunk_index"))
                .stream()
                .filter(this::isUsableChunk)
                .toList();
    }

    private boolean isUsableChunk(PaperChunk chunk) {
        if (chunk == null || chunk.getContent() == null || chunk.getContent().isBlank()) {
            return false;
        }
        if (Boolean.TRUE.equals(chunk.getIsReference()) || Boolean.TRUE.equals(chunk.getIsNoise())) {
            return false;
        }
        String sectionType = chunk.getSectionType();
        return !"REFERENCES".equals(sectionType) && !"BACK_MATTER".equals(sectionType);
    }

    private Map<Long, List<PaperChunk>> groupChunksBySection(List<PaperChunk> chunks) {
        Map<Long, List<PaperChunk>> result = new LinkedHashMap<>();
        for (PaperChunk chunk : chunks) {
            Long sectionId = chunk.getSectionId() != null ? chunk.getSectionId() : -chunk.getId();
            result.computeIfAbsent(sectionId, ignored -> new ArrayList<>()).add(chunk);
        }
        return result;
    }

    private PaperSectionSummary generateAndUpsertSectionSummary(PaperReference paper,
                                                                PaperSection section,
                                                                Long sectionId,
                                                                List<PaperChunk> chunks) {
        PaperChunk firstChunk = chunks.get(0);
        String sectionType = section != null ? section.getSectionType() : nullToDefault(firstChunk.getSectionType(), "UNKNOWN");
        String sectionTitle = section != null ? section.getSectionTitle() : nullToDefault(firstChunk.getSectionTitle(), "Unknown");
        String prompt = buildSectionSummaryPrompt(paper, sectionType, sectionTitle, chunks);
        String llmText = llmService.generateAnswer(prompt);

        PaperSectionSummary summary = new PaperSectionSummary();
        summary.setPaperId(paper.getId());
        summary.setSectionId(sectionId);
        summary.setSectionType(sectionType);
        summary.setSectionTitle(sectionTitle);
        summary.setSummary(extractBetween(llmText, "摘要：", "关键点："));
        summary.setKeyPoints(extractAfter(llmText, "关键点："));
        summary.setSourceChunkIds(chunks.stream().map(PaperChunk::getId).filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(",")));
        summary.setSourceTokenCount(chunks.stream().mapToInt(this::tokenCount).sum());
        summary.setSummaryVersion(SECTION_SUMMARY_VERSION);
        return upsertSectionSummary(summary);
    }

    private String buildSectionSummaryPrompt(PaperReference paper, String sectionType, String sectionTitle, List<PaperChunk> chunks) {
        StringBuilder content = new StringBuilder();
        int usedChars = 0;
        for (PaperChunk chunk : chunks) {
            String normalized = normalize(chunk.getContent());
            if (normalized.isBlank()) {
                continue;
            }
            int remaining = MAX_SECTION_CONTEXT_CHARS - usedChars;
            if (remaining <= 0) {
                break;
            }
            if (normalized.length() > remaining) {
                normalized = normalized.substring(0, remaining);
            }
            content.append(normalized).append("\n\n");
            usedChars += normalized.length();
        }

        return "你是论文阅读助手。请根据下面某篇论文的一个章节内容，生成章节摘要。\n\n"
                + "要求：\n"
                + "1. 用中文。\n"
                + "2. 不要编造章节中没有的信息。\n"
                + "3. 摘要控制在 150~300 字。\n"
                + "4. 提取 3~6 条关键点。\n"
                + "5. 如果该章节内容不足以总结，请说明信息不足。\n\n"
                + "论文标题：" + nullToDefault(paper.getTitle(), "") + "\n"
                + "章节类型：" + nullToDefault(sectionType, "UNKNOWN") + "\n"
                + "章节标题：" + nullToDefault(sectionTitle, "") + "\n"
                + "章节正文：\n" + content;
    }

    private PaperSectionSummary upsertSectionSummary(PaperSectionSummary summary) {
        PaperSectionSummary existing = sectionSummaryMapper.selectOne(new QueryWrapper<PaperSectionSummary>()
                .eq("paper_id", summary.getPaperId())
                .eq("section_id", summary.getSectionId())
                .eq("summary_version", summary.getSummaryVersion()));
        if (existing == null) {
            sectionSummaryMapper.insert(summary);
            return summary;
        }
        summary.setId(existing.getId());
        sectionSummaryMapper.updateById(summary);
        return summary;
    }

    private PaperProfile generateAndUpsertProfile(PaperReference paper, List<PaperSectionSummary> summaries) {
        String prompt = buildProfilePrompt(paper, summaries);
        String llmText = llmService.generateAnswer(prompt);

        PaperProfile profile = new PaperProfile();
        profile.setPaperId(paper.getId());
        profile.setTitle(paper.getTitle());
        profile.setResearchProblem(extractBetween(llmText, "研究问题：", "方法概述："));
        profile.setMethodSummary(extractBetween(llmText, "方法概述：", "实验与评估："));
        profile.setExperimentSummary(extractBetween(llmText, "实验与评估：", "主要贡献："));
        profile.setKeyContributions(extractBetween(llmText, "主要贡献：", "局限性："));
        profile.setLimitations(extractBetween(llmText, "局限性：", "关键词："));
        profile.setKeywords(extractBetween(llmText, "关键词：", "完整画像："));
        profile.setProfileText(extractAfter(llmText, "完整画像："));
        if (profile.getProfileText().isBlank()) {
            profile.setProfileText(llmText);
        }
        profile.setSourceSectionSummaryIds(summaries.stream().map(PaperSectionSummary::getId).filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(",")));
        profile.setProfileVersion(PAPER_PROFILE_VERSION);
        return upsertProfile(profile);
    }

    private String buildProfilePrompt(PaperReference paper, List<PaperSectionSummary> summaries) {
        String sectionTexts = summaries.stream()
                .sorted(Comparator.comparing(PaperSectionSummary::getSectionId, Comparator.nullsLast(Long::compareTo)))
                .map(summary -> "[" + nullToDefault(summary.getSectionType(), "UNKNOWN") + "] "
                        + nullToDefault(summary.getSectionTitle(), "") + "\n"
                        + "摘要：" + nullToDefault(summary.getSummary(), "") + "\n"
                        + "关键点：" + nullToDefault(summary.getKeyPoints(), ""))
                .collect(Collectors.joining("\n\n"));

        return "你是论文/文献 AI 研究助手。请根据下面的章节摘要，生成整篇论文画像。\n\n"
                + "需要覆盖：\n"
                + "1. 研究问题\n2. 方法概述\n3. 实验与评估\n4. 主要贡献\n5. 局限性\n6. 关键词\n7. 一段适合 RAG 使用的完整画像文本\n\n"
                + "要求：\n"
                + "- 用中文。\n"
                + "- 不要使用 References 或后置声明作为论文正文结论。\n"
                + "- 如果某项信息不足，请写“信息不足”，不要编造。\n\n"
                + "输出格式必须使用以下中文字段名：\n"
                + "研究问题：...\n方法概述：...\n实验与评估：...\n主要贡献：...\n局限性：...\n关键词：...\n完整画像：...\n\n"
                + "论文标题：" + nullToDefault(paper.getTitle(), "") + "\n\n"
                + "章节摘要：\n" + sectionTexts;
    }

    private PaperProfile upsertProfile(PaperProfile profile) {
        PaperProfile existing = paperProfileMapper.selectOne(new QueryWrapper<PaperProfile>()
                .eq("paper_id", profile.getPaperId())
                .eq("profile_version", profile.getProfileVersion()));
        if (existing == null) {
            paperProfileMapper.insert(profile);
            return profile;
        }
        profile.setId(existing.getId());
        paperProfileMapper.updateById(profile);
        return profile;
    }

    private PaperProfileResponse toProfileResponse(PaperProfile profile) {
        PaperProfileResponse response = new PaperProfileResponse();
        response.setId(profile.getId());
        response.setPaperId(profile.getPaperId());
        response.setTitle(profile.getTitle());
        response.setResearchProblem(profile.getResearchProblem());
        response.setMethodSummary(profile.getMethodSummary());
        response.setExperimentSummary(profile.getExperimentSummary());
        response.setKeyContributions(profile.getKeyContributions());
        response.setLimitations(profile.getLimitations());
        response.setKeywords(profile.getKeywords());
        response.setProfileText(profile.getProfileText());
        response.setProfileVersion(profile.getProfileVersion());
        return response;
    }

    private PaperSectionSummaryResponse toSummaryResponse(PaperSectionSummary summary) {
        PaperSectionSummaryResponse response = new PaperSectionSummaryResponse();
        response.setId(summary.getId());
        response.setPaperId(summary.getPaperId());
        response.setSectionId(summary.getSectionId());
        response.setSectionType(summary.getSectionType());
        response.setSectionTitle(summary.getSectionTitle());
        response.setSummary(summary.getSummary());
        response.setKeyPoints(summary.getKeyPoints());
        response.setSourceTokenCount(summary.getSourceTokenCount());
        response.setSummaryVersion(summary.getSummaryVersion());
        return response;
    }

    private int tokenCount(PaperChunk chunk) {
        if (chunk.getTokenCount() != null && chunk.getTokenCount() > 0) {
            return chunk.getTokenCount();
        }
        return Math.max(1, (int) Math.ceil(nullToDefault(chunk.getContent(), "").length() / 4.0));
    }

    private String normalize(String content) {
        return content == null ? "" : content.replaceAll("\\s+", " ").trim();
    }

    private String extractBetween(String text, String startMarker, String endMarker) {
        if (text == null || text.isBlank()) {
            return "信息不足";
        }
        int start = text.indexOf(startMarker);
        if (start < 0) {
            return "信息不足";
        }
        start += startMarker.length();
        int end = text.indexOf(endMarker, start);
        if (end < 0) {
            end = text.length();
        }
        String value = text.substring(start, end).trim();
        return value.isBlank() ? "信息不足" : value;
    }

    private String extractAfter(String text, String marker) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int start = text.indexOf(marker);
        if (start < 0) {
            return "";
        }
        return text.substring(start + marker.length()).trim();
    }

    private String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
```

- [ ] **Step 5: Run focused service tests**

Run:

```bash
cd <project-root>/backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileServiceImplTest test
```

Expected: `Tests run: 4, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 6: Update progress document**

Append:

```text
文献画像与章节摘要基础层阶段 2 已完成：新增 PaperProfileService，支持校验已解析论文、过滤参考文献/噪声 chunk、按章节生成摘要、汇总章节摘要生成文献画像，并按固定版本 upsert 保存；PaperProfileServiceImplTest 通过，验证未解析拒绝、无有效正文拒绝、过滤 reference/noise 和查询空画像。
文献画像与章节摘要基础层进入阶段 3：新增手动生成和查询文献画像接口，并补充接口层测试/编译验证。
```

- [ ] **Step 7: Check diff checkpoint**

Run:

```bash
git status --short
```

Expected: service and test files appear in diff. Do not commit unless the user explicitly asks.

---

### Task 3: 后端接口接入

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperController.java`
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileServiceImplTest.java` only if service method names changed; otherwise no change.
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: `PaperProfileService.generateProfile(Long)`, `PaperProfileService.getProfile(Long)`.
- Produces:
  - `POST /api/papers/{id}/profile -> Result<PaperProfileResult>`
  - `GET /api/papers/{id}/profile -> Result<PaperProfileResult>`

- [ ] **Step 1: Modify controller constructor and fields**

Change `PaperController.java` imports and constructor:

```java
import com.myagent.assistant.paper.dto.PaperProfileResult;
import com.myagent.assistant.paper.service.PaperProfileService;
```

Change fields:

```java
private final PaperReferenceService paperReferenceService;
private final PaperProfileService paperProfileService;
```

Change constructor:

```java
public PaperController(PaperReferenceService paperReferenceService,
                       PaperProfileService paperProfileService) {
    this.paperReferenceService = paperReferenceService;
    this.paperProfileService = paperProfileService;
}
```

- [ ] **Step 2: Add profile endpoints**

Add near the existing paper endpoints:

```java
/**
 * 手动生成或更新文献画像。
 *
 * 访问示例：
 * POST /api/papers/7/profile
 */
@PostMapping("/api/papers/{id}/profile")
public Result<PaperProfileResult> generateProfile(@PathVariable Long id) {
    return Result.success(paperProfileService.generateProfile(id));
}

/**
 * 查询文献画像。
 *
 * 访问示例：
 * GET /api/papers/7/profile
 */
@GetMapping("/api/papers/{id}/profile")
public Result<PaperProfileResult> getProfile(@PathVariable Long id) {
    return Result.success(paperProfileService.getProfile(id));
}
```

- [ ] **Step 3: Run compile**

Run:

```bash
cd <project-root>/backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Run focused tests**

Run:

```bash
cd <project-root>/backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileServiceImplTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Update progress document**

Append:

```text
文献画像与章节摘要基础层阶段 3 已完成：PaperController 新增 POST /api/papers/{id}/profile 手动生成/更新画像和 GET /api/papers/{id}/profile 查询画像接口，返回统一 Result<PaperProfileResult>；后端编译和 PaperProfileServiceImplTest 通过。
文献画像与章节摘要基础层进入阶段 4：执行数据库迁移、整体测试、Apifox 接口验证建议和后续 HYBRID_RAG 衔接记录。
```

- [ ] **Step 6: Check diff checkpoint**

Run:

```bash
git status --short
```

Expected: controller file modified. Do not commit unless the user explicitly asks.

---

### Task 4: 整体验证、迁移说明和阶段收尾

**Files:**
- Modify: `docs/status/current.md`
- Optional manual execution: `docs/database/migrations/paper-profile-section-summary.sql` against local MySQL.

**Interfaces:**
- Consumes: all artifacts from Tasks 1-3.
- Produces: verified backend build and clear Apifox/manual validation steps.

- [ ] **Step 1: Run full backend tests**

Run:

```bash
cd <project-root>/backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

Expected: `BUILD SUCCESS`; existing test count should be at least 48 after adding `PaperProfileServiceImplTest`.

- [ ] **Step 2: Apply local database migration if running interface verification**

Run in Git Bash if MySQL CLI is available:

```bash
mysql --default-character-set=utf8mb4 -uroot -p123456 research_assistant < <project-root>/docs/database/migrations/paper-profile-section-summary.sql
```

Expected: no SQL error. If MySQL CLI is unavailable in this shell, ask the user to run this command from their MySQL-capable terminal before Apifox verification.

- [ ] **Step 3: Start backend for manual verification**

Run:

```bash
cd <project-root>/backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw spring-boot:run
```

Expected: Spring Boot starts on configured port, normally `8080`.

- [ ] **Step 4: Apifox verification for profile generation**

In Apifox:

```http
POST http://localhost:8080/api/papers/7/profile
```

Expected response shape:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "profile": {
      "paperId": 7,
      "profileVersion": "paper-profile-v1",
      "researchProblem": "...",
      "methodSummary": "...",
      "experimentSummary": "...",
      "keyContributions": "...",
      "limitations": "...",
      "keywords": "...",
      "profileText": "..."
    },
    "sectionSummaries": [
      {
        "paperId": 7,
        "summaryVersion": "section-summary-v1",
        "sectionType": "METHOD",
        "summary": "...",
        "keyPoints": "..."
      }
    ]
  }
}
```

- [ ] **Step 5: Apifox verification for profile query**

In Apifox:

```http
GET http://localhost:8080/api/papers/7/profile
```

Expected: returns the same `profileVersion = paper-profile-v1` and at least one `sectionSummaries` item for an already generated paper.

- [ ] **Step 6: Repeat generation to verify upsert**

In Apifox, call again:

```http
POST http://localhost:8080/api/papers/7/profile
```

Expected: returns `code = 200`; database should not accumulate duplicate same-version rows because unique keys are `(paper_id, section_id, summary_version)` and `(paper_id, profile_version)`.

- [ ] **Step 7: Update final progress document**

Append concise final status:

```text
文献画像与章节摘要基础层已完成阶段性实现：新增 paper_section_summary 章节摘要表和 paper_profile 文献画像表；新增 PaperProfileService，可从已解析论文中排除 REFERENCES/BACK_MATTER/reference/noise chunk，按章节生成中文摘要和关键点，再汇总生成研究问题、方法概述、实验评估、主要贡献、局限性、关键词和 RAG 画像文本；新增 POST /api/papers/{id}/profile 和 GET /api/papers/{id}/profile。最终验证：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过；如已执行本地迁移和 Apifox 验证，则记录对应接口结果。下一阶段建议进入 HYBRID_RAG 设计：让多篇/长论文问答组合使用 paper_profile、section_summary 和少量 raw chunks。
```

If Apifox was not run because the server or database was not started, record that truthfully:

```text
接口人工验证待用户在本地 MySQL 执行 docs/database/migrations/paper-profile-section-summary.sql 并通过 Apifox 调用 POST/GET /api/papers/{id}/profile 后补充。
```

- [ ] **Step 8: Final diff review**

Run:

```bash
git status --short
```

Expected: implementation files and docs are modified. Do not commit unless the user explicitly asks.

---

## Self-Review

- Spec coverage: 数据模型、Mapper、DTO、Service、Controller、过滤规则、版本策略、upsert、测试、Apifox 验证和进度记录均有对应任务。
- Placeholder scan: 本计划不包含 TBD/TODO/“稍后实现”类占位描述；代码步骤给出具体类名、方法名和核心代码。
- Type consistency: `PaperProfileService.generateProfile/getProfile`、`PaperProfileResult`、`PaperProfileResponse`、`PaperSectionSummaryResponse` 在任务 2 和任务 3 中保持一致。
- Scope check: 本计划只做基础资产层，不实现 HYBRID_RAG、不改 RAG 主链路、不做前端 UI，符合当前设计文档范围。
