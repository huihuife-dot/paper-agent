package com.myagent.assistant.rag.context;

import com.myagent.assistant.rag.dto.EvidenceQueryPlan;
import com.myagent.assistant.rag.dto.RagSource;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StructuredEvidenceContext {
    private boolean handled;
    private String contextStrategy;
    private String contextText;
    private Integer tokenCount;
    private List<Long> paperIds = new ArrayList<>();
    private List<RagSource> sources = new ArrayList<>();
    private EvidenceQueryPlan plan;
}
