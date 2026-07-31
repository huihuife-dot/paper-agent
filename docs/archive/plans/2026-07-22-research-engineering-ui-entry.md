# Research Engineering Agent: web entry plan

## Goal

Let a user select a Paper or Research Idea in MyAgent and obtain the exact local CLI commands needed to create and fetch the matching long-lived Agent project.

## Boundary

- The browser only displays and copies commands; it must not claim to launch a local process.
- Paper mode creates a reproduction project from one selected paper.
- Idea mode requires the user to provide an existing local repository worktree and a local baseline JSON file.
- The server remains read-only during this handoff.

## Steps

1. [x] Add a tested command-template helper.
2. [x] Add one reusable command dialog and expose it from Paper and Idea pages.
3. [x] Build the frontend and update progress/status documentation.
