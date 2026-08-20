package com.myagent.assistant.knowledge.service;

import com.myagent.assistant.knowledge.dto.KnowledgeBuildResponse;
import com.myagent.assistant.knowledge.dto.KnowledgeCatalogResponse;
import com.myagent.assistant.knowledge.dto.KnowledgeFeedbackRequest;
import com.myagent.assistant.knowledge.dto.KnowledgeDatasetItem;
import com.myagent.assistant.knowledge.dto.KnowledgeTopicResponse;
import com.myagent.assistant.knowledge.dto.KnowledgeUnitResponse;
import com.myagent.assistant.knowledge.entity.PaperKnowledgeUnit;

import java.util.List;

public interface PaperKnowledgeService {
    KnowledgeBuildResponse startBuild(Long paperId);
    KnowledgeBuildResponse build(Long paperId);
    KnowledgeBuildResponse getBuildStatus(Long paperId);
    List<KnowledgeCatalogResponse> listCatalogs();
    List<KnowledgeUnitResponse> listUnits(Long paperId, String knowledgeType, boolean includeRejected);
    KnowledgeUnitResponse submitFeedback(Long unitId, KnowledgeFeedbackRequest request);
    List<KnowledgeTopicResponse> listTopics(String knowledgeType, int limit);
    List<KnowledgeDatasetItem> exportDataset(String minimumConfidence);
    List<PaperKnowledgeUnit> findUnits(List<Long> paperIds, List<String> knowledgeTypes, int limitPerPaper);
}
