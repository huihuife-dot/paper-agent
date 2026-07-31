package com.myagent.assistant.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.rag.context.ContextStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextStrategyServiceImplTest {

    @Test
    void chooseFullTextForSingleParsedPaperWithinBudget() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);

        PaperReference paper = new PaperReference();
        paper.setId(7L);
        paper.setParseStatus("COMPLETED");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);

        PaperChunk chunk = new PaperChunk();
        chunk.setTokenCount(1000);
        chunk.setIsReference(false);
        chunk.setIsNoise(false);
        chunk.setSectionType("METHOD");
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chunk));

        ContextStrategyServiceImpl service = new ContextStrategyServiceImpl(
                paperReferenceMapper,
                paperChunkMapper,
                mock(PaperProfileMapper.class),
                60000
        );

        assertThat(service.chooseStrategy(List.of(7L))).isEqualTo(ContextStrategy.FULL_TEXT_PARSED);
    }

    @Test
    void chooseHybridRagForMultiplePapersWhenProfilesExist() {
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperProfile profile = new PaperProfile();
        profile.setProfileVersion("paper-profile-v1");
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(profile);

        ContextStrategyServiceImpl service = new ContextStrategyServiceImpl(
                mock(PaperReferenceMapper.class),
                mock(PaperChunkMapper.class),
                paperProfileMapper,
                60000
        );

        assertThat(service.chooseStrategy(List.of(7L, 8L))).isEqualTo(ContextStrategy.HYBRID_RAG);
    }

    @Test
    void chooseVectorRagForMultiplePapersWhenAnyProfileMissing() {
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperProfile profile = new PaperProfile();
        profile.setProfileVersion("paper-profile-v1");
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(profile, null);

        ContextStrategyServiceImpl service = new ContextStrategyServiceImpl(
                mock(PaperReferenceMapper.class),
                mock(PaperChunkMapper.class),
                paperProfileMapper,
                60000
        );

        assertThat(service.chooseStrategy(List.of(7L, 8L))).isEqualTo(ContextStrategy.VECTOR_RAG);
    }

    @Test
    void chooseVectorRagWhenPaperNotParsed() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);

        PaperReference paper = new PaperReference();
        paper.setId(7L);
        paper.setParseStatus("FAILED");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);

        ContextStrategyServiceImpl service = new ContextStrategyServiceImpl(
                paperReferenceMapper,
                mock(PaperChunkMapper.class),
                mock(PaperProfileMapper.class),
                60000
        );

        assertThat(service.chooseStrategy(List.of(7L))).isEqualTo(ContextStrategy.VECTOR_RAG);
    }

    @Test
    void chooseVectorRagWhenTokenBudgetExceeded() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);

        PaperReference paper = new PaperReference();
        paper.setId(7L);
        paper.setParseStatus("COMPLETED");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);

        PaperChunk chunk = new PaperChunk();
        chunk.setTokenCount(70000);
        chunk.setIsReference(false);
        chunk.setIsNoise(false);
        chunk.setSectionType("METHOD");
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chunk));

        ContextStrategyServiceImpl service = new ContextStrategyServiceImpl(
                paperReferenceMapper,
                paperChunkMapper,
                mock(PaperProfileMapper.class),
                60000
        );

        assertThat(service.chooseStrategy(List.of(7L))).isEqualTo(ContextStrategy.VECTOR_RAG);
    }
}
