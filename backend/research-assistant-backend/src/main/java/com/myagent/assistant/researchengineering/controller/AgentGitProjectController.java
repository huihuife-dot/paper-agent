package com.myagent.assistant.researchengineering.controller;

import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.researchengineering.dto.AgentGitProjectResponse;
import com.myagent.assistant.researchengineering.dto.ExternalGitPrepareRequest;
import com.myagent.assistant.researchengineering.dto.GiteePublishRequest;
import com.myagent.assistant.researchengineering.service.AgentGitRemoteService;
import com.myagent.assistant.researchengineering.service.AgentGitService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/agent-git-projects")
public class AgentGitProjectController {
    private final AgentGitService gitService;
    private final AgentGitRemoteService remoteService;

    public AgentGitProjectController(AgentGitService gitService, AgentGitRemoteService remoteService) {
        this.gitService = gitService;
        this.remoteService = remoteService;
    }

    @GetMapping
    public Result<List<AgentGitProjectResponse>> list() { return Result.success(gitService.list()); }

    @GetMapping("/{mode}/{sourceId}")
    public Result<AgentGitProjectResponse> get(@PathVariable String mode, @PathVariable long sourceId) {
        return Result.success(gitService.get(mode, sourceId));
    }

    @PostMapping("/{mode}/{sourceId}/prepare-external")
    public Result<AgentGitProjectResponse> prepareExternal(@PathVariable String mode, @PathVariable long sourceId,
                                                            @RequestBody(required = false) ExternalGitPrepareRequest request) {
        return Result.success(gitService.prepareExternalRun(mode, sourceId, request == null ? null : request.workspacePath()));
    }

    @PostMapping("/{mode}/{sourceId}/gitee")
    public Result<AgentGitProjectResponse> publishGitee(@PathVariable String mode, @PathVariable long sourceId,
                                                        @RequestBody GiteePublishRequest request) {
        return Result.success(remoteService.publish(mode, sourceId, request));
    }

    @PostMapping("/{mode}/{sourceId}/refresh")
    public Result<AgentGitProjectResponse> refresh(@PathVariable String mode, @PathVariable long sourceId) {
        return Result.success(remoteService.refresh(mode, sourceId));
    }

    @PostMapping("/{mode}/{sourceId}/push")
    public Result<AgentGitProjectResponse> retryPush(@PathVariable String mode, @PathVariable long sourceId) {
        return Result.success(remoteService.retryPush(mode, sourceId));
    }

    @GetMapping("/{mode}/{sourceId}/delivery.zip")
    public ResponseEntity<FileSystemResource> delivery(@PathVariable String mode, @PathVariable long sourceId) {
        Path file = gitService.createDeliveryZip(mode, sourceId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.getFileName().toString(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(new FileSystemResource(file));
    }
}
