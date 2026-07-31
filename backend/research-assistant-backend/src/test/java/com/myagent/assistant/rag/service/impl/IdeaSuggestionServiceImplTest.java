package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.llm.LlmService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdeaSuggestionServiceImplTest {

    // ========================
    // 触发 1：改进意图检测
    // ========================

    @Test
    void detectImprovementIntentMatchesChinesePatterns() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        assertThat(service.detectImprovementIntent("这个方法还能怎么改进？")).isTrue();
        assertThat(service.detectImprovementIntent("如何优化这个模型的性能？")).isTrue();
        assertThat(service.detectImprovementIntent("有什么优化建议吗？")).isTrue();
        assertThat(service.detectImprovementIntent("未来的改进方向有哪些？")).isTrue();
        assertThat(service.detectImprovementIntent("下一步可以做什么？")).isTrue();
        assertThat(service.detectImprovementIntent("还有什么值得探索的？")).isTrue();
        assertThat(service.detectImprovementIntent("可以怎么提升准确率？")).isTrue();
        assertThat(service.detectImprovementIntent("这个方法有什么不足？")).isTrue();
        assertThat(service.detectImprovementIntent("有哪些局限性？")).isTrue();
        assertThat(service.detectImprovementIntent("可以尝试哪些替代方案？")).isTrue();
        assertThat(service.detectImprovementIntent("what are the limitations and future work")).isTrue();
    }

    @Test
    void detectImprovementIntentRejectsKnowledgeQuestions() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        assertThat(service.detectImprovementIntent("这篇论文用了什么方法？")).isFalse();
        assertThat(service.detectImprovementIntent("介绍一下Transformer的结构")).isFalse();
        assertThat(service.detectImprovementIntent("这篇论文的主要贡献是什么？")).isFalse();
        assertThat(service.detectImprovementIntent("作者做了哪些实验？")).isFalse();
        assertThat(service.detectImprovementIntent("这个数据集有多大？")).isFalse();
        assertThat(service.detectImprovementIntent("")).isFalse();
        assertThat(service.detectImprovementIntent(null)).isFalse();
    }

    @Test
    void detectImprovementIntentRejectsCasualChat() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        assertThat(service.detectImprovementIntent("你好")).isFalse();
        assertThat(service.detectImprovementIntent("谢谢你的回答")).isFalse();
        assertThat(service.detectImprovementIntent("这篇论文讲了什么？")).isFalse();
    }

    // ========================
    // 触发 2 门控
    // ========================

    @Test
    void passesResearchGateAllowsDirectionalQuestions() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        assertThat(service.passesResearchGate("有什么改进空间")).isTrue();
        assertThat(service.passesResearchGate("优化方向是什么")).isTrue();
        assertThat(service.passesResearchGate("未来的探索方向")).isTrue();
        assertThat(service.passesResearchGate("有没有更好的替代方案")).isTrue();
        assertThat(service.passesResearchGate("对比一下这两种方法")).isTrue();
        assertThat(service.passesResearchGate("limitation of this approach")).isTrue();
    }

    @Test
    void passesResearchGateBlocksPureKnowledgeQuestions() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        assertThat(service.passesResearchGate("这篇论文讲了什么？")).isFalse();
        assertThat(service.passesResearchGate("作者是谁")).isFalse();
        assertThat(service.passesResearchGate("这篇论文的数据集是什么")).isFalse();
        assertThat(service.passesResearchGate("")).isFalse();
        assertThat(service.passesResearchGate(null)).isFalse();
    }

    // ========================
    // 触发 2：LLM 分类
    // ========================

    @Test
    void classifyAnswerQualityReturnsTrueWhenLlmSaysYes() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.generateAnswer(any(String.class))).thenReturn("是");
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        assertThat(service.classifyAnswerQuality("有什么改进方向？", "可以尝试引入注意力机制来提升特征提取能力")).isTrue();
    }

    @Test
    void classifyAnswerQualityReturnsFalseWhenLlmSaysNo() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.generateAnswer(any(String.class))).thenReturn("否");
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        assertThat(service.classifyAnswerQuality("有什么改进方向？", "该方法使用CNN提取特征，在ImageNet上达到了SOTA性能")).isFalse();
    }

    @Test
    void classifyAnswerQualityReturnsFalseWhenLlmFails() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.generateAnswer(any(String.class))).thenThrow(new RuntimeException("API error"));
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        assertThat(service.classifyAnswerQuality("有什么改进方向？", "some answer")).isFalse();
    }

    @Test
    void classifyAnswerQualityReturnsFalseForEmptyAnswer() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        assertThat(service.classifyAnswerQuality("有什么改进方向？", "")).isFalse();
        assertThat(service.classifyAnswerQuality("有什么改进方向？", null)).isFalse();
    }

    // ========================
    // shouldSuggestSaveAsIdea 完整流程
    // ========================

    @Test
    void shouldSuggestSaveAsIdeaReturnsTrueForImprovementQuestion() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        boolean result = service.shouldSuggestSaveAsIdea(
                "这个方法还能怎么改进？",
                "你可以尝试加入自注意力机制来捕捉长距离依赖。",
                Collections.emptyList()
        );

        assertThat(result).isTrue();
        // 触发 1 命中时不应调用 LLM 分类
        verify(llmService, never()).generateAnswer(any());
    }

    @Test
    void shouldSuggestSaveAsIdeaReturnsTrueWhenLlmClassifiesAsIdea() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.generateAnswer(any(String.class))).thenReturn("是");
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        // 问题不触发触发 1，但通过门控 → LLM 分类判断
        boolean result = service.shouldSuggestSaveAsIdea(
                "这个模型有什么改进空间？",
                "可以尝试将静态图卷积替换为动态图卷积，并结合时序注意力机制来提升风速预测的准确性。",
                Collections.emptyList()
        );

        assertThat(result).isTrue();
    }

    @Test
    void shouldSuggestSaveAsIdeaReturnsFalseWhenLlmClassifiesAsNotIdea() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.generateAnswer(any(String.class))).thenReturn("否");
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        boolean result = service.shouldSuggestSaveAsIdea(
                "这个模型有什么改进空间？",
                "该模型使用GCN进行空间建模，是目前风电预测领域的主流方法之一。",
                Collections.emptyList()
        );

        assertThat(result).isFalse();
    }

    @Test
    void shouldSuggestSaveAsIdeaReturnsFalseForPureKnowledgeQuestion() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        boolean result = service.shouldSuggestSaveAsIdea(
                "这篇论文用了什么方法？",
                "这篇论文使用了图卷积网络和时序注意力机制进行风速预测。",
                Collections.emptyList()
        );

        assertThat(result).isFalse();
        // 触发 1 不命中 + 门控不通过 → 不调用 LLM
        verify(llmService, never()).generateAnswer(any());
    }

    @Test
    void shouldSuggestSaveAsIdeaReturnsFalseForCasualChat() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        assertThat(service.shouldSuggestSaveAsIdea("你好", "你好！有什么可以帮助你的？", Collections.emptyList())).isFalse();
        verify(llmService, never()).generateAnswer(any());
    }

    @Test
    void shouldSuggestSaveAsIdeaReturnsFalseForEmptyAnswer() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        assertThat(service.shouldSuggestSaveAsIdea("怎么改进？", "", Collections.emptyList())).isFalse();
        assertThat(service.shouldSuggestSaveAsIdea("怎么改进？", null, Collections.emptyList())).isFalse();
        verify(llmService, never()).generateAnswer(any());
    }

    // ========================
    // buildSuggestionReason
    // ========================

    @Test
    void buildSuggestionReasonReturnsImprovementMessageForTrigger1() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        String reason = service.buildSuggestionReason(
                "这个方法还能怎么改进？",
                "你可以尝试加入自注意力机制。",
                Collections.emptyList()
        );

        assertThat(reason).contains("改进或优化建议");
        verify(llmService, never()).generateAnswer(any());
    }

    @Test
    void buildSuggestionReasonReturnsIdeaMessageForTrigger2() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.generateAnswer(any(String.class))).thenReturn("是");
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        // 问题不触发触发 1，但通过门控 + LLM 分类命中
        String reason = service.buildSuggestionReason(
                "这个模型有什么改进空间？",
                "可以尝试动态图卷积结合时序注意力机制。",
                Collections.emptyList()
        );

        assertThat(reason).contains("进一步探索");
    }

    @Test
    void buildSuggestionReasonReturnsNullForNoTrigger() {
        LlmService llmService = mock(LlmService.class);
        IdeaSuggestionServiceImpl service = new IdeaSuggestionServiceImpl(llmService);

        String reason = service.buildSuggestionReason(
                "这篇论文用了什么方法？",
                "使用了图卷积网络。",
                Collections.emptyList()
        );

        assertThat(reason).isNull();
    }
}
