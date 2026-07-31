# Session-Based Chat Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `/chat` the single session-based workspace where users can select a session, see its message history, ask follow-up questions, view latest sources, and save the session as a research idea.

**Architecture:** Keep backend endpoints unchanged. `RagChatView.vue` consumes both RAG chat API and chat history API, managing sessions, selected session messages, composer state, and latest sources in one page. `App.vue` removes the separate `对话历史` navigation entry while keeping the route/file available for now.

**Tech Stack:** Vue 3, Vue Router 4, Vite 5, Axios, Element Plus.

## Global Constraints

- Project is not a Git repository, so do not create git commits.
- No backend changes in this stage.
- Keep `/chat-history` route and file for now, but remove it from the primary sidebar navigation.
- `/chat` must show current session history while asking follow-up questions.
- Sending a question must reload the selected session messages after backend saves user and assistant messages.
- Verification commands: `node --test src/api/rag.test.js`, `node --test src/api/chatHistory.test.js`, and `npm run build` from `frontend/research-assistant-frontend`.

---

## File Structure

- Modify `frontend/research-assistant-frontend/src/views/RagChatView.vue`
  - Owns session list, current message timeline, composer, latest sources, and save-as-idea action.
- Modify `frontend/research-assistant-frontend/src/App.vue`
  - Removes `对话历史` from primary sidebar navigation.
- Modify `docs/status/current.md`
  - Records the corrected session-based chat workspace design and verification.

---

### Task 1: Merge session history into RagChatView

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`

**Interfaces:**
- Consumes:
  - `chatWithRag(payload)`
  - `saveDraftFromSession(sessionId)`
  - `listChatSessions()`
  - `listChatMessages(sessionId)`
- Produces:
  - Session list in left panel.
  - Current session message timeline in middle panel.
  - Composer at the bottom of the message panel.
  - Latest sources and save action in right panel.

- [ ] **Step 1: Update imports**

Add chat history helpers and router helpers:

```js
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listChatMessages, listChatSessions } from '../api/chatHistory.js'
```

- [ ] **Step 2: Add state**

Add:

```js
const router = useRouter()
const sessions = ref([])
const messages = ref([])
const sessionsLoading = ref(false)
const messagesLoading = ref(false)
const selectedSession = ref(null)
```

- [ ] **Step 3: Add session loading functions**

Add:

```js
async function loadSessions() {
  sessionsLoading.value = true

  try {
    sessions.value = await listChatSessions()
    const routeSessionId = parseSessionId(route.query.sessionId)
    const preferredSession = sessions.value.find((session) => session.id === routeSessionId) || sessions.value[0] || null

    if (preferredSession) {
      await selectSession(preferredSession, { updateRoute: false })
    }
  } catch (error) {
    ElMessage.error(`会话列表加载失败：${error.message}`)
  } finally {
    sessionsLoading.value = false
  }
}

async function selectSession(session, options = {}) {
  selectedSession.value = session
  sessionId.value = session?.id || null

  if (options.updateRoute !== false && session?.id) {
    router.replace({ path: '/chat', query: { sessionId: session.id } })
  }

  if (!session?.id) {
    messages.value = []
    return
  }

  await loadMessages(session.id)
}

async function loadMessages(nextSessionId = sessionId.value) {
  if (!nextSessionId) {
    messages.value = []
    return
  }

  messagesLoading.value = true

  try {
    messages.value = await listChatMessages(nextSessionId)
  } catch (error) {
    ElMessage.error(`会话消息加载失败：${error.message}`)
  } finally {
    messagesLoading.value = false
  }
}
```

- [ ] **Step 4: Update handleAsk**

After response is received:

```js
sessionId.value = response.sessionId || sessionId.value
...
await loadSessions()
if (sessionId.value) {
  const current = sessions.value.find((session) => session.id === sessionId.value) || selectedSession.value || { id: sessionId.value }
  await selectSession(current)
}
```

Clear `question.value = ''` after a successful send.

- [ ] **Step 5: Add helper functions**

Add:

```js
function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return String(value).replace('T', ' ').slice(0, 19)
}

function formatMessageRole(role) {
  return role === 'assistant' ? 'AI 回答' : '用户问题'
}
```

- [ ] **Step 6: Update route watcher and mounted hook**

Use:

```js
watch(
  () => route.query.sessionId,
  async (value) => {
    const nextSessionId = parseSessionId(value)
    if (!nextSessionId || nextSessionId === sessionId.value) {
      return
    }

    const session = sessions.value.find((item) => item.id === nextSessionId) || { id: nextSessionId }
    await selectSession(session, { updateRoute: false })
  },
)

onMounted(loadSessions)
```

- [ ] **Step 7: Replace template with three-column session workspace**

Use:

```vue
<section class="page-panel chat-page">
  <div class="page-toolbar">
    <div>
      <p class="eyebrow">Ask</p>
      <h1>论文问答</h1>
      <p>选择一个会话查看上下文，在同一条对话里继续追问。</p>
    </div>
    <div class="page-actions">
      <el-tag v-if="sessionId" type="info" effect="plain">Session #{{ sessionId }}</el-tag>
      <el-tag v-else type="info" effect="plain">新会话</el-tag>
    </div>
  </div>

  <div class="chat-workspace-grid">
    <!-- left: sessions -->
    <!-- middle: current messages + composer -->
    <!-- right: latest sources + actions -->
  </div>
</section>
```

The left panel should list `sessions`, the middle panel should render `messages`, and the right panel should render `sources` plus save action.

---

### Task 2: Remove separate history nav from sidebar

**Files:**
- Modify: `frontend/research-assistant-frontend/src/App.vue`

**Interfaces:**
- Removes visible navigation link to `/chat-history`.
- Keeps other navigation links intact.

- [ ] **Step 1: Remove `RouterLink to="/chat-history"` block**

Remove:

```vue
<RouterLink to="/chat-history">
  <span class="nav-kicker">History</span>
  <strong>对话历史</strong>
</RouterLink>
```

- [ ] **Step 2: Update workflow copy if needed**

Keep:

```text
上传文献 → 向量化 → 问答 → 沉淀想法
```

---

### Task 3: Verify and update progress

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes successful test/build output.
- Produces updated project progress.

- [ ] **Step 1: Run API tests**

```bash
node --test src/api/rag.test.js
node --test src/api/chatHistory.test.js
```

Expected: all tests pass.

- [ ] **Step 2: Run frontend build**

```bash
npm run build
```

Expected: build succeeds. Existing Vite third-party PURE comment and chunk-size warnings are acceptable.

- [ ] **Step 3: Update progress document**

Record:

```text
前端论文问答页已改为 session-based 会话工作台：会话列表、当前会话消息、继续提问、最新引用片段和保存研究想法集中在 /chat 页面；侧边栏不再单独展示对话历史入口。
```

Record verification:

```text
Claude 已完成 session-based 论文问答页验证：rag 与 chatHistory API helper 测试通过；npm run build 通过，Vite 仅提示第三方 PURE 注释和 chunk 大小警告。
```

---

## Self-Review

- Spec coverage: The plan merges session history into `/chat`, removes the separate history nav, preserves backend endpoints, and verifies tests/build.
- Placeholder scan: No TBD/TODO placeholders remain.
- Type consistency: Function names match existing API helper exports.
