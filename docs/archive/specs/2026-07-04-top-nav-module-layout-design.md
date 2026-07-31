# Top Navigation + Module Sidebars Layout Design

## Goal

Refactor the frontend information architecture into a clean product layout:

- global top navigation for primary modules;
- home dashboard for statistics and recent activity;
- module-specific sidebars for chat, papers, and ideas;
- main content focused on one primary list/conversation, with secondary actions moved into sidebars, dialogs, or drawers.

## User Requirements

1. Add a home page.
2. Use a top navigation bar instead of a global sidebar.
3. Top navigation should show the primary functions: 对话、文献、想法, plus 首页.
4. Home page should mainly show statistics and overview data.
5. Chat module should have a sidebar divided by session id/session title. Clicking a session opens that chat and shows its history.
6. Papers and Ideas modules may also have sidebars so future features can be added there.
7. Do not pile all features on the main page. Use buttons, dialogs, drawers, or sidebars for secondary actions.
8. Keep the webpage clean and organized.

## Information Architecture

```text
Top nav: 首页 | 对话 | 文献 | 想法

Home:
  Dashboard stats and recent activity

Chat:
  Left module sidebar: sessions
  Main area: current conversation + composer
  Right/secondary area: latest sources and save as idea action

Papers:
  Left module sidebar: paper status/filter actions and add paper button
  Main area: paper list
  Add paper: dialog

Ideas:
  Left module sidebar: saveType status/filter actions
  Main area: idea list
  Detail/edit actions: dialog/drawer
```

## Route Structure

- `/` → Dashboard home
- `/chat` → Session-based paper chat
- `/papers` → Paper library
- `/ideas` → Research ideas
- `/chat-history` remains available for now but is not shown in top nav.

## Home Dashboard

Home should aggregate existing frontend API calls rather than adding a backend dashboard endpoint in this stage.

Use:

- `GET /api/papers`
- `GET /api/chat/sessions`
- `GET /api/research-ideas`
- `GET /api/research-ideas/stats/save-type`

Display:

- total papers;
- parsed papers;
- vectorized papers;
- chat sessions;
- research ideas total;
- idea saveType stats;
- recent sessions;
- recent ideas.

## App Shell

Replace current left global sidebar with:

```text
header.top-nav
main.app-main
```

Top nav:

- brand: Research Desk / 论文证据工作台
- nav links: 首页、对话、文献、想法
- small stage tag: MVP 联调

App shell should still use fixed viewport height:

- app root: `height: 100vh`
- top nav fixed height
- main content: remaining height
- module panels scroll internally

## Module Sidebar Pattern

Use a shared two-column module layout:

```text
module-layout
├── module-sidebar
└── module-main
```

Home can use a single-column dashboard layout.

## Paper Module

Main area shows paper list only.

Sidebar contains:

- paper counts if available;
- filter shortcuts: 全部、待解析、待向量化、已向量化;
- primary action: 添加文献.

Add paper is a dialog containing the upload form. The upload form should not be permanently visible in the main page.

## Ideas Module

Main area shows ideas list only.

Sidebar contains:

- status stats;
- filter shortcuts: 全部、草稿、想法、待办、已实现;
- keyword search.

Keep status transition in the row operation menu/button area.

## Chat Module

Chat already became session-based. Keep that direction but align it with top-nav shell.

Left sidebar:

- sessions list;
- refresh sessions.

Middle/main:

- current session message timeline;
- composer at bottom.

Right panel:

- latest sources;
- save as idea;
- session metadata.

## Visual Style

- Clean, calm research tool.
- Less decoration than earlier versions.
- Keep deep blue as brand color but use it mostly in top nav and accents.
- Use pale paper panels with light borders.
- Avoid oversized hero sections.
- Avoid technical copy like API paths, `topK`, `sourcesJson` on main surfaces.

## Testing

Run all frontend API helper tests and build:

```bash
node --test src/api/papers.test.js
node --test src/api/rag.test.js
node --test src/api/researchIdeas.test.js
node --test src/api/chatHistory.test.js
npm run build
```

## Completion Criteria

- Top nav is visible globally.
- Home route `/` shows dashboard statistics.
- Chat/Papers/Ideas each use module-specific sidebars.
- Paper upload is opened from a button/dialog, not always visible.
- Main pages focus on lists/conversation instead of all controls.
- Tests and build pass.
