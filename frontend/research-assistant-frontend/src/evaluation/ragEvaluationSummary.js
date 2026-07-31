export const ragEvaluationSummary = {
  version: 'rag-corpus-16-v1',
  evaluatedAt: '2026-07-16',
  corpusSize: 16,
  caseCount: 40,
  topK: 12,
  executionSuccessRate: 100,
  metrics: [
    { key: 'discoveryRecall', label: '全库发现 Recall', baseline: 82.33, final: 96.67, target: 90, unit: '%' },
    { key: 'discoveryPrecision', label: '全库发现 Precision', baseline: 93, final: 91.67, target: 90, unit: '%' },
    { key: 'multiGranularity', label: '严格多粒度使用率', baseline: 10, final: 70, target: 50, unit: '%' },
    { key: 'overallRecall', label: '总体 Recall', baseline: 95.35, final: 99.12, unit: '%' },
    { key: 'overallPrecision', label: '总体 Precision', baseline: 96.18, final: 96.49, unit: '%' },
    { key: 'keywordCoverage', label: '答案关键词覆盖率', baseline: 93.42, final: 96.93, unit: '%' },
  ],
  qualityGates: [
    { label: '策略准确率', value: 100, unit: '%', status: 'passed' },
    { label: '指定论文范围纯度', value: 100, unit: '%', status: 'passed' },
    { label: '引用 / 噪声污染率', value: 0, unit: '%', status: 'passed' },
    { label: '无答案人工拒答', value: 100, unit: '%', status: 'passed' },
  ],
  categories: [
    { label: '单篇精读', cases: 16, recall: 100, precision: 100, averageLatencySeconds: 19.034 },
    { label: '多篇比较', cases: 8, recall: 100, precision: 100, averageLatencySeconds: 39.715 },
    { label: '全库发现', cases: 10, recall: 96.67, precision: 91.67, averageLatencySeconds: 14.578 },
    { label: '困难负例', cases: 4, recall: 100, precision: 87.5, averageLatencySeconds: 23.198 },
    { label: '无答案负例', cases: 2, recall: null, precision: null, averageLatencySeconds: 34.886 },
  ],
  latency: {
    baselineAverageSeconds: 21.582,
    finalAverageSeconds: 23.265,
    baselineP95Seconds: 34.947,
    finalP95Seconds: 48.098,
    discoveryP95Seconds: 23.494,
  },
  limitations: [
    'D-GRAPH 的检索与推荐覆盖 5/5，但生成模型对论文 30 是否属于图模型仍存在方法归类分歧。',
    'D-DECOMPOSITION 在不同真实调用轮次出现 4/6 至 5/6 的召回波动。',
    '完整 P95 由多篇长回答的模型生成耗时主导，本轮不宣称延迟得到优化。',
    '当前结论基于 16 篇开发语料；扩充到 50 篇以上后需要重新建立大语料基线。',
  ],
  resumeDescription: '针对论文画像、章节摘要与原文块共享 TopK 导致的跨论文漏召回问题，设计分类型向量召回、paperId 级聚合重排、动态候选截止与多粒度证据配额；构建 16 篇论文、40 道真实 Qwen/Qdrant 回归集，将全库论文发现 Recall 由 82.33% 提升至 96.67%，严格多粒度证据使用率由 10% 提升至 70%，Precision 保持 91.67%，并维持策略准确率与范围纯度 100%、reference/noise 污染率 0%。',
}

export function metricDelta(metric) {
  return Number((metric.final - metric.baseline).toFixed(2))
}

export function metricPassed(metric) {
  return metric.target == null || metric.final >= metric.target
}
