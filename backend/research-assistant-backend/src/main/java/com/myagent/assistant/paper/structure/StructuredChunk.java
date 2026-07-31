package com.myagent.assistant.paper.structure;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 结构化 chunk 结果。
 */
@Data
@AllArgsConstructor
public class StructuredChunk {
    private String sectionTitle;
    private SectionType sectionType;
    private Integer sectionIndex;
    private String content;
    private String indexText;
    private Integer chunkIndex;
    private Integer tokenCount;
    private Integer charCount;
    private Boolean reference;
    private Boolean noise;
    private Double qualityScore;
    private String chunkStrategyVersion;
}
