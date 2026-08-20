package com.myagent.assistant.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一篇论文的低成本目录卡片与知识索引状态。
 */
@Data
@TableName("paper_catalog")
public class PaperCatalog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private String status;
    private String researchDomains;
    private String researchTasks;
    private String methodTags;
    private String datasetTags;
    private String metricTags;
    private String catalogText;
    private Integer knowledgeCount;
    private Integer verifiedCount;
    private String extractionVersion;
    private String modelProvider;
    private String modelName;
    private String errorMessage;
    private LocalDateTime buildTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
