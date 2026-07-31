package com.myagent.assistant.paper.structure;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * PDF 文本清洗器。
 *
 * 负责在进入章节识别前清理 PDFBox 抽取文本中的常见噪声，
 * 包括换行规整、断词修复、页码、出版元信息、作者单位和邮箱等。
 */
@Component
public class PaperTextCleaner {

    public String clean(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String text = rawText.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("([A-Za-z])-\\n([A-Za-z])", "$1$2");

        text = Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(line -> !line.matches("^\\d{1,4}$"))
                .filter(line -> !isNoiseLine(line))
                .map(line -> line.replaceAll("[ \\t]+", " "))
                .collect(Collectors.joining("\n"));

        return text.replaceAll("\\n{3,}", "\n\n").trim();
    }

    private boolean isNoiseLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }

        String normalized = line.trim().toLowerCase();
        if (normalized.contains("journal homepage")) {
            return true;
        }
        if (normalized.matches(".*available online\\s+\\d{1,2}\\s+[a-z]+\\s+\\d{4}.*")) {
            return true;
        }
        if (normalized.contains("doi.org") || normalized.startsWith("doi:")) {
            return true;
        }
        if (normalized.contains("©") || normalized.contains("copyright") || normalized.contains("published by")) {
            return true;
        }
        if (normalized.matches(".*\\b(received|revised|accepted)\\b.*\\d{4}.*")) {
            return true;
        }
        if (normalized.contains("corresponding author")) {
            return true;
        }
        if (normalized.contains("e-mail address") || normalized.contains("email address")) {
            return true;
        }
        if (normalized.matches(".*[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}.*")) {
            return true;
        }
        if (normalized.contains("provided proper attribution") && normalized.contains("permission to reproduce")) {
            return true;
        }
        if (normalized.contains("equal contribution") || normalized.contains("work performed while at")) {
            return true;
        }
        if (normalized.contains("conference on neural information processing systems") || normalized.matches(".*\\bnips\\s+\\d{4}.*")) {
            return true;
        }
        if (normalized.replace(" ", "").contains("arxiv:")) {
            return true;
        }
        return normalized.matches("^[a-z]\\s+(school|department|college|faculty|institute|laboratory|university)\\b.*");
    }
}
