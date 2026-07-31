# Paper Delete Core Assets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement safe literature deletion that removes the local PDF, MySQL paper core assets, and Qdrant vector points for the deleted paper while preserving chat history and Research Idea records.

**Architecture:** Keep the existing `DELETE /api/papers/{id}` API and extend the service layer. `PaperReferenceServiceImpl.deletePaper` will validate the paper, delete Qdrant points by `paperId` payload filter before removing local/MySQL data, then rely on existing MySQL `ON DELETE CASCADE` constraints for sections, chunks, profiles, summaries, and profile jobs. The frontend paper management table will add a destructive delete action with confirmation.

**Tech Stack:** Spring Boot, MyBatis-Plus, Qdrant REST API, Vue 3, Element Plus, Node test runner, JUnit 5, Mockito.

## Global Constraints

- Preserve chat history and Research Idea records; do not delete or mutate `chat_session`, `chat_message`, or `research_idea`.
- Do not add new database tables for this feature.
- Do not commit changes unless the user explicitly asks.
- Update `docs/status/current.md` with substantive stage progress and verification results.
- Follow existing API response pattern: controller returns `Result.success()` for successful deletion.

---

### Task 1: Backend Qdrant Paper-Scoped Vector Deletion

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/qdrant/service/QdrantService.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/qdrant/service/QdrantServiceTest.java`

**Interfaces:**
- Consumes: Qdrant payload field `paperId` written by `QdrantService.buildPayload(Long paperId, PaperChunk chunk)`.
- Produces: `public Map<String, Object> deletePaperPoints(Long paperId)` returning `success`, `collection`, `paperId`, and either `response` or `error`.

- [ ] Add a unit test that verifies paper-scoped delete request shape can be built without embedding.
- [ ] Add `deletePaperPoints(Long paperId)` using Qdrant endpoint `POST /collections/paper_chunks/points/delete` with body:

```json
{
  "filter": {
    "must": [
      {
        "key": "paperId",
        "match": { "value": 12 }
      }
    ]
  }
}
```

- [ ] Return `success=false` when `paperId` is null or not positive.
- [ ] Run `JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=QdrantServiceTest test` from `backend/research-assistant-backend`.

### Task 2: Backend Delete Flow Integration

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperReferenceServiceImpl.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImplTest.java`

**Interfaces:**
- Consumes: `QdrantService.deletePaperPoints(Long paperId)` from Task 1.
- Produces: Existing `PaperReferenceService.deletePaper(Long id)` now deletes Qdrant vectors before local file and DB record.

- [ ] Add test: deleting an existing paper calls `qdrantService.deletePaperPoints(id)`, deletes the local file, and deletes `paper_reference`.
- [ ] Add test: if Qdrant deletion returns `success=false`, deletion aborts before deleting local file or DB record.
- [ ] Implement minimal changes in `deletePaper`: call `qdrantService.deletePaperPoints(id)` after paper lookup and before file deletion; throw `RuntimeException("删除文献向量数据失败：" + error)` when result is not successful.
- [ ] Run `JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperReferenceServiceImplTest,QdrantServiceTest test`.

### Task 3: Frontend Delete API and Paper Table Action

**Files:**
- Modify: `frontend/research-assistant-frontend/src/api/papers.js`
- Modify: `frontend/research-assistant-frontend/src/api/papers.test.js`
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`

**Interfaces:**
- Consumes: Existing backend `DELETE /api/papers/{id}`.
- Produces: `deletePaper(id, client = apiClient)` helper and table delete button.

- [ ] Add fake client `delete(path)` support in `papers.test.js`.
- [ ] Add test asserting `deletePaper(12, client)` calls `/api/papers/12` with method `delete`.
- [ ] Add `export function deletePaper(id, client = apiClient)` to `papers.js`.
- [ ] In `PaperManagementView.vue`, import `deletePaper`, add a danger plain button labelled `删除`, and implement `handleDeletePaper(row)` using `ElMessageBox.confirm`.
- [ ] Confirmation text must explain: deleting the paper removes the PDF, parsed chunks, vector data, profile data, and profile jobs, but does not delete chat history or Research Idea records.
- [ ] After success, stop profile polling if the deleted paper is open in the profile dialog, close that dialog, and call `reloadLibrary()`.
- [ ] Run `node --test src/api/papers.test.js` and `npm run build` from `frontend/research-assistant-frontend`.

### Task 4: Progress Documentation and Verification

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: actual test/build outputs from Tasks 1-3.
- Produces: A concise progress entry stating what deletion now removes, what it intentionally preserves, and verification commands/results.

- [ ] Add an entry before implementation starts to record entering the paper deletion stage.
- [ ] After verification, update the same progress section with completed behavior and test results.
- [ ] Run final targeted backend/frontend checks used above.
