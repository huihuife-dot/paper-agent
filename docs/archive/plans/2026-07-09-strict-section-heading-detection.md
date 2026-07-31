# Strict Section Heading Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce false section boundaries caused by PDFBox line breaks by using strict standalone short-heading rules.

**Architecture:** Keep heading boundary detection in `PaperSectionDetector`, semantic section type classification in `SectionType`, and source text noise filtering in `PaperTextCleaner`. Heading detection must first prove a line looks like a standalone heading; only then may section type keywords classify it.

**Tech Stack:** Spring Boot backend, Java 21, JUnit tests through Maven.

## Global Constraints

- Do not let content keywords such as `propose`, `architecture`, `experiment`, or `result` alone create section boundaries.
- Prefer fewer section boundaries over false boundaries that split Abstract or Introduction.
- Preserve References detection.
- Keep old API/database behavior unchanged; this is a parser quality change only.

---

### Task 1: Strict heading boundary rules

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperSectionDetector.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`

**Interfaces:**
- Consumes: `SectionType.fromTitle(String title)`.
- Produces: `detect(String cleanedText)` where sentence fragments are content, not titles.

- [ ] Add failing tests that reject Abstract fragments such as `mechanism. We propose...` and accept short headings such as `Multi-Head Attention`.
- [ ] Implement strict rules: standard title, numbered heading, or 1-8 word Title Case short heading; reject sentence fragments, punctuation endings, lower-case starts, Table/Figure captions, and front-matter metadata.
- [ ] Run `JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest test`.

### Task 2: Safe section type classification

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/SectionType.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`

**Interfaces:**
- Consumes: confirmed heading title strings.
- Produces: `SectionType` classification for accepted headings only.

- [ ] Remove broad `proposed` title classification that turns ordinary sentences into METHOD.
- [ ] Add precise short-heading terms: attention/encoder/decoder/feed-forward/embedding/positional encoding/self-attention/head -> METHOD; training/optimizer/regularization/machine translation/model variations/parsing -> EXPERIMENT or RESULT as appropriate.
- [ ] Keep content-based `infer(title, content)` for fallback after sections are formed.

### Task 3: Front-matter noise filtering

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperTextCleaner.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperTextCleanerTest.java`

**Interfaces:**
- Produces cleaned text before heading detection.

- [ ] Add tests for filtering NIPS permission statement, equal contribution line, work-performed footnote, conference footer, and arXiv marker.
- [ ] Add targeted noise filters without removing normal Abstract/Introduction content.

### Task 4: Structured chunking regression

**Files:**
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/StructuredChunkingServiceTest.java`

**Interfaces:**
- Consumes strict detector and cleaner.
- Produces chunk list where Abstract fragments stay under Abstract and short model headings are accepted.

- [ ] Add regression test with Transformer-like text.
- [ ] Run focused structure tests.

### Task 5: Progress documentation

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Records substantive parser quality improvement and verification.

- [ ] Append a concise progress entry after tests pass.
