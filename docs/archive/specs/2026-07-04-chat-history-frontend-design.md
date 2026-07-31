# Chat History Frontend Integration Design

## Goal

Add a dedicated frontend page for browsing RAG chat history and continuing a previous session from the RAG chat page.

## Scope

This stage implements the MVP chat history frontend integration only:

- Add a new `/chat-history` route.
- Add a sidebar navigation entry: `History / 对话历史`.
- Add API helpers for existing backend chat history endpoints.
- Add a `ChatHistoryView.vue` page with a session list and message detail panel.
- Allow continuing a selected session by navigating to `/chat?sessionId=<id>`.
- Update `RagChatView.vue` to read `sessionId` from route query and reuse that session for new RAG questions.

This stage does not add session rename, session deletion, full source parsing UI, pagination, or search.

## Existing Backend Interfaces

```http
POST /api/chat/sessions?title=论文问答&paperId=1
GET /api/chat/sessions
GET /api/chat/sessions/{sessionId}/messages
```

Returned session fields:

```text
id
title
paperId
createTime
updateTime
```

Returned message fields:

```text
id
sessionId
role
content
modelProvider
modelName
sourcesJson
createTime
```

## Architecture

Create a focused API helper file:

```text
frontend/research-assistant-frontend/src/api/chatHistory.js
```

This file owns HTTP calls and response unwrapping for chat history endpoints.

Create a dedicated view:

```text
frontend/research-assistant-frontend/src/views/ChatHistoryView.vue
```

The view owns UI state: session list, selected session, message list, loading states, and navigation to continue a session.

Update existing files:

```text
frontend/research-assistant-frontend/src/router/index.js
frontend/research-assistant-frontend/src/App.vue
frontend/research-assistant-frontend/src/views/RagChatView.vue
```

## UI Design

Follow the current Research Desk visual system: deep-blue sidebar, paper-like cards, Element Plus controls, and restrained research-workbench copy.

Layout:

```text
Page heading
┌───────────────────────┬──────────────────────────────────┐
│ Session list           │ Message timeline                 │
│ - title                │ user question                    │
│ - #id / updated time   │ assistant answer                 │
│ - paperId if present   │ model tag + sourcesJson preview  │
└───────────────────────┴──────────────────────────────────┘
```

The session list highlights the selected session. The right panel shows an empty state until a session is selected.

Messages render by role:

- `user`: question-style card.
- `assistant`: answer-style card with model provider/name if present.
- `sourcesJson`: displayed in a collapsed text area style or preformatted block; no complex parsing in this stage.

## Data Flow

On page mount:

```text
ChatHistoryView mounted
→ GET /api/chat/sessions
→ store sessions
→ if sessions exist, optionally select the first session
→ GET /api/chat/sessions/{sessionId}/messages
→ render messages
```

Continue session:

```text
Click “继续问答”
→ router.push({ path: '/chat', query: { sessionId } })
→ RagChatView reads route.query.sessionId
→ next POST /api/rag/chat includes that sessionId
```

## Error Handling

- Session list load failure: show Element Plus error message.
- Message list load failure: show Element Plus error message and keep selected session visible.
- Invalid query `sessionId` in RAG page: ignore it and allow the backend to create a new session when asking.

## Testing

Add a Node test file:

```text
frontend/research-assistant-frontend/src/api/chatHistory.test.js
```

Test cases:

- `listChatSessions` calls `GET /api/chat/sessions`.
- `listChatMessages` calls `GET /api/chat/sessions/{sessionId}/messages`.
- `createChatSession` calls `POST /api/chat/sessions` with non-empty query params.

Verification commands:

```bash
node --test src/api/chatHistory.test.js
npm run build
```

## Completion Criteria

- `/chat-history` route opens without build errors.
- Navigation includes `History / 对话历史`.
- Chat history API helper tests pass.
- Frontend production build succeeds.
- `docs/status/current.md` records the completed frontend chat history integration and verification result.
