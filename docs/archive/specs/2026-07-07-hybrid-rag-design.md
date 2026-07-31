# 多篇论文 HYBRID_RAG 设计

更新时间：2026-07-07

## 1. 背景

当前系统已经完成以下 RAG 基础能力：

```text
PDF 结构化解析
→ paper_section 章节层
→ paper_chunk 结构化 chunk
→ 单篇预算内 FULL_TEXT_PARSED
→ paper_section_summary 章节摘要
→ paper_profile 文献画像
```

`FULL_TEXT_PARSED` 已解决单篇论文宏观问题只看局部 topK chunk 的问题；`paper_profile` 和 `paper_section_summary` 已沉淀为可复用的论文级理解资产。

当前主要短板集中在多篇论文问题：

```text
这几篇论文的方法有什么区别？
它们各自有什么优缺点？
哪篇更适合作为我的研究基础？
这些论文的共同趋势是什么？
```

这类问题不适合只用全局向量 topK。全局 topK 容易被某一篇论文占满，也可能只召回局部片段，缺少逐篇理解和横向比较所需的完整上下文。

因此下一阶段实现第一版 `HYBRID_RAG`，优先解决“用户选中多篇论文后的比较型 / 分析型问答”。

## 2. 本阶段目标

本阶段名称：多篇论文 HYBRID_RAG。

目标：

```text
用户选择 2 篇及以上论文
→ 系统读取每篇论文的 paper_profile
→ 根据问题选择相关 section_summary
→ 每篇论文补充少量 raw chunk 证据
→ 按论文均衡组织上下文
→ 生成逐篇分析 + 横向比较回答
```

核心原则：

```text
多篇比较时，不要只按全局 topK；先理解每篇论文，再做横向比较。
```

## 3. 本阶段范围

### 3.1 本阶段做

```text
1. 扩展 ContextStrategy，新增 HYBRID_RAG。
2. ContextStrategyService 在多篇且画像齐全时选择 HYBRID_RAG。
3. 新增 HybridRagContextService，构造多篇混合上下文。
4. HYBRID_RAG 上下文组合 paper_profile、section_summary 和少量 raw chunk。
5. 新增 HYBRID_RAG prompt，要求模型逐篇分析后横向比较。
6. /api/rag/chat 返回 contextStrategy=HYBRID_RAG、contextTokenCount、contextPaperIds 和 sources。
7. 增加单元测试覆盖策略选择、上下文构造、prompt 结构和回退逻辑。
8. 每个实质阶段更新 docs/status/current.md。
```

### 3.2 本阶段不做

```text
1. 不把 paper_profile / paper_section_summary 写入 Qdrant。
2. 不重建 Qdrant collection。
3. 不实现 LLM Query Analyzer。
4. 不实现 reranker 模型。
5. 不自动在问答时生成缺失的 paper_profile。
6. 不新增前端页面；前端继续沿用已有 paperIds 多选问答能力。
7. 不做 claim card / finding card。
```

这些内容留给后续阶段：

```text
多类型向量索引
→ LLM 问题分析
→ section_summary 语义检索
→ rerank
→ claim cards
→ 前端展示 contextStrategy 和 profile 状态
```

## 4. 策略选择设计

### 4.1 现有策略

当前上下文策略主要是：

```text
单篇已解析且正文 token 不超预算 → FULL_TEXT_PARSED
其他情况 → VECTOR_RAG
```

### 4.2 新增策略

新增：

```text
HYBRID_RAG
```

策略含义：

```text
使用文献画像 + 章节摘要 + 少量原文 chunk，按论文均衡组织多篇上下文。
```

### 4.3 触发条件

第一版规则：

```text
如果 paperIds 数量 >= 2
并且所有选中文献都有 paper_profile-v1
并且 contextMode 为 AUTO 或未传
则选择 HYBRID_RAG
```

否则保持现有逻辑：

```text
单篇预算内 → FULL_TEXT_PARSED
其他情况 → VECTOR_RAG
```

### 4.4 缺失画像时的处理

如果多篇论文中任意一篇缺失 `paper_profile-v1`：

```text
不在问答时自动生成画像
直接回退 VECTOR_RAG
```

原因：

```text
1. 自动生成画像会触发大量 LLM 调用，导致一次问答变慢、变贵。
2. profile 生成失败会污染问答主流程。
3. 用户已可通过 POST /api/papers/{id}/profile 显式生成画像。
```

后续可在前端提示“部分文献未生成画像，建议先生成后再进行多篇比较”。

## 5. 上下文构造设计

### 5.1 上下文来源

HYBRID_RAG 第一版使用三类上下文：

```text
PAPER_PROFILE     文献整体画像
SECTION_SUMMARY   相关章节摘要
RAW_CHUNK         少量原文证据片段
```

其中：

```text
PAPER_PROFILE 是多篇比较的主干。
SECTION_SUMMARY 用于补足方法、实验、结果、讨论等局部细节。
RAW_CHUNK 用于提供可追溯原文证据，避免只根据摘要自由发挥。
```

### 5.2 每篇论文的上下文结构

每篇论文组织为：

```text
[Paper {paperId}] {title}

一、文献画像
- 研究问题
- 方法概述
- 实验与评估
- 主要贡献
- 局限性
- 关键词
- RAG 画像文本

二、相关章节摘要
- sectionType
- sectionTitle
- summary
- keyPoints

三、原文证据片段
- chunkId
- sectionType
- sectionTitle
- content excerpt
```

多篇论文按 `paperIds` 输入顺序排列，避免回答时混淆论文。

### 5.3 均衡策略

第一版采用固定上限，避免某一篇论文占满 prompt：

```text
每篇论文最多 1 条 paper_profile
每篇论文最多 4 条 section_summary
每篇论文最多 2 条 raw_chunk
```

后续可配置化，例如：

```text
hybrid.maxSectionSummariesPerPaper=4
hybrid.maxRawChunksPerPaper=2
hybrid.maxContextTokens=30000
```

第一版可以先写在服务常量里，后续再抽配置。

### 5.4 token 控制

HYBRID_RAG 不追求把所有摘要和 chunk 全塞进 prompt，而是按预算裁剪：

```text
1. 优先保留 paper_profile。
2. 再保留相关 section_summary。
3. 最后保留 raw_chunk 证据片段。
```

如果超出预算，裁剪顺序：

```text
先减少 raw_chunk 内容长度
再减少 raw_chunk 数量
再减少 section_summary 数量
最后保留最小 paper_profile 摘要字段
```

第一版可用现有粗略 token 估算方式，与 `FULL_TEXT_PARSED` 保持一致。

## 6. section_summary 选择规则

第一版不用 LLM Query Analyzer，采用关键词规则选择章节类型。

### 6.1 方法类问题

触发词：

```text
方法、模型、框架、流程、架构、method、approach、model、framework
```

优先章节：

```text
METHOD
RELATED_WORK
INTRODUCTION
```

### 6.2 实验结果类问题

触发词：

```text
实验、结果、性能、数据集、评估、对比实验、evaluation、experiment、result、dataset、performance
```

优先章节：

```text
EXPERIMENT
RESULT
DISCUSSION
```

### 6.3 优缺点 / 局限类问题

触发词：

```text
优点、缺点、局限、不足、问题、未来工作、limitation、weakness、future work、strength
```

优先章节：

```text
DISCUSSION
CONCLUSION
RESULT
EXPERIMENT
```

### 6.4 创新点 / 贡献类问题

触发词：

```text
创新、贡献、亮点、核心思想、contribution、novelty
```

优先章节：

```text
ABSTRACT
INTRODUCTION
METHOD
CONCLUSION
```

### 6.5 对比 / 综述类问题

触发词：

```text
对比、区别、差异、相同点、共同点、趋势、综述、compare、difference、similarity、trend、survey
```

优先章节：

```text
METHOD
EXPERIMENT
RESULT
DISCUSSION
CONCLUSION
```

### 6.6 兜底规则

如果问题没有命中任何规则：

```text
ABSTRACT
INTRODUCTION
METHOD
EXPERIMENT
RESULT
CONCLUSION
```

并按章节顺序最多取 4 条。

## 7. raw chunk 证据选择

第一版复用现有 raw chunk 检索能力：

```text
对用户问题做现有向量召回
限制在 selected paperIds 内
扩大召回数量
回查 MySQL 后过滤 reference/noise
按 paperId 分组
每篇论文最多保留 1~2 条最高分 chunk
```

这样既不改 Qdrant collection，也能补充原文证据。

如果某篇论文没有召回 raw chunk：

```text
该论文仍保留 profile + section_summary
sources 中不强制出现 raw_chunk
回答中如果缺证据，需要说明对应信息来自画像/摘要
```

## 8. Prompt 设计

HYBRID_RAG prompt 需要明确区分论文与来源类型。

核心要求：

```text
你是论文/文献 AI 研究助手。下面提供多篇论文的混合上下文，包括文献画像、章节摘要和少量原文证据。

请遵守：
1. 先逐篇分析每篇论文，再做横向比较。
2. 不要把 A 论文的信息归到 B 论文。
3. 优先依据 paper_profile 和 section_summary 形成整体判断。
4. 涉及具体论据时参考 raw_chunk。
5. 如果某篇论文某项信息不足，请明确说明“信息不足”。
6. 回答用户问题，不要泛泛复述上下文。
```

对于比较型问题，建议回答结构：

```text
1. 简要结论
2. 逐篇分析
3. 对比表
4. 共同点
5. 主要差异
6. 适合作为后续研究基础的建议
7. 引用来源说明
```

不是所有问题都强制输出完整结构，但 prompt 应鼓励这种组织方式。

## 9. Service 边界设计

### 9.1 新增 HybridRagContextService

建议新增：

```text
HybridRagContextService
- buildHybridContext(HybridRagContextRequest request): HybridRagContext
```

职责：

```text
1. 校验 paperIds。
2. 查询 paper_profile。
3. 查询 paper_section_summary。
4. 按问题选择相关 section_summary。
5. 接收或内部补充 raw chunk sources。
6. 按论文组织上下文文本。
7. 生成 HYBRID_RAG sources。
8. 估算 contextTokenCount。
```

### 9.2 DTO 建议

新增内部 DTO：

```text
HybridRagContextRequest
- question
- paperIds
- rawChunkSources
- maxSectionSummariesPerPaper
- maxRawChunksPerPaper

HybridRagContext
- contextText
- sources
- contextTokenCount
- contextPaperIds
```

如果现有 DTO 已能表达 sources，可复用现有 `RagSource`，新增字段：

```text
sourceType: paper_profile / section_summary / raw_chunk / full_text
sectionSummaryId
profileId
```

### 9.3 与 RagChatService 的关系

`RagChatService` 保持主编排职责：

```text
1. 读取请求。
2. 调用 ContextStrategyService 判断策略。
3. FULL_TEXT_PARSED → FullTextContextService。
4. HYBRID_RAG → HybridRagContextService。
5. VECTOR_RAG → 现有 retrieval 流程。
6. 调用 RagPromptService 构造 prompt。
7. 调用 LlmService。
8. 保存 chat_message。
9. 返回 RagChatResponse。
```

避免把 HYBRID_RAG 的上下文构造细节全部塞进 `RagChatService`。

## 10. 响应字段设计

沿用 `/api/rag/chat`，请求体仍支持：

```json
{
  "question": "这几篇论文的方法有什么区别？",
  "paperIds": [7, 8],
  "topK": 5
}
```

响应继续包含：

```text
answer
sources
model
sessionId
contextStrategy
contextTokenCount
contextPaperIds
suggestSaveAsIdea
ideaSuggestionReason
```

HYBRID_RAG 下：

```text
contextStrategy = HYBRID_RAG
contextPaperIds = [7, 8]
sources 包含 paper_profile、section_summary、raw_chunk 等 sourceType
```

source 示例：

```json
{
  "paperId": 7,
  "paperTitle": "...",
  "sourceType": "section_summary",
  "sectionType": "METHOD",
  "sectionTitle": "Method",
  "summaryVersion": "section-summary-v1"
}
```

## 11. 错误处理与回退

### 11.1 paperIds 为空

保持现有 `VECTOR_RAG` 行为，不进入 HYBRID_RAG。

### 11.2 单篇论文

保持现有规则：

```text
预算内 → FULL_TEXT_PARSED
否则 → VECTOR_RAG
```

### 11.3 多篇但画像缺失

回退：

```text
VECTOR_RAG
```

不自动生成画像。

### 11.4 section_summary 缺失

如果 profile 存在但 section_summary 为空：

```text
仍可进入 HYBRID_RAG
上下文只包含 paper_profile + raw_chunk
```

因为 `paper_profile` 是最低可用资产。

### 11.5 raw chunk 缺失

如果某篇论文没有 raw chunk source：

```text
不失败
保留 profile + section_summary
```

### 11.6 LLM 调用失败

沿用现有全局异常处理，不落库不完整 assistant 回答。

## 12. 测试设计

单元测试重点：

```text
1. ContextStrategyService：多篇且画像齐全时选择 HYBRID_RAG。
2. ContextStrategyService：多篇但任一画像缺失时回退 VECTOR_RAG。
3. HybridRagContextService：上下文包含每篇 paper_profile。
4. HybridRagContextService：方法类问题优先选择 METHOD summary。
5. HybridRagContextService：每篇 section_summary 和 raw_chunk 数量受限。
6. RagPromptService：HYBRID_RAG prompt 包含逐篇分析和横向比较要求。
7. RagChatService：HYBRID_RAG 分支返回 contextStrategy、contextPaperIds 和 hybrid sources。
```

整体后端验证：

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

Apifox 验证建议：

```http
POST /api/papers/7/profile
POST /api/papers/8/profile
POST /api/rag/chat
```

请求体：

```json
{
  "question": "这两篇论文的方法有什么区别？",
  "paperIds": [7, 8],
  "topK": 5
}
```

验证点：

```text
code = 200
contextStrategy = HYBRID_RAG
contextPaperIds 包含 7 和 8
sources 中包含 paper_profile 或 section_summary
回答包含逐篇分析和横向比较
```

## 13. 与后续阶段的衔接

本阶段完成后，系统上下文策略变为：

```text
单篇可读全文 → FULL_TEXT_PARSED
多篇画像齐全 → HYBRID_RAG
其他情况 → VECTOR_RAG
```

后续可以继续增强：

```text
1. paper_profile / section_summary 入 Qdrant，支持多类型语义召回。
2. 新增 Query Analyzer，用 LLM 判断问题类型和目标章节。
3. 新增 rerank，对 raw chunk 证据做更精细排序。
4. 新增 Claim Cards，为 Research Idea 和文献综述提供更强证据层。
5. 前端展示 contextStrategy，并提示哪些论文缺少画像。
```

## 14. 进度记录要求

本阶段每个实质阶段都要更新 `docs/status/current.md`：

```text
阶段 1：策略枚举和选择规则
阶段 2：HybridRagContextService 上下文构造
阶段 3：RAG chat/prompt 接入
阶段 4：整体测试、Apifox 验证建议和改进记录
```

记录只写项目实质进展、验证结果、当前状态和下一步任务，不记录临时命令细节。
