# Research Ideas Frontend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the Research Idea page to the real backend APIs for listing, filtering, status statistics, saveType transitions, detail viewing, and deletion.

**Architecture:** Keep the existing Vue 3 + Element Plus structure. Put HTTP concerns in `src/api/researchIdeas.js`, keep UI state and user interactions in `src/views/ResearchIdeasView.vue`, and verify with the existing Node test file plus a Vite production build.

**Tech Stack:** Vue 3, Vite 5, Axios, Element Plus, Vue Router 4, Node built-in test runner.

## Global Constraints

- Project is not a Git repository, so do not create git commits.
- Follow the existing frontend style and API helper pattern.
- Keep this stage focused on the Research Idea MVP page; do not add a rich editor or unrelated features.
- Backend endpoints already available:
  - `GET /api/research-ideas?keyword=xxx&sourceType=rag_chat&saveType=draft&sourceSessionId=123`
  - `PATCH /api/research-ideas/{id}/save-type`
  - `GET /api/research-ideas/stats/save-type`
  - `DELETE /api/research-ideas/{id}`
  - `POST /api/research-ideas/save-draft-from-session/{sessionId}`

---

## File Structure

- Modify `frontend/research-assistant-frontend/src/api/researchIdeas.js`
  - Responsibility: expose focused API helper functions for Research Idea endpoints.
- Modify `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`
  - Responsibility: render stats, filters, table, status transition actions, detail dialog, and delete flow using the API helper.
- Verify `frontend/research-assistant-frontend/src/api/researchIdeas.test.js`
  - Responsibility: existing test coverage for helper request paths and payloads.
- Modify `docs/status/current.md`
  - Responsibility: record this substantive frontend module progress after successful verification.

---

### Task 1: Complete Research Idea API helpers

**Files:**
- Modify: `frontend/research-assistant-frontend/src/api/researchIdeas.js`
- Test: `frontend/research-assistant-frontend/src/api/researchIdeas.test.js`

**Interfaces:**
- Consumes: `apiClient` with `get`, `post`, `patch`, and `delete` methods returning response data from the Axios interceptor.
- Produces:
  - `listResearchIdeas(filters, client = apiClient): Promise<any>`
  - `countResearchIdeaSaveTypes(client = apiClient): Promise<any>`
  - `updateResearchIdeaSaveType(id, saveType, client = apiClient): Promise<any>`
  - `deleteResearchIdea(id, client = apiClient): Promise<any>`
  - `saveDraftFromSession(sessionId, client = apiClient): Promise<any>`

- [ ] **Step 1: Replace API helper content with complete functions**

```js
import apiClient from './client.js'

function unwrapResult(result) {
  return result.data ?? result
}

function compactFilters(filters = {}) {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== '' && value !== null && value !== undefined),
  )
}

export function listResearchIdeas(filters = {}, client = apiClient) {
  return client
    .get('/api/research-ideas', {
      params: compactFilters(filters),
    })
    .then(unwrapResult)
}

export function countResearchIdeaSaveTypes(client = apiClient) {
  return client.get('/api/research-ideas/stats/save-type').then(unwrapResult)
}

export function updateResearchIdeaSaveType(id, saveType, client = apiClient) {
  return client
    .patch(`/api/research-ideas/${id}/save-type`, { saveType })
    .then(unwrapResult)
}

export function deleteResearchIdea(id, client = apiClient) {
  return client.delete(`/api/research-ideas/${id}`).then(unwrapResult)
}

export function saveDraftFromSession(sessionId, client = apiClient) {
  return client
    .post(`/api/research-ideas/save-draft-from-session/${sessionId}`)
    .then(unwrapResult)
}
```

- [ ] **Step 2: Run helper tests**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/researchIdeas.test.js
```

Expected: all 5 tests pass.

---

### Task 2: Connect Research Ideas view to real data

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`

**Interfaces:**
- Consumes:
  - `listResearchIdeas({ keyword, saveType })`
  - `countResearchIdeaSaveTypes()`
  - `updateResearchIdeaSaveType(id, saveType)`
  - `deleteResearchIdea(id)`
- Produces: a page that loads real stats and table rows, filters by keyword/status, updates saveType, deletes ideas, and shows detail dialog.

- [ ] **Step 1: Import Vue lifecycle/computed helpers and API helpers**

```js
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  countResearchIdeaSaveTypes,
  deleteResearchIdea,
  listResearchIdeas,
  updateResearchIdeaSaveType,
} from '../api/researchIdeas.js'
```

- [ ] **Step 2: Replace static stats and rows with reactive state**

```js
const keyword = ref('')
const saveType = ref('')
const ideas = ref([])
const statsSummary = ref({ draft: 0, idea: 0, todo: 0, implemented: 0, total: 0 })
const loading = ref(false)
const selectedIdea = ref(null)
const detailVisible = ref(false)
```

- [ ] **Step 3: Add loading functions and data normalization**

```js
function normalizeIdeaList(result) {
  if (Array.isArray(result)) {
    return result
  }

  return result?.records || result?.list || result?.data || []
}

async function loadIdeas() {
  loading.value = true
  try {
    const result = await listResearchIdeas({
      keyword: keyword.value.trim(),
      saveType: saveType.value,
    })
    ideas.value = normalizeIdeaList(result)
  } catch (error) {
    ElMessage.error(error.message || 'Research Idea 列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const result = await countResearchIdeaSaveTypes()
    statsSummary.value = {
      draft: result?.draft ?? 0,
      idea: result?.idea ?? 0,
      todo: result?.todo ?? 0,
      implemented: result?.implemented ?? 0,
      total: result?.total ?? 0,
    }
  } catch (error) {
    ElMessage.error(error.message || 'Research Idea 状态统计加载失败')
  }
}
```

- [ ] **Step 4: Add status update, delete, detail, and refresh handlers**

```js
async function refreshAll() {
  await Promise.all([loadIdeas(), loadStats()])
}

async function handleSaveTypeChange(row, nextSaveType) {
  try {
    await updateResearchIdeaSaveType(row.id, nextSaveType)
    ElMessage.success('状态已更新')
    await refreshAll()
  } catch (error) {
    ElMessage.error(error.message || '状态更新失败')
  }
}

function openDetail(row) {
  selectedIdea.value = row
  detailVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title || '未命名 Idea'}」吗？`, '删除 Research Idea', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteResearchIdea(row.id)
    ElMessage.success('已删除')
    await refreshAll()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}
```

- [ ] **Step 5: Add computed display helpers and watchers**

```js
const stats = computed(() => [
  { label: 'draft', value: statsSummary.value.draft },
  { label: 'idea', value: statsSummary.value.idea },
  { label: 'todo', value: statsSummary.value.todo },
  { label: 'implemented', value: statsSummary.value.implemented },
])

function formatTags(tags) {
  if (Array.isArray(tags)) {
    return tags.join(', ')
  }
  return tags || '-'
}

let filterTimer
watch([keyword, saveType], () => {
  clearTimeout(filterTimer)
  filterTimer = setTimeout(loadIdeas, 300)
})

onMounted(refreshAll)
```

- [ ] **Step 6: Update template bindings**

Use:

```vue
<el-table v-loading="loading" :data="ideas" border empty-text="暂无 Research Idea">
```

Use scoped row slots for actions:

```vue
<template #default="{ row }">
  <el-button size="small" @click="openDetail(row)">查看</el-button>
  <el-dropdown trigger="click" @command="(nextSaveType) => handleSaveTypeChange(row, nextSaveType)">
    <el-button size="small" type="primary" plain>推进状态</el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="draft">草稿</el-dropdown-item>
        <el-dropdown-item command="idea">想法</el-dropdown-item>
        <el-dropdown-item command="todo">待办</el-dropdown-item>
        <el-dropdown-item command="implemented">已实现</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
</template>
```

Add a detail dialog that reads `selectedIdea` and displays title, content, tags, saveType, sourceType, and sourceSessionId.

---

### Task 3: Verify and update progress

**Files:**
- Modify: `<project-root>/docs/status/current.md`

**Interfaces:**
- Consumes: successful output from helper tests and Vite build.
- Produces: progress document records that Research Idea frontend real API integration is complete.

- [ ] **Step 1: Run helper tests**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/researchIdeas.test.js
```

Expected: all 5 tests pass.

- [ ] **Step 2: Run production build**

Run from `frontend/research-assistant-frontend`:

```bash
npm run build
```

Expected: build succeeds. Vite chunk size warnings are acceptable because prior frontend stages already had the same warning.

- [ ] **Step 3: Update progress document**

Add to completed frontend progress that Research Idea page now uses real backend APIs for list, filters, stats, saveType transition, detail, and deletion.

---

## Self-Review

- Spec coverage: This plan covers API helper completion, page integration, verification, and progress documentation.
- Placeholder scan: No TBD/TODO placeholders remain.
- Type consistency: Function names and endpoint paths match the existing tests and backend progress document.
