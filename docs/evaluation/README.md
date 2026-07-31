# RAG 评测文档

## 当前入口

| 文档 | 作用 |
| --- | --- |
| [评测方法](methodology.md) | 指标定义、运行原则和结果解释 |
| [固定用例说明](cases.md) | 评测问题类型与早期指标设计 |
| [16 篇语料审计](corpus-16.md) | 当前开发语料和 40 题构建依据 |
| [多模态复现评测基线](multimodal-reproduction-baseline.md) | 8 篇 PDF、资产/事实金标和纯文本结构基线 |
| [定向视觉模型阶段 3 验证](reports/multimodal-vision-stage3-2026-07-27.md) | Qwen-VL 门控、缓存和失败边界真实验证 |
| [复现事实与独立索引阶段 4 验证](reports/multimodal-reproduction-facts-stage4-2026-07-27.md) | 原子事实、来源、冲突、MySQL 回查和真实 Qwen Embedding 验证 |
| [ReproductionSpec 与 Agent Context v2 阶段 5 验证](reports/multimodal-agent-context-stage5-2026-07-27.md) | 两篇真实论文的准备度、直接原文、可信事实、冲突/缺口和 v1 兼容验证 |
| [多模态复现证据层发布评测](reports/multimodal-reproduction-release-2026-07-28.md) | 8 篇固定多模态评测、视觉耗时、生命周期和 16 篇/40 题回归 |
| [最终 16 篇/40 题报告](reports/final-16-paper-2026-07-16.md) | 当前正式量化结论 |
| [RAG 分段耗时报告](reports/rag-performance-observability-2026-07-16.md) | 三类策略的真实链路耗时和瓶颈定位 |
| [RAG SSE 流式评测](reports/rag-sse-streaming-2026-07-16.md) | 首段等待、完整耗时和流式一致性 |
| [历史感知多轮 RAG 评测](reports/rag-multiturn-2026-07-16.md) | 代词、比较和全库指代追问的改写与召回结果 |

原始可机读结果保存在项目根目录 `test/results/`。RAG 脚本为 `test/run-rag-evaluation.mjs`，多模态候选评测脚本为 `test/run-multimodal-evaluation-candidate.mjs`，视觉锚点脚本为 `test/run-multimodal-vision-anchors.mjs`。

多模态复现评测与 RAG 回归相互独立：它使用 `test/multimodal-evaluation-*.json`，不修改 16 篇/40 题固定语料或结果。

## 报告层级

- `reports/final-*`：当前可对外引用的最终报告；
- `reports/baseline-*`：优化前基线；
- `reports/discovery-stage*`：优化过程中的定向阶段报告。
- `reports/rag-performance-*`：性能采集口径、分段耗时和瓶颈结论。
- `reports/rag-sse-*`：流式输出的首段等待和体验改善结果。
- `reports/rag-multiturn-*`：连续追问的历史改写、路由与目标论文召回结果。

新增评测必须同时记录语料版本、用例版本、TopK、模型/数据库环境和原始 JSON 路径，不能只保留汇总百分比。
