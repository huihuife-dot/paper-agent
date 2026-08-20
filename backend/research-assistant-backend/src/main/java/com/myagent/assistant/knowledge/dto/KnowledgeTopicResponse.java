package com.myagent.assistant.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class KnowledgeTopicResponse {
    private String knowledgeType;
    private String label;
    private Integer paperCount;
    private Integer unitCount;
    private List<Long> paperIds;
}
