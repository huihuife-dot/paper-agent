package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论文章节实体。
 *
 * 作为 paper_chunk 的父级结构，用于保存论文中的摘要、方法、实验、结论等章节信息。
 */
@Data
@TableName("paper_section")
public class PaperSection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;
    private String sectionTitle;
    private String sectionType;
    private Integer sectionIndex;
    private Integer pageStart;
    private Integer pageEnd;
    private String contentPreview;
    private LocalDateTime createTime;
}
