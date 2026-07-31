# Research Engineering Structured Task Package

## Objective

Make the web system give the local Research Engineering Agent a complete and stable task brief, rather than a loose collection of paper/Idea text. The same structure supports both paper reproduction and Idea-driven code improvement.

## Contract

The read-only context APIs now include `taskPackage`:

- `taskType`: `PAPER_REPRODUCTION` or `IDEA_CODE_IMPROVEMENT`
- `goal`: one concrete code-delivery goal
- `implementationSteps`: ordered implementation steps
- `interfaces`: expected input/output boundary
- `parameters`: known values and where they came from
- `evidence`: traceable short evidence snippets
- `assumptionsAndGaps`: facts that are missing and cannot be invented silently
- `codeScope`: scope placeholder from MyAgent; local Bridge fills real local paths
- `acceptanceChecks`: basic runnable checks only, never research experiments

## Local specialization

The localhost Bridge fetches this online package and combines it with the selected local repository baseline. The final local package carries approved paths, prohibited paths, and existing safe checks. The Agent prompt requires each action to be treated as one of:

1. evidence-backed;
2. explicit safe assumption;
3. blocked because evidence is absent.

The permission pipeline remains the enforcement layer: an Idea Agent cannot write outside the baseline even if a model proposes it.

## Implementation checklist

- [x] Add typed `taskPackage` DTO to Paper and Idea context APIs.
- [x] Persist the remote package in the immutable local context snapshot.
- [x] Build the final local package with repository scope and use it in code-delivery prompts.
- [x] Add CodeAgent contract tests for persistence, scope, and prompt use.
- [x] Compile the MyAgent backend and run focused CodeAgent tests.

## Out of scope

This task package directs implementation and basic runnability only. It does not authorize autonomous experiments, full paper evaluation, package installation, Git merge/push, or modifications to MyAgent records.
