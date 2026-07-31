package com.myagent.assistant.paper.structure;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于规则的论文章节识别器。
 *
 * 识别策略：
 * 1. 先用短行标题判断章节边界；
 * 2. sectionTitle 永远保留原始标题；
 * 3. sectionType 用标题规则优先推断，标题不明确时再用内容关键词辅助；
 * 4. 不确定时保留 UNKNOWN，避免强行归类。
 */
@Component
public class PaperSectionDetector {

    public List<DetectedSection> detect(String cleanedText) {
        if (cleanedText == null || cleanedText.isBlank()) {
            return List.of();
        }

        List<SectionDraft> drafts = splitIntoDrafts(cleanedText);
        List<DetectedSection> sections = new ArrayList<>();

        for (SectionDraft draft : drafts) {
            SectionType type = SectionType.infer(draft.title(), draft.content());
            sections.add(new DetectedSection(
                    draft.title(),
                    type,
                    draft.content().trim(),
                    sections.size()
            ));
        }

        return sections;
    }

    private List<SectionDraft> splitIntoDrafts(String cleanedText) {
        List<SectionDraft> drafts = new ArrayList<>();
        String currentTitle = "Unknown";
        StringBuilder currentContent = new StringBuilder();
        boolean seenHeading = false;
        boolean inReferences = false;

        for (String rawLine : cleanedText.split("\n")) {
            String line = rawLine.trim();

            if (line.isBlank()) {
                appendLine(currentContent, "");
                continue;
            }

            boolean isHeading = looksLikeHeading(line);
            SectionType headingType = SectionType.fromTitle(line);

            if (isHeading && !inReferences) {
                if (seenHeading || !currentContent.toString().isBlank()) {
                    addDraft(drafts, currentTitle, currentContent.toString());
                    currentContent.setLength(0);
                }

                currentTitle = normalizeHeadingTitle(line);
                seenHeading = true;
                inReferences = SectionType.REFERENCES.equals(headingType);
                continue;
            }

            appendLine(currentContent, line);
        }

        if (!currentContent.toString().isBlank() || drafts.isEmpty()) {
            addDraft(drafts, currentTitle, currentContent.toString());
        }

        return drafts;
    }

    private SectionType detectHeadingType(String line) {
        if (!looksLikeHeading(line)) {
            return SectionType.UNKNOWN;
        }
        return SectionType.fromTitle(line);
    }

    private boolean looksLikeHeading(String line) {
        if (line.length() > 120) {
            return false;
        }
        if (line.endsWith(".")) {
            return false;
        }
        SectionType type = SectionType.fromTitle(line);
        return type != SectionType.UNKNOWN || looksLikeNumberedShortHeading(line) || looksLikeShortTitleCaseHeading(line);
    }

    private boolean looksLikeNumberedShortHeading(String line) {
        return line.matches("^[0-9IVXLCDMivxlcdm]+[.)、]?\\s+[A-Z][A-Za-z0-9 ,&/-]{2,80}$");
    }

    private boolean looksLikeShortTitleCaseHeading(String line) {
        return line.matches("^[A-Z][A-Za-z0-9 ,&/-]{2,60}$") && line.split("\\s+").length <= 5;
    }

    private String normalizeHeadingTitle(String line) {
        return line.replaceAll("^\\s*(\\d+(?:\\.\\d+)*|[IVXLCDMivxlcdm]+)[.)、:-]?\\s+", "").trim();
    }

    private void appendLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append("\n");
        }
        builder.append(line);
    }

    private void addDraft(List<SectionDraft> drafts, String title, String content) {
        drafts.add(new SectionDraft(title, content == null ? "" : content.trim()));
    }

    private record SectionDraft(String title, String content) {
    }
}
