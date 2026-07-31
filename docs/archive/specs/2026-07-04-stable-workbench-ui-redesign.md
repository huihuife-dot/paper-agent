# Stable Workbench UI Redesign Design

## Goal

Refactor the frontend into a calmer, stable research workbench layout so pages no longer jump in size as content loads, the sidebar carries useful context, and actions are not visually dumped into the main content area.

## User Feedback Driving This Work

The current frontend feels:

- visually messy;
- too crowded;
- unstable because content loading changes page size;
- overly focused on exposing every backend action in the main page;
- weak in sidebar usage;
- awkward in wording, with too many technical labels.

## Scope

This stage changes frontend layout, styling, and copy only. It does not add backend interfaces or new product capabilities.

Files in scope:

```text
frontend/research-assistant-frontend/src/App.vue
frontend/research-assistant-frontend/src/style.css
frontend/research-assistant-frontend/src/views/PaperManagementView.vue
frontend/research-assistant-frontend/src/views/RagChatView.vue
frontend/research-assistant-frontend/src/views/ChatHistoryView.vue
frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue
```

## Chosen Approach

Use **stable workbench layout**.

The app should feel like a fixed research desk rather than a scrolling collection of admin cards.

High-level structure:

```text
┌──────────────────────┬────────────────────────────────────────┐
│ Fixed sidebar         │ Fixed-height workspace                  │
│                      │                                        │
│ Brand                │ Compact page toolbar                    │
│ Navigation           │ Main panels                             │
│ Current workflow     │ - panel bodies scroll internally         │
│ Short guidance       │ - page height stays stable               │
└──────────────────────┴────────────────────────────────────────┘
```

## Layout Rules

### App Shell

- `app-shell` uses `height: 100vh` and `overflow: hidden`.
- Sidebar remains fixed width on desktop.
- Workspace is a vertical flex column with `min-height: 0`.
- `RouterView` area owns scrolling behavior through panels, not page growth.

### Page Structure

Each page should follow:

```text
page-panel
├── page-toolbar
│   ├── compact title/copy
│   └── main page action if needed
└── workbench-grid
    ├── side panel / control panel
    └── main panel / detail panel
```

The old oversized `page-heading` hero treatment should be replaced by a compact `page-toolbar`.

### Panels

- Use `workbench-card` or adapt `workflow-card` to fixed-height behavior.
- Panel bodies should use internal scrolling where needed.
- Lists, tables, message timelines, and source stacks must not grow the outer page indefinitely.
- Use consistent heights:
  - Desktop workspace content roughly fills remaining viewport height.
  - Tables should use fixed/max heights where practical.

### Sidebar

Sidebar should do more than route navigation:

- Brand: `Research Desk`.
- Navigation grouped in product order:
  - 文献库
  - 论文问答
  - 对话历史
  - 研究想法
- A concise workflow note:
  - `上传文献 → 向量化 → 问答 → 沉淀想法`
- A small status block:
  - `当前阶段：前端 MVP 联调`

Avoid verbose marketing copy.

## Page-Specific Changes

### Paper Management

Current problem: upload form and list sit as large blocks; table growth can dominate page.

New structure:

- Left control panel: upload form.
- Right main panel: paper table.
- Table panel scrolls internally.
- Copy changes:
  - `文献库与解析工作流` → `文献库`
  - `研究文献索引` → `文献列表`
  - `导入论文` remains acceptable.
  - Remove explicit API wording from upload tip.

### RAG Chat

Current problem: answer and sources can grow the page; labels feel technical.

New structure:

- Left control panel: question input, citation count, session info, save idea action.
- Right main panel split visually into:
  - answer area;
  - source area with internal scroll.
- Copy changes:
  - `基于证据的论文问答` → `论文问答`
  - `Evidence stack` → `引用片段`
  - `召回数量 topK` → `引用数量`
  - `Sources` → `引用数`

### Chat History

Current problem: session list and messages can grow unpredictably.

New structure:

- Left panel: session list with fixed internal scroll.
- Right panel: message timeline with fixed internal scroll.
- Copy changes:
  - `Conversation archive` → `History`
  - `查看 sourcesJson` → `查看原始引用数据`

### Research Ideas

Current problem: stats, filters, table, and actions compete visually.

New structure:

- Left panel: status stats and filters.
- Right panel: idea table.
- Keep status transition in operation column, per user preference.
- Copy changes:
  - `Research Idea` visible label can be `研究想法` in UI.
  - `Idea ledger` → `Ideas`
  - `想法索引` → `想法列表`
  - `研究推进状态` → `状态概览`

## Visual Direction

Keep the existing deep-blue research desk identity, but reduce decorative weight:

- Less large shadow.
- Fewer oversized rounded hero cards.
- More consistent panel borders.
- More compact titles.
- Stable spacing.

Tone: practical research tool, not marketing dashboard.

## Error Handling / Behavior

No API behavior changes. Existing loading, error messages, and actions remain.

The main behavioral improvement is layout stability:

- Loading content should not significantly resize the outer page.
- Long answers, long source lists, long message lists, and long tables scroll inside their panels.

## Testing

Use existing frontend verification:

```bash
node --test src/api/papers.test.js
node --test src/api/rag.test.js
node --test src/api/researchIdeas.test.js
node --test src/api/chatHistory.test.js
npm run build
```

Browser validation after implementation:

- Resize browser: layout remains stable.
- Load long RAG answer: answer panel scrolls, page shell does not jump.
- Open chat history with many messages: message panel scrolls.
- Open ideas with many rows: table panel remains controlled.
- Sidebar stays usable and not cluttered.

## Completion Criteria

- App shell uses stable viewport-height workbench layout.
- Sidebar includes useful workflow/status context.
- Pages use compact toolbar instead of oversized hero blocks.
- Major content areas scroll internally.
- User-facing copy is less technical and more consistent.
- Existing API helper tests pass.
- Frontend build succeeds.
