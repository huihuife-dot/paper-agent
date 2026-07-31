package com.myagent.assistant.paper.controller;

import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.paper.reproduction.ReproductionSpecDocument;
import com.myagent.assistant.paper.service.PaperReproductionSpecService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/papers/{paperId}/reproduction-spec")
public class PaperReproductionSpecController {
    private final PaperReproductionSpecService service;

    public PaperReproductionSpecController(PaperReproductionSpecService service) {
        this.service = service;
    }

    @GetMapping
    public Result<ReproductionSpecDocument> get(@PathVariable Long paperId) {
        return Result.success(service.get(paperId));
    }

    @PostMapping("/rebuild")
    public Result<ReproductionSpecDocument> rebuild(@PathVariable Long paperId) {
        return Result.success(service.rebuild(paperId));
    }
}
