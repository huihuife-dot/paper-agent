package com.myagent.assistant.knowledge.dto;

import lombok.Data;

/**
 * LLM 抽取阶段使用的固定 JSON Schema。
 */
@Data
public class ExtractedKnowledgeUnit {
    private String knowledgeType;
    private Long sectionId;
    private String subject;
    private String predicate;
    private String objectValue;
    private String valueUnit;
    private String applicableCondition;
    private String evidenceQuote;
}
