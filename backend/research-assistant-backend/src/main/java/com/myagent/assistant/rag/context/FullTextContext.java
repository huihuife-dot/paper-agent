package com.myagent.assistant.rag.context;

import com.myagent.assistant.rag.dto.RagSource;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 单篇论文全文解析上下文。
 */
@Data
@AllArgsConstructor
public class FullTextContext {
    private Long paperId;
    private String paperTitle;
    private String contextText;
    private List<RagSource> sources;
    private Integer tokenCount;
}
