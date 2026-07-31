package com.myagent.assistant.paper.structure;

import java.util.Locale;

/**
 * 论文标准章节类型。
 *
 * sectionType 是系统归一化标签，不替代论文原始 sectionTitle。
 * 推断不确定时返回 UNKNOWN，避免强行归类。
 */
public enum SectionType {
    TITLE,
    ABSTRACT,
    INTRODUCTION,
    RELATED_WORK,
    METHOD,
    EXPERIMENT,
    RESULT,
    DISCUSSION,
    CONCLUSION,
    REFERENCES,
    APPENDIX,
    BACK_MATTER,
    UNKNOWN;

    public static SectionType fromTitle(String title) {
        if (title == null || title.isBlank()) {
            return UNKNOWN;
        }

        String normalized = normalize(title);

        if (normalized.matches("^(abstract|摘要)$")) {
            return ABSTRACT;
        }
        if (normalized.matches("^(introduction|引言|绪论)$")) {
            return INTRODUCTION;
        }
        if (normalized.contains("related work")
                || normalized.contains("literature review")
                || normalized.contains("background")) {
            return RELATED_WORK;
        }
        if (normalized.contains("method")
                || normalized.contains("methodology")
                || normalized.contains("approach")
                || normalized.contains("framework")
                || normalized.contains("architecture")
                || normalized.contains("model design")
                || normalized.contains("system design")
                || normalized.contains("proposed")) {
            return METHOD;
        }
        if (normalized.contains("experiment")
                || normalized.contains("evaluation")
                || normalized.contains("empirical study")
                || normalized.contains("case study")
                || normalized.contains("ablation")
                || normalized.contains("implementation details")) {
            return EXPERIMENT;
        }
        if (normalized.matches(".*\\bresults?\\b.*")
                || normalized.contains("analysis")
                || normalized.contains("performance")) {
            return RESULT;
        }
        if (normalized.contains("discussion")
                || normalized.contains("limitation")) {
            return DISCUSSION;
        }
        if (normalized.contains("conclusion")
                || normalized.contains("future work")) {
            return CONCLUSION;
        }
        if (normalized.matches("^(references|bibliography|参考文献|参考资料|参考书目)$")) {
            return REFERENCES;
        }
        if (normalized.contains("appendix")
                || normalized.contains("supplementary")) {
            return APPENDIX;
        }
        if (normalized.contains("acknowledg")
                || normalized.contains("author contribution")
                || normalized.contains("contribution statement")
                || normalized.contains("conflict of interest")
                || normalized.contains("competing interest")
                || normalized.contains("data availability")
                || normalized.contains("funding")
                || normalized.contains("ethics statement")) {
            return BACK_MATTER;
        }

        return UNKNOWN;
    }

    public static SectionType infer(String title, String content) {
        SectionType titleType = fromTitle(title);
        if (titleType != UNKNOWN) {
            return titleType;
        }

        if (content == null || content.isBlank()) {
            return UNKNOWN;
        }

        String normalized = normalize(content);

        if (containsAny(normalized, "we propose", "proposed method", "algorithm", "architecture", "module", "training procedure", "model consists")) {
            return METHOD;
        }
        if (containsAny(normalized, "dataset", "baseline", "evaluation", "experiment", "ablation", "metric")) {
            return EXPERIMENT;
        }
        if (containsAny(normalized, "outperform", "performance", "results show", "comparison", "improvement")) {
            return RESULT;
        }
        if (containsAny(normalized, "limitation", "future work", "discussion", "threats to validity")) {
            return DISCUSSION;
        }
        if (containsAny(normalized, "author contributions", "conflict of interest", "data availability", "funding", "acknowledgement", "acknowledgment")) {
            return BACK_MATTER;
        }

        return UNKNOWN;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("^\\s*(\\d+(?:\\.\\d+)*|[ivxlcdm]+)[.)、:-]?\\s+", "")
                .trim();
    }
}
