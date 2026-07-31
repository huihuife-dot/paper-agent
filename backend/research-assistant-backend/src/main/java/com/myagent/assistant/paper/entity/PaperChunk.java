package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文献切片实体。
 *
 * 一篇文献解析后会被切成多个 chunk。
 * chunk 原文保存在 MySQL 中，后续生成的向量会保存到 Qdrant。
 */
@Data
@TableName("paper_chunk")
public class PaperChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属文献 ID，对应 paper_reference.id。
     */
    private Long paperId;

    /**
     * 所属章节 ID，对应 paper_section.id。
     */
    private Long sectionId;

    /**
     * 章节标题冗余字段，便于 sources 展示和调试。
     */
    private String sectionTitle;

    /**
     * 标准章节类型，例如 METHOD、EXPERIMENT、REFERENCES。
     */
    private String sectionType;

    /**
     * 切片类型。
     * 第一阶段主要是 text，后期可以扩展 table、figure_description 等。
     */
    private String chunkType;

    /**
     * 切片文本内容。
     */
    private String content;

    /**
     * 用于向量化检索的文本。
     * 可以包含论文标题、章节类型、章节标题和清洗后的 chunk 内容。
     */
    private String indexText;

    /**
     * 页码。
     * 第一版可以先不精确记录页码，暂时为空。
     */
    private Integer pageNumber;

    private Integer pageStart;
    private Integer pageEnd;

    /**
     * 当前 chunk 在该文献中的序号。
     */
    private Integer chunkIndex;

    private Integer tokenCount;
    private Integer charCount;
    private Boolean isReference;
    private Boolean isNoise;
    private Double qualityScore;
    private String chunkStrategyVersion;

    /**
     * Qdrant 中对应的向量点 ID。
     * 当前解析阶段先为空，等向量化时再回写。
     */
    private String qdrantPointId;

    private LocalDateTime createTime;
}
