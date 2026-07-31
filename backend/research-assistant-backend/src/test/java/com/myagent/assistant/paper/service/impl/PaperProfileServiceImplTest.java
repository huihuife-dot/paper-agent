package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.paper.dto.PaperProfileResult;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSection;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.paper.service.PaperProfileProgressListener;
import com.myagent.assistant.qdrant.service.QdrantService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperProfileServiceImplTest {

    @Test
    void generateProfileRejectsUnparsedPaper() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperReference paper = paper(7L, "Wind Paper", "PENDING");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);
        PaperProfileServiceImpl service = createService(paperReferenceMapper);

        assertThatThrownBy(() -> service.generateProfile(7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("请先解析 PDF 后再生成文献画像");
    }

    @Test
    void generateProfileFiltersReferenceAndNoiseChunks() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        LlmService llmService = mock(LlmService.class);
        QdrantService qdrantService = mock(QdrantService.class);

        PaperReference paper = paper(7L, "Wind Paper", "COMPLETED");
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper);
        PaperSection method = section(10L, 7L, "METHOD", "Method", 0);
        when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(method));
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                chunk(1L, 7L, 10L, "METHOD", "Method", 0, "method content explains the proposed model", false, false, 10),
                chunk(2L, 7L, 10L, "REFERENCES", "References", 1, "reference list", true, false, 5),
                chunk(3L, 7L, 10L, "BACK_MATTER", "Funding", 2, "funding text", false, true, 5)
        ));
        when(sectionSummaryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(llmService.generateAnswer(any(String.class)))
                .thenReturn("摘要：方法摘要\n关键点：\n- 方法关键点")
                .thenReturn("研究问题：研究问题\n方法概述：方法概述\n实验与评估：信息不足\n主要贡献：贡献\n局限性：信息不足\n关键词：RAG\n完整画像：完整画像文本");

        PaperProfileServiceImpl service = new PaperProfileServiceImpl(
                paperReferenceMapper,
                paperSectionMapper,
                paperChunkMapper,
                sectionSummaryMapper,
                paperProfileMapper,
                llmService,
                qdrantService
        );

        PaperProfileResult result = service.generateProfile(7L);

        assertThat(result.getProfile().getPaperId()).isEqualTo(7L);
        assertThat(result.getProfile().getResearchProblem()).isEqualTo("研究问题");
        assertThat(result.getSectionSummaries()).hasSize(1);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, org.mockito.Mockito.times(2)).generateAnswer(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(0)).contains("method content explains the proposed model");
        assertThat(promptCaptor.getAllValues().get(0)).doesNotContain("reference list");
        assertThat(promptCaptor.getAllValues().get(0)).doesNotContain("funding text");
    }

    @Test
    void generateProfileParsesMarkdownStyleLlmFields() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        LlmService llmService = mock(LlmService.class);
        QdrantService qdrantService = mock(QdrantService.class);

        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
        when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(section(10L, 7L, "METHOD", "Method", 0)));
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                chunk(1L, 7L, 10L, "METHOD", "Method", 0, "method content explains the proposed model", false, false, 10)
        ));
        when(sectionSummaryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(llmService.generateAnswer(any(String.class)))
                .thenReturn("### 摘要\n该章节提出了一个用于论文问答的模型方法。\n### 关键点\n- 使用结构化上下文\n- 提升多篇比较质量")
                .thenReturn("### 研究问题\n如何提升多篇论文比较问答的完整性。\n### 方法概述\n结合文献画像、章节摘要和原文证据。\n### 实验与评估\n在多篇论文对比问题上观察回答完整性。\n### 主要贡献\n提出混合上下文组织方式。\n### 局限性\n需要预先生成画像。\n### 关键词\nHYBRID_RAG, paper profile\n### 完整画像\n这篇论文关注多篇论文比较问答的上下文组织。");

        PaperProfileServiceImpl service = new PaperProfileServiceImpl(
                paperReferenceMapper,
                paperSectionMapper,
                paperChunkMapper,
                sectionSummaryMapper,
                paperProfileMapper,
                llmService,
                qdrantService
        );

        PaperProfileResult result = service.generateProfile(7L);

        assertThat(result.getSectionSummaries().get(0).getSummary()).contains("论文问答的模型方法");
        assertThat(result.getSectionSummaries().get(0).getKeyPoints()).contains("结构化上下文");
        assertThat(result.getProfile().getResearchProblem()).contains("多篇论文比较问答");
        assertThat(result.getProfile().getMethodSummary()).contains("文献画像");
        assertThat(result.getProfile().getProfileText()).contains("上下文组织");
    }

    @Test
    void profilePromptIncludesRawEvidenceChunksToAvoidSummaryInformationLoss() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        LlmService llmService = mock(LlmService.class);
        QdrantService qdrantService = mock(QdrantService.class);

        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
        when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                section(10L, 7L, "EXPERIMENT", "Experiment", 0),
                section(11L, 7L, "DISCUSSION", "Discussion", 1)
        ));
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                chunk(1L, 7L, 10L, "EXPERIMENT", "Experiment", 0, "experiments show accuracy improves by 12 percent on the benchmark", false, false, 20),
                chunk(2L, 7L, 11L, "DISCUSSION", "Discussion", 1, "the main limitation is high GPU memory cost during inference", false, false, 20)
        ));
        when(sectionSummaryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(llmService.generateAnswer(any(String.class)))
                .thenReturn("摘要：实验摘要\n关键点：实验关键点")
                .thenReturn("摘要：讨论摘要\n关键点：讨论关键点")
                .thenReturn("研究问题：研究问题\n方法概述：方法\n实验与评估：实验\n主要贡献：贡献\n局限性：局限\n关键词：关键词\n完整画像：画像");

        PaperProfileServiceImpl service = new PaperProfileServiceImpl(
                paperReferenceMapper,
                paperSectionMapper,
                paperChunkMapper,
                sectionSummaryMapper,
                paperProfileMapper,
                llmService,
                qdrantService
        );

        service.generateProfile(7L);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, org.mockito.Mockito.times(3)).generateAnswer(promptCaptor.capture());
        String profilePrompt = promptCaptor.getAllValues().get(2);
        assertThat(profilePrompt).contains("原文证据片段");
        assertThat(profilePrompt).contains("accuracy improves by 12 percent");
        assertThat(profilePrompt).contains("high GPU memory cost");
    }

    @Test
    void generateProfileReportsProgressForSectionsAndProfile() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        LlmService llmService = mock(LlmService.class);
        QdrantService qdrantService = mock(QdrantService.class);

        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
        when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                section(10L, 7L, "METHOD", "Method", 0),
                section(11L, 7L, "EXPERIMENT", "Experiment", 1)
        ));
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                chunk(1L, 7L, 10L, "METHOD", "Method", 0,
                        "method content explains the proposed model architecture and training objective for wind prediction", false, false, 20),
                chunk(2L, 7L, 11L, "EXPERIMENT", "Experiment", 1,
                        "experiment content describes the datasets baselines metrics and comparison results", false, false, 20)
        ));
        when(sectionSummaryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(llmService.generateAnswer(any(String.class)))
                .thenReturn("摘要：方法摘要\n关键点：方法关键点")
                .thenReturn("摘要：实验摘要\n关键点：实验关键点")
                .thenReturn("研究问题：研究问题\n方法概述：方法\n实验与评估：实验\n主要贡献：贡献\n局限性：局限\n关键词：关键词\n完整画像：画像");

        PaperProfileServiceImpl service = new PaperProfileServiceImpl(
                paperReferenceMapper,
                paperSectionMapper,
                paperChunkMapper,
                sectionSummaryMapper,
                paperProfileMapper,
                llmService,
                qdrantService
        );
        List<String> events = new ArrayList<>();

        service.generateProfile(7L, new PaperProfileProgressListener() {
            @Override
            public void onPreparing() {
                events.add("preparing");
            }

            @Override
            public void onSectionProgress(int processedSections, int totalSections) {
                events.add("section:" + processedSections + "/" + totalSections);
            }

            @Override
            public void onGeneratingProfile() {
                events.add("profile");
            }

            @Override
            public void onSavingResult() {
                events.add("saving");
            }
        });

        assertThat(events).containsExactly("preparing", "section:1/2", "section:2/2", "profile", "saving");
    }

    @Test
    void generateProfileUsesRawLlmTextAsSummaryFallbackWhenFieldMissing() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        LlmService llmService = mock(LlmService.class);
        QdrantService qdrantService = mock(QdrantService.class);

        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
        when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(section(10L, 7L, "METHOD", "Method", 0)));
        PaperChunk methodChunk = chunk(1L, 7L, 10L, "METHOD", "Method", 0,
                "The proposed model combines graph convolution and temporal attention for wind prediction.", false, false, 20);
        methodChunk.setChunkType("text");
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(methodChunk));
        when(sectionSummaryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(llmService.generateAnswer(any(String.class)))
                .thenReturn("该章节介绍了一种结合图卷积和时间注意力的风速预测模型。关键点包括空间依赖建模和时间动态捕获。")
                .thenReturn("研究问题：研究问题\n方法概述：方法\n实验与评估：实验\n主要贡献：贡献\n局限性：局限\n关键词：关键词\n完整画像：画像");

        PaperProfileServiceImpl service = new PaperProfileServiceImpl(
                paperReferenceMapper,
                paperSectionMapper,
                paperChunkMapper,
                sectionSummaryMapper,
                paperProfileMapper,
                llmService,
                qdrantService
        );

        PaperProfileResult result = service.generateProfile(7L);

        assertThat(result.getSectionSummaries()).hasSize(1);
        assertThat(result.getSectionSummaries().get(0).getSummary()).contains("图卷积");
        assertThat(result.getSectionSummaries().get(0).getSummary()).doesNotContain("信息不足");
    }

    @Test
    void generateProfileSkipsTableOnlySectionsForSectionSummary() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        LlmService llmService = mock(LlmService.class);
        QdrantService qdrantService = mock(QdrantService.class);

        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
        when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(section(10L, 7L, "RESULT", "Table 1", 0)));
        PaperChunk tableChunk = chunk(1L, 7L, 10L, "RESULT", "Table 1", 0,
                "Table 1 MAE RMSE MAPE Accuracy 0.91 0.82 0.77", false, false, 20);
        tableChunk.setChunkType("table");
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(tableChunk));
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(llmService.generateAnswer(any(String.class)))
                .thenReturn("研究问题：研究问题\n方法概述：方法\n实验与评估：实验\n主要贡献：贡献\n局限性：局限\n关键词：关键词\n完整画像：画像");

        PaperProfileServiceImpl service = new PaperProfileServiceImpl(
                paperReferenceMapper,
                paperSectionMapper,
                paperChunkMapper,
                sectionSummaryMapper,
                paperProfileMapper,
                llmService,
                qdrantService
        );

        PaperProfileResult result = service.generateProfile(7L);

        assertThat(result.getSectionSummaries()).isEmpty();
        verify(sectionSummaryMapper, never()).insert(any(PaperSectionSummary.class));
    }

    @Test
    void getProfileReturnsNullProfileWhenMissing() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperSectionSummaryMapper sectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
        when(paperProfileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(sectionSummaryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        PaperProfileServiceImpl service = new PaperProfileServiceImpl(
                paperReferenceMapper,
                mock(PaperSectionMapper.class),
                mock(PaperChunkMapper.class),
                sectionSummaryMapper,
                paperProfileMapper,
                mock(LlmService.class),
                mock(QdrantService.class)
        );

        PaperProfileResult result = service.getProfile(7L);

        assertThat(result.getProfile()).isNull();
        assertThat(result.getSectionSummaries()).isEmpty();
    }

    @Test
    void generateProfileRejectsPaperWithoutUsableChunks() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        LlmService llmService = mock(LlmService.class);
        QdrantService qdrantService = mock(QdrantService.class);
        when(paperReferenceMapper.selectById(7L)).thenReturn(paper(7L, "Wind Paper", "COMPLETED"));
        when(paperSectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(section(10L, 7L, "REFERENCES", "References", 0)));
        when(paperChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                chunk(1L, 7L, 10L, "REFERENCES", "References", 0, "reference list", true, false, 5)
        ));

        PaperProfileServiceImpl service = new PaperProfileServiceImpl(
                paperReferenceMapper,
                paperSectionMapper,
                paperChunkMapper,
                mock(PaperSectionSummaryMapper.class),
                mock(PaperProfileMapper.class),
                llmService,
                qdrantService
        );

        assertThatThrownBy(() -> service.generateProfile(7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("当前论文没有可用于生成画像的正文内容");
        verify(llmService, never()).generateAnswer(any(String.class));
    }

    private PaperProfileServiceImpl createService(PaperReferenceMapper paperReferenceMapper) {
        return new PaperProfileServiceImpl(
                paperReferenceMapper,
                mock(PaperSectionMapper.class),
                mock(PaperChunkMapper.class),
                mock(PaperSectionSummaryMapper.class),
                mock(PaperProfileMapper.class),
                mock(LlmService.class),
                mock(QdrantService.class)
        );
    }

    private PaperReference paper(Long id, String title, String parseStatus) {
        PaperReference paper = new PaperReference();
        paper.setId(id);
        paper.setTitle(title);
        paper.setParseStatus(parseStatus);
        return paper;
    }

    private PaperSection section(Long id, Long paperId, String sectionType, String sectionTitle, Integer sectionIndex) {
        PaperSection section = new PaperSection();
        section.setId(id);
        section.setPaperId(paperId);
        section.setSectionType(sectionType);
        section.setSectionTitle(sectionTitle);
        section.setSectionIndex(sectionIndex);
        return section;
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
        return chunk;
    }
}
