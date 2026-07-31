package com.myagent.assistant.researchengineering.service;

import com.myagent.assistant.researchengineering.dto.IdeaImprovementContextResponse;
import com.myagent.assistant.researchengineering.dto.PaperReproductionContextResponse;

/** Aggregates existing MyAgent data into the two local-agent input contracts. */
public interface ResearchEngineeringContextService {
    IdeaImprovementContextResponse getIdeaImprovementContext(Long ideaId);

    PaperReproductionContextResponse getPaperReproductionContext(Long paperId);

    PaperReproductionContextResponse getPaperReproductionContext(Long paperId, int protocolVersion);
}
