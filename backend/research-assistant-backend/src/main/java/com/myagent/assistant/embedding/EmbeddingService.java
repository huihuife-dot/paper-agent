package com.myagent.assistant.embedding;

import java.util.List;

/**
 * Embedding 抽象服务。
 *
 * 负责把文本转换成向量。
 * QdrantService 只依赖这个接口，不直接绑定具体 embedding 供应商。
 */
public interface EmbeddingService {

    /**
     * 将一段文本转换成向量。
     *
     * @param text 文本内容
     * @return embedding 向量
     */
    List<Double> embed(String text);

    /**
     * 批量向量化。默认实现保持旧 Provider 兼容，支持批处理的 Provider 可覆盖它。
     */
    default List<List<Double>> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    /**
     * 返回当前 embedding 向量维度。
     *
     * Qdrant collection 的 vector size 必须和这里返回的维度一致。
     *
     * @return embedding 向量维度
     */
    int dimension();

    /**
     * 当前 embedding 供应商。
     *
     * 例如：fake、qwen。
     */
    String provider();

    /**
     * 当前 embedding 模型名称。
     *
     * 例如：fake-embedding、text-embedding-v4。
     */
    String modelName();
}
