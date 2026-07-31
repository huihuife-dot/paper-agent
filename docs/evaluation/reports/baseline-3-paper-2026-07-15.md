# MyAgent 三篇论文 RAG 基线报告

评测日期：2026-07-15  
基线代码：`c907d85` + 评测工具开发工作区  
模型：Qwen（项目IDEA运行配置）  
Embedding：Qwen text-embedding-v4，1024维  
向量库：Qdrant 1.18.2  
topK：12  
语料：paperId 13、15、20  

## 1. 评测对象

| paperId | 方法特征 | 用途 |
| --- | --- | --- |
| 13 | 时频增强时空图网络 | 深度学习、时频、图建模问题 |
| 15 | GNN + FFTransformer/FFT-Attention | Transformer、注意力、多步预测问题 |
| 20 | ARMA/VAR风速-风向联合预测 | 经典统计、圆形变量问题 |

Qdrant评测时包含888个RAW_CHUNK、3个PAPER_PROFILE和215个SECTION_SUMMARY，共1106点。

## 2. 用例构成

| 类别 | 数量 | 验证目标 |
| --- | ---: | --- |
| 单篇理解 | 6 | FULL_TEXT_PARSED、单篇事实和方法理解 |
| 多篇比较 | 4 | HYBRID_RAG、三篇覆盖、跨论文比较 |
| 全库发现 | 4 | LIBRARY_DISCOVERY、论文召回与精确率 |
| 无答案拒答 | 1 | 不依据无关文献编造量子纠错算法 |
| 合计 | 15 | 完整RAG主链路 |

## 3. 总体量化结果

| 指标 | 首轮结果 | 解读 |
| --- | ---: | --- |
| 执行成功率 | 93.33%（14/15） | 1题发生Qwen Connection reset |
| 策略准确率 | 100% | 单篇、比较、全库均选择正确策略 |
| 目标论文宏平均召回率 | 96.43% | 注意力发现题漏召回paperId 13 |
| 指定范围纯度 | 100% | 指定论文问答无越界source |
| reference/noise污染率 | 0% | 未召回参考文献或噪声chunk |
| 答案关键词覆盖率 | 94.87% | 单篇局限题只命中1/3预设关键词 |
| 无答案拒答准确率 | 100% | 正确说明三篇均不涉及量子纠错 |
| 全库发现三层同时使用率 | 0% | 有摘要参与，但没有任一发现题同时使用画像、摘要、原文三层 |
| 平均延迟 | 23.353秒 | 真实端到端生成耗时 |
| P50延迟 | 19.679秒 | 一半请求不超过约19.7秒 |
| P95延迟 | 44.335秒 | 长尾主要来自多篇比较/拒答 |

全库发现论文精确率按四题单独计算：

```text
D-FREQUENCY：2个目标 / 4篇实际 = 50.00%
D-ATTENTION：1个目标 / 3篇实际 = 33.33%
D-STATISTICAL：1个目标 / 1篇实际 = 100.00%
D-GRAPH：2个目标 / 3篇实际 = 66.67%
宏平均Precision = 62.50%
```

这说明当前系统“找得到相关论文”的Recall较高，但全库发现会带入额外候选论文，Precision是下一阶段更值得提升的指标。

## 4. 分类结果

### 4.0 逐题原始摘要

| caseId | 首轮 | 策略 | Recall | Precision | 关键词覆盖 | 耗时(ms) | 主要结果 |
| --- | --- | --- | ---: | ---: | ---: | ---: | --- |
| S13-METHOD | 失败 | - | - | - | - | 440 | Qwen Connection reset；单独重试成功17,392ms |
| S13-EXPERIMENT | 成功 | FULL_TEXT_PARSED | 100% | 100% | 100% | 23,679 | 命中MAE/MSE |
| S15-METHOD | 成功 | FULL_TEXT_PARSED | 100% | 100% | 100% | 22,059 | 命中FFT/Attention/GNN |
| S15-LIMITATION | 成功 | FULL_TEXT_PARSED | 100% | 100% | 33.33% | 19,679 | 只直接命中“计算” |
| S20-METHOD | 成功 | FULL_TEXT_PARSED | 100% | 100% | 100% | 16,765 | 命中ARMA/VAR/风向 |
| S20-FACT | 成功 | FULL_TEXT_PARSED | 100% | 100% | 100% | 14,005 | 命中probit/圆/边界 |
| M-ALL-METHOD | 成功 | HYBRID_RAG | 100% | 100% | 100% | 34,922 | 覆盖13/15/20 |
| M-ALL-TEMPORAL | 成功 | HYBRID_RAG | 100% | 100% | 100% | 34,122 | 覆盖图/时间/ARMA |
| M-ALL-EXPERIMENT | 成功 | HYBRID_RAG | 100% | 100% | 100% | 33,923 | 覆盖MAE/数据/实验 |
| M-ALL-LIMITATION | 成功 | HYBRID_RAG | 100% | 100% | 100% | 34,312 | 覆盖局限/改进 |
| D-FREQUENCY | 成功 | LIBRARY_DISCOVERY | 100% | 50.00% | 100% | 14,338 | 目标13/15，另召回19/22 |
| D-ATTENTION | 成功 | LIBRARY_DISCOVERY | 50% | 33.33% | 100% | 12,079 | 命中15，漏13，另召回17/21 |
| D-STATISTICAL | 成功 | LIBRARY_DISCOVERY | 100% | 100% | 100% | 10,730 | 只召回目标20 |
| D-GRAPH | 成功 | LIBRARY_DISCOVERY | 100% | 66.67% | 100% | 11,994 | 命中13/15，另召回21 |
| N-QUANTUM | 成功 | HYBRID_RAG | 100% | 100% | - | 44,335 | 正确拒答 |

### 4.1 单篇理解

- 首轮5/6成功；S13-METHOD因Qwen连接重置失败。
- 失败题单独重试成功，耗时17.392秒，策略为FULL_TEXT_PARSED，sources全部来自paperId 13。
- 其余单篇策略、论文范围、reference/noise控制均为100%。
- S15-LIMITATION自动关键词覆盖仅33.33%，回答提到计算开销，但未直接命中预设“短期、泛化”表述；需要人工复核是表达改写还是内容遗漏。

### 4.2 多篇比较

- 4/4成功，全部使用HYBRID_RAG。
- 每题均覆盖paperId 13、15、20，scopePurity=100%。
- sources稳定包含3个paper_profile、12个section_summary和5~6个raw_chunk，证明三层上下文在指定多篇模式下工作稳定。
- 多篇请求耗时约33.9~34.9秒，是当前主要性能瓶颈。

### 4.3 全库论文发现

- 4/4执行成功，全部使用LIBRARY_DISCOVERY。
- 频域、统计模型、图网络三题目标论文Recall=100%。
- 注意力题只召回paperId 15，漏掉预期paperId 13，Recall=50%。
- 四题均未召回paper_profile进入最终top12；两题包含section_summary，另两题全部为raw_chunk。
- 宏平均论文Precision为62.50%，说明RRF结果仍偏向多个零散chunk，缺少论文级配额或二次重排。

### 4.4 无答案拒答

- 正确回答三篇论文均不涉及量子计算纠错算法。
- 使用HYBRID_RAG且覆盖三篇，未虚构算法名称，拒答准确率100%。
- 耗时44.335秒，为本轮最长请求。

## 5. 当前优势

1. 路由策略稳定：strategyAccuracy=100%。
2. 指定范围隔离可靠：scopePurity=100%。
3. reference/noise过滤有效：污染率0%。
4. 多篇画像、章节摘要、原文三层上下文覆盖稳定。
5. 无答案问题能够拒答，没有明显凭空编造。

## 6. 当前问题与下一步优化假设

### P1：全库发现Precision只有62.50%

建议增加论文级候选聚合后的二次排序或每篇source配额，避免同一主题下带入过多弱相关论文。

### P1：注意力题漏召回paperId 13

需要检查paperId 13画像/摘要中的attention/Transformer表达是否被召回，并比较profile、summary、raw三路的单独排名。

### P2：全库发现三层同时使用率为0%

当前不同contentType在同一相似度空间直接竞争，raw_chunk数量远多于profile。建议采用分类型召回后再融合，例如profile×N、summary×N、raw×N分别检索。

### P2：端到端延迟较高

平均23.353秒、P95 44.335秒。后续可记录Query Rewrite、Embedding、Qdrant、Prompt和LLM生成分段耗时，优先优化多篇HYBRID_RAG。

### P2：瞬时API失败缺少重试

首题出现Connection reset，人工重试成功。建议对可重试网络错误增加有限次数、指数退避和幂等保护。

## 7. 后续对比标准

下一版本至少重复运行同一15题，并重点比较：

- 全库发现Recall：基线96.43%
- 全库发现Precision：基线62.50%
- 三层同时使用率：基线0%
- 指定范围纯度：不得低于100%
- reference/noise污染率：不得高于0%
- P50/P95延迟：基线19.679/44.335秒
- 首轮执行成功率：基线93.33%

若优化Precision，不能以明显牺牲Recall为代价；所有提升都应报告百分点和相对提升率。

## 8. 简历可用描述

可在后续优化完成后表述为：

> 建立覆盖单篇理解、多文献比较、全库发现与拒答场景的15题RAG回归评测集，设计策略准确率、目标论文Recall/Precision、范围纯度、引用噪声率、关键词覆盖率及P50/P95延迟等指标；基线实现策略准确率100%、目标论文召回率96.43%、指定范围纯度100%、噪声召回率0%，并通过失败案例定位全库发现精确率与多粒度融合问题，为后续检索优化提供可量化对照。

注意：简历中应保留“15题三篇论文小规模基线”的上下文，避免把小样本结果包装为大规模生产指标。
