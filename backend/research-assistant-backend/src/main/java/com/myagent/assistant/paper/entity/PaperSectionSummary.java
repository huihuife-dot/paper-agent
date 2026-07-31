package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论文章节摘要实体。
 */
@Data
@TableName("paper_section_summary")
public class PaperSectionSummary {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long sectionId;
    private String sectionType;
    private String sectionTitle;
    private String summary;
    private String keyPoints;
    private String sourceChunkIds;
    private Integer sourceTokenCount;
    private String summaryVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
