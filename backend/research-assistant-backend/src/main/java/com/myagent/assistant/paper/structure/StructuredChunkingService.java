package com.myagent.assistant.paper.structure;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化分块服务。
 *
 * 先按章节识别，再在章节内按段落和长度生成 chunk。
 */
@Service
public class StructuredChunkingService {

    public static final String STRATEGY_VERSION = "paper-structure-v1";
    private static final int TARGET_CHARS = 1600;
    private static final int MAX_CHARS = 2200;
    private static final int OVERLAP_CHARS = 200;

    private final PaperTextCleaner textCleaner;
    private final PaperSectionDetector sectionDetector;

    public StructuredChunkingService(PaperTextCleaner textCleaner,
                                     PaperSectionDetector sectionDetector) {
        this.textCleaner = textCleaner;
        this.sectionDetector = sectionDetector;
    }

    public List<StructuredChunk> chunk(String paperTitle, String rawText) {
        String cleanedText = textCleaner.clean(rawText);
        List<DetectedSection> sections = sectionDetector.detect(cleanedText);
        List<StructuredChunk> chunks = new ArrayList<>();

        for (DetectedSection section : sections) {
            List<String> sectionChunks = splitSectionContent(section.getContent());

            for (String content : sectionChunks) {
                if (content.isBlank()) {
                    continue;
                }

                boolean noise = isNoise(content) || section.isLowValueBackMatter();

                chunks.add(new StructuredChunk(
                        section.getTitle(),
                        section.getType(),
                        section.getSectionIndex(),
                        content,
                        buildIndexText(paperTitle, section, content),
                        chunks.size(),
                        estimateTokens(content),
                        content.length(),
                        section.isReference(),
                        noise,
                        qualityScore(content, noise),
                        STRATEGY_VERSION
                ));
            }
        }

        return chunks;
    }

    private List<String> splitSectionContent(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : content.split("\\n\\s*\\n")) {
            String normalized = paragraph.replaceAll("\\s+", " ").trim();
            if (normalized.isBlank()) {
                continue;
            }

            if (current.length() + normalized.length() + 2 <= TARGET_CHARS) {
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
                current.append(normalized);
                continue;
            }

            if (!current.isEmpty()) {
                addWithLongSplit(result, current.toString());
                current.setLength(0);
            }

            addWithLongSplit(result, normalized);
        }

        if (!current.isEmpty()) {
            addWithLongSplit(result, current.toString());
        }

        return result;
    }

    private void addWithLongSplit(List<String> result, String text) {
        if (text.length() <= MAX_CHARS) {
            result.add(text.trim());
            return;
        }

        int step = MAX_CHARS - OVERLAP_CHARS;
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + MAX_CHARS, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                result.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
        }
    }

    private String buildIndexText(String paperTitle, DetectedSection section, String content) {
        return "Paper Title: " + safe(paperTitle) + "\n"
                + "Section Type: " + section.getType().name() + "\n"
                + "Section Title: " + safe(section.getTitle()) + "\n"
                + "Content:\n" + content;
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private boolean isNoise(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        String normalized = content.trim();
        return normalized.length() < 30;
    }

    private double qualityScore(String content, boolean noise) {
        return noise ? 0.2 : 1.0;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
