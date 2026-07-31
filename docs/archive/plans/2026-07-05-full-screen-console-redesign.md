# Full-Screen Console Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the frontend from a card-heavy dashboard into a flatter full-screen console layout with fewer visible boxes, hidden scrollbars, menu-driven secondary actions, and a drawer-based chat sources panel.

**Architecture:** Keep the current Vue 3 + Vite + Element Plus app and existing API helpers. Refactor page templates and shared CSS classes so each route has a toolbar plus one full-height content frame, with optional flat side menus and popup/drawer secondary surfaces.

**Tech Stack:** Vue 3 `<script setup>`, Vue Router 4, Element Plus, Axios API helpers, Vite 5, Node test runner.

## Global Constraints

- Do not use many obvious long/short uneven cards.
- Make pages fill the available screen instead of looking like scattered blocks.
- Use options, menus, dialogs, and drawers for secondary functions.
- Avoid visible scrollbars while content remains scrollable.
- Chat page must move sources and save-idea operations into top buttons and a drawer instead of a permanent right-side panel.
- Papers and ideas pages use flat side menus and full-height tables.
- Keep existing backend APIs and current API helper behavior.
- Run frontend API helper tests and `npm run build` before completion.
- Project is not a Git repository, so commit steps are intentionally omitted.

---

## File Structure

- Modify `frontend/research-assistant-frontend/src/style.css`: add full-screen console frame, flat side menu, hidden scrollbar utilities, and reduce card-heavy visuals.
- Modify `frontend/research-assistant-frontend/src/views/DashboardView.vue`: replace staggered dashboard grid with one full-height overview frame, stat strip, and equal recent panels.
- Modify `frontend/research-assistant-frontend/src/views/RagChatView.vue`: remove permanent right sources panel and add drawer-based sources display plus toolbar save action.
- Modify `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`: keep upload dialog, flatten sidebar into a menu, and make main table fill the content frame.
- Modify `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`: move search to toolbar, flatten status filters into side menu, and keep main table full height.
- Modify `docs/status/current.md`: record the substantial UI correction and verification results after implementation.

---

### Task 1: Shared Full-Screen Console CSS

**Files:**
- Modify: `frontend/research-assistant-frontend/src/style.css`

**Interfaces:**
- Produces CSS classes used by later tasks: `.console-frame`, `.console-grid`, `.console-menu`, `.console-main`, `.console-toolbar-actions`, `.scroll-clean`, `.flat-menu-item`, `.overview-frame`, `.overview-stat-strip`, `.overview-lower-grid`, `.drawer-source-list`.

- [ ] **Step 1: Add hidden-scrollbar utility**

Add this CSS after `.panel-scroll`:

```css
.scroll-clean,
.panel-scroll {
  scrollbar-width: none;
}

.scroll-clean::-webkit-scrollbar,
.panel-scroll::-webkit-scrollbar {
  width: 0;
  height: 0;
}
```

- [ ] **Step 2: Add full-screen console frame styles**

Add these styles:

```css
.console-frame {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 42px rgba(20, 32, 51, 0.07);
}

.console-grid {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
}

.console-menu {
  min-height: 0;
  overflow: auto;
  padding: 16px;
  border-right: 1px solid var(--line);
  background: #f7faff;
}

.console-main {
  min-height: 0;
  overflow: hidden;
  padding: 16px;
  background: var(--surface);
}

.console-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
```

- [ ] **Step 3: Add flat menu styles**

Add these styles:

```css
.flat-menu-list {
  display: grid;
  gap: 6px;
}

.flat-menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 11px 12px;
  border: 0;
  border-radius: 12px;
  color: var(--ink-soft);
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition: 160ms ease;
}

.flat-menu-item:hover,
.flat-menu-item.active {
  color: var(--brand);
  background: #eaf2ff;
}

.flat-menu-item span {
  font-weight: 800;
}

.flat-menu-item strong {
  color: inherit;
}
```

- [ ] **Step 4: Add overview and drawer styles**

Add these styles:

```css
.overview-frame {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 16px;
  overflow: hidden;
}

.overview-stat-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border: 1px solid var(--line);
  border-radius: 18px;
  overflow: hidden;
  background: var(--surface);
}

.overview-stat-item {
  padding: 18px;
  border-right: 1px solid var(--line);
}

.overview-stat-item:last-child {
  border-right: 0;
}

.overview-stat-item span {
  display: block;
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
}

.overview-stat-item strong {
  display: block;
  margin-top: 8px;
  color: var(--ink);
  font-size: 30px;
}

.overview-lower-grid {
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.overview-panel {
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
}

.overview-panel-header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
  color: var(--ink);
  font-weight: 900;
}

.overview-panel-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 12px;
}

.drawer-source-list {
  display: grid;
  gap: 12px;
}
```

- [ ] **Step 5: Reduce card-heavy shadows**

Change `.workflow-card, .workbench-card` box-shadow to a lighter value:

```css
box-shadow: none;
```

- [ ] **Step 6: Verify CSS still builds**

Run from `frontend/research-assistant-frontend`:

```bash
npm run build
```

Expected: build succeeds. Existing Vite PURE/chunk warnings are acceptable.

---

### Task 2: Home Full-Screen Overview

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/DashboardView.vue`

**Interfaces:**
- Consumes existing `summaryStats`, `recentSessions`, `recentIdeas`, `loadDashboard()`, `formatDateTime()`, `saveTypeLabel()`.
- Produces one full-height overview frame with equal lower panels.

- [ ] **Step 1: Expand summary stats to five columns**

Update `summaryStats` to include five top-level numbers:

```js
const summaryStats = computed(() => [
  { label: '文献总数', value: paperStats.value.total },
  { label: '已解析', value: paperStats.value.parsed },
  { label: '已向量化', value: paperStats.value.vectorized },
  { label: '历史会话', value: sessions.value.length },
  { label: '研究想法', value: ideaStats.value.total },
])
```

- [ ] **Step 2: Replace template with full-screen overview**

Use this structure:

```vue
<template>
  <section class="page-panel dashboard-page">
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">Overview</p>
        <h1>研究工作台首页</h1>
        <p>集中查看当前研究资产状态，选择下一步进入对话、文献或想法管理。</p>
      </div>
      <div class="console-toolbar-actions">
        <el-button plain @click="$router.push('/chat')">进入对话</el-button>
        <el-button plain @click="$router.push('/papers')">文献管理</el-button>
        <el-button type="primary" plain :loading="loading" @click="loadDashboard">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" class="console-frame">
      <div class="overview-frame">
        <div class="overview-stat-strip">
          <div v-for="item in summaryStats" :key="item.label" class="overview-stat-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <div class="overview-lower-grid">
          <section class="overview-panel">
            <div class="overview-panel-header">
              <span>最近会话</span>
              <el-tag type="info" effect="plain">{{ recentSessions.length }} 条</el-tag>
            </div>
            <div class="overview-panel-body scroll-clean">
              <el-empty v-if="recentSessions.length === 0" description="暂无会话，先进入论文问答。" />
              <div v-else class="dashboard-list">
                <div v-for="session in recentSessions" :key="session.id" class="dashboard-list-item">
                  <strong>{{ session.title || `Session #${session.id}` }}</strong>
                  <span>#{{ session.id }} · {{ formatDateTime(session.updateTime || session.createTime) }}</span>
                </div>
              </div>
            </div>
          </section>

          <section class="overview-panel">
            <div class="overview-panel-header">
              <span>最近想法</span>
              <el-tag type="info" effect="plain">{{ recentIdeas.length }} 条</el-tag>
            </div>
            <div class="overview-panel-body scroll-clean">
              <el-empty v-if="recentIdeas.length === 0" description="暂无研究想法，可从论文问答中保存。" />
              <div v-else class="dashboard-list">
                <div v-for="idea in recentIdeas" :key="idea.id" class="dashboard-list-item">
                  <strong>{{ idea.title || '未命名想法' }}</strong>
                  <span>{{ saveTypeLabel(idea.saveType) }} · {{ formatDateTime(idea.updateTime || idea.createTime) }}</span>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  </section>
</template>
```

- [ ] **Step 3: Verify dashboard build**

Run:

```bash
npm run build
```

Expected: build succeeds.

---

### Task 3: Chat Two-Column Layout with Sources Drawer

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`

**Interfaces:**
- Consumes existing `sources`, `sessionId`, `savingIdea`, `handleSaveIdea()`, `loadSessions()`, `selectSession()`, `handleAsk()`.
- Produces new `sourcesDrawerVisible` ref and a drawer for source display.

- [ ] **Step 1: Add drawer visibility state**

Add after `const savedIdea = ref(null)`:

```js
const sourcesDrawerVisible = ref(false)
```

- [ ] **Step 2: Replace top actions**

In the page toolbar actions, use:

```vue
<div class="console-toolbar-actions">
  <el-tag v-if="sessionId" type="info" effect="plain">Session #{{ sessionId }}</el-tag>
  <el-button plain :loading="sessionsLoading" @click="loadSessions">刷新会话</el-button>
  <el-button plain :disabled="sources.length === 0" @click="sourcesDrawerVisible = true">
    引用片段 {{ sources.length }}
  </el-button>
  <el-button type="primary" plain :disabled="!sessionId" :loading="savingIdea" @click="handleSaveIdea">
    保存想法
  </el-button>
</div>
```

- [ ] **Step 3: Replace three-column layout with console frame**

Use this content structure:

```vue
<div class="console-frame">
  <div class="console-grid chat-console-grid">
    <aside class="console-menu scroll-clean">
      <div class="flat-menu-list">
        <button
          v-for="session in sessions"
          :key="session.id"
          class="flat-menu-item"
          :class="{ active: selectedSession?.id === session.id }"
          type="button"
          @click="selectSession(session)"
        >
          <span>{{ session.title || `Session #${session.id}` }}</span>
          <strong>#{{ session.id }}</strong>
        </button>
      </div>
    </aside>

    <main class="console-main">
      <!-- keep current conversation card/panel content here, without the old right-side sources card -->
    </main>
  </div>
</div>
```

- [ ] **Step 4: Add sources drawer**

Add after the console frame:

```vue
<el-drawer v-model="sourcesDrawerVisible" title="引用片段" size="420px">
  <div class="drawer-source-list scroll-clean">
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
</el-drawer>
```

- [ ] **Step 5: Keep save feedback visible**

Move existing `suggestSaveAsIdea` and `savedIdea` alerts into the conversation panel above the composer so the user still sees save prompts without a right panel.

- [ ] **Step 6: Verify chat tests and build**

Run:

```bash
node --test src/api/rag.test.js
node --test src/api/chatHistory.test.js
npm run build
```

Expected: tests pass and build succeeds.

---

### Task 4: Papers Flat Menu and Full Table

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`

**Interfaces:**
- Consumes existing `paperFilterOptions`, `paperStatusFilter`, `filteredPaperRows`, `uploadDialogVisible`, `loadPapers()`.
- Produces flat side menu and full table frame.

- [ ] **Step 1: Move toolbar actions to the top**

Use toolbar actions:

```vue
<div class="console-toolbar-actions">
  <el-button type="primary" @click="uploadDialogVisible = true">添加文献</el-button>
  <el-button plain :loading="loading" @click="loadPapers">刷新</el-button>
</div>
```

- [ ] **Step 2: Replace sidebar card with flat console menu**

Use:

```vue
<div class="console-frame">
  <div class="console-grid">
    <aside class="console-menu scroll-clean">
      <div class="flat-menu-list">
        <button
          v-for="item in paperFilterOptions"
          :key="item.value"
          class="flat-menu-item"
          :class="{ active: paperStatusFilter === item.value }"
          type="button"
          @click="paperStatusFilter = item.value"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.count }}</strong>
        </button>
      </div>
    </aside>

    <main class="console-main">
      <!-- table fills this area -->
    </main>
  </div>
</div>
```

- [ ] **Step 3: Keep upload dialog unchanged**

Keep the existing `el-dialog` upload form and `handleUpload()` behavior.

- [ ] **Step 4: Verify paper tests and build**

Run:

```bash
node --test src/api/papers.test.js
npm run build
```

Expected: tests pass and build succeeds.

---

### Task 5: Ideas Toolbar Search and Flat Menu

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`

**Interfaces:**
- Consumes existing `keyword`, `saveType`, `saveTypeFilterOptions`, `ideas`, `refreshAll()`.
- Produces top toolbar search and flat side menu.

- [ ] **Step 1: Move search into toolbar**

Use toolbar actions:

```vue
<div class="console-toolbar-actions">
  <el-input v-model="keyword" class="toolbar-search" placeholder="搜索标题、内容或标签" clearable />
  <el-button plain :loading="loading || statsLoading" @click="refreshAll">刷新</el-button>
</div>
```

- [ ] **Step 2: Replace sidebar card with flat menu**

Use:

```vue
<div class="console-frame">
  <div class="console-grid">
    <aside class="console-menu scroll-clean">
      <div class="flat-menu-list">
        <button
          v-for="item in saveTypeFilterOptions"
          :key="item.value || 'all'"
          class="flat-menu-item"
          :class="{ active: saveType === item.value }"
          type="button"
          @click="saveType = item.value"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.count }}</strong>
        </button>
      </div>
    </aside>

    <main class="console-main">
      <!-- idea table fills this area -->
    </main>
  </div>
</div>
```

- [ ] **Step 3: Add toolbar search width CSS**

Add to `style.css`:

```css
.toolbar-search {
  width: 280px;
}
```

- [ ] **Step 4: Verify idea tests and build**

Run:

```bash
node --test src/api/researchIdeas.test.js
npm run build
```

Expected: tests pass and build succeeds.

---

### Task 6: Final Verification and Progress Update

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes completed full-screen console redesign and verification outputs.
- Produces concise progress and verification entries.

- [ ] **Step 1: Run all frontend tests and build**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/papers.test.js
node --test src/api/rag.test.js
node --test src/api/researchIdeas.test.js
node --test src/api/chatHistory.test.js
npm run build
```

Expected: all tests pass and build succeeds. Existing Vite PURE/chunk warnings are acceptable.

- [ ] **Step 2: Update `docs/status/current.md`**

Add concise current status/progress entry:

```text
前端已完成全屏控制台式视觉修正：首页改为整屏概览面板；对话页改为两栏布局并用抽屉展示引用片段；文献和想法页改为扁平左侧菜单 + 主表格铺满；滚动条视觉隐藏，减少长短不一的卡片感。
```

Add verification entry:

```text
Claude 已完成全屏控制台式视觉修正验证：papers、rag、researchIdeas、chatHistory API helper 测试均通过；npm run build 通过，Vite 仅提示第三方 PURE 注释和 chunk 大小警告。
```

- [ ] **Step 3: Report final result**

Report changed files, verification command outcomes, and note that this is a visual correction responding to the user's feedback about uneven boxes and visible scrollbars.
