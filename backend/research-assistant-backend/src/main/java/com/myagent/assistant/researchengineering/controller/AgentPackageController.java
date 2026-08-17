package com.myagent.assistant.researchengineering.controller;

import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.researchengineering.dto.AgentPackageResponse;
import com.myagent.assistant.researchengineering.service.AgentPackageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/agent-packages")
public class AgentPackageController {
    private final AgentPackageService packageService;

    public AgentPackageController(AgentPackageService packageService) { this.packageService = packageService; }

    @PostMapping("/papers/{paperId}")
    public Result<AgentPackageResponse> createPaper(@PathVariable long paperId) {
        return Result.success(packageService.createPaperPackage(paperId));
    }

    @PostMapping("/ideas/{ideaId}")
    public Result<AgentPackageResponse> createIdea(@PathVariable long ideaId) {
        return Result.success(packageService.createIdeaPackage(ideaId));
    }

    @GetMapping("/{packageId}/download")
    public ResponseEntity<Resource> download(@PathVariable String packageId) {
        Resource resource = packageService.packageResource(packageId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(packageId + ".zip", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
