# Research Desk Visual Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve the Vue frontend from a default Element Plus admin look into a calm paper-research workbench.

**Architecture:** Keep the existing Vue components and API behavior, but replace the visual system with a Research Desk direction: paper-like surfaces, deep ink navigation, evidence cards, and clearer research workflow hierarchy. Use global CSS tokens and targeted class additions instead of adding dependencies or rewriting state logic.

**Tech Stack:** Vue 3, Vite 5, Axios, Element Plus, Vue Router 4.

## Global Constraints

- Project path: `<project-root>/frontend/research-assistant-frontend`.
- Current Node.js is `v21.5.0`; keep Vite at 5.x and Vue Router at 4.x.
- Do not add unrelated dependencies.
- Match the current JavaScript Vue scaffold; do not convert to TypeScript.
- Keep page copy in Chinese for the current user workflow.
- Do not break existing Paper Management and RAG Chat API behavior.
- Visual direction: Research Desk deep-blue paper style, not generic Element Plus admin dashboard.

---

### Task 1: Redesign App Shell and Shared Visual Tokens

**Files:**
- Modify: `frontend/research-assistant-frontend/src/App.vue`
- Modify: `frontend/research-assistant-frontend/src/style.css`

**Interfaces:**
- Consumes: existing router links and `<RouterView />`.
- Produces: shared visual tokens, app shell, card, table, button, tag, and responsive styles used by all pages.

- [ ] Update `App.vue` copy and shell markup to present the app as a Research Desk workspace.
- [ ] Replace global CSS colors, spacing, navigation, cards, table, and Element Plus overrides with the Research Desk visual system.
- [ ] Keep existing route paths unchanged: `/papers`, `/chat`, `/ideas`.

---

### Task 2: Refine Page Markup for Research Workflows

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/RagChatView.vue`
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`
- Modify: `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`

**Interfaces:**
- Consumes: existing page API methods and reactive state.
- Produces: clearer page hierarchy through visual-only class names and copy refinements.

- [ ] Update page headings and card labels to read like a research workflow.
- [ ] Add visual-only classes for answer panels, source cards, upload panels, and idea status tiles.
- [ ] Do not change API helper imports, request payloads, or state transitions.

---

### Task 3: Verify Build and Update Progress

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: final verified frontend build.
- Produces: concise progress update only after build passes.

- [ ] Run `npm run build` from `frontend/research-assistant-frontend`.
- [ ] If build passes, record the Research Desk visual redesign and build result in `docs/status/current.md`.

---

## Self-Review

- Spec coverage: covers global shell, shared visual system, RAG page, Paper page, Idea page, build verification, and progress update.
- Placeholder scan: no placeholders; this is a visual refactor with no new backend behavior.
- Type consistency: no new data types or API signatures are introduced.
