# Subsection Heading Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore subsection heading detection quality for paper chunking while preventing table rows, sentence fragments, metric rows, and model-name rows from becoming section titles.

**Architecture:** Keep the current structured chunking pipeline intact and focus the change on `PaperSectionDetector`. Use a two-stage heading decision: first recall likely headings, especially numbered subsections and known academic subsection phrases; then reject known false positives before creating `DetectedSection` drafts.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, AssertJ, Maven Wrapper.

## Global Constraints

- Preserve existing PDF cleaning, structured chunk size, `chunkType`, profile job progress, frontend workflow queue, and Qdrant logic.
- Do not rewrite the parser or change database schemas in this task.
- Do not commit automatically; this repository requires an explicit user request before committing.
- Use TDD: write/adjust the failing behavior test before changing production logic.
- Use `JAVA_HOME=/path/to/jdk-21` for backend Maven commands.
- Keep `sectionTitle` as the original detected heading text after existing number normalization.

---

## File Structure

- Modify `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`
  - Responsibility: capture desired positive subsection detection and negative false-positive filtering behavior.
- Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperSectionDetector.java`
  - Responsibility: decide which cleaned PDF lines are section headings and split text into section drafts.
- Modify `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/SectionType.java` only if tests show a detected subsection gets the wrong normalized `SectionType`.
  - Responsibility: infer normalized section categories from original heading titles and section content.
- Modify `docs/status/current.md` after implementation and verification only if the change is completed and verified.
  - Responsibility: record durable project progress, not temporary test attempts.

---

### Task 1: Add focused subsection detection regression tests

**Files:**
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`

**Interfaces:**
- Consumes: `PaperSectionDetector.detect(String cleanedText): List<DetectedSection>`
- Produces: tests that define expected behavior for `PaperSectionDetector.looksLikeHeading(String line)` through public detection results.

- [ ] **Step 1: Add a positive regression test for numbered and unnumbered subsections**

Append this test method before the final closing brace of `PaperSectionDetectorTest`:

```java
    @Test
    void detectRestoresNumberedAndAcademicSubsectionHeadings() {
        PaperSectionDetector detector = new PaperSectionDetector();

        List<DetectedSection> sections = detector.detect("""
                3 Methodology
                This section introduces the whole method.
                3.1 Data preprocessing
                Raw wind speed records are cleaned and normalized.
                3.2 Feature extraction
                Temporal and spatial features are extracted.
                3.3 Model architecture
                The forecasting network contains attention and graph modules.
                4 Experiments
                Experiments are conducted on public datasets.
                4.1 Datasets
                Dataset details are described.
                4.2 Evaluation metrics
                MAE and RMSE are used to evaluate the models.
                Ablation Study
                We remove each module to evaluate its contribution.
                Parameter Sensitivity and Optimization Analysis
                We analyze how hyperparameters influence performance.
                """);

        assertThat(sections).extracting(DetectedSection::getTitle)
                .containsExactly(
                        "Methodology",
                        "Data preprocessing",
                        "Feature extraction",
                        "Model architecture",
                        "Experiments",
                        "Datasets",
                        "Evaluation metrics",
                        "Ablation Study",
                        "Parameter Sensitivity and Optimization Analysis"
                );
        assertThat(sections).extracting(DetectedSection::getType)
                .containsExactly(
                        SectionType.METHOD,
                        SectionType.EXPERIMENT,
                        SectionType.METHOD,
                        SectionType.METHOD,
                        SectionType.EXPERIMENT,
                        SectionType.EXPERIMENT,
                        SectionType.EXPERIMENT,
                        SectionType.EXPERIMENT,
                        SectionType.RESULT
                );
    }
```

- [ ] **Step 2: Add a negative regression test for known false positives**

Append this test method after the positive regression test:

```java
    @Test
    void detectRejectsKnownSubsectionFalsePositives() {
        PaperSectionDetector detector = new PaperSectionDetector();

        List<DetectedSection> sections = detector.detect("""
                4 Results and discussion
                This section reports the forecasting performance.
                Table 3 Performance comparison of different models
                Method Category Model Advantages Disadvantages
                Dataset 1 (10-minute wind speed records from Site A)
                Params Training Time Inference Latency
                RMSE MAE MAPE R2
                As shown in Table 3, the proposed method achieves the best performance
                BVMD-PAMST-MLP
                ST-LSTM
                WeatherGCNet
                Model complexity and efficiency analysis
                We compare the training cost and inference latency in detail.
                Conclusion and future research
                This study concludes that the proposed method is effective.
                """);

        assertThat(sections).extracting(DetectedSection::getTitle)
                .containsExactly(
                        "Results and discussion",
                        "Model complexity and efficiency analysis",
                        "Conclusion and future research"
                );
    }
```

- [ ] **Step 3: Run the focused detector test and confirm the current behavior**

Run from `<project-root>/backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest test
```

Expected before production changes:

- The command may fail if current rules miss one of the new subsection headings or misclassify a false positive.
- Existing tests should still compile; if the new expected `SectionType` values expose a current classification mismatch, handle it in Task 3.

---

### Task 2: Refine heading candidate recall and false-positive filtering

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperSectionDetector.java`

**Interfaces:**
- Consumes: existing private method `looksLikeHeading(String line)` used by `splitIntoDrafts(String cleanedText)`.
- Produces: stricter helper methods used by `looksLikeHeading(String line)`:
  - `isKnownFalseHeading(String line): boolean`
  - `looksLikeNumberedShortHeading(String line): boolean`
  - `looksLikeShortTitleCaseHeading(String line): boolean`
  - `containsAcademicHeadingKeyword(String normalized): boolean`

- [ ] **Step 1: Replace `looksLikeHeading` with a two-stage decision**

In `PaperSectionDetector.java`, replace the current `looksLikeHeading` method body with this implementation:

```java
    private boolean looksLikeHeading(String line) {
        if (line.length() > 120) {
            return false;
        }
        if (line.endsWith(".")) {
            return false;
        }
        if (isKnownFalseHeading(line)) {
            return false;
        }

        SectionType type = SectionType.fromTitle(line);
        if (type != SectionType.UNKNOWN) {
            return true;
        }

        return looksLikeNumberedShortHeading(line) || looksLikeShortTitleCaseHeading(line);
    }
```

- [ ] **Step 2: Replace the old false-positive helper**

Replace the current `isLikelyTableOrModelLine(String line)` method with this method. Keep the method name `isKnownFalseHeading` exactly as used in Step 1:

```java
    private boolean isKnownFalseHeading(String line) {
        String normalized = line.trim().toLowerCase();
        String compact = normalized.replaceAll("[^a-z0-9]", "");

        if (normalized.matches("^(table|tab\\.?|fig\\.?|figure)\\s*\\d+.*")) {
            return true;
        }
        if (normalized.matches("^dataset\\s*\\d+\\s*\\(.+\\).*") || normalized.matches("^dataset\\s*\\d+[:：].*")) {
            return true;
        }
        if (normalized.matches("^(as shown|as can be seen|it can be seen|we can see|the proposed|this paper|this study|these results|the results).+")) {
            return true;
        }
        if (normalized.contains("category") && normalized.contains("model")
                && (normalized.contains("advantage") || normalized.contains("disadvantage"))) {
            return true;
        }
        if (containsAtLeast(normalized, 3, "rmse", "mae", "mape", "r2", "params", "flops", "latency", "accuracy", "precision", "recall", "f1")) {
            return true;
        }
        if (normalized.matches(".*\\btraining time\\b.*") || normalized.matches(".*\\binference latency\\b.*")) {
            return true;
        }
        if (compact.matches("[a-z]+\\d*[a-z]*") && line.matches("^[A-Z][A-Za-z0-9-]{2,24}$")
                && !normalized.matches("abstract|introduction|background|method|methods|methodology|experiments?|results?|discussion|conclusion|references|appendix|training|optimizer|regularization")) {
            return true;
        }
        return false;
    }
```

- [ ] **Step 3: Add a small reusable counter helper**

Add this method below `isKnownFalseHeading`:

```java
    private boolean containsAtLeast(String text, int threshold, String... keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (text.matches(".*\\b" + keyword + "\\b.*")) {
                count++;
            }
        }
        return count >= threshold;
    }
```

- [ ] **Step 4: Replace numbered subsection recognition**

Replace the current `looksLikeNumberedShortHeading` method with this implementation:

```java
    private boolean looksLikeNumberedShortHeading(String line) {
        String normalized = line.trim();
        if (!normalized.matches("^(\\d+(?:\\.\\d+)*|[IVXLCDMivxlcdm]+)[.)、]?\\s+\\S.+")) {
            return false;
        }

        String title = normalizeHeadingTitle(normalized);
        if (title.length() > 90 || title.split("\\s+").length > 10) {
            return false;
        }
        if (title.matches(".*[,;:]\\s+.*")) {
            return false;
        }
        if (!title.matches("^[A-Z][A-Za-z0-9 ,&/()\\-]+$")) {
            return false;
        }
        return !isKnownFalseHeading(title);
    }
```

- [ ] **Step 5: Replace unnumbered short title recognition**

Replace the current `looksLikeShortTitleCaseHeading` method with this implementation:

```java
    private boolean looksLikeShortTitleCaseHeading(String line) {
        String normalized = line.trim();
        int wordCount = normalized.split("\\s+").length;
        if (wordCount > 8 || normalized.length() > 80) {
            return false;
        }
        if (!normalized.matches("^[A-Z][A-Za-z0-9 ,&/()\\-]+$")) {
            return false;
        }
        if (normalized.matches(".*[,;:]\\s+.*")) {
            return false;
        }

        String lower = normalized.toLowerCase();
        if (containsAcademicHeadingKeyword(lower)) {
            return true;
        }

        return wordCount <= 5 && normalized.matches("^([A-Z][A-Za-z0-9()\\-]*|and|or|of|in|the|for|to|with)(\\s+([A-Z][A-Za-z0-9()\\-]*|and|or|of|in|the|for|to|with))*$");
    }
```

- [ ] **Step 6: Add academic heading keyword helper**

Add this method below `looksLikeShortTitleCaseHeading`:

```java
    private boolean containsAcademicHeadingKeyword(String normalized) {
        return normalized.contains("dataset")
                || normalized.contains("evaluation")
                || normalized.contains("metric")
                || normalized.contains("baseline")
                || normalized.contains("ablation")
                || normalized.contains("parameter")
                || normalized.contains("sensitivity")
                || normalized.contains("complexity")
                || normalized.contains("efficiency")
                || normalized.contains("architecture")
                || normalized.contains("module")
                || normalized.contains("training")
                || normalized.contains("implementation")
                || normalized.contains("preprocessing")
                || normalized.contains("feature")
                || normalized.contains("attention")
                || normalized.contains("encoder")
                || normalized.contains("decoder")
                || normalized.contains("embedding")
                || normalized.contains("optimizer")
                || normalized.contains("regularization")
                || normalized.contains("loss")
                || normalized.contains("analysis")
                || normalized.contains("forecasting")
                || normalized.contains("prediction");
    }
```

- [ ] **Step 7: Run focused detector tests**

Run from `<project-root>/backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest test
```

Expected after Task 2:

- Most or all `PaperSectionDetectorTest` tests pass.
- If only `SectionType` expectations fail for correctly detected titles, proceed to Task 3.
- If title lists still fail, adjust only `PaperSectionDetector.java` helpers from this task and rerun the same command.

---

### Task 3: Align section type inference for restored subsection titles

**Files:**
- Modify only if needed: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/SectionType.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`

**Interfaces:**
- Consumes: `SectionType.fromTitle(String title)` and `SectionType.infer(String title, String content)`.
- Produces: stable classification for restored subsection headings.

- [ ] **Step 1: If Task 2 fails because `Data preprocessing` is classified as `UNKNOWN`, update method classification**

In `SectionType.fromTitle`, extend the `METHOD` condition by adding these normalized title checks inside the existing `if` that returns `METHOD`:

```java
                || normalized.contains("preprocessing")
                || normalized.contains("feature extraction")
                || normalized.contains("feature selection")
                || normalized.contains("module")
                || normalized.contains("loss function")
```

The resulting METHOD block must still return `METHOD` exactly once at the end of the block.

- [ ] **Step 2: If Task 2 fails because experiment subsection headings are classified as `UNKNOWN`, update experiment classification**

In `SectionType.fromTitle`, extend the `EXPERIMENT` condition by adding these normalized title checks inside the existing `if` that returns `EXPERIMENT`:

```java
                || normalized.contains("baseline")
                || normalized.contains("evaluation metrics")
                || normalized.contains("experimental setup")
                || normalized.contains("ablation study")
```

The resulting EXPERIMENT block must still return `EXPERIMENT` exactly once at the end of the block.

- [ ] **Step 3: If Task 2 fails because analysis subsection headings are classified incorrectly, keep analysis as `RESULT`**

Confirm the existing `RESULT` block contains these checks:

```java
        if (normalized.matches(".*\\bresults?\\b.*")
                || normalized.contains("analysis")
                || normalized.contains("performance")
                || normalized.contains("model variations")) {
            return RESULT;
        }
```

If this block is already present, do not change it.

- [ ] **Step 4: Run focused tests again**

Run from `<project-root>/backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest test
```

Expected:

- `PaperSectionDetectorTest` passes.
- The positive regression test reports all expected subsection titles.
- The negative regression test excludes all listed false positives.

---

### Task 4: Verify structured chunking still works with restored headings

**Files:**
- Test only: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/StructuredChunkingServiceTest.java`
- No production file changes unless an existing chunking test exposes a real integration failure caused by Task 2.

**Interfaces:**
- Consumes: `StructuredChunkingService` behavior that relies on detected section boundaries.
- Produces: verification that restored subsection headings do not break chunk generation, chunk type detection, or reference/noise handling.

- [ ] **Step 1: Run detector plus chunking tests**

Run from `<project-root>/backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest,StructuredChunkingServiceTest test
```

Expected:

- Both test classes pass.
- Existing table/figure chunk type tests remain green.

- [ ] **Step 2: If chunking tests fail because a fixture now has more sections, inspect the expected titles before changing assertions**

Use the failure message to identify whether the new section split is a desired restored subsection or a false positive.

- If it is a desired restored subsection, update only the test assertion that assumed the old count.
- If it is a false positive, return to Task 2 and add that exact line to `detectRejectsKnownSubsectionFalsePositives` before changing production code.

- [ ] **Step 3: Run full backend tests**

Run from `<project-root>/backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

Expected:

- All backend tests pass.
- No database migration is required.

---

### Task 5: Update progress documentation after verified implementation

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: actual test results from Tasks 1-4.
- Produces: one concise durable progress entry for this subsection heading detection fix.

- [ ] **Step 1: Add one concise progress entry**

In `docs/status/current.md`, append a short entry near the current PDF data quality section. Use this exact content, replacing the test count only if Maven reports a different count:

```text
PDF 小标题识别恢复优化已完成：PaperSectionDetector 改为“宽松召回 + 后置过滤”的标题判断方式，恢复 3.1/4.2 等编号小标题和常见学术短标题识别，同时过滤 Table/Figure 行、表格表头、指标行、正文长句、单个模型名和数据集描述行等误判。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest,StructuredChunkingServiceTest test 通过；JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过。
```

- [ ] **Step 2: Check current uncommitted diff summary**

Run from `<project-root>`:

```bash
git diff --stat HEAD -- backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure docs/status/current.md
```

Expected:

- Diff only includes the intended detector/test/doc changes plus pre-existing uncommitted structure changes already present before this task.
- Do not commit unless the user explicitly asks.

---

## Self-Review

- Spec coverage: The plan covers positive subsection recall, false-positive filtering, preservation of other parser features, testing, and documentation.
- Placeholder scan: No `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: Method names used in production snippets match the planned helper names: `isKnownFalseHeading`, `containsAtLeast`, `looksLikeNumberedShortHeading`, `looksLikeShortTitleCaseHeading`, and `containsAcademicHeadingKeyword`.
- Scope check: The plan is focused on `PaperSectionDetector` and optional `SectionType` alignment; it does not expand into parser rewrites, frontend changes, Qdrant changes, or database migrations.
