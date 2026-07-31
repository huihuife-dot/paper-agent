# 单篇论文 FULL_TEXT_PARSED Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为单篇论文问答增加 `FULL_TEXT_PARSED` 上下文策略：当用户只选择 1 篇已解析论文且正文 token 不超预算时，按章节组织较完整正文上下文回答宏观问题，否则回退当前 `VECTOR_RAG`。

**Architecture:** 在现有 RAG chat 流程前增加策略路由层：`ContextStrategyService` 负责选择 `FULL_TEXT_PARSED` 或 `VECTOR_RAG`；`FullTextContextService` 负责从 MySQL 的 `paper_section` / `paper_chunk` 构造单篇论文全文上下文；`RagChatServiceImpl` 根据策略选择 prompt 构造路径。保持 `/api/rag/sources` 仍为向量检索调试接口，不做前端 UI 大改。

**Tech Stack:** Spring Boot 3.5.14, Java 21, MyBatis-Plus 3.5.7, MySQL 8, Qdrant REST API, JUnit 5, Mockito, AssertJ。

## Global Constraints

- 每个实质阶段开始和完成后都要更新 `docs/status/current.md`，只记录项目实质状态、关键验证结果、当前状态和下一步任务。
- 本阶段只做单篇论文 `FULL_TEXT_PARSED`；多篇全文、HYBRID、文献画像、章节摘要、claim cards、reranker、前端 UI 大改不纳入本阶段。
- `FULL_TEXT_PARSED` 只在有效 `paperIds` 只有 1 篇、论文 `parse_status=COMPLETED`、非 reference/noise chunk 总 token 不超过预算时启用。
- 默认排除 `isReference=true`、`isNoise=true`、`sectionType=REFERENCES`、`sectionType=BACK_MATTER` 的 chunk。
- 其他情况必须回退现有 `VECTOR_RAG`，保证旧问答流程兼容。
- 需要在 `RagChatResponse` 返回 `contextStrategy`、`contextTokenCount`、`contextPaperIds` 便于调试和评测。
- 完成后必须更新 `docs/archive/notes/rag-improvement-notes.md`，记录相比 `paper-structure-v1` 的改进、验证结果和当前遗留问题。
- 完成后运行后端测试，并提交 Git 版本。
- 后端测试命令使用 JDK 21：`JAVA_HOME=/path/to/jdk-21 ./mvnw test`。
- 设计依据：`docs/archive/specs/2026-07-06-single-paper-full-text-context-design.md`。

---

## File Structure

### Backend DTO / model

- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/context/ContextStrategy.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/context/FullTextContext.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/dto/RagChatResponse.java`

### Backend services

- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/ContextStrategyService.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/FullTextContextService.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/ContextStrategyServiceImpl.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/FullTextContextServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/RagPromptService.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagPromptServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagChatServiceImpl.java`

### Tests

- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/ContextStrategyServiceImplTest.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/FullTextContextServiceImplTest.java`
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/RagChatServiceImplTest.java`

### Docs

- Modify: `docs/status/current.md`
- Modify: `docs/archive/notes/rag-improvement-notes.md`

---

## Task 1: Context Strategy Foundation

**Files:**
- Modify: `docs/status/current.md`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/context/ContextStrategy.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/ContextStrategyService.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/ContextStrategyServiceImpl.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/ContextStrategyServiceImplTest.java`

**Interfaces:**
- Produces: `ContextStrategy` enum with `VECTOR_RAG` and `FULL_TEXT_PARSED`.
- Produces: `ContextStrategyService.chooseStrategy(List<Long> paperIds): ContextStrategy`.
- Consumes: `PaperReferenceMapper` and `PaperChunkMapper`.

- [ ] **Step 1: Record stage start**

Update `docs/status/current.md`:

```text
单篇论文 FULL_TEXT_PARSED 进入阶段 1：新增上下文策略基础，支持根据单篇论文范围、解析状态和 token 预算自动选择 FULL_TEXT_PARSED 或回退 VECTOR_RAG。
```

- [ ] **Step 2: Create `ContextStrategy` enum**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/context/ContextStrategy.java`:

```java
package com.myagent.assistant.rag.context;

/**
 * RAG 上下文策略。
 */
public enum ContextStrategy {
    /**
     * 当前已有向量检索 RAG：问题 embedding -> Qdrant topK -> chunk prompt。
     */
    VECTOR_RAG,

    /**
     * 单篇论文全文解析上下文：按章节组织 MySQL 中的结构化 chunk。
     */
    FULL_TEXT_PARSED
}
```

- [ ] **Step 3: Create `ContextStrategyService` interface**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/ContextStrategyService.java`:

```java
package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.context.ContextStrategy;

import java.util.List;

/**
 * RAG 上下文策略选择服务。
 */
public interface ContextStrategyService {

    /**
     * 根据本轮有效论文范围选择上下文策略。
     *
     * @param paperIds 本轮限定的论文 ID；为空或多篇时默认回退 VECTOR_RAG
     * @return 上下文策略
     */
    ContextStrategy chooseStrategy(List<Long> paperIds);
}
```

- [ ] **Step 4: Write `ContextStrategyServiceImplTest`**

Create `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/ContextStrategyServiceImplTest.java`:

```java
package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.rag.context.ContextStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextStrategyServiceImplTest {

    @Test
    void chooseFullTextForSingleParsedPaperWithinBudget() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);

        PaperReference paper = new PaperReference();
        paper.setId(7L);
        paper.setParseStatus("COMPLETED");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);

        PaperChunk chunk = new PaperChunk();
        chunk.setTokenCount(1000);
        chunk.setIsReference(false);
        chunk.setIsNoise(false);
        chunk.setSectionType("METHOD");
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chunk));

        ContextStrategyServiceImpl service = new ContextStrategyServiceImpl(
                paperReferenceMapper,
                paperChunkMapper,
                60000
        );

        assertThat(service.chooseStrategy(List.of(7L))).isEqualTo(ContextStrategy.FULL_TEXT_PARSED);
    }

    @Test
    void chooseVectorRagForMultiplePapers() {
        ContextStrategyServiceImpl service = new ContextStrategyServiceImpl(
                mock(PaperReferenceMapper.class),
                mock(PaperChunkMapper.class),
                60000
        );

        assertThat(service.chooseStrategy(List.of(7L, 8L))).isEqualTo(ContextStrategy.VECTOR_RAG);
    }

    @Test
    void chooseVectorRagWhenPaperNotParsed() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);

        PaperReference paper = new PaperReference();
        paper.setId(7L);
        paper.setParseStatus("FAILED");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);

        ContextStrategyServiceImpl service = new ContextStrategyServiceImpl(
                paperReferenceMapper,
                mock(PaperChunkMapper.class),
                60000
        );

        assertThat(service.chooseStrategy(List.of(7L))).isEqualTo(ContextStrategy.VECTOR_RAG);
    }

    @Test
    void chooseVectorRagWhenTokenBudgetExceeded() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);

        PaperReference paper = new PaperReference();
        paper.setId(7L);
        paper.setParseStatus("COMPLETED");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);

        PaperChunk chunk = new PaperChunk();
        chunk.setTokenCount(70000);
        chunk.setIsReference(false);
        chunk.setIsNoise(false);
        chunk.setSectionType("METHOD");
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chunk));

        ContextStrategyServiceImpl service = new ContextStrategyServiceImpl(
                paperReferenceMapper,
                paperChunkMapper,
                60000
        );

        assertThat(service.chooseStrategy(List.of(7L))).isEqualTo(ContextStrategy.VECTOR_RAG);
    }
}
```

- [ ] **Step 5: Run strategy tests and verify failure**

Run from `backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=ContextStrategyServiceImplTest test
```

Expected: fails because implementation does not exist.

- [ ] **Step 6: Implement `ContextStrategyServiceImpl`**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/ContextStrategyServiceImpl.java`:

```java
package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.rag.context.ContextStrategy;
import com.myagent.assistant.rag.service.ContextStrategyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 上下文策略选择服务实现。
 */
@Service
public class ContextStrategyServiceImpl implements ContextStrategyService {

    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperChunkMapper paperChunkMapper;
    private final int fullTextBudgetTokens;

    public ContextStrategyServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                      PaperChunkMapper paperChunkMapper,
                                      @Value("${rag.full-text-budget-tokens:60000}") int fullTextBudgetTokens) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperChunkMapper = paperChunkMapper;
        this.fullTextBudgetTokens = fullTextBudgetTokens;
    }

    @Override
    public ContextStrategy chooseStrategy(List<Long> paperIds) {
        List<Long> normalizedPaperIds = normalizePaperIds(paperIds);
        if (normalizedPaperIds.size() != 1) {
            return ContextStrategy.VECTOR_RAG;
        }

        Long paperId = normalizedPaperIds.get(0);
        PaperReference paper = paperReferenceMapper.selectById(paperId);
        if (paper == null || !"COMPLETED".equals(paper.getParseStatus())) {
            return ContextStrategy.VECTOR_RAG;
        }

        List<PaperChunk> chunks = paperChunkMapper.selectList(
                new QueryWrapper<PaperChunk>()
                        .eq("paper_id", paperId)
                        .orderByAsc("chunk_index")
        );

        int tokenCount = chunks.stream()
                .filter(this::isUsableFullTextChunk)
                .mapToInt(this::tokenCount)
                .sum();

        if (tokenCount <= 0 || tokenCount > fullTextBudgetTokens) {
            return ContextStrategy.VECTOR_RAG;
        }

        return ContextStrategy.FULL_TEXT_PARSED;
    }

    private boolean isUsableFullTextChunk(PaperChunk chunk) {
        if (chunk == null) {
            return false;
        }
        if (Boolean.TRUE.equals(chunk.getIsReference()) || Boolean.TRUE.equals(chunk.getIsNoise())) {
            return false;
        }
        String sectionType = chunk.getSectionType();
        return !"REFERENCES".equals(sectionType) && !"BACK_MATTER".equals(sectionType);
    }

    private int tokenCount(PaperChunk chunk) {
        if (chunk.getTokenCount() != null && chunk.getTokenCount() > 0) {
            return chunk.getTokenCount();
        }
        String content = chunk.getContent() != null ? chunk.getContent() : "";
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private List<Long> normalizePaperIds(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return List.of();
        }
        return paperIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }
}
```

- [ ] **Step 7: Run strategy tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=ContextStrategyServiceImplTest test
```

Expected: 4 tests pass.

- [ ] **Step 8: Record stage completion**

Update `docs/status/current.md`:

```text
单篇论文 FULL_TEXT_PARSED 阶段 1 已完成：新增 ContextStrategy 和 ContextStrategyService，可在单篇已解析论文且 token 不超预算时选择 FULL_TEXT_PARSED，其余情况回退 VECTOR_RAG；相关单元测试通过。
```

---

## Task 2: Full Text Context Builder

**Files:**
- Modify: `docs/status/current.md`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/context/FullTextContext.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/FullTextContextService.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/FullTextContextServiceImpl.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/FullTextContextServiceImplTest.java`

**Interfaces:**
- Produces: `FullTextContextService.buildContext(Long paperId): FullTextContext`.
- Produces: `FullTextContext` containing `paperId`, `paperTitle`, `contextText`, `sources`, `tokenCount`.
- Consumes: `PaperReferenceMapper`, `PaperSectionMapper`, `PaperChunkMapper`, existing `RagSource`.

- [ ] **Step 1: Record stage start**

Update `docs/status/current.md`:

```text
单篇论文 FULL_TEXT_PARSED 进入阶段 2：实现 FullTextContextService，从 MySQL 按章节组织单篇论文正文上下文，并默认排除 REFERENCES、BACK_MATTER、reference/noise chunk。
```

- [ ] **Step 2: Create `FullTextContext`**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/context/FullTextContext.java`:

```java
package com.myagent.assistant.rag.context;

import com.myagent.assistant.rag.dto.RagSource;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 单篇论文全文解析上下文。
 */
@Data
@AllArgsConstructor
public class FullTextContext {
    private Long paperId;
    private String paperTitle;
    private String contextText;
    private List<RagSource> sources;
    private Integer tokenCount;
}
```

- [ ] **Step 3: Create `FullTextContextService` interface**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/FullTextContextService.java`:

```java
package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.context.FullTextContext;

/**
 * 单篇论文全文解析上下文构造服务。
 */
public interface FullTextContextService {

    /**
     * 根据单篇论文 ID 构造按章节组织的正文上下文。
     */
    FullTextContext buildContext(Long paperId);
}
```

- [ ] **Step 4: Write `FullTextContextServiceImplTest`**

Create `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/FullTextContextServiceImplTest.java`:

```java
package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.rag.context.FullTextContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FullTextContextServiceImplTest {

    @Test
    void buildContextGroupsChunksBySectionAndFiltersNoise() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);

        PaperReference paper = new PaperReference();
        paper.setId(7L);
        paper.setTitle("Wind Paper");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);

        PaperChunk method = chunk(1L, 7L, 10L, "METHOD", "Proposed Method", 0, "method content", false, false, 10);
        PaperChunk experiment = chunk(2L, 7L, 20L, "EXPERIMENT", "Experiments", 1, "experiment content", false, false, 12);
        PaperChunk references = chunk(3L, 7L, 30L, "REFERENCES", "References", 2, "reference content", true, false, 8);
        PaperChunk backMatter = chunk(4L, 7L, 40L, "BACK_MATTER", "Funding", 3, "funding content", false, true, 8);

        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(method, experiment, references, backMatter));

        FullTextContextServiceImpl service = new FullTextContextServiceImpl(
                paperReferenceMapper,
                paperChunkMapper,
                60000,
                20
        );

        FullTextContext context = service.buildContext(7L);

        assertThat(context.getPaperId()).isEqualTo(7L);
        assertThat(context.getPaperTitle()).isEqualTo("Wind Paper");
        assertThat(context.getContextText()).contains("[METHOD] Proposed Method");
        assertThat(context.getContextText()).contains("method content");
        assertThat(context.getContextText()).contains("[EXPERIMENT] Experiments");
        assertThat(context.getContextText()).doesNotContain("reference content");
        assertThat(context.getContextText()).doesNotContain("funding content");
        assertThat(context.getSources()).hasSize(2);
        assertThat(context.getTokenCount()).isEqualTo(22);
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
        chunk.setChunkStrategyVersion("paper-structure-v1");
        return chunk;
    }
}
```

- [ ] **Step 5: Run full text context test and verify failure**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=FullTextContextServiceImplTest test
```

Expected: fails because implementation does not exist.

- [ ] **Step 6: Implement `FullTextContextServiceImpl`**

Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/FullTextContextServiceImpl.java`:

```java
package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.rag.context.FullTextContext;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.service.FullTextContextService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单篇论文全文解析上下文构造服务实现。
 */
@Service
public class FullTextContextServiceImpl implements FullTextContextService {

    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperChunkMapper paperChunkMapper;
    private final int fullTextBudgetTokens;
    private final int fullTextMaxSources;

    public FullTextContextServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                      PaperChunkMapper paperChunkMapper,
                                      @Value("${rag.full-text-budget-tokens:60000}") int fullTextBudgetTokens,
                                      @Value("${rag.full-text-max-sources:20}") int fullTextMaxSources) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperChunkMapper = paperChunkMapper;
        this.fullTextBudgetTokens = fullTextBudgetTokens;
        this.fullTextMaxSources = fullTextMaxSources;
    }

    @Override
    public FullTextContext buildContext(Long paperId) {
        if (paperId == null || paperId <= 0) {
            throw new RuntimeException("paperId 不能为空");
        }

        PaperReference paper = paperReferenceMapper.selectById(paperId);
        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }

        List<PaperChunk> chunks = paperChunkMapper.selectList(
                new QueryWrapper<PaperChunk>()
                        .eq("paper_id", paperId)
                        .orderByAsc("chunk_index")
        );

        List<PaperChunk> usableChunks = chunks.stream()
                .filter(this::isUsableFullTextChunk)
                .toList();

        if (usableChunks.isEmpty()) {
            throw new RuntimeException("当前文献没有可用于全文上下文的正文 chunk");
        }

        StringBuilder context = new StringBuilder();
        context.append("论文ID：").append(paperId).append("\n");
        context.append("论文标题：").append(nullToEmpty(paper.getTitle())).append("\n\n");

        Map<String, List<PaperChunk>> chunksBySection = groupBySection(usableChunks);
        List<RagSource> sources = new ArrayList<>();
        int usedTokens = 0;

        for (Map.Entry<String, List<PaperChunk>> entry : chunksBySection.entrySet()) {
            List<PaperChunk> sectionChunks = entry.getValue();
            if (sectionChunks.isEmpty()) {
                continue;
            }

            PaperChunk first = sectionChunks.get(0);
            String sectionType = nullToEmpty(first.getSectionType(), "UNKNOWN");
            String sectionTitle = nullToEmpty(first.getSectionTitle(), "Unknown");

            StringBuilder sectionText = new StringBuilder();
            int sectionTokens = 0;

            for (PaperChunk chunk : sectionChunks) {
                int chunkTokens = tokenCount(chunk);
                if (usedTokens + sectionTokens + chunkTokens > fullTextBudgetTokens) {
                    break;
                }
                sectionText.append(normalize(chunk.getContent())).append("\n\n");
                sectionTokens += chunkTokens;
                if (sources.size() < fullTextMaxSources) {
                    sources.add(toSource(chunk, paper));
                }
            }

            if (sectionText.isEmpty()) {
                break;
            }

            context.append("[").append(sectionType).append("] ").append(sectionTitle).append("\n");
            context.append(sectionText).append("\n");
            usedTokens += sectionTokens;
        }

        return new FullTextContext(
                paperId,
                paper.getTitle(),
                context.toString().trim(),
                sources,
                usedTokens
        );
    }

    private Map<String, List<PaperChunk>> groupBySection(List<PaperChunk> chunks) {
        Map<String, List<PaperChunk>> result = new LinkedHashMap<>();
        for (PaperChunk chunk : chunks) {
            String key = chunk.getSectionId() != null
                    ? "section:" + chunk.getSectionId()
                    : "chunk:" + chunk.getChunkIndex();
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(chunk);
        }
        return result;
    }

    private RagSource toSource(PaperChunk chunk, PaperReference paper) {
        RagSource source = new RagSource();
        source.setPaperId(chunk.getPaperId());
        source.setPaperTitle(paper.getTitle());
        source.setChunkId(chunk.getId());
        source.setChunkIndex(chunk.getChunkIndex());
        source.setSectionId(chunk.getSectionId());
        source.setSectionTitle(chunk.getSectionTitle());
        source.setSectionType(chunk.getSectionType());
        source.setIsReference(chunk.getIsReference());
        source.setIsNoise(chunk.getIsNoise());
        source.setChunkStrategyVersion(chunk.getChunkStrategyVersion());
        source.setScore(null);
        source.setContent(chunk.getContent());
        source.setRetrievalRoute("full_text");
        return source;
    }

    private boolean isUsableFullTextChunk(PaperChunk chunk) {
        if (chunk == null) {
            return false;
        }
        if (Boolean.TRUE.equals(chunk.getIsReference()) || Boolean.TRUE.equals(chunk.getIsNoise())) {
            return false;
        }
        String sectionType = chunk.getSectionType();
        return !"REFERENCES".equals(sectionType) && !"BACK_MATTER".equals(sectionType);
    }

    private int tokenCount(PaperChunk chunk) {
        if (chunk.getTokenCount() != null && chunk.getTokenCount() > 0) {
            return chunk.getTokenCount();
        }
        String content = chunk.getContent() != null ? chunk.getContent() : "";
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("\\s+", " ").trim();
    }

    private String nullToEmpty(String value) {
        return nullToEmpty(value, "");
    }

    private String nullToEmpty(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
```

- [ ] **Step 7: Run full text context test**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=FullTextContextServiceImplTest test
```

Expected: 1 test passes.

- [ ] **Step 8: Record stage completion**

Update `docs/status/current.md`:

```text
单篇论文 FULL_TEXT_PARSED 阶段 2 已完成：新增 FullTextContextService，可按章节组织单篇论文正文上下文，默认排除 REFERENCES、BACK_MATTER、reference/noise chunk，并返回 full_text sources；相关单元测试通过。
```

---

## Task 3: Prompt and Response Integration

**Files:**
- Modify: `docs/status/current.md`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/RagPromptService.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagPromptServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/dto/RagChatResponse.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagChatServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/RagChatServiceImplTest.java`

**Interfaces:**
- Consumes: `ContextStrategyService`, `FullTextContextService`, `FullTextContext`.
- Produces: `RagPromptService.buildFullTextPrompt(String question, FullTextContext context)`.
- Produces: `RagChatResponse.contextStrategy`, `contextTokenCount`, `contextPaperIds`.
- Updates: `RagChatServiceImpl.chat` strategy branch.

- [ ] **Step 1: Record stage start**

Update `docs/status/current.md`:

```text
单篇论文 FULL_TEXT_PARSED 进入阶段 3：接入 RAG 问答流程，新增全文上下文 prompt 和响应中的 contextStrategy/contextTokenCount/contextPaperIds 调试字段。
```

- [ ] **Step 2: Extend `RagPromptService`**

Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/RagPromptService.java`:

```java
package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.context.FullTextContext;
import com.myagent.assistant.rag.dto.RagSource;

import java.util.List;

/**
 * RAG Prompt 构造服务。
 */
public interface RagPromptService {

    String buildPrompt(String question, List<RagSource> sources);

    String buildFullTextPrompt(String question, FullTextContext context);
}
```

- [ ] **Step 3: Implement full text prompt**

Add to `RagPromptServiceImpl`:

```java
    @Override
    public String buildFullTextPrompt(String question, FullTextContext context) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("问题不能为空");
        }
        if (context == null || context.getContextText() == null || context.getContextText().isBlank()) {
            throw new RuntimeException("全文上下文不能为空");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个论文/文献 AI 研究助手。\n\n");
        prompt.append("请根据下面给出的“按章节组织的论文正文上下文”回答用户问题。\n");
        prompt.append("如果上下文中没有足够信息，请明确说明，不要编造。\n\n");
        prompt.append("用户问题：\n").append(question).append("\n\n");
        prompt.append("论文全文上下文：\n").append(context.getContextText()).append("\n\n");
        prompt.append("回答要求：\n");
        prompt.append("1. 用中文回答。\n");
        prompt.append("2. 优先从整篇论文角度总结。\n");
        prompt.append("3. 如果问题是论文讲什么、创新点、整体方法、优缺点，请综合摘要、引言、方法、实验、结果和结论。\n");
        prompt.append("4. 不要使用 References、Author Contributions、Funding、Conflict of Interest、Data Availability 等后置内容作为正文结论。\n");
        prompt.append("5. 如果引用具体依据，可以用章节名说明，例如“根据 Method 章节”或“根据 Experiments 章节”。\n");
        return prompt.toString();
    }
```

Also improve existing VECTOR_RAG source metadata in `buildPrompt`: after `Chunk Index` line add:

```java
                prompt.append("章节类型：").append(nullToEmpty(source.getSectionType())).append("\n");
                prompt.append("章节标题：").append(nullToEmpty(source.getSectionTitle())).append("\n");
```

- [ ] **Step 4: Extend `RagChatResponse`**

Add fields to `RagChatResponse`:

```java
    /**
     * 本轮使用的上下文策略：VECTOR_RAG / FULL_TEXT_PARSED。
     */
    private String contextStrategy;

    /**
     * 本轮上下文估算 token 数。
     */
    private Integer contextTokenCount;

    /**
     * 本轮上下文实际使用的论文 ID。
     */
    private List<Long> contextPaperIds;
```

- [ ] **Step 5: Update `RagChatServiceImpl` constructor and branch logic**

Add imports:

```java
import com.myagent.assistant.rag.context.ContextStrategy;
import com.myagent.assistant.rag.context.FullTextContext;
import com.myagent.assistant.rag.service.ContextStrategyService;
import com.myagent.assistant.rag.service.FullTextContextService;
```

Add fields:

```java
    private final ContextStrategyService contextStrategyService;
    private final FullTextContextService fullTextContextService;
```

Update constructor to accept the two services.

In `chat`, after resolving `effectivePaperIds`, choose strategy:

```java
        ContextStrategy contextStrategy = contextStrategyService.chooseStrategy(effectivePaperIds);
        List<RagSource> sources;
        String prompt;
        Integer contextTokenCount;
        List<Long> contextPaperIds;

        if (ContextStrategy.FULL_TEXT_PARSED.equals(contextStrategy)) {
            Long paperId = effectivePaperIds.get(0);
            FullTextContext fullTextContext = fullTextContextService.buildContext(paperId);
            sources = fullTextContext.getSources();
            prompt = ragPromptService.buildFullTextPrompt(request.getQuestion(), fullTextContext);
            contextTokenCount = fullTextContext.getTokenCount();
            contextPaperIds = List.of(paperId);
        } else {
            sources = ragRetrievalService.retrieveSources(request.getQuestion(), topK, effectivePaperIds);
            prompt = ragPromptService.buildPrompt(request.getQuestion(), sources);
            contextTokenCount = sources == null ? 0 : sources.stream()
                    .map(RagSource::getContent)
                    .filter(content -> content != null && !content.isBlank())
                    .mapToInt(content -> Math.max(1, (int) Math.ceil(content.length() / 4.0)))
                    .sum();
            contextPaperIds = effectivePaperIds;
        }
```

Remove the old unconditional retrieval and prompt construction lines.

Set response fields:

```java
        response.setContextStrategy(contextStrategy.name());
        response.setContextTokenCount(contextTokenCount);
        response.setContextPaperIds(contextPaperIds);
```

- [ ] **Step 6: Update `RagChatServiceImplTest`**

Modify existing test setup to mock new dependencies. Add a new test:

```java
    @Test
    void chatUsesFullTextContextWhenStrategySelectsFullText() {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        RagPromptService ragPromptService = mock(RagPromptService.class);
        LlmService llmService = mock(LlmService.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        IdeaSuggestionService ideaSuggestionService = mock(IdeaSuggestionService.class);
        ContextStrategyService contextStrategyService = mock(ContextStrategyService.class);
        FullTextContextService fullTextContextService = mock(FullTextContextService.class);

        RagSource source = new RagSource();
        source.setPaperId(7L);
        source.setChunkId(1L);
        source.setContent("method content");
        source.setRetrievalRoute("full_text");

        FullTextContext fullTextContext = new FullTextContext(
                7L,
                "Wind Paper",
                "[METHOD] Method\nmethod content",
                List.of(source),
                100
        );

        when(contextStrategyService.chooseStrategy(List.of(7L))).thenReturn(ContextStrategy.FULL_TEXT_PARSED);
        when(fullTextContextService.buildContext(7L)).thenReturn(fullTextContext);
        when(ragPromptService.buildFullTextPrompt("这篇论文讲了什么？", fullTextContext)).thenReturn("full text prompt");
        when(llmService.generateAnswer("full text prompt")).thenReturn("全文回答");
        when(llmService.provider()).thenReturn("qwen");
        when(llmService.modelName()).thenReturn("qwen-plus");
        when(chatHistoryService.saveRagChat(null, 7L, "这篇论文讲了什么？", "全文回答", "qwen", "qwen-plus", List.of(source))).thenReturn(99L);
        when(ideaSuggestionService.shouldSuggestSaveAsIdea("这篇论文讲了什么？", "全文回答", List.of(source))).thenReturn(false);
        when(ideaSuggestionService.buildSuggestionReason("这篇论文讲了什么？", "全文回答", List.of(source))).thenReturn(null);

        RagChatServiceImpl service = new RagChatServiceImpl(
                ragRetrievalService,
                ragPromptService,
                llmService,
                chatHistoryService,
                ideaSuggestionService,
                contextStrategyService,
                fullTextContextService
        );

        RagChatRequest request = new RagChatRequest();
        request.setQuestion("这篇论文讲了什么？");
        request.setPaperIds(List.of(7L));

        RagChatResponse response = service.chat(request);

        assertThat(response.getAnswer()).isEqualTo("全文回答");
        assertThat(response.getContextStrategy()).isEqualTo("FULL_TEXT_PARSED");
        assertThat(response.getContextTokenCount()).isEqualTo(100);
        assertThat(response.getContextPaperIds()).containsExactly(7L);
        verify(ragRetrievalService, never()).retrieveSources(any(), any(), any());
    }
```

Add imports as needed:

```java
import com.myagent.assistant.rag.context.ContextStrategy;
import com.myagent.assistant.rag.context.FullTextContext;
import com.myagent.assistant.rag.service.ContextStrategyService;
import com.myagent.assistant.rag.service.FullTextContextService;
import static org.mockito.Mockito.never;
```

- [ ] **Step 7: Run RAG chat tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=RagChatServiceImplTest test
```

Expected: tests pass.

- [ ] **Step 8: Record stage completion**

Update `docs/status/current.md`:

```text
单篇论文 FULL_TEXT_PARSED 阶段 3 已完成：RAG 问答已接入上下文策略，单篇论文在预算内会使用按章节组织的全文解析上下文，响应返回 contextStrategy、contextTokenCount、contextPaperIds；相关单元测试通过。
```

---

## Task 4: Final Verification, Notes, and Commit

**Files:**
- Modify: `docs/status/current.md`
- Modify: `docs/archive/notes/rag-improvement-notes.md`
- All changed backend files.

**Interfaces:**
- Verifies full backend test suite.
- Verifies `/api/rag/chat` returns `contextStrategy=FULL_TEXT_PARSED` for a single parsed paper.
- Produces Git commit for this stage.

- [ ] **Step 1: Record final verification start**

Update `docs/status/current.md`:

```text
单篇论文 FULL_TEXT_PARSED 进入阶段 4：进行后端整体测试、接口验证、改进效果记录和 Git 版本提交。
```

- [ ] **Step 2: Run full backend tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

Expected: all tests pass.

- [ ] **Step 3: Verify API manually with curl or Apifox**

Use current parsed/vectorized paper id `7` if still present locally.

Run with curl if backend is running:

```bash
curl -s -X POST http://localhost:8080/api/rag/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"这篇论文主要讲了什么？","paperIds":[7],"topK":5}'
```

Expected response facts:

```text
code = 200
contextStrategy = FULL_TEXT_PARSED
contextPaperIds contains 7
contextTokenCount > 0
answer is generated by configured LLM
prompt contains “论文全文上下文” and chapter blocks
```

If Bash Chinese JSON encoding causes issues, use Apifox with the same JSON body.

- [ ] **Step 4: Update `docs/archive/notes/rag-improvement-notes.md`**

Append a section:

```markdown
## 11. 已落地改进记录：单篇 FULL_TEXT_PARSED

更新时间：2026-07-06

### 相比 paper-structure-v1 的改进

paper-structure-v1 解决了参考文献误召回和 chunk 缺少章节元数据的问题，但宏观问题仍然只看 topK 局部片段。FULL_TEXT_PARSED 在单篇论文、token 不超预算时，直接按章节组织较完整正文上下文，让模型能综合 Abstract、Introduction、Method、Experiment、Result、Conclusion 回答。

### 解决的问题

- 改善“这篇论文主要讲了什么？”这类整篇概括问题。
- 改善“这篇论文的创新点是什么？”这类需要综合引言、方法、实验和结论的问题。
- 减少仅靠局部 topK chunk 导致回答片面的情况。
- 保留 REFERENCES / BACK_MATTER 默认排除策略，避免全文上下文引入参考文献和作者贡献等低价值内容。

### 验证结果

记录本次实际测试结果：

```text
contextStrategy = FULL_TEXT_PARSED
contextPaperIds = [7]
contextTokenCount = <实际值>
sourceCount = <实际值>
```

### 当前仍存在的问题

- 只支持单篇论文全文上下文，多篇论文比较仍回退 VECTOR_RAG。
- 超过 token 预算的长论文仍回退 VECTOR_RAG。
- 图表、公式、表格结构仍依赖 PDFBox 文本结果，未做多模态理解。
- 仍缺少文献画像、章节摘要和多篇 HYBRID 对比能力。
```

Replace `<实际值>` with actual response data.

- [ ] **Step 5: Update final progress**

Update `docs/status/current.md` with actual verification results:

```text
单篇论文 FULL_TEXT_PARSED 已完成：当用户选择 1 篇已解析论文且正文 token 不超预算时，RAG 问答会使用按章节组织的全文解析上下文；否则回退 VECTOR_RAG。响应已返回 contextStrategy、contextTokenCount、contextPaperIds。最终验证：后端 ./mvnw test 通过（X 个测试，0 失败）；/api/rag/chat 单篇论文验证返回 contextStrategy=FULL_TEXT_PARSED、contextTokenCount=XXX、sourceCount=XXX。改进效果和当前遗留问题已记录到 docs/archive/notes/rag-improvement-notes.md。
```

- [ ] **Step 6: Inspect git diff**

Run:

```bash
git status --short
git diff --stat
```

Expected: only planned backend and docs files changed.

- [ ] **Step 7: Commit**

Run:

```bash
git add backend/research-assistant-backend/src/main/java backend/research-assistant-backend/src/test/java docs/status/current.md docs/archive/notes/rag-improvement-notes.md docs/archive/plans/2026-07-06-single-paper-full-text-context.md
git commit -m "Add single paper full text RAG context"
```

Commit body must include:

```text
Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## Plan Self-Review

### Spec coverage

- `ContextStrategy` / `ContextStrategyService`: Task 1.
- `FullTextContextService`: Task 2.
- FULL_TEXT_PARSED prompt: Task 3.
- `RagChatResponse` context fields: Task 3.
- `RagChatServiceImpl` strategy branch: Task 3.
- Tests: Tasks 1-3.
- Progress and improvement notes: all tasks plus Task 4.
- Final verification and commit: Task 4.

### Scope deliberately excluded

- Multi-paper FULL_TEXT, HYBRID, paper profile, section summary, claim cards, reranker, front-end UI redesign, and direct PDF LLM reading remain outside this stage.

### Type consistency

- Strategy enum values are exactly `VECTOR_RAG` and `FULL_TEXT_PARSED`.
- Response fields are `contextStrategy`, `contextTokenCount`, and `contextPaperIds`.
- Full text retrieval route is `full_text`.
- Config keys are `rag.full-text-budget-tokens` and `rag.full-text-max-sources`.
