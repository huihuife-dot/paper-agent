package com.myagent.assistant.rag.context;

import com.myagent.assistant.rag.dto.RagSource;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 多篇论文混合 RAG 上下文。
 */
@Data
@AllArgsConstructor
public class HybridRagContext {
    private String contextText;
    private List<RagSource> sources;
    private Integer tokenCount;
    private List<Long> paperIds;
}
