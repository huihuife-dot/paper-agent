package com.myagent.assistant.writing.service;

import com.myagent.assistant.writing.dto.*;
import com.myagent.assistant.writing.entity.WritingRevision;
import java.util.List;

public interface WritingProjectService {
    WritingProjectResponse create(WritingProjectCreateRequest request);
    List<WritingProjectResponse> list();
    WritingProjectResponse get(Long id);
    WritingProjectResponse update(Long id, WritingProjectUpdateRequest request);
    void delete(Long id);
    WritingProjectResponse generateOutline(Long id, WritingGenerateRequest request);
    WritingProjectResponse generateDraft(Long id, WritingGenerateRequest request);
    WritingProjectResponse revise(Long id, WritingReviseRequest request);
    List<WritingRevision> listRevisions(Long id);
    WritingProjectResponse restoreRevision(Long id, Long revisionId);
}
