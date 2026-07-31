# Frontend Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the Vue frontend into a clean light dashboard with top global navigation, balanced module sidebars, and an AI-style chat workspace.

**Architecture:** Keep the current Vue 3 + Vite + Element Plus app and refactor existing views instead of introducing a new UI framework. Use global layout CSS for shared shell/card/sidebar primitives, keep API clients unchanged, and add one small pure helper module for chat paper-selection state so the key interaction has direct unit coverage.

**Tech Stack:** Vue 3, Vite 5, Element Plus 2, Vue Router 4, Axios, Node built-in test runner.

## Global Constraints

- Frontend directory: `frontend/research-assistant-frontend`.
- Do not add backend APIs in this implementation.
- Do not change database schema or RAG retrieval logic.
- Keep the global layout as top navigation plus per-page module sidebars.
- Use a simple light dashboard style; do not use full-page paper texture or dark AI Lab style.
- Homepage must only show overview, recent sessions, recent ideas, and lightweight module entry buttons.
- Chat page left sidebar must show `+ 新建会话` and all session titles.
- Chat page save Research Idea entry must be inside the current conversation area, not inside the left session sidebar.
- Chat page right context panel is opened on demand for reference papers or sources.
- Reference paper selection is multi-select by default: click any paper to select it, click it again to unselect it.
- Project is not a Git repository, so do not run `git commit`; after each substantive completed implementation stage, update `docs/status/current.md`.

---

## Scope Check

The spec covers one frontend redesign surface and can be implemented as one plan. It touches several pages, but they share one visual shell and can be verified together with existing frontend tests and `npm run build`.

## File Structure

- Modify `frontend/research-assistant-frontend/src/App.vue` — keep the global top navigation shell and align labels with the approved design.
- Modify `frontend/research-assistant-frontend/src/style.css` — define the light dashboard shell, shared panels, balanced sidebars, chat layout, right context panel, and responsive rules.
- Modify `frontend/research-assistant-frontend/src/views/DashboardView.vue` — make the homepage overview-only with statistics, recent sessions, recent ideas, and lightweight module entry buttons.
- Modify `frontend/research-assistant-frontend/src/views/PaperManagementView.vue` — keep the paper list as the main area and use a balanced sidebar for status filters plus upload/refresh actions.
- Modify `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue` — keep idea table/detail as the main area and use a balanced sidebar for stats, keyword search, state filters, and refresh.
- Modify `frontend/research-assistant-frontend/src/views/RagChatView.vue` — convert chat into the approved AI-style layout: left session list, middle conversation, on-demand right context panel.
- Create `frontend/research-assistant-frontend/src/views/ragChatState.js` — pure helper functions for paper multi-select and selected-paper display text.
- Create `frontend/research-assistant-frontend/src/views/ragChatState.test.js` — Node tests for the helper functions.
- Modify `docs/status/current.md` — record completed implementation and verification results after the frontend changes pass.

---

### Task 1: Add testable chat paper-selection helpers

**Files:**
- Create: `frontend/research-assistant-frontend/src/views/ragChatState.js`
- Create: `frontend/research-assistant-frontend/src/views/ragChatState.test.js`

**Interfaces:**
- Consumes: plain arrays of paper objects from `listPapers()`.
- Produces:
  - `togglePaperSelection(selectedIds: number[], paperId: number): number[]`
  - `isPaperSelected(selectedIds: number[], paperId: number): boolean`
  - `formatSelectedPaperSummary(papers: Array<{ id: number, title?: string, fileName?: string }>, selectedIds: number[]): string`
  - `normalizePaperRows(result: unknown): Array<object>`

- [ ] **Step 1: Write the failing helper test**

Create `frontend/research-assistant-frontend/src/views/ragChatState.test.js` with this content:

```js
import assert from 'node:assert/strict'
import test from 'node:test'
import {
  formatSelectedPaperSummary,
  isPaperSelected,
  normalizePaperRows,
  togglePaperSelection,
} from './ragChatState.js'

test('togglePaperSelection adds and removes a paper id without mutating the input', () => {
  const original = [1, 3]

  const added = togglePaperSelection(original, 5)
  const removed = togglePaperSelection(added, 3)

  assert.deepEqual(original, [1, 3])
  assert.deepEqual(added, [1, 3, 5])
  assert.deepEqual(removed, [1, 5])
})

test('isPaperSelected checks selected ids with numeric conversion', () => {
  assert.equal(isPaperSelected([1, 2, 3], '2'), true)
  assert.equal(isPaperSelected([1, 2, 3], '8'), false)
})

test('formatSelectedPaperSummary describes no selection, one selection, and many selections', () => {
  const papers = [
    { id: 1, title: 'RAG Survey' },
    { id: 2, fileName: 'agent-paper.pdf' },
    { id: 3, title: 'Embedding Notes' },
  ]

  assert.equal(formatSelectedPaperSummary(papers, []), '未选择参考论文')
  assert.equal(formatSelectedPaperSummary(papers, [1]), '已选择：RAG Survey')
  assert.equal(formatSelectedPaperSummary(papers, [1, 2, 3]), '已选择 3 篇参考论文')
})

test('normalizePaperRows accepts direct arrays and common paged shapes', () => {
  const rows = [{ id: 1 }, { id: 2 }]

  assert.deepEqual(normalizePaperRows(rows), rows)
  assert.deepEqual(normalizePaperRows({ records: rows }), rows)
  assert.deepEqual(normalizePaperRows({ list: rows }), rows)
  assert.deepEqual(normalizePaperRows({ data: rows }), rows)
  assert.deepEqual(normalizePaperRows(null), [])
})
```

- [ ] **Step 2: Run the helper test to verify it fails**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/views/ragChatState.test.js
```

Expected result: FAIL with an error like `Cannot find module` for `ragChatState.js`.

- [ ] **Step 3: Implement the helper module**

Create `frontend/research-assistant-frontend/src/views/ragChatState.js` with this content:

```js
export function togglePaperSelection(selectedIds = [], paperId) {
  const normalizedPaperId = Number(paperId)

  if (!Number.isFinite(normalizedPaperId)) {
    return [...selectedIds]
  }

  if (selectedIds.map(Number).includes(normalizedPaperId)) {
    return selectedIds.map(Number).filter((id) => id !== normalizedPaperId)
  }

  return [...selectedIds.map(Number), normalizedPaperId]
}

export function isPaperSelected(selectedIds = [], paperId) {
  const normalizedPaperId = Number(paperId)

  return selectedIds.map(Number).includes(normalizedPaperId)
}

export function formatSelectedPaperSummary(papers = [], selectedIds = []) {
  const normalizedSelectedIds = selectedIds.map(Number)

  if (normalizedSelectedIds.length === 0) {
    return '未选择参考论文'
  }

  if (normalizedSelectedIds.length === 1) {
    const selectedPaper = papers.find((paper) => Number(paper.id) === normalizedSelectedIds[0])
    const paperTitle = selectedPaper?.title || selectedPaper?.fileName || `Paper #${normalizedSelectedIds[0]}`

    return `已选择：${paperTitle}`
  }

  return `已选择 ${normalizedSelectedIds.length} 篇参考论文`
}

export function normalizePaperRows(result) {
  if (Array.isArray(result)) {
    return result
  }

  return result?.records || result?.list || result?.data || []
}
```

- [ ] **Step 4: Run the helper test to verify it passes**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/views/ragChatState.test.js
```

Expected result: PASS, with output ending in `fail 0`.

---

### Task 2: Refresh the global app shell and shared light-dashboard CSS

**Files:**
- Modify: `frontend/research-assistant-frontend/src/App.vue`
- Modify: `frontend/research-assistant-frontend/src/style.css`

**Interfaces:**
- Consumes: existing Vue Router routes `/`, `/papers`, `/chat`, `/ideas`.
- Produces: shared CSS classes used by later tasks: `.app-shell`, `.app-header`, `.top-nav`, `.workspace`, `.page-panel`, `.page-toolbar`, `.module-layout`, `.module-sidebar`, `.module-main`, `.workbench-card`, `.panel-scroll`, `.sidebar-filter-item`, `.chat-workspace-grid`, `.chat-session-sidebar`, `.chat-main-panel`, `.chat-context-panel`.

- [ ] **Step 1: Replace the App shell template**

Replace the full content of `frontend/research-assistant-frontend/src/App.vue` with:

```vue
<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink to="/" class="top-brand">
        <span class="brand-mark">RA</span>
        <span>
          <strong>Research Desk</strong>
          <small>论文研究助手</small>
        </span>
      </RouterLink>

      <nav class="top-nav" aria-label="主导航">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/papers">文献</RouterLink>
        <RouterLink to="/chat">问答</RouterLink>
        <RouterLink to="/ideas">想法</RouterLink>
      </nav>

      <el-tag type="primary" effect="plain">MVP</el-tag>
    </header>

    <main class="workspace">
      <RouterView />
    </main>
  </div>
</template>
```

- [ ] **Step 2: Replace the shared CSS with the approved light dashboard system**

Replace `frontend/research-assistant-frontend/src/style.css` with this content:

```css
:root {
  font-family: Inter, "Microsoft YaHei", "PingFang SC", system-ui, sans-serif;
  color: #142033;
  background: #f3f6fb;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;

  --ink: #142033;
  --ink-soft: #314155;
  --muted: #6b7788;
  --surface: #ffffff;
  --surface-soft: #f8fafd;
  --surface-blue: #eef5ff;
  --line: #dfe7f1;
  --brand: #1d5fd1;
  --brand-deep: #164aa3;
  --green: #16845f;
  --amber: #b7791f;
  --danger: #b42318;
  --shadow-soft: 0 16px 36px rgba(20, 32, 51, 0.08);
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
}

body {
  margin: 0;
  min-width: 320px;
  min-height: 100vh;
  background: #f3f6fb;
}

button,
input,
textarea {
  font: inherit;
}

.app-shell {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f8fbff 0%, #eef3f9 100%);
}

.app-header {
  flex: 0 0 68px;
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 0 28px;
  border-bottom: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(16px);
}

.top-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--ink);
  text-decoration: none;
}

.top-brand strong,
.top-brand small,
.stat-tile span,
.stat-tile strong,
.dashboard-list-item strong,
.dashboard-list-item span,
.source-item strong,
.source-item small,
.chat-session-title,
.chat-session-meta {
  display: block;
}

.top-brand small {
  margin-top: 2px;
  color: var(--muted);
  font-size: 12px;
}

.brand-mark {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(135deg, var(--brand), var(--brand-deep));
  font-weight: 900;
  letter-spacing: 0.04em;
  box-shadow: 0 10px 22px rgba(29, 95, 209, 0.22);
}

.top-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface-soft);
}

.top-nav a {
  padding: 9px 18px;
  border-radius: 999px;
  color: var(--ink-soft);
  font-weight: 800;
  text-decoration: none;
  transition: 160ms ease;
}

.top-nav a:hover,
.top-nav a.router-link-active {
  color: #fff;
  background: var(--brand);
  box-shadow: 0 10px 20px rgba(29, 95, 209, 0.18);
}

.workspace {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  padding: 20px clamp(18px, 3vw, 34px);
}

.page-panel {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.eyebrow {
  margin: 0 0 6px;
  color: var(--brand);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.13em;
  text-transform: uppercase;
}

.page-toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
}

.page-toolbar h1 {
  margin: 0;
  color: var(--ink);
  font-size: clamp(24px, 3vw, 34px);
  letter-spacing: -0.04em;
}

.page-toolbar p:last-child {
  max-width: 760px;
  margin: 6px 0 0;
  color: var(--muted);
  line-height: 1.55;
}

.page-actions,
.console-toolbar-actions,
.table-actions,
.chat-toolbar-actions {
  flex: 0 0 auto;
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.module-layout {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
}

.module-main,
.module-sidebar {
  min-height: 0;
}

.workbench-card {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line) !important;
  border-radius: 22px !important;
  background: rgba(255, 255, 255, 0.94) !important;
  box-shadow: 0 18px 42px rgba(20, 32, 51, 0.07) !important;
}

.workbench-card :deep(.el-card__header) {
  flex: 0 0 auto;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
}

.workbench-card :deep(.el-card__body) {
  flex: 1 1 auto;
  min-height: 0;
  padding: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--ink);
  font-weight: 900;
}

.panel-scroll {
  height: 100%;
  min-height: 0;
  overflow: auto;
  padding: 14px;
}

.scroll-clean {
  scrollbar-width: thin;
  scrollbar-color: #b8c6da transparent;
}

.sidebar-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sidebar-section {
  display: grid;
  gap: 8px;
}

.sidebar-section-title {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.sidebar-filter-item,
.chat-session-item,
.reference-paper-item {
  width: 100%;
  border: 1px solid transparent;
  border-radius: 12px;
  color: var(--ink-soft);
  background: transparent;
  cursor: pointer;
  transition: 160ms ease;
}

.sidebar-filter-item {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 12px;
  text-align: left;
}

.sidebar-filter-item:hover,
.sidebar-filter-item.active,
.chat-session-item:hover,
.chat-session-item.active,
.reference-paper-item:hover,
.reference-paper-item.active {
  border-color: rgba(29, 95, 209, 0.16);
  color: var(--brand);
  background: var(--surface-blue);
}

.full-width-button {
  width: 100%;
}

.stats-grid,
.overview-stat-strip {
  display: grid;
  gap: 12px;
}

.stat-tile,
.overview-stat-item {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface-soft);
}

.stat-tile span,
.overview-stat-item span {
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
}

.stat-tile strong,
.overview-stat-item strong {
  margin-top: 6px;
  color: var(--ink);
  font-size: 26px;
  letter-spacing: -0.04em;
}

.dashboard-list {
  display: grid;
  gap: 10px;
}

.dashboard-list-item,
.source-item,
.idea-detail-block {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface-soft);
}

.dashboard-list-item span,
.source-item small,
.paper-meta {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.chat-workspace-grid {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
}

.chat-workspace-grid.context-open {
  grid-template-columns: 280px minmax(0, 1fr) 340px;
}

.chat-session-sidebar,
.chat-main-panel,
.chat-context-panel {
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 42px rgba(20, 32, 51, 0.07);
}

.chat-session-header,
.chat-panel-header,
.chat-context-header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
  font-weight: 900;
}

.chat-session-list,
.chat-message-scroll,
.chat-context-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 14px;
}

.chat-session-list {
  display: grid;
  align-content: start;
  gap: 8px;
}

.chat-session-item {
  padding: 10px 12px;
  text-align: left;
}

.chat-session-title {
  font-weight: 900;
}

.chat-session-meta {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}

.chat-message-scroll {
  display: grid;
  align-content: start;
  gap: 12px;
}

.message {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface-soft);
}

.question-message {
  margin-left: auto;
  max-width: 78%;
  border-color: rgba(29, 95, 209, 0.18);
  background: #eef5ff;
}

.answer-message {
  max-width: 86%;
}

.answer-header,
.source-title-row,
.idea-suggestion-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.message p,
.source-item p {
  margin: 8px 0 0;
  color: var(--ink-soft);
  line-height: 1.65;
  white-space: pre-wrap;
}

.message small {
  display: block;
  margin-top: 8px;
  color: var(--muted);
}

.chat-composer {
  flex: 0 0 auto;
  padding: 14px;
  border-top: 1px solid var(--line);
  background: var(--surface);
}

.chat-composer-actions {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.context-toggle-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.reference-paper-list,
.drawer-source-list {
  display: grid;
  gap: 10px;
}

.reference-paper-item {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: 10px;
  padding: 11px 12px;
  text-align: left;
}

.reference-paper-check {
  width: 16px;
  height: 16px;
  margin-top: 2px;
  border: 1px solid #9eb2ce;
  border-radius: 5px;
  background: #fff;
}

.reference-paper-item.active .reference-paper-check {
  border-color: var(--brand);
  background: var(--brand);
  box-shadow: inset 0 0 0 3px #fff;
}

.reference-paper-title {
  font-weight: 900;
}

.reference-paper-meta,
.selected-paper-summary {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}

.source-route {
  color: var(--brand);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.compact-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

@media (max-width: 1180px) {
  .module-layout,
  .chat-workspace-grid,
  .chat-workspace-grid.context-open {
    grid-template-columns: 1fr;
  }

  .workspace {
    overflow: auto;
  }

  .page-panel,
  .module-layout,
  .chat-workspace-grid {
    height: auto;
    min-height: 0;
  }
}
```

- [ ] **Step 3: Run a build check**

Run from `frontend/research-assistant-frontend`:

```bash
npm run build
```

Expected result: build succeeds. Existing Vite chunk-size warnings are acceptable; syntax errors are not acceptable.

---

### Task 3: Make the homepage overview-only

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/DashboardView.vue`

**Interfaces:**
- Consumes: `listPapers()`, `listChatSessions()`, `listResearchIdeas()`, `countResearchIdeaSaveTypes()`.
- Produces: overview homepage with `summaryStats`, `recentSessions`, and `recentIdeas`.

- [ ] **Step 1: Replace the Dashboard template with overview-only content**

In `DashboardView.vue`, replace the full `<template>...</template>` block with:

```vue
<template>
  <section class="page-panel dashboard-page">
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">Overview</p>
        <h1>研究工作台首页</h1>
        <p>查看当前研究资产状态和最近活动，再进入文献、问答或想法模块继续工作。</p>
      </div>
      <div class="console-toolbar-actions">
        <el-button plain @click="router.push('/papers')">文献库</el-button>
        <el-button plain @click="router.push('/chat')">论文问答</el-button>
        <el-button type="primary" plain :loading="loading" @click="loadDashboard">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" class="workbench-card dashboard-overview-card">
      <div class="panel-scroll">
        <div class="overview-stat-strip">
          <div v-for="item in summaryStats" :key="item.label" class="overview-stat-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <div class="overview-lower-grid">
          <section class="overview-panel">
            <div class="card-header">
              <span>最近会话</span>
              <el-tag type="info" effect="plain">{{ recentSessions.length }} 条</el-tag>
            </div>
            <div class="dashboard-list">
              <el-empty v-if="recentSessions.length === 0" description="暂无会话，先进入论文问答。" />
              <button
                v-for="session in recentSessions"
                v-else
                :key="session.id"
                class="dashboard-list-item dashboard-link-item"
                type="button"
                @click="router.push({ path: '/chat', query: { sessionId: session.id } })"
              >
                <strong>{{ session.title || `Session #${session.id}` }}</strong>
                <span>#{{ session.id }} · {{ formatDateTime(session.updateTime || session.createTime) }}</span>
              </button>
            </div>
          </section>

          <section class="overview-panel">
            <div class="card-header">
              <span>最近想法</span>
              <el-tag type="info" effect="plain">{{ recentIdeas.length }} 条</el-tag>
            </div>
            <div class="dashboard-list">
              <el-empty v-if="recentIdeas.length === 0" description="暂无研究想法，可从论文问答中保存。" />
              <button
                v-for="idea in recentIdeas"
                v-else
                :key="idea.id"
                class="dashboard-list-item dashboard-link-item"
                type="button"
                @click="router.push('/ideas')"
              >
                <strong>{{ idea.title || '未命名想法' }}</strong>
                <span>{{ saveTypeLabel(idea.saveType) }} · {{ formatDateTime(idea.updateTime || idea.createTime) }}</span>
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>
  </section>
</template>
```

- [ ] **Step 2: Add the homepage-specific CSS at the end of `style.css`**

Append this CSS to `frontend/research-assistant-frontend/src/style.css`:

```css
.dashboard-overview-card {
  flex: 1 1 auto;
}

.overview-stat-strip {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-bottom: 16px;
}

.overview-lower-grid {
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.overview-panel {
  min-height: 360px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
}

.dashboard-link-item {
  width: 100%;
  border: 1px solid var(--line);
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  transition: 160ms ease;
}

.dashboard-link-item:hover {
  border-color: rgba(29, 95, 209, 0.22);
  background: var(--surface-blue);
}

@media (max-width: 1180px) {
  .overview-stat-strip,
  .overview-lower-grid {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 3: Run the frontend build**

Run from `frontend/research-assistant-frontend`:

```bash
npm run build
```

Expected result: build succeeds.

---

### Task 4: Keep paper and idea pages as balanced-sidebar module pages

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`
- Modify: `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`

**Interfaces:**
- Consumes: existing computed values and methods in each view.
- Produces: paper and idea pages that use sidebars for filters/actions and main panels for tables/details.

- [ ] **Step 1: Verify the paper sidebar matches the approved structure**

In `PaperManagementView.vue`, ensure the sidebar template inside `<aside class="module-sidebar">` contains this structure:

```vue
<aside class="module-sidebar">
  <el-card class="workbench-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>文献侧栏</span>
        <el-tag type="info" effect="plain">{{ paperStats.total }} 篇</el-tag>
      </div>
    </template>

    <div class="panel-scroll sidebar-panel">
      <el-button type="primary" class="full-width-button" @click="uploadDialogVisible = true">上传文献</el-button>
      <el-button plain class="full-width-button" :loading="loading" @click="loadPapers">刷新文献</el-button>

      <div class="sidebar-section">
        <p class="sidebar-section-title">状态筛选</p>
        <button
          v-for="item in paperFilterOptions"
          :key="item.value"
          class="sidebar-filter-item"
          :class="{ active: paperStatusFilter === item.value }"
          type="button"
          @click="paperStatusFilter = item.value"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.count }}</strong>
        </button>
      </div>
    </div>
  </el-card>
</aside>
```

- [ ] **Step 2: Verify the paper main panel stays table-focused**

In `PaperManagementView.vue`, ensure the main area remains:

```vue
<main class="module-main">
  <el-card class="workbench-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>文献列表</span>
        <el-tag type="info" effect="plain">{{ filteredPaperRows.length }} 篇</el-tag>
      </div>
    </template>

    <div class="panel-scroll">
      <el-table v-loading="loading" :data="filteredPaperRows" border height="100%" empty-text="还没有文献，先上传一个 PDF。">
        <!-- keep the existing table columns and row actions -->
      </el-table>
    </div>
  </el-card>
</main>
```

Keep existing table columns and existing `handleParse`, `handleVectorize`, and `goToChat` behavior.

- [ ] **Step 3: Verify the ideas sidebar matches the approved structure**

In `ResearchIdeasView.vue`, ensure the left card contains:

```vue
<el-card class="workbench-card" shadow="never">
  <template #header>
    <div class="card-header">
      <span>想法侧栏</span>
      <el-tag type="info" effect="plain">{{ statsSummary.total }} 条</el-tag>
    </div>
  </template>

  <div class="panel-scroll sidebar-panel">
    <div class="stats-grid compact-stats-grid">
      <div v-for="item in stats" :key="item.label" class="stat-tile">
        <span>{{ saveTypeLabel(item.label) }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <div class="sidebar-section">
      <p class="sidebar-section-title">关键词</p>
      <el-input v-model="keyword" placeholder="搜索标题、内容或标签" clearable />
    </div>

    <div class="sidebar-section">
      <p class="sidebar-section-title">状态筛选</p>
      <button
        v-for="item in saveTypeFilterOptions"
        :key="item.value || 'all'"
        class="sidebar-filter-item"
        :class="{ active: saveType === item.value }"
        type="button"
        @click="saveType = item.value"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.count }}</strong>
      </button>
    </div>

    <el-button plain class="full-width-button" :loading="loading || statsLoading" @click="refreshAll">刷新想法</el-button>
  </div>
</el-card>
```

- [ ] **Step 4: Run existing API helper tests**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/papers.test.js src/api/researchIdeas.test.js
```

Expected result: all tests pass.

- [ ] **Step 5: Run the frontend build**

Run from `frontend/research-assistant-frontend`:

```bash
npm run build
```

Expected result: build succeeds.

---

### Task 5: Refactor the chat page into the approved AI-style workspace

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`
- Modify: `frontend/research-assistant-frontend/src/style.css`
- Uses: `frontend/research-assistant-frontend/src/views/ragChatState.js`

**Interfaces:**
- Consumes:
  - `chatWithRag(payload)` from `src/api/rag.js`
  - `listChatMessages(sessionId)` and `listChatSessions()` from `src/api/chatHistory.js`
  - `saveDraftFromSession(sessionId)` from `src/api/researchIdeas.js`
  - `listPapers()` from `src/api/papers.js`
  - `togglePaperSelection`, `isPaperSelected`, `formatSelectedPaperSummary`, `normalizePaperRows` from `src/views/ragChatState.js`
- Produces:
  - Left session list with `+ 新建会话`
  - Middle conversation area with save-idea action inside the current conversation
  - On-demand right context panel with multi-select reference papers and retrieved sources

- [ ] **Step 1: Add the required imports**

In `RagChatView.vue`, add these imports beside existing imports:

```js
import { listPapers } from '../api/papers.js'
import {
  formatSelectedPaperSummary,
  isPaperSelected,
  normalizePaperRows,
  togglePaperSelection,
} from './ragChatState.js'
```

- [ ] **Step 2: Add chat context state**

In the `<script setup>` section of `RagChatView.vue`, add these refs after the existing `sourcesDrawerVisible` ref:

```js
const papers = ref([])
const papersLoading = ref(false)
const selectedPaperIds = ref([])
const contextPanelOpen = ref(false)
const contextPanelMode = ref('papers')
```

Add this computed import at the top if it is not already present:

```js
import { computed, onMounted, ref, watch } from 'vue'
```

Add these computed values after the refs:

```js
const selectedPaperSummary = computed(() => formatSelectedPaperSummary(papers.value, selectedPaperIds.value))
const hasSources = computed(() => sources.value.length > 0)
```

If the file currently imports `onMounted, ref, watch` from Vue, replace that import with `computed, onMounted, ref, watch`.

- [ ] **Step 3: Add paper loading and context functions**

Add these functions before `onMounted(loadSessions)`:

```js
async function loadPapersForContext() {
  papersLoading.value = true

  try {
    const result = await listPapers()
    papers.value = normalizePaperRows(result)
  } catch (error) {
    ElMessage.error(`参考论文加载失败：${error.message}`)
  } finally {
    papersLoading.value = false
  }
}

function startNewSession() {
  selectedSession.value = null
  sessionId.value = null
  messages.value = []
  sources.value = []
  savedIdea.value = null
  suggestSaveAsIdea.value = false
  ideaSuggestionReason.value = ''
  question.value = ''
  router.replace({ path: '/chat' })
  openContextPanel('papers')
}

function openContextPanel(mode = 'papers') {
  contextPanelMode.value = mode
  contextPanelOpen.value = true

  if (mode === 'papers' && papers.value.length === 0) {
    loadPapersForContext()
  }
}

function closeContextPanel() {
  contextPanelOpen.value = false
}

function handlePaperToggle(paper) {
  selectedPaperIds.value = togglePaperSelection(selectedPaperIds.value, paper.id)
}

function isReferencePaperSelected(paper) {
  return isPaperSelected(selectedPaperIds.value, paper.id)
}
```

Change the bottom lifecycle call from:

```js
onMounted(loadSessions)
```

to:

```js
onMounted(async () => {
  await Promise.all([loadSessions(), loadPapersForContext()])
})
```

- [ ] **Step 4: Preserve selected paper context when sending a question**

Inside `handleAsk()`, keep the existing `chatWithRag` payload unchanged for this stage, and add a comment immediately above it:

```js
// 当前阶段后端 RAG API 尚未接收 selectedPaperIds；前端先保留 selectedPaperIds 作为会话参考范围展示。
const response = await chatWithRag({
  question: trimmedQuestion,
  topK: topK.value,
  sessionId: sessionId.value,
})
```

After sources are assigned in `handleAsk()`, add:

```js
if (sources.value.length > 0) {
  contextPanelMode.value = 'sources'
}
```

- [ ] **Step 5: Replace the chat template**

Replace the full `<template>...</template>` block in `RagChatView.vue` with:

```vue
<template>
  <section class="page-panel chat-page">
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">Chat</p>
        <h1>论文问答</h1>
        <p>左侧管理会话，中间继续追问；需要参考论文或查看引用时，再打开右侧上下文面板。</p>
      </div>
      <div class="console-toolbar-actions">
        <el-tag v-if="sessionId" type="info" effect="plain">Session #{{ sessionId }}</el-tag>
        <el-tag v-else type="info" effect="plain">新会话</el-tag>
        <el-button plain :loading="sessionsLoading" @click="loadSessions">刷新会话</el-button>
        <el-button plain @click="openContextPanel('papers')">参考论文</el-button>
        <el-button plain :disabled="!hasSources" @click="openContextPanel('sources')">引用片段 {{ sources.length }}</el-button>
      </div>
    </div>

    <div class="chat-workspace-grid" :class="{ 'context-open': contextPanelOpen }">
      <aside class="chat-session-sidebar">
        <div class="chat-session-header">
          <span>会话</span>
          <el-button type="primary" plain size="small" @click="startNewSession">+ 新建</el-button>
        </div>

        <div v-loading="sessionsLoading" class="chat-session-list scroll-clean">
          <el-empty v-if="sessions.length === 0 && !sessionsLoading" description="暂无会话，点击新建开始。" />
          <button
            v-for="session in sessions"
            v-else
            :key="session.id"
            class="chat-session-item"
            :class="{ active: selectedSession?.id === session.id }"
            type="button"
            @click="selectSession(session)"
          >
            <span class="chat-session-title">{{ session.title || `Session #${session.id}` }}</span>
            <span class="chat-session-meta">#{{ session.id }} · {{ formatDateTime(session.updateTime || session.createTime) }}</span>
          </button>
        </div>
      </aside>

      <main class="chat-main-panel">
        <div class="chat-panel-header">
          <span>{{ selectedSession ? selectedSession.title || `Session #${selectedSession.id}` : '新会话' }}</span>
          <div class="chat-toolbar-actions">
            <el-tag type="info" effect="plain">{{ selectedPaperSummary }}</el-tag>
            <el-button type="primary" plain size="small" :disabled="!sessionId" :loading="savingIdea" @click="handleSaveIdea">
              保存想法
            </el-button>
          </div>
        </div>

        <div v-loading="messagesLoading" class="chat-message-scroll scroll-clean">
          <el-empty v-if="!sessionId && messages.length === 0" description="点击左侧新建会话，选择参考论文后开始提问。" />
          <el-empty v-else-if="messages.length === 0 && !messagesLoading" description="这个会话还没有消息。" />

          <article
            v-for="message in messages"
            :key="message.id"
            class="message history-message"
            :class="message.role === 'assistant' ? 'answer-message' : 'question-message'"
          >
            <div class="answer-header">
              <strong>{{ formatMessageRole(message.role) }}</strong>
              <el-tag v-if="message.modelProvider || message.modelName" type="info" effect="plain">
                {{ formatModel(message) }}
              </el-tag>
            </div>
            <p>{{ message.content || '暂无内容' }}</p>
            <small>{{ formatDateTime(message.createTime) }}</small>

            <el-alert
              v-if="message.role === 'assistant' && suggestSaveAsIdea && message.id === messages[messages.length - 1]?.id"
              class="idea-suggestion"
              type="success"
              :closable="false"
              show-icon
              title="这轮回答适合保存为研究想法"
            >
              <div class="idea-suggestion-actions">
                <p>{{ ideaSuggestionReason || '这轮讨论包含可继续推进的研究线索。' }}</p>
                <el-button type="success" plain size="small" :loading="savingIdea" @click="handleSaveIdea">保存为想法</el-button>
              </div>
            </el-alert>
          </article>

          <el-alert
            v-if="savedIdea"
            class="idea-suggestion"
            type="info"
            :closable="false"
            show-icon
            title="已保存为研究想法草稿"
          >
            <p>{{ savedIdea.title || `Idea #${savedIdea.id}` }}</p>
          </el-alert>
        </div>

        <div class="chat-composer">
          <el-form label-position="top" class="chat-form compact-form">
            <el-form-item label="继续提问">
              <el-input
                v-model="question"
                type="textarea"
                :rows="3"
                placeholder="基于当前会话继续提问，例如：这个方法还能怎么改进？"
              />
            </el-form-item>

            <div class="chat-composer-actions">
              <el-form-item label="引用数量">
                <el-input-number v-model="topK" :min="1" :max="10" />
              </el-form-item>
              <el-button type="primary" :loading="asking" @click="handleAsk">发送</el-button>
            </div>
          </el-form>
        </div>
      </main>

      <aside v-if="contextPanelOpen" class="chat-context-panel">
        <div class="chat-context-header">
          <span>{{ contextPanelMode === 'papers' ? '参考论文' : '引用片段' }}</span>
          <div class="context-toggle-row">
            <el-button size="small" plain @click="contextPanelMode = 'papers'">论文</el-button>
            <el-button size="small" plain :disabled="!hasSources" @click="contextPanelMode = 'sources'">引用</el-button>
            <el-button size="small" text @click="closeContextPanel">关闭</el-button>
          </div>
        </div>

        <div class="chat-context-body scroll-clean">
          <template v-if="contextPanelMode === 'papers'">
            <p class="selected-paper-summary">{{ selectedPaperSummary }}</p>
            <div v-loading="papersLoading" class="reference-paper-list">
              <el-empty v-if="papers.length === 0 && !papersLoading" description="暂无可选文献，请先上传并向量化论文。" />
              <button
                v-for="paper in papers"
                v-else
                :key="paper.id"
                class="reference-paper-item"
                :class="{ active: isReferencePaperSelected(paper) }"
                type="button"
                @click="handlePaperToggle(paper)"
              >
                <span class="reference-paper-check" aria-hidden="true"></span>
                <span>
                  <strong class="reference-paper-title">{{ paper.title || paper.fileName || `Paper #${paper.id}` }}</strong>
                  <small class="reference-paper-meta">{{ paper.authors || '未知作者' }} · {{ paper.publishYear || '未知年份' }}</small>
                </span>
              </button>
            </div>
          </template>

          <template v-else>
            <div class="drawer-source-list">
              <el-empty v-if="sources.length === 0" description="发送问题后，这里会显示本轮引用片段。" />
              <div v-for="source in sources" v-else :key="source.chunkId" class="source-item">
                <div class="source-title-row">
                  <span class="source-route">{{ source.retrievalRoute || 'retrieved' }}</span>
                  <el-tag size="small" effect="plain">score {{ formatScore(source.score) }}</el-tag>
                </div>
                <strong>{{ source.paperTitle || `Paper #${source.paperId}` }}</strong>
                <small>chunk {{ source.chunkIndex ?? source.chunkId }}</small>
                <p>{{ source.content || '该引用片段暂未返回文本内容。' }}</p>
              </div>
            </div>
          </template>
        </div>
      </aside>
    </div>
  </section>
</template>
```

- [ ] **Step 6: Run chat-related tests**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/rag.test.js src/api/chatHistory.test.js src/views/ragChatState.test.js
```

Expected result: all tests pass.

- [ ] **Step 7: Run the frontend build**

Run from `frontend/research-assistant-frontend`:

```bash
npm run build
```

Expected result: build succeeds.

---

### Task 6: Final verification and progress update

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: completed Tasks 1-5.
- Produces: verified frontend redesign and updated project progress.

- [ ] **Step 1: Run all frontend helper tests**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/papers.test.js src/api/rag.test.js src/api/researchIdeas.test.js src/api/chatHistory.test.js src/views/ragChatState.test.js
```

Expected result: all tests pass.

- [ ] **Step 2: Run the production build**

Run from `frontend/research-assistant-frontend`:

```bash
npm run build
```

Expected result: build succeeds. Existing Vite warnings about chunk size or third-party annotations are acceptable; build errors are not acceptable.

- [ ] **Step 3: Browser smoke-check the redesigned pages**

Run from `frontend/research-assistant-frontend`:

```bash
npm run dev
```

Expected result: Vite prints a local URL like `http://localhost:5173/`.

Open the app in the browser and check:

```text
/
- Top navigation is visible.
- Homepage shows only overview statistics, recent sessions, recent ideas, and module entry buttons.
- Homepage does not show upload forms, full paper tables, or chat input.

/papers
- Page uses a left module sidebar for upload, refresh, and status filters.
- Main area focuses on the paper table.

/chat
- Left sidebar shows + 新建 and session titles.
- Save Research Idea is inside the conversation area.
- Right context panel is hidden by default.
- Clicking 参考论文 opens the right panel.
- Clicking papers toggles selected state; multiple papers can be selected.
- Clicking 引用片段 shows retrieved sources after a question returns sources.

/ideas
- Page uses a left module sidebar for stats, keyword search, status filters, and refresh.
- Main area focuses on the idea table and detail dialog.
```

- [ ] **Step 4: Update `docs/status/current.md` after verification**

In `docs/status/current.md`, update `更新时间` to the current date and add one concise progress line under `当前状态`:

```text
前端页面重设计已完成并验证：全局保留顶部导航，首页聚焦概览与最近活动，文献/想法页使用平衡型模块侧边栏，问答页改为主流网页 AI 式会话布局，右侧参考论文/引用片段按需打开且参考论文支持多选。
```

Add one concise verification line under `本次验证`:

```text
前端页面重设计验证完成：papers、rag、researchIdeas、chatHistory、ragChatState 测试通过，npm run build 通过，并完成首页、文献页、问答页、想法页浏览器烟测。
```

- [ ] **Step 5: Stop the dev server after browser smoke-check**

In the terminal running `npm run dev`, press:

```text
Ctrl+C
```

Expected result: Vite dev server exits and returns to the shell prompt.

---

## Self-Review

- Spec coverage: The plan covers the approved top navigation, light dashboard style, overview-only homepage, balanced sidebars, AI-style chat session list, in-conversation save idea action, on-demand right context panel, and default multi-select paper interaction.
- Placeholder scan: The plan contains exact file paths, commands, expected results, and code blocks for all new helper functions and key template/CSS changes.
- Type consistency: `togglePaperSelection`, `isPaperSelected`, `formatSelectedPaperSummary`, and `normalizePaperRows` are defined in Task 1 and consumed with the same names in Task 5.
