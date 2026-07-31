# Top Navigation + Module Sidebars Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the frontend into a top-navigation product shell with a dashboard home page and module-specific sidebars for chat, papers, and ideas.

**Architecture:** Replace the global left sidebar with an app header and main content area. Add a dashboard view that aggregates existing APIs. Keep module sidebars inside module pages: sessions for chat, paper filters/actions for papers, idea status filters for ideas.

**Tech Stack:** Vue 3, Vue Router 4, Vite 5, Axios, Element Plus.

## Global Constraints

- Project is not a Git repository, so do not create git commits.
- No backend changes in this stage.
- Use existing frontend API helpers and existing backend endpoints.
- Top navigation shows 首页、对话、文献、想法.
- Chat, Papers, and Ideas may each have their own module sidebar.
- Main page areas must focus on core content; secondary forms/actions should move into sidebars, dialogs, or row actions.
- Verification commands: `node --test src/api/papers.test.js`, `node --test src/api/rag.test.js`, `node --test src/api/researchIdeas.test.js`, `node --test src/api/chatHistory.test.js`, and `npm run build` from `frontend/research-assistant-frontend`.

---

## File Structure

- Create `frontend/research-assistant-frontend/src/views/DashboardView.vue`
  - Home dashboard aggregating papers, sessions, and ideas data.
- Modify `frontend/research-assistant-frontend/src/router/index.js`
  - `/` points to DashboardView; keep `/papers`, `/chat`, `/ideas`.
- Modify `frontend/research-assistant-frontend/src/App.vue`
  - Top navigation shell.
- Modify `frontend/research-assistant-frontend/src/style.css`
  - Top-nav shell, module layout, dashboard cards, dialogs, module sidebar styles.
- Modify `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`
  - Convert permanent upload form into Add Paper dialog; use module sidebar for filters/actions.
- Modify `frontend/research-assistant-frontend/src/views/RagChatView.vue`
  - Keep session sidebar but adapt to new top-nav module layout.
- Modify `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`
  - Use module sidebar for stats/status filters and list as main content.
- Modify `docs/status/current.md`
  - Record this UI architecture change and verification.

---

### Task 1: Add Dashboard home and top-nav shell

**Files:**
- Create: `frontend/research-assistant-frontend/src/views/DashboardView.vue`
- Modify: `frontend/research-assistant-frontend/src/router/index.js`
- Modify: `frontend/research-assistant-frontend/src/App.vue`
- Modify: `frontend/research-assistant-frontend/src/style.css`

**Interfaces:**
- Dashboard consumes:
  - `listPapers()`
  - `listChatSessions()`
  - `listResearchIdeas()`
  - `countResearchIdeaSaveTypes()`

- [ ] **Step 1: Create DashboardView**

Create a dashboard that loads stats from existing helpers and shows cards/recent lists.

- [ ] **Step 2: Update router**

Import `DashboardView` and change `/` from redirect to component:

```js
{
  path: '/',
  name: 'dashboard',
  component: DashboardView,
  meta: { title: '首页' },
}
```

- [ ] **Step 3: Replace App shell**

`App.vue` should use:

```text
app-shell top-shell
  app-header
    brand
    nav links: 首页 / 对话 / 文献 / 想法
    stage tag
  workspace
    RouterView
```

- [ ] **Step 4: Update CSS shell**

Add/adjust:

```css
.app-shell {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.app-header {
  height: 68px;
  flex: 0 0 auto;
}

.workspace {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}

.module-layout {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
}
```

---

### Task 2: Refactor Paper page to sidebar + list + dialog

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`

**Interfaces:**
- Preserve existing API behavior.
- Add `uploadDialogVisible` state.
- Keep table row actions parse/vectorize/chat.

- [ ] **Step 1: Move upload form into dialog**

Main page should show paper list only; upload form appears in `el-dialog` when clicking `添加文献`.

- [ ] **Step 2: Add module sidebar**

Sidebar contains:

- 添加文献 button
- 全部文献 shortcut
- 待解析
- 待向量化
- 已向量化

Filtering can be frontend computed in this stage.

- [ ] **Step 3: Keep main table clean**

Main area:

- title `文献列表`
- table
- row actions only

---

### Task 3: Adapt Chat page to top-nav module layout

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`

**Interfaces:**
- Preserve session-based behavior from the previous implementation.
- Use `.module-layout.chat-module-layout` instead of global workbench shell assumptions.

- [ ] **Step 1: Keep session sidebar as module sidebar**

Left sidebar remains session list.

- [ ] **Step 2: Main area contains conversation + sources panel**

Main area can keep two inner columns:

```text
conversation timeline + composer | latest sources/actions
```

---

### Task 4: Refactor Ideas page to sidebar + list

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`

**Interfaces:**
- Preserve list, filters, stats, detail, status transition, delete.

- [ ] **Step 1: Use module sidebar for stats and status filters**

Sidebar contains stats and filters.

- [ ] **Step 2: Main area contains ideas list only**

Main list remains table with row actions.

---

### Task 5: Verify and update progress

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes successful verification.

- [ ] **Step 1: Run all frontend API helper tests**

```bash
node --test src/api/papers.test.js
node --test src/api/rag.test.js
node --test src/api/researchIdeas.test.js
node --test src/api/chatHistory.test.js
```

- [ ] **Step 2: Run frontend build**

```bash
npm run build
```

- [ ] **Step 3: Update progress document**

Record:

```text
前端信息架构已重构为顶部导航 + 模块侧边栏：新增首页统计概览，顶部导航包含首页/对话/文献/想法；对话、文献、想法模块分别使用模块内侧边栏，主内容区聚焦会话、文献列表和想法列表，添加文献等次级操作改为弹窗触发。
```

---

## Self-Review

- Spec coverage: Covers homepage, top nav, module sidebars, papers dialog, chat sessions, ideas sidebar, tests/build.
- Placeholder scan: No TBD/TODO placeholders remain.
- Type consistency: Uses existing API helper names and existing route names.
