package com.myagent.assistant.rag.context;

import com.myagent.assistant.rag.dto.RagSource;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 多篇论文混合 RAG 上下文构造请求。
 */
@Data
@AllArgsConstructor
public class HybridRagContextRequest {
    private String question;
    private List<Long> paperIds;
    private List<RagSource> rawChunkSources;
    private Integer maxSectionSummariesPerPaper;
    private Integer maxRawChunksPerPaper;
}
