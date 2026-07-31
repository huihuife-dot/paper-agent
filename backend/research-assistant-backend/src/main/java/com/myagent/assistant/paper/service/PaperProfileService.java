package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.dto.PaperProfileResult;

/**
 * 文献画像服务。
 */
public interface PaperProfileService {

    /**
     * 为指定已解析论文生成或更新文献画像。
     */
    PaperProfileResult generateProfile(Long paperId);

    /**
     * 为指定已解析论文生成或更新文献画像，并通过 listener 汇报后台任务进度。
     */
    PaperProfileResult generateProfile(Long paperId, PaperProfileProgressListener listener);

    /**
     * 查询指定论文已生成的文献画像。
     */
    PaperProfileResult getProfile(Long paperId);
}
