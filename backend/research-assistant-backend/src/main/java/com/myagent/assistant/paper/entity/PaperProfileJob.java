package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文献画像异步生成任务。
 */
@Data
@TableName("paper_profile_job")
public class PaperProfileJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    /**
     * PENDING / PROCESSING / COMPLETED / FAILED
     */
    private String status;

    /**
     * WAITING / PREPARING / GENERATING_SECTIONS / GENERATING_PROFILE / SAVING_RESULT / COMPLETED / FAILED
     */
    private String currentStep;

    private Integer progressPercent;

    private Integer processedSections;

    private Integer totalSections;

    private Integer retryCount;

    private String errorMessage;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
