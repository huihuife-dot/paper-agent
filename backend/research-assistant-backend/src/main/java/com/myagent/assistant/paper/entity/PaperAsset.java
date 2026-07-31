package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("paper_asset")
public class PaperAsset {
    @TableId(type = IdType.AUTO) private Long id;
    private Long paperId;
    private String assetType;
    private String assetLabel;
    private String caption;
    private Integer pageStart;
    private Integer pageEnd;
    private String boundingBoxJson;
    private String rawAssetPath;
    private String rawText;
    private String structuredContentJson;
    private String semanticDescription;
    private String analysisVersion;
    private String extractionMethod;
    private Double extractionConfidence;
    private String verificationStatus;
    private String contentHash;
    private String parserVersion;
    private String sourceRevision;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
