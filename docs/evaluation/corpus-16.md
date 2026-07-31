# 16篇文献RAG评测语料审计与用例设计

更新时间：2026-07-16

## 1. 阶段结论

当前16篇文献可以作为MyAgent第一版正式开发评测语料。16篇均已完成PDF解析、原文向量化、文献画像、章节摘要和Qdrant多粒度索引，文件内容不存在重复。

本语料规模适合验证检索质量、论文区分、范围隔离和回答质量，但仍属于开发级评测，不代表数百篇文献库的规模性能。

## 2. 数据完整性审计

| 检查项 | 结果 |
| --- | ---: |
| MySQL文献数 | 16 |
| parseStatus=COMPLETED | 16/16 |
| vectorStatus=COMPLETED | 16/16 |
| MySQL画像存在 | 16/16 |
| Qdrant画像已索引 | 16/16 |
| 章节摘要索引完整 | 16/16 |
| 唯一文件SHA-256 | 16/16 |
| 重复文件 | 0 |

Qdrant `paper_chunks` collection审计：

| 点类型 | 数量 |
| --- | ---: |
| RAW_CHUNK | 1,579 |
| PAPER_PROFILE | 16 |
| SECTION_SUMMARY | 1,327 |
| 合计 | 2,922 |

Collection为1024维Cosine向量，状态为green，覆盖16个不同paperId。`indexed_vectors_count=0`是因为当前2,922点低于Qdrant的10,000点索引阈值，当前采用精确扫描，不表示向量缺失。

## 3. 固定语料

固定paperId：

```text
13, 15, 17, 18, 19, 21, 22, 27, 28, 29, 30, 31, 33, 34, 35, 38
```

语料结构：

- 15篇风速、风功率或局地天气预测论文；
- 1篇光伏功率预测论文（paperId=38），用于测试跨主题干扰过滤；
- 1篇系统综述（paperId=29），用于测试“综述”和“原创模型”的区分；
- 方法覆盖图神经网络、Transformer、信号分解、经典统计模型、集成学习、多模态和多任务学习；
- 预测时间尺度覆盖分钟级、小时级、季度尺度和年度长期趋势。

机器可读清单保存在 `test/rag-evaluation-corpus-16.json`，其中固定标题、年份、主题、方法标签和客观评测事实。后续算法优化期间不得随意修改；确需修正时必须提升`corpusVersion`并在报告中说明。

## 4. 元数据质量修正

审计发现并经用户确认两处年份字段错位，已通过文献信息编辑接口修正：

1. paperId=15：清空错误的`authors=2023`，设置`publishYear=2023`。
2. paperId=35：清空错误的`journal=2020`，设置`publishYear=2020`。

修正后两篇文献的解析和向量状态仍为COMPLETED，没有改变PDF、画像、章节摘要或Qdrant数据。

## 5. 40题评测集结构

| 类别 | 数量 | 主要目标 |
| --- | ---: | --- |
| 单篇理解 | 16 | 每篇至少有一道核心方法题，验证范围隔离和正文理解 |
| 多篇比较 | 8 | 比较图方法、分解方法、统计方法、预测时间尺度等 |
| 全库发现 | 10 | 验证未指定论文时的目标论文召回与Precision |
| 困难干扰 | 4 | 区分相似方法、综述与原创研究、风能与光伏 |
| 无答案拒答 | 2 | 量子纠错和医学CT问题，验证幻觉控制 |
| 合计 | 40 | 形成16篇正式开发基线 |

机器可读用例保存在 `test/rag-evaluation-cases-16.json`。与旧三篇用例相比，新用例重点增加：

- `RWT-ARIMA`与`ARMA-ESN`的相似方法区分；
- 条件局部卷积图与ERA5-MADIS异构图的区分；
- 唯一光伏论文的跨主题发现；
- 系统综述与原创预测模型的区分；
- 最多5篇目标论文的全库召回测试。

## 6. 运行方式

后端、MySQL和Qdrant正常运行，且IDEA中已配置模型API Key后执行：

```powershell
$env:RAG_EVAL_CASES_FILE='rag-evaluation-cases-16.json'
$env:RAG_EVAL_OUTPUT_FILE='test/results/rag-evaluation-16-baseline-2026-07-16.json'
node test/run-rag-evaluation.mjs
```

脚本已支持通过`RAG_EVAL_CASES_FILE`切换用例文件，报告会自动从用例中计算语料paperId，不再写死旧的三篇论文ID。首次正式运行保持`topK=12`，与原基线一致。

## 7. 下一阶段验收指标

首次40题运行先建立基线，不预设结果一定优于三篇测试。重点报告：

- executionSuccessRate；
- strategyAccuracy；
- expectedPaperRecall；
- expectedPaperPrecision；
- scopePurity；
- referenceNoiseRate；
- keywordCoverage；
- refusalAccuracy；
- discoveryMultiGranularityRate；
- 平均延迟、P50、P95；
- 按单篇、多篇、全库发现、困难干扰、拒答分别统计结果。

全库发现题的目标优先级为：先保证Recall不低于80%，再将宏平均Precision提升到70%以上；范围纯度必须保持100%，reference/noise污染率目标为0%。

## 8. 下一步

运行40题真实基线并保存原始JSON结果；分析失败案例后，再决定优先优化分类型多粒度召回、论文级重排、候选论文配额还是外部API重试机制。
