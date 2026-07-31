# 文献画像异步生成 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将文献画像生成从同步长请求改为异步任务，前端点击后立即显示处理中，完成后自动刷新画像，失败时展示错误。

**Architecture:** 后端新增 `paper_profile_job` 状态表和异步任务服务，启动接口立即返回任务状态，后台复用现有 `PaperProfileService.generateProfile(id)` 生成画像并更新状态。前端文献页调用异步启动接口，然后轮询任务状态，完成后调用现有画像查询接口刷新内容。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、JUnit 5、Mockito、Vue 3、Axios、Element Plus、Node test。

## Global Constraints

- 不再让前端长时间等待同步画像生成请求。
- 同一篇论文正在生成时，不重复启动新任务，直接返回当前 PROCESSING 状态。
- 已有画像允许用户重新生成；重新生成会创建/复用最新任务并最终 upsert `paper-profile-v1` 与 `section-summary-v1`。
- 后端失败必须保存 `errorMessage`，前端必须展示可读失败原因。
- 完成实质阶段后更新 `docs/status/current.md`。

---

## File Structure

- Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperProfileJob.java`：画像任务实体。
- Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/mapper/PaperProfileJobMapper.java`：任务 Mapper。
- Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperProfileJobResponse.java`：任务状态响应 DTO。
- Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperProfileJobService.java`：任务服务接口。
- Create `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/impl/PaperProfileJobServiceImpl.java`：启动任务、查询状态、后台执行。
- Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperController.java`：新增启动和查询任务接口。
- Modify database scripts under `docs/` and backend resources to add `paper_profile_job`.
- Modify `frontend/research-assistant-frontend/src/api/papers.js` and tests：新增 async job helper。
- Modify `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`：生成按钮改为异步启动和轮询。
- Modify `docs/status/current.md`：记录进度和验证。

---

### Task 1: 后端异步画像任务基础

**Files:**
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperProfileJob.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/mapper/PaperProfileJobMapper.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperProfileJobResponse.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperProfileJobService.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/impl/PaperProfileJobServiceImpl.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileJobServiceImplTest.java`

**Interfaces:**
- Produces: `PaperProfileJobResponse startProfileJob(Long paperId)`
- Produces: `PaperProfileJobResponse getLatestJob(Long paperId)`

- [ ] Write failing service tests for PROCESSING reuse and completed update.
- [ ] Implement entity, mapper, DTO, service.
- [ ] Run targeted tests.

---

### Task 2: 后端接口与数据库脚本

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperController.java`
- Modify: database init/migration scripts under `docs/` and resources.

**Interfaces:**
- Produces: `POST /api/papers/{id}/profile/async`
- Produces: `GET /api/papers/{id}/profile/job`

- [ ] Add controller endpoints.
- [ ] Add SQL table definition and migration.
- [ ] Run backend compile/tests.

---

### Task 3: 前端异步入口和轮询

**Files:**
- Modify: `frontend/research-assistant-frontend/src/api/papers.js`
- Modify: `frontend/research-assistant-frontend/src/api/papers.test.js`
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`

**Interfaces:**
- Consumes: `startPaperProfileJob(id)` -> `POST /api/papers/{id}/profile/async`
- Consumes: `getPaperProfileJob(id)` -> `GET /api/papers/{id}/profile/job`

- [ ] Add failing helper tests.
- [ ] Add API helpers.
- [ ] Change UI to start async job and poll every 3 seconds.
- [ ] Stop polling on completed/failed/dialog close.
- [ ] Run frontend tests/build.

---

### Task 4: 验证和进度记录

**Files:**
- Modify: `docs/status/current.md`

- [ ] Run backend targeted tests and compile.
- [ ] Run frontend API tests and build.
- [ ] Update progress docs with behavior and manual verification steps.
