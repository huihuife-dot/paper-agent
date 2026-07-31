package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("paper_multimodal_job")
public class PaperMultimodalJob {
    @TableId(type = IdType.AUTO) private Long id;
    private Long paperId;
    private String jobType;
    private String status;
    private Integer totalItems;
    private Integer processedItems;
    private Integer failedItems;
    private String provider;
    private String model;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
}
