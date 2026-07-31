package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.reproduction.ReproductionSpecDocument;

public interface PaperReproductionSpecService {
    ReproductionSpecDocument rebuild(Long paperId);
    ReproductionSpecDocument get(Long paperId);
    ReproductionSpecDocument getOrBuild(Long paperId);
    void invalidateForPaper(Long paperId);
}
