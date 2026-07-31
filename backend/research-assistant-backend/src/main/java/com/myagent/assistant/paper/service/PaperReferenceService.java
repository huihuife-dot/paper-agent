package com.myagent.assistant.paper.service;

import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.dto.PaperMetadataUpdateRequest;

import org.springframework.web.multipart.MultipartFile;
import com.myagent.assistant.paper.entity.PaperChunk;
import java.util.List;
import java.util.Map;

public interface PaperReferenceService {

    List<PaperReference> listPapers(Long categoryId);

    /**
     * 上传文献
     *
     * @param file       文件
     * @param title      标题
     * @param authors    作者
     * @param publishYear 发表年份
     * @param journal    期刊
     * @param keywords   关键词
     * @param remark     备注
     * @return 上传成功后的文献信息
     */
    PaperReference uploadPaper(MultipartFile file, String title, String authors, Integer publishYear, String journal, String keywords, String remark, Long categoryId);



    /**
     * 根据文献 ID 获取文献信息，用于下载文件。
     *
     * @param id 文献 ID
     * @return 文献信息
     */
    PaperReference getPaperById(Long id);

    /**
     * 修改文献所属分类。
     *
     * @param id 文献 ID
     * @param categoryId 新分类 ID
     * @return 修改后的文献信息
     */
    PaperReference updatePaperCategory(Long id, Long categoryId);

    /**
     * 修改文献的书目信息，不改变文件、分类和处理状态。
     */
    PaperReference updatePaperMetadata(Long id, PaperMetadataUpdateRequest request);



    /**
     * 删除文献记录和本地文件。
     *
     * @param id 文献 ID
     */
    void deletePaper(Long id);




    /**
     * 解析指定文献，并将解析后的文本切片保存到 MySQL。
     *
     * @param id 文献 ID
     * @return 生成的 chunk 数量
     */
    int parsePaper(Long id);


    /**
     * 查询指定文献的所有 chunk。
     *
     * 后续做向量化时，需要先拿到这些 chunk 文本，
     * 再把每个 chunk 转成 embedding 向量写入 Qdrant。
     *
     * @param paperId 文献 ID
     * @return 该文献对应的 chunk 列表
     */
    List<PaperChunk> listChunksByPaperId(Long paperId);

    /**
     * 向量化指定文献的 chunk。
     *
     * 当前阶段先使用 4 维假 embedding，重点是打通：
     * MySQL chunk -> 向量 -> Qdrant -> 回写状态。
     *
     * @param paperId 文献 ID
     * @return 本次向量化结果
     */
    Map<String, Object> vectorizePaper(Long paperId);

    /**
     * 清理并重建指定文献的解析派生资产。
     *
     * 保留 paper_reference 和本地 PDF，不删除对话历史和 Research Idea。
     */
    Map<String, Object> reprocessPaperAssets(Long paperId);

}



