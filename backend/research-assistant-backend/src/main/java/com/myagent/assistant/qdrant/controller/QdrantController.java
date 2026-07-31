package com.myagent.assistant.qdrant.controller;
import org.springframework.web.bind.annotation.PostMapping;
import com.myagent.assistant.paper.common.Result;
import com.myagent.assistant.qdrant.service.QdrantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;

/**
 * Qdrant 控制器。
 * 提供和向量数据库相关的接口。
 */
@RestController
public class QdrantController {

    private final QdrantService qdrantService;

    public QdrantController(QdrantService qdrantService) {
        this.qdrantService = qdrantService;
    }

    /**
     * 检查后端是否能访问 Qdrant。
     *
     * 访问示例：
     * GET /api/qdrant/health
     */
    @GetMapping("/api/qdrant/health")
    public Result<Map<String, Object>> health() {
        return Result.success(qdrantService.health());
    }



    /**
     * 创建 paper_chunks collection。
     *
     * 访问示例：
     * POST /api/qdrant/collections/paper-chunks
     */
    @PostMapping("/api/qdrant/collections/paper-chunks")
    public Result<Map<String, Object>> createPaperChunksCollection() {
        return Result.success(qdrantService.createPaperChunksCollection());
    }



    /**
     * 根据用户问题检索相似 chunk。
     *
     * 访问示例：
     * GET /api/qdrant/search?question=transformer&topK=5
     *
     * 当前是 RAG 前置调试接口：
     * 只验证“问题 embedding -> Qdrant 检索”链路，
     * 暂时不调用大模型生成回答。
     */
    @GetMapping("/api/qdrant/search")
    public Result<Map<String, Object>> search(
            @RequestParam("question") String question,
            @RequestParam(value = "topK", required = false) Integer topK
    ) {
        return Result.success(qdrantService.searchSimilarChunks(question, topK));
    }


}