package com.myagent.assistant.writing.controller;

import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.writing.dto.*;
import com.myagent.assistant.writing.entity.WritingRevision;
import com.myagent.assistant.writing.service.WritingProjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/writing-projects")
public class WritingProjectController {
    private final WritingProjectService writingProjectService;

    public WritingProjectController(WritingProjectService writingProjectService) {
        this.writingProjectService = writingProjectService;
    }

    @PostMapping
    public Result<WritingProjectResponse> create(@RequestBody WritingProjectCreateRequest request) {
        return Result.success(writingProjectService.create(request));
    }

    @GetMapping
    public Result<List<WritingProjectResponse>> list() {
        return Result.success(writingProjectService.list());
    }

    @GetMapping("/{id}")
    public Result<WritingProjectResponse> get(@PathVariable Long id) {
        return Result.success(writingProjectService.get(id));
    }

    @PutMapping("/{id}")
    public Result<WritingProjectResponse> update(@PathVariable Long id,
                                                 @RequestBody WritingProjectUpdateRequest request) {
        return Result.success(writingProjectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        writingProjectService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/outline")
    public Result<WritingProjectResponse> generateOutline(@PathVariable Long id,
                                                           @RequestBody(required = false) WritingGenerateRequest request) {
        return Result.success(writingProjectService.generateOutline(id, request));
    }

    @PostMapping("/{id}/draft")
    public Result<WritingProjectResponse> generateDraft(@PathVariable Long id,
                                                         @RequestBody(required = false) WritingGenerateRequest request) {
        return Result.success(writingProjectService.generateDraft(id, request));
    }

    @PostMapping("/{id}/revise")
    public Result<WritingProjectResponse> revise(@PathVariable Long id,
                                                  @RequestBody WritingReviseRequest request) {
        return Result.success(writingProjectService.revise(id, request));
    }

    @GetMapping("/{id}/revisions")
    public Result<List<WritingRevision>> revisions(@PathVariable Long id) {
        return Result.success(writingProjectService.listRevisions(id));
    }

    @PostMapping("/{id}/revisions/{revisionId}/restore")
    public Result<WritingProjectResponse> restore(@PathVariable Long id, @PathVariable Long revisionId) {
        return Result.success(writingProjectService.restoreRevision(id, revisionId));
    }
}
