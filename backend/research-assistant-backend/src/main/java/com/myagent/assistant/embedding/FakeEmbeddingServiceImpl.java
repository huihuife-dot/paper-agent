package com.myagent.assistant.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 假 embedding 实现。
 *
 * 当前用于打通流程，不调用真实模型。
 * 注意：这里返回 4 维向量，必须和测试用 Qdrant collection 的 size=4 保持一致。
 */
@Service
@ConditionalOnProperty(name = "embedding.provider", havingValue = "fake", matchIfMissing = true)
public class FakeEmbeddingServiceImpl implements EmbeddingService {

    private static final int DIMENSION = 4;

    @Override
    public List<Double> embed(String text) {
        int contentLength = text != null ? text.length() : 0;
        int hash = text != null ? Math.abs(text.hashCode()) : 0;

        return List.of(
                normalize(contentLength, 1000),
                normalize(hash % 100, 100),
                normalize((contentLength * 3) % 100, 100),
                normalize((hash % 97) + 1, 100)
        );
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }

    @Override
    public String provider() {
        return "fake";
    }

    @Override
    public String modelName() {
        return "fake-embedding";
    }

    /**
     * 把数字压到 0~1 区间附近，避免测试向量数值过大。
     */
    private double normalize(int value, int base) {
        return Math.min(1.0, value / (double) base);
    }
}