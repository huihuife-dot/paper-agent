package com.myagent.assistant.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.paper.entity.PaperAsset;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.mapper.PaperAssetMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperMultimodalJobMapper;
import com.myagent.assistant.paper.entity.PaperMultimodalJob;
import com.myagent.assistant.paper.service.PaperAssetService;
import com.myagent.assistant.paper.service.MultimodalJobAsyncExecutor;
import com.myagent.assistant.paper.service.PaperReproductionFactService;
import com.myagent.assistant.paper.dto.PaperAssetAnalysisUpdateRequest;
import com.myagent.assistant.vision.service.VisionModelService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDateTime;

@Service
public class PaperAssetServiceImpl implements PaperAssetService {
    private static final String PARSER_VERSION = "page-assets-v3";
    /*
     * PDFBox frequently joins a running header and a caption on the same line.
     * Figure captions are therefore recognized by the punctuation after their
     * numeric label instead of requiring the label to be at the beginning of a line.
     * Tables additionally support the common bare "Table N" line.
     */
    private static final Pattern FIGURE_CAPTION = Pattern.compile(
            "(?i)\\b((?:fig(?:ure)?\\.?)\\s*\\d+[a-z]?)\\s*[.:]\\s*([^\\r\\n]{1,500})");
    private static final Pattern TABLE_CAPTION = Pattern.compile(
            "(?im)(?:^|[^\\p{L}\\p{N}])((?:table)\\s*\\d+[a-z]?)\\s*(?:[.:]\\s*([^\\r\\n]{1,500})|\\r?$)");
    private static final Pattern EQUATION = Pattern.compile("(?m)^\\s*([^\\r\\n]{0,180}(?:=|≈|≤|≥)[^\\r\\n]{0,180})\\s*$");
    private static final Pattern ALGORITHM = Pattern.compile("(?im)^\\s*((?:algorithm|pseudocode)\\s*\\d*[^\\r\\n]*)");
    @Value("${app.data-dir:ResearchAssistantData}") private String dataDir;
    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperAssetMapper paperAssetMapper;
    private final VisionModelService visionModelService;
    private final PaperMultimodalJobMapper jobMapper;
    private final MultimodalJobAsyncExecutor asyncExecutor;
    private final PaperReproductionFactService reproductionFactService;

    public PaperAssetServiceImpl(PaperReferenceMapper paperReferenceMapper, PaperAssetMapper paperAssetMapper,
                                 VisionModelService visionModelService, PaperMultimodalJobMapper jobMapper,
                                 MultimodalJobAsyncExecutor asyncExecutor,
                                 PaperReproductionFactService reproductionFactService) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperAssetMapper = paperAssetMapper;
        this.visionModelService = visionModelService;
        this.jobMapper = jobMapper;
        this.asyncExecutor = asyncExecutor;
        this.reproductionFactService = reproductionFactService;
    }

    @Override @Transactional
    public Map<String, Object> extract(Long paperId) {
        PaperReference paper = requirePaper(paperId);
        reproductionFactService.invalidateForPaper(paperId);
        Path pdf = Path.of(paper.getFilePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(pdf)) throw new RuntimeException("PDF 文件不存在");
        String revision = sha256(FilesRead.read(pdf));
        Path root = assetRoot(paperId, revision);
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            paperAssetMapper.delete(new QueryWrapper<PaperAsset>().eq("paper_id", paperId));
            Files.createDirectories(root.resolve("pages"));
            PDFRenderer renderer = new PDFRenderer(document);
            PDFTextStripper stripper = new PDFTextStripper();
            java.util.Set<String> seenEquationHashes = new java.util.HashSet<>();
            int figures = 0, tables = 0;
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                int page = index + 1;
                Path image = root.resolve("pages").resolve(String.format("page-%04d.png", page));
                BufferedImage rendered = renderer.renderImageWithDPI(index, 144);
                ImageIO.write(rendered, "png", image.toFile());
                stripper.setStartPage(page); stripper.setEndPage(page);
                String text = stripper.getText(document);
                insert(paperId, "PAGE_IMAGE", "Page " + page, null, page, relative(image), text, sha256(text + page), revision);
                for (CaptionCandidate candidate : captionCandidates(text)) {
                    String caption = candidate.caption();
                    String nearby = nearbyText(text, candidate.start(), 1800);
                    String structured = "TABLE".equals(candidate.type())
                            ? "{\"format\":\"native-text-table-v1\",\"caption\":\"" + jsonEscape(caption)
                            + "\",\"rawLines\":\"" + jsonEscape(nearby)
                            + "\",\"confidence\":0.45,\"needsReview\":true}"
                            : "{\"format\":\"figure-caption-context-v1\",\"caption\":\"" + jsonEscape(caption)
                            + "\",\"nearbyText\":\"" + jsonEscape(nearby)
                            + "\",\"confidence\":0.65,\"needsReview\":true}";
                    insert(paperId, candidate.type(), candidate.label(), caption, page, relative(image),
                            nearby, structured, sha256(candidate.type() + candidate.label() + page), revision);
                    if ("TABLE".equals(candidate.type())) tables++; else figures++;
                }
                Matcher equations = EQUATION.matcher(text);
                int equationIndex = 0;
                while (equations.find() && equationIndex < 12) {
                    String expression = equations.group(1).replaceAll("\\s+", " ").trim();
                    if (expression.length() < 8 || expression.matches(".*https?://.*")) continue;
                    equationIndex++;
                    String equationHash = sha256("eq" + expression + page);
                    if (!seenEquationHashes.add(equationHash)) continue;
                    String context = nearbyText(text, equations.start(), 500);
                    insert(paperId, "EQUATION", "Equation candidate " + equationIndex, null, page, relative(image), expression,
                            "{\"format\":\"equation-text-v1\",\"expression\":\"" + jsonEscape(expression) + "\",\"nearbyText\":\"" + jsonEscape(context) + "\",\"confidence\":0.55,\"needsReview\":true}", equationHash, revision);
                }
                Matcher algorithms = ALGORITHM.matcher(text);
                while (algorithms.find()) {
                    String title = algorithms.group(1).replaceAll("\\s+", " ").trim();
                    String steps = nearbyText(text, algorithms.start(), 1200);
                    insert(paperId, "ALGORITHM", title, title, page, relative(image), title,
                            "{\"format\":\"algorithm-block-v1\",\"title\":\"" + jsonEscape(title) + "\",\"rawSteps\":\"" + jsonEscape(steps) + "\",\"confidence\":0.6,\"needsReview\":true}", sha256("algorithm" + title + page), revision);
                }
                String normalizedPageText = text.replaceAll("\\s+", " ");
                Matcher encodeProcessDecode = Pattern.compile(
                        "(?i)(?:three|3)\\s+steps?\\s*:\\s*encode\\s*,?\\s*process\\s+and\\s+decode")
                        .matcher(normalizedPageText);
                if (encodeProcessDecode.find()) {
                    String steps = nearbyText(normalizedPageText, Math.max(0, encodeProcessDecode.start() - 160), 1400);
                    insert(paperId, "ALGORITHM", "model architecture", "Encode-process-decode model architecture",
                            page, relative(image), steps,
                            "{\"format\":\"algorithm-block-v1\",\"title\":\"model architecture\",\"rawSteps\":\""
                                    + jsonEscape(steps) + "\",\"confidence\":0.6,\"needsReview\":true}",
                            sha256("algorithm-model-architecture" + page), revision);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("paperId", paperId); result.put("sourceRevision", revision); result.put("pageCount", document.getNumberOfPages());
            result.put("figureCount", figures); result.put("tableCount", tables); result.put("parserVersion", PARSER_VERSION);
            return result;
        } catch (IOException e) { throw new RuntimeException("页面资产提取失败", e); }
    }

    @Override public List<PaperAsset> list(Long paperId, String assetType) {
        requirePaper(paperId); QueryWrapper<PaperAsset> q = new QueryWrapper<PaperAsset>().eq("paper_id", paperId);
        if (assetType != null && !assetType.isBlank()) q.eq("asset_type", assetType.trim().toUpperCase());
        return paperAssetMapper.selectList(q.orderByAsc("page_start").orderByAsc("id"));
    }
    @Override public PaperAsset get(Long paperId, Long assetId) {
        PaperAsset asset = paperAssetMapper.selectOne(new QueryWrapper<PaperAsset>().eq("id", assetId).eq("paper_id", paperId));
        if (asset == null) throw new RuntimeException("论文资产不存在"); return asset;
    }
    @Override public Path resolveContent(Long paperId, Long assetId) {
        PaperAsset asset = get(paperId, assetId); Path root = Path.of(dataDir).toAbsolutePath().normalize().resolve("paper-assets").resolve(String.valueOf(paperId)).normalize();
        Path file = assetRootPath().resolve(asset.getRawAssetPath()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new RuntimeException("资产文件不存在或路径非法"); return file;
    }
    @Override @Transactional public PaperAsset updateAnalysis(Long paperId, Long assetId, PaperAssetAnalysisUpdateRequest request) {
        if (request == null) throw new RuntimeException("修改内容不能为空");
        PaperAsset asset = get(paperId, assetId);
        asset.setStructuredContentJson(request.getStructuredContentJson());
        asset.setSemanticDescription(request.getSemanticDescription());
        asset.setVerificationStatus("USER_CONFIRMED");
        paperAssetMapper.updateById(asset);
        reproductionFactService.invalidateForPaper(paperId);
        return asset;
    }
    @Override @Transactional public PaperAsset confirm(Long paperId, Long assetId, boolean accepted) {
        PaperAsset asset = get(paperId, assetId);
        asset.setVerificationStatus(accepted ? "USER_CONFIRMED" : "REJECTED");
        paperAssetMapper.updateById(asset);
        reproductionFactService.invalidateForPaper(paperId);
        return asset;
    }
    @Override @Transactional public PaperAsset analyze(Long paperId, Long assetId) {
        PaperAsset asset = get(paperId, assetId);
        ensureVisionEligible(asset, true);
        String analysisVersion = visionModelService.analysisVersion();
        if (analysisVersion.equals(asset.getAnalysisVersion()) && asset.getSemanticDescription() != null && !asset.getSemanticDescription().isBlank()) return asset;
        String result = visionModelService.analyze(asset.getAssetType(), asset.getCaption(), asset.getRawText(), resolveContent(paperId, assetId));
        asset.setSemanticDescription(result); asset.setVerificationStatus("MODEL_INFERRED"); asset.setExtractionMethod("HYBRID");
        asset.setAnalysisVersion(analysisVersion); paperAssetMapper.updateById(asset);
        reproductionFactService.invalidateForPaper(paperId);
        return asset;
    }
    @Override public Map<String, Object> analyzeEligible(Long paperId) {
        requirePaper(paperId);
        List<PaperAsset> candidates = list(paperId, null).stream().filter(this::automaticVisionEligible).toList();
        PaperMultimodalJob job = new PaperMultimodalJob();
        job.setPaperId(paperId); job.setJobType("VISION_ANALYSIS"); job.setStatus("RUNNING");
        job.setTotalItems(candidates.size()); job.setProcessedItems(0); job.setFailedItems(0);
        job.setProvider("qwen"); job.setModel(visionModelService.analysisVersion()); job.setStartedAt(LocalDateTime.now());
        jobMapper.insert(job);
        List<Long> candidateIds = candidates.stream().map(PaperAsset::getId).toList();
        asyncExecutor.executeAsync(() -> executeVisionJob(job.getId(), paperId, candidateIds));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", job.getId()); result.put("status", "RUNNING"); result.put("totalItems", candidates.size());
        result.put("processedItems", 0); result.put("failedItems", 0);
        return result;
    }
    private void executeVisionJob(Long jobId, Long paperId, List<Long> candidateIds) {
        PaperMultimodalJob job = jobMapper.selectById(jobId);
        List<String> failures = new ArrayList<>();
        int processed = 0;
        for (Long assetId : candidateIds) {
            try { analyze(paperId, assetId); processed++; }
            catch (RuntimeException e) { failures.add("asset " + assetId + ": " + e.getMessage()); }
            job.setProcessedItems(processed); job.setFailedItems(failures.size()); jobMapper.updateById(job);
        }
        job.setStatus(failures.isEmpty() ? "COMPLETED" : processed > 0 ? "PARTIAL_SUCCESS" : "FAILED");
        job.setErrorMessage(failures.isEmpty() ? null : String.join("; ", failures));
        job.setFinishedAt(LocalDateTime.now()); jobMapper.updateById(job);
    }
    @Override public Map<String, Object> latestAnalysisJob(Long paperId) {
        requirePaper(paperId);
        PaperMultimodalJob job = jobMapper.selectOne(new QueryWrapper<PaperMultimodalJob>().eq("paper_id", paperId)
                .eq("job_type", "VISION_ANALYSIS").orderByDesc("id").last("LIMIT 1"));
        if (job == null) return Map.of("paperId", paperId, "status", "NOT_STARTED");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paperId", paperId); result.put("jobId", job.getId()); result.put("status", job.getStatus());
        result.put("totalItems", job.getTotalItems()); result.put("processedItems", job.getProcessedItems());
        result.put("failedItems", job.getFailedItems()); result.put("model", job.getModel());
        result.put("errorMessage", job.getErrorMessage()); return result;
    }
    @Override @Transactional public void deleteForPaper(Long paperId) {
        reproductionFactService.invalidateForPaper(paperId);
        jobMapper.delete(new QueryWrapper<PaperMultimodalJob>().eq("paper_id", paperId));
        paperAssetMapper.delete(new QueryWrapper<PaperAsset>().eq("paper_id", paperId));
        Path root = Path.of(dataDir).toAbsolutePath().normalize().resolve("paper-assets").resolve(String.valueOf(paperId));
        if (Files.exists(root)) try (var paths = Files.walk(root)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException e) { throw new RuntimeException(e); } }); } catch (IOException e) { throw new RuntimeException("清理论文资产失败", e); }
    }
    private void insert(Long paperId, String type, String label, String caption, int page, String path, String text, String hash, String revision) { insert(paperId, type, label, caption, page, path, text, null, hash, revision); }
    private void insert(Long paperId, String type, String label, String caption, int page, String path, String text, String structured, String hash, String revision) {
        PaperAsset a = new PaperAsset(); a.setPaperId(paperId); a.setAssetType(type); a.setAssetLabel(label); a.setCaption(caption); a.setPageStart(page); a.setPageEnd(page); a.setRawAssetPath(path); a.setRawText(text); a.setStructuredContentJson(structured); a.setExtractionMethod("PDF_NATIVE"); a.setExtractionConfidence(defaultConfidence(type)); a.setVerificationStatus("EXTRACTED"); a.setContentHash(hash); a.setParserVersion(PARSER_VERSION); a.setSourceRevision(revision); paperAssetMapper.insert(a);
    }
    private PaperReference requirePaper(Long id) { PaperReference p = paperReferenceMapper.selectById(id); if (p == null) throw new RuntimeException("文献不存在"); return p; }
    private Path assetRoot(Long id, String revision) { return Path.of(dataDir).toAbsolutePath().normalize().resolve("paper-assets").resolve(String.valueOf(id)).resolve(revision); }
    private String relative(Path path) { return assetRootPath().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'); }
    private Path assetRootPath() { return Path.of(dataDir).toAbsolutePath().normalize().resolve("paper-assets"); }
    private static String sha256(String value) { return sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static String jsonEscape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private void ensureVisionEligible(PaperAsset asset, boolean explicit) {
        if (!List.of("FIGURE", "TABLE", "EQUATION").contains(asset.getAssetType())) throw new RuntimeException("该资产类型不需要视觉分析");
        if ("USER_CONFIRMED".equals(asset.getVerificationStatus()) || "REJECTED".equals(asset.getVerificationStatus())) throw new RuntimeException("人工已确认或拒绝的资产不能自动覆盖");
        if (!explicit && !automaticVisionEligible(asset)) throw new RuntimeException("资产未达到自动视觉分析门槛");
    }
    private boolean automaticVisionEligible(PaperAsset asset) {
        if (!List.of("FIGURE", "TABLE", "EQUATION").contains(asset.getAssetType())) return false;
        if ("USER_CONFIRMED".equals(asset.getVerificationStatus()) || "REJECTED".equals(asset.getVerificationStatus())) return false;
        if (visionModelService.analysisVersion().equals(asset.getAnalysisVersion()) && asset.getSemanticDescription() != null) return false;
        if ("FIGURE".equals(asset.getAssetType())) {
            String caption = String.valueOf(asset.getCaption()).toLowerCase();
            return caption.matches(".*(architecture|framework|flowchart|network|structure|pipeline|workflow).*");
        }
        String reproductionText = (String.valueOf(asset.getCaption()) + " " + String.valueOf(asset.getRawText())).toLowerCase();
        if ("TABLE".equals(asset.getAssetType())) {
            return (asset.getExtractionConfidence() == null || asset.getExtractionConfidence() < 0.7)
                    && reproductionText.matches(".*(parameter|configuration|setting|algorithm|hyperparameter).*");
        }
        return (asset.getExtractionConfidence() == null || asset.getExtractionConfidence() < 0.7)
                && reproductionText.matches(".*(loss|objective|input|output|where|defined|learning|optimization).*");
    }
    private double defaultConfidence(String type) {
        return switch (type) {
            case "TABLE" -> 0.45;
            case "EQUATION" -> 0.55;
            case "ALGORITHM" -> 0.60;
            case "FIGURE" -> 0.65;
            default -> 1.0;
        };
    }
    private static String nearbyText(String text, int start, int length) { return text.substring(start, Math.min(text.length(), start + length)).replaceAll("\\s+", " ").trim(); }
    static List<CaptionCandidate> captionCandidates(String text) {
        if (text == null || text.isBlank()) return List.of();
        Map<String, CaptionCandidate> candidates = new LinkedHashMap<>();
        collectCaptions(text, FIGURE_CAPTION, "FIGURE", candidates);
        collectCaptions(text, TABLE_CAPTION, "TABLE", candidates);
        return new ArrayList<>(candidates.values());
    }
    private static void collectCaptions(String text, Pattern pattern, String type,
                                        Map<String, CaptionCandidate> candidates) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String label = matcher.group(1).replaceAll("\\s+", " ").trim();
            String detail = matcher.groupCount() >= 2 && matcher.group(2) != null
                    ? matcher.group(2).replaceAll("\\s+", " ").trim() : "";
            String caption = detail.isBlank() ? label : label + ". " + detail;
            String key = type + "|" + label.toLowerCase(java.util.Locale.ROOT)
                    .replace("figure", "fig").replace(".", "").replaceAll("\\s+", "");
            candidates.putIfAbsent(key, new CaptionCandidate(type, label, caption, matcher.start(1)));
        }
    }
    record CaptionCandidate(String type, String label, String caption, int start) {}
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static final class FilesRead { static byte[] read(Path path) { try { return Files.readAllBytes(path); } catch (IOException e) { throw new RuntimeException(e); } } }
}
