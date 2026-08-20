package com.myagent.assistant.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户对知识单元的确认、纠正或拒绝记录。
 */
@Data
@TableName("paper_knowledge_feedback")
public class PaperKnowledgeFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long unitId;
    private Long paperId;
    private String actionType;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String commentText;
    private LocalDateTime createTime;
}
