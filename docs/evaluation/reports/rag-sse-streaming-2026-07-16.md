# RAG SSE 流式输出评测报告

> 日期：2026-07-16
> 环境：真实 Qwen、MySQL、Qdrant，本地 16 篇语料
> 原始结果：`test/results/rag-streaming-2026-07-16.json`

## 目标

上一阶段确认 LLM 完整生成约占 RAG 服务端耗时的 91.5%。本阶段不缩短完整生成时间，而是通过模型原生流式响应和 SSE，让用户尽早看到第一个回答片段。

## 实现口径

链路为：前端 `fetch` POST → Spring MVC `SseEmitter` → RAG 检索与 Prompt → OpenAI-compatible `stream=true` → 逐行解析 `delta.content` → 前端实时追加 → 完成后保存完整历史。

事件顺序固定为：

1. `phase`：连接建立，进入检索；
2. `metadata`：检索完成，返回策略、来源和上下文信息；
3. `delta`：模型增量回答，可出现多次；
4. `complete`：完整回答、会话、来源和 timing；
5. `error`：任一阶段失败时返回明确错误。

原同步接口 `POST /api/rag/chat` 保持不变，用于兼容调用和原有评测。

## 测试方法

使用 `test/run-rag-streaming-evaluation.mjs` 运行两个真实用例：

- 单篇全文：`PERF-SINGLE-13`；
- 全库发现：`PERF-DISCOVERY-FREQUENCY`。

客户端记录请求开始到 metadata、首个 delta 和 complete 的时间，并校验全部 delta 拼接结果是否与 complete 中的最终答案严格相等。

## 量化结果

| 用例 | 策略 | 元数据到达 | 首段出现 | 完整完成 | 增量片段 | 等待感缩短 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 单篇全文 | `FULL_TEXT_PARSED` | 201 ms | 1,803 ms | 41,929 ms | 384 | 95.70% |
| 全库发现 | `LIBRARY_DISCOVERY` | 5,062 ms | 5,432 ms | 21,108 ms | 186 | 74.27% |
| 平均 | — | 2,632 ms | 3,618 ms | 31,519 ms | 285 | 84.98% |

两题执行成功率、策略准确率和流式/最终答案一致率均为 100%。共收到 570 个模型增量片段。

“等待感缩短”按 `1 - 首段耗时 / 完整耗时` 计算。它表示用户不再需要等待完整回答才看到内容，不代表模型总推理时间被缩短。

全库题首段比单篇题慢，主要因为首段之前需要完成 Query Rewrite 和多路检索；其服务端 `firstTokenMs` 仅 369 ms，但从请求开始到首段为 5,423 ms。这个结果符合当前链路设计。

## 结论

SSE 已达到本阶段目标：不改变检索策略和最终答案的情况下，平均从 31.52 秒完整等待降低到 3.62 秒看到首段，感知等待减少约 85.0%。当前无需继续进行无边界性能优化。

后续只有真实使用出现断线或并发压力问题时，再单独评估取消传播、断线续传或并发限流。

## 复现

IDEA 后端、MySQL、Qdrant 和模型 API 正常运行后，在项目根目录执行：

```powershell
node test/run-rag-streaming-evaluation.mjs
```
