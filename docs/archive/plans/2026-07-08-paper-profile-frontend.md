# 文献画像前端入口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐前端文献画像生成/查看入口，让多篇 HYBRID_RAG 依赖的 paper-profile-v1 能由用户显式生成并实际启用。

**Architecture:** 后端已有 `GET /api/papers/{id}/profile` 和 `POST /api/papers/{id}/profile`，本次不改变后端策略。前端在文献页增加画像查看/生成弹窗，在问答页增加多篇比较画像依赖提示，API helper 与测试覆盖新增接口。

**Tech Stack:** Vue 3、Vite 5、Element Plus、Axios、Spring Boot 现有 REST API。

## Global Constraints

- 遵守 `docs/status/current.md`：完成实质阶段后更新项目进度。
- 不在 `/api/rag/chat` 中自动生成画像，避免聊天请求变慢和失败点增多。
- 复用现有 `/api/papers/{id}/profile` 后端接口，保持 HYBRID_RAG“画像齐全才启用，缺失则回退 VECTOR_RAG”的策略。
- 前端错误信息直接展示后端业务错误，例如“请先解析 PDF 后再生成文献画像”。

---

## File Structure

- Modify `frontend/research-assistant-frontend/src/api/papers.js`：新增 `getPaperProfile(id, client)` 和 `generatePaperProfile(id, client)`。
- Modify `frontend/research-assistant-frontend/src/api/papers.test.js`：覆盖两个画像 API helper 的请求路径和方法。
- Modify `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`：文献列表增加“画像”按钮、画像弹窗、生成/刷新逻辑和展示字段。
- Modify `frontend/research-assistant-frontend/src/views/RagChatView.vue`：多选参考论文时提示多篇 HYBRID_RAG 依赖文献画像，并引导回文献页生成。
- Modify `docs/status/current.md`：记录根因、修复、验证结果和下一步状态。

---

### Task 1: API helper 与测试

**Files:**
- Modify: `frontend/research-assistant-frontend/src/api/papers.js`
- Modify: `frontend/research-assistant-frontend/src/api/papers.test.js`

**Interfaces:**
- Produces: `getPaperProfile(id, client = apiClient): Promise<PaperProfileResult>`；调用 `GET /api/papers/{id}/profile`。
- Produces: `generatePaperProfile(id, client = apiClient): Promise<PaperProfileResult>`；调用 `POST /api/papers/{id}/profile`。

- [ ] **Step 1: Write failing tests**
  - 在 `papers.test.js` import 列表加入 `getPaperProfile`、`generatePaperProfile`。
  - 新增两个测试：确认 GET/POST 路径分别是 `/api/papers/12/profile`。

- [ ] **Step 2: Run tests and confirm failure**
  - Run: `cd frontend/research-assistant-frontend && node --test src/api/papers.test.js`
  - Expected: 因 helper 未导出而失败。

- [ ] **Step 3: Implement helpers**
  - 在 `papers.js` 增加两个函数，沿用现有 `.then((result) => result.data ?? result)` 解析模式。

- [ ] **Step 4: Run tests and confirm pass**
  - Run: `cd frontend/research-assistant-frontend && node --test src/api/papers.test.js`
  - Expected: 全部通过。

---

### Task 2: 文献页画像弹窗与生成入口

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`

**Interfaces:**
- Consumes: `getPaperProfile(id)`、`generatePaperProfile(id)` from `../api/papers.js`。
- Produces UI: 操作列“画像”按钮；`profileDialogVisible` 控制弹窗；`profileResult.profile` 展示整篇画像；`profileResult.sectionSummaries` 展示章节摘要数量。

- [ ] **Step 1: Add imports and state**
  - 从 papers API 导入 `getPaperProfile`、`generatePaperProfile`。
  - 新增 `profileDialogVisible`、`profilePaper`、`profileResult`、`profileLoading`、`profileGenerating`。

- [ ] **Step 2: Add table action**
  - 操作列宽度从 `340` 调整为足够容纳新增按钮。
  - 在“向量化”和“改分类”之间增加“画像”按钮，点击 `openProfileDialog(row)`。

- [ ] **Step 3: Add profile dialog**
  - 弹窗标题显示当前论文名。
  - 加载中显示 Element Plus loading。
  - 未生成画像时显示提示和“生成画像”按钮。
  - 已生成画像时展示研究问题、方法概述、实验与评估、主要贡献、局限性、关键词、完整画像和章节摘要数量。

- [ ] **Step 4: Implement methods**
  - `openProfileDialog(row)`：打开弹窗并调用 `loadPaperProfile(row)`。
  - `loadPaperProfile(row)`：调用 GET，写入 `profileResult`。
  - `handleGenerateProfile()`：调用 POST，成功后写入 `profileResult` 并提示“文献画像已生成”。
  - `hasProfile()`：判断 `profileResult?.profile` 是否存在。

---

### Task 3: 问答页多篇画像依赖提示

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`

**Interfaces:**
- Consumes: `selectedPaperIds`。
- Produces UI: 当 `selectedPaperIds.length >= 2` 时，在参考论文上下文面板顶部显示提示：多篇比较会在画像齐全时启用 HYBRID_RAG，若回答片段不足，请先到文献页生成画像。

- [ ] **Step 1: Add computed**
  - 新增 `shouldShowHybridProfileHint = computed(() => selectedPaperIds.value.length >= 2)`。

- [ ] **Step 2: Add alert**
  - 在参考论文面板 `selected-paper-summary` 下方增加 `el-alert`。
  - Alert 文案说明“多篇比较依赖文献画像”。
  - 提供按钮跳转 `/papers`。

---

### Task 4: Verification and progress docs

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Produces: 项目进度记录包含根因、修复内容、验证命令和当前状态。

- [ ] **Step 1: Run frontend helper tests**
  - Run: `cd frontend/research-assistant-frontend && node --test src/api/papers.test.js src/api/rag.test.js src/views/ragChatState.test.js`
  - Expected: PASS。

- [ ] **Step 2: Run frontend build**
  - Run: `cd frontend/research-assistant-frontend && npm run build`
  - Expected: build success；允许 Vite 既有第三方 PURE 注释或 chunk 大小警告。

- [ ] **Step 3: Run backend compile or targeted tests if needed**
  - Run: `cd backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile`
  - Expected: BUILD SUCCESS。

- [ ] **Step 4: Update progress**
  - 在 `docs/status/current.md` 当前状态与验证处追加本次前端画像入口、HYBRID_RAG 实际启用前提提示、验证结果。

---

## Self-Review

- Spec coverage: 覆盖文献页画像生成/查看、问答页提示、API helper 测试、验证和进度更新。
- Placeholder scan: 无 TBD/TODO/“以后实现”等占位。
- Type consistency: helper 名称在计划中统一为 `getPaperProfile` 和 `generatePaperProfile`。
