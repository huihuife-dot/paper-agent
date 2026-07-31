package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.llm.LlmService;
import com.myagent.assistant.paper.dto.PaperProfileResponse;
import com.myagent.assistant.paper.dto.PaperProfileResult;
import com.myagent.assistant.paper.dto.PaperSectionSummaryResponse;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSection;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.paper.service.PaperProfileProgressListener;
import com.myagent.assistant.paper.service.PaperProfileService;
import com.myagent.assistant.qdrant.service.QdrantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文献画像服务实现。
 */
@Service
public class PaperProfileServiceImpl implements PaperProfileService {

    private static final String SECTION_SUMMARY_VERSION = "section-summary-v1";
    private static final String PAPER_PROFILE_VERSION = "paper-profile-v1";
    private static final int MAX_SECTION_CONTEXT_CHARS = 8000;
    private static final int MAX_PROFILE_EVIDENCE_CHARS = 12000;

    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperSectionMapper paperSectionMapper;
    private final PaperChunkMapper paperChunkMapper;
    private final PaperSectionSummaryMapper sectionSummaryMapper;
    private final PaperProfileMapper paperProfileMapper;
    private final LlmService llmService;
    private final QdrantService qdrantService;

    private static final Logger log = LoggerFactory.getLogger(PaperProfileServiceImpl.class);

    public PaperProfileServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                   PaperSectionMapper paperSectionMapper,
                                   PaperChunkMapper paperChunkMapper,
                                   PaperSectionSummaryMapper sectionSummaryMapper,
                                   PaperProfileMapper paperProfileMapper,
                                   LlmService llmService,
                                   QdrantService qdrantService) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperSectionMapper = paperSectionMapper;
        this.paperChunkMapper = paperChunkMapper;
        this.sectionSummaryMapper = sectionSummaryMapper;
        this.paperProfileMapper = paperProfileMapper;
        this.llmService = llmService;
        this.qdrantService = qdrantService;
    }

    @Override
    public PaperProfileResult generateProfile(Long paperId) {
        return generateProfile(paperId, PaperProfileProgressListener.NOOP);
    }

    @Override
    public PaperProfileResult generateProfile(Long paperId, PaperProfileProgressListener listener) {
        PaperProfileProgressListener progressListener = listener == null ? PaperProfileProgressListener.NOOP : listener;
        progressListener.onPreparing();

        PaperReference paper = requireParsedPaper(paperId);
        List<PaperSection> sections = listSections(paperId);
        List<PaperChunk> usableChunks = listUsableChunks(paperId);

        if (usableChunks.isEmpty()) {
            throw new RuntimeException("当前论文没有可用于生成画像的正文内容");
        }

        Map<Long, PaperSection> sectionsById = sections.stream()
                .filter(section -> section.getId() != null)
                .collect(Collectors.toMap(PaperSection::getId, section -> section, (a, b) -> a, LinkedHashMap::new));
        Map<Long, List<PaperChunk>> chunksBySection = groupChunksBySection(usableChunks);
        List<PaperSectionSummary> savedSummaries = new ArrayList<>();
        int totalSections = chunksBySection.size();
        int processedSections = 0;

        for (Map.Entry<Long, List<PaperChunk>> entry : chunksBySection.entrySet()) {
            List<PaperChunk> summaryChunks = entry.getValue().stream()
                    .filter(this::isSummarizableChunk)
                    .toList();
            if (summaryChunks.isEmpty()) {
                continue;
            }
            PaperSection section = sectionsById.get(entry.getKey());
            PaperSectionSummary summary = generateAndUpsertSectionSummary(paper, section, entry.getKey(), summaryChunks);
            savedSummaries.add(summary);
            processedSections++;
            progressListener.onSectionProgress(processedSections, totalSections);
        }

        progressListener.onGeneratingProfile();
        PaperProfile profile = generateAndUpsertProfile(paper, savedSummaries, usableChunks);
        progressListener.onSavingResult();

        // 将画像和摘要写入 Qdrant，供全库检索使用
        writeProfileAndSummariesToQdrant(profile, savedSummaries);

        return new PaperProfileResult(toProfileResponse(profile), savedSummaries.stream().map(this::toSummaryResponse).toList());
    }

    @Override
    public PaperProfileResult getProfile(Long paperId) {
        requirePaper(paperId);
        PaperProfile profile = paperProfileMapper.selectOne(new QueryWrapper<PaperProfile>()
                .eq("paper_id", paperId)
                .eq("profile_version", PAPER_PROFILE_VERSION));
        List<PaperSectionSummary> summaries = sectionSummaryMapper.selectList(new QueryWrapper<PaperSectionSummary>()
                .eq("paper_id", paperId)
                .eq("summary_version", SECTION_SUMMARY_VERSION)
                .orderByAsc("section_id"));
        return new PaperProfileResult(
                profile == null ? null : toProfileResponse(profile),
                summaries.stream().map(this::toSummaryResponse).toList()
        );
    }

    private PaperReference requireParsedPaper(Long paperId) {
        PaperReference paper = requirePaper(paperId);
        if (!"COMPLETED".equals(paper.getParseStatus())) {
            throw new RuntimeException("请先解析 PDF 后再生成文献画像");
        }
        return paper;
    }

    private PaperReference requirePaper(Long paperId) {
        if (paperId == null || paperId <= 0) {
            throw new RuntimeException("paperId 不能为空");
        }
        PaperReference paper = paperReferenceMapper.selectById(paperId);
        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }
        return paper;
    }

    private List<PaperSection> listSections(Long paperId) {
        return paperSectionMapper.selectList(new QueryWrapper<PaperSection>()
                .eq("paper_id", paperId)
                .orderByAsc("section_index"));
    }

    private List<PaperChunk> listUsableChunks(Long paperId) {
        return paperChunkMapper.selectList(new QueryWrapper<PaperChunk>()
                        .eq("paper_id", paperId)
                        .orderByAsc("chunk_index"))
                .stream()
                .filter(this::isUsableChunk)
                .toList();
    }

    private boolean isUsableChunk(PaperChunk chunk) {
        if (chunk == null || chunk.getContent() == null || chunk.getContent().isBlank()) {
            return false;
        }
        if (Boolean.TRUE.equals(chunk.getIsReference()) || Boolean.TRUE.equals(chunk.getIsNoise())) {
            return false;
        }
        String sectionType = chunk.getSectionType();
        return !"REFERENCES".equals(sectionType) && !"BACK_MATTER".equals(sectionType);
    }

    private boolean isSummarizableChunk(PaperChunk chunk) {
        if (!isUsableChunk(chunk)) {
            return false;
        }
        String chunkType = chunk.getChunkType();
        if ("table".equals(chunkType) || "figure_caption".equals(chunkType)) {
            return false;
        }
        return nullToDefault(chunk.getContent(), "").length() >= 20;
    }

    private Map<Long, List<PaperChunk>> groupChunksBySection(List<PaperChunk> chunks) {
        Map<Long, List<PaperChunk>> result = new LinkedHashMap<>();
        for (PaperChunk chunk : chunks) {
            Long sectionId = chunk.getSectionId() != null ? chunk.getSectionId() : -chunk.getId();
            result.computeIfAbsent(sectionId, ignored -> new ArrayList<>()).add(chunk);
        }
        return result;
    }

    private PaperSectionSummary generateAndUpsertSectionSummary(PaperReference paper,
                                                                PaperSection section,
                                                                Long sectionId,
                                                                List<PaperChunk> chunks) {
        PaperChunk firstChunk = chunks.get(0);
        String sectionType = section != null ? section.getSectionType() : nullToDefault(firstChunk.getSectionType(), "UNKNOWN");
        String sectionTitle = section != null ? section.getSectionTitle() : nullToDefault(firstChunk.getSectionTitle(), "Unknown");
        String prompt = buildSectionSummaryPrompt(paper, sectionType, sectionTitle, chunks);
        String llmText = llmService.generateAnswer(prompt);

        PaperSectionSummary summary = new PaperSectionSummary();
        String summaryText = extractBetween(llmText, "摘要：", "关键点：");
        String keyPointsText = extractAfter(llmText, "关键点：");
        if ("信息不足".equals(summaryText) && hasUsefulLlmText(llmText)) {
            summaryText = fallbackSummary(llmText);
        }
        if (keyPointsText.isBlank() && hasUsefulLlmText(llmText)) {
            keyPointsText = fallbackKeyPoints(llmText);
        }

        summary.setPaperId(paper.getId());
        summary.setSectionId(sectionId);
        summary.setSectionType(sectionType);
        summary.setSectionTitle(sectionTitle);
        summary.setSummary(summaryText);
        summary.setKeyPoints(keyPointsText);
        summary.setSourceChunkIds(chunks.stream().map(PaperChunk::getId).filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(",")));
        summary.setSourceTokenCount(chunks.stream().mapToInt(this::tokenCount).sum());
        summary.setSummaryVersion(SECTION_SUMMARY_VERSION);
        return upsertSectionSummary(summary);
    }

    private String buildSectionSummaryPrompt(PaperReference paper, String sectionType, String sectionTitle, List<PaperChunk> chunks) {
        StringBuilder content = new StringBuilder();
        int usedChars = 0;
        for (PaperChunk chunk : chunks) {
            String normalized = normalize(chunk.getContent());
            if (normalized.isBlank()) {
                continue;
            }
            int remaining = MAX_SECTION_CONTEXT_CHARS - usedChars;
            if (remaining <= 0) {
                break;
            }
            if (normalized.length() > remaining) {
                normalized = normalized.substring(0, remaining);
            }
            content.append(normalized).append("\n\n");
            usedChars += normalized.length();
        }

        return "你是论文阅读助手。请根据下面某篇论文的一个章节或小章节内容，生成章节摘要。\n\n"
                + "要求：\n"
                + "1. 用中文。\n"
                + "2. 如果内容包含有效方法、实验、结果、讨论或结论，请正常总结。\n"
                + "3. 只有当内容完全是页眉、版权、作者单位、参考文献、无上下文表格数字时，才说明信息不足。\n"
                + "4. 摘要控制在 150~300 字。\n"
                + "5. 提取 3~6 条关键点。\n"
                + "6. 不要编造章节中没有的信息。\n\n"
                + "输出格式：\n"
                + "摘要：...\n"
                + "关键点：\n- ...\n\n"
                + "论文标题：" + nullToDefault(paper.getTitle(), "") + "\n"
                + "章节类型：" + nullToDefault(sectionType, "UNKNOWN") + "\n"
                + "章节标题：" + nullToDefault(sectionTitle, "") + "\n"
                + "章节正文：\n" + content;
    }

    private PaperSectionSummary upsertSectionSummary(PaperSectionSummary summary) {
        PaperSectionSummary existing = sectionSummaryMapper.selectOne(new QueryWrapper<PaperSectionSummary>()
                .eq("paper_id", summary.getPaperId())
                .eq("section_id", summary.getSectionId())
                .eq("summary_version", summary.getSummaryVersion()));
        if (existing == null) {
            sectionSummaryMapper.insert(summary);
            return summary;
        }
        summary.setId(existing.getId());
        sectionSummaryMapper.updateById(summary);
        return summary;
    }

    private PaperProfile generateAndUpsertProfile(PaperReference paper, List<PaperSectionSummary> summaries, List<PaperChunk> evidenceChunks) {
        String prompt = buildProfilePrompt(paper, summaries, evidenceChunks);
        String llmText = llmService.generateAnswer(prompt);

        PaperProfile profile = new PaperProfile();
        profile.setPaperId(paper.getId());
        profile.setTitle(paper.getTitle());
        profile.setResearchProblem(extractBetween(llmText, "研究问题：", "方法概述："));
        profile.setMethodSummary(extractBetween(llmText, "方法概述：", "实验与评估："));
        profile.setExperimentSummary(extractBetween(llmText, "实验与评估：", "主要贡献："));
        profile.setKeyContributions(extractBetween(llmText, "主要贡献：", "局限性："));
        profile.setLimitations(extractBetween(llmText, "局限性：", "关键词："));
        profile.setKeywords(extractBetween(llmText, "关键词：", "完整画像："));
        profile.setProfileText(extractAfter(llmText, "完整画像："));
        if (profile.getProfileText().isBlank()) {
            profile.setProfileText(llmText);
        }
        profile.setSourceSectionSummaryIds(summaries.stream().map(PaperSectionSummary::getId).filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(",")));
        profile.setProfileVersion(PAPER_PROFILE_VERSION);
        return upsertProfile(profile);
    }

    private String buildProfilePrompt(PaperReference paper, List<PaperSectionSummary> summaries, List<PaperChunk> evidenceChunks) {
        String sectionTexts = summaries.stream()
                .sorted(Comparator.comparing(PaperSectionSummary::getSectionId, Comparator.nullsLast(Long::compareTo)))
                .map(summary -> "[" + nullToDefault(summary.getSectionType(), "UNKNOWN") + "] "
                        + nullToDefault(summary.getSectionTitle(), "") + "\n"
                        + "摘要：" + nullToDefault(summary.getSummary(), "") + "\n"
                        + "关键点：" + nullToDefault(summary.getKeyPoints(), ""))
                .collect(Collectors.joining("\n\n"));
        String evidenceText = buildProfileEvidenceText(evidenceChunks);

        return "你是论文/文献 AI 研究助手。请根据下面的章节摘要和原文证据片段，生成整篇论文画像。\n\n"
                + "需要覆盖：\n"
                + "1. 研究问题\n2. 方法概述\n3. 实验与评估\n4. 主要贡献\n5. 局限性\n6. 关键词\n7. 一段适合 RAG 使用的完整画像文本\n\n"
                + "要求：\n"
                + "- 用中文。\n"
                + "- 章节摘要用于快速理解结构，原文证据片段用于补充实验、贡献、局限等容易被摘要遗漏的信息。\n"
                + "- 不要使用 References 或后置声明作为论文正文结论。\n"
                + "- 如果章节摘要中写了信息不足，但原文证据片段包含对应信息，应优先利用原文证据片段。\n"
                + "- 如果章节摘要和原文证据片段都没有某项信息，才写“信息不足”，不要编造。\n\n"
                + "输出格式必须使用以下中文字段名：\n"
                + "研究问题：...\n方法概述：...\n实验与评估：...\n主要贡献：...\n局限性：...\n关键词：...\n完整画像：...\n\n"
                + "论文标题：" + nullToDefault(paper.getTitle(), "") + "\n\n"
                + "章节摘要：\n" + sectionTexts + "\n\n"
                + "原文证据片段：\n" + evidenceText;
    }

    private String buildProfileEvidenceText(List<PaperChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        StringBuilder evidence = new StringBuilder();
        int usedChars = 0;
        for (PaperChunk chunk : chunks) {
            String content = normalize(chunk.getContent());
            if (content.isBlank()) {
                continue;
            }
            String header = "[" + nullToDefault(chunk.getSectionType(), "UNKNOWN") + "] "
                    + nullToDefault(chunk.getSectionTitle(), "") + "\n";
            int remaining = MAX_PROFILE_EVIDENCE_CHARS - usedChars - header.length();
            if (remaining <= 0) {
                break;
            }
            if (content.length() > remaining) {
                content = content.substring(0, remaining);
            }
            evidence.append(header).append(content).append("\n\n");
            usedChars += header.length() + content.length();
        }
        return evidence.toString().trim();
    }

    private PaperProfile upsertProfile(PaperProfile profile) {
        PaperProfile existing = paperProfileMapper.selectOne(new QueryWrapper<PaperProfile>()
                .eq("paper_id", profile.getPaperId())
                .eq("profile_version", profile.getProfileVersion()));
        if (existing == null) {
            paperProfileMapper.insert(profile);
            return profile;
        }
        profile.setId(existing.getId());
        paperProfileMapper.updateById(profile);
        return profile;
    }

    /**
     * 将文献画像和章节摘要写入 Qdrant，供全库检索（LIBRARY_DISCOVERY）时使用。
     * Qdrant 写入失败不影响画像生成主流程。
     */
    private void writeProfileAndSummariesToQdrant(PaperProfile profile, List<PaperSectionSummary> summaries) {
        try {
            qdrantService.upsertProfilePoint(profile);
        } catch (Exception e) {
            log.warn("文献画像写入 Qdrant 失败 paperId={}: {}", profile.getPaperId(), e.getMessage());
        }
        for (PaperSectionSummary summary : summaries) {
            try {
                qdrantService.upsertSummaryPoint(summary);
            } catch (Exception e) {
                log.warn("章节摘要写入 Qdrant 失败 summaryId={}: {}", summary.getId(), e.getMessage());
            }
        }
    }

    private PaperProfileResponse toProfileResponse(PaperProfile profile) {
        PaperProfileResponse response = new PaperProfileResponse();
        response.setId(profile.getId());
        response.setPaperId(profile.getPaperId());
        response.setTitle(profile.getTitle());
        response.setResearchProblem(profile.getResearchProblem());
        response.setMethodSummary(profile.getMethodSummary());
        response.setExperimentSummary(profile.getExperimentSummary());
        response.setKeyContributions(profile.getKeyContributions());
        response.setLimitations(profile.getLimitations());
        response.setKeywords(profile.getKeywords());
        response.setProfileText(profile.getProfileText());
        response.setProfileVersion(profile.getProfileVersion());
        return response;
    }

    private PaperSectionSummaryResponse toSummaryResponse(PaperSectionSummary summary) {
        PaperSectionSummaryResponse response = new PaperSectionSummaryResponse();
        response.setId(summary.getId());
        response.setPaperId(summary.getPaperId());
        response.setSectionId(summary.getSectionId());
        response.setSectionType(summary.getSectionType());
        response.setSectionTitle(summary.getSectionTitle());
        response.setSummary(summary.getSummary());
        response.setKeyPoints(summary.getKeyPoints());
        response.setSourceTokenCount(summary.getSourceTokenCount());
        response.setSummaryVersion(summary.getSummaryVersion());
        return response;
    }

    private int tokenCount(PaperChunk chunk) {
        if (chunk.getTokenCount() != null && chunk.getTokenCount() > 0) {
            return chunk.getTokenCount();
        }
        return Math.max(1, (int) Math.ceil(nullToDefault(chunk.getContent(), "").length() / 4.0));
    }

    private String normalize(String content) {
        return content == null ? "" : content.replaceAll("\\s+", " ").trim();
    }

    private boolean hasUsefulLlmText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() < 20) {
            return false;
        }
        return !normalized.matches(".*(信息不足|无法总结|无法提供).*") || normalized.length() > 80;
    }

    private String fallbackSummary(String text) {
        String normalized = text.replaceAll("(?m)^\\s*[-*]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300);
    }

    private String fallbackKeyPoints(String text) {
        String normalized = text == null ? "" : text.trim();
        String points = normalized.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("-") || line.startsWith("•") || line.matches("^\\d+[.)、].*"))
                .limit(6)
                .collect(Collectors.joining("\n"));
        return points.isBlank() ? "" : points;
    }

    private String extractBetween(String text, String startMarker, String endMarker) {
        String startLabel = labelOf(startMarker);
        String endLabel = labelOf(endMarker);
        String value = extractField(text, startLabel, endLabel);
        return value.isBlank() ? "信息不足" : value;
    }

    private String extractAfter(String text, String marker) {
        return extractField(text, labelOf(marker), null);
    }

    private String extractField(String text, String startLabel, String endLabel) {
        if (text == null || text.isBlank() || startLabel == null || startLabel.isBlank()) {
            return "";
        }

        Pattern startPattern = fieldPattern(startLabel);
        Matcher startMatcher = startPattern.matcher(text);
        if (!startMatcher.find()) {
            return "";
        }

        int valueStart = startMatcher.end();
        int valueEnd = text.length();
        if (endLabel != null && !endLabel.isBlank()) {
            Matcher endMatcher = fieldPattern(endLabel).matcher(text);
            if (endMatcher.find(valueStart)) {
                valueEnd = endMatcher.start();
            }
        }

        return text.substring(valueStart, valueEnd)
                .replaceFirst("^[\\s:：\\-—]+", "")
                .trim();
    }

    private Pattern fieldPattern(String label) {
        String quotedLabel = Pattern.quote(label);
        return Pattern.compile("(?m)^\\s*(?:#{1,6}\\s*)?(?:[-*]\\s*)?(?:\\d+[.、]\\s*)?(?:\\*\\*)?" + quotedLabel + "(?:\\*\\*)?\\s*[:：]?\\s*");
    }

    private String labelOf(String marker) {
        if (marker == null) {
            return "";
        }
        return marker.replace("：", "").replace(":", "").trim();
    }

    private String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
