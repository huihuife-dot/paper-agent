package com.myagent.assistant.rag.service.impl;

import com.myagent.assistant.rag.dto.EvidenceQueryPlan;
import com.myagent.assistant.rag.service.EvidenceQueryPlanner;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 低成本问题规划器。
 *
 * 明确问题由规则直接定位知识类型和章节；无法稳定定位时降低置信度，后续服务自动用 RAG 补漏。
 * 这里有意不为每个问题额外调用一次 LLM，避免“为了省 Token 又新增一次模型调用”。
 */
@Service
public class EvidenceQueryPlannerImpl implements EvidenceQueryPlanner {

    @Override
    public EvidenceQueryPlan plan(String question, List<Long> paperIds) {
        if (question == null || question.isBlank()) {
            throw new RuntimeException("问题不能为空");
        }
        List<Long> normalizedPaperIds = paperIds == null ? List.of() : paperIds.stream()
                .filter(id -> id != null && id > 0).distinct().toList();
        String normalized = question.toLowerCase(Locale.ROOT);
        EvidenceQueryPlan plan = new EvidenceQueryPlan();
        plan.setScope(normalizedPaperIds.isEmpty() ? "LIBRARY"
                : normalizedPaperIds.size() == 1 ? "SINGLE" : "MULTI");

        Set<String> types = new LinkedHashSet<>();
        Set<String> sections = new LinkedHashSet<>();
        String intent = "OVERVIEW";
        String primaryLayer = "PAPER_PROFILE";
        String confidence = "MEDIUM";

        if (containsAny(normalized, "数据集", "数据来源", "样本", "scada", "dataset", "data set")) {
            intent = "DATASET";
            types.add("DATASET");
            types.add("INPUT_VARIABLE");
            sections.addAll(List.of("DATA", "DATASET", "EXPERIMENTS", "EXPERIMENT"));
            primaryLayer = "KNOWLEDGE_UNIT";
            confidence = "HIGH";
        }
        if (containsAny(normalized, "学习率", "批大小", "batch size", "超参数", "参数设置", "实验设置", "训练设置", "implementation detail")) {
            intent = "EXPERIMENT_SETTING";
            types.add("EXPERIMENT_SETTING");
            sections.addAll(List.of("EXPERIMENTS", "EXPERIMENT", "METHODS", "METHOD"));
            primaryLayer = "KNOWLEDGE_UNIT";
            confidence = "HIGH";
        }
        if (containsAny(normalized, "评价指标", "评估指标", "指标", "metric", "mae", "rmse", "accuracy", "precision", "recall")) {
            intent = "METRIC";
            types.add("METRIC");
            sections.addAll(List.of("EXPERIMENTS", "EXPERIMENT", "RESULTS", "RESULT"));
            primaryLayer = "KNOWLEDGE_UNIT";
            confidence = "HIGH";
        }
        if (containsAny(normalized, "实验结果", "结果如何", "提升多少", "性能", "效果", "result", "performance", "对比结果")) {
            intent = "RESULT";
            types.add("RESULT");
            types.add("COMPARISON");
            sections.addAll(List.of("RESULTS", "RESULT", "EXPERIMENTS", "EXPERIMENT"));
            primaryLayer = "KNOWLEDGE_UNIT";
            confidence = "HIGH";
        }
        if (containsAny(normalized, "局限", "不足", "缺点", "限制", "limitation", "weakness")) {
            intent = "LIMITATION";
            types.clear();
            types.add("LIMITATION");
            sections.clear();
            sections.addAll(List.of("LIMITATIONS", "DISCUSSION", "CONCLUSION"));
            primaryLayer = "KNOWLEDGE_UNIT";
            confidence = "HIGH";
        } else if (containsAny(normalized, "贡献", "创新点", "创新之处", "contribution", "novelty")) {
            intent = "CONTRIBUTION";
            types.clear();
            types.add("CONTRIBUTION");
            sections.clear();
            sections.addAll(List.of("ABSTRACT", "INTRODUCTION", "CONCLUSION"));
            primaryLayer = "KNOWLEDGE_UNIT";
            confidence = "HIGH";
        } else if (containsAny(normalized, "什么方法", "使用的方法", "采用什么", "模型结构", "算法", "方法", "模型", "method", "model", "architecture")) {
            intent = "METHOD";
            types.add("METHOD");
            types.add("MODEL_COMPONENT");
            sections.addAll(List.of("METHODS", "METHOD", "METHODOLOGY", "MODEL"));
            primaryLayer = "KNOWLEDGE_UNIT";
            confidence = "HIGH";
        }

        if (containsAny(normalized, "研究问题", "解决什么问题", "研究任务", "研究目标", "task", "problem")) {
            intent = "RESEARCH_TASK";
            types.clear();
            types.add("RESEARCH_TASK");
            types.add("RESEARCH_PROBLEM");
            sections.clear();
            sections.addAll(List.of("ABSTRACT", "INTRODUCTION"));
            primaryLayer = "PAPER_PROFILE";
            confidence = "HIGH";
        }

        boolean broadDiscovery = containsAny(normalized, "哪些论文", "相关论文", "研究方向", "发展趋势", "当前研究", "近年来", "综述", "survey", "trend", "related work");
        boolean fuzzy = containsAny(normalized, "新颖", "值得", "比较好", "相关的", "类似", "可能", "启发", "方向")
                && types.isEmpty();
        boolean crossSection = containsAny(normalized, "全面", "综合", "从整体", "详细介绍", "完整说明", "优缺点", "如何实现")
                && types.isEmpty();

        if (broadDiscovery && "LIBRARY".equals(plan.getScope())) {
            intent = "DISCOVERY";
            primaryLayer = "PAPER_CATALOG";
            confidence = types.isEmpty() ? "MEDIUM" : confidence;
        } else if (fuzzy) {
            intent = "FUZZY";
            primaryLayer = "PAPER_CATALOG";
            confidence = "LOW";
        } else if (crossSection) {
            intent = "CROSS_SECTION";
            primaryLayer = "PROFILE_AND_SECTION";
            confidence = "MEDIUM";
        }

        plan.setIntent(intent);
        plan.setConfidence(confidence);
        plan.setPrimaryLayer(primaryLayer);
        plan.setTargetKnowledgeTypes(List.copyOf(types));
        plan.setTargetSectionTypes(List.copyOf(sections));
        plan.setExplanation(explain(plan));
        return plan;
    }

    private String explain(EvidenceQueryPlan plan) {
        String scope = switch (plan.getScope()) {
            case "SINGLE" -> "单篇论文";
            case "MULTI" -> "多篇论文";
            default -> "整个文献库";
        };
        String layer = switch (plan.getPrimaryLayer()) {
            case "KNOWLEDGE_UNIT" -> "结构化知识单元";
            case "PAPER_CATALOG" -> "论文目录与主题标签";
            case "PROFILE_AND_SECTION" -> "论文画像与相关章节";
            default -> "论文画像";
        };
        return "本轮范围是" + scope + "，优先从" + layer + "确定性取证；证据不足时才追加语义检索。";
    }

    private boolean containsAny(String text, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker)) return true;
        }
        return false;
    }
}
