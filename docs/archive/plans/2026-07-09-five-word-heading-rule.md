# Five-Word Heading Rule Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make paper section detection stricter by accepting unnumbered short headings only when they contain at most five words.

**Architecture:** Keep heading boundary detection in `PaperSectionDetector`. Standard headings remain whitelist-based, numbered headings are allowed up to seven words, and unnumbered Title Case headings are capped at five words.

**Tech Stack:** Spring Boot backend, Java 21, Maven, JUnit.

## Global Constraints

- Unnumbered headings must contain at most 5 words.
- Numbered headings must contain at most 7 words after removing the number.
- Standard heading whitelist remains accepted directly.
- Sentence-fragment rejection remains enabled.
- Update progress documentation after verification.

---

### Task 1: Update heading limit tests

**Files:**
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/StructuredChunkingServiceTest.java`

**Interfaces:**
- Consumes: `PaperSectionDetector.detect(String cleanedText)`.
- Produces: tests showing `Applications of Attention in our Model` is not a separate section, while `Encoder and Decoder Stacks` remains accepted.

- [ ] Add assertions that unnumbered headings over five words are merged into the previous section.
- [ ] Keep assertions for five-word headings and shorter headings.

### Task 2: Implement five-word limits

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperSectionDetector.java`

**Interfaces:**
- Produces: `looksLikeShortTitleCaseHeading` capped at 5 words, `looksLikeNumberedShortHeading` capped at 7 words.

- [ ] Change unnumbered word limit from 8 to 5.
- [ ] Change numbered word limit from 10 to 7.

### Task 3: Verify and document

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Records test and runtime verification evidence.

- [ ] Run `JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest,StructuredChunkingServiceTest test`.
- [ ] Run full backend tests if focused tests pass.
- [ ] Re-parse paperId=11 through the running backend if available and inspect section/chunk counts.
- [ ] Update `docs/status/current.md`.
