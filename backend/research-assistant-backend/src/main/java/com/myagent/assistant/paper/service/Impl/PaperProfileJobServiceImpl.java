package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.paper.dto.PaperProfileJobResponse;
import com.myagent.assistant.paper.entity.PaperProfileJob;
import com.myagent.assistant.paper.mapper.PaperProfileJobMapper;
import com.myagent.assistant.paper.service.PaperProfileJobService;
import com.myagent.assistant.paper.service.PaperProfileProgressListener;
import com.myagent.assistant.paper.service.PaperProfileService;
import com.myagent.assistant.paper.service.ProfileJobAsyncExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 文献画像异步任务服务实现。
 *
 * 异步执行链路：
 * startProfileJob() → ProfileJobAsyncExecutor（@Async）→ Spring 托管线程池 → generateProfile()
 *
 * 线程池配置见 common/config/AsyncConfig.java。
 */
@Service
public class PaperProfileJobServiceImpl implements PaperProfileJobService {

    private static final Logger log = LoggerFactory.getLogger(PaperProfileJobServiceImpl.class);

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

    private final PaperProfileJobMapper jobMapper;
    private final PaperProfileService profileService;
    private final ProfileJobAsyncExecutor asyncExecutor;

    public PaperProfileJobServiceImpl(PaperProfileJobMapper jobMapper,
                                      PaperProfileService profileService,
                                      ProfileJobAsyncExecutor asyncExecutor) {
        this.jobMapper = jobMapper;
        this.profileService = profileService;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public PaperProfileJobResponse startProfileJob(Long paperId) {
        validatePaperId(paperId);

        PaperProfileJob processingJob = findProcessingJob(paperId);
        if (processingJob != null) {
            return toResponse(processingJob);
        }

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

        // Spring @Async 托管：profileTaskExecutor 线程池中执行
        asyncExecutor.executeAsync(() -> runJob(job));

        return toResponse(job);
    }

    @Override
    public PaperProfileJobResponse getLatestJob(Long paperId) {
        validatePaperId(paperId);
        PaperProfileJob job = findLatestJob(paperId);
        return job == null ? null : toResponse(job);
    }

    private void runJob(PaperProfileJob job) {
        try {
            profileService.generateProfile(job.getPaperId(), new PaperProfileProgressListener() {
                @Override
                public void onPreparing() {
                    updateProgress(job, STEP_PREPARING, 5, 0, 0);
                }

                @Override
                public void onSectionProgress(int processedSections, int totalSections) {
                    updateProgress(job, STEP_GENERATING_SECTIONS,
                            sectionProgressPercent(processedSections, totalSections),
                            processedSections, totalSections);
                }

                @Override
                public void onGeneratingProfile() {
                    updateProgress(job, STEP_GENERATING_PROFILE, 90,
                            safeInt(job.getProcessedSections()), safeInt(job.getTotalSections()));
                }

                @Override
                public void onSavingResult() {
                    updateProgress(job, STEP_SAVING_RESULT, 98,
                            safeInt(job.getProcessedSections()), safeInt(job.getTotalSections()));
                }
            });
            job.setStatus(STATUS_COMPLETED);
            job.setCurrentStep(STEP_COMPLETED);
            job.setProgressPercent(100);
            job.setErrorMessage(null);
        } catch (Exception e) {
            log.error("文献画像生成失败 paperId={}: {}", job.getPaperId(), e.getMessage(), e);
            job.setStatus(STATUS_FAILED);
            job.setCurrentStep(STEP_FAILED);
            job.setErrorMessage(e.getMessage());
        } finally {
            job.setFinishTime(LocalDateTime.now());
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
        }
    }

    private PaperProfileJob findProcessingJob(Long paperId) {
        return jobMapper.selectOne(new QueryWrapper<PaperProfileJob>()
                .eq("paper_id", paperId)
                .eq("status", STATUS_PROCESSING)
                .orderByDesc("create_time")
                .last("LIMIT 1"));
    }

    private PaperProfileJob findLatestJob(Long paperId) {
        return jobMapper.selectOne(new QueryWrapper<PaperProfileJob>()
                .eq("paper_id", paperId)
                .orderByDesc("create_time")
                .last("LIMIT 1"));
    }

    private void validatePaperId(Long paperId) {
        if (paperId == null || paperId <= 0) {
            throw new RuntimeException("paperId 不能为空");
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int sectionProgressPercent(int processedSections, int totalSections) {
        if (totalSections <= 0) return 5;
        int boundedProcessed = Math.max(0, Math.min(processedSections, totalSections));
        return 5 + (int) Math.floor((boundedProcessed * 75.0) / totalSections);
    }

    private void updateProgress(PaperProfileJob job, String currentStep, int progressPercent,
                                int processedSections, int totalSections) {
        job.setCurrentStep(currentStep);
        job.setProgressPercent(Math.max(0, Math.min(progressPercent, 100)));
        job.setProcessedSections(Math.max(0, processedSections));
        job.setTotalSections(Math.max(0, totalSections));
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    private PaperProfileJobResponse toResponse(PaperProfileJob job) {
        PaperProfileJobResponse response = new PaperProfileJobResponse();
        response.setId(job.getId());
        response.setPaperId(job.getPaperId());
        response.setStatus(job.getStatus());
        response.setCurrentStep(job.getCurrentStep());
        response.setProgressPercent(job.getProgressPercent());
        response.setProcessedSections(job.getProcessedSections());
        response.setTotalSections(job.getTotalSections());
        response.setRetryCount(job.getRetryCount());
        response.setErrorMessage(job.getErrorMessage());
        response.setStartTime(job.getStartTime());
        response.setFinishTime(job.getFinishTime());
        response.setCreateTime(job.getCreateTime());
        response.setUpdateTime(job.getUpdateTime());
        return response;
    }
}
