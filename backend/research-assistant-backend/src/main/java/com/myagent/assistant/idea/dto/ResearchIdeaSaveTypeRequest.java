package com.myagent.assistant.idea.dto;

import lombok.Data;

/**
 * Research Idea 保存类型更新请求。
 */
@Data
public class ResearchIdeaSaveTypeRequest {

    /**
     * 保存类型。
     *
     * 当前支持：
     * draft：草稿
     * idea：正式研究想法
     * todo：待执行任务
     * implemented：已实现 / 已完成
     */
    private String saveType;
}
