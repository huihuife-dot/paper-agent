package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.rag.context.ContextStrategy;
import com.myagent.assistant.rag.service.ContextStrategyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 上下文策略选择服务实现。
 */
@Service
public class ContextStrategyServiceImpl implements ContextStrategyService {

    private static final String PAPER_PROFILE_VERSION = "paper-profile-v1";

    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperChunkMapper paperChunkMapper;
    private final PaperProfileMapper paperProfileMapper;
    private final int fullTextBudgetTokens;

    public ContextStrategyServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                      PaperChunkMapper paperChunkMapper,
                                      PaperProfileMapper paperProfileMapper,
                                      @Value("${rag.full-text-budget-tokens:60000}") int fullTextBudgetTokens) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperChunkMapper = paperChunkMapper;
        this.paperProfileMapper = paperProfileMapper;
        this.fullTextBudgetTokens = fullTextBudgetTokens;
    }

    @Override
    public ContextStrategy chooseStrategy(List<Long> paperIds) {
        List<Long> normalizedPaperIds = normalizePaperIds(paperIds);
        if (normalizedPaperIds.isEmpty()) {
            // 未指定任何论文 → 全库文献发现模式
            return ContextStrategy.LIBRARY_DISCOVERY;
        }
        if (normalizedPaperIds.size() >= 2) {
            return hasProfiles(normalizedPaperIds) ? ContextStrategy.HYBRID_RAG : ContextStrategy.VECTOR_RAG;
        }
        if (normalizedPaperIds.size() != 1) {
            return ContextStrategy.VECTOR_RAG;
        }

        Long paperId = normalizedPaperIds.get(0);
        PaperReference paper = paperReferenceMapper.selectById(paperId);
        if (paper == null || !"COMPLETED".equals(paper.getParseStatus())) {
            return ContextStrategy.VECTOR_RAG;
        }

        List<PaperChunk> chunks = paperChunkMapper.selectList(
                new QueryWrapper<PaperChunk>()
                        .eq("paper_id", paperId)
                        .orderByAsc("chunk_index")
        );

        int tokenCount = chunks.stream()
                .filter(this::isUsableFullTextChunk)
                .mapToInt(this::tokenCount)
                .sum();

        if (tokenCount <= 0 || tokenCount > fullTextBudgetTokens) {
            return ContextStrategy.VECTOR_RAG;
        }

        return ContextStrategy.FULL_TEXT_PARSED;
    }

    private boolean hasProfiles(List<Long> paperIds) {
        for (Long paperId : paperIds) {
            PaperProfile profile = paperProfileMapper.selectOne(new QueryWrapper<PaperProfile>()
                    .eq("paper_id", paperId)
                    .eq("profile_version", PAPER_PROFILE_VERSION));
            if (profile == null) {
                return false;
            }
        }
        return true;
    }

    private boolean isUsableFullTextChunk(PaperChunk chunk) {
        if (chunk == null) {
            return false;
        }
        if (Boolean.TRUE.equals(chunk.getIsReference()) || Boolean.TRUE.equals(chunk.getIsNoise())) {
            return false;
        }
        String sectionType = chunk.getSectionType();
        return !"REFERENCES".equals(sectionType) && !"BACK_MATTER".equals(sectionType);
    }

    private int tokenCount(PaperChunk chunk) {
        if (chunk.getTokenCount() != null && chunk.getTokenCount() > 0) {
            return chunk.getTokenCount();
        }
        String content = chunk.getContent() != null ? chunk.getContent() : "";
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private List<Long> normalizePaperIds(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return List.of();
        }
        return paperIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }
}
