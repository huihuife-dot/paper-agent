package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.rag.context.FullTextContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FullTextContextServiceImplTest {

    @Test
    void buildContextGroupsChunksBySectionAndFiltersNoise() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);

        PaperReference paper = new PaperReference();
        paper.setId(7L);
        paper.setTitle("Wind Paper");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);

        PaperChunk method = chunk(1L, 7L, 10L, "METHOD", "Proposed Method", 0, "method content", false, false, 10);
        PaperChunk experiment = chunk(2L, 7L, 20L, "EXPERIMENT", "Experiments", 1, "experiment content", false, false, 12);
        PaperChunk references = chunk(3L, 7L, 30L, "REFERENCES", "References", 2, "reference content", true, false, 8);
        PaperChunk backMatter = chunk(4L, 7L, 40L, "BACK_MATTER", "Funding", 3, "funding content", false, true, 8);

        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(method, experiment, references, backMatter));

        FullTextContextServiceImpl service = new FullTextContextServiceImpl(
                paperReferenceMapper,
                paperChunkMapper,
                60000,
                20
        );

        FullTextContext context = service.buildContext(7L);

        assertThat(context.getPaperId()).isEqualTo(7L);
        assertThat(context.getPaperTitle()).isEqualTo("Wind Paper");
        assertThat(context.getContextText()).contains("[METHOD] Proposed Method");
        assertThat(context.getContextText()).contains("method content");
        assertThat(context.getContextText()).contains("[EXPERIMENT] Experiments");
        assertThat(context.getContextText()).doesNotContain("reference content");
        assertThat(context.getContextText()).doesNotContain("funding content");
        assertThat(context.getSources()).hasSize(2);
        assertThat(context.getSources()).extracting("retrievalRoute").containsOnly("full_text");
        assertThat(context.getTokenCount()).isEqualTo(22);
    }

    private PaperChunk chunk(Long id,
                             Long paperId,
                             Long sectionId,
                             String sectionType,
                             String sectionTitle,
                             Integer chunkIndex,
                             String content,
                             boolean reference,
                             boolean noise,
                             int tokenCount) {
        PaperChunk chunk = new PaperChunk();
        chunk.setId(id);
        chunk.setPaperId(paperId);
        chunk.setSectionId(sectionId);
        chunk.setSectionType(sectionType);
        chunk.setSectionTitle(sectionTitle);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setIsReference(reference);
        chunk.setIsNoise(noise);
        chunk.setTokenCount(tokenCount);
        chunk.setChunkStrategyVersion("paper-structure-v1");
        return chunk;
    }
}
