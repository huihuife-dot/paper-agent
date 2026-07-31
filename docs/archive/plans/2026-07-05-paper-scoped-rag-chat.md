# Paper Scoped RAG Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复“从文献库点击某篇论文问答时没有绑定该论文，且 RAG 检索会跨论文召回”的问题。

**Architecture:** 先做单篇论文闭环，不引入多论文关联表。前端从文献列表进入 `/chat?paperId=ID`，问答页读取该参数并在发送 RAG 请求时传 `paperId`；后端将 `paperId` 传入 retrieval 层，Qdrant search 使用 payload filter 只检索该论文的 chunk。

**Tech Stack:** Vue 3 + Vue Router + Node test；Spring Boot 3.5 + MyBatis-Plus + JUnit 5 + Mockito；Qdrant REST API。

## Global Constraints

- 项目不是 Git 仓库，不执行 git commit。
- 遵循 TDD：生产代码修改前先补测试，并运行测试确认 RED。
- 只做单篇 `paperId` 限定，暂不做多篇论文会话关联表。
- 保持当前接口兼容：不传 `paperId` 时仍允许全库 RAG 检索。
- 修改完成后更新 `docs/status/current.md`，只记录实质功能进展和验证结果。

---

## File Structure

- `frontend/research-assistant-frontend/src/views/ragChatState.js`
  - 新增路由与论文 ID 辅助函数：`buildPaperChatRoute(paper)`、`parsePaperId(value)`。
- `frontend/research-assistant-frontend/src/views/ragChatState.test.js`
  - 覆盖文献进入问答路由和 paperId 解析。
- `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`
  - 文献列表“问答”按钮传当前行，跳转到 `/chat?paperId=<row.id>`。
- `frontend/research-assistant-frontend/src/views/RagChatView.vue`
  - 初始化和监听 `route.query.paperId`；发送 RAG 时携带 `paperId`；如果没有 `sessionId` 但有 `paperId`，不自动选择第一条历史会话。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/qdrant/service/QdrantService.java`
  - 新增 `searchSimilarChunksRaw(String question, Integer topK, Long paperId)`，带 paperId 时构造 Qdrant filter。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/RagRetrievalService.java`
  - 新增带 `paperId` 参数的 retrieval 接口。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagRetrievalServiceImpl.java`
  - 传递 paperId 到两路召回。
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagChatServiceImpl.java`
  - 使用 `request.getPaperId()` 进行限定检索，并继续保存到会话。
- 新建 `backend/research-assistant-backend/src/test/java/com/myagent/assistant/qdrant/service/QdrantServiceTest.java`
  - 用 MockRestServiceServer 验证 Qdrant body 中包含 `filter.must.key=paperId`。
- 新建 `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/RagChatServiceImplTest.java`
  - 验证 RAG chat 会把 `paperId` 传给 retrieval，并保存到 chat session。

---

### Task 1: Frontend paperId route and request payload

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/ragChatState.js`
- Modify: `frontend/research-assistant-frontend/src/views/ragChatState.test.js`
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`

**Interfaces:**
- Produces: `buildPaperChatRoute(paper): { path: string, query: { paperId: number } } | { path: string }`
- Produces: `parsePaperId(value): number | null`
- Consumes: existing `chatWithRag(payload)` API helper.

- [ ] **Step 1: Write failing frontend state tests**

Add tests to `src/views/ragChatState.test.js`:

```js
import {
  buildPaperChatRoute,
  parsePaperId,
} from './ragChatState.js'

test('buildPaperChatRoute opens chat with the selected paper id', () => {
  assert.deepEqual(buildPaperChatRoute({ id: 7 }), {
    path: '/chat',
    query: { paperId: 7 },
  })
})

test('parsePaperId accepts positive numeric ids only', () => {
  assert.equal(parsePaperId('9'), 9)
  assert.equal(parsePaperId(3), 3)
  assert.equal(parsePaperId('abc'), null)
  assert.equal(parsePaperId('0'), null)
})
```

- [ ] **Step 2: Run frontend RED test**

Run in `frontend/research-assistant-frontend`:

```bash
node --test src/views/ragChatState.test.js
```

Expected: FAIL because `buildPaperChatRoute` and `parsePaperId` are not exported yet.

- [ ] **Step 3: Implement frontend helper functions**

Add to `src/views/ragChatState.js`:

```js
export function parsePaperId(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

export function buildPaperChatRoute(paper) {
  const paperId = parsePaperId(paper?.id)

  if (!paperId) {
    return { path: '/chat' }
  }

  return {
    path: '/chat',
    query: { paperId },
  }
}
```

- [ ] **Step 4: Wire PaperManagementView route**

In `PaperManagementView.vue`:

```vue
<el-button size="small" type="success" plain @click="goToChat(row)">
  问答
</el-button>
```

```js
import { buildPaperChatRoute } from './ragChatState.js'

function goToChat(row) {
  router.push(buildPaperChatRoute(row))
}
```

- [ ] **Step 5: Wire RagChatView paperId selection and payload**

In `RagChatView.vue`:

```js
import {
  formatSelectedPaperSummary,
  isPaperSelected,
  normalizePaperRows,
  parsePaperId,
  togglePaperSelection,
} from './ragChatState.js'
```

Initialize:

```js
const selectedPaperIds = ref(parsePaperId(route.query.paperId) ? [parsePaperId(route.query.paperId)] : [])
```

Change `loadSessions()` so `/chat?paperId=ID` does not auto-open the first unrelated session:

```js
const routePaperId = parsePaperId(route.query.paperId)

if (routeSessionId) {
  const preferredSession = sessions.value.find((session) => session.id === routeSessionId) || { id: routeSessionId }
  await selectSession(preferredSession, { updateRoute: false })
  return
}

if (!routePaperId && !selectedSession.value && sessions.value.length > 0) {
  await selectSession(sessions.value[0], { updateRoute: false })
}
```

Before sending RAG:

```js
const scopedPaperId = selectedPaperIds.value.length === 1 ? selectedPaperIds.value[0] : null
const response = await chatWithRag({
  question: trimmedQuestion,
  topK: topK.value,
  sessionId: sessionId.value,
  paperId: scopedPaperId,
})
```

- [ ] **Step 6: Run frontend GREEN tests**

Run:

```bash
node --test src/views/ragChatState.test.js src/api/rag.test.js src/api/chatHistory.test.js
npm run build
```

Expected: tests pass and build succeeds.

---

### Task 2: Backend paperId-scoped retrieval

**Files:**
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/qdrant/service/QdrantServiceTest.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/rag/service/impl/RagChatServiceImplTest.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/qdrant/service/QdrantService.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/RagRetrievalService.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagRetrievalServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/rag/service/impl/RagChatServiceImpl.java`

**Interfaces:**
- Produces: `QdrantService.searchSimilarChunksRaw(String question, Integer topK, Long paperId)`.
- Produces: `RagRetrievalService.retrieveSources(String question, Integer topK, Long paperId)`.
- Consumes: existing `RagChatRequest.getPaperId()`.

- [ ] **Step 1: Write failing Qdrant filter test**

Create `QdrantServiceTest.java` that injects fake embedding and verifies POST body includes `filter.must` with `paperId`.

- [ ] **Step 2: Write failing RagChatService delegation test**

Create `RagChatServiceImplTest.java` verifying `ragRetrievalService.retrieveSources(question, topK, paperId)` and `chatHistoryService.saveRagChat(sessionId, paperId, ...)` are called.

- [ ] **Step 3: Run backend RED tests**

Run in `backend/research-assistant-backend` with JDK 21:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=QdrantServiceTest,RagChatServiceImplTest test
```

Expected: FAIL because overloads do not exist yet.

- [ ] **Step 4: Add Qdrant paperId filter overload**

Implement overload while preserving old method:

```java
public String searchSimilarChunksRaw(String question, Integer topK) {
    return searchSimilarChunksRaw(question, topK, null);
}

public String searchSimilarChunksRaw(String question, Integer topK, Long paperId) {
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

    if (paperId != null) {
        body.put("filter", Map.of(
                "must", List.of(Map.of(
                        "key", "paperId",
                        "match", Map.of("value", paperId)
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
```

- [ ] **Step 5: Add retrieval overload and pass paperId through**

Update interface and implementation so both original and rewritten query paths call:

```java
searchSources(query, limit, "original", paperId)
```

and then:

```java
qdrantService.searchSimilarChunksRaw(query, topK, paperId)
```

- [ ] **Step 6: Update RagChatServiceImpl**

Change retrieval call to:

```java
List<RagSource> sources = ragRetrievalService.retrieveSources(
        request.getQuestion(),
        topK,
        request.getPaperId()
);
```

- [ ] **Step 7: Run backend GREEN tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=QdrantServiceTest,RagChatServiceImplTest test
```

Expected: PASS.

---

### Task 3: Full regression verification and progress update

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: Task 1 and Task 2 complete.
- Produces: verified project state documented in progress file.

- [ ] **Step 1: Run frontend regression tests**

Run in `frontend/research-assistant-frontend`:

```bash
node --test src/api/papers.test.js src/api/rag.test.js src/api/researchIdeas.test.js src/api/chatHistory.test.js src/views/ragChatState.test.js
npm run build
```

Expected: all tests pass and Vite build succeeds.

- [ ] **Step 2: Run backend regression tests**

Run in `backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=QdrantServiceTest,RagChatServiceImplTest,ChatHistoryServiceImplTest test
```

Expected: all selected tests pass.

- [ ] **Step 3: Update project progress**

Add concise progress lines to `docs/status/current.md`:

```text
前端文献库进入论文问答已绑定 paperId：点击某篇论文“问答”会进入 /chat?paperId=...，问答页自动选中该论文并在 RAG 请求中传递 paperId。
后端 RAG 已支持单篇论文限定检索：/api/rag/chat 接收 paperId 后，Qdrant 检索会按 payload.paperId 过滤，只召回该论文 chunk。
```

Add verification lines:

```text
Claude 已完成文献限定 RAG 问答修复验证：前端 ragChatState/rag/chatHistory 相关测试通过，前端构建通过；后端 QdrantService/RagChatServiceImpl 相关测试通过。
```

---

## Self-Review

- Spec coverage: 覆盖文献库跳转、问答页 paperId 状态、RAG 请求、后端 retrieval、Qdrant filter、进度文档。
- Placeholder scan: 无 TBD/TODO/稍后实现。
- Type consistency: 前端 `paperId` 使用 number；后端 `paperId` 使用 `Long`；旧接口保留无 paperId 兼容方法。