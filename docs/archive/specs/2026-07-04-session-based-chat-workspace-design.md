# Session-Based Chat Workspace Design

## Goal

Fix the current split between `论文问答` and `对话历史` by making `/chat` the single session-based chat workspace. Users should see the current session history while asking follow-up questions.

## Problem

The current frontend separates:

- `/chat`: ask one question and see the latest answer/sources;
- `/chat-history`: browse sessions and messages.

This is confusing because chat history is not a separate product workflow. A conversation is session-based, and the user should see the session message timeline while continuing to ask.

## Chosen Design

Merge chat history into `/chat`.

The `/chat` page becomes:

```text
┌───────────────┬───────────────────────────────┬──────────────────┐
│ 会话列表        │ 当前对话                        │ 引用与操作          │
│               │                               │                  │
│ Session A     │ user message                   │ 当前回答引用        │
│ Session B     │ assistant answer               │ 保存为研究想法      │
│ Session C     │ user message                   │ 当前会话信息        │
│               │ assistant answer               │                  │
│               │                               │                  │
│               │ 底部输入框：继续提问              │                  │
└───────────────┴───────────────────────────────┴──────────────────┘
```

## Navigation

Remove `对话历史` from the sidebar navigation. Keep the route/file for now to avoid unnecessary deletion, but the main user path is `/chat`.

Sidebar order becomes:

```text
文献库
论文问答
研究想法
```

## Data Flow

On `/chat` mount:

```text
GET /api/chat/sessions
→ show session list
→ if route query has sessionId, select that session
→ else select latest session if available
→ GET /api/chat/sessions/{sessionId}/messages
→ show current conversation timeline
```

When asking:

```text
POST /api/rag/chat with current sessionId if present
→ backend saves user + assistant messages
→ response returns sessionId + answer + sources
→ frontend sets current sessionId
→ reloads session messages
→ reloads session list
→ right panel shows latest sources
```

If no session exists yet, first question creates one through `/api/rag/chat` because backend already handles missing `sessionId`.

## UI Behavior

- Left panel: session list, refresh button, selected session highlight.
- Middle panel: current session message timeline and question composer.
- Right panel: latest citation sources, session metadata, save as research idea.
- Message timeline scrolls internally.
- Composer stays in the current conversation panel, not in a separate disconnected card.

## Copy

Use user-facing wording:

- `会话列表`
- `当前对话`
- `继续提问`
- `引用片段`
- `保存为研究想法`
- `暂无会话，先提出一个问题。`

Avoid exposing technical labels like `sourcesJson` in the main chat workspace.

## Scope

In this stage:

- Modify `RagChatView.vue`.
- Modify `App.vue` to hide the `对话历史` nav item.
- Keep `ChatHistoryView.vue` and `/chat-history` route for now; they can be removed later after browser validation.
- No backend changes.

## Verification

Run:

```bash
node --test src/api/rag.test.js
node --test src/api/chatHistory.test.js
npm run build
```

Browser validation:

1. Open `/chat`.
2. Confirm session list appears.
3. Select a session and see its historical messages.
4. Ask a follow-up question.
5. Confirm the new user/assistant messages appear in the same timeline.
6. Confirm latest sources appear in the right panel.
7. Confirm sidebar no longer shows `对话历史`.
