package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.paper.dto.PaperProfileJobResponse;
import com.myagent.assistant.paper.entity.PaperProfileJob;
import com.myagent.assistant.paper.mapper.PaperProfileJobMapper;
import com.myagent.assistant.paper.service.PaperProfileProgressListener;
import com.myagent.assistant.paper.service.PaperProfileService;
import com.myagent.assistant.paper.service.ProfileJobAsyncExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperProfileJobServiceImplTest {

    /**
     * 模拟异步执行器：只收集任务不执行，由测试手动 run() 控制时机。
     */
    private static ProfileJobAsyncExecutor collectingExecutor(List<Runnable> sink) {
        return new ProfileJobAsyncExecutor() {
            @Override
            public void executeAsync(Runnable runnable) {
                sink.add(runnable);
            }
        };
    }

    private PaperProfileJobServiceImpl createService(PaperProfileJobMapper jm, PaperProfileService ps, List<Runnable> s) {
        return new PaperProfileJobServiceImpl(jm, ps, collectingExecutor(s));
    }

    @Test
    void startProfileJobReusesProcessingJob() {
        PaperProfileJobMapper jobMapper = mock(PaperProfileJobMapper.class);
        PaperProfileService profileService = mock(PaperProfileService.class);
        PaperProfileJob existing = job(3L, 7L, "PROCESSING", null);
        when(jobMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        PaperProfileJobServiceImpl service = createService(jobMapper, profileService, new ArrayList<>());
        PaperProfileJobResponse response = service.startProfileJob(7L);

        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getPaperId()).isEqualTo(7L);
        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        verify(jobMapper, never()).insert(any(PaperProfileJob.class));
        verify(profileService, never()).generateProfile(eq(7L), any(PaperProfileProgressListener.class));
    }

    @Test
    void startProfileJobInitializesProgressFieldsAndPassesProgressListener() {
        PaperProfileJobMapper jobMapper = mock(PaperProfileJobMapper.class);
        PaperProfileService profileService = mock(PaperProfileService.class);
        when(jobMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        List<Runnable> tasks = new ArrayList<>();
        PaperProfileJobServiceImpl service = createService(jobMapper, profileService, tasks);

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
        PaperProfileJobServiceImpl service = createService(jobMapper, profileService, tasks);

        PaperProfileJobResponse response = service.startProfileJob(7L);

        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        assertThat(response.getRetryCount()).isEqualTo(3);
        ArgumentCaptor<PaperProfileJob> jobCaptor = ArgumentCaptor.forClass(PaperProfileJob.class);
        verify(jobMapper).insert(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getRetryCount()).isEqualTo(3);
    }

    @Test
    void startProfileJobRunsGenerationAndMarksCompleted() {
        PaperProfileJobMapper jobMapper = mock(PaperProfileJobMapper.class);
        PaperProfileService profileService = mock(PaperProfileService.class);
        when(jobMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        List<Runnable> tasks = new ArrayList<>();
        PaperProfileJobServiceImpl service = createService(jobMapper, profileService, tasks);

        PaperProfileJobResponse response = service.startProfileJob(7L);

        assertThat(response.getPaperId()).isEqualTo(7L);
        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        assertThat(tasks).hasSize(1);

        tasks.get(0).run();

        verify(profileService).generateProfile(eq(7L), any(PaperProfileProgressListener.class));
        ArgumentCaptor<PaperProfileJob> jobCaptor = ArgumentCaptor.forClass(PaperProfileJob.class);
        verify(jobMapper).insert(jobCaptor.capture());
        PaperProfileJob inserted = jobCaptor.getValue();
        assertThat(inserted.getPaperId()).isEqualTo(7L);
        assertThat(inserted.getStatus()).isEqualTo("COMPLETED");
        assertThat(inserted.getCurrentStep()).isEqualTo("COMPLETED");
        assertThat(inserted.getProgressPercent()).isEqualTo(100);
        assertThat(inserted.getFinishTime()).isNotNull();
        verify(jobMapper).updateById(inserted);
    }

    @Test
    void startProfileJobMarksFailedWhenGenerationThrows() {
        PaperProfileJobMapper jobMapper = mock(PaperProfileJobMapper.class);
        PaperProfileService profileService = mock(PaperProfileService.class);
        when(jobMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(profileService.generateProfile(eq(7L), any(PaperProfileProgressListener.class)))
                .thenThrow(new RuntimeException("Qwen API Key 未配置"));

        List<Runnable> tasks = new ArrayList<>();
        PaperProfileJobServiceImpl service = createService(jobMapper, profileService, tasks);

        service.startProfileJob(7L);
        tasks.get(0).run();

        ArgumentCaptor<PaperProfileJob> jobCaptor = ArgumentCaptor.forClass(PaperProfileJob.class);
        verify(jobMapper).insert(jobCaptor.capture());
        PaperProfileJob inserted = jobCaptor.getValue();
        assertThat(inserted.getStatus()).isEqualTo("FAILED");
        assertThat(inserted.getCurrentStep()).isEqualTo("FAILED");
        assertThat(inserted.getErrorMessage()).contains("Qwen API Key 未配置");
        assertThat(inserted.getFinishTime()).isNotNull();
        verify(jobMapper).updateById(inserted);
    }

    private PaperProfileJob job(Long id, Long paperId, String status, String errorMessage) {
        PaperProfileJob job = new PaperProfileJob();
        job.setId(id);
        job.setPaperId(paperId);
        job.setStatus(status);
        job.setErrorMessage(errorMessage);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        return job;
    }
}
