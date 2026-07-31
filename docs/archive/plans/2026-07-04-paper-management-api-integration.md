# Paper Management API Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the Vue Paper Management page to the existing Spring Boot paper APIs.

**Architecture:** Add a focused paper API helper module that wraps the existing Axios client, then update `PaperManagementView.vue` to load papers, upload PDFs, parse papers, and vectorize papers. Keep this stage page-local and avoid global state until more pages need shared data.

**Tech Stack:** Vue 3, Vite 5, Axios, Element Plus, Vue Router 4, Node built-in test runner for lightweight API helper tests.

## Global Constraints

- Project path: `<project-root>/frontend/research-assistant-frontend`.
- Current Node.js is `v21.5.0`; keep Vite at 5.x and Vue Router at 4.x.
- Do not add unrelated dependencies.
- Match the current JavaScript Vue scaffold; do not convert to TypeScript.
- Keep page copy in Chinese for the current user workflow.
- Design direction: research workstation, calm document-oriented UI, not a generic marketing dashboard.
- Backend API returns unified `Result<T>` objects, and `src/api/client.js` currently unwraps `response.data`.

---

### Task 1: Add Paper API Helper

**Files:**
- Create: `frontend/research-assistant-frontend/src/api/papers.js`
- Create: `frontend/research-assistant-frontend/src/api/papers.test.js`

**Interfaces:**
- Produces: `listPapers(client = apiClient): Promise<Array>`
- Produces: `uploadPaper(formData, client = apiClient): Promise<Object>`
- Produces: `parsePaper(id, client = apiClient): Promise<number>`
- Produces: `vectorizePaper(id, client = apiClient): Promise<Object>`

- [ ] Write a failing Node test that imports `src/api/papers.js` and verifies endpoint/method usage with a fake client.
- [ ] Run `node --test src/api/papers.test.js` and confirm it fails because the helper does not exist.
- [ ] Implement `src/api/papers.js` with the four helper functions.
- [ ] Re-run `node --test src/api/papers.test.js` and confirm it passes.

---

### Task 2: Connect Paper Management Page

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`

**Interfaces:**
- Consumes: `listPapers`, `uploadPaper`, `parsePaper`, and `vectorizePaper` from `src/api/papers.js`.
- Produces: A page that loads real papers, uploads a selected PDF, parses a selected paper, and vectorizes a selected paper.

- [ ] Replace static paper rows with reactive rows loaded on mount.
- [ ] Add upload form fields for title, authors, publishYear, journal, keywords, and remark.
- [ ] Use Element Plus messages for success and failure feedback.
- [ ] Add loading states to list, upload, parse, and vectorize actions.
- [ ] Keep the “问答” button as navigation to `/chat` for the next stage.
- [ ] Run `npm run build` and confirm Vite build succeeds.

---

### Task 3: Update Project Progress

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: final verified result from Task 2.
- Produces: Updated progress record only if this stage is verified.

- [ ] If tests/build pass, record that the frontend Paper Management page now connects to backend paper APIs.
- [ ] Record verification commands and results concisely.

---

## Self-Review

- Spec coverage: covers list, upload, parse, vectorize, refresh, and user-facing messages for the Paper Management page.
- Placeholder scan: no missing implementation placeholders; RAG page navigation remains intentionally deferred to the next stage.
- Type consistency: helper function names match the imports planned for `PaperManagementView.vue`.
