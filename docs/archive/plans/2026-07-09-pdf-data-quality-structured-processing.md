# PDF 数据清洗、章节识别与段落分块质量升级 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve PDF ingestion quality so `paper_section` reflects real paper sections, `paper_chunk` uses cleaner paragraph/small-section chunks, and `paper_section_summary` stops being dominated by “信息不足”.

**Architecture:** Strengthen the existing Java/PDFBox pipeline rather than introducing OCR or external table extraction. Keep the flow as `PDF text -> clean -> detect sections -> structured chunks -> MySQL/Qdrant/profile`, but add stricter cleaning, heading scoring, content-type detection, summary quality gating, and a controlled reprocess path for existing papers.

**Tech Stack:** Spring Boot, MyBatis-Plus, MySQL 8, PDFBox-based text extraction, Qdrant, JUnit 5, Mockito, AssertJ.

## Global Constraints

- Do not automatically commit; user approval is required before `git commit`.
- Update `docs/status/current.md` after each substantive project stage.
- Backend compile/test commands must set `JAVA_HOME=/path/to/jdk-21`.
- Do not introduce OCR, multimodal image understanding, Camelot, Tabula, or other table extraction dependencies in this stage.
- Do not delete `chat_session`, `chat_message`, or `research_idea` during reprocessing.
- Do not default to reprocessing all papers; support specified paper IDs only.
- Preserve the existing `paper_reference` row and original PDF file during reprocessing.

---

## File Structure

### Existing files to modify

- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperTextCleaner.java`
  - Strengthen line-level PDF noise filtering.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperSectionDetector.java`
  - Replace permissive heading detection with heading candidate scoring and reject table/metadata/model-name lines.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/SectionType.java`
  - Add stronger section title inference rules if needed.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/StructuredChunk.java`
  - Add `chunkType` so parse can persist `text`, `table`, or `figure_caption`.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/StructuredChunkingService.java`
  - Lower chunk size, detect table/figure captions, keep table/figure from becoming headings, mark low-quality chunks.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperReferenceServiceImpl.java`
  - Persist `structuredChunk.getChunkType()` instead of hardcoded `text`; add controlled reprocess flow.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperReferenceService.java`
  - Expose a service method for reprocessing derived assets.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperController.java`
  - Add a management endpoint for specified-paper reprocessing if approved in implementation.
- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperProfileServiceImpl.java`
  - Skip low-quality/table-heavy sections and add summary parsing fallback.
- `docs/status/current.md`
  - Record each substantive stage.

### Tests to modify or create

- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperTextCleanerTest.java`
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/StructuredChunkingServiceTest.java`
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileServiceImplTest.java`
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImplTest.java`

---

## Task 1: Strengthen PDF Text Cleaning

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperTextCleaner.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperTextCleanerTest.java`
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: `PaperTextCleaner.clean(String rawText): String`.
- Produces: same method signature, with stronger filtering for PDF metadata/noise lines.

- [ ] **Step 1: Add failing tests for PDF noise filtering**

Append these tests to `PaperTextCleanerTest.java`:

```java
@Test
void cleanRemovesCommonPublisherAndMetadataLines() {
    PaperTextCleaner cleaner = new PaperTextCleaner();

    String cleaned = cleaner.clean("""
            journal homepage: www.elsevier.com/locate/apm
            Available online 6 June 2026
            0307-904X/© 2026 Published by Elsevier Inc.
            https://doi.org/10.1016/j.apm.2026.01.001
            Received 1 January 2026; Accepted 1 May 2026
            Introduction
            Wind speed forecasting is important for renewable energy systems.
            """);

    assertThat(cleaned).doesNotContain("journal homepage");
    assertThat(cleaned).doesNotContain("Available online");
    assertThat(cleaned).doesNotContain("Published by Elsevier");
    assertThat(cleaned).doesNotContain("doi.org");
    assertThat(cleaned).doesNotContain("Received 1 January");
    assertThat(cleaned).contains("Introduction");
    assertThat(cleaned).contains("Wind speed forecasting");
}

@Test
void cleanRemovesAuthorAffiliationAndEmailLinesBeforeBody() {
    PaperTextCleaner cleaner = new PaperTextCleaner();

    String cleaned = cleaner.clean("""
            a School of Computer Science, Example University, Beijing, China
            b Department of Electrical Engineering, Example Institute
            * Corresponding author.
            E-mail address: author@example.com
            Abstract
            This paper proposes a forecasting model.
            """);

    assertThat(cleaned).doesNotContain("School of Computer Science");
    assertThat(cleaned).doesNotContain("Department of Electrical Engineering");
    assertThat(cleaned).doesNotContain("Corresponding author");
    assertThat(cleaned).doesNotContain("author@example.com");
    assertThat(cleaned).contains("Abstract");
    assertThat(cleaned).contains("forecasting model");
}
```

- [ ] **Step 2: Run cleaner tests and verify failure**

Run from `backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperTextCleanerTest test
```

Expected: new tests fail because current cleaner only removes page numbers and whitespace.

- [ ] **Step 3: Implement noise line filtering**

Modify `PaperTextCleaner.java` to introduce `isNoiseLine` and filter it in the stream:

```java
@Component
public class PaperTextCleaner {

    public String clean(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String text = rawText.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("([A-Za-z])-\\n([A-Za-z])", "$1$2");

        text = Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(line -> !line.matches("^\\d{1,4}$"))
                .filter(line -> !isNoiseLine(line))
                .map(line -> line.replaceAll("[ \\t]+", " "))
                .collect(Collectors.joining("\n"));

        return text.replaceAll("\\n{3,}", "\n\n").trim();
    }

    private boolean isNoiseLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String normalized = line.trim().toLowerCase();
        if (normalized.contains("journal homepage")) {
            return true;
        }
        if (normalized.matches(".*available online\\s+\\d{1,2}\\s+[a-z]+\\s+\\d{4}.*")) {
            return true;
        }
        if (normalized.contains("doi.org") || normalized.startsWith("doi:")) {
            return true;
        }
        if (normalized.contains("©") || normalized.contains("copyright") || normalized.contains("published by")) {
            return true;
        }
        if (normalized.matches(".*\\b(received|revised|accepted)\\b.*\\d{4}.*")) {
            return true;
        }
        if (normalized.contains("corresponding author")) {
            return true;
        }
        if (normalized.contains("e-mail address") || normalized.contains("email address")) {
            return true;
        }
        if (normalized.matches(".*[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}.*")) {
            return true;
        }
        return normalized.matches("^[a-z]\\s+(school|department|college|faculty|institute|laboratory|university)\\b.*");
    }
}
```

- [ ] **Step 4: Run cleaner tests and verify pass**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperTextCleanerTest test
```

Expected: all `PaperTextCleanerTest` tests pass.

- [ ] **Step 5: Update progress document**

Append this concise entry inside `docs/status/current.md` “下一步任务” code block:

```text
PDF 数据清洗、章节识别与段落分块质量升级进入阶段 1：PaperTextCleaner 已增强常见 PDF 噪声过滤，可过滤 journal homepage、Available online、DOI、版权/出版声明、收稿日期、通讯作者、邮箱和首页作者单位行，减少页眉页脚、期刊信息和作者单位进入后续 section/chunk。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperTextCleanerTest test。
```

---

## Task 2: Improve Section Heading Detection

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperSectionDetector.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/SectionType.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: cleaned text from `PaperTextCleaner.clean()`.
- Produces: `List<DetectedSection>` with fewer false headings and more stable section types.

- [ ] **Step 1: Add failing tests for false heading rejection and subsection detection**

Append to `PaperSectionDetectorTest.java`:

```java
@Test
void detectDoesNotTreatPublisherTableOrModelLinesAsHeadings() {
    PaperSectionDetector detector = new PaperSectionDetector();

    List<DetectedSection> sections = detector.detect("""
            Abstract
            This paper studies wind prediction and proposes a hybrid model.
            Applied Mathematical Modelling
            Table 1
            Method Category Model Advantages Disadvantages
            ST-LSTM
            WeatherGCNet
            1 Introduction
            Wind forecasting is important.
            """);

    assertThat(sections).extracting(DetectedSection::getTitle)
            .containsExactly("Abstract", "Introduction");
    assertThat(sections.get(0).getContent()).contains("Applied Mathematical Modelling");
    assertThat(sections.get(0).getContent()).contains("Table 1");
    assertThat(sections.get(0).getContent()).contains("WeatherGCNet");
}

@Test
void detectRecognizesNumberedSubsectionsAndInfersParentType() {
    PaperSectionDetector detector = new PaperSectionDetector();

    List<DetectedSection> sections = detector.detect("""
            3 Methodology
            This section introduces the proposed model.
            3.1 Feature Refinement
            We decompose wind speed signals and refine time-frequency features.
            3.2 Proposed Model Architecture
            The model combines graph convolution and temporal attention.
            4 Experiments
            We evaluate on two datasets.
            """);

    assertThat(sections).extracting(DetectedSection::getTitle)
            .containsExactly("Methodology", "Feature Refinement", "Proposed Model Architecture", "Experiments");
    assertThat(sections).extracting(DetectedSection::getType)
            .containsExactly(SectionType.METHOD, SectionType.METHOD, SectionType.METHOD, SectionType.EXPERIMENT);
}
```

- [ ] **Step 2: Run detector tests and verify failure**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest test
```

Expected: false heading rejection test fails with current permissive Title Case heading detection.

- [ ] **Step 3: Implement heading candidate rejection and subsection support**

Modify `PaperSectionDetector.java`:

1. Replace `looksLikeHeading` body with a scoring/rejection flow:

```java
private boolean looksLikeHeading(String line) {
    if (line == null || line.isBlank()) {
        return false;
    }
    if (line.length() > 120) {
        return false;
    }
    if (line.endsWith(".")) {
        return false;
    }
    if (isRejectedHeadingCandidate(line)) {
        return false;
    }
    SectionType type = SectionType.fromTitle(line);
    return type != SectionType.UNKNOWN || looksLikeNumberedShortHeading(line) || looksLikeShortTitleCaseHeading(line);
}
```

2. Add explicit reject rules:

```java
private boolean isRejectedHeadingCandidate(String line) {
    String normalized = line.trim().toLowerCase();
    if (normalized.matches("^table\\s+\\d+.*") || normalized.matches("^tab\\.\\s*\\d+.*")) {
        return true;
    }
    if (normalized.matches("^fig\\.?\\s*\\d+.*") || normalized.matches("^figure\\s+\\d+.*")) {
        return true;
    }
    if (normalized.contains("journal") || normalized.contains("available online")) {
        return true;
    }
    if (normalized.matches(".*\\b(mae|rmse|mape|accuracy|precision|recall|f1|bleu|rouge)\\b.*") && digitRatio(normalized) > 0.15) {
        return true;
    }
    if (digitRatio(normalized) > 0.35) {
        return true;
    }
    if (line.matches("^[A-Z][A-Za-z0-9-]{1,25}$") && !SectionType.fromTitle(line).equals(SectionType.UNKNOWN)) {
        return false;
    }
    return line.matches("^[A-Z][A-Za-z0-9-]{1,25}$");
}

private double digitRatio(String value) {
    if (value == null || value.isBlank()) {
        return 0.0;
    }
    long digits = value.chars().filter(Character::isDigit).count();
    return digits * 1.0 / value.length();
}
```

3. Make numbered heading support decimals:

```java
private boolean looksLikeNumberedShortHeading(String line) {
    return line.matches("^(\\d+(?:\\.\\d+)*|[0-9IVXLCDMivxlcdm]+)[.)、]?\\s+[A-Z][A-Za-z0-9 ,&()/-]{2,90}$");
}
```

4. Make Title Case fallback stricter:

```java
private boolean looksLikeShortTitleCaseHeading(String line) {
    SectionType type = SectionType.fromTitle(line);
    if (type != SectionType.UNKNOWN) {
        return true;
    }
    return line.matches("^[A-Z][A-Za-z0-9 ,&()/-]{2,60}$")
            && line.split("\\s+").length <= 4
            && containsHeadingSignal(line);
}

private boolean containsHeadingSignal(String line) {
    String normalized = line.toLowerCase();
    return normalized.contains("method")
            || normalized.contains("experiment")
            || normalized.contains("result")
            || normalized.contains("discussion")
            || normalized.contains("conclusion")
            || normalized.contains("evaluation")
            || normalized.contains("analysis")
            || normalized.contains("dataset")
            || normalized.contains("architecture")
            || normalized.contains("framework");
}
```

5. Keep `normalizeHeadingTitle` decimal-aware:

```java
private String normalizeHeadingTitle(String line) {
    return line.replaceAll("^\\s*(\\d+(?:\\.\\d+)*|[IVXLCDMivxlcdm]+)[.)、:-]?\\s+", "").trim();
}
```

- [ ] **Step 4: Strengthen `SectionType.fromTitle` subsection inference**

In `SectionType.java`, ensure `methodology`, `feature refinement`, `model architecture`, `dataset`, and `metrics` map sensibly:

```java
if (normalized.contains("method")
        || normalized.contains("methodology")
        || normalized.contains("approach")
        || normalized.contains("framework")
        || normalized.contains("architecture")
        || normalized.contains("feature refinement")
        || normalized.contains("model design")
        || normalized.contains("system design")
        || normalized.contains("proposed")) {
    return METHOD;
}
if (normalized.contains("experiment")
        || normalized.contains("evaluation")
        || normalized.contains("empirical study")
        || normalized.contains("case study")
        || normalized.contains("dataset")
        || normalized.contains("metric")
        || normalized.contains("ablation")
        || normalized.contains("implementation details")) {
    return EXPERIMENT;
}
```

- [ ] **Step 5: Run detector tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest test
```

Expected: all detector tests pass.

- [ ] **Step 6: Update progress document**

Append:

```text
PDF 数据清洗、章节识别与段落分块质量升级阶段 2 已完成：PaperSectionDetector 已收紧标题候选规则，避免 Table/Figure、期刊信息、数值密集表格行、单个模型名和正文半句误判为 section；支持 3.1/4.2 等小章节标题，并增强 Method/Experiment/Result 等 sectionType 推断。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest test。
```

---

## Task 3: Improve Chunk Granularity and Content Type Marking

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/StructuredChunk.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/StructuredChunkingService.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperReferenceServiceImpl.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/StructuredChunkingServiceTest.java`
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: improved `DetectedSection` output.
- Produces: `StructuredChunk.getChunkType()` with values `text`, `table`, or `figure_caption`; smaller paragraph-oriented chunks.

- [ ] **Step 1: Add failing tests for smaller chunks and content type detection**

Append to `StructuredChunkingServiceTest.java`:

```java
@Test
void chunkUsesSmallerParagraphOrientedChunks() {
    StructuredChunkingService service = new StructuredChunkingService(
            new PaperTextCleaner(),
            new PaperSectionDetector()
    );

    String paragraph = "This paragraph explains the proposed model architecture and training objective for wind forecasting. ".repeat(14);
    List<StructuredChunk> chunks = service.chunk("Paper", "Method\n" + paragraph + "\n\n" + paragraph);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getCharCount()).isLessThanOrEqualTo(1700));
}

@Test
void chunkMarksTableAndFigureCaptionContentTypes() {
    StructuredChunkingService service = new StructuredChunkingService(
            new PaperTextCleaner(),
            new PaperSectionDetector()
    );

    List<StructuredChunk> chunks = service.chunk("Paper", """
            Experiments
            Table 1 MAE RMSE MAPE Accuracy F1 0.91 0.82 0.77 0.95 0.93
            Fig. 2. Overall architecture of the proposed model.
            The proposed model outperforms baseline systems on two datasets.
            """);

    assertThat(chunks).anySatisfy(chunk -> {
        assertThat(chunk.getChunkType()).isEqualTo("table");
        assertThat(chunk.getNoise()).isFalse();
    });
    assertThat(chunks).anySatisfy(chunk -> assertThat(chunk.getChunkType()).isEqualTo("figure_caption"));
}
```

- [ ] **Step 2: Run chunking tests and verify failure**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=StructuredChunkingServiceTest test
```

Expected: compilation fails because `StructuredChunk.getChunkType()` does not exist.

- [ ] **Step 3: Add chunkType to StructuredChunk**

Modify `StructuredChunk.java`:

```java
@Data
@AllArgsConstructor
public class StructuredChunk {
    private String sectionTitle;
    private SectionType sectionType;
    private Integer sectionIndex;
    private String chunkType;
    private String content;
    private String indexText;
    private Integer chunkIndex;
    private Integer tokenCount;
    private Integer charCount;
    private Boolean reference;
    private Boolean noise;
    private Double qualityScore;
    private String chunkStrategyVersion;
}
```

- [ ] **Step 4: Lower chunk size and classify content type**

Modify constants in `StructuredChunkingService.java`:

```java
private static final int TARGET_CHARS = 1100;
private static final int MAX_CHARS = 1600;
private static final int OVERLAP_CHARS = 120;
```

In chunk creation, compute chunk type:

```java
String chunkType = detectChunkType(content);
boolean noise = isNoise(content) || section.isLowValueBackMatter();
if ("table".equals(chunkType) || "figure_caption".equals(chunkType)) {
    noise = false;
}

chunks.add(new StructuredChunk(
        section.getTitle(),
        section.getType(),
        section.getSectionIndex(),
        chunkType,
        content,
        buildIndexText(paperTitle, section, content),
        chunks.size(),
        estimateTokens(content),
        content.length(),
        section.isReference(),
        noise,
        qualityScore(content, noise),
        STRATEGY_VERSION
));
```

Add helper:

```java
private String detectChunkType(String content) {
    String normalized = content == null ? "" : content.trim();
    String lower = normalized.toLowerCase();
    if (lower.matches("(?s)^(table|tab\\.)\\s*\\d+.*") || looksLikeTableText(lower)) {
        return "table";
    }
    if (lower.matches("(?s)^(fig\\.?|figure)\\s*\\d+.*")) {
        return "figure_caption";
    }
    return "text";
}

private boolean looksLikeTableText(String lower) {
    if (lower == null || lower.isBlank()) {
        return false;
    }
    boolean hasMetric = lower.matches(".*\\b(mae|rmse|mape|accuracy|precision|recall|f1|bleu|rouge)\\b.*");
    long digits = lower.chars().filter(Character::isDigit).count();
    double digitRatio = digits * 1.0 / lower.length();
    return hasMetric && digitRatio > 0.12;
}
```

- [ ] **Step 5: Persist chunkType in parse flow**

Modify `PaperReferenceServiceImpl.parsePaper` chunk insertion:

```java
chunk.setChunkType(structuredChunk.getChunkType());
```

Replace the current hardcoded:

```java
chunk.setChunkType("text");
```

- [ ] **Step 6: Run chunking tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=StructuredChunkingServiceTest test
```

Expected: all structured chunking tests pass.

- [ ] **Step 7: Update progress document**

Append:

```text
PDF 数据清洗、章节识别与段落分块质量升级阶段 3 已完成：StructuredChunkingService 已调整为更小的段落优先 chunk 粒度，目标约 1100 字符、最大 1600 字符、重叠 120 字符；StructuredChunk 新增 chunkType，并可初步标记 table 与 figure_caption，解析入库时写入 paper_chunk.chunk_type，避免表格和图注被当作普通正文处理。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=StructuredChunkingServiceTest test。
```

---

## Task 4: Improve Section Summary Quality and Fallback Parsing

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperProfileServiceImpl.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileServiceImplTest.java`
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: `paper_chunk.chunk_type`, `is_reference`, `is_noise`, `section_type`, `content`.
- Produces: fewer useless `paper_section_summary` rows and fallback summary parsing when LLM output is useful but not in exact field format.

- [ ] **Step 1: Add failing tests for summary fallback and table skipping**

Append to `PaperProfileServiceImplTest.java`:

```java
@Test
void generateProfileUsesRawLlmTextAsSummaryFallbackWhenFieldMissing() {
    PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
    PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
    PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
    PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
    PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
    LlmService llmService = mock(LlmService.class);

    when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
    when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(section(10L, 7L, "METHOD", "Method", 0)));
    PaperChunk methodChunk = chunk(1L, 7L, 10L, "METHOD", "Method", 0, "The proposed model combines graph convolution and temporal attention for wind prediction.", false, false, 20);
    methodChunk.setChunkType("text");
    when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(methodChunk));
    when(sectionSummaryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    when(llmService.generateAnswer(any(String.class)))
            .thenReturn("该章节介绍了一种结合图卷积和时间注意力的风速预测模型。关键点包括空间依赖建模和时间动态捕获。")
            .thenReturn("研究问题：研究问题\n方法概述：方法\n实验与评估：实验\n主要贡献：贡献\n局限性：局限\n关键词：关键词\n完整画像：画像");

    PaperProfileServiceImpl service = new PaperProfileServiceImpl(
            paperReferenceMapper,
            paperSectionMapper,
            paperChunkMapper,
            sectionSummaryMapper,
            paperProfileMapper,
            llmService
    );

    PaperProfileResult result = service.generateProfile(7L);

    assertThat(result.getSectionSummaries()).hasSize(1);
    assertThat(result.getSectionSummaries().get(0).getSummary()).contains("图卷积");
    assertThat(result.getSectionSummaries().get(0).getSummary()).doesNotContain("信息不足");
}

@Test
void generateProfileSkipsTableOnlySectionsForSectionSummary() {
    PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
    PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
    PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
    PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
    PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
    LlmService llmService = mock(LlmService.class);

    when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
    when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(section(10L, 7L, "RESULT", "Table 1", 0)));
    PaperChunk tableChunk = chunk(1L, 7L, 10L, "RESULT", "Table 1", 0, "Table 1 MAE RMSE MAPE Accuracy 0.91 0.82 0.77", false, false, 20);
    tableChunk.setChunkType("table");
    when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(tableChunk));
    when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    when(llmService.generateAnswer(any(String.class)))
            .thenReturn("研究问题：研究问题\n方法概述：方法\n实验与评估：实验\n主要贡献：贡献\n局限性：局限\n关键词：关键词\n完整画像：画像");

    PaperProfileServiceImpl service = new PaperProfileServiceImpl(
            paperReferenceMapper,
            paperSectionMapper,
            paperChunkMapper,
            sectionSummaryMapper,
            paperProfileMapper,
            llmService
    );

    PaperProfileResult result = service.generateProfile(7L);

    assertThat(result.getSectionSummaries()).isEmpty();
    verify(sectionSummaryMapper, never()).insert(any(PaperSectionSummary.class));
}
```

- [ ] **Step 2: Run profile tests and verify failure**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileServiceImplTest test
```

Expected: fallback test fails because summary extraction returns “信息不足”; table-only test may fail because table chunks are still summarized.

- [ ] **Step 3: Skip low-quality chunks/sections for summaries**

In `PaperProfileServiceImpl`, update summary generation loop to skip groups not suitable for section summaries:

```java
for (Map.Entry<Long, List<PaperChunk>> entry : chunksBySection.entrySet()) {
    List<PaperChunk> summaryChunks = entry.getValue().stream()
            .filter(this::isSummarizableChunk)
            .toList();
    if (summaryChunks.isEmpty()) {
        continue;
    }
    PaperSection section = sectionsById.get(entry.getKey());
    PaperSectionSummary summary = generateAndUpsertSectionSummary(paper, section, entry.getKey(), summaryChunks);
    savedSummaries.add(summary);
    processedSections++;
    progressListener.onSectionProgress(processedSections, totalSections);
}
```

Add helper:

```java
private boolean isSummarizableChunk(PaperChunk chunk) {
    if (!isUsableChunk(chunk)) {
        return false;
    }
    String chunkType = chunk.getChunkType();
    if ("table".equals(chunkType) || "figure_caption".equals(chunkType)) {
        return false;
    }
    return nullToDefault(chunk.getContent(), "").length() >= 120;
}
```

- [ ] **Step 4: Improve section summary prompt**

In `buildSectionSummaryPrompt`, replace requirements block with:

```java
return "你是论文阅读助手。请根据下面某篇论文的一个章节或小章节内容，生成章节摘要。\n\n"
        + "要求：\n"
        + "1. 用中文。\n"
        + "2. 如果内容包含有效方法、实验、结果、讨论或结论，请正常总结。\n"
        + "3. 只有当内容完全是页眉、版权、作者单位、参考文献、无上下文表格数字时，才说明信息不足。\n"
        + "4. 摘要控制在 150~300 字。\n"
        + "5. 提取 3~6 条关键点。\n"
        + "6. 不要编造章节中没有的信息。\n\n"
        + "输出格式：\n"
        + "摘要：...\n"
        + "关键点：\n- ...\n\n"
        + "论文标题：" + nullToDefault(paper.getTitle(), "") + "\n"
        + "章节类型：" + nullToDefault(sectionType, "UNKNOWN") + "\n"
        + "章节标题：" + nullToDefault(sectionTitle, "") + "\n"
        + "章节正文：\n" + content;
```

- [ ] **Step 5: Add summary parse fallback**

Modify `generateAndUpsertSectionSummary`:

```java
String summaryText = extractBetween(llmText, "摘要：", "关键点：");
String keyPointsText = extractAfter(llmText, "关键点：");
if ("信息不足".equals(summaryText) && hasUsefulLlmText(llmText)) {
    summaryText = fallbackSummary(llmText);
}
if (keyPointsText.isBlank() && hasUsefulLlmText(llmText)) {
    keyPointsText = fallbackKeyPoints(llmText);
}
summary.setSummary(summaryText);
summary.setKeyPoints(keyPointsText);
```

Add helpers:

```java
private boolean hasUsefulLlmText(String text) {
    if (text == null || text.isBlank()) {
        return false;
    }
    String normalized = text.replaceAll("\\s+", " ").trim();
    if (normalized.length() < 20) {
        return false;
    }
    return !normalized.matches(".*(信息不足|无法总结|无法提供).*") || normalized.length() > 80;
}

private String fallbackSummary(String text) {
    String normalized = text.replaceAll("(?m)^\\s*[-*]\\s*", "")
            .replaceAll("\\s+", " ")
            .trim();
    if (normalized.length() <= 300) {
        return normalized;
    }
    return normalized.substring(0, 300);
}

private String fallbackKeyPoints(String text) {
    String normalized = text == null ? "" : text.trim();
    List<String> points = normalized.lines()
            .map(String::trim)
            .filter(line -> line.startsWith("-") || line.startsWith("•") || line.matches("^\\d+[.)、].*"))
            .limit(6)
            .collect(Collectors.joining("\n"));
    return points.isBlank() ? "" : points;
}
```

`Collectors` is already imported in `PaperProfileServiceImpl`.

- [ ] **Step 6: Run profile service tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileServiceImplTest test
```

Expected: all profile tests pass.

- [ ] **Step 7: Update progress document**

Append:

```text
PDF 数据清洗、章节识别与段落分块质量升级阶段 4 已完成：PaperProfileService 生成章节摘要时会跳过 reference/noise/table/figure_caption/过短低质量 chunk，章节摘要 prompt 已改为适配小章节和段落集合，并增加 LLM 输出解析兜底，避免有效输出因格式不匹配被误写为“信息不足”。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileServiceImplTest test。
```

---

## Task 5: Add Controlled Paper Reprocess Flow and Verification

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperReferenceService.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperReferenceServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperController.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImplTest.java`
- Modify: `docs/status/current.md`

**Interfaces:**
- Produces: `PaperReferenceService.reprocessPaperAssets(Long paperId): Map<String, Object>`.
- Produces: optional endpoint `POST /api/papers/{id}/reprocess-assets` for Apifox/manual reprocessing.

- [ ] **Step 1: Add failing service test for reprocess cleanup**

Add to `PaperReferenceServiceImplTest.java`:

```java
@Test
void reprocessPaperAssetsCleansDerivedAssetsAndKeepsPaperReference() {
    PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
    PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
    PdfParseService pdfParseService = mock(PdfParseService.class);
    QdrantService qdrantService = mock(QdrantService.class);
    PaperCategoryService categoryService = mock(PaperCategoryService.class);
    PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
    PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
    PaperProfileMapper profileMapper = mock(PaperProfileMapper.class);
    PaperProfileJobMapper profileJobMapper = mock(PaperProfileJobMapper.class);

    PaperReference paper = new PaperReference();
    paper.setId(7L);
    paper.setTitle("Wind Paper");
    paper.setFilePath("paper.pdf");
    when(paperReferenceMapper.selectById(7L)).thenReturn(paper);
    when(qdrantService.deletePaperPoints(7L)).thenReturn(Map.of("success", true));
    when(pdfParseService.parseText("paper.pdf")).thenReturn("Method\nThis paper proposes a model for wind prediction.");

    PaperReferenceServiceImpl service = new PaperReferenceServiceImpl(
            paperReferenceMapper,
            paperChunkMapper,
            pdfParseService,
            qdrantService,
            categoryService,
            paperSectionMapper,
            new StructuredChunkingService(new PaperTextCleaner(), new PaperSectionDetector()),
            sectionSummaryMapper,
            profileMapper,
            profileJobMapper
    );

    Map<String, Object> result = service.reprocessPaperAssets(7L);

    assertThat(result.get("success")).isEqualTo(true);
    verify(qdrantService).deletePaperPoints(7L);
    verify(profileJobMapper).delete(any(QueryWrapper.class));
    verify(profileMapper).delete(any(QueryWrapper.class));
    verify(sectionSummaryMapper).delete(any(QueryWrapper.class));
    verify(paperChunkMapper, atLeastOnce()).delete(any(QueryWrapper.class));
    verify(paperSectionMapper, atLeastOnce()).delete(any(QueryWrapper.class));
    verify(paperReferenceMapper, never()).deleteById(7L);
}
```

Ensure imports exist:

```java
import static org.mockito.Mockito.atLeastOnce;
```

- [ ] **Step 2: Run test and verify missing method failure**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperReferenceServiceImplTest test
```

Expected: compilation fails because `reprocessPaperAssets` does not exist.

- [ ] **Step 3: Add service method signature**

Modify `PaperReferenceService.java`:

```java
/**
 * 清理并重建指定文献的解析派生资产。
 *
 * 保留 paper_reference 和本地 PDF，不删除对话历史和 Research Idea。
 */
Map<String, Object> reprocessPaperAssets(Long paperId);
```

- [ ] **Step 4: Implement controlled reprocess method**

In `PaperReferenceServiceImpl`, add:

```java
@Override
@Transactional
public Map<String, Object> reprocessPaperAssets(Long paperId) {
    PaperReference paper = paperReferenceMapper.selectById(paperId);
    if (paper == null) {
        throw new RuntimeException("文献不存在");
    }
    if (paper.getFilePath() == null || paper.getFilePath().isBlank()) {
        throw new RuntimeException("文献文件路径为空");
    }

    Map<String, Object> qdrantDeleteResult = qdrantService.deletePaperPoints(paperId);
    if (!Boolean.TRUE.equals(qdrantDeleteResult.get("success"))) {
        throw new RuntimeException("删除 Qdrant 向量失败：" + qdrantDeleteResult.getOrDefault("error", "未知错误"));
    }

    paperProfileJobMapper.delete(new QueryWrapper<PaperProfileJob>().eq("paper_id", paperId));
    paperProfileMapper.delete(new QueryWrapper<PaperProfile>().eq("paper_id", paperId));
    paperSectionSummaryMapper.delete(new QueryWrapper<PaperSectionSummary>().eq("paper_id", paperId));
    paperChunkMapper.delete(new QueryWrapper<PaperChunk>().eq("paper_id", paperId));
    paperSectionMapper.delete(new QueryWrapper<PaperSection>().eq("paper_id", paperId));

    paper.setParseStatus("PENDING");
    paper.setVectorStatus("PENDING");
    paperReferenceMapper.updateById(paper);

    int chunkCount = parsePaper(paperId);
    Map<String, Object> vectorizeResult = vectorizePaper(paperId);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("paperId", paperId);
    result.put("chunkCount", chunkCount);
    result.put("vectorizeResult", vectorizeResult);
    return result;
}
```

- [ ] **Step 5: Add controller endpoint**

In `PaperController.java`, add:

```java
/**
 * 重建指定文献的解析派生资产。
 *
 * 访问示例：
 * POST /api/papers/7/reprocess-assets
 */
@PostMapping("/api/papers/{id}/reprocess-assets")
public Result<Map<String, Object>> reprocessPaperAssets(@PathVariable Long id) {
    return Result.success(paperReferenceService.reprocessPaperAssets(id));
}
```

- [ ] **Step 6: Run reprocess service tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperReferenceServiceImplTest test
```

Expected: tests pass.

- [ ] **Step 7: Run focused structure/profile tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperTextCleanerTest,PaperSectionDetectorTest,StructuredChunkingServiceTest,PaperProfileServiceImplTest,PaperReferenceServiceImplTest test
```

Expected: all selected tests pass.

- [ ] **Step 8: Update progress document**

Append:

```text
PDF 数据清洗、章节识别与段落分块质量升级阶段 5 已完成：新增指定论文重建解析派生资产能力，可保留 paper_reference 和原始 PDF，清理 Qdrant 向量、paper_profile_job、paper_profile、paper_section_summary、paper_chunk 和 paper_section 后重新解析并向量化；该流程不删除 chat_session、chat_message 和 research_idea。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperTextCleanerTest,PaperSectionDetectorTest,StructuredChunkingServiceTest,PaperProfileServiceImplTest,PaperReferenceServiceImplTest test。
```

---

## Task 6: Rebuild One Existing Paper and Record Database Quality Evidence

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: endpoint `POST /api/papers/{id}/reprocess-assets` or service via tests/manual call.
- Produces: database evidence showing improved `paper_section` and `paper_section_summary` quality.

- [ ] **Step 1: Run backend full tests before manual rebuild**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

Expected: all backend tests pass.

- [ ] **Step 2: Ask user before reprocessing real local paper data**

Before executing any local reprocess against real MySQL/Qdrant data, ask the user which paper ID to rebuild. Recommend one test paper, for example:

```text
建议先重建 1 篇测试文献，例如 paperId=9。该操作会删除该论文旧 section/chunk/summary/profile/job 和 Qdrant 向量，再重新解析和向量化，但不会删除原始 PDF、对话历史和 Research Idea。是否重建 paperId=9？
```

Do not proceed without explicit confirmation.

- [ ] **Step 3: Reprocess confirmed paper**

After confirmation, run through Apifox or curl/PowerShell. Example bash command if backend is running:

```bash
curl -X POST http://localhost:8080/api/papers/9/reprocess-assets
```

Expected response contains:

```json
{
  "code": 200,
  "data": {
    "success": true,
    "paperId": 9,
    "chunkCount": 1
  }
}
```

`chunkCount` is variable; verify success, not exact count.

- [ ] **Step 4: Regenerate profile for the rebuilt paper**

Use existing async endpoint:

```bash
curl -X POST http://localhost:8080/api/papers/9/profile/async
```

Then poll:

```bash
curl http://localhost:8080/api/papers/9/profile/job
```

Expected: status eventually becomes `COMPLETED`.

- [ ] **Step 5: Query database quality metrics**

Run:

```bash
mysql --default-character-set=utf8mb4 -uroot -p123456 research_assistant -e "SELECT paper_id, section_type, COUNT(*) AS section_count FROM paper_section WHERE paper_id = 9 GROUP BY paper_id, section_type ORDER BY section_count DESC; SELECT paper_id, COUNT(*) AS total, SUM(summary LIKE '%信息不足%') AS info_insufficient FROM paper_section_summary WHERE paper_id = 9 GROUP BY paper_id; SELECT id, paper_id, section_type, LEFT(section_title, 120) AS section_title FROM paper_section WHERE paper_id = 9 ORDER BY section_index LIMIT 30;"
```

Expected:

```text
section titles no longer dominated by journal names, table rows, model names, or half sentences.
section_summary info_insufficient count is much lower than total.
```

- [ ] **Step 6: Run final frontend/backend verification**

Run backend:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

Run frontend if paper page API/UI changed in this stage:

```bash
cd ../../frontend/research-assistant-frontend
node --test src/api/papers.test.js src/views/paperWorkflowState.test.js src/api/paperCategories.test.js src/api/rag.test.js src/views/ragChatState.test.js
npm run build
```

Expected: all tests/build pass.

- [ ] **Step 7: Record final progress**

Append final entry to `docs/status/current.md`:

```text
PDF 数据清洗、章节识别与段落分块质量升级已完成阶段性实现：文本清洗已过滤常见 PDF 元信息和作者单位噪声，章节识别改为更严格的标题候选规则并支持小章节，分块调整为更小的段落优先 chunk 并标记 table/figure_caption，章节摘要生成会跳过低质量内容并增加解析兜底，指定论文重建流程可清理旧 section/chunk/summary/profile/job 和 Qdrant 向量后重新解析与向量化。最终验证：后端 ./mvnw test 通过；已按用户确认重建 paperId=<实际ID> 并记录 section/summary 数据质量对比；前端相关测试和 npm run build 按需通过。
```

---

## Self-Review Notes

- Spec coverage: Tasks cover text cleaning, section detection, chunk granularity, table/figure marking, summary fallback, controlled reprocessing, verification, and progress documentation.
- Placeholder scan: No unfinished placeholder markers or open-ended implementation placeholders remain in this plan.
- Type consistency: `StructuredChunk.chunkType` is introduced in Task 3 and consumed by `PaperReferenceServiceImpl` and `PaperProfileServiceImpl`; `reprocessPaperAssets(Long)` is introduced in Task 5 and used by the controller.
