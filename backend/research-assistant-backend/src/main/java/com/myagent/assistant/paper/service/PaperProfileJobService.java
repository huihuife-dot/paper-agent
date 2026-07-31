package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.dto.PaperProfileJobResponse;

/**
 * 文献画像异步任务服务。
 */
public interface PaperProfileJobService {

    /**
     * 启动文献画像异步生成任务；如果已有运行中任务，则直接返回该任务。
     */
    PaperProfileJobResponse startProfileJob(Long paperId);

    /**
     * 查询指定文献最新的画像生成任务。
     */
    PaperProfileJobResponse getLatestJob(Long paperId);
}
