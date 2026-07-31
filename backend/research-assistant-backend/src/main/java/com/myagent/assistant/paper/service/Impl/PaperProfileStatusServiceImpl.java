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
import com.myagent.assistant.paper.service.PaperProfileIndexService;
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
    private final PaperProfileIndexService profileIndexService;

    public PaperProfileStatusServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                         PaperProfileMapper paperProfileMapper,
                                         PaperSectionSummaryMapper sectionSummaryMapper,
                                         PaperProfileJobMapper jobMapper,
                                         PaperProfileIndexService profileIndexService) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperProfileMapper = paperProfileMapper;
        this.sectionSummaryMapper = sectionSummaryMapper;
        this.jobMapper = jobMapper;
        this.profileIndexService = profileIndexService;
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
        if (profile != null) {
            try {
                var indexStatus = profileIndexService.getIndexStatus(paperId);
                response.setProfileIndexed(indexStatus.getProfileIndexed());
                response.setIndexedSectionSummaryCount(indexStatus.getIndexedSectionSummaries());
                response.setProfileIndexComplete(indexStatus.getSuccess());
            } catch (RuntimeException ignored) {
                response.setProfileIndexed(false);
                response.setIndexedSectionSummaryCount(0);
                response.setProfileIndexComplete(false);
            }
        } else {
            response.setProfileIndexed(false);
            response.setIndexedSectionSummaryCount(0);
            response.setProfileIndexComplete(false);
        }
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
