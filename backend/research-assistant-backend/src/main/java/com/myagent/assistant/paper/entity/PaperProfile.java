package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 整篇文献画像实体。
 */
@Data
@TableName("paper_profile")
public class PaperProfile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private String title;
    private String researchProblem;
    private String methodSummary;
    private String experimentSummary;
    private String keyContributions;
    private String limitations;
    private String keywords;
    private String profileText;
    private String sourceSectionSummaryIds;
    private String profileVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
