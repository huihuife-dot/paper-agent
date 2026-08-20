package com.myagent.assistant.writing.dto;

import com.myagent.assistant.writing.entity.WritingProject;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class WritingProjectResponse {
    private WritingProject project;
    private List<Long> selectedPaperIds = new ArrayList<>();
    private List<WritingPaperResponse> selectedPapers = new ArrayList<>();
    private List<WritingCitationResponse> citations = new ArrayList<>();
    private WritingCitationAuditResponse citationAudit = new WritingCitationAuditResponse();
}
