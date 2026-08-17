package com.myagent.assistant.researchengineering.controller;

import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.researchengineering.dto.AgentExecutionRequest;
import com.myagent.assistant.researchengineering.dto.AgentExecutionStatusResponse;
import com.myagent.assistant.researchengineering.service.AgentExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-executions")
public class AgentExecutionController {
    private final AgentExecutionService executionService;

    public AgentExecutionController(AgentExecutionService executionService) { this.executionService = executionService; }

    @PostMapping("/papers/{paperId}")
    public Result<AgentExecutionStatusResponse> startPaper(@PathVariable long paperId, @RequestBody(required = false) AgentExecutionRequest request) {
        return Result.success(executionService.startPaper(paperId, request));
    }

    @GetMapping("/papers/{paperId}")
    public Result<AgentExecutionStatusResponse> paperStatus(@PathVariable long paperId) {
        return Result.success(executionService.status("paper", paperId));
    }

    @PostMapping("/papers/{paperId}/stop")
    public Result<AgentExecutionStatusResponse> stopPaper(@PathVariable long paperId) {
        return Result.success(executionService.stop("paper", paperId));
    }

    @PostMapping("/ideas/{ideaId}")
    public Result<AgentExecutionStatusResponse> startIdea(@PathVariable long ideaId, @RequestBody AgentExecutionRequest request) {
        return Result.success(executionService.startIdea(ideaId, request));
    }

    @GetMapping("/ideas/{ideaId}")
    public Result<AgentExecutionStatusResponse> ideaStatus(@PathVariable long ideaId) {
        return Result.success(executionService.status("idea", ideaId));
    }

    @PostMapping("/ideas/{ideaId}/stop")
    public Result<AgentExecutionStatusResponse> stopIdea(@PathVariable long ideaId) {
        return Result.success(executionService.stop("idea", ideaId));
    }
}
