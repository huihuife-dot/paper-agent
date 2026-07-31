package com.myagent.assistant.paper.controller;

import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.paper.entity.PaperReproductionFact;
import com.myagent.assistant.paper.service.PaperReproductionFactService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/papers/{paperId}/reproduction-facts")
public class PaperReproductionFactController {
    private final PaperReproductionFactService service;

    public PaperReproductionFactController(PaperReproductionFactService service) {
        this.service = service;
    }

    @PostMapping("/rebuild")
    public Result<Map<String, Object>> rebuild(@PathVariable Long paperId) {
        return Result.success(service.rebuild(paperId));
    }

    @GetMapping
    public Result<List<PaperReproductionFact>> list(
            @PathVariable Long paperId,
            @RequestParam(defaultValue = "false") boolean includeModelInferred) {
        return Result.success(service.list(paperId, includeModelInferred));
    }

    @GetMapping("/search")
    public Result<List<PaperReproductionFact>> search(
            @PathVariable Long paperId,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") Integer topK,
            @RequestParam(defaultValue = "false") boolean includeModelInferred) {
        return Result.success(service.search(paperId, query, topK, includeModelInferred));
    }
}
