# 多模态复现事实阶段 4 验证

> 日期：2026-07-27<br>
> 样例：Paper 38<br>
> 抽取版本：`reproduction-fact-v1`<br>
> 向量模型：Qwen `text-embedding-v4`，1024 维<br>
> 原始结果：[multimodal-reproduction-facts-stage4-paper38-2026-07-27.json](../../../test/results/multimodal-reproduction-facts-stage4-paper38-2026-07-27.json)

## 验证结论

阶段 4 已把原文块和多模态资产转换为可回查的原子事实。MySQL 保存事实真值和来源，Qdrant 只保存可重建索引；新索引位于独立 collection，不进入普通 RAG 候选池。

| 指标 | Paper 38 结果 |
| --- | ---: |
| 事实总数 | 93 |
| 默认可用事实 | 51 |
| 模型推断事实 | 42 |
| 冲突组 | 2 |
| 具有来源 ID | 93/93 |
| 具有页码 | 93/93 |
| 具有证据摘录 | 93/93 |
| 具有 Qdrant 点 | 93/93 |
| Qdrant 实际点数 | 93 |
| 具有适用条件 | 93/93 |
| 真实 Qwen 批量索引耗时 | 5.35 秒 |

来源构成为：算法 6 条、公式 43 条、图片 42 条、表格 2 条。默认接口只返回 `EXTRACTED`、`CROSS_CHECKED` 和 `USER_CONFIRMED`，必须显式选择才显示 `MODEL_INFERRED`。

## 安全与一致性边界

- 语义描述中的 `uncertainClaims` 不进入事实库。
- 完全相同的事实合并，同时保留 `supportingSourcesJson`。
- 同一事实键出现不同值时分别保存，并共享 `conflictGroup`，不会自动选择一个真值。
- 事实键包含资产标签和页码作用域，避免不同图片中的 `component_1` 被误判为冲突。
- Qdrant 命中只提供 `factId`；服务端回查 MySQL 后才返回。联调中删除一条 MySQL 事实后，其孤立向量没有再次返回，随后重建恢复。
- 资产重新解析、视觉分析、人工确认或拒绝时，会使该论文旧事实和索引失效。
- Qwen Embedding 按每批最多 10 条调用，Paper 38 的 93 条事实不再产生 93 次单条请求。

## 检索验证

查询 `LSTM architecture` 返回了 LSTM 门结构、信息流和相关公式事实，证明真实 1024 维 Qwen Embedding collection 可用。该入口只属于显式复现事实检索，普通 RAG 仍使用原 `paper_chunks` collection。

## 自动化回归

- 阶段 4 聚焦后端测试：12 项全部通过。
- 后端全量：156 项，155 通过，0 失败，1 项外部对比测试按条件跳过。
- 前端 Node：63/63 通过。
- 前端生产构建成功；仅保留既有第三方 PURE 注释和大包体提示。
