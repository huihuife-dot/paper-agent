package com.myagent.assistant.idea.controller;

import com.myagent.assistant.idea.dto.ResearchIdeaCreateRequest;
import com.myagent.assistant.idea.dto.ResearchIdeaUpdateRequest;
import com.myagent.assistant.idea.dto.ResearchIdeaSaveTypeRequest;
import com.myagent.assistant.idea.dto.ResearchIdeaSaveTypeStatsResponse;
import com.myagent.assistant.idea.entity.ResearchIdea;
import com.myagent.assistant.idea.service.ResearchIdeaService;
import com.myagent.assistant.paper.common.Result;
import org.springframework.web.bind.annotation.*;
import com.myagent.assistant.idea.dto.ResearchIdeaDraftResponse;
import java.util.List;

/**
 * Research Idea 控制器。
 */
@RestController
public class ResearchIdeaController {

    private final ResearchIdeaService researchIdeaService;

    public ResearchIdeaController(ResearchIdeaService researchIdeaService) {
        this.researchIdeaService = researchIdeaService;
    }

    /**
     * 创建研究想法。
     *
     * POST /api/research-ideas
     */
    @PostMapping("/api/research-ideas")
    public Result<ResearchIdea> create(@RequestBody ResearchIdeaCreateRequest request) {
        return Result.success(researchIdeaService.create(request));
    }



    /**
     * 根据聊天会话生成 Research Idea 草稿。
     *
     * POST /api/research-ideas/draft-from-session/{sessionId}
     *
     * 注意：
     * 这里只生成草稿预览，不写入 research_idea 表。
     * 用户确认后，再调用 POST /api/research-ideas 保存。
     */
    @PostMapping("/api/research-ideas/draft-from-session/{sessionId}")
    public Result<ResearchIdeaDraftResponse> draftFromSession(@PathVariable Long sessionId) {
        return Result.success(researchIdeaService.draftFromSession(sessionId));
    }



    /**
     * 根据聊天会话生成 Research Idea 草稿，并直接保存为 draft。
     *
     * POST /api/research-ideas/save-draft-from-session/{sessionId}
     */
    @PostMapping("/api/research-ideas/save-draft-from-session/{sessionId}")
    public Result<ResearchIdea> saveDraftFromSession(@PathVariable Long sessionId) {
        return Result.success(researchIdeaService.saveDraftFromSession(sessionId));
    }



    /**
     * 根据 RAG 会话 ID 查询已经保存过的 Research Idea。
     *
     * GET /api/research-ideas/by-session/{sessionId}
     */
    @GetMapping("/api/research-ideas/by-session/{sessionId}")
    public Result<ResearchIdea> getBySourceSessionId(@PathVariable Long sessionId) {
        return Result.success(researchIdeaService.getBySourceSessionId(sessionId));
    }



    /**
     * 查询研究想法列表。
     *
     * GET /api/research-ideas?keyword=RAG&sourceType=rag_chat&saveType=draft&sourceSessionId=123
     */
    @GetMapping("/api/research-ideas")
    public Result<List<ResearchIdea>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "saveType", required = false) String saveType,
            @RequestParam(value = "sourceSessionId", required = false) Long sourceSessionId
    ) {
        return Result.success(researchIdeaService.list(keyword, sourceType, saveType, sourceSessionId));
    }

    /**
     * 查看研究想法详情。
     *
     * GET /api/research-ideas/{id}
     */
    @GetMapping("/api/research-ideas/{id}")
    public Result<ResearchIdea> getById(@PathVariable Long id) {
        return Result.success(researchIdeaService.getById(id));
    }

    /**
     * 更新研究想法。
     *
     * PUT /api/research-ideas/{id}
     */
    @PutMapping("/api/research-ideas/{id}")
    public Result<ResearchIdea> update(
            @PathVariable Long id,
            @RequestBody ResearchIdeaUpdateRequest request
    ) {
        return Result.success(researchIdeaService.update(id, request));
    }

    /**
     * 更新 Research Idea 保存类型。
     *
     * PATCH /api/research-ideas/{id}/save-type
     */
    @PatchMapping("/api/research-ideas/{id}/save-type")
    public Result<ResearchIdea> updateSaveType(
            @PathVariable Long id,
            @RequestBody ResearchIdeaSaveTypeRequest request
    ) {
        return Result.success(researchIdeaService.updateSaveType(id, request.getSaveType()));
    }

    /**
     * 按 saveType 统计 Research Idea 数量。
     *
     * GET /api/research-ideas/stats/save-type
     */
    @GetMapping("/api/research-ideas/stats/save-type")
    public Result<ResearchIdeaSaveTypeStatsResponse> countBySaveType() {
        return Result.success(researchIdeaService.countBySaveType());
    }

    /**
     * 删除研究想法。
     *
     * DELETE /api/research-ideas/{id}
     */
    @DeleteMapping("/api/research-ideas/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        researchIdeaService.delete(id);
        return Result.success(null);
    }
}