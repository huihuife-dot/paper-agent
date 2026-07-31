package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.entity.PaperAsset;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.myagent.assistant.paper.dto.PaperAssetAnalysisUpdateRequest;

public interface PaperAssetService {
    Map<String, Object> extract(Long paperId);
    List<PaperAsset> list(Long paperId, String assetType);
    PaperAsset get(Long paperId, Long assetId);
    Path resolveContent(Long paperId, Long assetId);
    PaperAsset updateAnalysis(Long paperId, Long assetId, PaperAssetAnalysisUpdateRequest request);
    PaperAsset confirm(Long paperId, Long assetId, boolean accepted);
    PaperAsset analyze(Long paperId, Long assetId);
    Map<String, Object> analyzeEligible(Long paperId);
    Map<String, Object> latestAnalysisJob(Long paperId);
    void deleteForPaper(Long paperId);
}
