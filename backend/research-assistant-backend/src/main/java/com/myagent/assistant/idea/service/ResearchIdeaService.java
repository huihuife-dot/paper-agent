package com.myagent.assistant.idea.service;

import com.myagent.assistant.idea.dto.ResearchIdeaCreateRequest;
import com.myagent.assistant.idea.dto.ResearchIdeaUpdateRequest;
import com.myagent.assistant.idea.dto.ResearchIdeaSaveTypeStatsResponse;
import com.myagent.assistant.idea.entity.ResearchIdea;
import com.myagent.assistant.idea.dto.ResearchIdeaDraftResponse;
import java.util.List;

public interface ResearchIdeaService {

    /**
     * 创建研究想法。
     */
    ResearchIdea create(ResearchIdeaCreateRequest request);


    /**
     * 根据某个聊天会话生成 Research Idea 草稿。
     *
     * 只生成预览，不写入 research_idea 表。
     * 用户确认后，再调用 create(...) 保存。
     */
    ResearchIdeaDraftResponse draftFromSession(Long sessionId);


    /**
     * 根据某个聊天会话生成 Research Idea 草稿，并直接保存为 draft。
     *
     * 用于 /api/rag/chat 返回 suggestSaveAsIdea=true 后的一键保存场景。
     */
    ResearchIdea saveDraftFromSession(Long sessionId);


    /**
     * 根据 RAG 会话 ID 查询已经保存过的 Research Idea。
     *
     * 如果该会话还没有保存过 Idea，则返回 null。
     */
    ResearchIdea getBySourceSessionId(Long sessionId);


    /**
     * 查询研究想法列表。
     *
     * keyword 可选，用于按 title / tags 简单搜索；
     * sourceType、saveType、sourceSessionId 可选，用于组合筛选来源、状态和会话。
     */
    List<ResearchIdea> list(String keyword, String sourceType, String saveType, Long sourceSessionId);

    /**
     * 查看研究想法详情。
     */
    ResearchIdea getById(Long id);

    /**
     * 更新研究想法。
     */
    ResearchIdea update(Long id, ResearchIdeaUpdateRequest request);

    /**
     * 更新 Research Idea 保存类型。
     *
     * 当前支持：draft、idea、todo、implemented。
     */
    ResearchIdea updateSaveType(Long id, String saveType);

    /**
     * 按保存类型统计 Research Idea 数量。
     */
    ResearchIdeaSaveTypeStatsResponse countBySaveType();

    /**
     * 删除研究想法。
     */
    void delete(Long id);
}