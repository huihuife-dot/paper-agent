# ReproductionSpec 与 Agent Context v2 阶段 5 验证

## 结论

阶段 5 已完成并通过 MyAgent、独立 Research Engineering Agent 和两篇真实论文的端到端验收。系统能够把复现事实持久化为带版本的 `ReproductionSpec`，计算准备度、来源完整率、冲突和缺口；Agent Context v2 会直接提供关键原文和可信事实，同时保留 v1 读取方式。

真实 Paper 13 和 38 均完成“事实重建 → 规格持久化 → v2 上下文 → v1 兼容读取”联调。两篇论文的可信事实 evidence 均具有来源类型和来源 ID，模型推断没有混入可信 evidence，Paper 38 的 2 个冲突组原样进入任务包。

localhost Bridge 启动后，独立 Agent 已升级为保留 v2 规格和 evidence 来源的本地快照，结构化 handoff 会分别记录可信事实、模型推断、安全默认值、冲突和缺失信息。Paper 13 生成 BVMD-PAM-TWGCNet 最小入口并完成合成数据端到端 smoke run；Paper 38 保留现有 OVMD-IPSO-LSTM 项目，补充无 SciPy 的 NumPy fallback，21 项短测试全部通过。

协议联调原始结果保存在 `test/results/multimodal-agent-context-stage5-papers-13-38-2026-07-27.json`，最终 Agent 验收结果保存在 `test/results/multimodal-agent-context-stage5-final-2026-07-27.json`。前一份结果保留了 Bridge 尚未启动时的真实状态，没有被覆盖美化。

## 固定口径

- 后端：本机 `http://127.0.0.1:8080`
- 数据库：MySQL `research_assistant`
- 论文：真实 Paper 13、38
- v2：`GET /api/papers/{id}/reproduction-context?version=2`
- v1：`GET /api/papers/{id}/reproduction-context?version=1`
- 可信 evidence 来源完整：`sourceKind` 与 `sourceId` 均非空
- 不把章节摘要、画像计入“关键原文”或“可信事实”数量

## 真实结果

| 指标 | Paper 13 | Paper 38 |
| --- | ---: | ---: |
| 事实总数 | 41 | 93 |
| 默认可用事实 | 29 | 51 |
| 规格状态 | `PARTIAL` | `NEEDS_REVIEW` |
| 关键项覆盖率 | 12.50% | 37.50% |
| 来源完整率 | 95.12% | 100% |
| 可信 / 待复核 / 模型推断 | 2 / 27 / 12 | 47 / 0 / 42 |
| 冲突组 / 缺失项 | 0 / 7 | 2 / 5 |
| v2 关键原文 evidence | 8 | 8 |
| v2 可信事实 evidence | 2 | 40 |
| 可信事实 evidence 来源完整 | 2/2 | 40/40 |
| v1 / v2 evidence 总数 | 18 / 28 | 18 / 66 |

Paper 13 的准备度较低不是接口失败，而是其当前确定性事实多数低于可信阈值；系统将 27 条内容放入“待复核”，没有为了提高覆盖率把它们伪装成论文事实。Paper 38 具备更多可信事实，但存在 2 个冲突组，因此状态为 `NEEDS_REVIEW`，Agent 必须先暴露冲突。

## 回归结果

- 后端全量：158 项，157 通过，1 项外部对比测试按条件跳过，0 失败；
- ReproductionSpec / Agent Context 聚焦测试：5/5 通过；
- 前端 Node：64/64 通过；
- 前端生产构建：通过，仅保留既有第三方注释和大包体警告；
- MySQL migration：`paper_reproduction_spec` 已实际创建并检查表结构。
- 独立 Agent：18/18 项 v2 上下文、任务包、handoff 和 Bridge 聚焦测试通过；全量 201/201 项通过；
- Paper 13：`python -u run.py` 退出码 0，合成数据流水线端到端完成；
- Paper 38：无额外安装依赖，`run_smoke_tests.py` 实际执行 21/21 项通过。

## 独立 Agent 交付边界

两篇模型交付轮次都触及本地 Agent 的安全轮数上限，已有文件按设计保留。随后只针对实际 smoke 错误进行了收尾修正，并生成单独的 v2 validation handoff。Paper 13 的合成指标不代表论文结果；Paper 38 的两个算法冲突仍保持未决，没有为了让测试通过而选择其中一个候选。
