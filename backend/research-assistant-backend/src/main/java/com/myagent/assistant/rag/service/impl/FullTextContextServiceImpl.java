package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.rag.context.FullTextContext;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.service.FullTextContextService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单篇论文全文解析上下文构造服务实现。
 */
@Service
public class FullTextContextServiceImpl implements FullTextContextService {

    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperChunkMapper paperChunkMapper;
    private final int fullTextBudgetTokens;
    private final int fullTextMaxSources;

    public FullTextContextServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                      PaperChunkMapper paperChunkMapper,
                                      @Value("${rag.full-text-budget-tokens:60000}") int fullTextBudgetTokens,
                                      @Value("${rag.full-text-max-sources:20}") int fullTextMaxSources) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperChunkMapper = paperChunkMapper;
        this.fullTextBudgetTokens = fullTextBudgetTokens;
        this.fullTextMaxSources = fullTextMaxSources;
    }

    @Override
    public FullTextContext buildContext(Long paperId) {
        if (paperId == null || paperId <= 0) {
            throw new RuntimeException("paperId 不能为空");
        }

        PaperReference paper = paperReferenceMapper.selectById(paperId);
        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }

        List<PaperChunk> chunks = paperChunkMapper.selectList(
                new QueryWrapper<PaperChunk>()
                        .eq("paper_id", paperId)
                        .orderByAsc("chunk_index")
        );

        List<PaperChunk> usableChunks = chunks.stream()
                .filter(this::isUsableFullTextChunk)
                .toList();

        if (usableChunks.isEmpty()) {
            throw new RuntimeException("当前文献没有可用于全文上下文的正文 chunk");
        }

        StringBuilder context = new StringBuilder();
        context.append("论文ID：").append(paperId).append("\n");
        context.append("论文标题：").append(nullToDefault(paper.getTitle(), "")).append("\n\n");

        Map<String, List<PaperChunk>> chunksBySection = groupBySection(usableChunks);
        List<RagSource> sources = new ArrayList<>();
        int usedTokens = 0;

        for (List<PaperChunk> sectionChunks : chunksBySection.values()) {
            if (sectionChunks.isEmpty()) {
                continue;
            }

            PaperChunk first = sectionChunks.get(0);
            String sectionType = nullToDefault(first.getSectionType(), "UNKNOWN");
            String sectionTitle = nullToDefault(first.getSectionTitle(), "Unknown");
            StringBuilder sectionText = new StringBuilder();
            int sectionTokens = 0;

            for (PaperChunk chunk : sectionChunks) {
                int chunkTokens = tokenCount(chunk);
                if (usedTokens + sectionTokens + chunkTokens > fullTextBudgetTokens) {
                    break;
                }
                sectionText.append(normalize(chunk.getContent())).append("\n\n");
                sectionTokens += chunkTokens;
                if (sources.size() < fullTextMaxSources) {
                    sources.add(toSource(chunk, paper));
                }
            }

            if (sectionText.isEmpty()) {
                break;
            }

            context.append("[").append(sectionType).append("] ").append(sectionTitle).append("\n");
            context.append(sectionText).append("\n");
            usedTokens += sectionTokens;
        }

        return new FullTextContext(
                paperId,
                paper.getTitle(),
                context.toString().trim(),
                sources,
                usedTokens
        );
    }

    private Map<String, List<PaperChunk>> groupBySection(List<PaperChunk> chunks) {
        Map<String, List<PaperChunk>> result = new LinkedHashMap<>();
        for (PaperChunk chunk : chunks) {
            String key = chunk.getSectionId() != null
                    ? "section:" + chunk.getSectionId()
                    : "chunk:" + chunk.getChunkIndex();
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(chunk);
        }
        return result;
    }

    private RagSource toSource(PaperChunk chunk, PaperReference paper) {
        RagSource source = new RagSource();
        source.setPaperId(chunk.getPaperId());
        source.setPaperTitle(paper.getTitle());
        source.setChunkId(chunk.getId());
        source.setChunkIndex(chunk.getChunkIndex());
        source.setSectionId(chunk.getSectionId());
        source.setSectionTitle(chunk.getSectionTitle());
        source.setSectionType(chunk.getSectionType());
        source.setIsReference(chunk.getIsReference());
        source.setIsNoise(chunk.getIsNoise());
        source.setChunkStrategyVersion(chunk.getChunkStrategyVersion());
        source.setScore(null);
        source.setContent(chunk.getContent());
        source.setRetrievalRoute("full_text");
        return source;
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

    private String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("\\s+", " ").trim();
    }

    private String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
