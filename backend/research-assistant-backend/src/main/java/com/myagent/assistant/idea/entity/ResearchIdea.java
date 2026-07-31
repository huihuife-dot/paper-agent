package com.myagent.assistant.idea.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Research Idea 实体。
 *
 * 用于保存从论文阅读、RAG 问答、人工输入中沉淀出的研究想法。
 */
@Data
@TableName("research_idea")
public class ResearchIdea {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 研究想法标题。
     */
    private String title;

    /**
     * 原始内容。
     * 可以是用户手动记录，也可以来自某次 RAG 回答。
     */
    private String originalContent;

    /**
     * 整理后的内容。
     * 后续可由大模型润色生成。
     */
    private String refinedContent;

    /**
     * 创新点。
     */
    private String innovationPoints;

    /**
     * 研究问题。
     */
    private String researchQuestion;

    /**
     * 可能的方法。
     */
    private String possibleMethod;

    /**
     * 标签，先用逗号分隔字符串保存。
     */
    private String tags;

    /**
     * 来源类型。
     * 例如：manual、rag_chat、paper。
     */
    private String sourceType;

    /**
     * 保存类型。
     * 例如：draft、idea、todo。
     */
    private String saveType;

    /**
     * 来源会话 ID。
     */
    private Long sourceSessionId;

    /**
     * 来源消息 ID。
     */
    private Long sourceMessageId;

    /**
     * 关联文献 ID 列表。
     * 先用逗号分隔字符串保存，例如：1,2,3。
     */
    private String relatedPaperIds;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}