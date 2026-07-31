# 单篇论文 FULL_TEXT_PARSED 上下文策略设计

更新时间：2026-07-06

## 1. 背景

上一阶段已完成 `paper-structure-v1` 结构化分块：PDF 解析后会生成 `paper_section` 和带章节元数据的 `paper_chunk`，RAG 检索默认过滤 `REFERENCES` 和 `BACK_MATTER` 等低价值内容。

该改进已经解决了一个明显问题：用户询问“论文创新点”“论文讲什么”“核心方法”时，系统不再频繁召回参考文献内容，召回内容更接近正文。

但当前 RAG 主链路仍然是：

```text
用户问题
→ Qdrant topK 检索
→ 回查若干 chunk
→ 拼接 prompt
→ LLM 回答
```

对于单篇论文的宏观问题，这仍然不够。例如：

```text
这篇论文主要讲了什么？
这篇论文的创新点是什么？
这篇论文整体方法流程是什么？
这篇论文有什么优缺点？
```

这些问题需要模型综合摘要、引言、方法、实验、结果和结论，而不是只看 topK 的几个局部 chunk。

## 2. 本阶段目标

本阶段实现单篇论文 `FULL_TEXT_PARSED` 上下文策略。

目标：

```text
当用户只选择 1 篇论文，并且该论文结构化正文 token 数不超过预算时，
RAG 问答不走普通 topK 向量检索，
而是从 MySQL 读取该论文的结构化 chunk，
按章节组织较完整正文上下文，
默认排除 REFERENCES / BACK_MATTER / isReference / isNoise，
再交给大模型回答。
```

核心原则：

```text
能读全文时，不要只看片段。
```

## 3. 本阶段范围

### 3.1 本阶段做

```text
1. 新增上下文策略类型：VECTOR_RAG / FULL_TEXT_PARSED。
2. 新增 ContextStrategyService，自动判断本轮问答使用哪种策略。
3. 新增 FullTextContextService，从 MySQL 按章节组织单篇论文全文上下文。
4. 扩展 RagPromptService，支持 FULL_TEXT_PARSED prompt。
5. 扩展 RagChatResponse，返回 contextStrategy、contextTokenCount、contextPaperIds。
6. RagChatService 根据策略选择：FULL_TEXT_PARSED 或当前 VECTOR_RAG。
7. 增加单元测试。
8. 更新 docs/status/current.md，阶段开始、阶段完成、验证结果都要记录。
9. 完成后提交一个 Git 版本。
10. 完成后更新 docs/archive/notes/rag-improvement-notes.md，记录相比 paper-structure-v1 的改进、可量化验证结果和当前遗留问题。
```

### 3.2 本阶段不做

```text
1. 不做多篇 FULL_TEXT。
2. 不做 HYBRID。
3. 不做文献画像。
4. 不做章节摘要。
5. 不做 claim cards。
6. 不做 reranker 模型。
7. 不做前端 UI 大改。
8. 不改变 /api/rag/sources 调试接口，它仍然返回向量检索结果。
```

## 4. 策略判断设计

新增策略：

```text
VECTOR_RAG
FULL_TEXT_PARSED
```

初始判断规则：

```text
如果有效 paperIds 只有 1 篇
并且该论文 parse_status = COMPLETED
并且非参考文献、非噪声 chunk 的估算 token 总数 <= fullTextBudgetTokens
则选择 FULL_TEXT_PARSED
否则选择 VECTOR_RAG
```

初始配置建议：

```properties
rag.full-text-budget-tokens=60000
rag.full-text-max-sources=20
```

说明：

```text
fullTextBudgetTokens 控制放入 prompt 的全文上下文上限。
fullTextMaxSources 控制 FULL_TEXT_PARSED 响应中返回多少个 sources，避免前端和对话历史过大。
```

## 5. FullTextContextService 设计

新增服务：

```text
FullTextContextService
```

职责：

```text
1. 根据 paperId 查询 paper_reference。
2. 查询该论文下的 paper_section。
3. 查询该论文下的 paper_chunk。
4. 过滤 isReference=true、isNoise=true、sectionType=REFERENCES、sectionType=BACK_MATTER 的 chunk。
5. 按 sectionIndex + chunkIndex 排序。
6. 按章节组织上下文。
7. 控制 token budget。
8. 返回 promptContext、sources、tokenCount、paperIds。
```

建议返回对象：

```text
FullTextContext
- paperId
- paperTitle
- contextText
- sources
- tokenCount
```

上下文格式：

```text
论文ID：7
论文标题：xxx

[ABSTRACT] Abstract
...

[INTRODUCTION] Introduction
...

[METHOD] Proposed Method
...

[EXPERIMENT] Experiments
...

[RESULT] Results and Discussion
...

[CONCLUSION] Conclusion
...
```

## 6. Prompt 设计

新增 FULL_TEXT_PARSED prompt：

```text
你是一个论文/文献 AI 研究助手。

请根据下面给出的“按章节组织的论文正文上下文”回答用户问题。
如果上下文中没有足够信息，请明确说明，不要编造。

用户问题：
...

论文全文上下文：
...

回答要求：
1. 用中文回答。
2. 优先从整篇论文角度总结。
3. 如果问题是论文讲什么、创新点、整体方法、优缺点，请综合摘要、引言、方法、实验、结果和结论。
4. 不要使用 References、Author Contributions、Funding、Conflict of Interest、Data Availability 等后置内容作为正文结论。
5. 如果引用具体依据，可以用章节名说明，例如“根据 Method 章节...”或“根据 Experiments 章节...”。
```

当前 `VECTOR_RAG` prompt 保持兼容，只需补充 source 的章节信息展示。

## 7. RagChatResponse 扩展

新增字段：

```text
contextStrategy      本轮使用的上下文策略：VECTOR_RAG / FULL_TEXT_PARSED
contextTokenCount    本轮上下文估算 token 数
contextPaperIds      本轮上下文实际使用的论文 ID 列表
```

示例：

```json
{
  "contextStrategy": "FULL_TEXT_PARSED",
  "contextTokenCount": 18500,
  "contextPaperIds": [7]
}
```

## 8. sources 设计

FULL_TEXT_PARSED 不走 Qdrant topK，但仍返回 sources。

第一版返回参与全文上下文的前若干个 chunk source：

```text
默认最多 20 个。
保留 paperId、paperTitle、chunkId、chunkIndex、sectionId、sectionTitle、sectionType、content。
retrievalRoute = full_text
score = null
```

这样：

```text
1. 前端 sources 区域仍可显示引用片段。
2. 对话历史仍可保存 sources。
3. 暂时不需要改前端结构。
```

## 9. 错误和回退策略

以下情况回退 `VECTOR_RAG`：

```text
paperIds 为空。
paperIds 多于 1 篇。
论文不存在。
论文 parse_status 不是 COMPLETED。
该论文没有可用非 reference / 非 noise chunk。
总 token 超过 fullTextBudgetTokens。
```

如果 FULL_TEXT_PARSED 构造过程中异常，不静默吞掉，应抛出明确错误，方便调试。

## 10. 评测与记录

本阶段完成后，需要在 `docs/archive/notes/rag-improvement-notes.md` 记录：

```text
1. 相比 paper-structure-v1 版本，FULL_TEXT_PARSED 解决了什么问题。
2. 是否改善“论文讲什么 / 创新点 / 整体方法 / 优缺点”等宏观问题。
3. 有量化数据时记录：contextStrategy、contextTokenCount、sourceCount、answerCompletenessScore、keyPointCoverage 等。
4. 如果没有充分量化数据，也要记录人工体验观察。
5. 记录当前仍存在的问题，例如多篇论文比较仍未解决、超长论文仍需 VECTOR_RAG/HYBRID、图表公式仍未处理。
```

## 11. 验证方式

单元测试：

```text
ContextStrategyServiceTest
- 单篇、已解析、token 不超预算 → FULL_TEXT_PARSED
- 多篇 → VECTOR_RAG
- 未解析 → VECTOR_RAG
- token 超预算 → VECTOR_RAG

FullTextContextServiceTest
- 默认排除 REFERENCES / BACK_MATTER / isReference / isNoise
- 按章节和 chunk 顺序组织上下文
- 返回 full_text sources

RagChatServiceImplTest
- FULL_TEXT_PARSED 时不调用 RagRetrievalService
- VECTOR_RAG 时保持原逻辑
```

人工验证：

```http
POST /api/rag/chat
Content-Type: application/json

{
  "question": "这篇论文主要讲了什么？",
  "paperIds": [7],
  "topK": 5
}
```

期望：

```text
contextStrategy = FULL_TEXT_PARSED
contextPaperIds = [7]
contextTokenCount > 0
answer 能综合论文整体内容
prompt 中按章节组织正文
默认不包含 References / BACK_MATTER
```

## 12. 交付要求

```text
1. 实现前先更新 docs/status/current.md，记录进入 FULL_TEXT_PARSED 阶段。
2. 每个实质阶段完成后更新 docs/status/current.md。
3. 完成后运行后端测试。
4. 完成后进行至少一次 Apifox/curl 风格接口验证。
5. 完成后更新 docs/archive/notes/rag-improvement-notes.md，写清楚相对上一版本的改进、效果和遗留问题。
6. 完成后提交 Git 版本。
```
