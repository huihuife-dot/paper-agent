package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.dto.PaperProfileIndexResponse;

public interface PaperProfileIndexService {
    PaperProfileIndexResponse indexProfile(Long paperId);
    PaperProfileIndexResponse getIndexStatus(Long paperId);
}
