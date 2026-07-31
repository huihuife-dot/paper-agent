# Frontend MVP Structure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first usable Vue frontend structure for the paper/literature AI research assistant.

**Architecture:** The Vue app uses a small shell layout with top navigation, Vue Router for page switching, Element Plus for UI components, and a focused Axios client for Spring Boot API calls. This stage creates page skeletons only; backend API integration actions are represented as UI placeholders and client helper functions.

**Tech Stack:** Vue 3, Vite 5, Axios, Element Plus, Vue Router 4.

## Global Constraints

- Project path: `<project-root>/frontend/research-assistant-frontend`.
- Current Node.js is `v21.5.0`; keep Vite at 5.x and Vue Router at 4.x.
- Do not add unrelated dependencies.
- Match the current JavaScript Vue scaffold; do not convert to TypeScript.
- Keep page copy in Chinese for the current user workflow.
- Design direction: research workstation, calm document-oriented UI, not a generic marketing dashboard.

---

### Task 1: Register Element Plus and Router

**Files:**
- Modify: `src/main.js`
- Create: `src/router/index.js`

**Interfaces:**
- Produces: `router` default export from `src/router/index.js`.
- Consumes: Vue app created in `src/main.js`.

- [ ] **Step 1: Create router file**

Create `src/router/index.js` with route definitions for `/`, `/papers`, `/chat`, and `/ideas`.

- [ ] **Step 2: Register plugins**

Update `src/main.js` to import Element Plus CSS, use Element Plus, and use the router.

- [ ] **Step 3: Verify build**

Run: `npm run build`
Expected: Vite build completes successfully.

---

### Task 2: Create API Client

**Files:**
- Create: `src/api/client.js`
- Create: `src/api/papers.js`
- Create: `src/api/rag.js`
- Create: `src/api/researchIdeas.js`

**Interfaces:**
- Produces: Axios instance `apiClient` with base URL from `VITE_API_BASE_URL`, defaulting to `http://localhost:8080`.
- Produces: named API helper functions for later page integration.

- [ ] **Step 1: Create Axios client**

Create `src/api/client.js` with a reusable Axios instance.

- [ ] **Step 2: Create endpoint helper modules**

Create focused modules for papers, RAG, and Research Idea APIs.

- [ ] **Step 3: Verify build**

Run: `npm run build`
Expected: Vite build completes successfully.

---

### Task 3: Replace Default App with MVP Shell

**Files:**
- Modify: `src/App.vue`
- Modify: `src/style.css`
- Delete: `src/components/HelloWorld.vue`

**Interfaces:**
- Consumes: Vue Router routes.
- Produces: App shell with navigation and `<router-view />`.

- [ ] **Step 1: Replace default Vite UI**

Update `App.vue` with a research-assistant layout and navigation menu.

- [ ] **Step 2: Replace global styles**

Update `style.css` with app-wide layout and Element Plus-friendly styling.

- [ ] **Step 3: Remove unused default component**

Delete `src/components/HelloWorld.vue` because the app shell no longer imports it.

- [ ] **Step 4: Verify build**

Run: `npm run build`
Expected: Vite build completes successfully.

---

### Task 4: Create MVP Page Skeletons

**Files:**
- Create: `src/views/PaperManagementView.vue`
- Create: `src/views/RagChatView.vue`
- Create: `src/views/ResearchIdeasView.vue`

**Interfaces:**
- Consumes: routes from `src/router/index.js`.
- Produces: three page components used by router.

- [ ] **Step 1: Create Paper Management page**

Include upload, parse, vectorize, and paper list placeholders.

- [ ] **Step 2: Create RAG Chat page**

Include question input, answer area, source list, and save-as-idea action placeholders.

- [ ] **Step 3: Create Research Idea page**

Include filters, status summary, and idea list placeholders.

- [ ] **Step 4: Verify build**

Run: `npm run build`
Expected: Vite build completes successfully.

---

## Self-Review

- Spec coverage: covers router, Element Plus, Axios client, and three MVP page skeletons.
- Placeholder scan: page skeleton placeholders are intentional UI placeholders for this stage, not missing implementation details.
- Type consistency: JavaScript modules export names used by later page integration stages.
