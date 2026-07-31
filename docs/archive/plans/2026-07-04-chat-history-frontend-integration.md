# Chat History Frontend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated Vue frontend page for browsing chat sessions/messages and continuing an existing RAG session.

**Architecture:** Keep HTTP logic in `src/api/chatHistory.js`, page state in `src/views/ChatHistoryView.vue`, and route-level session continuation in `RagChatView.vue` by reading `route.query.sessionId`. The new page follows the existing Research Desk visual system and does not add backend endpoints.

**Tech Stack:** Vue 3, Vue Router 4, Vite 5, Axios, Element Plus, Node built-in test runner.

## Global Constraints

- Project is not a Git repository, so do not create git commits.
- Keep this stage focused on chat history browsing and session continuation; do not add rename, delete, search, pagination, or rich source parsing.
- Existing backend interfaces are `POST /api/chat/sessions`, `GET /api/chat/sessions`, and `GET /api/chat/sessions/{sessionId}/messages`.
- Follow the existing frontend style and API helper pattern.
- Verification commands are `node --test src/api/chatHistory.test.js` and `npm run build` from `frontend/research-assistant-frontend`.

---

## File Structure

- Create `frontend/research-assistant-frontend/src/api/chatHistory.js`
  - Owns chat history HTTP calls and Result data unwrapping.
- Create `frontend/research-assistant-frontend/src/api/chatHistory.test.js`
  - Verifies helper paths, params, and response unwrapping.
- Create `frontend/research-assistant-frontend/src/views/ChatHistoryView.vue`
  - Owns session list, selected session, message timeline, and continue-session navigation.
- Modify `frontend/research-assistant-frontend/src/router/index.js`
  - Registers `/chat-history` route.
- Modify `frontend/research-assistant-frontend/src/App.vue`
  - Adds `History / 对话历史` navigation entry.
- Modify `frontend/research-assistant-frontend/src/views/RagChatView.vue`
  - Reads `sessionId` from route query and reuses it for RAG chat.
- Modify `docs/status/current.md`
  - Records substantive frontend chat history integration and verification results.

---

### Task 1: Add Chat History API helper

**Files:**
- Create: `frontend/research-assistant-frontend/src/api/chatHistory.js`
- Create: `frontend/research-assistant-frontend/src/api/chatHistory.test.js`

**Interfaces:**
- Produces:
  - `listChatSessions(client = apiClient): Promise<Array>`
  - `listChatMessages(sessionId, client = apiClient): Promise<Array>`
  - `createChatSession({ title, paperId } = {}, client = apiClient): Promise<Object>`

- [ ] **Step 1: Write failing helper tests**

Create `src/api/chatHistory.test.js`:

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import { createChatSession, listChatMessages, listChatSessions } from './chatHistory.js'

function createFakeClient() {
  const calls = []

  return {
    calls,
    get(path) {
      calls.push({ method: 'get', path })
      return Promise.resolve({ data: [{ id: 1 }] })
    },
    post(path, body, config) {
      calls.push({ method: 'post', path, body, config })
      return Promise.resolve({ data: { id: 2 } })
    },
  }
}

test('listChatSessions calls the session list endpoint', async () => {
  const client = createFakeClient()

  const result = await listChatSessions(client)

  assert.deepEqual(result, [{ id: 1 }])
  assert.deepEqual(client.calls, [
    { method: 'get', path: '/api/chat/sessions' },
  ])
})

test('listChatMessages calls the session messages endpoint', async () => {
  const client = createFakeClient()

  await listChatMessages(7, client)

  assert.deepEqual(client.calls, [
    { method: 'get', path: '/api/chat/sessions/7/messages' },
  ])
})

test('createChatSession sends non-empty params', async () => {
  const client = createFakeClient()

  const result = await createChatSession({ title: '论文问答', paperId: 3 }, client)

  assert.deepEqual(result, { id: 2 })
  assert.deepEqual(client.calls, [
    {
      method: 'post',
      path: '/api/chat/sessions',
      body: null,
      config: { params: { title: '论文问答', paperId: 3 } },
    },
  ])
})
```

- [ ] **Step 2: Run tests and verify RED**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/chatHistory.test.js
```

Expected: fails because `src/api/chatHistory.js` does not exist.

- [ ] **Step 3: Implement helper**

Create `src/api/chatHistory.js`:

```js
import apiClient from './client.js'

function unwrapResult(result) {
  return Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result
}

function compactParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== '' && value !== null && value !== undefined),
  )
}

export function listChatSessions(client = apiClient) {
  return client.get('/api/chat/sessions').then(unwrapResult)
}

export function listChatMessages(sessionId, client = apiClient) {
  return client.get(`/api/chat/sessions/${sessionId}/messages`).then(unwrapResult)
}

export function createChatSession({ title, paperId } = {}, client = apiClient) {
  return client
    .post('/api/chat/sessions', null, {
      params: compactParams({ title, paperId }),
    })
    .then(unwrapResult)
}
```

- [ ] **Step 4: Run helper tests and verify GREEN**

```bash
node --test src/api/chatHistory.test.js
```

Expected: 3 tests pass.

---

### Task 2: Add Chat History page, route, and navigation

**Files:**
- Create: `frontend/research-assistant-frontend/src/views/ChatHistoryView.vue`
- Modify: `frontend/research-assistant-frontend/src/router/index.js`
- Modify: `frontend/research-assistant-frontend/src/App.vue`

**Interfaces:**
- Consumes:
  - `listChatSessions()`
  - `listChatMessages(sessionId)`
  - Vue Router `router.push({ path: '/chat', query: { sessionId } })`
- Produces:
  - A `/chat-history` page that shows sessions and messages.

- [ ] **Step 1: Create ChatHistoryView**

Create `src/views/ChatHistoryView.vue` with a Research Desk style page:

```vue
<template>
  <section class="page-panel history-page">
    <div class="page-heading">
      <p class="eyebrow">Conversation archive</p>
      <h1>对话历史</h1>
      <p>回看围绕论文证据展开的 RAG 会话，继续追问，或把已有讨论沉淀为 Research Idea。</p>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :lg="8">
        <el-card class="workflow-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>会话列表</span>
              <el-button type="primary" plain :loading="sessionsLoading" @click="loadSessions">刷新</el-button>
            </div>
          </template>

          <el-empty v-if="sessions.length === 0 && !sessionsLoading" description="暂无历史会话，先完成一次 RAG 问答。" />

          <div v-else v-loading="sessionsLoading" class="history-session-list">
            <button
              v-for="session in sessions"
              :key="session.id"
              class="history-session-item"
              :class="{ active: selectedSession?.id === session.id }"
              type="button"
              @click="selectSession(session)"
            >
              <strong>{{ session.title || `Session #${session.id}` }}</strong>
              <span>#{{ session.id }} · {{ formatDateTime(session.updateTime || session.createTime) }}</span>
              <small v-if="session.paperId">Paper #{{ session.paperId }}</small>
            </button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card class="workflow-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ selectedSession ? selectedSession.title || `Session #${selectedSession.id}` : '消息记录' }}</span>
              <el-button type="success" plain :disabled="!selectedSession" @click="continueSession">
                继续问答
              </el-button>
            </div>
          </template>

          <el-empty v-if="!selectedSession" description="选择左侧会话后，这里会显示消息记录。" />

          <div v-else v-loading="messagesLoading" class="history-message-list">
            <el-empty v-if="messages.length === 0 && !messagesLoading" description="该会话还没有消息。" />

            <article
              v-for="message in messages"
              :key="message.id"
              class="message history-message"
              :class="message.role === 'assistant' ? 'answer-message' : 'question-message'"
            >
              <div class="answer-header">
                <strong>{{ message.role === 'assistant' ? 'AI 回答' : '用户问题' }}</strong>
                <el-tag v-if="message.modelProvider || message.modelName" type="info" effect="plain">
                  {{ formatModel(message) }}
                </el-tag>
              </div>
              <p>{{ message.content || '暂无内容' }}</p>
              <small>{{ formatDateTime(message.createTime) }}</small>

              <el-collapse v-if="message.sourcesJson" class="history-sources">
                <el-collapse-item title="查看 sourcesJson" :name="message.id">
                  <pre>{{ message.sourcesJson }}</pre>
                </el-collapse-item>
              </el-collapse>
            </article>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listChatMessages, listChatSessions } from '../api/chatHistory.js'

const router = useRouter()

const sessions = ref([])
const messages = ref([])
const selectedSession = ref(null)
const sessionsLoading = ref(false)
const messagesLoading = ref(false)

async function loadSessions() {
  sessionsLoading.value = true

  try {
    sessions.value = await listChatSessions()
    if (!selectedSession.value && sessions.value.length > 0) {
      await selectSession(sessions.value[0])
    }
  } catch (error) {
    ElMessage.error(`会话列表加载失败：${error.message}`)
  } finally {
    sessionsLoading.value = false
  }
}

async function selectSession(session) {
  selectedSession.value = session
  messagesLoading.value = true

  try {
    messages.value = await listChatMessages(session.id)
  } catch (error) {
    ElMessage.error(`消息记录加载失败：${error.message}`)
  } finally {
    messagesLoading.value = false
  }
}

function continueSession() {
  if (!selectedSession.value) {
    return
  }

  router.push({
    path: '/chat',
    query: { sessionId: selectedSession.value.id },
  })
}

function formatModel(message) {
  return [message.modelProvider, message.modelName].filter(Boolean).join(' · ')
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadSessions)
</script>
```

- [ ] **Step 2: Register route**

Modify `src/router/index.js`:

```js
import ChatHistoryView from '../views/ChatHistoryView.vue'
```

Add route:

```js
{
  path: '/chat-history',
  name: 'chat-history',
  component: ChatHistoryView,
  meta: {
    title: '对话历史',
  },
},
```

- [ ] **Step 3: Add navigation link**

Modify `src/App.vue`, add between RAG 问答 and Research Idea:

```vue
<RouterLink to="/chat-history">
  <span class="nav-kicker">History</span>
  <strong>对话历史</strong>
</RouterLink>
```

---

### Task 3: Continue existing sessions from RAG chat

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`

**Interfaces:**
- Consumes: route query `sessionId`.
- Produces: `RagChatView` initializes `sessionId` from `/chat?sessionId=<id>` and passes it to `chatWithRag`.

- [ ] **Step 1: Import route helpers**

Change import:

```js
import { computed, ref } from 'vue'
```

to:

```js
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
```

- [ ] **Step 2: Initialize route and session id**

Add after imports:

```js
const route = useRoute()
```

Change:

```js
const sessionId = ref(null)
```

to:

```js
const sessionId = ref(parseSessionId(route.query.sessionId))
```

- [ ] **Step 3: Add query parsing and watcher**

Add helper functions before `handleAsk`:

```js
function parseSessionId(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

watch(
  () => route.query.sessionId,
  (value) => {
    sessionId.value = parseSessionId(value)
  },
)
```

`handleAsk` already sends `sessionId: sessionId.value`, so no other request change is needed.

---

### Task 4: Verify and update progress

**Files:**
- Modify: `<project-root>/docs/status/current.md`

**Interfaces:**
- Consumes successful verification output.
- Produces updated project progress and next-step note.

- [ ] **Step 1: Run helper tests**

```bash
node --test src/api/chatHistory.test.js
```

Expected: 3 tests pass.

- [ ] **Step 2: Run frontend build**

```bash
npm run build
```

Expected: build succeeds. Existing Vite chunk size and third-party PURE comment warnings are acceptable.

- [ ] **Step 3: Update progress document**

Record:

```text
前端对话历史页已接入真实后端接口：支持会话列表、消息列表、sourcesJson 查看和从历史会话继续 RAG 问答。
```

Record verification:

```text
Claude 已完成前端 Chat History API helper 测试：node --test src/api/chatHistory.test.js 通过，3 个测试全部通过。
Claude 已完成前端对话历史页构建验证：npm run build 通过；Vite 仅提示第三方 PURE 注释和 chunk 大小警告。
```

---

## Self-Review

- Spec coverage: The plan covers the new route, navigation, API helper, history page, continue-session behavior, tests, build, and progress docs.
- Placeholder scan: No placeholder text remains.
- Type consistency: Function names and endpoint paths match the design and backend controller.
