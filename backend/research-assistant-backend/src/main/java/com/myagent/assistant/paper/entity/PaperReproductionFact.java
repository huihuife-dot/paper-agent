package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可直接影响论文复现实现的原子事实。
 * MySQL 是事实主库，Qdrant 中的内容只是一份可重建索引。
 */
@Data
@TableName("paper_reproduction_fact")
public class PaperReproductionFact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private String factType;
    private String factKey;
    private String factValue;
    private String unit;
    private String conditionsJson;
    private String sourceKind;
    private Long sourceId;
    private Integer pageNumber;
    private String evidenceExcerpt;
    private Double confidence;
    private String verificationStatus;
    private String conflictGroup;
    private String supportingSourcesJson;
    private String extractorVersion;
    private String sourceRevision;
    private String qdrantPointId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
