package com.myagent.assistant.paper.controller;

import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.paper.entity.PaperAsset;
import com.myagent.assistant.paper.service.PaperAssetService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.myagent.assistant.paper.dto.PaperAssetAnalysisUpdateRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/papers/{paperId}/assets")
public class PaperAssetController {
    private final PaperAssetService paperAssetService;
    public PaperAssetController(PaperAssetService paperAssetService) { this.paperAssetService = paperAssetService; }
    @PostMapping("/extract") public Result<Map<String, Object>> extract(@PathVariable Long paperId) { return Result.success(paperAssetService.extract(paperId)); }
    @GetMapping public Result<List<PaperAsset>> list(@PathVariable Long paperId, @RequestParam(required = false) String type) { return Result.success(paperAssetService.list(paperId, type)); }
    @GetMapping("/{assetId}") public Result<PaperAsset> detail(@PathVariable Long paperId, @PathVariable Long assetId) { return Result.success(paperAssetService.get(paperId, assetId)); }
    @GetMapping("/{assetId}/content") public ResponseEntity<FileSystemResource> content(@PathVariable Long paperId, @PathVariable Long assetId) {
        Path path = paperAssetService.resolveContent(paperId, assetId);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(new FileSystemResource(path));
    }
    @PutMapping("/{assetId}/analysis") public Result<PaperAsset> updateAnalysis(@PathVariable Long paperId, @PathVariable Long assetId, @RequestBody PaperAssetAnalysisUpdateRequest request) { return Result.success(paperAssetService.updateAnalysis(paperId, assetId, request)); }
    @PostMapping("/{assetId}/confirm") public Result<PaperAsset> confirm(@PathVariable Long paperId, @PathVariable Long assetId) { return Result.success(paperAssetService.confirm(paperId, assetId, true)); }
    @PostMapping("/{assetId}/reject") public Result<PaperAsset> reject(@PathVariable Long paperId, @PathVariable Long assetId) { return Result.success(paperAssetService.confirm(paperId, assetId, false)); }
    @PostMapping("/{assetId}/analyze") public Result<PaperAsset> analyze(@PathVariable Long paperId, @PathVariable Long assetId) { return Result.success(paperAssetService.analyze(paperId, assetId)); }
    @PostMapping("/analyze") public Result<Map<String, Object>> analyzeEligible(@PathVariable Long paperId) { return Result.success(paperAssetService.analyzeEligible(paperId)); }
    @GetMapping("/status") public Result<Map<String, Object>> status(@PathVariable Long paperId) { return Result.success(paperAssetService.latestAnalysisJob(paperId)); }
}
