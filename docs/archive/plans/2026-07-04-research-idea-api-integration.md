# Research Idea API Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the Vue Research Idea page to the existing Spring Boot Research Idea APIs.

**Architecture:** Extend the existing `researchIdeas.js` API helper with list, stats, saveType update, and delete functions, then update `ResearchIdeasView.vue` to load real data, filter it, show status stats, update saveType, and delete records. Keep create/edit dialogs out of scope to finish the current front-back integration loop first.

**Tech Stack:** Vue 3, Vite 5, Axios, Element Plus, Vue Router 4, Node built-in test runner.

## Global Constraints

- Project path: `<project-root>/frontend/research-assistant-frontend`.
- Current Node.js is `v21.5.0`; keep Vite at 5.x and Vue Router at 4.x.
- Do not add unrelated dependencies.
- Match the current JavaScript Vue scaffold; do not convert to TypeScript.
- Keep page copy in Chinese for the current user workflow.
- Do not change backend APIs.
- Backend API returns unified `Result<T>` objects, and `src/api/client.js` unwraps HTTP responses to the backend result object.

---

### Task 1: Extend Research Idea API Helper

**Files:**
- Modify: `frontend/research-assistant-frontend/src/api/researchIdeas.js`
- Create: `frontend/research-assistant-frontend/src/api/researchIdeas.test.js`

**Interfaces:**
- Produces: `listResearchIdeas(filters, client = apiClient): Promise<Array>`.
- Produces: `countResearchIdeaSaveTypes(client = apiClient): Promise<Object>`.
- Produces: `updateResearchIdeaSaveType(id, saveType, client = apiClient): Promise<Object>`.
- Produces: `deleteResearchIdea(id, client = apiClient): Promise<null>`.
- Preserves: `saveDraftFromSession(sessionId, client = apiClient): Promise<Object>`.

- [ ] Write a failing Node test that verifies endpoint, query params, method, and request body usage with a fake client.
- [ ] Run `node --test src/api/researchIdeas.test.js` and confirm it fails because the new helper functions do not exist.
- [ ] Implement the helper functions in `src/api/researchIdeas.js`.
- [ ] Re-run `node --test src/api/researchIdeas.test.js` and confirm it passes.

---

### Task 2: Connect Research Ideas Page

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`

**Interfaces:**
- Consumes: `listResearchIdeas`, `countResearchIdeaSaveTypes`, `updateResearchIdeaSaveType`, `deleteResearchIdea` from `src/api/researchIdeas.js`.
- Produces: A page that loads real Idea rows and stats, filters by keyword/saveType, updates saveType, and deletes rows.

- [ ] Replace static stats and rows with reactive API-loaded state.
- [ ] Load stats and rows on mount.
- [ ] Add search and reset actions for keyword/saveType filters.
- [ ] Add per-row saveType select that calls `PATCH /api/research-ideas/{id}/save-type`.
- [ ] Add per-row delete action with Element Plus confirmation.
- [ ] Keep the page visually consistent with the current Research Desk style.

---

### Task 3: Verify and Update Progress

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: final verified result from Tasks 1 and 2.
- Produces: updated progress record only after tests/build pass.

- [ ] Run `node --test src/api/researchIdeas.test.js`.
- [ ] Run `npm run build`.
- [ ] If both pass, record the Research Idea frontend integration and verification results in `docs/status/current.md`.

---

## Self-Review

- Spec coverage: covers list, filters, stats, saveType flow, delete, tests, build, and progress update.
- Placeholder scan: no placeholders; create/edit dialogs are explicitly out of scope.
- Type consistency: helper function names match planned page imports.
