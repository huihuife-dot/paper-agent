package com.myagent.assistant.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 从论文中提取的一条可查询、可回溯的学术知识。
 */
@Data
@TableName("paper_knowledge_unit")
public class PaperKnowledgeUnit {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long sectionId;
    private Long chunkId;
    private String knowledgeType;
    private String subjectText;
    private String predicateText;
    private String objectValue;
    private String valueUnit;
    private String applicableCondition;
    private String normalizedKey;
    private String conflictGroupKey;
    private Boolean hasConflict;
    private String sourceType;
    private Long sourceId;
    private Integer pageNumber;
    private String evidenceText;
    private String confidenceLevel;
    private String verificationStatus;
    private String extractionMethod;
    private String extractionVersion;
    private String modelProvider;
    private String modelName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
