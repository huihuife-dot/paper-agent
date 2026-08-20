package com.myagent.assistant.knowledge.controller;

import com.myagent.assistant.knowledge.dto.KnowledgeBuildResponse;
import com.myagent.assistant.knowledge.dto.KnowledgeCatalogResponse;
import com.myagent.assistant.knowledge.dto.KnowledgeFeedbackRequest;
import com.myagent.assistant.knowledge.dto.KnowledgeDatasetItem;
import com.myagent.assistant.knowledge.dto.KnowledgeTopicResponse;
import com.myagent.assistant.knowledge.dto.KnowledgeUnitResponse;
import com.myagent.assistant.knowledge.service.PaperKnowledgeService;
import com.myagent.assistant.paper.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 论文目录、学术知识单元、主题导航和用户反馈接口。
 */
@RestController
public class KnowledgeController {

    private final PaperKnowledgeService knowledgeService;

    public KnowledgeController(PaperKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/api/knowledge/catalogs")
    public Result<List<KnowledgeCatalogResponse>> listCatalogs() {
        return Result.success(knowledgeService.listCatalogs());
    }

    @GetMapping("/api/knowledge/topics")
    public Result<List<KnowledgeTopicResponse>> listTopics(
            @RequestParam(value = "knowledgeType", required = false) String knowledgeType,
            @RequestParam(value = "limit", defaultValue = "60") int limit) {
        return Result.success(knowledgeService.listTopics(knowledgeType, limit));
    }

    @GetMapping("/api/knowledge/dataset")
    public Result<List<KnowledgeDatasetItem>> exportDataset(
            @RequestParam(value = "minimumConfidence", defaultValue = "SILVER") String minimumConfidence) {
        return Result.success(knowledgeService.exportDataset(minimumConfidence));
    }

    @PostMapping("/api/papers/{paperId}/knowledge/build")
    public Result<KnowledgeBuildResponse> build(@PathVariable Long paperId,
                                                @RequestParam(value = "async", defaultValue = "true") boolean async) {
        return Result.success(async ? knowledgeService.startBuild(paperId) : knowledgeService.build(paperId));
    }

    @GetMapping("/api/papers/{paperId}/knowledge/status")
    public Result<KnowledgeBuildResponse> status(@PathVariable Long paperId) {
        return Result.success(knowledgeService.getBuildStatus(paperId));
    }

    @GetMapping("/api/papers/{paperId}/knowledge")
    public Result<List<KnowledgeUnitResponse>> listUnits(
            @PathVariable Long paperId,
            @RequestParam(value = "knowledgeType", required = false) String knowledgeType,
            @RequestParam(value = "includeRejected", defaultValue = "false") boolean includeRejected) {
        return Result.success(knowledgeService.listUnits(paperId, knowledgeType, includeRejected));
    }

    @PostMapping("/api/knowledge/units/{unitId}/feedback")
    public Result<KnowledgeUnitResponse> feedback(@PathVariable Long unitId,
                                                  @Valid @RequestBody KnowledgeFeedbackRequest request) {
        return Result.success(knowledgeService.submitFeedback(unitId, request));
    }
}
