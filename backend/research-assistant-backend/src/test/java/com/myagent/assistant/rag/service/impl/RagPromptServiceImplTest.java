package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.rag.context.FullTextContext;
import com.myagent.assistant.rag.context.HybridRagContext;
import com.myagent.assistant.rag.dto.RagSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptServiceImplTest {

    @Test
    void buildFullTextPromptUsesChapterContextAndMacroQuestionGuidance() {
        RagPromptServiceImpl service = new RagPromptServiceImpl();
        FullTextContext context = new FullTextContext(
                7L,
                "Wind Paper",
                "论文ID：7\n论文标题：Wind Paper\n\n[METHOD] Method\nmethod content",
                List.of(),
                100
        );

        String prompt = service.buildFullTextPrompt("这篇论文主要讲了什么？", context);

        assertThat(prompt).contains("按章节组织的论文正文上下文");
        assertThat(prompt).contains("这篇论文主要讲了什么？");
        assertThat(prompt).contains("[METHOD] Method");
        assertThat(prompt).contains("综合摘要、引言、方法、实验、结果和结论");
        assertThat(prompt).contains("不要使用 References");
    }

    @Test
    void buildPromptIncludesSectionMetadataForVectorSources() {
        RagPromptServiceImpl service = new RagPromptServiceImpl();
        RagSource source = new RagSource();
        source.setPaperId(7L);
        source.setPaperTitle("Wind Paper");
        source.setChunkId(10L);
        source.setChunkIndex(2);
        source.setSectionType("METHOD");
        source.setSectionTitle("Proposed Method");
        source.setContent("This source chunk has enough useful content to avoid low value filtering and should be included in the vector prompt metadata block.");

        String prompt = service.buildPrompt("总结方法", List.of(source));

        assertThat(prompt).contains("章节类型：METHOD");
        assertThat(prompt).contains("章节标题：Proposed Method");
    }

    @Test
    void buildHybridPromptRequiresPerPaperAnalysisAndComparison() {
        RagPromptServiceImpl service = new RagPromptServiceImpl();
        HybridRagContext context = new HybridRagContext(
                "[Paper 7] A\n一、文献画像\n方法概述：A method\n\n[Paper 8] B\n一、文献画像\n方法概述：B method",
                List.of(),
                200,
                List.of(7L, 8L)
        );

        String prompt = service.buildHybridPrompt("这两篇论文的方法有什么区别？", context);

        assertThat(prompt).contains("多篇论文的混合上下文");
        assertThat(prompt).contains("先逐篇分析每篇论文，再做横向比较");
        assertThat(prompt).contains("不要把 A 论文的信息归到 B 论文");
        assertThat(prompt).contains("这两篇论文的方法有什么区别？");
    }

    @Test
    void buildLibraryDiscoveryPromptSeparatesEvidenceNumberFromPaperId() {
        RagPromptServiceImpl service = new RagPromptServiceImpl();
        RagSource source = new RagSource();
        source.setPaperId(29L);
        source.setPaperTitle("Review Paper");
        source.setSectionType("METHOD");
        source.setContent("This sufficiently long source describes graph construction, adjacency matrices, nodes, and neighborhood aggregation for forecasting.");

        String prompt = service.buildLibraryDiscoveryPrompt("哪些论文使用图结构？", List.of(source));

        assertThat(prompt).contains("[来源 1]");
        assertThat(prompt).contains("论文ID：29");
        assertThat(prompt).contains("[来源 X] 是证据片段编号，不是论文 ID");
        assertThat(prompt).contains("构图、邻接矩阵、节点或近邻聚合");
        assertThat(prompt).contains("论文ID：真实ID，来源编号：[X]");
    }
}
