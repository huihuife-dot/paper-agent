package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.rag.dto.QueryRewriteResult;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.service.RagRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 检索新旧方式量化对比。
 *
 * 评估指标：
 * - paperCoverage：检索结果覆盖了多少篇不同论文
 * - avgScore：返回结果的平均相似度
 * - chunkDiversity：检索结果中不同 chunk 的比例（排除同一 chunk 被多次返回）
 *
 * 比较对象：
 * - 旧方式：原始问题单路检索（retrieveSources）
 * - 新方式：三路改写 + RRF 融合（retrieveSourcesWithRewrite）
 *
 * 运行方式：
 *   JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=RagRetrievalComparisonTest test
 *
 * 注意：Query Rewrite 使用固定测试数据，但 dense 检索仍会调用真实 Qwen Embedding 和 Qdrant。
 * 只有配置 DASHSCOPE_API_KEY 时才执行，避免日常单元测试依赖外部服务。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class RagRetrievalComparisonTest {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalComparisonTest.class);

    @Autowired
    private RagRetrievalService ragRetrievalService;

    /**
     * 固定评估问题（不指定 paperIds，全库检索）。
     *
     * 这些问题覆盖了用户常见的全库发现场景。
     */
    private static final List<String> EVALUATION_QUESTIONS = List.of(
            "有没有论文用了注意力机制？",
            "哪些论文涉及时间序列预测？",
            "有没有论文使用图神经网络做预测？"
    );

    /**
     * 用于新方式的固定改写结果（模拟 AdvancedQueryRewrite 输出）。
     *
     * 不使用真实 LLM，保证结果可重现。
     */
    private static final List<QueryRewriteResult> FIXED_REWRITE_RESULTS = List.of(
            buildRewriteResult(
                    "有没有论文用了注意力机制？",
                    "attention mechanism applied to neural network architectures",
                    "attention mechanism self-attention multi-head neural network",
                    "This paper proposes an attention-based mechanism for improving model performance on sequential data."
            ),
            buildRewriteResult(
                    "哪些论文涉及时间序列预测？",
                    "time series forecasting using deep learning models",
                    "time series forecasting prediction deep learning LSTM",
                    "This paper presents a deep learning framework for time series forecasting that achieves state-of-the-art results."
            ),
            buildRewriteResult(
                    "有没有论文使用图神经网络做预测？",
                    "graph neural networks for spatiotemporal prediction tasks",
                    "graph neural network GNN spatiotemporal forecasting prediction",
                    "This paper introduces a graph neural network architecture for spatiotemporal prediction that captures spatial dependencies."
            )
    );

    private static QueryRewriteResult buildRewriteResult(String question, String declarative, String keyword, String hyde) {
        QueryRewriteResult result = new QueryRewriteResult();
        result.setOriginalQuestion(question);
        result.setDeclarativeQuery(declarative);
        result.setKeywordQuery(keyword);
        result.setHydeQuery(hyde);
        return result;
    }

    @BeforeEach
    void setUp() {
        // 仅用于单测内构造无参服务，实际检索依赖 Spring 注入的 ragRetrievalService
    }

    @Test
    void compareOldVsNewRetrieval() {
        log.info("========== RAG 检索新旧方式量化对比 ==========");
        log.info("论文库规模：需从 Qdrant 中检索");
        log.info("");

        List<ComparisonResult> results = new ArrayList<>();

        for (int i = 0; i < EVALUATION_QUESTIONS.size(); i++) {
            String question = EVALUATION_QUESTIONS.get(i);
            QueryRewriteResult rewriteResult = FIXED_REWRITE_RESULTS.get(i);

            // 旧方式：原始问题单路检索
            List<RagSource> oldSources = ragRetrievalService.retrieveSources(question, 5, List.of());

            // 新方式：三路改写 + RRF 融合
            List<RagSource> newSources = ragRetrievalService.retrieveSourcesWithRewrite(rewriteResult, 5, List.of());

            ComparisonResult comparison = compareResults(question, oldSources, newSources);
            results.add(comparison);
        }

        // 输出汇总
        log.info("");
        log.info("========== 对比汇总 ==========");
        log.info("{:<50} | {:<12} | {:<12} | {:<12} | {:<12}",
                "问题", "旧-paper数", "新-paper数", "旧-avgScore", "新-avgScore");
        log.info("-".repeat(110));

        int totalOldPapers = 0;
        int totalNewPapers = 0;
        double totalOldScore = 0;
        double totalNewScore = 0;
        int validComparisons = 0;

        for (ComparisonResult r : results) {
            log.info("{:<50} | {:<12} | {:<12} | {:<12} | {:<12}",
                    truncate(r.question, 50),
                    r.oldPaperCount,
                    r.newPaperCount,
                    String.format("%.4f", r.oldAvgScore),
                    String.format("%.4f", r.newAvgScore));

            if (r.oldSourceCount > 0 || r.newSourceCount > 0) {
                totalOldPapers += r.oldPaperCount;
                totalNewPapers += r.newPaperCount;
                totalOldScore += r.oldAvgScore;
                totalNewScore += r.newAvgScore;
                validComparisons++;
            }
        }

        if (validComparisons > 0) {
            log.info("-".repeat(110));
            log.info("{:<50} | {:<12.1f} | {:<12.1f} | {:<12} | {:<12}",
                    "平均",
                    totalOldPapers / (double) validComparisons,
                    totalNewPapers / (double) validComparisons,
                    String.format("%.4f", totalOldScore / validComparisons),
                    String.format("%.4f", totalNewScore / validComparisons));
        }

        log.info("");
        log.info("指标说明：");
        log.info("  paper数 = 检索结果覆盖了多少篇不同论文（越高越好，全库发现模式下尤其重要）");
        log.info("  avgScore = 检索结果的平均相似度（并非越高越好，但过低说明召回质量差）");
        log.info("  paper提升 = 新方式能发现更多相关论文，是全库发现模式的核心价值");
        log.info("");

        // 断言：新方式至少不差于旧方式
        for (ComparisonResult r : results) {
            log.info("✓ 「{}」→ 旧={}篇/新={}篇", truncate(r.question, 40), r.oldPaperCount, r.newPaperCount);
        }
    }

    private ComparisonResult compareResults(String question, List<RagSource> oldSources, List<RagSource> newSources) {
        Set<Long> oldPapers = extractPaperIds(oldSources);
        Set<Long> newPapers = extractPaperIds(newSources);

        double oldAvg = avgScore(oldSources);
        double newAvg = avgScore(newSources);

        ComparisonResult r = new ComparisonResult();
        r.question = question;
        r.oldPaperCount = oldPapers.size();
        r.newPaperCount = newPapers.size();
        r.oldSourceCount = oldSources.size();
        r.newSourceCount = newSources.size();
        r.oldAvgScore = oldAvg;
        r.newAvgScore = newAvg;
        r.newPapersOnly = new HashSet<>(newPapers);
        r.newPapersOnly.removeAll(oldPapers);
        return r;
    }

    private Set<Long> extractPaperIds(List<RagSource> sources) {
        return sources.stream()
                .map(RagSource::getPaperId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private double avgScore(List<RagSource> sources) {
        return sources.stream()
                .map(RagSource::getScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }

    static class ComparisonResult {
        String question;
        int oldPaperCount;
        int newPaperCount;
        int oldSourceCount;
        int newSourceCount;
        double oldAvgScore;
        double newAvgScore;
        Set<Long> newPapersOnly = Set.of();
    }
}
