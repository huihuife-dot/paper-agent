# Stable Workbench UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the Vue frontend into a stable, cleaner research workbench layout with fixed viewport behavior, better sidebar usage, less visual clutter, and more suitable product copy.

**Architecture:** Keep existing routes and API behavior unchanged. Update `App.vue` for the shell/sidebar, `style.css` for shared workbench layout primitives, and the four existing views to use compact toolbars plus internally scrolling panels.

**Tech Stack:** Vue 3, Vue Router 4, Vite 5, Axios, Element Plus.

## Global Constraints

- Project is not a Git repository, so do not create git commits.
- This stage changes frontend layout, styling, and copy only; it does not add backend interfaces or new product capabilities.
- Preserve existing API calls and user flows.
- Keep status transition in Research Idea operation column, per user preference.
- Use stable viewport-height layout: content panels scroll internally instead of resizing the outer page.
- Verification commands: `node --test src/api/papers.test.js`, `node --test src/api/rag.test.js`, `node --test src/api/researchIdeas.test.js`, `node --test src/api/chatHistory.test.js`, and `npm run build` from `frontend/research-assistant-frontend`.

---

## File Structure

- Modify `frontend/research-assistant-frontend/src/App.vue`
  - Sidebar navigation, workflow/status blocks, concise shell copy.
- Modify `frontend/research-assistant-frontend/src/style.css`
  - App shell height, fixed workspace, compact toolbar, workbench grid, scroll panels, reduced decoration.
- Modify `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`
  - Compact toolbar, upload control panel, fixed table panel, cleaner copy.
- Modify `frontend/research-assistant-frontend/src/views/RagChatView.vue`
  - Compact toolbar, left question/session panel, right answer/source panel, cleaner copy.
- Modify `frontend/research-assistant-frontend/src/views/ChatHistoryView.vue`
  - Compact toolbar, fixed session/message panels, cleaner source copy.
- Modify `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`
  - Compact toolbar, left stats/filter panel, fixed table panel, cleaner copy.
- Modify `docs/status/current.md`
  - Record the UI redesign and verification.

---

### Task 1: Refactor shell and shared CSS

**Files:**
- Modify: `frontend/research-assistant-frontend/src/App.vue`
- Modify: `frontend/research-assistant-frontend/src/style.css`

**Interfaces:**
- Produces shared layout classes:
  - `.workspace-body`
  - `.page-toolbar`
  - `.workbench-grid`
  - `.workbench-grid.side-main`
  - `.workbench-card`
  - `.panel-scroll`
  - `.compact-form`

- [ ] **Step 1: Update `App.vue` sidebar copy and workspace wrapper**

Replace the sidebar note with workflow/status blocks and wrap `RouterView` in `.workspace-body`:

```vue
<div class="app-shell">
  <aside class="side-rail">
    <div class="brand-block">
      <span class="brand-mark">RA</span>
      <div>
        <strong>Research Desk</strong>
        <small>论文证据工作台</small>
      </div>
    </div>

    <nav class="main-nav" aria-label="主导航">
      <!-- existing links, with cleaner labels -->
    </nav>

    <div class="desk-note">
      <span>Workflow</span>
      <strong>上传文献 → 向量化 → 问答 → 沉淀想法</strong>
    </div>

    <div class="desk-note desk-status">
      <span>Stage</span>
      <strong>前端 MVP 联调</strong>
    </div>
  </aside>

  <main class="workspace">
    <header class="workspace-header">
      <div>
        <p class="eyebrow">MyAgent</p>
        <h2>论文研究工作台</h2>
      </div>
      <el-tag type="primary" effect="plain">MVP</el-tag>
    </header>

    <div class="workspace-body">
      <RouterView />
    </div>
  </main>
</div>
```

- [ ] **Step 2: Update global layout CSS**

In `style.css`, adjust shell layout:

```css
html,
body,
#app {
  height: 100%;
}

.app-shell {
  height: 100vh;
  overflow: hidden;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
}

.side-rail {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.workspace {
  min-width: 0;
  height: 100vh;
  padding: 22px clamp(18px, 3vw, 34px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.workspace-header {
  flex: 0 0 auto;
  margin-bottom: 14px;
}

.workspace-body {
  flex: 1 1 auto;
  min-height: 0;
}
```

- [ ] **Step 3: Add shared workbench classes**

Append shared classes:

```css
.page-panel {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid rgba(23, 42, 58, 0.10);
  border-radius: 20px;
  background: rgba(248, 245, 238, 0.88);
  box-shadow: 0 10px 26px rgba(23, 42, 58, 0.08);
}

.page-toolbar h1 {
  margin: 0;
  color: var(--ink);
  font-size: clamp(24px, 3vw, 34px);
  letter-spacing: -0.04em;
}

.page-toolbar p:last-child {
  max-width: 680px;
  margin: 6px 0 0;
  color: var(--muted);
  line-height: 1.55;
}

.workbench-grid {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  gap: 16px;
}

.workbench-grid.side-main {
  grid-template-columns: minmax(280px, 0.38fr) minmax(0, 1fr);
}

.workbench-card {
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.workbench-card .el-card__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}

.panel-scroll {
  height: 100%;
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
}

.compact-form .el-form-item {
  margin-bottom: 14px;
}
```

- [ ] **Step 4: Add responsive behavior**

Adjust existing media queries so desktop remains fixed and mobile can scroll:

```css
@media (max-width: 980px) {
  .app-shell {
    height: auto;
    min-height: 100vh;
    overflow: visible;
    grid-template-columns: 1fr;
  }

  .side-rail,
  .workspace {
    height: auto;
  }

  .workspace {
    overflow: visible;
  }

  .workbench-grid.side-main {
    grid-template-columns: 1fr;
  }
}
```

---

### Task 2: Refactor Paper Management view

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`

**Interfaces:**
- Preserve existing functions and API calls: `loadPapers`, `handleUpload`, `handleParse`, `handleVectorize`, `goToChat`.

- [ ] **Step 1: Replace large heading with toolbar**

Use:

```vue
<div class="page-toolbar">
  <div>
    <p class="eyebrow">Library</p>
    <h1>文献库</h1>
    <p>上传论文，完成解析和向量化，为后续问答准备证据。</p>
  </div>
  <el-button type="primary" plain :loading="loading" @click="loadPapers">刷新文献</el-button>
</div>
```

- [ ] **Step 2: Change row container to workbench grid**

Replace `el-row/el-col` outer layout with:

```vue
<div class="workbench-grid side-main">
  <el-card class="workflow-card workbench-card" shadow="never">...</el-card>
  <el-card class="workflow-card workbench-card" shadow="never">...</el-card>
</div>
```

- [ ] **Step 3: Make table scroll internally**

Wrap paper table:

```vue
<div class="panel-scroll">
  <el-table ... height="100%">
    ...
  </el-table>
</div>
```

- [ ] **Step 4: Clean copy**

Change upload tip from API wording to:

```text
选择 PDF 后上传，系统会保存到文献库。
```

Change card headers:

```text
导入论文
文献列表
```

---

### Task 3: Refactor RAG Chat view

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`

**Interfaces:**
- Preserve existing `chatWithRag`, `saveDraftFromSession`, and route query `sessionId` behavior.

- [ ] **Step 1: Replace heading with compact toolbar**

Use:

```vue
<div class="page-toolbar">
  <div>
    <p class="eyebrow">Ask</p>
    <h1>论文问答</h1>
    <p>基于已向量化的论文片段提问，保留回答和引用证据。</p>
  </div>
  <el-tag v-if="sessionId" type="info" effect="plain">Session #{{ sessionId }}</el-tag>
</div>
```

- [ ] **Step 2: Use fixed two-panel grid**

Left panel: question input, citation count, actions, session metadata.
Right panel: answer and sources.

- [ ] **Step 3: Clean labels**

Use:

```text
问题
引用数量
发送
保存为研究想法
引用片段
引用数
```

- [ ] **Step 4: Make answer/source areas scroll internally**

Wrap answer and source list in `.panel-scroll`.

---

### Task 4: Refactor Chat History view

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/ChatHistoryView.vue`

**Interfaces:**
- Preserve existing API calls and `continueSession` behavior.

- [ ] **Step 1: Replace heading with toolbar**

Use:

```vue
<div class="page-toolbar">
  <div>
    <p class="eyebrow">History</p>
    <h1>对话历史</h1>
    <p>查看历史问答，回到已有会话继续追问。</p>
  </div>
  <el-button type="primary" plain :loading="sessionsLoading" @click="loadSessions">刷新会话</el-button>
</div>
```

- [ ] **Step 2: Use fixed panels**

Session list panel and message panel use `.workbench-card` and `.panel-scroll`.

- [ ] **Step 3: Clean technical copy**

Change `查看 sourcesJson` to `查看原始引用数据`.

---

### Task 5: Refactor Research Ideas view

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`

**Interfaces:**
- Preserve existing list/filter/stats/status/delete/detail behavior.
- Keep status transition in operation column.

- [ ] **Step 1: Replace heading with toolbar**

Use:

```vue
<div class="page-toolbar">
  <div>
    <p class="eyebrow">Ideas</p>
    <h1>研究想法</h1>
    <p>整理从论文问答中沉淀出的想法，跟踪草稿、待办和已完成状态。</p>
  </div>
  <el-button type="primary" plain :loading="loading || statsLoading" @click="refreshAll">刷新</el-button>
</div>
```

- [ ] **Step 2: Move filters to left panel**

Left panel contains status stats and filters. Right panel contains table.

- [ ] **Step 3: Make table scroll internally**

Wrap idea table in `.panel-scroll`; use `height="100%"`.

- [ ] **Step 4: Clean copy**

Use:

```text
状态概览
筛选
想法列表
查看
推进状态
删除
```

---

### Task 6: Verify and update progress

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes successful test/build output.
- Produces updated project progress.

- [ ] **Step 1: Run all frontend API helper tests**

```bash
node --test src/api/papers.test.js
node --test src/api/rag.test.js
node --test src/api/researchIdeas.test.js
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
前端已完成稳定工作台 UI 重构：应用外壳固定为 100vh，页面使用紧凑工具栏和内部滚动面板，侧边栏补充工作流和阶段信息，文案改为更贴近论文研究流程。
```

Record verification:

```text
Claude 已完成稳定工作台 UI 重构验证：papers、rag、researchIdeas、chatHistory API helper 测试均通过；npm run build 通过，Vite 仅提示第三方 PURE 注释和 chunk 大小警告。
```

---

## Self-Review

- Spec coverage: Covers shell, sidebar, stable viewport, internal scrolling panels, all four pages, copy cleanup, and verification.
- Placeholder scan: No TBD/TODO placeholders remain.
- Type consistency: No API signatures changed; CSS class names are defined before use.
