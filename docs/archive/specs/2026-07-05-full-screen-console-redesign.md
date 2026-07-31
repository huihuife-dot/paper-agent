# Full-Screen Console UI Redesign

## Goal

Improve the frontend visual experience after user feedback that the previous dashboard felt unattractive, too fragmented, and too card-heavy.

The new direction is a full-screen console layout:

- fewer visible boxes;
- no obvious uneven card heights;
- main areas fill the available page space;
- secondary features open through buttons, menus, dialogs, or drawers;
- scrollbars are visually hidden while content remains scrollable;
- pages feel flatter, cleaner, and more balanced.

## User Feedback Converted to Requirements

1. Do not use many obvious long/short uneven cards.
2. Make pages fill the available screen instead of looking like scattered blocks.
3. Use options, menus, dialogs, and drawers for secondary functions.
4. Avoid visible scrollbars.
5. Make the page more aesthetically comfortable and less cluttered.
6. For the chat page, move sources and save-idea operations into top buttons and a drawer instead of a permanent right-side panel.

## Layout Direction

Use a consistent structure:

```text
Global top nav
Page toolbar with title and actions
Full-height content frame
  Optional left menu column
  Main content area fills the rest
```

The design should look like one cohesive work surface rather than many independent cards.

## Home Page

Home uses one full-height overview frame.

Structure:

```text
Toolbar: title + refresh + quick module buttons/menu
Stat strip: paper total | parsed | vectorized | sessions | ideas
Equal lower panels: recent sessions | recent ideas
```

Rules:

- Use equal-height sections.
- Do not use staggered dashboard card blocks.
- Quick entry actions live in the toolbar.
- Recent session and recent idea lists should align visually.

## Chat Page

Chat uses two columns only:

```text
Left: session menu/list
Main: conversation timeline + composer
Toolbar actions: refresh sessions | latest sources | save as idea
```

Rules:

- Remove permanent right-side sources panel.
- Add a `引用片段` button that opens a drawer.
- Add a `保存想法` button in the toolbar.
- Keep session list and conversation area the same height.
- Keep the existing session behavior and API calls.

## Papers Page

Papers use a left status menu and one main table.

```text
Toolbar: title + add paper + refresh
Left menu: all | pending parse | pending vector | vectorized
Main: paper table fills the content area
Upload: dialog
```

Rules:

- No status stat cards in the sidebar.
- The sidebar should read as a flat menu.
- Paper upload remains a dialog.
- The table should visually fill the main content area.

## Ideas Page

Ideas use top search, left status menu, and one main table.

```text
Toolbar: title + search + refresh
Left menu: all | draft | idea | todo | implemented
Main: idea table fills the content area
Detail: dialog
```

Rules:

- Remove the small status grid.
- Put keyword search in the toolbar.
- Keep status filtering in the left menu.
- Keep row-level view, status transition, and delete actions.

## Scrollbar Treatment

- The overall page should not show browser scrollbars.
- Internal areas may scroll, but scrollbars should be hidden visually.
- Use cross-browser CSS:

```css
.scroll-clean {
  scrollbar-width: none;
}

.scroll-clean::-webkit-scrollbar {
  width: 0;
  height: 0;
}
```

## Visual Style

- Light, flat, full-screen console.
- Reduce shadows and decorative gradients.
- Use fewer borders and more alignment.
- Keep deep blue as action/active color.
- Use consistent frame heights and equal lower panels.
- Avoid making each dataset look like a separate floating card.

## Files Expected to Change

- `frontend/research-assistant-frontend/src/style.css`
- `frontend/research-assistant-frontend/src/views/DashboardView.vue`
- `frontend/research-assistant-frontend/src/views/RagChatView.vue`
- `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`
- `frontend/research-assistant-frontend/src/views/ResearchIdeasView.vue`
- `docs/status/current.md`

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

- Home no longer looks like uneven dashboard cards.
- Chat page is two-column and sources open in a drawer.
- Papers and ideas pages use flat side menus and full-height tables.
- Secondary actions use toolbar buttons, dialogs, or drawers.
- Visible scrollbars are hidden.
- Tests and build pass.
