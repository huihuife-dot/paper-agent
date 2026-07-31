package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.rag.dto.RagSource;
import com.myagent.assistant.rag.service.IdeaSuggestionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Research Idea 建议服务实现。
 *
 * 触发策略分三种：
 * - 触发 1（零成本）：用户问题中明确表达了改进/优化/建议/未来方向意图。
 * - 触发 2（轻量 LLM 分类）：问题包含研究导向词，回答可能包含实质 idea。
 * - 触发 3：前端手动"保存想法"按钮，不经过本服务。
 */
@Service
public class IdeaSuggestionServiceImpl implements IdeaSuggestionService {

    /**
     * 触发 1：用户主动寻求改进、优化、建议或未来研究方向的意图模式。
     *
     * 这些模式比旧版 45 个关键词更精准，不会在普通知识问答中误触发。
     */
    private static final Pattern IMPROVEMENT_INTENT = Pattern.compile(
            "怎么改进|如何改进|怎样改进|如何优化|怎么优化|怎样优化|"
                    + "有什么建议|有哪些建议|给些建议|给点建议|有什么推荐|"
                    + "改进建议|优化建议|研究建议|改进方向|优化方向|研究方向|未来方向|"
                    + "未来研究|进一步研究|后续研究|下一步|"
                    + "还有什么可以|还可以做什么|还能做什么|"
                    + "值得探索|值得研究|值得深入|可以尝试|"
                    + "可以怎么提升|哪些方面可以改进|有没有更好的|"
                    + "怎么提升|如何提升|怎样提升|"
                    + "有什么不足|有哪些局限|局限性是什么|"
                    + "limitation|future work|future research|improvement|"
                    + "可以怎么扩展|如何扩展|怎么扩展"
    );

    /**
     * 触发 2 门控：问题中包含研究导向词时才值得做 LLM 分类。
     *
     * 门控词比旧版关键词更聚焦"方向性"而非"知识性"，
     * 避免纯知识问答（如"这个方法是什么"）也触发 LLM 分类。
     */
    private static final Pattern RESEARCH_GATE = Pattern.compile(
            "改进|优化|建议|方向|探索|未来|提升|扩展|不足|局限|"
                    + "尝试|替代|对比|比较|创新|新方法|新思路|"
                    + "improve|optimize|suggest|explore|future|limitation|"
                    + "extension|alternative|compare|novel|innovative"
    );

    /**
     * 触发 2 分类：回答最多取前 800 个字符，降低 token 消耗。
     */
    private static final int CLASSIFY_ANSWER_MAX_CHARS = 800;

    private final LlmService llmService;

    public IdeaSuggestionServiceImpl(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public boolean shouldSuggestSaveAsIdea(String question, String answer, List<RagSource> sources) {
        if (answer == null || answer.isBlank()) {
            return false;
        }

        // 触发 1：问题中明确表达了寻求改进/建议的意图。
        if (detectImprovementIntent(question)) {
            return true;
        }

        // 触发 2：问题包含研究导向词时，用 LLM 判断回答是否包含实质 idea。
        if (passesResearchGate(question)) {
            return classifyAnswerQuality(question, answer);
        }

        return false;
    }

    @Override
    public String buildSuggestionReason(String question, String answer, List<RagSource> sources) {
        if (answer == null || answer.isBlank()) {
            return null;
        }

        // 触发 1 命中 → 用户主动寻求改进
        if (detectImprovementIntent(question)) {
            return "你询问了改进或优化建议，这轮讨论可以保存为研究想法草稿，方便后续回顾。";
        }

        // 触发 2 命中 → LLM 判断回答有实质 idea
        if (passesResearchGate(question) && classifyAnswerQuality(question, answer)) {
            return "这轮回答包含值得进一步探索的研究方向或改进思路，建议保存为研究想法。";
        }

        return null;
    }

    /**
     * 触发 1：检测用户问题是否明确表达改进/建议意图。
     */
    boolean detectImprovementIntent(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return IMPROVEMENT_INTENT.matcher(question).find();
    }

    /**
     * 触发 2 门控：问题是否包含研究导向词。
     *
     * 纯知识问答（如"这篇论文的贡献是什么"）不会通过门控，
     * 只有带方向性/探索性的问题才值得进一步判断。
     */
    boolean passesResearchGate(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return RESEARCH_GATE.matcher(question).find();
    }

    /**
     * 触发 2 核心：用小 LLM 调用判断回答是否包含实质研究 idea。
     *
     * 分类 prompt 非常短（~120 token），仅要求回答"是"或"否"。
     */
    boolean classifyAnswerQuality(String question, String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }

        String truncatedAnswer = answer.length() <= CLASSIFY_ANSWER_MAX_CHARS
                ? answer
                : answer.substring(0, CLASSIFY_ANSWER_MAX_CHARS);

        String prompt = "判断以下AI回答是否包含一个值得保存为「研究想法」的具体内容。\n"
                + "研究想法指：具体的研究方向、改进方案、实验设计、方法创新等，\n"
                + "而不是一般的知识问答或论文内容总结。\n\n"
                + "用户问题：" + (question == null ? "" : question) + "\n"
                + "AI回答：" + truncatedAnswer + "\n\n"
                + "仅回答「是」或「否」。";

        try {
            String result = llmService.generateAnswer(prompt);
            if (result == null || result.isBlank()) {
                return false;
            }
            String normalized = result.trim();
            return normalized.startsWith("是") || normalized.equalsIgnoreCase("yes");
        } catch (RuntimeException e) {
            // 分类 LLM 调用失败时保守处理：不阻塞回答流程，不弹窗。
            return false;
        }
    }
}
