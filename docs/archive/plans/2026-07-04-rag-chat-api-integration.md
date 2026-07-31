# RAG Chat API Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the Vue RAG Chat page to the existing Spring Boot RAG and Research Idea APIs.

**Architecture:** Add focused frontend API helper modules for RAG chat and Research Idea session-save operations, then update `RagChatView.vue` to manage one active session, send questions, render answer/sources/model metadata, and save suggested ideas. Keep state local to the page because no other page consumes the active chat state yet.

**Tech Stack:** Vue 3, Vite 5, Axios, Element Plus, Vue Router 4, Node built-in test runner for lightweight API helper tests.

## Global Constraints

- Project path: `<project-root>/frontend/research-assistant-frontend`.
- Current Node.js is `v21.5.0`; keep Vite at 5.x and Vue Router at 4.x.
- Do not add unrelated dependencies.
- Match the current JavaScript Vue scaffold; do not convert to TypeScript.
- Keep page copy in Chinese for the current user workflow.
- Design direction: research workstation, calm document-oriented UI, not a generic marketing dashboard.
- Backend API returns unified `Result<T>` objects, and `src/api/client.js` unwraps HTTP responses to the backend result object.

---

### Task 1: Add RAG and Research Idea API Helpers

**Files:**
- Create: `frontend/research-assistant-frontend/src/api/rag.js`
- Create: `frontend/research-assistant-frontend/src/api/researchIdeas.js`
- Create: `frontend/research-assistant-frontend/src/api/rag.test.js`

**Interfaces:**
- Produces: `chatWithRag(payload, client = apiClient): Promise<Object>` where `payload` contains `question`, `topK`, optional `sessionId`, and optional `paperId`.
- Produces: `saveDraftFromSession(sessionId, client = apiClient): Promise<Object>`.

- [ ] Write a failing Node test that imports `chatWithRag` and `saveDraftFromSession`, then verifies their endpoint, method, and request body usage with a fake client.
- [ ] Run `node --test src/api/rag.test.js` and confirm it fails because helper modules do not exist.
- [ ] Implement `src/api/rag.js` and `src/api/researchIdeas.js` with the two helper functions.
- [ ] Re-run `node --test src/api/rag.test.js` and confirm it passes.

---

### Task 2: Connect RAG Chat Page

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`
- Modify: `frontend/research-assistant-frontend/src/style.css`

**Interfaces:**
- Consumes: `chatWithRag` from `src/api/rag.js`.
- Consumes: `saveDraftFromSession` from `src/api/researchIdeas.js`.
- Produces: A page that sends real RAG questions, displays answer and sources, keeps returned `sessionId`, and saves suggested Research Ideas.

- [ ] Replace static answer/sources placeholders with reactive state from `POST /api/rag/chat`.
- [ ] Add `topK` control, send loading state, empty-state copy, and error messages.
- [ ] Display `answer`, `modelProvider`, `modelName`, `sessionId`, `retrievalQuestion`, `suggestSaveAsIdea`, and `ideaSuggestionReason`.
- [ ] Render source cards with `paperTitle`, `chunkIndex`, `score`, `retrievalRoute`, and content preview.
- [ ] Enable “保存为 Research Idea” only when a `sessionId` exists, and call `POST /api/research-ideas/save-draft-from-session/{sessionId}`.
- [ ] Run `npm run build` and confirm Vite build succeeds.

---

### Task 3: Update Project Progress

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: final verified result from Tasks 1 and 2.
- Produces: Updated progress record only after tests/build pass.

- [ ] If tests/build pass, record that the frontend RAG Chat page now connects to real RAG and Research Idea save APIs.
- [ ] Record verification commands and results concisely.
- [ ] Update the next task to browser manual verification, then Research Idea list page integration.

---

## Self-Review

- Spec coverage: covers sending questions, showing answer/sources/model/session metadata, showing idea suggestion, and saving a draft from the returned session.
- Placeholder scan: no missing implementation placeholders; chat history listing remains intentionally deferred.
- Type consistency: helper function names match planned page imports.
