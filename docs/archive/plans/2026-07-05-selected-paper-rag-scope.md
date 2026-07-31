# Selected Paper RAG Scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `/chat` selected reference papers actually restrict backend RAG retrieval to those papers, while keeping no-selection behavior as all-paper retrieval.

**Architecture:** Add a provider-neutral `paperIds` scope from frontend request payload through `RagChatRequest`, `RagChatServiceImpl`, `RagRetrievalService`, and `QdrantService`. Use Qdrant payload filtering on `paperId` so retrieval is constrained before MySQL chunk hydration and prompt construction.

**Tech Stack:** Spring Boot 3.5.14, Java 21, MyBatis-Plus, Qdrant REST API, Vue 3, Vite 5, Axios, Node test runner.

## Global Constraints

- Project is not a Git repository; do not include `git commit` steps.
- Backend compile and tests require JDK 21; use `JAVA_HOME=C:/path/to/jdk-21` when running Maven from this environment.
- Keep existing `paperId` request field for backward compatibility with old single-paper callers.
- New `paperIds` request field takes precedence over `paperId` when it contains valid positive IDs.
- Empty or missing `paperIds` means search all papers.
- When selected papers are provided, do not silently fall back to all-paper retrieval if scoped results are weak or empty.
- Preserve existing cross-language dual-route retrieval: original query and rewritten query both use the same paper scope.
- Keep project progress in `docs/status/current.md` after implementation and verification.

---

## File Structure

- Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/dto/RagChatRequest.java`
  - Add `List<Long> paperIds` with comments.
- Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/RagRetrievalService.java`
  - Add overloaded retrieval method accepting `List<Long> paperIds`.
- Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagRetrievalServiceImpl.java`
  - Normalize scoped paper IDs, pass scope to both original and rewritten searches.
- Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/qdrant/service/QdrantService.java`
  - Add overloaded raw search method accepting `List<Long> paperIds`, and include Qdrant payload filter only when scope is non-empty.
- Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagChatServiceImpl.java`
  - Resolve effective paper scope from `paperIds` or legacy `paperId`, pass to retrieval, and keep chat history `paperId` as single paper only when exactly one paper is scoped.
- Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/controller/RagController.java`
  - Add optional `paperIds` query parameter for `/api/rag/sources` debugging.
- Create `backend/research-assistant-backend/src/test/java/com/myagent/assistant/qdrant/service/QdrantServiceTest.java`
  - Verify unscoped search body has no filter and scoped search body contains `match.any` paper IDs.
- Create `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/RagChatServiceImplTest.java`
  - Verify `paperIds` are passed to retrieval and legacy `paperId` is converted to a one-item scope.
- Modify `frontend/research-assistant-frontend/src/views/RagChatView.vue`
  - Send `paperIds` for zero, one, or many selected reference papers.
- Modify `frontend/research-assistant-frontend/src/api/rag.test.js`
  - Assert `chatWithRag` forwards `paperIds` unchanged.
- Modify `docs/status/current.md`
  - Record the completed feature and verification results after tests pass.

---

### Task 1: Backend Request and Service Interfaces

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/dto/RagChatRequest.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/RagRetrievalService.java`

**Interfaces:**
- Consumes: existing `RagChatRequest.paperId`, existing `RagRetrievalService.retrieveSources(String, Integer)`.
- Produces: `RagChatRequest.paperIds: List<Long>` and `RagRetrievalService.retrieveSources(String question, Integer topK, List<Long> paperIds)`.

- [ ] **Step 1: Add request field**

Add this import and field to `RagChatRequest.java`:

```java
import java.util.List;
```

```java
    /**
     * 限定检索的文献 ID 列表。
     *
     * 为空或空列表时，默认检索全部文献；
     * 不为空时，只从这些文献的 chunks 中检索。
     */
    private List<Long> paperIds;
```

- [ ] **Step 2: Add retrieval overload**

Update `RagRetrievalService.java` to include:

```java
    /**
     * 根据用户问题和可选文献范围检索相关 chunk 来源。
     *
     * @param question 用户问题
     * @param topK     返回前 K 个来源
     * @param paperIds 限定检索的文献 ID 列表；为空时检索全部文献
     * @return RAG sources
     */
    List<RagSource> retrieveSources(String question, Integer topK, List<Long> paperIds);
```

- [ ] **Step 3: Run compile check**

Run from `backend/research-assistant-backend`:

```bash
JAVA_HOME=C:/path/to/jdk-21 ./mvnw -DskipTests compile
```

Expected: compile fails until implementation classes add the new interface method. This confirms the interface change is detected.

---

### Task 2: Qdrant Scoped Search

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/qdrant/service/QdrantService.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/qdrant/service/QdrantServiceTest.java`

**Interfaces:**
- Consumes: `EmbeddingService.embed(String): List<Double>`, existing `searchSimilarChunksRaw(String, Integer)`.
- Produces: `searchSimilarChunksRaw(String question, Integer topK, List<Long> paperIds)`.

- [ ] **Step 1: Write failing Qdrant request-body tests**

Create `QdrantServiceTest.java` with tests that start a local HTTP server, capture Qdrant request JSON, and assert filter behavior:

```java
package com.myagent.assistant.qdrant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.embedding.EmbeddingService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QdrantServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void searchSimilarChunksRawAddsPaperIdFilterWhenScopeIsProvided() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startQdrantStub(requestBody);

        try {
            QdrantService service = createService(server);

            service.searchSimilarChunksRaw("transformer", 5, List.of(1L, 2L));

            JsonNode body = objectMapper.readTree(requestBody.get());
            assertThat(body.get("limit").asInt()).isEqualTo(5);
            assertThat(body.get("with_payload").asBoolean()).isTrue();
            assertThat(body.get("filter").get("must").get(0).get("key").asText()).isEqualTo("paperId");
            assertThat(body.get("filter").get("must").get(0).get("match").get("any"))
                    .extracting(JsonNode::asLong)
                    .containsExactly(1L, 2L);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void searchSimilarChunksRawOmitsFilterWhenScopeIsEmpty() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startQdrantStub(requestBody);

        try {
            QdrantService service = createService(server);

            service.searchSimilarChunksRaw("transformer", 5, List.of());

            JsonNode body = objectMapper.readTree(requestBody.get());
            assertThat(body.has("filter")).isFalse();
        } finally {
            server.stop(0);
        }
    }

    private QdrantService createService(HttpServer server) {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embed("transformer")).thenReturn(List.of(0.1, 0.2, 0.3, 0.4));
        when(embeddingService.dimension()).thenReturn(4);

        QdrantService service = new QdrantService(embeddingService);
        ReflectionTestUtils.setField(service, "qdrantUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        return service;
    }

    private HttpServer startQdrantStub(AtomicReference<String> requestBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections/paper_chunks/points/search", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"result\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        server.start();
        return server;
    }
}
```

- [ ] **Step 2: Run failing test**

Run:

```bash
JAVA_HOME=C:/path/to/jdk-21 ./mvnw -Dtest=QdrantServiceTest test
```

Expected: FAIL because `searchSimilarChunksRaw(String, Integer, List<Long>)` does not exist.

- [ ] **Step 3: Implement scoped Qdrant search**

In `QdrantService.java`, import:

```java
import java.util.ArrayList;
```

Add overload and helper:

```java
    public String searchSimilarChunksRaw(String question, Integer topK, List<Long> paperIds) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("问题不能为空");
        }

        int limit = topK != null && topK > 0 ? topK : 5;
        List<Double> queryVector = embeddingService.embed(question);
        String url = qdrantUrl + "/collections/paper_chunks/points/search";

        Map<String, Object> body = new HashMap<>();
        body.put("vector", queryVector);
        body.put("limit", limit);
        body.put("with_payload", true);

        List<Long> scopedPaperIds = normalizePaperIds(paperIds);
        if (!scopedPaperIds.isEmpty()) {
            body.put("filter", Map.of(
                    "must", List.of(Map.of(
                            "key", "paperId",
                            "match", Map.of("any", scopedPaperIds)
                    ))
            ));
        }

        return RestClient.create()
                .post()
                .uri(url)
                .body(body)
                .retrieve()
                .body(String.class);
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
```

Update existing two-argument method to delegate:

```java
    public String searchSimilarChunksRaw(String question, Integer topK) {
        return searchSimilarChunksRaw(question, topK, List.of());
    }
```

- [ ] **Step 4: Run Qdrant tests**

Run:

```bash
JAVA_HOME=C:/path/to/jdk-21 ./mvnw -Dtest=QdrantServiceTest test
```

Expected: PASS.

---

### Task 3: Backend RAG Scope Propagation

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagChatServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/controller/RagController.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/RagChatServiceImplTest.java`

**Interfaces:**
- Consumes: `RagChatRequest.getPaperIds()`, `RagChatRequest.getPaperId()`, `RagRetrievalService.retrieveSources(String, Integer, List<Long>)`.
- Produces: effective retrieval scope rule: `paperIds` first, then legacy `paperId`, then all papers.

- [ ] **Step 1: Write failing chat service scope tests**

Create `RagChatServiceImplTest.java`:

```java
package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.chat.service.ChatHistoryService;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.dto.RagChatRequest;
import com.myagent.assistant.rag.service.IdeaSuggestionService;
import com.myagent.assistant.rag.service.RagPromptService;
import com.myagent.assistant.rag.service.RagRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagChatServiceImplTest {

    @Test
    void chatPassesSelectedPaperIdsToRetrieval() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagPromptService promptService = mock(RagPromptService.class);
        LlmService llmService = mock(LlmService.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        IdeaSuggestionService ideaSuggestionService = mock(IdeaSuggestionService.class);

        when(retrievalService.retrieveSources("什么是 RAG？", 5, List.of(1L, 2L))).thenReturn(List.of());
        when(promptService.buildPrompt(any(String.class), any(List.class))).thenReturn("prompt");
        when(llmService.generateAnswer("prompt")).thenReturn("answer");
        when(llmService.provider()).thenReturn("test");
        when(llmService.modelName()).thenReturn("fake");
        when(chatHistoryService.saveRagChat(null, null, "什么是 RAG？", "answer", "test", "fake", List.of()))
                .thenReturn(9L);

        RagChatServiceImpl service = new RagChatServiceImpl(
                retrievalService,
                promptService,
                llmService,
                chatHistoryService,
                ideaSuggestionService
        );

        RagChatRequest request = new RagChatRequest();
        request.setQuestion("什么是 RAG？");
        request.setTopK(5);
        request.setPaperIds(List.of(1L, 2L));

        assertThat(service.chat(request).getSessionId()).isEqualTo(9L);
        verify(retrievalService).retrieveSources("什么是 RAG？", 5, List.of(1L, 2L));
    }

    @Test
    void chatUsesLegacyPaperIdWhenPaperIdsAreEmpty() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagPromptService promptService = mock(RagPromptService.class);
        LlmService llmService = mock(LlmService.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        IdeaSuggestionService ideaSuggestionService = mock(IdeaSuggestionService.class);

        when(retrievalService.retrieveSources("总结这篇论文", 5, List.of(7L))).thenReturn(List.of());
        when(promptService.buildPrompt(any(String.class), any(List.class))).thenReturn("prompt");
        when(llmService.generateAnswer("prompt")).thenReturn("answer");
        when(llmService.provider()).thenReturn("test");
        when(llmService.modelName()).thenReturn("fake");
        when(chatHistoryService.saveRagChat(null, 7L, "总结这篇论文", "answer", "test", "fake", List.of()))
                .thenReturn(10L);

        RagChatServiceImpl service = new RagChatServiceImpl(
                retrievalService,
                promptService,
                llmService,
                chatHistoryService,
                ideaSuggestionService
        );

        RagChatRequest request = new RagChatRequest();
        request.setQuestion("总结这篇论文");
        request.setPaperId(7L);

        assertThat(service.chat(request).getSessionId()).isEqualTo(10L);
        verify(retrievalService).retrieveSources("总结这篇论文", 5, List.of(7L));
    }
}
```

- [ ] **Step 2: Run failing chat service test**

Run:

```bash
JAVA_HOME=C:/path/to/jdk-21 ./mvnw -Dtest=RagChatServiceImplTest test
```

Expected: FAIL until `RagChatServiceImpl` calls the new scoped retrieval method.

- [ ] **Step 3: Implement retrieval scope in `RagRetrievalServiceImpl`**

Add new method and update internal search calls:

```java
    @Override
    public List<RagSource> retrieveSources(String question, Integer topK) {
        return retrieveSources(question, topK, List.of());
    }

    @Override
    public List<RagSource> retrieveSources(String question, Integer topK, List<Long> paperIds) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("问题不能为空");
        }

        try {
            int limit = topK != null && topK > 0 ? topK : 5;
            List<Long> scopedPaperIds = normalizePaperIds(paperIds);

            List<RagSource> originalSources = searchSources(question, limit, "original", scopedPaperIds);
            String retrievalQuestion = queryRewriteService.rewriteForRetrieval(question);

            List<RagSource> rewrittenSources;
            if (retrievalQuestion != null
                    && !retrievalQuestion.isBlank()
                    && !retrievalQuestion.equals(question)) {
                rewrittenSources = searchSources(retrievalQuestion, limit, "rewritten", scopedPaperIds);
            } else {
                rewrittenSources = List.of();
            }

            Map<Long, RagSource> merged = new LinkedHashMap<>();
            mergeSources(merged, originalSources);
            mergeSources(merged, rewrittenSources);

            return merged.values()
                    .stream()
                    .sorted((a, b) -> {
                        double scoreA = a.getScore() != null ? a.getScore() : 0.0;
                        double scoreB = b.getScore() != null ? b.getScore() : 0.0;
                        return Double.compare(scoreB, scoreA);
                    })
                    .limit(limit)
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException("RAG 检索失败：" + e.getMessage(), e);
        }
    }
```

Change private search signature and Qdrant call:

```java
    private List<RagSource> searchSources(String query, Integer topK, String retrievalRoute, List<Long> paperIds) throws Exception {
        String rawResponse = qdrantService.searchSimilarChunksRaw(query, topK, paperIds);
        ...
    }
```

Add helper:

```java
    private List<Long> normalizePaperIds(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return List.of();
        }

        return paperIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }
```

- [ ] **Step 4: Implement chat effective scope**

In `RagChatServiceImpl.java`, change retrieval call:

```java
        List<Long> effectivePaperIds = resolvePaperIds(request);
        List<RagSource> sources = ragRetrievalService.retrieveSources(request.getQuestion(), topK, effectivePaperIds);
```

Change `saveRagChat` paper argument:

```java
                resolveHistoryPaperId(effectivePaperIds),
```

Add helpers:

```java
    private List<Long> resolvePaperIds(RagChatRequest request) {
        if (request.getPaperIds() != null && !request.getPaperIds().isEmpty()) {
            return request.getPaperIds()
                    .stream()
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
        }

        if (request.getPaperId() != null && request.getPaperId() > 0) {
            return List.of(request.getPaperId());
        }

        return List.of();
    }

    private Long resolveHistoryPaperId(List<Long> paperIds) {
        return paperIds != null && paperIds.size() == 1 ? paperIds.get(0) : null;
    }
```

- [ ] **Step 5: Support scoped `/api/rag/sources` debug query**

In `RagController.java`, add import:

```java
import java.util.Arrays;
```

Update method signature:

```java
    public Result<List<RagSource>> sources(
            @RequestParam("question") String question,
            @RequestParam(value = "topK", required = false) Integer topK,
            @RequestParam(value = "paperIds", required = false) String paperIds
    ) {
        return Result.success(ragRetrievalService.retrieveSources(question, topK, parsePaperIds(paperIds)));
    }
```

Add helper:

```java
    private List<Long> parsePaperIds(String paperIds) {
        if (paperIds == null || paperIds.isBlank()) {
            return List.of();
        }

        return Arrays.stream(paperIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }
```

- [ ] **Step 6: Run backend scoped tests**

Run:

```bash
JAVA_HOME=C:/path/to/jdk-21 ./mvnw -Dtest=QdrantServiceTest,RagChatServiceImplTest test
```

Expected: PASS.

---

### Task 4: Frontend Send Multi-Paper Scope

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`
- Modify: `frontend/research-assistant-frontend/src/api/rag.test.js`

**Interfaces:**
- Consumes: `selectedPaperIds` array from `RagChatView.vue`.
- Produces: `/api/rag/chat` payload with `paperIds: number[]` and compatibility `paperId` when exactly one paper is selected.

- [ ] **Step 1: Update frontend API test payload**

In `rag.test.js`, update the payload to include `paperIds`:

```js
  const payload = {
    question: '这篇论文的方法有什么改进空间？',
    topK: 5,
    sessionId: 12,
    paperId: 3,
    paperIds: [3, 5],
  }
```

The existing assertion stays valid because `chatWithRag` forwards payloads unchanged.

- [ ] **Step 2: Run frontend API test**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/rag.test.js
```

Expected: PASS, because API helper already forwards arbitrary payload fields.

- [ ] **Step 3: Send selected paper IDs from chat page**

In `RagChatView.vue`, replace current `scopedPaperId` request section:

```js
    const scopedPaperId = selectedPaperIds.value.length === 1 ? selectedPaperIds.value[0] : null
    const response = await chatWithRag({
      question: trimmedQuestion,
      topK: topK.value,
      sessionId: sessionId.value,
      paperId: scopedPaperId,
    })
```

with:

```js
    const scopedPaperIds = selectedPaperIds.value.map(Number).filter((id) => Number.isFinite(id) && id > 0)
    const scopedPaperId = scopedPaperIds.length === 1 ? scopedPaperIds[0] : null
    const response = await chatWithRag({
      question: trimmedQuestion,
      topK: topK.value,
      sessionId: sessionId.value,
      paperId: scopedPaperId,
      paperIds: scopedPaperIds,
    })
```

- [ ] **Step 4: Run frontend focused tests and build**

Run:

```bash
node --test src/api/rag.test.js src/views/ragChatState.test.js
npm run build
```

Expected: tests PASS and build succeeds. Vite may still print existing chunk-size or third-party annotation warnings; those are not failures.

---

### Task 5: End-to-End Verification and Progress Update

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: backend and frontend verification results from Tasks 2-4.
- Produces: updated project progress and next task notes.

- [ ] **Step 1: Run combined backend verification**

Run from `backend/research-assistant-backend`:

```bash
JAVA_HOME=C:/path/to/jdk-21 ./mvnw -Dtest=QdrantServiceTest,RagChatServiceImplTest test
```

Expected: PASS.

- [ ] **Step 2: Run combined frontend verification**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/rag.test.js src/views/ragChatState.test.js
npm run build
```

Expected: tests PASS and build succeeds.

- [ ] **Step 3: Manual Apifox/browser checks**

Use these checks after starting backend, frontend, MySQL, and Qdrant:

```http
POST /api/rag/chat
Content-Type: application/json

{
  "question": "这几篇论文的核心方法是什么？",
  "topK": 5,
  "paperIds": [1, 2]
}
```

Expected: every item in `data.sources` has `paperId` equal to `1` or `2`.

```http
POST /api/rag/chat
Content-Type: application/json

{
  "question": "这几篇论文的核心方法是什么？",
  "topK": 5,
  "paperIds": []
}
```

Expected: request still works and can search all papers.

```http
GET /api/rag/sources?question=transformer&topK=5&paperIds=1,2
```

Expected: every returned source has `paperId` equal to `1` or `2`.

- [ ] **Step 4: Update `docs/status/current.md`**

Add a concise completed-state line under current task status:

```text
选中文献限定 RAG 检索范围已完成：前端 /chat 会传递 paperIds，后端通过 Qdrant payload filter 将 sources 限定在选中文献内；未选择文献时仍默认检索全部论文。
```

Add verification line:

```text
Claude 已完成选中文献限定 RAG 检索范围验证：QdrantServiceTest、RagChatServiceImplTest 通过，前端 rag.test.js、ragChatState.test.js 通过，npm run build 通过。
```

Set next task suggestion:

```text
下一阶段可补充 Research Idea 编辑能力，或继续增强限定检索体验，例如在回答中展示当前检索范围和空结果提示。
```
