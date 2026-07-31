package com.myagent.assistant.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 模型开始生成前发送给前端的 RAG 检索元数据。
 */
@Data
@AllArgsConstructor
public class RagStreamMetadata {

    private String contextStrategy;
    private String retrievalQuestion;
    private Integer contextTokenCount;
    private List<Long> contextPaperIds;
    private List<RagSource> sources;
    private String modelProvider;
    private String modelName;
}
