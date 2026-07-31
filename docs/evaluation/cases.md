# RAG 评测用例与指标记录

更新时间：2026-07-15

> 当前正式自动评测方案见[评测方法](methodology.md)，首轮量化结果见[三篇论文基线](reports/baseline-3-paper-2026-07-15.md)。本文保留早期结构化分块评测设计作为历史记录。

本文用于记录固定 RAG 评测问题和每次优化前后的量化指标。后续每次 RAG 优化都应尽量使用同一批问题，避免只凭主观感觉判断效果。

## 1. 为什么需要评测基线

RAG 优化容易出现“主观感觉变好，但实际没有稳定提升”的问题。因此后续每次调整召回、分块、rerank、全文策略或文献画像前后，都应尽量用同一批问题测试。

评测目标：

```text
召回是否更准？
参考文献误召回是否减少？
回答是否覆盖更多关键点？
sources 是否来自正确论文和正确章节？
单篇论文问题是否比之前更完整？
```

## 2. 指标定义

| 指标 | 含义 | 趋势 |
| --- | --- | --- |
| referenceHitRate | sources 中 `isReference=true` 的比例 | 越低越好 |
| expectedSectionHitRate | sources 命中期望 sectionType 的比例 | 越高越好 |
| paperHitRate | sources 来自目标 paperIds 的比例 | 越高越好 |
| topKUsefulRate | 人工判断 topK sources 中有用片段比例 | 越高越好 |
| keyPointCoverage | 回答覆盖 expectedKeyPoints 的比例 | 越高越好 |
| citationSupportRate | 回答主要结论能被 sources 支撑的比例 | 越高越好 |
| answerCompletenessScore | 人工 1~5 分，评价回答完整性 | 越高越好 |
| answerNoiseScore | 人工 1~5 分，评价无关内容/参考文献污染 | 越低越好 |

### 2.1 简单计算方式

```text
referenceHitRate = isReference=true 的 source 数 / sources 总数
expectedSectionHitRate = sectionType 属于 expectedSectionTypes 的 source 数 / sources 总数
paperHitRate = paperId 属于目标 paperIds 的 source 数 / sources 总数
topKUsefulRate = 人工判断有用 source 数 / sources 总数
keyPointCoverage = 回答覆盖 expectedKeyPoints 数 / expectedKeyPoints 总数
citationSupportRate = 有 sources 支撑的主要结论数 / 回答主要结论总数
answerCompletenessScore = 人工 1~5 分
answerNoiseScore = 人工 1~5 分
```

## 3. 固定评测用例

实际测试时，需要把 `paperIds` 替换为当前本地已解析、已向量化论文的真实 ID。建议至少选择一篇结构清晰的英文论文作为单篇评测对象，再选择两篇主题相近的论文作为多篇比较评测对象。

### Case 1：单篇方法问题

```json
{
  "caseId": "single-paper-method-001",
  "question": "这篇论文的核心方法是什么？",
  "paperIds": [1],
  "questionType": "METHOD",
  "expectedSectionTypes": ["METHOD"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "说明模型或方法框架",
    "说明关键模块",
    "说明方法解决的问题"
  ]
}
```

### Case 2：单篇实验问题

```json
{
  "caseId": "single-paper-experiment-001",
  "question": "这篇论文做了哪些实验，实验结果说明了什么？",
  "paperIds": [1],
  "questionType": "EXPERIMENT",
  "expectedSectionTypes": ["EXPERIMENT", "RESULT"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "说明实验设置或数据集",
    "说明对比基线",
    "说明主要实验结果"
  ]
}
```

### Case 3：单篇创新点问题

```json
{
  "caseId": "single-paper-contribution-001",
  "question": "这篇论文的主要创新点是什么？",
  "paperIds": [1],
  "questionType": "SUMMARY",
  "expectedSectionTypes": ["ABSTRACT", "INTRODUCTION", "METHOD", "CONCLUSION"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "总结研究问题",
    "总结核心贡献",
    "说明相对已有工作的改进"
  ]
}
```

### Case 4：单篇局限问题

```json
{
  "caseId": "single-paper-limitation-001",
  "question": "这篇论文有什么不足或局限？",
  "paperIds": [1],
  "questionType": "LIMITATION",
  "expectedSectionTypes": ["DISCUSSION", "CONCLUSION", "RESULT"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "说明方法或实验限制",
    "说明适用场景限制",
    "说明未来工作方向"
  ]
}
```

### Case 5：参考文献干扰测试

```json
{
  "caseId": "single-paper-reference-noise-001",
  "question": "这篇论文的方法是否基于 Transformer？",
  "paperIds": [1],
  "questionType": "FACT",
  "expectedSectionTypes": ["METHOD", "EXPERIMENT", "RESULT"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "根据正文判断是否使用 Transformer",
    "不能只因为参考文献出现 Transformer 就判断使用了 Transformer"
  ]
}
```

### Case 6：后置低价值内容干扰测试

```json
{
  "caseId": "single-paper-back-matter-noise-001",
  "question": "这篇论文的主要结论是什么？",
  "paperIds": [1],
  "questionType": "CONCLUSION",
  "expectedSectionTypes": ["CONCLUSION", "DISCUSSION"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "总结论文结论",
    "不要把作者贡献、利益冲突、致谢、资金来源当作正文结论"
  ]
}
```

### Case 7：多篇比较问题

```json
{
  "caseId": "multi-paper-method-compare-001",
  "question": "这几篇论文的方法路线有什么区别？",
  "paperIds": [1, 2],
  "questionType": "COMPARISON",
  "expectedSectionTypes": ["METHOD", "EXPERIMENT", "RESULT"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "分别说明每篇论文的方法",
    "比较方法差异",
    "指出适用场景或优缺点"
  ]
}
```

## 4. 手工记录模板

| 日期 | 版本/commit | caseId | paperIds | referenceHitRate | expectedSectionHitRate | paperHitRate | topKUsefulRate | keyPointCoverage | citationSupportRate | answerCompletenessScore | answerNoiseScore | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-07-06 | baseline-before-structured-chunking | single-paper-method-001 | [1] |  |  |  |  |  |  |  |  |  |

## 5. Apifox 验证建议

### 5.1 sources 检索

```http
GET /api/rag/sources?question=这篇论文的核心方法是什么？&topK=8&paperIds=1
```

记录：

```text
sources 总数
isReference=true 数量
isNoise=true 数量
sectionType 命中 METHOD 数量
是否来自目标 paperIds
人工判断有用片段数量
```

### 5.2 RAG 问答

```http
POST /api/rag/chat
Content-Type: application/json

{
  "question": "这篇论文的核心方法是什么？",
  "topK": 8,
  "paperIds": [1]
}
```

记录：

```text
回答是否覆盖 expectedKeyPoints
回答结论是否能被 sources 支撑
回答完整性 1~5 分
回答噪声 1~5 分
```

## 6. 一次完整评测流程

```text
1. 选择固定 paperIds。
2. 确认这些论文已经 parse 和 vectorize。
3. 针对每个 case 先调用 /api/rag/sources。
4. 统计 referenceHitRate、expectedSectionHitRate、paperHitRate、topKUsefulRate。
5. 再调用 /api/rag/chat。
6. 根据 expectedKeyPoints 人工记录 keyPointCoverage。
7. 记录 citationSupportRate、answerCompletenessScore、answerNoiseScore。
8. 后续 RAG 改进后，用同一批 case 重复测试并对比。
```

## 7. 全库检索 Query Rewrite 新旧对比评测

### 7.1 对比目标

比较旧方式（原始问题直接检索）和新方式（AdvancedQueryRewrite 三路 + RRF）在全库检索场景下的差异。

### 7.2 测试问题

| 编号 | 问题 | 测试目标 |
|------|------|---------|
| LD-01 | 有没有论文用了注意力机制？ | 检验"attention mechanism"关键词匹配 |
| LD-02 | 哪些论文涉及时间序列预测？ | 检验"time series forecasting" |
| LD-03 | 有没有论文使用图神经网络？ | 检验"graph neural network" |

### 7.3 对比步骤

**旧方式（原始问题直搜）：**
```bash
curl -s "http://localhost:8080/api/rag/sources?question=$(python3 -c 'import urllib.parse; print(urllib.parse.quote("有没有论文用了注意力机制？"))')&topK=5"
```

**新方式（AdvancedQueryRewrite → RRF）：**

只能通过 `/api/rag/chat` 触发（不传 paperIds 则自动走 LIBRARY_DISCOVERY）：
```bash
curl -s -X POST http://localhost:8080/api/rag/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"有没有论文用了注意力机制？","topK":5}'
```

### 7.4 记录指标

对每个问题，记录新旧两路结果：

| 指标 | 含义 | 理想方向 |
|------|------|---------|
| 返回 chunk 数 | topK 内的实际结果数 | ≥ 预期 |
| 覆盖论文数 | 不同 paperId 数量（全库发现模式核心指标） | 越高越好 |
| reference 占比 | sources 中 isReference=true 的比例 | 0% |
| 章节分布 | sources 的 sectionType 分布（METHOD/EXPERIMENT/...） | 集中在正文章节 |
| paperRelevance | 新方式专属：回答下方的论文相关度列表 | 应列出最相关论文 |

### 7.5 记录模板

| 日期 | 问题 | 方式 | chunk数 | 覆盖论文数 | reference占比 | avgScore | 备注 |
|------|------|------|---------|-----------|-------------|----------|------|
| 2026-07-10 | LD-01 | 旧(原始) | | | | | |
| 2026-07-10 | LD-01 | 新(RRF) | | | | | contextStrategy=LIBRARY_DISCOVERY |
| 2026-07-10 | LD-02 | 旧(原始) | | | | | |
| 2026-07-10 | LD-02 | 新(RRF) | | | | | |
| 2026-07-10 | LD-03 | 旧(原始) | | | | | |
| 2026-07-10 | LD-03 | 新(RRF) | | | | | |

### 7.6 预期效果

新方式相比旧方式的预期提升：
- **覆盖论文数**：相同 topK 下覆盖更多不同论文（RRF 多路融合去重后更分散）
- **reference 占比**：两者相同（都经过 isReference 过滤）
- **章节分布**：新方式可能更多命中 METHOD/EXPERIMENT 等学术关键词对应的章节（因为 HyDE 生成的也是学术陈述句）
- **前端体验**：新方式独有的 paperRelevance 卡片让用户看到"最相关论文"排名

## 8. 后续自动化方向

当前先采用文档化人工评测。等评测问题稳定后，可以进一步做：

```text
把 cases 转成 JSON 文件
新增后端或脚本自动调用 /api/rag/sources
自动统计 referenceHitRate / expectedSectionHitRate / paperHitRate
回答质量先保留人工评分，后续再考虑 LLM-as-judge
```
