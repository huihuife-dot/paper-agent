# 多篇论文 HYBRID_RAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为用户选中 2 篇及以上且已生成文献画像的论文问答新增 `HYBRID_RAG` 上下文策略，组合 `paper_profile`、`paper_section_summary` 和少量 raw chunks，实现逐篇分析 + 横向比较。

**Architecture:** `RagChatServiceImpl` 继续做主编排；`ContextStrategyService` 负责策略选择；新增 `HybridRagContextService` 负责多篇混合上下文构造；`RagPromptService` 新增 HYBRID_RAG prompt。缺失画像不自动生成，直接回退 `VECTOR_RAG`。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、JUnit 5、Mockito、AssertJ、MySQL、Qdrant；继续复用 provider-neutral `LlmService`，不引入 Claude/Anthropic SDK。

## Global Constraints

- 只实现规则型第一版 HYBRID_RAG，不新增 Qdrant 多类型索引，不重建 collection。
- 多篇任一论文缺失 `paper-profile-v1` 时回退 `VECTOR_RAG`，不在问答时自动生成画像。
- 不新增前端页面；前端继续使用已有 `paperIds` 多选问答能力。
- HYBRID_RAG 上下文来源固定为 `paper_profile`、`section_summary`、`raw_chunk`。
- 每篇论文固定上限：1 条 profile、最多 4 条 section_summary、最多 2 条 raw_chunk。
- token 估算沿用项目现有 `ceil(text.length / 4.0)`，不接入 provider-specific token counting API。
- 每个实质阶段完成后更新 `docs/status/current.md`。

---

## File Structure

### Create

- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/context/HybridRagContext.java`
  - HYBRID_RAG 构造结果：`contextText`、`sources`、`tokenCount`、`paperIds`。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/context/HybridRagContextRequest.java`
  - HYBRID_RAG 构造输入：`question`、`paperIds`、`rawChunkSources`、`maxSectionSummariesPerPaper`、`maxRawChunksPerPaper`。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/HybridRagContextService.java`
  - 上下文构造接口：`HybridRagContext buildContext(HybridRagContextRequest request)`。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/HybridRagContextServiceImpl.java`
  - 查询 profile/summary，按问题规则选择章节摘要，按论文组织上下文和 sources。
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/HybridRagContextServiceImplTest.java`
  - 单元测试 HYBRID_RAG 上下文构造、summary 选择和数量上限。

### Modify

- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/context/ContextStrategy.java`
  - 新增 `HYBRID_RAG`。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/ContextStrategyServiceImpl.java`
  - 注入 `PaperProfileMapper`，多篇画像齐全时选择 `HYBRID_RAG`。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/dto/RagSource.java`
  - 新增 `sourceType`、`sectionSummaryId`、`summaryVersion`、`profileId`、`profileVersion`。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/RagPromptService.java`
  - 新增 `String buildHybridPrompt(String question, HybridRagContext context)`。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagPromptServiceImpl.java`
  - 实现 HYBRID_RAG prompt。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagChatServiceImpl.java`
  - 注入 `HybridRagContextService`，新增 `HYBRID_RAG` 分支。
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/ContextStrategyServiceImplTest.java`
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/RagPromptServiceImplTest.java`
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/RagChatServiceImplTest.java`
- `docs/status/current.md`

---

### Task 1: 策略枚举和选择规则

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/context/ContextStrategy.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/ContextStrategyServiceImpl.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/ContextStrategyServiceImplTest.java`

**Interfaces:**
- Consumes: `PaperProfileMapper.selectOne(QueryWrapper<PaperProfile>)`
- Produces: `ContextStrategy.HYBRID_RAG`

- [ ] **Step 1: Write failing tests**

Add tests:

```java
@Test
void chooseHybridRagForMultiplePapersWhenProfilesExist() {
    PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
    PaperProfile profile = new PaperProfile();
    profile.setProfileVersion("paper-profile-v1");
    when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(profile);

    ContextStrategyServiceImpl service = new ContextStrategyServiceImpl(
            mock(PaperReferenceMapper.class),
            mock(PaperChunkMapper.class),
            paperProfileMapper,
            60000
    );

    assertThat(service.chooseStrategy(List.of(7L, 8L))).isEqualTo(ContextStrategy.HYBRID_RAG);
}

@Test
void chooseVectorRagForMultiplePapersWhenAnyProfileMissing() {
    PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
    PaperProfile profile = new PaperProfile();
    when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(profile, null);

    ContextStrategyServiceImpl service = new ContextStrategyServiceImpl(
            mock(PaperReferenceMapper.class),
            mock(PaperChunkMapper.class),
            paperProfileMapper,
            60000
    );

    assertThat(service.chooseStrategy(List.of(7L, 8L))).isEqualTo(ContextStrategy.VECTOR_RAG);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend/research-assistant-backend
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=ContextStrategyServiceImplTest test
```

Expected: FAIL because constructor signature and `HYBRID_RAG` enum do not exist yet.

- [ ] **Step 3: Implement minimal strategy changes**

Update enum:

```java
public enum ContextStrategy {
    VECTOR_RAG,
    FULL_TEXT_PARSED,
    HYBRID_RAG
}
```

Update constructor and multi-paper branch:

```java
private static final String PAPER_PROFILE_VERSION = "paper-profile-v1";
private final PaperProfileMapper paperProfileMapper;

public ContextStrategyServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                  PaperChunkMapper paperChunkMapper,
                                  PaperProfileMapper paperProfileMapper,
                                  @Value("${rag.full-text-budget-tokens:60000}") int fullTextBudgetTokens) {
    this.paperReferenceMapper = paperReferenceMapper;
    this.paperChunkMapper = paperChunkMapper;
    this.paperProfileMapper = paperProfileMapper;
    this.fullTextBudgetTokens = fullTextBudgetTokens;
}

@Override
public ContextStrategy chooseStrategy(List<Long> paperIds) {
    List<Long> normalizedPaperIds = normalizePaperIds(paperIds);
    if (normalizedPaperIds.size() >= 2) {
        return hasProfiles(normalizedPaperIds) ? ContextStrategy.HYBRID_RAG : ContextStrategy.VECTOR_RAG;
    }
    if (normalizedPaperIds.size() != 1) {
        return ContextStrategy.VECTOR_RAG;
    }
    // keep existing single-paper FULL_TEXT_PARSED logic
}

private boolean hasProfiles(List<Long> paperIds) {
    for (Long paperId : paperIds) {
        PaperProfile profile = paperProfileMapper.selectOne(new QueryWrapper<PaperProfile>()
                .eq("paper_id", paperId)
                .eq("profile_version", PAPER_PROFILE_VERSION));
        if (profile == null) {
            return false;
        }
    }
    return true;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run same command. Expected: PASS.

- [ ] **Step 5: Update progress**

Add one concise line in `docs/status/current.md`: HYBRID_RAG 阶段 1 已完成，策略枚举和多篇画像齐全选择规则通过测试。

---

### Task 2: HybridRagContextService 上下文构造

**Files:**
- Create: `HybridRagContext.java`
- Create: `HybridRagContextRequest.java`
- Create: `HybridRagContextService.java`
- Create: `HybridRagContextServiceImpl.java`
- Modify: `RagSource.java`
- Test: `HybridRagContextServiceImplTest.java`

**Interfaces:**
- Consumes: `PaperProfileMapper`、`PaperSectionSummaryMapper`、`PaperReferenceMapper` 和 raw `List<RagSource>`
- Produces: `HybridRagContext buildContext(HybridRagContextRequest request)`

- [ ] **Step 1: Extend RagSource**

Add fields:

```java
private String sourceType;
private Long sectionSummaryId;
private String summaryVersion;
private Long profileId;
private String profileVersion;
```

Use values: `paper_profile`、`section_summary`、`raw_chunk`、`full_text`。

- [ ] **Step 2: Create context DTOs**

`HybridRagContext.java`:

```java
@Data
@AllArgsConstructor
public class HybridRagContext {
    private String contextText;
    private List<RagSource> sources;
    private Integer tokenCount;
    private List<Long> paperIds;
}
```

`HybridRagContextRequest.java`:

```java
@Data
@AllArgsConstructor
public class HybridRagContextRequest {
    private String question;
    private List<Long> paperIds;
    private List<RagSource> rawChunkSources;
    private Integer maxSectionSummariesPerPaper;
    private Integer maxRawChunksPerPaper;
}
```

- [ ] **Step 3: Write failing tests**

Test cases:

```java
@Test
void buildContextIncludesProfilesForEachPaperInInputOrder() {
    // mock paper 7 and 8, mock profile 7 and 8
    // call buildContext("对比方法", [7,8], empty raw sources)
    // assert context contains [Paper 7] before [Paper 8]
    // assert sources have sourceType paper_profile for 7 and 8
}

@Test
void methodQuestionPrioritizesMethodSummaryAndLimitsCount() {
    // mock METHOD, EXPERIMENT, INTRODUCTION, RESULT, DISCUSSION summaries
    // question contains "方法"
    // maxSectionSummariesPerPaper = 2
    // assert METHOD is included and total section_summary sources for paper is 2
}

@Test
void rawChunkSourcesAreGroupedAndLimitedPerPaper() {
    // pass 3 raw sources for paper 7 and 1 raw source for paper 8
    // maxRawChunksPerPaper = 2
    // assert paper 7 only has 2 raw_chunk sources
}
```

- [ ] **Step 4: Run test to verify it fails**

Run:

```bash
cd backend/research-assistant-backend
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=HybridRagContextServiceImplTest test
```

Expected: FAIL because service classes do not exist.

- [ ] **Step 5: Implement service**

Core constants:

```java
private static final String PAPER_PROFILE_VERSION = "paper-profile-v1";
private static final String SECTION_SUMMARY_VERSION = "section-summary-v1";
private static final int DEFAULT_MAX_SECTION_SUMMARIES_PER_PAPER = 4;
private static final int DEFAULT_MAX_RAW_CHUNKS_PER_PAPER = 2;
```

Implementation requirements:

```java
public HybridRagContext buildContext(HybridRagContextRequest request) {
    // validate question and paperIds
    // normalize paperIds: positive distinct, preserve order
    // for each paperId:
    //   select paper_reference
    //   select paper_profile where profile_version = paper-profile-v1
    //   select paper_section_summary where summary_version = section-summary-v1 order by section_id
    //   choose relevant summaries by keyword rules
    //   choose raw sources from request.rawChunkSources grouped by paperId, sorted by score desc
    //   append [Paper {id}] {title}, profile fields, summaries, raw excerpts
    //   add RagSource entries with sourceType
    // return contextText, sources, estimated tokens, paperIds
}
```

Keyword priority rules:

```java
// 方法类：METHOD, RELATED_WORK, INTRODUCTION
// 实验结果类：EXPERIMENT, RESULT, DISCUSSION
// 优缺点/局限：DISCUSSION, CONCLUSION, RESULT, EXPERIMENT
// 创新贡献：ABSTRACT, INTRODUCTION, METHOD, CONCLUSION
// 对比综述：METHOD, EXPERIMENT, RESULT, DISCUSSION, CONCLUSION
// 兜底：ABSTRACT, INTRODUCTION, METHOD, EXPERIMENT, RESULT, CONCLUSION
```

Use helper methods: `normalizePaperIds`、`selectRelevantSummaries`、`priorityOf`、`appendProfile`、`appendSummaries`、`appendRawChunks`、`estimateTokens`、`clip`。

- [ ] **Step 6: Run test to verify it passes**

Run same command. Expected: PASS.

- [ ] **Step 7: Update progress**

Add one concise line in `docs/status/current.md`: HYBRID_RAG 阶段 2 已完成，上下文构造服务可按论文组织 profile、summary 和 raw chunks。

---

### Task 3: RAG chat / prompt 接入

**Files:**
- Modify: `RagPromptService.java`
- Modify: `RagPromptServiceImpl.java`
- Modify: `RagChatServiceImpl.java`
- Test: `RagPromptServiceImplTest.java`
- Test: `RagChatServiceImplTest.java`

**Interfaces:**
- Consumes: `HybridRagContextService.buildContext(HybridRagContextRequest)`
- Produces: `/api/rag/chat` response with `contextStrategy=HYBRID_RAG`、`contextTokenCount`、`contextPaperIds`、hybrid sources

- [ ] **Step 1: Write prompt failing test**

Add to `RagPromptServiceImplTest`:

```java
@Test
void buildHybridPromptRequiresPerPaperAnalysisAndComparison() {
    RagPromptServiceImpl service = new RagPromptServiceImpl();
    HybridRagContext context = new HybridRagContext(
            "[Paper 7] A\n一、文献画像\n方法概述：A method\n\n[Paper 8] B\n一、文献画像\n方法概述：B method",
            List.of(),
            200,
            List.of(7L, 8L)
    );

    String prompt = service.buildHybridPrompt("这两篇论文的方法有什么区别？", context);

    assertThat(prompt).contains("多篇论文的混合上下文");
    assertThat(prompt).contains("先逐篇分析每篇论文，再做横向比较");
    assertThat(prompt).contains("不要把 A 论文的信息归到 B 论文");
    assertThat(prompt).contains("这两篇论文的方法有什么区别？");
}
```

- [ ] **Step 2: Implement prompt interface and method**

Interface:

```java
String buildHybridPrompt(String question, HybridRagContext context);
```

Prompt body must include:

```text
你是论文/文献 AI 研究助手。下面提供多篇论文的混合上下文，包括文献画像、章节摘要和少量原文证据。

请遵守：
1. 先逐篇分析每篇论文，再做横向比较。
2. 不要把 A 论文的信息归到 B 论文。
3. 优先依据 paper_profile 和 section_summary 形成整体判断。
4. 涉及具体论据时参考 raw_chunk。
5. 如果某篇论文某项信息不足，请明确说明“信息不足”。
6. 回答用户问题，不要泛泛复述上下文。
```

- [ ] **Step 3: Write chat failing test**

Add to `RagChatServiceImplTest`:

```java
@Test
void chatUsesHybridContextWhenStrategySelectsHybridRag() {
    // mock all existing collaborators plus HybridRagContextService
    // contextStrategyService.chooseStrategy([7,8]) returns HYBRID_RAG
    // ragRetrievalService.retrieveSources(question, topK, [7,8]) returns raw sources
    // hybridRagContextService.buildContext(...) returns HybridRagContext
    // ragPromptService.buildHybridPrompt(...) returns "hybrid prompt"
    // llmService.generateAnswer("hybrid prompt") returns "hybrid answer"
    // assert response contextStrategy HYBRID_RAG, contextPaperIds [7,8], sources hybrid sources
    // verify buildPrompt/buildFullTextPrompt not called
}
```

- [ ] **Step 4: Update RagChatServiceImpl constructor and branch**

Inject:

```java
private final HybridRagContextService hybridRagContextService;
```

Branch:

```java
} else if (ContextStrategy.HYBRID_RAG.equals(contextStrategy)) {
    List<RagSource> rawSources = ragRetrievalService.retrieveSources(request.getQuestion(), topK, effectivePaperIds);
    HybridRagContext hybridContext = hybridRagContextService.buildContext(new HybridRagContextRequest(
            request.getQuestion(),
            effectivePaperIds,
            rawSources,
            4,
            2
    ));
    sources = hybridContext.getSources();
    prompt = ragPromptService.buildHybridPrompt(request.getQuestion(), hybridContext);
    contextTokenCount = hybridContext.getTokenCount();
    contextPaperIds = hybridContext.getPaperIds();
} else {
    // existing VECTOR_RAG branch
}
```

- [ ] **Step 5: Run focused tests**

Run:

```bash
cd backend/research-assistant-backend
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=RagPromptServiceImplTest,RagChatServiceImplTest test
```

Expected: PASS.

- [ ] **Step 6: Update progress**

Add one concise line in `docs/status/current.md`: HYBRID_RAG 阶段 3 已完成，RAG chat 和 prompt 已接入混合上下文分支。

---

### Task 4: 整体测试与 Apifox 验证建议

**Files:**
- Modify: `docs/status/current.md`
- Optional modify: `docs/archive/notes/rag-improvement-notes.md` if manual comparison shows clear improvement or limitation.

**Interfaces:**
- Consumes: completed Tasks 1-3
- Produces: verified backend state and user-facing Apifox validation checklist

- [ ] **Step 1: Run full backend test suite**

Run:

```bash
cd backend/research-assistant-backend
JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

Expected: all tests PASS.

- [ ] **Step 2: Compile if tests pass**

Run:

```bash
cd backend/research-assistant-backend
JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Start backend for manual validation**

Run in a terminal with the same JDK 21:

```bash
cd backend/research-assistant-backend
JAVA_HOME=/path/to/jdk-21 ./mvnw spring-boot:run
```

Expected: backend starts on configured port.

- [ ] **Step 4: Apifox prepare profiles**

For at least two parsed/vectorized papers, call:

```http
POST /api/papers/7/profile
POST /api/papers/8/profile
```

Expected: both return `code=200` and profile data.

- [ ] **Step 5: Apifox verify HYBRID_RAG**

Request:

```http
POST /api/rag/chat
Content-Type: application/json

{
  "question": "这两篇论文的方法有什么区别？",
  "paperIds": [7, 8],
  "topK": 5
}
```

Expected:

```text
code = 200
contextStrategy = HYBRID_RAG
contextPaperIds contains 7 and 8
sources contains sourceType = paper_profile or section_summary
answer contains per-paper analysis and comparison
```

- [ ] **Step 6: Apifox verify fallback**

Use one paper that has no `paper-profile-v1` or temporarily pick a missing profile paper:

```json
{
  "question": "这两篇论文的方法有什么区别？",
  "paperIds": [7, 9999],
  "topK": 5
}
```

Expected: no auto profile generation; if valid paper scope still enters retrieval, response uses `contextStrategy=VECTOR_RAG` or returns existing paper validation error according to current retrieval behavior. Do not silently create profile.

- [ ] **Step 7: Update final progress**

Record concise final status in `docs/status/current.md`:

```text
多篇论文 HYBRID_RAG 已完成：多篇画像齐全时使用 paper_profile + section_summary + raw chunks 构造均衡上下文；画像缺失时回退 VECTOR_RAG；/api/rag/chat 返回 contextStrategy=HYBRID_RAG、contextTokenCount、contextPaperIds 和 hybrid sources。最终验证：后端 ./mvnw test 通过；Apifox 多篇比较问答返回 HYBRID_RAG。
```

- [ ] **Step 8: Optional commit only when user asks**

```bash
git add backend/research-assistant-backend docs/status/current.md docs/archive/notes/rag-improvement-notes.md
git commit -m "Add hybrid RAG for multi-paper comparison

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Self-Review

- Spec coverage: covers strategy enum, profile-complete HYBRID_RAG selection, missing-profile fallback, context construction from profile/summary/raw chunks, prompt, response debug fields, unit tests, full backend verification, and Apifox checks.
- Out of scope preserved: no Qdrant rebuild, no profile/summary vector index, no query analyzer, no reranker, no frontend page.
- Type consistency: `HybridRagContext`、`HybridRagContextRequest`、`HybridRagContextService.buildContext(...)` names are used consistently across tasks.
