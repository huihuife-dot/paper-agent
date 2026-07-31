# Research Engineering Agent web entry: progress (2026-07-22)

## Completed

- Added a reusable launch dialog for Paper reproduction and Idea-driven repository improvement.
- Added tested command templates. A Paper command consistently keeps the selected paper ID; an Idea command explicitly requires a local `repository-baseline.json`.
- Added visible entry buttons in the Paper and Research Idea pages.
- The dialog copies commands only. It clearly tells the user that the web page cannot start a local process by itself.

## Verification

- Frontend production build produced `dist/index.html`.
- Focused command-template test: 2/2 passed.

## Next

Build an optional local Bridge service if a real browser-button-to-local-Agent handoff is needed. That is a separate security-sensitive feature because the browser must authenticate to the local machine.

## Bridge UI follow-up (2026-07-22)

- The Paper launch dialog can now call the authenticated local Bridge directly.
- The user explicitly enters the local Bridge URL and token for the current request; the token is cleared after success and is not sent to MyAgent.
- The frontend sends only `{ "paper_id": id }` plus the token header to a localhost URL.
- Focused frontend tests: 3/3 passed; production build output is present.
