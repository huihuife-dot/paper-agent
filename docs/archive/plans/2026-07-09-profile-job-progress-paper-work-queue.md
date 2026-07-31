# 文献画像任务进度与文献处理队列 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a clear paper processing workflow where newly uploaded papers stay in a待处理队列 until parse, vectorize, and profile generation are complete, with visible profile job progress and retry support.

**Architecture:** Keep parse and vectorize synchronous for this stage, and enhance only the slower paper-profile generation path as an observable background job. Add a lightweight batch profile-status endpoint for the paper page, then refactor the Vue paper page into two lists: pending work queue and ready library.

**Tech Stack:** Spring Boot, MyBatis-Plus, MySQL 8, Vue 3, Vite 5, Element Plus, Node.js built-in test runner.

## Global Constraints

- Do not automatically commit; user approval is required before `git commit`.
- Update `docs/status/current.md` after each substantive project stage.
- Backend compile/test commands must set `JAVA_HOME=/path/to/jdk-21`.
- Keep parse and vectorize as existing synchronous actions in this stage.
- Use polling, not WebSocket or SSE, for profile progress.
- A paper is ready only when parse, vectorize, and paper profile are all complete.
- The profile start/retry endpoint remains `POST /api/papers/{id}/profile/async`.

---

## File Structure

### Backend files

- Modify: `docs/database/schema.sql`
  - Add profile job progress columns to the full schema.
- Modify: `docs/database/migrations/paper-profile-job.sql`
  - Add idempotent `ALTER TABLE` statements for existing databases.
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperProfileJob.java`
  - Add progress fields.
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperProfileJobResponse.java`
  - Expose progress fields to frontend.
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperProfileStatusResponse.java`
  - Batch page-level status for each paper.
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperProfileProgressListener.java`
  - Decouple profile generation from job persistence.
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperProfileService.java`
  - Add progress-listener overload.
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperProfileServiceImpl.java`
  - Emit progress events while generating summaries/profile.
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperProfileJobServiceImpl.java`
  - Track steps, percent, retry count, success and failure progress.
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperProfileJobService.java`
  - Keep start/query signatures and add batch status if this service owns status.
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperProfileStatusService.java`
  - Read-only batch profile status contract.
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperProfileStatusServiceImpl.java`
  - Build batch statuses from papers, profiles, summaries, and latest jobs.
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperController.java`
  - Add `GET /api/papers/profile-status` and inject status service.
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileJobServiceImplTest.java`
  - Cover progress, completion, failure, retry.
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileServiceImplTest.java`
  - Cover progress listener callbacks.
- Create: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileStatusServiceImplTest.java`
  - Cover batch status assembly.

### Frontend files

- Modify: `frontend/research-assistant-frontend/src/api/papers.js`
  - Add `listPaperProfileStatuses()`.
- Modify: `frontend/research-assistant-frontend/src/api/papers.test.js`
  - Test the new API helper.
- Create: `frontend/research-assistant-frontend/src/views/paperWorkflowState.js`
  - Pure state calculations for pending/ready lists and main action labels.
- Create: `frontend/research-assistant-frontend/src/views/paperWorkflowState.test.js`
  - Test workflow state calculation.
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`
  - Render two lists, progress, main buttons, and more menus.
- Modify: `docs/status/current.md`
  - Record substantive stage completions.

---

## Task 1: Backend Profile Job Progress and Retry

**Files:**
- Modify: `docs/database/schema.sql:152-168`
- Modify: `docs/database/migrations/paper-profile-job.sql:6-21`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/entity/PaperProfileJob.java:20-35`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperProfileJobResponse.java:12-19`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperProfileProgressListener.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperProfileService.java:10-18`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperProfileServiceImpl.java:63-87`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperProfileJobServiceImpl.java:22-115`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileJobServiceImplTest.java`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileServiceImplTest.java`

**Interfaces:**
- Consumes: existing `PaperProfileService.generateProfile(Long paperId)` and `PaperProfileJobMapper`.
- Produces:
  - `PaperProfileProgressListener` callbacks: `onPreparing()`, `onSectionProgress(int processedSections, int totalSections)`, `onGeneratingProfile()`, `onSavingResult()`.
  - `PaperProfileService.generateProfile(Long paperId, PaperProfileProgressListener listener)`.
  - Enhanced `PaperProfileJobResponse` fields: `currentStep`, `progressPercent`, `processedSections`, `totalSections`, `retryCount`.

- [ ] **Step 1: Write failing tests for job progress and retry**

Add these test methods to `PaperProfileJobServiceImplTest`:

```java
@Test
void startProfileJobInitializesProgressFieldsAndPassesProgressListener() {
    PaperProfileJobMapper jobMapper = mock(PaperProfileJobMapper.class);
    PaperProfileService profileService = mock(PaperProfileService.class);
    when(jobMapper.selectOne(any(Wrapper.class))).thenReturn(null);

    List<Runnable> tasks = new ArrayList<>();
    PaperProfileJobServiceImpl service = new PaperProfileJobServiceImpl(jobMapper, profileService, tasks::add);

    PaperProfileJobResponse response = service.startProfileJob(7L);

    assertThat(response.getPaperId()).isEqualTo(7L);
    assertThat(response.getStatus()).isEqualTo("PROCESSING");
    assertThat(response.getCurrentStep()).isEqualTo("WAITING");
    assertThat(response.getProgressPercent()).isEqualTo(0);
    assertThat(response.getProcessedSections()).isEqualTo(0);
    assertThat(response.getRetryCount()).isEqualTo(0);

    tasks.get(0).run();

    verify(profileService).generateProfile(eq(7L), any(PaperProfileProgressListener.class));
}

@Test
void startProfileJobCreatesRetryJobAfterFailure() {
    PaperProfileJobMapper jobMapper = mock(PaperProfileJobMapper.class);
    PaperProfileService profileService = mock(PaperProfileService.class);
    PaperProfileJob failed = job(3L, 7L, "FAILED", "Qwen API Key 未配置");
    failed.setRetryCount(2);
    when(jobMapper.selectOne(any(Wrapper.class)))
            .thenReturn(null)
            .thenReturn(failed);

    List<Runnable> tasks = new ArrayList<>();
    PaperProfileJobServiceImpl service = new PaperProfileJobServiceImpl(jobMapper, profileService, tasks::add);

    PaperProfileJobResponse response = service.startProfileJob(7L);

    assertThat(response.getStatus()).isEqualTo("PROCESSING");
    assertThat(response.getRetryCount()).isEqualTo(3);
    ArgumentCaptor<PaperProfileJob> jobCaptor = ArgumentCaptor.forClass(PaperProfileJob.class);
    verify(jobMapper).insert(jobCaptor.capture());
    assertThat(jobCaptor.getValue().getRetryCount()).isEqualTo(3);
}
```

Also update the existing completion and failure tests so they verify the listener overload and final progress fields:

```java
verify(profileService).generateProfile(eq(7L), any(PaperProfileProgressListener.class));
assertThat(inserted.getCurrentStep()).isEqualTo("COMPLETED");
assertThat(inserted.getProgressPercent()).isEqualTo(100);
```

```java
when(profileService.generateProfile(eq(7L), any(PaperProfileProgressListener.class)))
        .thenThrow(new RuntimeException("Qwen API Key 未配置"));
assertThat(inserted.getCurrentStep()).isEqualTo("FAILED");
assertThat(inserted.getErrorMessage()).contains("Qwen API Key 未配置");
```

- [ ] **Step 2: Write failing test for profile progress callbacks**

Add this test to `PaperProfileServiceImplTest`:

```java
@Test
void generateProfileReportsProgressForSectionsAndProfile() {
    PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
    PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
    PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
    PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
    PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
    LlmService llmService = mock(LlmService.class);

    when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
    when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            section(10L, 7L, "METHOD", "Method", 0),
            section(11L, 7L, "EXPERIMENT", "Experiment", 1)
    ));
    when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            chunk(1L, 7L, 10L, "METHOD", "Method", 0, "method content", false, false, 10),
            chunk(2L, 7L, 11L, "EXPERIMENT", "Experiment", 1, "experiment content", false, false, 10)
    ));
    when(sectionSummaryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    when(llmService.generateAnswer(any(String.class)))
            .thenReturn("摘要：方法摘要\n关键点：方法关键点")
            .thenReturn("摘要：实验摘要\n关键点：实验关键点")
            .thenReturn("研究问题：研究问题\n方法概述：方法\n实验与评估：实验\n主要贡献：贡献\n局限性：局限\n关键词：关键词\n完整画像：画像");

    PaperProfileServiceImpl service = new PaperProfileServiceImpl(
            paperReferenceMapper,
            paperSectionMapper,
            paperChunkMapper,
            sectionSummaryMapper,
            paperProfileMapper,
            llmService
    );
    List<String> events = new ArrayList<>();

    service.generateProfile(7L, new PaperProfileProgressListener() {
        @Override
        public void onPreparing() {
            events.add("preparing");
        }

        @Override
        public void onSectionProgress(int processedSections, int totalSections) {
            events.add("section:" + processedSections + "/" + totalSections);
        }

        @Override
        public void onGeneratingProfile() {
            events.add("profile");
        }

        @Override
        public void onSavingResult() {
            events.add("saving");
        }
    });

    assertThat(events).containsExactly("preparing", "section:1/2", "section:2/2", "profile", "saving");
}
```

Add imports if missing:

```java
import com.myagent.assistant.paper.service.PaperProfileProgressListener;
import java.util.ArrayList;
```

- [ ] **Step 3: Run backend tests and verify they fail for missing APIs**

Run from `backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileJobServiceImplTest,PaperProfileServiceImplTest test
```

Expected: compilation fails because `PaperProfileProgressListener`, the listener overload, and job progress fields do not exist yet.

- [ ] **Step 4: Add database columns**

Update the `paper_profile_job` create table block in `docs/database/schema.sql` to include:

```sql
    current_step VARCHAR(50) DEFAULT 'WAITING' COMMENT '当前步骤：WAITING/PREPARING/GENERATING_SECTIONS/GENERATING_PROFILE/SAVING_RESULT/COMPLETED/FAILED',
    progress_percent INT DEFAULT 0 COMMENT '进度百分比，0-100',
    processed_sections INT DEFAULT 0 COMMENT '已处理章节数',
    total_sections INT DEFAULT 0 COMMENT '总章节数',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
```

Place those columns after `status VARCHAR(50) NOT NULL ...` and before `error_message LONGTEXT ...`.

Update `docs/database/migrations/paper-profile-job.sql` after the `CREATE TABLE` statement with idempotent MySQL 8 column adds:

```sql
ALTER TABLE paper_profile_job
    ADD COLUMN IF NOT EXISTS current_step VARCHAR(50) DEFAULT 'WAITING' COMMENT '当前步骤：WAITING/PREPARING/GENERATING_SECTIONS/GENERATING_PROFILE/SAVING_RESULT/COMPLETED/FAILED' AFTER status,
    ADD COLUMN IF NOT EXISTS progress_percent INT DEFAULT 0 COMMENT '进度百分比，0-100' AFTER current_step,
    ADD COLUMN IF NOT EXISTS processed_sections INT DEFAULT 0 COMMENT '已处理章节数' AFTER progress_percent,
    ADD COLUMN IF NOT EXISTS total_sections INT DEFAULT 0 COMMENT '总章节数' AFTER processed_sections,
    ADD COLUMN IF NOT EXISTS retry_count INT DEFAULT 0 COMMENT '已重试次数' AFTER total_sections;
```

- [ ] **Step 5: Add progress fields to entity and response DTO**

Add these fields to `PaperProfileJob.java` after `status`:

```java
/**
 * WAITING / PREPARING / GENERATING_SECTIONS / GENERATING_PROFILE / SAVING_RESULT / COMPLETED / FAILED
 */
private String currentStep;

private Integer progressPercent;

private Integer processedSections;

private Integer totalSections;

private Integer retryCount;
```

Add matching fields to `PaperProfileJobResponse.java` after `status`:

```java
private String currentStep;
private Integer progressPercent;
private Integer processedSections;
private Integer totalSections;
private Integer retryCount;
```

- [ ] **Step 6: Add progress listener interface and service overload**

Create `PaperProfileProgressListener.java`:

```java
package com.myagent.assistant.paper.service;

/**
 * 文献画像生成进度监听器。
 *
 * 画像生成服务只报告进度事件，不直接依赖任务表，避免画像生成逻辑和任务持久化耦合。
 */
public interface PaperProfileProgressListener {

    PaperProfileProgressListener NOOP = new PaperProfileProgressListener() {
    };

    default void onPreparing() {
    }

    default void onSectionProgress(int processedSections, int totalSections) {
    }

    default void onGeneratingProfile() {
    }

    default void onSavingResult() {
    }
}
```

Modify `PaperProfileService.java`:

```java
package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.dto.PaperProfileResult;

/**
 * 文献画像服务。
 */
public interface PaperProfileService {

    /**
     * 为指定已解析论文生成或更新文献画像。
     */
    PaperProfileResult generateProfile(Long paperId);

    /**
     * 为指定已解析论文生成或更新文献画像，并通过 listener 汇报后台任务进度。
     */
    PaperProfileResult generateProfile(Long paperId, PaperProfileProgressListener listener);

    /**
     * 查询指定论文已生成的文献画像。
     */
    PaperProfileResult getProfile(Long paperId);
}
```

- [ ] **Step 7: Emit progress from profile generation**

Modify the top of `PaperProfileServiceImpl.generateProfile` to delegate:

```java
@Override
public PaperProfileResult generateProfile(Long paperId) {
    return generateProfile(paperId, PaperProfileProgressListener.NOOP);
}

@Override
public PaperProfileResult generateProfile(Long paperId, PaperProfileProgressListener listener) {
    PaperProfileProgressListener progressListener = listener == null ? PaperProfileProgressListener.NOOP : listener;
    progressListener.onPreparing();

    PaperReference paper = requireParsedPaper(paperId);
    List<PaperSection> sections = listSections(paperId);
    List<PaperChunk> usableChunks = listUsableChunks(paperId);

    if (usableChunks.isEmpty()) {
        throw new RuntimeException("当前论文没有可用于生成画像的正文内容");
    }

    Map<Long, PaperSection> sectionsById = sections.stream()
            .filter(section -> section.getId() != null)
            .collect(Collectors.toMap(PaperSection::getId, section -> section, (a, b) -> a, LinkedHashMap::new));
    Map<Long, List<PaperChunk>> chunksBySection = groupChunksBySection(usableChunks);
    List<PaperSectionSummary> savedSummaries = new ArrayList<>();
    int totalSections = chunksBySection.size();
    int processedSections = 0;

    for (Map.Entry<Long, List<PaperChunk>> entry : chunksBySection.entrySet()) {
        PaperSection section = sectionsById.get(entry.getKey());
        PaperSectionSummary summary = generateAndUpsertSectionSummary(paper, section, entry.getKey(), entry.getValue());
        savedSummaries.add(summary);
        processedSections++;
        progressListener.onSectionProgress(processedSections, totalSections);
    }

    progressListener.onGeneratingProfile();
    PaperProfile profile = generateAndUpsertProfile(paper, savedSummaries, usableChunks);
    progressListener.onSavingResult();
    return new PaperProfileResult(toProfileResponse(profile), savedSummaries.stream().map(this::toSummaryResponse).toList());
}
```

Add the import:

```java
import com.myagent.assistant.paper.service.PaperProfileProgressListener;
```

- [ ] **Step 8: Implement job progress and retry logic**

Modify `PaperProfileJobServiceImpl` constants:

```java
public static final String STATUS_PROCESSING = "PROCESSING";
public static final String STATUS_COMPLETED = "COMPLETED";
public static final String STATUS_FAILED = "FAILED";

public static final String STEP_WAITING = "WAITING";
public static final String STEP_PREPARING = "PREPARING";
public static final String STEP_GENERATING_SECTIONS = "GENERATING_SECTIONS";
public static final String STEP_GENERATING_PROFILE = "GENERATING_PROFILE";
public static final String STEP_SAVING_RESULT = "SAVING_RESULT";
public static final String STEP_COMPLETED = "COMPLETED";
public static final String STEP_FAILED = "FAILED";
```

In `startProfileJob`, initialize progress and retry count:

```java
PaperProfileJob latest = findLatestJob(paperId);
LocalDateTime now = LocalDateTime.now();
PaperProfileJob job = new PaperProfileJob();
job.setPaperId(paperId);
job.setStatus(STATUS_PROCESSING);
job.setCurrentStep(STEP_WAITING);
job.setProgressPercent(0);
job.setProcessedSections(0);
job.setTotalSections(0);
job.setRetryCount(latest == null ? 0 : safeInt(latest.getRetryCount()) + 1);
job.setErrorMessage(null);
job.setStartTime(now);
job.setCreateTime(now);
job.setUpdateTime(now);
jobMapper.insert(job);

executor.execute(() -> runJob(job));
return toResponse(job);
```

Add helper methods:

```java
private PaperProfileJob findLatestJob(Long paperId) {
    return jobMapper.selectOne(new QueryWrapper<PaperProfileJob>()
            .eq("paper_id", paperId)
            .orderByDesc("create_time")
            .last("LIMIT 1"));
}

private int safeInt(Integer value) {
    return value == null ? 0 : value;
}

private int sectionProgressPercent(int processedSections, int totalSections) {
    if (totalSections <= 0) {
        return 5;
    }
    int boundedProcessed = Math.max(0, Math.min(processedSections, totalSections));
    return 5 + (int) Math.floor((boundedProcessed * 75.0) / totalSections);
}

private void updateProgress(PaperProfileJob job,
                            String currentStep,
                            int progressPercent,
                            int processedSections,
                            int totalSections) {
    job.setCurrentStep(currentStep);
    job.setProgressPercent(Math.max(0, Math.min(progressPercent, 100)));
    job.setProcessedSections(Math.max(0, processedSections));
    job.setTotalSections(Math.max(0, totalSections));
    job.setUpdateTime(LocalDateTime.now());
    jobMapper.updateById(job);
}
```

Modify `runJob`:

```java
private void runJob(PaperProfileJob job) {
    try {
        profileService.generateProfile(job.getPaperId(), new PaperProfileProgressListener() {
            @Override
            public void onPreparing() {
                updateProgress(job, STEP_PREPARING, 5, 0, 0);
            }

            @Override
            public void onSectionProgress(int processedSections, int totalSections) {
                updateProgress(job, STEP_GENERATING_SECTIONS, sectionProgressPercent(processedSections, totalSections), processedSections, totalSections);
            }

            @Override
            public void onGeneratingProfile() {
                updateProgress(job, STEP_GENERATING_PROFILE, 90, safeInt(job.getProcessedSections()), safeInt(job.getTotalSections()));
            }

            @Override
            public void onSavingResult() {
                updateProgress(job, STEP_SAVING_RESULT, 98, safeInt(job.getProcessedSections()), safeInt(job.getTotalSections()));
            }
        });
        job.setStatus(STATUS_COMPLETED);
        job.setCurrentStep(STEP_COMPLETED);
        job.setProgressPercent(100);
        job.setErrorMessage(null);
    } catch (Exception e) {
        job.setStatus(STATUS_FAILED);
        job.setCurrentStep(STEP_FAILED);
        job.setErrorMessage(e.getMessage());
    } finally {
        job.setFinishTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
    }
}
```

Modify `toResponse` to copy new fields:

```java
response.setCurrentStep(job.getCurrentStep());
response.setProgressPercent(job.getProgressPercent());
response.setProcessedSections(job.getProcessedSections());
response.setTotalSections(job.getTotalSections());
response.setRetryCount(job.getRetryCount());
```

- [ ] **Step 9: Run focused backend tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileJobServiceImplTest,PaperProfileServiceImplTest test
```

Expected: all selected tests pass.

- [ ] **Step 10: Update progress document for backend stage 1**

Append one concise line under the “下一步任务” code block in `docs/status/current.md` after the latest related profile-job entry:

```text
文献画像任务进度与重试机制进入阶段 1：后端 paper_profile_job 已增强 currentStep、progressPercent、processedSections、totalSections 和 retryCount，画像生成过程中通过 PaperProfileProgressListener 持续更新准备、章节摘要、整篇画像和保存步骤；失败任务记录 FAILED 和错误原因，失败后再次启动会创建递增 retryCount 的重试任务。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileJobServiceImplTest,PaperProfileServiceImplTest test 通过。
```

---

## Task 2: Backend Batch Profile Status Endpoint

**Files:**
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/dto/PaperProfileStatusResponse.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/PaperProfileStatusService.java`
- Create: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperProfileStatusServiceImpl.java`
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/controller/PaperController.java:38-48,50-53`
- Test: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperProfileStatusServiceImplTest.java`

**Interfaces:**
- Consumes: `PaperReferenceMapper`, `PaperProfileMapper`, `PaperSectionSummaryMapper`, `PaperProfileJobMapper`.
- Produces:
  - `PaperProfileStatusService.listProfileStatuses(): List<PaperProfileStatusResponse>`.
  - `GET /api/papers/profile-status` returning `Result<List<PaperProfileStatusResponse>>`.

- [ ] **Step 1: Write failing service test**

Create `PaperProfileStatusServiceImplTest.java`:

```java
package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.paper.dto.PaperProfileStatusResponse;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperProfileJob;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperProfileJobMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperProfileStatusServiceImplTest {

    @Test
    void listProfileStatusesCombinesProfileSummaryCountAndLatestJob() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileJobMapper jobMapper = mock(PaperProfileJobMapper.class);

        when(paperReferenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(paper(7L), paper(8L)));
        when(paperProfileMapper.selectList(any(Wrapper.class))).thenReturn(List.of(profile(7L, "paper-profile-v1")));
        when(sectionSummaryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(summary(7L), summary(7L)));
        when(jobMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                job(1L, 7L, "COMPLETED", "COMPLETED", 100, 2, 2, 0, null, LocalDateTime.now().minusMinutes(1)),
                job(2L, 8L, "FAILED", "FAILED", 35, 1, 3, 2, "Qwen API Key 未配置", LocalDateTime.now())
        ));

        PaperProfileStatusServiceImpl service = new PaperProfileStatusServiceImpl(
                paperReferenceMapper,
                paperProfileMapper,
                sectionSummaryMapper,
                jobMapper
        );

        List<PaperProfileStatusResponse> statuses = service.listProfileStatuses();

        PaperProfileStatusResponse ready = statuses.stream().filter(item -> item.getPaperId().equals(7L)).findFirst().orElseThrow();
        assertThat(ready.getHasProfile()).isTrue();
        assertThat(ready.getProfileVersion()).isEqualTo("paper-profile-v1");
        assertThat(ready.getSectionSummaryCount()).isEqualTo(2);
        assertThat(ready.getJobStatus()).isEqualTo("COMPLETED");
        assertThat(ready.getProgressPercent()).isEqualTo(100);

        PaperProfileStatusResponse failed = statuses.stream().filter(item -> item.getPaperId().equals(8L)).findFirst().orElseThrow();
        assertThat(failed.getHasProfile()).isFalse();
        assertThat(failed.getJobStatus()).isEqualTo("FAILED");
        assertThat(failed.getCurrentStep()).isEqualTo("FAILED");
        assertThat(failed.getErrorMessage()).contains("Qwen API Key 未配置");
        assertThat(failed.getRetryCount()).isEqualTo(2);
    }

    private PaperReference paper(Long id) {
        PaperReference paper = new PaperReference();
        paper.setId(id);
        paper.setTitle("Paper " + id);
        return paper;
    }

    private PaperProfile profile(Long paperId, String version) {
        PaperProfile profile = new PaperProfile();
        profile.setPaperId(paperId);
        profile.setProfileVersion(version);
        return profile;
    }

    private PaperSectionSummary summary(Long paperId) {
        PaperSectionSummary summary = new PaperSectionSummary();
        summary.setPaperId(paperId);
        summary.setSummaryVersion("section-summary-v1");
        return summary;
    }

    private PaperProfileJob job(Long id,
                                Long paperId,
                                String status,
                                String currentStep,
                                Integer progressPercent,
                                Integer processedSections,
                                Integer totalSections,
                                Integer retryCount,
                                String errorMessage,
                                LocalDateTime createTime) {
        PaperProfileJob job = new PaperProfileJob();
        job.setId(id);
        job.setPaperId(paperId);
        job.setStatus(status);
        job.setCurrentStep(currentStep);
        job.setProgressPercent(progressPercent);
        job.setProcessedSections(processedSections);
        job.setTotalSections(totalSections);
        job.setRetryCount(retryCount);
        job.setErrorMessage(errorMessage);
        job.setCreateTime(createTime);
        return job;
    }
}
```

- [ ] **Step 2: Run test to verify missing classes fail**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileStatusServiceImplTest test
```

Expected: compilation fails because `PaperProfileStatusResponse`, `PaperProfileStatusService`, and implementation do not exist.

- [ ] **Step 3: Add DTO and service contract**

Create `PaperProfileStatusResponse.java`:

```java
package com.myagent.assistant.paper.dto;

import lombok.Data;

/**
 * 文献页使用的轻量画像状态。
 */
@Data
public class PaperProfileStatusResponse {
    private Long paperId;
    private Boolean hasProfile;
    private String profileVersion;
    private Integer sectionSummaryCount;
    private String jobStatus;
    private String currentStep;
    private Integer progressPercent;
    private Integer processedSections;
    private Integer totalSections;
    private Integer retryCount;
    private String errorMessage;
}
```

Create `PaperProfileStatusService.java`:

```java
package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.dto.PaperProfileStatusResponse;

import java.util.List;

/**
 * 文献画像状态查询服务。
 */
public interface PaperProfileStatusService {

    /**
     * 查询文献页所需的全部画像轻量状态。
     */
    List<PaperProfileStatusResponse> listProfileStatuses();
}
```

- [ ] **Step 4: Implement batch status service**

Create `PaperProfileStatusServiceImpl.java`:

```java
package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.paper.dto.PaperProfileStatusResponse;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperProfileJob;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperProfileJobMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.paper.service.PaperProfileStatusService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文献画像状态查询服务实现。
 */
@Service
public class PaperProfileStatusServiceImpl implements PaperProfileStatusService {

    private static final String PAPER_PROFILE_VERSION = "paper-profile-v1";
    private static final String SECTION_SUMMARY_VERSION = "section-summary-v1";

    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperProfileMapper paperProfileMapper;
    private final PaperSectionSummaryMapper sectionSummaryMapper;
    private final PaperProfileJobMapper jobMapper;

    public PaperProfileStatusServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                         PaperProfileMapper paperProfileMapper,
                                         PaperSectionSummaryMapper sectionSummaryMapper,
                                         PaperProfileJobMapper jobMapper) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperProfileMapper = paperProfileMapper;
        this.sectionSummaryMapper = sectionSummaryMapper;
        this.jobMapper = jobMapper;
    }

    @Override
    public List<PaperProfileStatusResponse> listProfileStatuses() {
        List<PaperReference> papers = paperReferenceMapper.selectList(new QueryWrapper<PaperReference>().orderByDesc("upload_time"));
        if (papers.isEmpty()) {
            return List.of();
        }

        List<Long> paperIds = papers.stream().map(PaperReference::getId).toList();
        Map<Long, PaperProfile> profilesByPaperId = paperProfileMapper.selectList(new QueryWrapper<PaperProfile>()
                        .in("paper_id", paperIds)
                        .eq("profile_version", PAPER_PROFILE_VERSION))
                .stream()
                .collect(Collectors.toMap(PaperProfile::getPaperId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, Long> summaryCountByPaperId = sectionSummaryMapper.selectList(new QueryWrapper<PaperSectionSummary>()
                        .in("paper_id", paperIds)
                        .eq("summary_version", SECTION_SUMMARY_VERSION))
                .stream()
                .collect(Collectors.groupingBy(PaperSectionSummary::getPaperId, Collectors.counting()));
        Map<Long, PaperProfileJob> latestJobByPaperId = latestJobByPaperId(paperIds);

        return papers.stream()
                .map(paper -> toResponse(paper.getId(), profilesByPaperId.get(paper.getId()), summaryCountByPaperId, latestJobByPaperId.get(paper.getId())))
                .toList();
    }

    private Map<Long, PaperProfileJob> latestJobByPaperId(List<Long> paperIds) {
        List<PaperProfileJob> jobs = jobMapper.selectList(new QueryWrapper<PaperProfileJob>()
                .in("paper_id", paperIds)
                .orderByDesc("create_time"));
        Map<Long, PaperProfileJob> latest = new LinkedHashMap<>();
        for (PaperProfileJob job : jobs) {
            latest.putIfAbsent(job.getPaperId(), job);
        }
        return latest;
    }

    private PaperProfileStatusResponse toResponse(Long paperId,
                                                  PaperProfile profile,
                                                  Map<Long, Long> summaryCountByPaperId,
                                                  PaperProfileJob job) {
        PaperProfileStatusResponse response = new PaperProfileStatusResponse();
        response.setPaperId(paperId);
        response.setHasProfile(profile != null);
        response.setProfileVersion(profile == null ? null : profile.getProfileVersion());
        response.setSectionSummaryCount(summaryCountByPaperId.getOrDefault(paperId, 0L).intValue());
        response.setJobStatus(job == null ? null : job.getStatus());
        response.setCurrentStep(job == null ? null : job.getCurrentStep());
        response.setProgressPercent(job == null || job.getProgressPercent() == null ? 0 : job.getProgressPercent());
        response.setProcessedSections(job == null || job.getProcessedSections() == null ? 0 : job.getProcessedSections());
        response.setTotalSections(job == null || job.getTotalSections() == null ? 0 : job.getTotalSections());
        response.setRetryCount(job == null || job.getRetryCount() == null ? 0 : job.getRetryCount());
        response.setErrorMessage(job == null ? null : job.getErrorMessage());
        return response;
    }
}
```

- [ ] **Step 5: Add controller endpoint**

Modify imports and constructor in `PaperController.java`:

```java
import com.myagent.assistant.paper.dto.PaperProfileStatusResponse;
import com.myagent.assistant.paper.service.PaperProfileStatusService;
```

Add field:

```java
private final PaperProfileStatusService paperProfileStatusService;
```

Modify constructor:

```java
public PaperController(PaperReferenceService paperReferenceService,
                       PaperProfileService paperProfileService,
                       PaperProfileJobService paperProfileJobService,
                       PaperProfileStatusService paperProfileStatusService) {
    this.paperReferenceService = paperReferenceService;
    this.paperProfileService = paperProfileService;
    this.paperProfileJobService = paperProfileJobService;
    this.paperProfileStatusService = paperProfileStatusService;
}
```

Add endpoint near `GET /api/papers` and before `GET /api/papers/{id}`:

```java
/**
 * 批量查询文献画像状态。
 *
 * 访问示例：
 * GET /api/papers/profile-status
 */
@GetMapping("/api/papers/profile-status")
public Result<List<PaperProfileStatusResponse>> listProfileStatuses() {
    return Result.success(paperProfileStatusService.listProfileStatuses());
}
```

- [ ] **Step 6: Run batch status tests**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileStatusServiceImplTest test
```

Expected: test passes.

- [ ] **Step 7: Run backend compile**

Run:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile
```

Expected: compile passes.

- [ ] **Step 8: Update progress document for backend stage 2**

Append one concise line to `docs/status/current.md`:

```text
文献画像任务进度与重试机制阶段 2 已完成：新增 GET /api/papers/profile-status 批量画像状态接口，可一次返回每篇文献是否已有 paper-profile-v1、章节摘要数量、最近画像任务状态、当前步骤、进度百分比、章节进度、重试次数和失败原因，前端文献页后续可避免逐篇请求画像状态。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileStatusServiceImplTest test 和 ./mvnw -DskipTests compile 通过。
```

---

## Task 3: Frontend API and Workflow State Model

**Files:**
- Modify: `frontend/research-assistant-frontend/src/api/papers.js:40-46`
- Modify: `frontend/research-assistant-frontend/src/api/papers.test.js:4-15,137-143`
- Create: `frontend/research-assistant-frontend/src/views/paperWorkflowState.js`
- Create: `frontend/research-assistant-frontend/src/views/paperWorkflowState.test.js`

**Interfaces:**
- Consumes: `listPapers()`, `startPaperProfileJob()`, new backend `GET /api/papers/profile-status`.
- Produces:
  - `listPaperProfileStatuses(client): Promise<Array>`.
  - `getPaperWorkflowState(paper, profileStatus): { key, label, description, actionLabel, actionType, ready, processing, failed }`.
  - `splitPaperRows(papers, statuses): { pendingRows, readyRows }`.

- [ ] **Step 1: Write failing API helper test**

Modify imports in `papers.test.js`:

```js
import {
  listPapers,
  uploadPaper,
  parsePaper,
  vectorizePaper,
  deletePaper,
  updatePaperCategoryOfPaper,
  getPaperProfile,
  generatePaperProfile,
  startPaperProfileJob,
  getPaperProfileJob,
  listPaperProfileStatuses,
} from './papers.js'
```

Add test:

```js
test('listPaperProfileStatuses calls the batch profile status endpoint', async () => {
  const client = createFakeClient()

  await listPaperProfileStatuses(client)

  assert.deepEqual(client.calls, [{ method: 'get', path: '/api/papers/profile-status' }])
})
```

- [ ] **Step 2: Write failing workflow state tests**

Create `paperWorkflowState.test.js`:

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import { getPaperWorkflowState, splitPaperRows } from './paperWorkflowState.js'

function paper(overrides = {}) {
  return {
    id: 7,
    parseStatus: 'PENDING',
    vectorStatus: 'PENDING',
    ...overrides,
  }
}

function status(overrides = {}) {
  return {
    paperId: 7,
    hasProfile: false,
    jobStatus: null,
    currentStep: null,
    progressPercent: 0,
    processedSections: 0,
    totalSections: 0,
    retryCount: 0,
    errorMessage: null,
    ...overrides,
  }
}

test('workflow state is pending parse before parsing completes', () => {
  const state = getPaperWorkflowState(paper(), status())

  assert.equal(state.key, 'pendingParse')
  assert.equal(state.actionLabel, '解析')
  assert.equal(state.actionType, 'parse')
  assert.equal(state.ready, false)
})

test('workflow state is pending vector after parsing completes', () => {
  const state = getPaperWorkflowState(paper({ parseStatus: 'COMPLETED' }), status())

  assert.equal(state.key, 'pendingVector')
  assert.equal(state.actionLabel, '向量化')
  assert.equal(state.actionType, 'vectorize')
})

test('workflow state is pending profile after vectorization completes', () => {
  const state = getPaperWorkflowState(paper({ parseStatus: 'COMPLETED', vectorStatus: 'COMPLETED' }), status())

  assert.equal(state.key, 'pendingProfile')
  assert.equal(state.actionLabel, '生成画像')
  assert.equal(state.actionType, 'profile')
})

test('workflow state shows profile processing progress', () => {
  const state = getPaperWorkflowState(
    paper({ parseStatus: 'COMPLETED', vectorStatus: 'COMPLETED' }),
    status({ jobStatus: 'PROCESSING', currentStep: 'GENERATING_SECTIONS', progressPercent: 35, processedSections: 12, totalSections: 51 }),
  )

  assert.equal(state.key, 'profileProcessing')
  assert.equal(state.actionLabel, '生成中')
  assert.equal(state.processing, true)
  assert.match(state.description, /12\/51/)
  assert.match(state.description, /35%/)
})

test('workflow state shows failed profile retry', () => {
  const state = getPaperWorkflowState(
    paper({ parseStatus: 'COMPLETED', vectorStatus: 'COMPLETED' }),
    status({ jobStatus: 'FAILED', currentStep: 'FAILED', errorMessage: 'Qwen API Key 未配置' }),
  )

  assert.equal(state.key, 'profileFailed')
  assert.equal(state.actionLabel, '重试画像')
  assert.equal(state.actionType, 'profile')
  assert.equal(state.failed, true)
})

test('workflow state is ready only when parse vector and profile are complete', () => {
  const state = getPaperWorkflowState(
    paper({ parseStatus: 'COMPLETED', vectorStatus: 'COMPLETED' }),
    status({ hasProfile: true, jobStatus: 'COMPLETED', currentStep: 'COMPLETED', progressPercent: 100 }),
  )

  assert.equal(state.key, 'ready')
  assert.equal(state.ready, true)
  assert.equal(state.actionLabel, '问答')
})

test('splitPaperRows separates pending and ready papers', () => {
  const papers = [
    paper({ id: 1, parseStatus: 'PENDING', vectorStatus: 'PENDING' }),
    paper({ id: 2, parseStatus: 'COMPLETED', vectorStatus: 'COMPLETED' }),
  ]
  const statuses = [
    status({ paperId: 1, hasProfile: false }),
    status({ paperId: 2, hasProfile: true, jobStatus: 'COMPLETED', currentStep: 'COMPLETED', progressPercent: 100 }),
  ]

  const result = splitPaperRows(papers, statuses)

  assert.deepEqual(result.pendingRows.map((row) => row.id), [1])
  assert.deepEqual(result.readyRows.map((row) => row.id), [2])
})
```

- [ ] **Step 3: Run frontend tests and verify failures**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/papers.test.js src/views/paperWorkflowState.test.js
```

Expected: tests fail because `listPaperProfileStatuses` and `paperWorkflowState.js` do not exist.

- [ ] **Step 4: Implement API helper**

Add to `papers.js`:

```js
export function listPaperProfileStatuses(client = apiClient) {
  return client.get('/api/papers/profile-status').then((result) => result.data ?? result)
}
```

- [ ] **Step 5: Implement workflow state helper**

Create `paperWorkflowState.js`:

```js
export function isCompletedStatus(status) {
  const normalizedStatus = String(status || '').toUpperCase()
  return ['COMPLETED', 'PARSED', 'VECTORIZED', 'SUCCESS'].includes(normalizedStatus)
}

export function isFailedStatus(status) {
  const normalizedStatus = String(status || '').toUpperCase()
  return ['FAILED', 'ERROR'].includes(normalizedStatus)
}

export function isProcessingStatus(status) {
  return String(status || '').toUpperCase() === 'PROCESSING'
}

export function getProfileStatusByPaperId(statuses = []) {
  return new Map(statuses.map((status) => [status.paperId, status]))
}

export function getPaperWorkflowState(paper, profileStatus = {}) {
  if (!isCompletedStatus(paper?.parseStatus)) {
    return {
      key: isFailedStatus(paper?.parseStatus) ? 'parseFailed' : 'pendingParse',
      label: isFailedStatus(paper?.parseStatus) ? '解析失败' : '待解析',
      description: isFailedStatus(paper?.parseStatus) ? 'PDF 解析失败，请重试解析。' : '还没有解析 PDF。',
      actionLabel: isFailedStatus(paper?.parseStatus) ? '重试解析' : '解析',
      actionType: 'parse',
      ready: false,
      processing: isProcessingStatus(paper?.parseStatus),
      failed: isFailedStatus(paper?.parseStatus),
    }
  }

  if (!isCompletedStatus(paper?.vectorStatus)) {
    return {
      key: isFailedStatus(paper?.vectorStatus) ? 'vectorFailed' : 'pendingVector',
      label: isFailedStatus(paper?.vectorStatus) ? '向量化失败' : '待向量化',
      description: isFailedStatus(paper?.vectorStatus) ? '写入 Qdrant 失败，请重试向量化。' : '已解析，等待写入 Qdrant。',
      actionLabel: isFailedStatus(paper?.vectorStatus) ? '重试向量化' : '向量化',
      actionType: 'vectorize',
      ready: false,
      processing: isProcessingStatus(paper?.vectorStatus),
      failed: isFailedStatus(paper?.vectorStatus),
    }
  }

  if (profileStatus?.jobStatus === 'PROCESSING') {
    return {
      key: 'profileProcessing',
      label: '画像生成中',
      description: profileProgressText(profileStatus),
      actionLabel: '生成中',
      actionType: null,
      ready: false,
      processing: true,
      failed: false,
    }
  }

  if (profileStatus?.jobStatus === 'FAILED') {
    return {
      key: 'profileFailed',
      label: '画像失败',
      description: profileStatus.errorMessage || '文献画像生成失败，请重试。',
      actionLabel: '重试画像',
      actionType: 'profile',
      ready: false,
      processing: false,
      failed: true,
    }
  }

  if (!profileStatus?.hasProfile) {
    return {
      key: 'pendingProfile',
      label: '待生成画像',
      description: '已解析并向量化，等待生成文献画像。',
      actionLabel: '生成画像',
      actionType: 'profile',
      ready: false,
      processing: false,
      failed: false,
    }
  }

  return {
    key: 'ready',
    label: '已入库',
    description: `画像 ${profileStatus.profileVersion || 'paper-profile-v1'}，章节摘要 ${profileStatus.sectionSummaryCount || 0} 条。`,
    actionLabel: '问答',
    actionType: 'chat',
    ready: true,
    processing: false,
    failed: false,
  }
}

export function profileProgressText(profileStatus = {}) {
  const percent = Number(profileStatus.progressPercent || 0)
  const processed = Number(profileStatus.processedSections || 0)
  const total = Number(profileStatus.totalSections || 0)
  const stepText = currentStepText(profileStatus.currentStep)

  if (total > 0) {
    return `${stepText} · 章节摘要 ${processed}/${total} · ${percent}%`
  }

  return `${stepText} · ${percent}%`
}

export function currentStepText(step) {
  const stepMap = {
    WAITING: '等待开始',
    PREPARING: '准备正文和章节',
    GENERATING_SECTIONS: '正在生成章节摘要',
    GENERATING_PROFILE: '正在生成整篇画像',
    SAVING_RESULT: '正在保存画像结果',
    COMPLETED: '已完成',
    FAILED: '已失败',
  }

  return stepMap[step] || '正在处理'
}

export function splitPaperRows(papers = [], profileStatuses = []) {
  const statusByPaperId = getProfileStatusByPaperId(profileStatuses)
  const rows = papers.map((paper) => {
    const profileStatus = statusByPaperId.get(paper.id) || { paperId: paper.id, hasProfile: false, progressPercent: 0 }
    const workflowState = getPaperWorkflowState(paper, profileStatus)
    return { ...paper, profileStatus, workflowState }
  })

  return {
    pendingRows: rows.filter((row) => !row.workflowState.ready),
    readyRows: rows.filter((row) => row.workflowState.ready),
  }
}
```

- [ ] **Step 6: Run frontend state/API tests**

Run:

```bash
node --test src/api/papers.test.js src/views/paperWorkflowState.test.js
```

Expected: tests pass.

---

## Task 4: Frontend Paper Page Two Lists and Progress UI

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue:86-160`
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue:339-824`
- Uses: `frontend/research-assistant-frontend/src/views/paperWorkflowState.js`

**Interfaces:**
- Consumes:
  - `listPaperProfileStatuses()` from `src/api/papers.js`.
  - `splitPaperRows()` and `getPaperWorkflowState()` from `src/views/paperWorkflowState.js`.
- Produces:
  - Pending table rows with one main action button.
  - Ready table rows with `问答 + 更多`.
  - Polling of `profile-status` while any job is `PROCESSING`.

- [ ] **Step 1: Update script imports**

Modify API imports:

```js
import {
  deletePaper,
  getPaperProfileJob,
  getPaperProfile,
  listPaperProfileStatuses,
  listPapers,
  parsePaper,
  startPaperProfileJob,
  uploadPaper,
  updatePaperCategoryOfPaper,
  vectorizePaper,
} from '../api/papers.js'
```

Add workflow imports:

```js
import { splitPaperRows } from './paperWorkflowState.js'
```

- [ ] **Step 2: Add profile status state and list splits**

Add refs near existing `paperRows`:

```js
const profileStatuses = ref([])
const profileStatusLoading = ref(false)
const profileStatusPollingTimer = ref(null)
```

Replace `filteredPaperRows` with split rows and filters:

```js
const paperWorkflowRows = computed(() => splitPaperRows(paperRows.value, profileStatuses.value))

const pendingPaperRows = computed(() => {
  const rows = paperWorkflowRows.value.pendingRows
  if (paperStatusFilter.value === 'pendingParse') {
    return rows.filter((row) => row.workflowState.key === 'pendingParse' || row.workflowState.key === 'parseFailed')
  }
  if (paperStatusFilter.value === 'pendingVector') {
    return rows.filter((row) => row.workflowState.key === 'pendingVector' || row.workflowState.key === 'vectorFailed')
  }
  if (paperStatusFilter.value === 'pendingProfile') {
    return rows.filter((row) => ['pendingProfile', 'profileProcessing', 'profileFailed'].includes(row.workflowState.key))
  }
  if (paperStatusFilter.value === 'processing') {
    return rows.filter((row) => row.workflowState.processing)
  }
  if (paperStatusFilter.value === 'failed') {
    return rows.filter((row) => row.workflowState.failed)
  }
  return rows
})

const readyPaperRows = computed(() => paperWorkflowRows.value.readyRows)
```

Update `paperStats`:

```js
const paperStats = computed(() => ({
  total: paperRows.value.length,
  pending: paperWorkflowRows.value.pendingRows.length,
  ready: paperWorkflowRows.value.readyRows.length,
  pendingParse: paperWorkflowRows.value.pendingRows.filter((paper) => ['pendingParse', 'parseFailed'].includes(paper.workflowState.key)).length,
  pendingVector: paperWorkflowRows.value.pendingRows.filter((paper) => ['pendingVector', 'vectorFailed'].includes(paper.workflowState.key)).length,
  pendingProfile: paperWorkflowRows.value.pendingRows.filter((paper) => ['pendingProfile', 'profileProcessing', 'profileFailed'].includes(paper.workflowState.key)).length,
  processing: paperWorkflowRows.value.pendingRows.filter((paper) => paper.workflowState.processing).length,
  failed: paperWorkflowRows.value.pendingRows.filter((paper) => paper.workflowState.failed).length,
}))
```

Update `paperFilterOptions`:

```js
const paperFilterOptions = computed(() => [
  { label: '全部待处理', value: 'all', count: paperStats.value.pending },
  { label: '待解析', value: 'pendingParse', count: paperStats.value.pendingParse },
  { label: '待向量化', value: 'pendingVector', count: paperStats.value.pendingVector },
  { label: '待画像', value: 'pendingProfile', count: paperStats.value.pendingProfile },
  { label: '处理中', value: 'processing', count: paperStats.value.processing },
  { label: '失败', value: 'failed', count: paperStats.value.failed },
])
```

- [ ] **Step 3: Load and poll profile statuses**

Add function:

```js
async function loadProfileStatuses() {
  profileStatusLoading.value = true

  try {
    profileStatuses.value = await listPaperProfileStatuses()
    syncProfileStatusPolling()
  } catch (error) {
    ElMessage.error(`加载画像任务状态失败：${error.message}`)
  } finally {
    profileStatusLoading.value = false
  }
}
```

Modify `reloadLibrary`:

```js
async function reloadLibrary() {
  await Promise.all([loadCategories(), loadPapers(), loadProfileStatuses()])
}
```

Add polling helpers:

```js
function hasProcessingProfileJob() {
  return profileStatuses.value.some((status) => status.jobStatus === 'PROCESSING')
}

function syncProfileStatusPolling() {
  if (hasProcessingProfileJob()) {
    startProfileStatusPolling()
  } else {
    stopProfileStatusPolling()
  }
}

function startProfileStatusPolling() {
  if (profileStatusPollingTimer.value) {
    return
  }
  profileStatusPollingTimer.value = window.setInterval(loadProfileStatuses, 3000)
}

function stopProfileStatusPolling() {
  if (profileStatusPollingTimer.value) {
    window.clearInterval(profileStatusPollingTimer.value)
    profileStatusPollingTimer.value = null
  }
}
```

Modify `onBeforeUnmount`:

```js
onBeforeUnmount(() => {
  stopProfilePolling()
  stopProfileStatusPolling()
})
```

- [ ] **Step 4: Add main action dispatcher**

Add functions:

```js
async function handlePrimaryWorkflowAction(row) {
  const actionType = row.workflowState?.actionType
  if (actionType === 'parse') {
    await handleParse(row)
    return
  }
  if (actionType === 'vectorize') {
    await handleVectorize(row)
    return
  }
  if (actionType === 'profile') {
    await startProfileJobFromList(row)
    return
  }
  if (actionType === 'chat') {
    goToChat(row)
  }
}

async function startProfileJobFromList(row) {
  activeAction.value = `profile-${row.id}`

  try {
    await startPaperProfileJob(row.id)
    profileDialogVisible.value = false
    ElMessage.success('文献画像已开始生成')
    await loadProfileStatuses()
  } catch (error) {
    ElMessage.error(`启动文献画像生成失败：${error.message}`)
  } finally {
    if (activeAction.value === `profile-${row.id}`) {
      activeAction.value = ''
    }
  }
}
```

Modify `handleGenerateProfile` so it closes the dialog and refreshes list progress:

```js
async function handleGenerateProfile() {
  if (!profilePaper.value?.id || isProfileProcessing()) {
    return
  }

  profileGenerating.value = true

  try {
    profileJob.value = await startPaperProfileJob(profilePaper.value.id)
    ElMessage.success('文献画像已开始生成')
    profileDialogVisible.value = false
    await loadProfileStatuses()
  } catch (error) {
    ElMessage.error(`启动文献画像生成失败：${error.message}`)
  } finally {
    profileGenerating.value = false
  }
}
```

Update `handleParse` and `handleVectorize` to refresh full library state after success:

```js
await reloadLibrary()
```

instead of only `await loadPapers()`.

- [ ] **Step 5: Replace the single table template with pending and ready cards**

Replace the current `<main class="module-main">...</main>` table block with this structure:

```vue
<main class="module-main paper-work-queue-main">
  <el-card class="workflow-card workbench-card paper-queue-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>待处理文献</span>
        <el-tag type="warning" effect="plain">{{ pendingPaperRows.length }} 篇</el-tag>
      </div>
    </template>

    <div class="panel-scroll queue-panel-scroll">
      <el-table
        v-loading="loading || profileStatusLoading"
        :data="pendingPaperRows"
        border
        height="100%"
        empty-text="暂无待处理文献。新上传或未完成解析、向量化、画像的文献会出现在这里。"
      >
        <el-table-column prop="title" label="文献" min-width="260">
          <template #default="{ row }">
            <strong>{{ row.title || row.fileName || '未命名文献' }}</strong>
            <p class="paper-meta">{{ formatPaperMeta(row) }}</p>
            <el-tag size="small" type="info" effect="plain">{{ row.categoryName || '未分类' }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="处理状态" min-width="260">
          <template #default="{ row }">
            <div class="workflow-status-cell">
              <div class="workflow-status-tags">
                <el-tag size="small" :type="statusTagType(row.parseStatus)" effect="plain">解析 {{ compactStatusText(row.parseStatus) }}</el-tag>
                <el-tag size="small" :type="statusTagType(row.vectorStatus)" effect="plain">向量 {{ compactStatusText(row.vectorStatus) }}</el-tag>
                <el-tag size="small" :type="workflowStateTagType(row.workflowState)" effect="plain">{{ row.workflowState.label }}</el-tag>
              </div>
              <p class="paper-meta">{{ row.workflowState.description }}</p>
              <el-progress
                v-if="row.workflowState.key === 'profileProcessing'"
                :percentage="Number(row.profileStatus?.progressPercent || 0)"
                :stroke-width="8"
              />
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="uploadTime" label="上传时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.uploadTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="compact-action-row">
              <el-button
                size="small"
                type="primary"
                :plain="row.workflowState.key !== 'pendingParse'"
                :disabled="row.workflowState.processing"
                :loading="activeAction === `${row.workflowState.actionType}-${row.id}` || activeAction === `profile-${row.id}`"
                @click="handlePrimaryWorkflowAction(row)"
              >
                {{ row.workflowState.actionLabel }}
              </el-button>
              <el-dropdown trigger="click" @command="(command) => handlePaperCommand(command, row)">
                <el-button size="small" plain>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="profile">查看画像</el-dropdown-item>
                    <el-dropdown-item command="regenerateProfile">重新生成画像</el-dropdown-item>
                    <el-dropdown-item command="move">改分类</el-dropdown-item>
                    <el-dropdown-item command="chat">问答</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-card>

  <el-card class="workflow-card workbench-card paper-queue-card ready-paper-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>已入库文献</span>
        <el-tag type="success" effect="plain">{{ readyPaperRows.length }} 篇</el-tag>
      </div>
    </template>

    <div class="panel-scroll queue-panel-scroll">
      <el-table
        v-loading="loading || profileStatusLoading"
        :data="readyPaperRows"
        border
        height="100%"
        empty-text="暂无已入库文献。解析、向量化和画像全部完成后会进入这里。"
      >
        <el-table-column prop="title" label="文献" min-width="280">
          <template #default="{ row }">
            <strong>{{ row.title || row.fileName || '未命名文献' }}</strong>
            <p class="paper-meta">{{ formatPaperMeta(row) }}</p>
            <el-tag size="small" type="info" effect="plain">{{ row.categoryName || '未分类' }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="画像资产" min-width="220">
          <template #default="{ row }">
            <el-tag type="success" effect="plain">{{ row.profileStatus?.profileVersion || 'paper-profile-v1' }}</el-tag>
            <p class="paper-meta">章节摘要 {{ row.profileStatus?.sectionSummaryCount || 0 }} 条</p>
          </template>
        </el-table-column>

        <el-table-column prop="uploadTime" label="上传时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.uploadTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="compact-action-row">
              <el-button size="small" type="success" plain @click="goToChat(row)">问答</el-button>
              <el-dropdown trigger="click" @command="(command) => handlePaperCommand(command, row)">
                <el-button size="small" plain>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="profile">查看画像</el-dropdown-item>
                    <el-dropdown-item command="regenerateProfile">重新生成画像</el-dropdown-item>
                    <el-dropdown-item command="move">改分类</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-card>
</main>
```

- [ ] **Step 6: Add menu command and small display helpers**

Add functions:

```js
async function handlePaperCommand(command, row) {
  if (command === 'profile') {
    await openProfileDialog(row)
    return
  }
  if (command === 'regenerateProfile') {
    await startProfileJobFromList(row)
    return
  }
  if (command === 'move') {
    openMovePaperDialog(row)
    return
  }
  if (command === 'chat') {
    goToChat(row)
    return
  }
  if (command === 'delete') {
    await handleDeletePaper(row)
  }
}

function compactStatusText(status) {
  const normalizedStatus = String(status || '').toUpperCase()
  if (['COMPLETED', 'PARSED', 'VECTORIZED', 'SUCCESS'].includes(normalizedStatus)) {
    return '✓'
  }
  if (normalizedStatus === 'PROCESSING') {
    return '处理中'
  }
  if (['FAILED', 'ERROR'].includes(normalizedStatus)) {
    return '失败'
  }
  return '待处理'
}

function workflowStateTagType(workflowState) {
  if (workflowState?.ready) {
    return 'success'
  }
  if (workflowState?.failed) {
    return 'danger'
  }
  if (workflowState?.processing) {
    return 'warning'
  }
  return 'info'
}
```

- [ ] **Step 7: Add focused CSS classes if existing layout needs spacing**

Add scoped class usage inside existing global style area of `PaperManagementView.vue` only if current CSS does not already cover these class names:

```css
.paper-work-queue-main {
  display: grid;
  grid-template-rows: minmax(260px, 1fr) minmax(260px, 1fr);
  gap: 16px;
  min-height: 0;
}

.paper-queue-card {
  min-height: 0;
}

.queue-panel-scroll {
  min-height: 260px;
}

.workflow-status-cell {
  display: grid;
  gap: 8px;
}

.workflow-status-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.compact-action-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
}
```

- [ ] **Step 8: Run frontend tests and build**

Run:

```bash
node --test src/api/papers.test.js src/views/paperWorkflowState.test.js src/api/paperCategories.test.js src/api/rag.test.js src/views/ragChatState.test.js
```

Expected: tests pass.

Run:

```bash
npm run build
```

Expected: build succeeds. Existing Vite warnings about third-party PURE comments or chunk size are acceptable if the build exits successfully.

- [ ] **Step 9: Update progress document for frontend stage**

Append one concise line to `docs/status/current.md`:

```text
文献处理队列前端阶段已完成：文献页主区域拆为“待处理文献”和“已入库文献”，待处理文献按解析、向量化、画像三步状态显示一个主按钮并在画像生成中展示章节进度和百分比；已入库文献只保留问答和更多菜单，查看画像、重新生成画像、改分类和删除收纳到更多菜单；点击生成画像后关闭弹窗并回到列表轮询进度。验证通过：node --test src/api/papers.test.js src/views/paperWorkflowState.test.js src/api/paperCategories.test.js src/api/rag.test.js src/views/ragChatState.test.js 通过，npm run build 通过。
```

---

## Task 5: Final Backend/Frontend Verification and Manual Test Notes

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: all code from Tasks 1-4.
- Produces: verified final project state and Apifox/browser validation instructions.

- [ ] **Step 1: Run backend full tests**

Run from `backend/research-assistant-backend`:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

Expected: all tests pass.

- [ ] **Step 2: Run frontend full relevant tests**

Run from `frontend/research-assistant-frontend`:

```bash
node --test src/api/papers.test.js src/views/paperWorkflowState.test.js src/api/paperCategories.test.js src/api/rag.test.js src/views/ragChatState.test.js src/api/researchIdeas.test.js src/api/chatHistory.test.js
```

Expected: all tests pass.

- [ ] **Step 3: Run frontend build**

Run:

```bash
npm run build
```

Expected: build succeeds. Existing Vite warnings about third-party PURE comments or chunk size are acceptable if the build exits successfully.

- [ ] **Step 4: Record manual validation instructions in final progress entry**

Append final concise entry to `docs/status/current.md`:

```text
文献画像任务进度与文献处理队列已完成阶段性实现：后端 paper_profile_job 支持 currentStep、progressPercent、processedSections、totalSections 和 retryCount，画像生成过程可展示准备、章节摘要、整篇画像、保存、完成和失败状态，失败后可通过原 async 接口重试；新增 GET /api/papers/profile-status 批量返回画像状态；前端文献页拆为待处理文献和已入库文献，三步完成才进入已入库，待处理列表使用一个主按钮推进解析、向量化、生成/重试画像，画像生成中在列表展示百分比和章节进度，已入库列表将低频操作收纳到更多菜单。最终验证：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过；前端 node --test src/api/papers.test.js src/views/paperWorkflowState.test.js src/api/paperCategories.test.js src/api/rag.test.js src/views/ragChatState.test.js src/api/researchIdeas.test.js src/api/chatHistory.test.js 通过；npm run build 通过。建议人工验证：上传新 PDF 后确认进入待处理列表，依次点击解析、向量化、生成画像，确认生成画像后弹窗关闭且列表显示进度，完成后自动进入已入库列表；再临时制造画像失败，确认显示失败原因和重试画像按钮。
```

- [ ] **Step 5: Check working tree summary without committing**

Run from project root:

```bash
git status --short
```

Expected: changed files are limited to backend profile job/status code, frontend paper page/workflow state code, tests, and progress/design/plan docs. Do not commit unless the user explicitly asks.
