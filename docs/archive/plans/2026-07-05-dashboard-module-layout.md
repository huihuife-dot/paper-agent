# Dashboard Module Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved clean dashboard layout with global top navigation, module-specific sidebars, dashboard statistics, and dialog-based secondary actions.

**Architecture:** Keep the existing Vue 3 + Vite + Element Plus frontend and existing API helper modules. Refactor only the app shell, page templates/state, and global CSS so the main surface stays focused while module sidebars and dialogs hold secondary controls.

**Tech Stack:** Vue 3 `<script setup>`, Vue Router 4, Element Plus, Axios API helpers, Vite 5, Node test runner.

## Global Constraints

- Keep existing backend APIs; do not add a new backend dashboard endpoint in this stage.
- Global top nav must expose 首页、对话、文献、想法.
- 首页 has no sidebar and focuses on statistics/recent activity.
- 对话、文献、想法 each use module-specific sidebars.
- Paper upload opens from a button/dialog, not as an always-visible form.
- Keep pages clean and avoid piling every function onto the main page.
- Keep deep blue as the primary brand/accent color; use a clean light dashboard surface.
- Run frontend API helper tests and `npm run build` before marking the implementation complete.
- Project is not a Git repository, so commit steps are intentionally omitted.

---

## File Structure

- Modify `frontend/research-assistant-frontend/src/App.vue`: keep global top-nav shell and adjust copy/status tag if needed.
- Modify `frontend/research-assistant-frontend/src/views/DashboardView.vue`: strengthen dashboard stats, quick-entry cards, recent lists.
- Modify `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`: move upload form into an `el-dialog`, add sidebar stats and local filters.
- Modify `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`: refine sidebar filters/stats and keep main list focused.
- Modify `frontend/research-assistant-frontend/src/views/RagChatView.vue`: refine session sidebar and right auxiliary panel layout while preserving session behavior.
- Modify `frontend/research-assistant-frontend/src/style.css`: replace heavier decorative style with cleaner dashboard tokens and module layout utilities.
- Modify `docs/status/current.md`: record only the substantial UI redesign and verification results after implementation.

---

### Task 1: Clean App Shell and Global Dashboard Styling

**Files:**
- Modify: `frontend/research-assistant-frontend/src/App.vue`
- Modify: `frontend/research-assistant-frontend/src/style.css`

**Interfaces:**
- Consumes: existing Vue Router links: `/`, `/chat`, `/papers`, `/ideas`.
- Produces: reusable CSS classes used by later page tasks: `.app-shell`, `.app-header`, `.top-nav`, `.workspace`, `.page-panel`, `.module-layout`, `.module-sidebar`, `.module-main`, `.stat-tile`, `.panel-scroll`, `.table-actions`.

- [ ] **Step 1: Keep the global top-navigation shell**

Ensure `frontend/research-assistant-frontend/src/App.vue` has this structure:

```vue
<template>
  <div class="app-shell top-shell">
    <header class="app-header">
      <RouterLink to="/" class="top-brand">
        <span class="brand-mark">RA</span>
        <span>
          <strong>Research Desk</strong>
          <small>论文证据工作台</small>
        </span>
      </RouterLink>

      <nav class="top-nav" aria-label="主导航">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/chat">对话</RouterLink>
        <RouterLink to="/papers">文献</RouterLink>
        <RouterLink to="/ideas">想法</RouterLink>
      </nav>

      <el-tag type="primary" effect="plain">MVP 联调</el-tag>
    </header>

    <main class="workspace">
      <div class="workspace-body">
        <RouterView />
      </div>
    </main>
  </div>
</template>
```

- [ ] **Step 2: Replace global CSS tokens and layout utilities**

In `frontend/research-assistant-frontend/src/style.css`, update the global tokens and core layout classes so the app uses a clean light dashboard style. Keep existing component class names that page files already use, but simplify the visual treatment.

Use these token values at the top of the file:

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
```

Keep these core behavior rules:

```css
html,
body,
#app {
  height: 100%;
}

body {
  margin: 0;
  min-width: 320px;
  min-height: 100vh;
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
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
  box-shadow: 0 10px 30px rgba(20, 32, 51, 0.06);
}

.workspace {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 20px clamp(18px, 3vw, 34px);
}

.workspace-body {
  flex: 1 1 auto;
  min-height: 0;
}

.page-panel {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.module-layout {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
}

.module-sidebar,
.module-main {
  min-height: 0;
}
```

- [ ] **Step 3: Verify the shell renders structurally**

Run from `frontend/research-assistant-frontend`:

```bash
npm run build
```

Expected: build succeeds. Vite may warn about chunk size or third-party comments; those warnings are acceptable.

---

### Task 2: Home Dashboard Refinement

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/DashboardView.vue`
- Modify: `frontend/research-assistant-frontend/src/style.css`

**Interfaces:**
- Consumes: `listPapers()`, `listChatSessions()`, `listResearchIdeas()`, `countResearchIdeaSaveTypes()`.
- Produces: dashboard summary cards and recent activity lists.

- [ ] **Step 1: Update dashboard computed stats**

In `DashboardView.vue`, keep the existing API calls and add a computed array for top stat cards:

```js
const summaryStats = computed(() => [
  { label: '文献总数', value: paperStats.value.total, hint: `${paperStats.value.parsed} 已解析` },
  { label: '已向量化', value: paperStats.value.vectorized, hint: '可参与 RAG 检索' },
  { label: '历史会话', value: sessions.value.length, hint: '论文问答上下文' },
  { label: '研究想法', value: ideaStats.value.total, hint: `${ideaStats.value.todo} 个待办` },
])
```

- [ ] **Step 2: Replace dashboard template with focused overview**

Use this template structure:

```vue
<template>
  <section class="page-panel dashboard-page">
    <div class="dashboard-hero">
      <div>
        <p class="eyebrow">Overview</p>
        <h1>研究工作台首页</h1>
        <p>集中查看文献、会话和研究想法的当前状态，快速进入下一步研究流程。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" plain :loading="loading" @click="loadDashboard">刷新数据</el-button>
      </div>
    </div>

    <div v-loading="loading" class="dashboard-content">
      <div class="dashboard-stat-strip">
        <div v-for="item in summaryStats" :key="item.label" class="stat-tile stat-tile-large">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </div>
      </div>

      <div class="dashboard-grid refined-dashboard-grid">
        <el-card class="workflow-card dashboard-card" shadow="never">
          <template #header>文献状态</template>
          <div class="mini-stat-list">
            <div class="mini-stat-item"><span>全部</span><strong>{{ paperStats.total }}</strong></div>
            <div class="mini-stat-item"><span>已解析</span><strong>{{ paperStats.parsed }}</strong></div>
            <div class="mini-stat-item"><span>已向量化</span><strong>{{ paperStats.vectorized }}</strong></div>
          </div>
        </el-card>

        <el-card class="workflow-card dashboard-card" shadow="never">
          <template #header>想法状态</template>
          <div class="mini-stat-list two-column">
            <div class="mini-stat-item"><span>草稿</span><strong>{{ ideaStats.draft }}</strong></div>
            <div class="mini-stat-item"><span>想法</span><strong>{{ ideaStats.idea }}</strong></div>
            <div class="mini-stat-item"><span>待办</span><strong>{{ ideaStats.todo }}</strong></div>
            <div class="mini-stat-item"><span>已完成</span><strong>{{ ideaStats.implemented }}</strong></div>
          </div>
        </el-card>

        <el-card class="workflow-card dashboard-card" shadow="never">
          <template #header>快速入口</template>
          <div class="quick-entry-list">
            <RouterLink to="/chat">进入论文问答</RouterLink>
            <RouterLink to="/papers">管理文献库</RouterLink>
            <RouterLink to="/ideas">整理研究想法</RouterLink>
          </div>
        </el-card>

        <el-card class="workflow-card dashboard-card dashboard-list-card" shadow="never">
          <template #header>最近会话</template>
          <div class="panel-scroll">
            <el-empty v-if="recentSessions.length === 0" description="暂无会话，先进入论文问答。" />
            <div v-else class="dashboard-list">
              <div v-for="session in recentSessions" :key="session.id" class="dashboard-list-item">
                <strong>{{ session.title || `Session #${session.id}` }}</strong>
                <span>#{{ session.id }} · {{ formatDateTime(session.updateTime || session.createTime) }}</span>
              </div>
            </div>
          </div>
        </el-card>

        <el-card class="workflow-card dashboard-card dashboard-list-card" shadow="never">
          <template #header>最近想法</template>
          <div class="panel-scroll">
            <el-empty v-if="recentIdeas.length === 0" description="暂无研究想法，可从论文问答中保存。" />
            <div v-else class="dashboard-list">
              <div v-for="idea in recentIdeas" :key="idea.id" class="dashboard-list-item">
                <strong>{{ idea.title || '未命名想法' }}</strong>
                <span>{{ saveTypeLabel(idea.saveType) }} · {{ formatDateTime(idea.updateTime || idea.createTime) }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </section>
</template>
```

- [ ] **Step 3: Add dashboard-specific CSS**

Add styles for `.dashboard-hero`, `.dashboard-content`, `.dashboard-stat-strip`, `.stat-tile-large`, `.mini-stat-list`, `.quick-entry-list`, and `.refined-dashboard-grid` to `style.css`.

- [ ] **Step 4: Verify dashboard build**

Run:

```bash
npm run build
```

Expected: build succeeds.

---

### Task 3: Paper Module Sidebar and Upload Dialog

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`
- Modify: `frontend/research-assistant-frontend/src/style.css`

**Interfaces:**
- Consumes: existing `paperRows`, upload form state, `handleUpload()`, `handleParse(row)`, `handleVectorize(row)`, `goToChat()`.
- Produces: `filteredPaperRows` computed array and dialog-based upload flow.

- [ ] **Step 1: Add imports and local filter state**

Change the Vue import to include `computed`:

```js
import { computed, onMounted, reactive, ref } from 'vue'
```

Add state after `const selectedFile = ref(null)`:

```js
const uploadDialogVisible = ref(false)
const paperStatusFilter = ref('all')
```

- [ ] **Step 2: Add paper status statistics and filtered rows**

Add these computed values:

```js
const paperStats = computed(() => ({
  total: paperRows.value.length,
  pendingParse: paperRows.value.filter((paper) => !['parsed', 'success'].includes(paper.parseStatus)).length,
  pendingVector: paperRows.value.filter((paper) => !['vectorized', 'success'].includes(paper.vectorStatus)).length,
  vectorized: paperRows.value.filter((paper) => ['vectorized', 'success'].includes(paper.vectorStatus)).length,
}))

const paperFilterOptions = computed(() => [
  { label: '全部文献', value: 'all', count: paperStats.value.total },
  { label: '待解析', value: 'pendingParse', count: paperStats.value.pendingParse },
  { label: '待向量化', value: 'pendingVector', count: paperStats.value.pendingVector },
  { label: '已向量化', value: 'vectorized', count: paperStats.value.vectorized },
])

const filteredPaperRows = computed(() => {
  if (paperStatusFilter.value === 'pendingParse') {
    return paperRows.value.filter((paper) => !['parsed', 'success'].includes(paper.parseStatus))
  }

  if (paperStatusFilter.value === 'pendingVector') {
    return paperRows.value.filter((paper) => !['vectorized', 'success'].includes(paper.vectorStatus))
  }

  if (paperStatusFilter.value === 'vectorized') {
    return paperRows.value.filter((paper) => ['vectorized', 'success'].includes(paper.vectorStatus))
  }

  return paperRows.value
})
```

- [ ] **Step 3: Close dialog after successful upload**

Inside `handleUpload()`, after `resetUploadForm()`, set:

```js
uploadDialogVisible.value = false
```

- [ ] **Step 4: Replace paper page template structure**

Use a module layout:

```vue
<div class="module-layout paper-module-layout">
  <aside class="module-sidebar">
    <el-card class="workflow-card workbench-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>文献侧栏</span>
          <el-tag type="info" effect="plain">{{ paperStats.total }} 篇</el-tag>
        </div>
      </template>
      <div class="panel-scroll sidebar-panel">
        <el-button type="primary" class="full-width-button" @click="uploadDialogVisible = true">添加文献</el-button>
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

  <main class="module-main">
    <el-card class="workflow-card workbench-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>文献列表</span>
          <el-tag type="info" effect="plain">{{ filteredPaperRows.length }} 篇</el-tag>
        </div>
      </template>
      <div class="panel-scroll">
        <el-table v-loading="loading" :data="filteredPaperRows" border height="100%" empty-text="还没有文献，先上传一个 PDF。">
          <!-- keep existing columns unchanged -->
        </el-table>
      </div>
    </el-card>
  </main>
</div>
```

Move the current upload form into:

```vue
<el-dialog v-model="uploadDialogVisible" title="添加文献" width="680px" destroy-on-close>
  <!-- current upload form -->
</el-dialog>
```

- [ ] **Step 5: Verify paper API helper tests and build**

Run:

```bash
node --test src/api/papers.test.js
npm run build
```

Expected: API helper tests pass and build succeeds.

---

### Task 4: Idea Module Sidebar Refinement

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`
- Modify: `frontend/research-assistant-frontend/src/style.css`

**Interfaces:**
- Consumes: existing `keyword`, `saveType`, `stats`, `statsSummary`, `ideas`, `refreshAll()`.
- Produces: sidebar filter buttons and focused main list.

- [ ] **Step 1: Add filter option computed array**

Add this computed value below `stats`:

```js
const saveTypeFilterOptions = computed(() => [
  { label: '全部', value: '', count: statsSummary.value.total },
  { label: '草稿', value: 'draft', count: statsSummary.value.draft },
  { label: '想法', value: 'idea', count: statsSummary.value.idea },
  { label: '待办', value: 'todo', count: statsSummary.value.todo },
  { label: '已实现', value: 'implemented', count: statsSummary.value.implemented },
])
```

- [ ] **Step 2: Replace the sidebar filter controls**

In the sidebar card body, use:

```vue
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
```

- [ ] **Step 3: Keep the idea table main area focused**

Ensure the main card header says `想法列表` and the tag uses `{{ ideas.length }} 条`. Keep row actions: 查看、推进状态、删除.

- [ ] **Step 4: Verify idea API helper tests and build**

Run:

```bash
node --test src/api/researchIdeas.test.js
npm run build
```

Expected: API helper tests pass and build succeeds.

---

### Task 5: Chat Module Polish

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`
- Modify: `frontend/research-assistant-frontend/src/style.css`

**Interfaces:**
- Consumes: existing session list, message list, `selectSession(session)`, `handleAsk()`, `handleSaveIdea()`.
- Produces: polished chat workspace with clear session sidebar, conversation center, sources/action right panel.

- [ ] **Step 1: Rename visible section copy**

Update page toolbar copy to make the module clearer:

```vue
<p class="eyebrow">Chat</p>
<h1>对话</h1>
<p>按会话 ID 管理论文问答历史，选择一个会话后继续基于上下文追问。</p>
```

- [ ] **Step 2: Rename chat sidebar header**

Change the first card header label from `会话列表` to `会话侧栏` and keep the refresh button.

- [ ] **Step 3: Keep main conversation behavior unchanged**

Do not change `loadSessions()`, `selectSession()`, `loadMessages()`, `handleAsk()`, or `handleSaveIdea()` except for safe copy/styling adjustments.

- [ ] **Step 4: Verify chat API helper tests and build**

Run:

```bash
node --test src/api/rag.test.js
node --test src/api/chatHistory.test.js
npm run build
```

Expected: API helper tests pass and build succeeds.

---

### Task 6: Final Verification and Progress Update

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: completed UI changes and verification command outputs.
- Produces: concise project progress update.

- [ ] **Step 1: Run all frontend tests**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/papers.test.js
node --test src/api/rag.test.js
node --test src/api/researchIdeas.test.js
node --test src/api/chatHistory.test.js
```

Expected: all tests pass.

- [ ] **Step 2: Run production build**

Run:

```bash
npm run build
```

Expected: build succeeds. Vite warnings about chunk size or third-party comments are acceptable.

- [ ] **Step 3: Update `docs/status/current.md` current status**

Add concise lines to the current status/module progress/verification sections:

```text
前端已完成 Dashboard + 模块侧边栏布局改造：首页聚合统计和最近活动；对话、文献、想法页采用模块侧边栏；文献上传改为弹窗入口，页面主区域更聚焦。
```

Verification line:

```text
Claude 已完成 Dashboard + 模块侧边栏布局改造验证：papers、rag、researchIdeas、chatHistory API helper 测试均通过；npm run build 通过，Vite 仅提示第三方 PURE 注释和 chunk 大小警告。
```

- [ ] **Step 4: Report final result**

Summarize:

- design doc path;
- plan doc path;
- changed frontend behavior;
- verification commands and outcomes;
- any warnings that do not block completion.
