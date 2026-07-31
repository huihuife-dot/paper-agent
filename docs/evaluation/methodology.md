# MyAgent RAG 评测方法

更新时间：2026-07-15

## 1. 目标

本评测用于回答三个问题：

1. 检索是否找到正确论文，并严格遵守用户指定范围？
2. 回答是否使用了合适的上下文策略和可追溯sources？
3. 后续改进相对当前版本提升了多少，而不是只靠主观感受？

固定测试集使用 paperId=13、15、20。每次优化前后必须使用同一份 `test/rag-evaluation-cases.json`、相同topK和相同模型配置。

## 2. 评测分层

| 层级 | 自动评测内容 | 人工评测内容 |
| --- | --- | --- |
| 路由 | contextStrategy是否符合预期 | 无 |
| 检索 | 目标论文召回、指定范围纯度、reference/noise比例、source类型 | source是否真正有用 |
| 回答 | 非空、关键词覆盖、无答案问题是否拒答 | 正确性、完整性、引用支持度、幻觉 |
| 性能 | 端到端耗时 | 回答等待体验 |

## 3. 自动指标与公式

### 3.1 strategyAccuracy

```text
策略正确用例数 / 总用例数
```

单篇应为FULL_TEXT_PARSED；多篇且画像齐全应为HYBRID_RAG；未指定论文应为LIBRARY_DISCOVERY。

### 3.2 expectedPaperRecall

```text
实际sources命中的预期paperId数量 / expectedPaperIds数量
```

这是论文发现能力的核心指标。若一个问题预期命中两篇，只命中一篇，则该用例为50%。

### 3.3 scopePurity

```text
指定paperIds范围内的source数量 / sources总数
```

只对指定论文用例计算。目标为100%，任何越界都视为严重缺陷。

### 3.4 expectedPaperPrecision

```text
实际命中的预期paperId数量 / 实际召回的不同paperId数量
```

主要用于全库发现。Recall衡量是否漏掉正确论文，Precision衡量是否引入过多非目标论文。

### 3.5 referenceNoiseRate

```text
(isReference=true 或 isNoise=true 的source数量) / sources总数
```

目标为0%。

### 3.6 keywordCoverage

```text
回答中命中的expectedAnswerKeywords数量 / 关键词总数
```

关键词采用大小写不敏感的直接匹配，只作为低成本代理指标，不等同于语义正确性。

### 3.7 refusalAccuracy

无答案用例中，回答包含“未找到、没有足够信息、无法判断、不涉及”等拒答表达则记为成功。

### 3.8 multiGranularityRate

LIBRARY_DISCOVERY用例中，sources同时出现paper_profile、section_summary或raw_chunk的覆盖情况。该指标用于确认多粒度索引确实参与，而不是只存在于数据库。

### 3.9 latency

记录每个用例从请求发出到收到完整响应的毫秒数，报告平均值、P50和P95。

## 4. 人工评分规则

每个回答保留四个1~5分人工字段：

- correctness：事实正确性
- completeness：问题关键点覆盖完整性
- citationSupport：主要结论是否能由sources支持
- hallucinationControl：是否避免无依据扩展，5分表示无明显幻觉

人工评分不混入第一版自动总分，防止主观权重掩盖检索缺陷。

## 5. 用例组成

- 单篇理解：6题
- 多篇比较：4题
- 全库论文发现：4题
- 无答案拒答：1题
- 合计：15题

## 6. 运行方式

前置条件：MySQL、Qdrant和携带DASHSCOPE_API_KEY的后端已启动。

```powershell
node test/run-rag-evaluation.mjs
```

脚本向 `POST /api/rag/chat` 顺序发送固定问题，并在标准输出打印JSON结果。保留输出即可与后续commit的结果做差值对比。

## 7. 版本对比方式

后续优化报告统一写成：

```text
指标提升 = 新版本指标 - 基线指标
相对提升率 = (新版本 - 基线) / 基线 × 100%
```

准确率类指标同时报告百分点变化，例如论文召回率从80%到90%，应表述为“提升10个百分点（相对提升12.5%）”。

## 8. 局限

- 当前只有三篇论文，结果不能代表大规模文献库性能。
- 关键词覆盖不能代替事实正确性。
- 模型输出存在随机性，重要版本建议重复运行3次并报告均值和标准差。
- 当前不使用LLM-as-judge；后续若引入，必须保存judge模型、prompt和原始评分理由。
