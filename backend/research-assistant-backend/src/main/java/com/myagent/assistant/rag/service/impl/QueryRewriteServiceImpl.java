package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.service.QueryRewriteService;
import org.springframework.stereotype.Service;

/**
 * RAG 检索问题改写实现。
 */
@Service
public class QueryRewriteServiceImpl implements QueryRewriteService {

    private final LlmService llmService;

    public QueryRewriteServiceImpl(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String rewriteForRetrieval(String question) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("检索问题不能为空");
        }

        // 如果不包含中文，说明大概率已经是英文问题，直接用于检索。
        if (!containsChinese(question)) {
            return question;
        }

        String prompt = """
                  你是论文检索 query 改写助手。

                  请把下面的中文问题翻译并改写成适合检索英文论文片段的英文 query。

                  要求：
                  1. 只输出英文 query，不要解释。
                  2. 保留专业术语、缩写和关键信息。
                  3. 如果中文里有论文领域术语，请翻译成常见英文学术表达。
                  4. 不要添加原问题没有的限定条件。

                  中文问题：
                  %s
                  """.formatted(question);

        String rewritten = llmService.generateAnswer(prompt);

        if (rewritten == null || rewritten.isBlank()) {
            return question;
        }

        return cleanQuery(rewritten);
    }

    /**
     * 判断文本中是否包含中文字符。
     */
    private boolean containsChinese(String text) {
        return text != null && text.matches(".*[\\u4e00-\\u9fa5].*");
    }

    /**
     * 清理模型输出，避免引号、换行、说明文字影响检索。
     */
    private String cleanQuery(String text) {
        String query = text.replaceAll("\\s+", " ").trim();

        if (query.startsWith("\"") && query.endsWith("\"") && query.length() > 1) {
            query = query.substring(1, query.length() - 1);
        }

        return query;
    }
}