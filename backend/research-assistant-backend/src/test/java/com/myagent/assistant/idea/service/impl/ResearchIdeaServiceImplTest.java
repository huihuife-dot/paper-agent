package com.myagent.assistant.idea.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.myagent.assistant.chat.entity.ChatMessage;
import com.myagent.assistant.chat.mapper.ChatMessageMapper;
import com.myagent.assistant.idea.dto.ResearchIdeaSaveTypeStatsResponse;
import com.myagent.assistant.idea.entity.ResearchIdea;
import com.myagent.assistant.idea.mapper.ResearchIdeaMapper;
import com.myagent.assistant.llm.LlmService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ResearchIdeaServiceImplTest {

    @Test
    void countBySaveTypeReturnsAllStatusCountsAndTotal() {
        ResearchIdeaMapper researchIdeaMapper = mock(ResearchIdeaMapper.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        LlmService llmService = mock(LlmService.class);

        when(researchIdeaMapper.selectCount(any(Wrapper.class)))
                .thenReturn(3L, 5L, 2L, 1L);

        ResearchIdeaServiceImpl service = new ResearchIdeaServiceImpl(
                researchIdeaMapper,
                chatMessageMapper,
                llmService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        ResearchIdeaSaveTypeStatsResponse stats = service.countBySaveType();

        assertThat(stats.getDraft()).isEqualTo(3L);
        assertThat(stats.getIdea()).isEqualTo(5L);
        assertThat(stats.getTodo()).isEqualTo(2L);
        assertThat(stats.getImplemented()).isEqualTo(1L);
        assertThat(stats.getTotal()).isEqualTo(11L);
    }

    @Test
    void updateSaveTypeSupportsImplementedStatus() {
        ResearchIdeaMapper researchIdeaMapper = mock(ResearchIdeaMapper.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        LlmService llmService = mock(LlmService.class);

        ResearchIdea idea = new ResearchIdea();
        idea.setId(1L);
        idea.setTitle("RAG 检索优化");
        idea.setSaveType("draft");

        when(researchIdeaMapper.selectById(1L)).thenReturn(idea);

        ResearchIdeaServiceImpl service = new ResearchIdeaServiceImpl(
                researchIdeaMapper,
                chatMessageMapper,
                llmService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        ResearchIdea result = service.updateSaveType(1L, "implemented");

        assertThat(result.getSaveType()).isEqualTo("implemented");
        assertThat(result.getUpdateTime()).isNotNull();
        verify(researchIdeaMapper).updateById(idea);
    }

    @Test
    void updateSaveTypeRejectsInvalidStatus() {
        ResearchIdeaMapper researchIdeaMapper = mock(ResearchIdeaMapper.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        LlmService llmService = mock(LlmService.class);

        ResearchIdeaServiceImpl service = new ResearchIdeaServiceImpl(
                researchIdeaMapper,
                chatMessageMapper,
                llmService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        assertThatThrownBy(() -> service.updateSaveType(1L, "abc"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("saveType 只能是 draft、idea、todo、implemented");

        verify(researchIdeaMapper, never()).updateById(any(ResearchIdea.class));
    }

    @Test
    void listSupportsCombinedFilters() {
        ResearchIdeaMapper researchIdeaMapper = mock(ResearchIdeaMapper.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        LlmService llmService = mock(LlmService.class);

        ResearchIdea idea = new ResearchIdea();
        idea.setId(1L);
        idea.setTitle("RAG 检索优化");
        idea.setSourceType("rag_chat");
        idea.setSaveType("draft");
        idea.setSourceSessionId(7L);

        when(researchIdeaMapper.selectList(any(Wrapper.class))).thenReturn(List.of(idea));

        ResearchIdeaServiceImpl service = new ResearchIdeaServiceImpl(
                researchIdeaMapper,
                chatMessageMapper,
                llmService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        List<ResearchIdea> result = service.list("RAG", "rag_chat", "draft", 7L);

        assertThat(result).containsExactly(idea);
        verify(researchIdeaMapper).selectList(any(Wrapper.class));
        verify(llmService, never()).generateAnswer(any(String.class));
        verify(chatMessageMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void getBySourceSessionIdReturnsSavedRagIdea() {
        ResearchIdeaMapper researchIdeaMapper = mock(ResearchIdeaMapper.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        LlmService llmService = mock(LlmService.class);

        ResearchIdea existingIdea = new ResearchIdea();
        existingIdea.setId(99L);
        existingIdea.setTitle("已保存的研究想法");
        existingIdea.setSourceType("rag_chat");
        existingIdea.setSaveType("draft");
        existingIdea.setSourceSessionId(7L);

        when(researchIdeaMapper.selectOne(any(Wrapper.class))).thenReturn(existingIdea);

        ResearchIdeaServiceImpl service = new ResearchIdeaServiceImpl(
                researchIdeaMapper,
                chatMessageMapper,
                llmService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        ResearchIdea result = service.getBySourceSessionId(7L);

        assertThat(result).isSameAs(existingIdea);
        verify(llmService, never()).generateAnswer(any(String.class));
        verify(chatMessageMapper, never()).selectList(any(Wrapper.class));
        verify(researchIdeaMapper, never()).insert(any(ResearchIdea.class));
    }

    @Test
    void saveDraftFromSessionReturnsExistingIdeaWhenSessionAlreadySaved() {
        ResearchIdeaMapper researchIdeaMapper = mock(ResearchIdeaMapper.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        LlmService llmService = mock(LlmService.class);

        ResearchIdea existingIdea = new ResearchIdea();
        existingIdea.setId(99L);
        existingIdea.setTitle("已保存的研究想法");
        existingIdea.setSourceType("rag_chat");
        existingIdea.setSaveType("draft");
        existingIdea.setSourceSessionId(7L);

        when(researchIdeaMapper.selectOne(any(Wrapper.class))).thenReturn(existingIdea);

        ResearchIdeaServiceImpl service = new ResearchIdeaServiceImpl(
                researchIdeaMapper,
                chatMessageMapper,
                llmService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        ResearchIdea result = service.saveDraftFromSession(7L);

        assertThat(result).isSameAs(existingIdea);
        verify(llmService, never()).generateAnswer(any(String.class));
        verify(chatMessageMapper, never()).selectList(any(Wrapper.class));
        verify(researchIdeaMapper, never()).insert(any(ResearchIdea.class));
    }

    @Test
    void saveDraftFromSessionCreatesDraftIdeaFromSessionSummary() {
        ResearchIdeaMapper researchIdeaMapper = mock(ResearchIdeaMapper.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        LlmService llmService = mock(LlmService.class);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setId(10L);
        userMessage.setSessionId(7L);
        userMessage.setRole("user");
        userMessage.setContent("可以进一步研究哪些方向？");

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setId(11L);
        assistantMessage.setSessionId(7L);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("可以探索跨语言检索和消融实验。");
        assistantMessage.setSourcesJson("[{\"paperId\":3}]");

        when(chatMessageMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(userMessage, assistantMessage));
        when(llmService.generateAnswer(any(String.class))).thenReturn("""
                {
                  "title": "跨语言检索研究",
                  "originalContent": "来自一次 RAG 对话中关于跨语言检索的讨论。",
                  "refinedContent": "研究跨语言 query rewrite 对英文论文 chunk 召回质量的影响。",
                  "innovationPoints": "结合原始问题与英文改写进行双路召回。",
                  "researchQuestion": "跨语言改写能否稳定提升中文问题检索英文文献的效果？",
                  "possibleMethod": "对比单路召回、双路召回和消融实验。",
                  "tags": "RAG,跨语言检索",
                  "relatedPaperIds": "3"
                }
                """);

        ResearchIdeaServiceImpl service = new ResearchIdeaServiceImpl(
                researchIdeaMapper,
                chatMessageMapper,
                llmService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        ResearchIdea savedIdea = service.saveDraftFromSession(7L);

        ArgumentCaptor<ResearchIdea> ideaCaptor = ArgumentCaptor.forClass(ResearchIdea.class);
        verify(researchIdeaMapper).insert(ideaCaptor.capture());
        ResearchIdea insertedIdea = ideaCaptor.getValue();

        assertThat(savedIdea).isSameAs(insertedIdea);
        assertThat(insertedIdea.getTitle()).isEqualTo("跨语言检索研究");
        assertThat(insertedIdea.getSourceType()).isEqualTo("rag_chat");
        assertThat(insertedIdea.getSaveType()).isEqualTo("draft");
        assertThat(insertedIdea.getSourceSessionId()).isEqualTo(7L);
        assertThat(insertedIdea.getSourceMessageId()).isNull();
        assertThat(insertedIdea.getRelatedPaperIds()).isEqualTo("3");
        assertThat(insertedIdea.getCreateTime()).isNotNull();
        assertThat(insertedIdea.getUpdateTime()).isNotNull();
    }

    @Test
    void saveDraftUsesRealSourcePaperIdsInsteadOfLlmGeneratedIds() {
        ResearchIdeaMapper researchIdeaMapper = mock(ResearchIdeaMapper.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
        LlmService llmService = mock(LlmService.class);

        ChatMessage firstAssistant = new ChatMessage();
        firstAssistant.setId(21L);
        firstAssistant.setSessionId(9L);
        firstAssistant.setRole("assistant");
        firstAssistant.setContent("第一轮回答");
        firstAssistant.setSourcesJson("[{\"paperId\":13},{\"paperId\":\"15\"},{\"paperId\":13}]");

        ChatMessage secondAssistant = new ChatMessage();
        secondAssistant.setId(22L);
        secondAssistant.setSessionId(9L);
        secondAssistant.setRole("assistant");
        secondAssistant.setContent("第二轮回答");
        secondAssistant.setSourcesJson("{\"sources\":[{\"paperId\":30},{\"paperId\":0},{\"paperId\":\"bad\"}]}");

        when(chatMessageMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(firstAssistant, secondAssistant));
        when(llmService.generateAnswer(any(String.class))).thenReturn("""
                {
                  "title": "图结构预测研究",
                  "originalContent": "比较多种空间建模方法。",
                  "refinedContent": "研究不同图结构的归纳偏置。",
                  "innovationPoints": "统一空间关系表示。",
                  "researchQuestion": "不同构图方式如何影响预测？",
                  "possibleMethod": "统一数据集消融。",
                  "tags": "图学习",
                  "relatedPaperIds": "999"
                }
                """);

        ResearchIdeaServiceImpl service = new ResearchIdeaServiceImpl(
                researchIdeaMapper,
                chatMessageMapper,
                llmService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        ResearchIdea savedIdea = service.saveDraftFromSession(9L);

        assertThat(savedIdea.getRelatedPaperIds()).isEqualTo("13,15,30");
        assertThat(savedIdea.getRelatedPaperIds()).doesNotContain("999");
    }
}
