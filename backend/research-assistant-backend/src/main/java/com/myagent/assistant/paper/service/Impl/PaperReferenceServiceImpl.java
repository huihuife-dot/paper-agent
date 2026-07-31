package com.myagent.assistant.paper.service.impl;

import com.myagent.assistant.paper.entity.PaperCategory;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperProfileJob;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSection;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.dto.PaperMetadataUpdateRequest;
import com.myagent.assistant.paper.mapper.PaperProfileJobMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.paper.mapper.PaperSectionMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.paper.service.PaperCategoryService;
import com.myagent.assistant.paper.service.PaperReferenceService;
import com.myagent.assistant.paper.structure.StructuredChunk;
import com.myagent.assistant.paper.structure.StructuredChunkingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.service.PdfParseService;
import com.myagent.assistant.paper.service.PaperAssetService;
import com.myagent.assistant.qdrant.service.QdrantService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;



import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaperReferenceServiceImpl implements PaperReferenceService {


    @Value("${app.upload-dir}")
    private String uploadDir;


    private final PaperReferenceMapper paperReferenceMapper;
    private final PaperChunkMapper paperChunkMapper;
    private final PdfParseService pdfParseService;
    private final QdrantService qdrantService;
    private final PaperCategoryService paperCategoryService;
    private final PaperSectionMapper paperSectionMapper;
    private final StructuredChunkingService structuredChunkingService;
    private final PaperSectionSummaryMapper paperSectionSummaryMapper;
    private final PaperProfileMapper paperProfileMapper;
    private final PaperProfileJobMapper paperProfileJobMapper;
    private final PaperAssetService paperAssetService;

    public PaperReferenceServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                     PaperChunkMapper paperChunkMapper,
                                     PdfParseService pdfParseService,
                                     QdrantService qdrantService,
                                     PaperCategoryService paperCategoryService,
                                     PaperSectionMapper paperSectionMapper,
                                     StructuredChunkingService structuredChunkingService,
                                     PaperSectionSummaryMapper paperSectionSummaryMapper,
                                     PaperProfileMapper paperProfileMapper,
                                     PaperProfileJobMapper paperProfileJobMapper,
                                     PaperAssetService paperAssetService) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperChunkMapper = paperChunkMapper;
        this.pdfParseService = pdfParseService;
        this.qdrantService = qdrantService;
        this.paperCategoryService = paperCategoryService;
        this.paperSectionMapper = paperSectionMapper;
        this.structuredChunkingService = structuredChunkingService;
        this.paperSectionSummaryMapper = paperSectionSummaryMapper;
        this.paperProfileMapper = paperProfileMapper;
        this.paperProfileJobMapper = paperProfileJobMapper;
        this.paperAssetService = paperAssetService;
    }

    @Override
    public List<PaperReference> listPapers(Long categoryId) {
        QueryWrapper<PaperReference> wrapper = new QueryWrapper<>();

        if (categoryId != null) {
            paperCategoryService.ensureCategoryExists(categoryId);
            wrapper.eq("category_id", categoryId);
        }

        wrapper.orderByDesc("upload_time").orderByDesc("id");
        List<PaperReference> papers = paperReferenceMapper.selectList(wrapper);
        fillCategoryNames(papers);
        return papers;
    }




    @Override
    public PaperReference uploadPaper(MultipartFile file, String title, String authors, Integer publishYear,
                                      String journal, String keywords, String remark, Long categoryId) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        PaperCategory category = paperCategoryService.ensureCategoryExists(categoryId);

        try {
            LocalDate now = LocalDate.now();
            Path dir = Path.of(uploadDir, String.valueOf(now.getYear()), String.format("%02d", now.getMonthValue()));
            Files.createDirectories(dir);

            String originalFilename = file.getOriginalFilename();
            String suffix = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String savedFileName = UUID.randomUUID() + suffix;
            Path targetPath = dir.resolve(savedFileName);
            file.transferTo(targetPath.toFile());

            PaperReference paper = new PaperReference();
            paper.setCategoryId(category.getId());
            paper.setCategoryName(category.getName());
            paper.setTitle(title != null && !title.isBlank() ? title : originalFilename);
            paper.setAuthors(authors);
            paper.setPublishYear(publishYear);
            paper.setJournal(journal);
            paper.setKeywords(keywords);
            paper.setFileName(originalFilename);
            paper.setFilePath(targetPath.toString());
            paper.setFileType(suffix);
            paper.setFileSize(file.getSize());
            paper.setParseStatus("PENDING");
            paper.setVectorStatus("PENDING");
            paper.setRemark(remark);

            paperReferenceMapper.insert(paper);
            return paper;
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }


    @Override
    public PaperReference getPaperById(Long id) {
        PaperReference paper = paperReferenceMapper.selectById(id);

        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }

        fillCategoryName(paper);
        return paper;
    }

    @Override
    public PaperReference updatePaperCategory(Long id, Long categoryId) {
        PaperReference paper = paperReferenceMapper.selectById(id);

        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }

        PaperCategory category = paperCategoryService.ensureCategoryExists(categoryId);
        PaperReference update = new PaperReference();
        update.setCategoryId(category.getId());
        paperReferenceMapper.update(
                update,
                new QueryWrapper<PaperReference>().eq("id", id)
        );

        paper.setCategoryId(category.getId());
        paper.setCategoryName(category.getName());
        return paper;
    }

    @Override
    public PaperReference updatePaperMetadata(Long id, PaperMetadataUpdateRequest request) {
        PaperReference paper = paperReferenceMapper.selectById(id);
        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }
        if (request == null) {
            throw new RuntimeException("文献信息不能为空");
        }

        String title = trimToNull(request.getTitle());
        if (title == null) {
            throw new RuntimeException("文献标题不能为空");
        }
        if (title.length() > 500) {
            throw new RuntimeException("文献标题不能超过500个字符");
        }
        if (request.getPublishYear() != null
                && (request.getPublishYear() < 1000 || request.getPublishYear() > 2100)) {
            throw new RuntimeException("发表年份必须在1000到2100之间");
        }

        String journal = trimToNull(request.getJournal());
        if (journal != null && journal.length() > 500) {
            throw new RuntimeException("期刊或会议名称不能超过500个字符");
        }

        // UpdateWrapper 显式 set 每个字段，使用户可以把原有可选信息清空。
        paperReferenceMapper.update(
                null,
                new UpdateWrapper<PaperReference>()
                        .eq("id", id)
                        .set("title", title)
                        .set("authors", trimToNull(request.getAuthors()))
                        .set("publish_year", request.getPublishYear())
                        .set("journal", journal)
                        .set("keywords", trimToNull(request.getKeywords()))
                        .set("abstract_text", trimToNull(request.getAbstractText()))
                        .set("remark", trimToNull(request.getRemark()))
        );

        paper.setTitle(title);
        paper.setAuthors(trimToNull(request.getAuthors()));
        paper.setPublishYear(request.getPublishYear());
        paper.setJournal(journal);
        paper.setKeywords(trimToNull(request.getKeywords()));
        paper.setAbstractText(trimToNull(request.getAbstractText()));
        paper.setRemark(trimToNull(request.getRemark()));
        fillCategoryName(paper);
        return paper;
    }

    @Override
    @Transactional
    public void deletePaper(Long id) {
        PaperReference paper = paperReferenceMapper.selectById(id);

        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }

        Map<String, Object> qdrantDeleteResult = qdrantService.deletePaperPoints(id);
        if (!Boolean.TRUE.equals(qdrantDeleteResult.get("success"))) {
            throw new RuntimeException("删除 Qdrant 向量失败：" + qdrantDeleteResult.getOrDefault("error", "未知错误"));
        }

        paperAssetService.deleteForPaper(id);
        paperProfileJobMapper.delete(new QueryWrapper<PaperProfileJob>().eq("paper_id", id));
        paperProfileMapper.delete(new QueryWrapper<PaperProfile>().eq("paper_id", id));
        paperSectionSummaryMapper.delete(new QueryWrapper<PaperSectionSummary>().eq("paper_id", id));
        paperChunkMapper.delete(new QueryWrapper<PaperChunk>().eq("paper_id", id));
        paperSectionMapper.delete(new QueryWrapper<PaperSection>().eq("paper_id", id));
        paperReferenceMapper.deleteById(id);

        deleteLocalFileIfExists(paper.getFilePath());
    }





    @Override
    public int parsePaper(Long id) {
        // 1. 查询文献信息
        PaperReference paper = paperReferenceMapper.selectById(id);

        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }

        if (paper.getFilePath() == null || paper.getFilePath().isBlank()) {
            throw new RuntimeException("文献文件路径为空");
        }

        // 2. 更新解析状态为处理中
        paper.setParseStatus("PROCESSING");
        paperReferenceMapper.updateById(paper);

        try {
            // 3. 从 PDF 中提取文本
            String text = pdfParseService.parseText(paper.getFilePath());

            if (text == null || text.isBlank()) {
                throw new RuntimeException("未能从 PDF 中解析出文本，可能是扫描版 PDF");
            }

            // 4. 为避免重复解析导致 section/chunk 重复，先删除旧 chunk，再删除旧 section。
            paperChunkMapper.delete(
                    new QueryWrapper<PaperChunk>().eq("paper_id", id)
            );
            paperSectionMapper.delete(
                    new QueryWrapper<PaperSection>().eq("paper_id", id)
            );

            // 5. 将 PDF 文本转换为带章节语义的结构化 chunk。
            List<StructuredChunk> structuredChunks = structuredChunkingService.chunk(paper.getTitle(), text);

            if (structuredChunks.isEmpty()) {
                throw new RuntimeException("未能从 PDF 中生成有效 chunk");
            }

            Map<Integer, PaperSection> sectionsByIndex = new LinkedHashMap<>();

            for (StructuredChunk structuredChunk : structuredChunks) {
                PaperSection section = sectionsByIndex.get(structuredChunk.getSectionIndex());

                if (section == null) {
                    section = new PaperSection();
                    section.setPaperId(id);
                    section.setSectionTitle(structuredChunk.getSectionTitle());
                    section.setSectionType(structuredChunk.getSectionType().name());
                    section.setSectionIndex(structuredChunk.getSectionIndex());
                    section.setContentPreview(previewText(structuredChunk.getContent(), 500));
                    paperSectionMapper.insert(section);
                    sectionsByIndex.put(structuredChunk.getSectionIndex(), section);
                }

                PaperChunk chunk = new PaperChunk();
                chunk.setPaperId(id);
                chunk.setSectionId(section.getId());
                chunk.setSectionTitle(section.getSectionTitle());
                chunk.setSectionType(section.getSectionType());
                chunk.setChunkType("text");
                chunk.setContent(structuredChunk.getContent());
                chunk.setIndexText(structuredChunk.getIndexText());
                chunk.setPageNumber(null);
                chunk.setPageStart(null);
                chunk.setPageEnd(null);
                chunk.setChunkIndex(structuredChunk.getChunkIndex());
                chunk.setTokenCount(structuredChunk.getTokenCount());
                chunk.setCharCount(structuredChunk.getCharCount());
                chunk.setIsReference(structuredChunk.getReference());
                chunk.setIsNoise(structuredChunk.getNoise());
                chunk.setQualityScore(structuredChunk.getQualityScore());
                chunk.setChunkStrategyVersion(structuredChunk.getChunkStrategyVersion());
                chunk.setQdrantPointId(null);

                paperChunkMapper.insert(chunk);
            }

            // 6. 更新解析状态为完成
            paper.setParseStatus("COMPLETED");
            paperReferenceMapper.updateById(paper);

            return structuredChunks.size();
        } catch (RuntimeException e) {
            // 8. 解析失败时更新状态，方便前端展示
            paper.setParseStatus("FAILED");
            paperReferenceMapper.updateById(paper);
            throw e;
        }
    }


    @Override
    public List<PaperChunk> listChunksByPaperId(Long paperId) {
        // 1. 先确认文献是否存在。
        // 如果文献 ID 写错，直接提示“文献不存在”，避免返回一个让人误解的空列表。
        PaperReference paper = paperReferenceMapper.selectById(paperId);

        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }

        // 2. 查询该文献下的所有 chunk。
        // orderByAsc("chunk_index") 表示按 chunk 顺序返回，
        // 这样前端或调试时看到的文本顺序和论文原文顺序基本一致。
        return paperChunkMapper.selectList(
                new QueryWrapper<PaperChunk>()
                        .eq("paper_id", paperId)
                        .orderByAsc("chunk_index")
        );
    }

    @Override
    public Map<String, Object> vectorizePaper(Long paperId) {
        // 1. 查询文献，确认这篇论文真实存在。
        PaperReference paper = paperReferenceMapper.selectById(paperId);

        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }

        // 2. 只有解析完成的论文才有稳定的 chunk 数据。
        // 如果还没解析，应该先调用 POST /api/papers/{id}/parse。
        if (!"COMPLETED".equals(paper.getParseStatus())) {
            throw new RuntimeException("文献尚未解析完成，请先解析 PDF");
        }

        // 3. 查出该论文的所有 chunk，后续每个 chunk 都会生成一个向量。
        List<PaperChunk> chunks = listChunksByPaperId(paperId);

        if (chunks.isEmpty()) {
            throw new RuntimeException("当前文献没有 chunk，请先解析 PDF");
        }

        // 4. 标记为向量化处理中，方便前端或调试时知道当前状态。
        paper.setVectorStatus("PROCESSING");
        paperReferenceMapper.updateById(paper);

        try {
            // 5. 调用 QdrantService 执行真正的写入。
            // 当前写入的是 4 维假 embedding；后面接真实 embedding 时，主要替换这一层的向量生成逻辑。
            Map<String, Object> result = qdrantService.upsertPaperChunks(paperId, chunks);

            if (!Boolean.TRUE.equals(result.get("success"))) {
                throw new RuntimeException(String.valueOf(result.get("error")));
            }

            // 6. 写入 Qdrant 成功后，把每个 chunk 对应的 qdrantPointId 回写到 MySQL。
            // 这里约定：Qdrant point id = paper_chunk.id。
            for (PaperChunk chunk : chunks) {
                chunk.setQdrantPointId(String.valueOf(chunk.getId()));
                paperChunkMapper.updateById(chunk);
            }

            // 7. 更新论文向量化状态为完成。
            paper.setVectorStatus("COMPLETED");
            paperReferenceMapper.updateById(paper);

            return result;
        } catch (RuntimeException e) {
            // 8. 如果中间任何一步失败，记录 FAILED，方便后续排查。
            paper.setVectorStatus("FAILED");
            paperReferenceMapper.updateById(paper);
            throw e;
        }
    }

    @Override
    @Transactional
    public Map<String, Object> reprocessPaperAssets(Long paperId) {
        PaperReference paper = paperReferenceMapper.selectById(paperId);
        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }
        if (paper.getFilePath() == null || paper.getFilePath().isBlank()) {
            throw new RuntimeException("文献文件路径为空");
        }

        Map<String, Object> qdrantDeleteResult = qdrantService.deletePaperPoints(paperId);
        if (!Boolean.TRUE.equals(qdrantDeleteResult.get("success"))) {
            throw new RuntimeException("删除 Qdrant 向量失败：" + qdrantDeleteResult.getOrDefault("error", "未知错误"));
        }

        paperAssetService.deleteForPaper(paperId);
        paperProfileJobMapper.delete(new QueryWrapper<PaperProfileJob>().eq("paper_id", paperId));
        paperProfileMapper.delete(new QueryWrapper<PaperProfile>().eq("paper_id", paperId));
        paperSectionSummaryMapper.delete(new QueryWrapper<PaperSectionSummary>().eq("paper_id", paperId));
        paperChunkMapper.delete(new QueryWrapper<PaperChunk>().eq("paper_id", paperId));
        paperSectionMapper.delete(new QueryWrapper<PaperSection>().eq("paper_id", paperId));

        paper.setParseStatus("PENDING");
        paper.setVectorStatus("PENDING");
        paperReferenceMapper.updateById(paper);

        int chunkCount = parsePaper(paperId);
        Map<String, Object> vectorizeResult = vectorizePaper(paperId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("paperId", paperId);
        result.put("chunkCount", chunkCount);
        result.put("vectorizeResult", vectorizeResult);
        return result;
    }

    private void deleteLocalFileIfExists(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException e) {
            throw new RuntimeException("删除本地文献文件失败", e);
        }
    }

    private String previewText(String content, int maxLength) {
        if (content == null) {
            return "";
        }

        String text = content.replaceAll("\\s+", " ").trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void fillCategoryNames(List<PaperReference> papers) {
        if (papers == null || papers.isEmpty()) {
            return;
        }

        List<Long> categoryIds = papers.stream()
                .map(PaperReference::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (categoryIds.isEmpty()) {
            return;
        }

        Map<Long, PaperCategory> categories = categoryIds.stream()
                .map(paperCategoryService::ensureCategoryExists)
                .collect(Collectors.toMap(PaperCategory::getId, Function.identity(), (left, right) -> left));

        for (PaperReference paper : papers) {
            PaperCategory category = categories.get(paper.getCategoryId());
            if (category != null) {
                paper.setCategoryName(category.getName());
            }
        }
    }

    private void fillCategoryName(PaperReference paper) {
        if (paper == null || paper.getCategoryId() == null) {
            return;
        }

        PaperCategory category = paperCategoryService.ensureCategoryExists(paper.getCategoryId());
        paper.setCategoryName(category.getName());
    }


}
