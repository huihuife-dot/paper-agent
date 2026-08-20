package com.myagent.assistant.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 可用于评测或后续训练准备的“证据 -> 结构化知识”样本。
 * 只导出仍然保留来源的 SILVER/GOLD 数据，不把无证据模型推断当真值。
 */
@Data
@AllArgsConstructor
public class KnowledgeDatasetItem {
    private Long unitId;
    private Long paperId;
    private String knowledgeType;
    private String evidenceText;
    private String structuredTarget;
    private String confidenceLevel;
    private String verificationStatus;
    private String extractionVersion;
}
