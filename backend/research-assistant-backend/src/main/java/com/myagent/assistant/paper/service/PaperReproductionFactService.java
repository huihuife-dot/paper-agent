package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.entity.PaperReproductionFact;

import java.util.List;
import java.util.Map;

public interface PaperReproductionFactService {
    Map<String, Object> rebuild(Long paperId);
    List<PaperReproductionFact> list(Long paperId, boolean includeModelInferred);
    List<PaperReproductionFact> search(Long paperId, String query, Integer topK, boolean includeModelInferred);
    void invalidateForPaper(Long paperId);
}
