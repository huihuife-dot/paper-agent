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
import com.myagent.assistant.paper.service.PaperProfileIndexService;
import com.myagent.assistant.paper.dto.PaperProfileIndexResponse;
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
        PaperProfileIndexService profileIndexService = mock(PaperProfileIndexService.class);

        when(paperReferenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(paper(7L), paper(8L)));
        when(paperProfileMapper.selectList(any(Wrapper.class))).thenReturn(List.of(profile(7L, "paper-profile-v1")));
        when(sectionSummaryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(summary(7L), summary(7L)));
        when(jobMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                job(1L, 7L, "COMPLETED", "COMPLETED", 100, 2, 2, 0, null, LocalDateTime.now().minusMinutes(1)),
                job(2L, 8L, "FAILED", "FAILED", 35, 1, 3, 2, "Qwen API Key 未配置", LocalDateTime.now())
        ));
        PaperProfileIndexResponse indexStatus = new PaperProfileIndexResponse();
        indexStatus.setProfileIndexed(true);
        indexStatus.setIndexedSectionSummaries(2);
        indexStatus.setSuccess(true);
        when(profileIndexService.getIndexStatus(7L)).thenReturn(indexStatus);

        PaperProfileStatusServiceImpl service = new PaperProfileStatusServiceImpl(
                paperReferenceMapper,
                paperProfileMapper,
                sectionSummaryMapper,
                jobMapper,
                profileIndexService
        );

        List<PaperProfileStatusResponse> statuses = service.listProfileStatuses();

        PaperProfileStatusResponse ready = statuses.stream().filter(item -> item.getPaperId().equals(7L)).findFirst().orElseThrow();
        assertThat(ready.getHasProfile()).isTrue();
        assertThat(ready.getProfileVersion()).isEqualTo("paper-profile-v1");
        assertThat(ready.getSectionSummaryCount()).isEqualTo(2);
        assertThat(ready.getJobStatus()).isEqualTo("COMPLETED");
        assertThat(ready.getProgressPercent()).isEqualTo(100);
        assertThat(ready.getProfileIndexComplete()).isTrue();

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
