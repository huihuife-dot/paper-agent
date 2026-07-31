package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.dto.PaperProfileStatusResponse;

import java.util.List;

/**
 * 文献画像状态查询服务。
 */
public interface PaperProfileStatusService {

    /**
     * 查询文献页所需的全部画像轻量状态。
     */
    List<PaperProfileStatusResponse> listProfileStatuses();
}
