# Literature Category Folder Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add user-defined literature category folders so each paper belongs to one category, can be filtered by category, and can move to “未分类” when a category is deleted.

**Architecture:** Add `paper_category` as a first-class paper-domain entity and connect `paper_reference.category_id` to it. Backend exposes category CRUD plus paper category assignment; frontend consumes those APIs to render folder-style category management in `/papers`, category overview in Dashboard, and category labels in `/chat` reference paper selection.

**Tech Stack:** Spring Boot, MyBatis-Plus, MySQL, Vue 3, Vite 5, Axios, Element Plus, node:test, JUnit 5, Mockito, AssertJ.

## Global Constraints

- User-defined categories are folder-like, not tags.
- One paper belongs to exactly one category.
- System category `未分类` must exist and cannot be deleted.
- Upload without category uses `未分类`.
- Deleting a normal category moves papers to `未分类` before deleting the category.
- Uploaded papers can be moved to another category later.
- Before each substantive phase, update `docs/status/current.md` with the phase plan.
- After each substantive phase, update `docs/status/current.md` with completed work, validation result, and next step.
- Backend test commands must use JDK 21 if current shell points to JDK 8.
- Final feature version must be committed after implementation and verification.

---

## File Structure

### Backend files to create

- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperCategory.java` — MyBatis entity for `paper_category`.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperCategoryCreateRequest.java` — create category request body.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperCategoryUpdateRequest.java` — update category request body.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperCategoryResponse.java` — category response with `paperCount`.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperCategoryAssignRequest.java` — paper category assignment request body.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/mapper/PaperCategoryMapper.java` — MyBatis mapper.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperCategoryService.java` — category service interface.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/impl/PaperCategoryServiceImpl.java` — category service implementation.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperCategoryController.java` — REST controller.
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperCategoryServiceImplTest.java` — category service unit tests.
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImplTest.java` — paper category integration behavior unit tests.

### Backend files to modify

- `docs/database/schema.sql` — add `paper_category`, `paper_reference.category_id`, default `未分类` seed, and index.
- `docs/status/current.md` — update before and after every phase.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperReference.java` — add `categoryId` and non-table `categoryName`.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperReferenceService.java` — support category filter, upload category, update paper category.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImpl.java` — implement category-aware list/upload/update.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperController.java` — wire query/form/body category parameters.

### Frontend files to create

- `frontend/research-assistant-frontend/src/api/paperCategories.js` — category API helpers.
- `frontend/research-assistant-frontend/src/api/paperCategories.test.js` — category API helper tests.

### Frontend files to modify

- `frontend/research-assistant-frontend/src/api/papers.js` — `listPapers({ categoryId })`, `updatePaperCategory`.
- `frontend/research-assistant-frontend/src/api/papers.test.js` — cover category query and patch helper.
- `frontend/research-assistant-frontend/src/views/PaperManagementView.vue` — category folder UI, dialogs, upload category, change category.
- `frontend/research-assistant-frontend/src/views/DashboardView.vue` — category overview.
- `frontend/research-assistant-frontend/src/views/RagChatView.vue` — reference paper category label.

---

### Task 1: Record Phase 1 and Add Backend Category Model

**Files:**
- Modify: `docs/status/current.md`
- Modify: `docs/database/schema.sql`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperCategory.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/mapper/PaperCategoryMapper.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperCategoryCreateRequest.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperCategoryUpdateRequest.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperCategoryResponse.java`

**Interfaces:**
- Produces: `PaperCategory`, `PaperCategoryMapper`, create/update/response DTO classes used by Tasks 2-3.

- [ ] **Step 1: Update `docs/status/current.md` before coding**

Add a current-state line under “当前任务状态”:

```text
文献分类功能进入阶段 1：新增 paper_category 数据表、paper_reference.category_id 字段、分类实体/Mapper/DTO，为分类增删改查打基础。
```

- [ ] **Step 2: Update database design**

Add `paper_category` before `paper_reference`, add `category_id` and index to `paper_reference`, and seed `未分类`.

- [ ] **Step 3: Create backend entity and mapper**

`PaperCategory` fields: `id`, `name`, `description`, `sortOrder`, `systemFlag`, `createTime`, `updateTime`.

- [ ] **Step 4: Create DTOs**

`PaperCategoryCreateRequest` and `PaperCategoryUpdateRequest`: `name`, `description`.

`PaperCategoryResponse`: entity fields plus `paperCount`.

- [ ] **Step 5: Run compile/test smoke**

Run from `backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile
```

Expected: build success.

---

### Task 2: Implement Category Service and Controller

**Files:**
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperCategoryService.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/impl/PaperCategoryServiceImpl.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperCategoryController.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperCategoryServiceImplTest.java`
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: `PaperCategoryMapper`, `PaperReferenceMapper`, category DTOs.
- Produces: `listCategories()`, `createCategory(request)`, `updateCategory(id, request)`, `deleteCategory(id)`, `getUncategorizedCategoryId()`, `ensureCategoryExists(categoryId)`.

- [ ] **Step 1: Write tests first**

Test cases:

```java
@Test
void createCategoryRejectsBlankName()

@Test
void createCategoryRejectsDuplicateName()

@Test
void deleteCategoryMovesPapersToUncategorizedBeforeDelete()

@Test
void deleteCategoryRejectsSystemCategory()
```

- [ ] **Step 2: Implement service**

Rules:

- `未分类` lookup by `system_flag = 1` and `name = 未分类`.
- Create validates blank and duplicate names.
- Update rejects missing category, system category rename, blank name, duplicate name.
- Delete rejects missing/system category, updates papers from deleted category to uncategorized category, then deletes category.

- [ ] **Step 3: Implement controller**

Routes:

```http
GET /api/paper-categories
POST /api/paper-categories
PUT /api/paper-categories/{id}
DELETE /api/paper-categories/{id}
```

- [ ] **Step 4: Run focused test**

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperCategoryServiceImplTest test
```

Expected: tests pass.

- [ ] **Step 5: Update `docs/status/current.md` after phase 1**

Record category table/model/service/controller completion and test result.

---

### Task 3: Add Category-Aware Paper Backend Behavior

**Files:**
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperCategoryAssignRequest.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperReference.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperReferenceService.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperController.java`
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImplTest.java`
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: `PaperCategoryService.getUncategorizedCategoryId()`, `PaperCategoryService.ensureCategoryExists(Long)`.
- Produces: `listPapers(Long categoryId)`, `uploadPaper(..., Long categoryId)`, `updatePaperCategory(Long paperId, Long categoryId)`.

- [ ] **Step 1: Update `docs/status/current.md` before coding**

Add:

```text
文献分类功能进入阶段 2：增强文献上传、文献列表和文献分类修改接口，使文献可归入用户自定义分类。
```

- [ ] **Step 2: Write tests first**

Test cases:

```java
@Test
void uploadPaperUsesUncategorizedWhenCategoryIdMissing()

@Test
void uploadPaperValidatesProvidedCategory()

@Test
void updatePaperCategoryRejectsMissingPaper()

@Test
void updatePaperCategoryUpdatesCategoryOnly()
```

- [ ] **Step 3: Modify entity/service/controller**

- `PaperReference` adds `categoryId` and `@TableField(exist = false) categoryName`.
- `GET /api/papers` accepts optional `categoryId`.
- Upload accepts optional form field `categoryId`.
- Add `PATCH /api/papers/{id}/category` with `{ "categoryId": 2 }`.

- [ ] **Step 4: Enrich paper category names**

After selecting papers, load relevant categories and set `paper.categoryName` for frontend display.

- [ ] **Step 5: Run focused tests**

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperReferenceServiceImplTest test
```

Expected: tests pass.

- [ ] **Step 6: Update `docs/status/current.md` after phase 2**

Record paper upload/list/update category completion and test result.

---

### Task 4: Add Frontend Category API Helpers

**Files:**
- Create: `frontend/research-assistant-frontend/src/api/paperCategories.js`
- Create: `frontend/research-assistant-frontend/src/api/paperCategories.test.js`
- Modify: `frontend/research-assistant-frontend/src/api/papers.js`
- Modify: `frontend/research-assistant-frontend/src/api/papers.test.js`
- Modify: `docs/status/current.md`

**Interfaces:**
- Produces: `listPaperCategories`, `createPaperCategory`, `updatePaperCategory`, `deletePaperCategory`, `updatePaperCategoryOfPaper`.

- [ ] **Step 1: Update `docs/status/current.md` before coding**

Add:

```text
文献分类功能进入阶段 3：前端接入分类 API helper，为文献页分类文件夹管理做准备。
```

- [ ] **Step 2: Write frontend helper tests first**

Cover:

- `GET /api/paper-categories`
- `POST /api/paper-categories`
- `PUT /api/paper-categories/{id}`
- `DELETE /api/paper-categories/{id}`
- `GET /api/papers?categoryId=2`
- `PATCH /api/papers/{id}/category`

- [ ] **Step 3: Implement API helpers**

Use existing `apiClient` pattern and unwrap `result.data ?? result`.

- [ ] **Step 4: Run tests**

```bash
cd frontend/research-assistant-frontend
node --test src/api/papers.test.js src/api/paperCategories.test.js
```

Expected: all tests pass.

---

### Task 5: Implement `/papers` Category Folder UI

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: category API helpers and paper category update helper from Task 4.

- [ ] **Step 1: Extend state**

Add state for `categories`, `categoryLoading`, `selectedCategoryId`, `categoryDialogVisible`, `editingCategory`, `categoryForm`, `paperCategoryDialogVisible`, `movingPaper`, `selectedMoveCategoryId`.

- [ ] **Step 2: Load categories and papers together**

On mount and after mutations, call `loadCategories()` and `loadPapers()`.

- [ ] **Step 3: Add sidebar category folder UI**

Show “全部文献”, then each category with paper count. Normal categories show edit/delete controls. System category does not show delete.

- [ ] **Step 4: Add create/edit/delete dialogs**

Use Element Plus dialog/form/buttons and confirmation message for delete.

- [ ] **Step 5: Add upload category select**

Upload form defaults to `未分类` category ID and appends `categoryId`.

- [ ] **Step 6: Add category labels and move action**

Show category tag under paper title. Add “改分类” button and move dialog.

- [ ] **Step 7: Run frontend tests/build**

```bash
cd frontend/research-assistant-frontend
node --test src/api/papers.test.js src/api/paperCategories.test.js
npm run build
```

Expected: tests pass, build succeeds. Vite chunk warning is acceptable.

- [ ] **Step 8: Update `docs/status/current.md` after phase 3**

Record front-end paper category management completion and validation result.

---

### Task 6: Add Dashboard and Chat Category Display

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/DashboardView.vue`
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: `listPaperCategories()` and paper `categoryName` returned by `listPapers()`.

- [ ] **Step 1: Update `docs/status/current.md` before coding**

Add:

```text
文献分类功能进入阶段 4：增强 Dashboard 和论文问答页的分类展示，使文献分类在主要使用场景中可见。
```

- [ ] **Step 2: Add Dashboard category overview**

Load categories with other Dashboard data and render a small “文献分类” panel with counts.

- [ ] **Step 3: Add chat reference paper category label**

In `/chat` reference paper list, render `paper.categoryName || '未分类'` before author/year.

- [ ] **Step 4: Run frontend tests/build**

```bash
cd frontend/research-assistant-frontend
node --test src/api/rag.test.js src/views/ragChatState.test.js src/api/paperCategories.test.js
npm run build
```

Expected: tests pass, build succeeds.

- [ ] **Step 5: Update `docs/status/current.md` after phase 4**

Record Dashboard/chat display completion and validation result.

---

### Task 7: Final Verification, Progress Update, and Commit

**Files:**
- Modify: `docs/status/current.md`
- Commit all changed feature files.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: final validated feature commit.

- [ ] **Step 1: Update `docs/status/current.md` before verification**

Add:

```text
文献分类功能进入阶段 5：进行后端、前端和人工流程的整体回归验证，准备提交功能版本。
```

- [ ] **Step 2: Run backend verification**

```bash
cd backend/research-assistant-backend
JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

Expected: all backend tests pass.

- [ ] **Step 3: Run frontend verification**

```bash
cd frontend/research-assistant-frontend
node --test src/api/papers.test.js src/api/paperCategories.test.js src/api/rag.test.js src/views/ragChatState.test.js
npm run build
```

Expected: all selected frontend tests pass and build succeeds.

- [ ] **Step 4: Update `docs/status/current.md` final state**

Record:

```text
文献分类文件夹功能已完成：支持用户自定义分类、上传选择分类、文献列表分类筛选、上传后修改分类、删除分类转入未分类，并已贯通文献页、Dashboard 和论文问答参考论文展示。
```

Also record exact verification commands and outcomes.

- [ ] **Step 5: Commit feature version**

```bash
git add docs backend frontend
git commit -m "Add literature category folder management

Co-Authored-By: Claude <noreply@anthropic.com>"
```

Expected: commit succeeds on current branch.

---

## Self-Review

- Spec coverage: all confirmed requirements map to Tasks 1-7.
- Placeholder scan: no `TBD`, `TODO`, or vague implementation-only placeholders remain.
- Type consistency: backend uses `categoryId`/`categoryName`; frontend uses `categoryId`/`categoryName`; category API path is consistently `/api/paper-categories`.
