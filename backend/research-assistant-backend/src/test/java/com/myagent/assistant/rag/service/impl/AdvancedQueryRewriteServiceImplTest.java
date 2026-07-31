package com.myagent.assistant.rag.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.dto.QueryRewriteResult;
import com.myagent.assistant.rag.service.AdvancedQueryRewriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AdvancedQueryRewriteService 单元测试。
 *
 * 覆盖正常改写、LLM 空返回兜底、LLM JSON 解析失败兜底、英文问题等场景。
 */
class AdvancedQueryRewriteServiceImplTest {

    private AdvancedQueryRewriteService service;
    private StubLlmService llmService;

    @BeforeEach
    void setUp() {
        llmService = new StubLlmService();
        service = new AdvancedQueryRewriteServiceImpl(llmService, new ObjectMapper());
    }

    @Test
    void shouldRewriteChineseQuestionSuccessfully() {
        llmService.setNextOutput("""
                {
                  "declarativeQuery": "Transformer architecture for wind speed time series forecasting",
                  "keywordQuery": "Transformer attention wind speed forecasting time series",
                  "keywordTerms": "Transformer, 注意力机制, 风速预测, 时间序列",
                  "hydeQuery": "This paper proposes a Transformer-based model for wind speed forecasting using attention mechanisms."
                }
                """);

        QueryRewriteResult result = service.rewrite("有没有论文用Transformer做风速预测？");

        assertEquals("有没有论文用Transformer做风速预测？", result.getOriginalQuestion());
        assertTrue(result.getDeclarativeQuery().contains("Transformer"));
        assertTrue(result.getKeywordQuery().contains("wind speed"));
        assertTrue(result.getHydeQuery().contains("Transformer-based"));
        assertEquals("Transformer, 注意力机制, 风速预测, 时间序列", result.getKeywordTerms());
    }

    @Test
    void shouldReturnMultipleQueries() {
        llmService.setNextOutput("""
                {
                  "declarativeQuery": "deep learning methods for multivariate time series forecasting",
                  "keywordQuery": "deep learning multivariate time series forecasting LSTM Transformer",
                  "keywordTerms": "深度学习, 多变量时间序列, 预测, LSTM, Transformer",
                  "hydeQuery": "This paper surveys deep learning approaches for multivariate time series forecasting including LSTM networks and Transformer architectures."
                }
                """);

        QueryRewriteResult result = service.rewrite("深度学习在时间序列预测中有哪些应用？");
        List<String> queries = result.allQueries();

        assertEquals(3, queries.size());
        assertTrue(queries.get(0).contains("deep learning"));
        assertTrue(result.isValid());
    }

    @Test
    void shouldFallbackWhenLlmReturnsEmpty() {
        llmService.setNextOutput(null);

        QueryRewriteResult result = service.rewrite("Transformer在NLP中的应用");

        assertNotNull(result);
        assertEquals("Transformer在NLP中的应用", result.getOriginalQuestion());
        assertEquals("Transformer在NLP中的应用", result.getDeclarativeQuery());
        assertNull(result.getHydeQuery());
        // 兜底时 allQueries 至少包含原始问题
        assertEquals(1, result.allQueries().size());
    }

    @Test
    void shouldFallbackWhenLlmReturnsBlank() {
        llmService.setNextOutput("   ");

        QueryRewriteResult result = service.rewrite("如何用机器学习预测股票？");

        assertNotNull(result);
        assertEquals("如何用机器学习预测股票？", result.getDeclarativeQuery());
        assertNull(result.getHydeQuery());
        assertEquals(1, result.allQueries().size());
    }

    @Test
    void shouldFallbackWhenJsonParsingFails() {
        llmService.setNextOutput("这是一个普通的文本回复，不是JSON。");

        QueryRewriteResult result = service.rewrite("有哪些NLP预训练模型？");

        assertNotNull(result);
        assertEquals("有哪些NLP预训练模型？", result.getDeclarativeQuery());
        assertEquals(1, result.allQueries().size());
    }

    @Test
    void shouldRejectNullQuestion() {
        assertThrows(RuntimeException.class, () -> service.rewrite(null));
    }

    @Test
    void shouldRejectBlankQuestion() {
        assertThrows(RuntimeException.class, () -> service.rewrite(""));
    }

    @Test
    void shouldRewriteEnglishQuestionToo() {
        llmService.setNextOutput("""
                {
                  "declarativeQuery": "graph neural networks applied to molecular property prediction",
                  "keywordQuery": "graph neural network molecular property prediction GNN",
                  "keywordTerms": "GNN, molecular, property prediction, drug discovery",
                  "hydeQuery": "Graph neural networks have been successfully applied to predict molecular properties by representing atoms as nodes and bonds as edges."
                }
                """);

        QueryRewriteResult result = service.rewrite("How are GNNs used in drug discovery?");

        assertTrue(result.getDeclarativeQuery().contains("graph neural"));
        assertTrue(result.getKeywordQuery().contains("GNN"));
        assertTrue(result.isValid());
    }

    @Test
    void shouldDeduplicateQueriesInAllQueries() {
        llmService.setNextOutput("""
                {
                  "declarativeQuery": "attention mechanism for NLP tasks",
                  "keywordQuery": "attention mechanism for NLP tasks",
                  "keywordTerms": "attention, NLP",
                  "hydeQuery": "attention mechanism for NLP tasks"
                }
                """);

        QueryRewriteResult result = service.rewrite("注意力机制在NLP中怎么用？");

        // 三个 query 内容相同，allQueries 应该去重为 1 个
        assertEquals(1, result.allQueries().size());
    }

    // ---- Stub LLM ----

    private static class StubLlmService implements LlmService {
        private String nextOutput;

        void setNextOutput(String output) {
            this.nextOutput = output;
        }

        @Override
        public String generateAnswer(String prompt) {
            return nextOutput;
        }

        @Override
        public String provider() { return "stub"; }

        @Override
        public String modelName() { return "stub"; }
    }
}
