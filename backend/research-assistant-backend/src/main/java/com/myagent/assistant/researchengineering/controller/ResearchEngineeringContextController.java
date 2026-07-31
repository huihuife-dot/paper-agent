package com.myagent.assistant.researchengineering.controller;

import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.researchengineering.dto.IdeaImprovementContextResponse;
import com.myagent.assistant.researchengineering.dto.PaperReproductionContextResponse;
import com.myagent.assistant.researchengineering.service.ResearchEngineeringContextService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/** Read-only endpoints used by a local Research Engineering Agent. */
@RestController
public class ResearchEngineeringContextController {
    private final ResearchEngineeringContextService contextService;

    public ResearchEngineeringContextController(ResearchEngineeringContextService contextService) {
        this.contextService = contextService;
    }

    @GetMapping("/api/research-ideas/{id}/improvement-context")
    public Result<IdeaImprovementContextResponse> improvementContext(@PathVariable Long id) {
        return Result.success(contextService.getIdeaImprovementContext(id));
    }

    @GetMapping("/api/papers/{id}/reproduction-context")
    public Result<PaperReproductionContextResponse> reproductionContext(
            @PathVariable Long id,
            @RequestParam(defaultValue = "2") int version) {
        return Result.success(contextService.getPaperReproductionContext(id, version));
    }
}
