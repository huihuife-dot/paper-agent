package com.myagent.assistant.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 内存 BM25 关键词检索索引。
 *
 * 原理：
 * - 从 MySQL 加载所有可用 chunk，为每个 chunk 建立词频统计
 * - 检索时对 query 分词，用 BM25 公式计算每个 chunk 与 query 的相关度
 * - BM25 比纯向量检索更适合精确术语匹配（模型名、数据集名、指标名）
 *
 * BM25 公式：
 *   score(d,q) = Σ IDF(qi) × tf_boost
 *   其中 tf_boost = (k1+1)×tf / (k1×(1-b+b×|d|/avgdl) + tf)
 *   IDF(qi) = log((N-df+0.5)/(df+0.5)+1)
 *
 * 参数：k1=1.5（词频饱和度），b=0.75（文档长度归一化）
 */
@Service
public class Bm25IndexService {

    private static final Logger log = LoggerFactory.getLogger(Bm25IndexService.class);

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final PaperChunkMapper paperChunkMapper;

    // 索引数据
    private final Map<Long, Map<String, Integer>> docTermFreq = new HashMap<>(); // chunkId → (term → tf)
    private final Map<Long, Integer> docLengths = new HashMap<>();                // chunkId → total terms
    private final Map<String, Integer> docFreq = new HashMap<>();                 // term → 出现多少篇文档
    private int totalDocs = 0;
    private double avgDocLength = 0.0;

    private final AtomicBoolean loaded = new AtomicBoolean(false);

    public Bm25IndexService(PaperChunkMapper paperChunkMapper) {
        this.paperChunkMapper = paperChunkMapper;
    }

    /**
     * 启动时从 MySQL 加载全部可用 chunk 并构建 BM25 索引。
     * 888 条 chunk 构建耗时约 100~300ms。
     */
    @PostConstruct
    public void buildIndex() {
        log.info("开始构建 BM25 索引...");
        long start = System.currentTimeMillis();

        List<PaperChunk> chunks = paperChunkMapper.selectList(new QueryWrapper<PaperChunk>()
                .orderByAsc("chunk_index"));

        int skipped = 0;
        for (PaperChunk chunk : chunks) {
            if (Boolean.TRUE.equals(chunk.getIsReference()) || Boolean.TRUE.equals(chunk.getIsNoise())) {
                skipped++;
                continue;
            }
            String sectionType = chunk.getSectionType();
            if ("REFERENCES".equals(sectionType) || "BACK_MATTER".equals(sectionType)) {
                skipped++;
                continue;
            }
            String text = indexText(chunk);
            if (text == null || text.isBlank()) {
                skipped++;
                continue;
            }

            List<String> tokens = tokenize(text);
            if (tokens.isEmpty()) continue;

            // 词频
            Map<String, Integer> tf = new HashMap<>();
            for (String token : tokens) {
                tf.merge(token, 1, Integer::sum);
            }
            docTermFreq.put(chunk.getId(), tf);
            docLengths.put(chunk.getId(), tokens.size());

            // 文档频率：去重后统计
            for (String token : tf.keySet()) {
                docFreq.merge(token, 1, Integer::sum);
            }
            totalDocs++;
        }

        if (totalDocs > 0) {
            avgDocLength = docLengths.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);
        }

        loaded.set(true);
        long elapsed = System.currentTimeMillis() - start;
        log.info("BM25 索引构建完成: {} 篇文档, {} 个唯一词条, 跳过 {} 个 chunk, 耗时 {}ms",
                totalDocs, docFreq.size(), skipped, elapsed);
    }

    /**
     * 检索与 query 最相关的 topK 个 chunkId。
     *
     * @return 按 BM25 分数降序排列的 (chunkId, score) 列表
     */
    public List<Map.Entry<Long, Double>> search(String query, int topK) {
        if (!loaded.get() || query == null || query.isBlank()) {
            return List.of();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        // 对每个候选文档计算 BM25 分数
        Map<Long, Double> scores = new HashMap<>();
        for (String token : queryTokens) {
            int df = docFreq.getOrDefault(token, 0);
            if (df == 0) continue;
            double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);

            for (Map.Entry<Long, Map<String, Integer>> docEntry : docTermFreq.entrySet()) {
                Long docId = docEntry.getKey();
                Integer tf = docEntry.getValue().get(token);
                if (tf == null) continue;

                int docLen = docLengths.getOrDefault(docId, 1);
                double tfBoost = (K1 + 1.0) * tf / (K1 * (1.0 - B + B * docLen / avgDocLength) + tf);

                scores.merge(docId, idf * tfBoost, Double::sum);
            }
        }

        return scores.entrySet()
                .stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    public boolean isReady() {
        return loaded.get();
    }

    // ===== 分词 =====

    /**
     * 简单分词：按空白和标点切分 → 小写 → 去停用词 → 保留长度≥2
     */
    private List<String> tokenize(String text) {
        String[] parts = text.toLowerCase().split("[\\s.,;:!?()\\[\\]{}<>\"'=+\\-*/|\\\\@#$%^&~`]+");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.length() >= 2 && !isStopword(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private boolean isStopword(String word) {
        return STOPWORDS.contains(word);
    }

    private String indexText(PaperChunk chunk) {
        if (chunk.getIndexText() != null && !chunk.getIndexText().isBlank()) {
            return chunk.getIndexText();
        }
        return chunk.getContent() != null ? chunk.getContent() : "";
    }

    // 英文停用词
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "shall", "in", "on", "at", "to",
            "for", "of", "with", "by", "from", "as", "into", "through", "during",
            "before", "after", "above", "below", "between", "and", "but", "or",
            "nor", "not", "so", "yet", "both", "either", "neither", "each", "every",
            "all", "any", "few", "more", "most", "other", "some", "such", "no",
            "only", "own", "same", "this", "that", "these", "those", "it", "its",
            "we", "they", "them", "their", "our", "he", "she", "his", "her",
            "which", "who", "whom", "what", "when", "where", "how", "if", "then",
            "than", "also", "about", "just", "very", "too", "well", "here", "there",
            "zero", "one", "two", "three", "first", "second",
            "using", "used", "based", "proposed", "results", "show", "shown",
            "table", "figure", "fig", "et", "al", "via", "per", "due", "thus"
    );
}
