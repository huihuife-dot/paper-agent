# RAG 分段耗时观测报告

> 日期：2026-07-16  
> 语料环境：本地 16 篇已加工文献  
> 模型与服务：真实 Qwen、MySQL、Qdrant  
> 原始结果：`test/results/rag-performance-observability-2026-07-16.json`

## 评测目的

本轮不优化召回和答案质量，只回答一个问题：用户等待一次 RAG 回答时，时间主要花在哪里。为此在原链路增加请求内计时，不增加数据库写入、远程请求或模型调用，也不改变任何检索排序分数。

## 用例设计

专用用例文件为 `test/rag-performance-cases.json`，固定 `topK=12`，覆盖三条不同链路：

| 用例 | 论文范围 | 期望与实际策略 | 覆盖重点 |
| --- | --- | --- | --- |
| `PERF-SINGLE-13` | 论文 13 | `FULL_TEXT_PARSED` | 单篇全文上下文 |
| `PERF-MULTI-13-15` | 论文 13、15 | `HYBRID_RAG` | Query Rewrite、Dense/BM25、混合上下文 |
| `PERF-DISCOVERY-FREQUENCY` | 全库 | `LIBRARY_DISCOVERY` | 多路检索、融合和论文发现 |

选择论文 13、15 做多篇用例，是因为测试时两篇均满足画像、章节摘要及 Qdrant 索引完整。论文 20 当时未出现在画像状态列表中，若将其加入范围会按产品规则降级到 `VECTOR_RAG`，不适合作为混合策略性能样本。

## 采集口径

后端返回的 `timing` 记录策略选择、改写、Embedding、Qdrant、来源回查、BM25、融合排序、上下文、Prompt、LLM、历史保存、后处理和总耗时。评测脚本同时保留客户端端到端 `latencyMs`，用于检查计时是否覆盖了大部分实际等待。

毫秒值来自单次真实调用，适合定位数量级和主要瓶颈，不等同于高并发压测结论。

## 量化结果

三题请求成功率、策略准确率、期望论文 Recall、范围纯度和关键词覆盖率均为 100%，说明三类链路均成功执行，且新增观测未改变本轮检索范围。

| 策略 | 客户端耗时 | 服务端总耗时 | Query Rewrite | 检索相关阶段 | LLM 生成 | 最大耗时阶段 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 单篇全文 | 21,241 ms | 21,030 ms | 0 ms | 0 ms | 20,948 ms | LLM 生成 |
| 多篇混合 | 48,074 ms | 48,061 ms | 900 ms | 725 ms | 45,663 ms | LLM 生成 |
| 全库发现 | 21,476 ms | 21,467 ms | 3,847 ms | 1,327 ms | 16,266 ms | LLM 生成 |
| 三题平均 | 30,264 ms | 30,186 ms | 1,582 ms | 683 ms | 27,626 ms | LLM 生成 |

其中“检索相关阶段”为 Embedding、Qdrant 搜索、来源回查、BM25 和融合排序之和。

平均服务端总耗时为 30,186 ms，LLM 生成平均 27,626 ms，占约 **91.5%**；Query Rewrite 占约 **5.2%**；检索相关阶段合计占约 **2.3%**。三题的 `dominantStage` 全部是 `llmGeneration`。客户端与服务端平均差约 78 ms，说明当前分段计时已经覆盖绝大部分真实等待时间。

## 结论与下一方向

当前性能瓶颈不是 Qdrant、BM25 或融合排序，而是等待模型完整生成。继续压缩检索阶段，即使将平均检索耗时全部消除，理论上也只减少约 2.3% 的等待，因此不值得在现阶段无限优化。

下一项性能工作只建议做 **SSE 流式输出**：它不会缩短模型生成总时长，但能显著降低用户看到首段答案的等待感。启动该任务前应另建计划，并把“首 token 时间、流式完成率、异常中断恢复”作为验收指标；本轮不直接实现。

## 复现方法

确保 IDEA 后端、MySQL、Qdrant 和模型 API 可用后，在项目根目录执行：

```powershell
$env:RAG_EVAL_CASES_FILE='rag-performance-cases.json'
$env:RAG_EVAL_OUTPUT_FILE='test/results/rag-performance-observability-2026-07-16.json'
node test/run-rag-evaluation.mjs
```
