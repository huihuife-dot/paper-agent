package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.context.StructuredEvidenceContext;

import java.util.List;

public interface StructuredEvidenceService {
    StructuredEvidenceContext build(String question, List<Long> paperIds, int topK);
}
