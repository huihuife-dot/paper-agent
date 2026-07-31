package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.paper.dto.PaperProfileIndexResponse;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.paper.service.PaperProfileIndexService;
import com.myagent.assistant.qdrant.service.QdrantService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaperProfileIndexServiceImpl implements PaperProfileIndexService {
    private static final String PROFILE_VERSION = "paper-profile-v1";
    private static final String SUMMARY_VERSION = "section-summary-v1";

    private final PaperReferenceMapper paperMapper;
    private final PaperProfileMapper profileMapper;
    private final PaperSectionSummaryMapper summaryMapper;
    private final QdrantService qdrantService;

    public PaperProfileIndexServiceImpl(PaperReferenceMapper paperMapper,
                                        PaperProfileMapper profileMapper,
                                        PaperSectionSummaryMapper summaryMapper,
                                        QdrantService qdrantService) {
        this.paperMapper = paperMapper;
        this.profileMapper = profileMapper;
        this.summaryMapper = summaryMapper;
        this.qdrantService = qdrantService;
    }

    @Override
    public PaperProfileIndexResponse indexProfile(Long paperId) {
        requirePaper(paperId);
        PaperProfile profile = findProfile(paperId);
        if (profile == null) throw new RuntimeException("请先生成文献画像");
        List<PaperSectionSummary> summaries = findSummaries(paperId);

        PaperProfileIndexResponse result = new PaperProfileIndexResponse();
        result.setPaperId(paperId);
        result.setTotalSectionSummaries(summaries.size());
        try {
            qdrantService.upsertProfilePoint(profile);
            result.setProfileIndexed(true);
        } catch (Exception e) {
            result.setProfileIndexed(false);
            result.getFailures().add("画像：" + e.getMessage());
        }
        int indexed = 0;
        for (PaperSectionSummary summary : summaries) {
            try {
                qdrantService.upsertSummaryPoint(summary);
                indexed++;
            } catch (Exception e) {
                result.getFailures().add("章节摘要 " + summary.getId() + "：" + e.getMessage());
            }
        }
        result.setIndexedSectionSummaries(indexed);
        result.setSuccess(Boolean.TRUE.equals(result.getProfileIndexed())
                && indexed == summaries.size() && result.getFailures().isEmpty());
        return result;
    }

    @Override
    public PaperProfileIndexResponse getIndexStatus(Long paperId) {
        requirePaper(paperId);
        List<PaperSectionSummary> summaries = findSummaries(paperId);
        long profiles = qdrantService.countPaperPoints(paperId, "PAPER_PROFILE");
        long indexedSummaries = qdrantService.countPaperPoints(paperId, "SECTION_SUMMARY");
        PaperProfileIndexResponse result = new PaperProfileIndexResponse();
        result.setPaperId(paperId);
        result.setProfileIndexed(profiles > 0);
        result.setTotalSectionSummaries(summaries.size());
        result.setIndexedSectionSummaries(Math.toIntExact(indexedSummaries));
        result.setSuccess(profiles > 0 && indexedSummaries == summaries.size());
        return result;
    }

    private PaperReference requirePaper(Long paperId) {
        if (paperId == null || paperId <= 0) throw new RuntimeException("paperId 不能为空");
        PaperReference paper = paperMapper.selectById(paperId);
        if (paper == null) throw new RuntimeException("文献不存在");
        return paper;
    }

    private PaperProfile findProfile(Long paperId) {
        return profileMapper.selectOne(new QueryWrapper<PaperProfile>()
                .eq("paper_id", paperId).eq("profile_version", PROFILE_VERSION));
    }

    private List<PaperSectionSummary> findSummaries(Long paperId) {
        return summaryMapper.selectList(new QueryWrapper<PaperSectionSummary>()
                .eq("paper_id", paperId).eq("summary_version", SUMMARY_VERSION).orderByAsc("section_id"));
    }
}
