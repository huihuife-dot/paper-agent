package com.myagent.assistant.paper.controller;

import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.paper.dto.PaperCategoryAssignRequest;
import com.myagent.assistant.paper.dto.PaperMetadataUpdateRequest;
import com.myagent.assistant.paper.dto.PaperProfileJobResponse;
import com.myagent.assistant.paper.dto.PaperProfileIndexResponse;
import com.myagent.assistant.paper.dto.PaperProfileResult;
import com.myagent.assistant.paper.dto.PaperProfileStatusResponse;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.service.PaperProfileJobService;
import com.myagent.assistant.paper.service.PaperProfileIndexService;
import com.myagent.assistant.paper.service.PaperProfileService;
import com.myagent.assistant.paper.service.PaperProfileStatusService;
import com.myagent.assistant.paper.service.PaperReferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;


import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import com.myagent.assistant.paper.entity.PaperChunk;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;

@RestController
public class PaperController {

    private final PaperReferenceService paperReferenceService;
    private final PaperProfileService paperProfileService;
    private final PaperProfileJobService paperProfileJobService;
    private final PaperProfileStatusService paperProfileStatusService;
    private final PaperProfileIndexService paperProfileIndexService;

    public PaperController(PaperReferenceService paperReferenceService,
                           PaperProfileService paperProfileService,
                           PaperProfileJobService paperProfileJobService,
                              PaperProfileStatusService paperProfileStatusService,
                              PaperProfileIndexService paperProfileIndexService) {
        this.paperReferenceService = paperReferenceService;
        this.paperProfileService = paperProfileService;
        this.paperProfileJobService = paperProfileJobService;
        this.paperProfileStatusService = paperProfileStatusService;
        this.paperProfileIndexService = paperProfileIndexService;
    }

    @GetMapping("/api/papers")
    public Result<List<PaperReference>> list(@RequestParam(value = "categoryId", required = false) Long categoryId) {
        return Result.success(paperReferenceService.listPapers(categoryId));
    }

    /**
     * 批量查询文献画像状态。
     *
     * 访问示例：
     * GET /api/papers/profile-status
     */
    @GetMapping("/api/papers/profile-status")
    public Result<List<PaperProfileStatusResponse>> listProfileStatuses() {
        return Result.success(paperProfileStatusService.listProfileStatuses());
    }


    /**
     * 上传文献。
     *
     * 访问示例：
     * POST /api/papers/upload
     *
     * 请求参数：
     * - file: 文件
     * - title: 标题
     * - authors: 作者
     * - publishYear: 发表年份
     * - journal: 期刊
     * - keywords: 关键词
     * - remark: 备注
     */
    @PostMapping("/api/papers/upload")
    public Result<PaperReference> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "authors", required = false) String authors,
            @RequestParam(value = "publishYear", required = false) Integer publishYear,
            @RequestParam(value = "journal", required = false) String journal,
            @RequestParam(value = "keywords", required = false) String keywords,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestParam(value = "categoryId", required = false) Long categoryId
    ) {
        PaperReference paper = paperReferenceService.uploadPaper(file, title, authors, publishYear, journal, keywords, remark, categoryId);
        return Result.success(paper);
    }







    /**
     * 下载文献原始文件。
     *
     * 访问示例：
     * GET /api/papers/1/download
     */
    @GetMapping("/api/papers/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        // 1. 查询文献信息
        PaperReference paper = paperReferenceService.getPaperById(id);

        // 2. 根据数据库中保存的文件路径读取本地文件
        FileSystemResource resource = new FileSystemResource(paper.getFilePath());

        if (!resource.exists()) {
            throw new RuntimeException("本地文件不存在");
        }

        // 3. 对文件名进行 URL 编码，避免中文文件名下载乱码
        String fileName = paper.getFileName() != null ? paper.getFileName() : "paper-file";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        // 4. 返回文件流给浏览器
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }

    /**
     * 在浏览器中直接预览文献原始 PDF。
     */
    @GetMapping("/api/papers/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable Long id) {
        PaperReference paper = paperReferenceService.getPaperById(id);
        FileSystemResource resource = new FileSystemResource(paper.getFilePath());
        if (!resource.exists()) {
            throw new RuntimeException("本地文件不存在");
        }

        String fileName = paper.getFileName() != null ? paper.getFileName() : "paper.pdf";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName)
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }




    /**
     * 删除文献。
     *
     * 访问示例：
     * DELETE /api/papers/1
     */
    @DeleteMapping("/api/papers/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        paperReferenceService.deletePaper(id);
        return Result.success();
    }



    /**
     * 解析文献文本并生成 chunk。
     *
     * 访问示例：
     * POST /api/papers/1/parse
     */
    @PostMapping("/api/papers/{id}/parse")
    public Result<Integer> parse(@PathVariable Long id) {
        int chunkCount = paperReferenceService.parsePaper(id);
        return Result.success(chunkCount);
    }

    /**
     * 查询单篇文献详情。
     *
     * 访问示例：
     * GET /api/papers/1
     *
     * 这个接口用于确认某篇文献是否存在，
     * 也方便后续前端进入文献详情页时展示基础信息。
     */
    @GetMapping("/api/papers/{id}")
    public Result<PaperReference> detail(@PathVariable Long id) {
        return Result.success(paperReferenceService.getPaperById(id));
    }

    /**
     * 修改文献所属分类。
     *
     * 访问示例：
     * PATCH /api/papers/1/category
     */
    @PatchMapping("/api/papers/{id}/category")
    public Result<PaperReference> updateCategory(@PathVariable Long id,
                                                 @RequestBody(required = false) PaperCategoryAssignRequest request) {
        if (request == null || request.getCategoryId() == null) {
            throw new RuntimeException("categoryId 不能为空");
        }

        return Result.success(paperReferenceService.updatePaperCategory(id, request.getCategoryId()));
    }

    /**
     * 修改标题、作者、年份等书目信息，不影响解析和向量状态。
     */
    @PatchMapping("/api/papers/{id}")
    public Result<PaperReference> updateMetadata(@PathVariable Long id,
                                                 @RequestBody(required = false) PaperMetadataUpdateRequest request) {
        return Result.success(paperReferenceService.updatePaperMetadata(id, request));
    }

    /**
     * 查询某篇文献解析后的 chunk 列表。
     *
     * 访问示例：
     * GET /api/papers/1/chunks
     *
     * 这个接口很重要：
     * 后续向量化接口会读取这些 chunk，
     * 然后逐条生成 embedding 并写入 Qdrant。
     */
    @GetMapping("/api/papers/{id}/chunks")
    public Result<List<PaperChunk>> chunks(@PathVariable Long id) {
        return Result.success(paperReferenceService.listChunksByPaperId(id));
    }

    /**
     * 向量化指定文献的 chunk。
     *
     * 访问示例：
     * POST /api/papers/2/vectorize
     *
     * 当前是学习阶段：
     * 先使用 4 维假 embedding 写入 Qdrant，
     * 等完整流程跑通后，再替换成真实 embedding 模型。
     */
    @PostMapping("/api/papers/{id}/vectorize")
    public Result<Map<String, Object>> vectorize(@PathVariable Long id) {
        return Result.success(paperReferenceService.vectorizePaper(id));
    }

    /**
     * 重建指定文献的解析派生资产。
     *
     * 访问示例：
     * POST /api/papers/7/reprocess-assets
     */
    @PostMapping("/api/papers/{id}/reprocess-assets")
    public Result<Map<String, Object>> reprocessPaperAssets(@PathVariable Long id) {
        return Result.success(paperReferenceService.reprocessPaperAssets(id));
    }

    /**
     * 手动生成或更新文献画像。
     *
     * 访问示例：
     * POST /api/papers/7/profile
     */
    @PostMapping("/api/papers/{id}/profile")
    public Result<PaperProfileResult> generateProfile(@PathVariable Long id) {
        return Result.success(paperProfileService.generateProfile(id));
    }

    /**
     * 查询文献画像。
     *
     * 访问示例：
     * GET /api/papers/7/profile
     */
    @GetMapping("/api/papers/{id}/profile")
    public Result<PaperProfileResult> getProfile(@PathVariable Long id) {
        return Result.success(paperProfileService.getProfile(id));
    }

    /**
     * 异步启动文献画像生成。
     *
     * 访问示例：
     * POST /api/papers/7/profile/async
     */
    @PostMapping("/api/papers/{id}/profile/async")
    public Result<PaperProfileJobResponse> startProfileJob(@PathVariable Long id) {
        return Result.success(paperProfileJobService.startProfileJob(id));
    }

    /**
     * 查询文献画像生成任务状态。
     *
     * 访问示例：
     * GET /api/papers/7/profile/job
     */
    @GetMapping("/api/papers/{id}/profile/job")
    public Result<PaperProfileJobResponse> getProfileJob(@PathVariable Long id) {
        return Result.success(paperProfileJobService.getLatestJob(id));
    }

    @PostMapping("/api/papers/{id}/profile/index")
    public Result<PaperProfileIndexResponse> indexProfile(@PathVariable Long id) {
        return Result.success(paperProfileIndexService.indexProfile(id));
    }

    @GetMapping("/api/papers/{id}/profile/index/status")
    public Result<PaperProfileIndexResponse> getProfileIndexStatus(@PathVariable Long id) {
        return Result.success(paperProfileIndexService.getIndexStatus(id));
    }


}
