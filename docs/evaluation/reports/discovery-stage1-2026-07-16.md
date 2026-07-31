# 全库论文发现阶段1定向评测报告

> 日期：2026-07-16
> 对照基线：`test/results/rag-evaluation-16-baseline-2026-07-16.json`
> 本轮原始结果：`test/results/rag-evaluation-16-discovery-focus-stage1-2026-07-16.json`

## 1. 评测目的

验证“分类型召回 + paperId级聚合 + 候选论文轮询覆盖”是否真正改善此前最明显的全库发现漏召回问题，并在进入阶段2前识别新的Precision和证据分配问题。

本轮不用于替代完整40题回归，只选择6道已知重点题：D-GRAPH、D-DECOMPOSITION、D-STATISTICAL、D-LONG-TERM、D-PV、H-PV-DISTRACTOR。

## 2. 环境与口径

- 语料：固定16篇，Qdrant共2,922个多粒度向量点。
- 后端：重启后加载阶段1新检索链。
- 模型：Qwen qwen-plus；Embedding为Qwen text-embedding-v4。
- topK：12。
- 执行方式：6题顺序执行，无并发，6/6首轮成功。
- source Recall/Precision口径与40题基线完全一致。
- 最终回答是否正确推荐论文采用人工复核，不用source集合代替回答结论。

## 3. 总体对比

| 指标 | 相同6题旧基线 | 阶段1 | 变化 |
| --- | ---: | ---: | ---: |
| source目标论文宏平均Recall | 70.56% | 94.45% | +23.89个百分点（相对+33.86%） |
| source目标论文宏平均Precision | 75.83% | 44.45% | -31.38个百分点 |
| 5道discovery严格三粒度使用率 | 0% | 80% | +80个百分点 |
| 平均延迟 | 14.577秒 | 15.294秒 | +0.718秒（+4.92%） |
| P95延迟 | 18.300秒 | 19.630秒 | +1.330秒（+7.27%） |
| 执行成功率 | 100% | 100% | 持平 |
| 策略准确率 | 100% | 100% | 持平 |
| reference/noise污染率 | 0% | 0% | 持平 |

结论：阶段1显著提高了重点题的候选论文覆盖，并让画像真正进入大部分全库问题；但固定选择最多6篇候选，使只需要1至3篇答案的问题也携带6篇source，source Precision明显下降。阶段1证明了方向有效，但还没有达到完整阶段验收条件。

## 4. 逐题对比

| 用例 | 旧source论文 | 阶段1 source论文 | Recall变化 | 阶段1最终回答复核 |
| --- | --- | --- | ---: | --- |
| D-GRAPH | 31、15 | 31、21、15、30、13、35 | 40% → 100% | 正确推荐31、21、15、13，但把30判为证据不足，回答Recall为4/5；35被正确排除。 |
| D-DECOMPOSITION | 29、38、17、18、19 | 29、38、18、17、19、15 | 66.67% → 66.67% | 最终只明确推荐38、17、18，仍漏13、19、22；没有把29和15误列为最终答案。 |
| D-STATISTICAL | 35、34 | 35、34、33、22、15、17 | 66.67% → 100% | 正确识别33、34、35分别对应VAR、ESN、时空克里金。 |
| D-LONG-TERM | 27 | 27、28、22、30、19、15 | 50% → 100% | 正确恢复28，并正确区分28的年度长期趋势和27的新疆季度中期预测。 |
| D-PV | 38、29 | 38、29、21、19、17、27 | 100% → 100% | 最终只选择38，干扰source没有污染答案。 |
| H-PV-DISTRACTOR | 38、17、34、13 | 38、13、19、29、18、28 | 100% → 100% | 最终只选择38，困难干扰控制正确。 |

## 5. 多粒度证据变化

- D-GRAPH：5个paper_profile + 7个raw_chunk，缺少section_summary。
- D-DECOMPOSITION：4个paper_profile + 1个section_summary + 7个raw_chunk。
- D-STATISTICAL：4个paper_profile + 3个section_summary + 5个raw_chunk。
- D-LONG-TERM：5个paper_profile + 3个section_summary + 4个raw_chunk。
- D-PV：6个paper_profile + 2个section_summary + 4个raw_chunk。
- H-PV-DISTRACTOR：5个paper_profile + 6个section_summary + 1个raw_chunk。

画像从旧基线重点题的0次进入最终topK，提升为6/6均有画像；5道discovery中4道同时具备画像、摘要、原文。说明分类型召回生效，但不同问题的证据比例仍不稳定。

## 6. 阶段2必须解决的问题

### 6.1 动态候选截止

当前最多6篇候选是固定上限，不管问题只需要1篇还是需要6篇，都会尽量填入6篇。这提高Recall但直接拉低source Precision。

阶段2应根据论文聚合分数与第一名的相对差距设置动态截止，同时保留最低和最高候选数：

- 单目标问题允许只保留高置信的1至3篇。
- 列举型问题允许扩展到6至8篇候选。
- 弱相关候选不能仅因“还有topK空位”而进入上下文。

### 6.2 候选后的证据配额

不能只给每篇候选任意高分source。对于高排名候选，应优先形成“画像判断主题 + 摘要或原文支撑事实”的组合；同一论文同一类型的重复证据继续衰减。

### 6.3 回答与source指标分离

D-PV和H-PV说明source Precision低不等于最终答案错误；D-GRAPH又说明source已召回不等于回答一定采用。后续评测脚本应分别保留：

- sourcePaperRecall/Precision；
- paperRelevance推荐集合Recall/Precision；
- 重点题人工复核的answerPaperRecall/Precision。

## 7. 阶段判断

阶段1真实定向验证通过“提高候选覆盖”和“提高多粒度利用”两项目标，但Precision退化明显，且D-DECOMPOSITION没有改善。因此不直接运行完整40题作为最终优化结果，先进入阶段2修复动态候选截止和证据补全，再复跑相同6题；只有重点题Recall保持、Precision恢复后，才运行完整40题。
