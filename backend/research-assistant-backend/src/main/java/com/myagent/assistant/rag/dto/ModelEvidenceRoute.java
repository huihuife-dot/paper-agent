package com.myagent.assistant.rag.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型只负责输出受约束的取证分类，不直接生成答案或改变论文范围。
 */
@Data
public class ModelEvidenceRoute {
    private String intent;
    private List<String> knowledgeTypes = new ArrayList<>();
    private List<String> sectionTypes = new ArrayList<>();
    private Double confidence;
    private String explanation;
}
