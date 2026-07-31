# Dashboard + Module Sidebar UI Redesign

## Goal

Refactor the frontend into a clean research-assistant dashboard layout that matches the approved direction:

- a global top navigation bar for 首页、对话、文献、想法;
- a home dashboard focused on statistics and recent activity;
- module-specific sidebars for chat, papers, and ideas;
- secondary actions moved into buttons, dialogs, or sidebars instead of permanently occupying the main surface;
- a simpler, sharper visual style inspired by modern dashboard products.

## Approved Scope

The implementation will keep the existing Vue 3 + Vite + Element Plus frontend and existing backend APIs. It will primarily change page structure, component state, and CSS. It will not add a new backend dashboard endpoint in this stage.

## Information Architecture

```text
Global top nav: 首页 | 对话 | 文献 | 想法

首页:
  Dashboard statistics, recent sessions, recent ideas, and light quick-entry cards.

对话:
  Left module sidebar: session list grouped by session id/title.
  Main area: selected conversation history and composer.
  Right module panel: latest sources, save-as-idea action, and session metadata.

文献:
  Left module sidebar: paper counts, status filters, refresh, and upload button.
  Main area: paper list and row-level parse/vectorize/chat actions.
  Upload paper: dialog opened by the sidebar button.

想法:
  Left module sidebar: saveType stats, keyword search, status filters, refresh.
  Main area: idea list and row-level view/status/delete actions.
  Detail: dialog.
```

## Page Design

### App Shell

- Keep the existing fixed-height app shell.
- Use a single global header with the brand on the left, top nav in the middle, and a small MVP/status tag on the right.
- The main workspace should fill the remaining viewport height and let each page handle its own internal scrolling.

### Home Dashboard

The home page should use stat tiles rather than heavy charts.

Data sources:

- `listPapers()`
- `listChatSessions()`
- `listResearchIdeas()`
- `countResearchIdeaSaveTypes()`

Display:

- total papers, parsed papers, vectorized papers;
- total chat sessions;
- total research ideas, draft, idea, todo, implemented;
- recent sessions;
- recent ideas;
- quick-entry cards for chat, papers, and ideas.

### Chat Module

The existing session-based chat direction is kept.

- Left sidebar lists sessions and exposes a refresh action.
- Selecting a session updates the route query and loads that session's messages.
- The center panel shows message history and keeps the composer fixed at the bottom of that panel.
- The right panel contains latest sources and the save-as-idea action.

### Paper Module

- Move the current upload form out of the always-visible sidebar and into an `el-dialog`.
- Sidebar contains counts and status filters.
- Main table can be filtered locally by parse/vector status.
- Row actions remain: parse, vectorize, chat.

### Idea Module

- Sidebar contains status counts, keyword search, and saveType filter buttons.
- Main table remains focused on the idea list.
- Detail remains in a dialog.
- Status transition remains a row operation.

## Visual Direction

- Use a clean light dashboard surface.
- Keep deep blue as the primary brand/accent color.
- Reduce decorative gradients and heavy paper texture.
- Use light cards, clear spacing, subtle borders, and concise copy.
- Keep statistics readable as headline numbers with labels, not dense charts.
- Avoid exposing technical implementation details on the main page unless needed for the user's workflow.

## Files Expected to Change

- `frontend/research-assistant-frontend/src/App.vue`
- `frontend/research-assistant-frontend/src/views/DashboardView.vue`
- `frontend/research-assistant-frontend/src/views/RagChatView.vue`
- `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`
- `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`
- `frontend/research-assistant-frontend/src/style.css`
- `docs/status/current.md` after implementation and verification

## Verification Plan

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/papers.test.js
node --test src/api/rag.test.js
node --test src/api/researchIdeas.test.js
node --test src/api/chatHistory.test.js
npm run build
```

## Completion Criteria

- Global top navigation is visible and clean.
- Home dashboard shows statistics and recent activity.
- Chat, papers, and ideas each have module-specific sidebars.
- Paper upload opens in a dialog instead of being permanently displayed.
- Main pages focus on the primary list or conversation.
- Frontend API helper tests pass.
- Frontend build passes.
