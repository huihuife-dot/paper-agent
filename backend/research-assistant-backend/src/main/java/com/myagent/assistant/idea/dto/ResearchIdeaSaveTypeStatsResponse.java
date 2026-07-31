package com.myagent.assistant.idea.dto;

import lombok.Data;

/**
 * Research Idea 保存类型统计响应。
 */
@Data
public class ResearchIdeaSaveTypeStatsResponse {

    /**
     * 草稿数量。
     */
    private Long draft;

    /**
     * 正式研究想法数量。
     */
    private Long idea;

    /**
     * 待执行 / 待实验数量。
     */
    private Long todo;

    /**
     * 已实现 / 已完成数量。
     */
    private Long implemented;

    /**
     * 总数量。
     */
    private Long total;
}
