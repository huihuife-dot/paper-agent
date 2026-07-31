package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.paper.dto.PaperProfileIndexResponse;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.qdrant.service.QdrantService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaperProfileIndexServiceImplTest {
    @Test
    void indexesExistingProfileAndSummariesIdempotentlyThroughUpsert() {
        PaperReferenceMapper papers = mock(PaperReferenceMapper.class);
        PaperProfileMapper profiles = mock(PaperProfileMapper.class);
        PaperSectionSummaryMapper summaries = mock(PaperSectionSummaryMapper.class);
        QdrantService qdrant = mock(QdrantService.class);
        PaperReference paper = new PaperReference(); paper.setId(13L);
        PaperProfile profile = new PaperProfile(); profile.setId(3L); profile.setPaperId(13L); profile.setProfileText("profile");
        PaperSectionSummary s1 = summary(51L, 13L); PaperSectionSummary s2 = summary(52L, 13L);
        when(papers.selectById(13L)).thenReturn(paper);
        when(profiles.selectOne(any(Wrapper.class))).thenReturn(profile);
        when(summaries.selectList(any(Wrapper.class))).thenReturn(List.of(s1, s2));

        PaperProfileIndexResponse result = new PaperProfileIndexServiceImpl(papers, profiles, summaries, qdrant)
                .indexProfile(13L);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getIndexedSectionSummaries()).isEqualTo(2);
        verify(qdrant).upsertProfilePoint(profile);
        verify(qdrant).upsertSummaryPoint(s1);
        verify(qdrant).upsertSummaryPoint(s2);
    }

    @Test
    void statusComparesQdrantCountsWithMysqlSummaryCount() {
        PaperReferenceMapper papers = mock(PaperReferenceMapper.class);
        PaperProfileMapper profiles = mock(PaperProfileMapper.class);
        PaperSectionSummaryMapper summaries = mock(PaperSectionSummaryMapper.class);
        QdrantService qdrant = mock(QdrantService.class);
        PaperReference paper = new PaperReference(); paper.setId(13L);
        when(papers.selectById(13L)).thenReturn(paper);
        when(summaries.selectList(any(Wrapper.class))).thenReturn(List.of(summary(51L, 13L), summary(52L, 13L)));
        when(qdrant.countPaperPoints(13L, "PAPER_PROFILE")).thenReturn(1L);
        when(qdrant.countPaperPoints(13L, "SECTION_SUMMARY")).thenReturn(2L);

        PaperProfileIndexResponse result = new PaperProfileIndexServiceImpl(papers, profiles, summaries, qdrant)
                .getIndexStatus(13L);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getProfileIndexed()).isTrue();
    }

    private PaperSectionSummary summary(Long id, Long paperId) {
        PaperSectionSummary summary = new PaperSectionSummary();
        summary.setId(id); summary.setPaperId(paperId); summary.setSummary("summary " + id);
        return summary;
    }
}
