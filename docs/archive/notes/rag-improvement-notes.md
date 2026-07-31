# RAG 优化问题与解决方案记录

更新时间：2026-07-08

本文记录当前论文/文献 AI 研究助手中 RAG 链路暴露出的关键问题，以及后续可采用的改进方向。本文不是立即实现计划，而是作为后续设计和开发的依据。

## 1. 单篇或少数几篇文献是否必须走 RAG 检索

### 问题

当前 RAG 链路默认是：

```text
用户问题
→ 问题向量化
→ Qdrant topK 检索
→ 回查 chunk 原文
→ 拼接 prompt
→ 大模型回答
```

但本项目面向的是论文/文献问答。单篇论文或少数几篇论文通常不会特别长，现代大模型上下文窗口已经有能力容纳完整正文或较完整的结构化正文。此时如果仍然只召回少量 chunk，会导致：

- 模型只能看到局部片段，缺少论文整体结构。
- 对“创新点、方法流程、优缺点、实验设计、局限性”等问题回答片面。
- topK 可能命中不关键的引言、背景或参考文献片段。
- 用户选中单篇论文精读时，系统却只基于几个片段回答，体验不符合预期。

### 解决方向

后续应引入 **Context Strategy Router（上下文策略路由器）**，根据文献范围、正文 token 数和问题类型自动选择上下文模式。

建议至少支持三种模式：

```text
FULL_TEXT_PARSED  单篇且 token 在预算内，直接提供结构化全文上下文
HYBRID_RAG        文献画像 + 章节摘要 + 原文证据，适合多篇对比和研究分析
VECTOR_RAG        文献较多或画像缺失时使用普通向量检索
```

设计原则：

```text
能读全文时，不要只看片段。
需要比较时，不要只按全局 topK，要按论文均衡组织证据。
需要研究洞察时，不要只用原文 chunk，要引入文献画像和章节摘要。
```

## 2. 多篇论文检索片段太片面，难以回答优缺点和横向比较

### 问题

普通 chunk 检索适合回答局部事实型问题，例如：

```text
哪一段提到了 xxx？
某篇论文是否使用了某个数据集？
```

但它不擅长回答分析型、比较型问题，例如：

```text
这几篇论文各自有什么优缺点？
这些论文的方法路线有什么区别？
哪篇更适合作为我的研究基础？
这些论文共同的研究趋势是什么？
```

原因是“优缺点、贡献、方法差异、研究价值”通常不是某个 chunk 直接写出来的，而是需要先理解整篇论文，再进行归纳和比较。

### 解决方向

后续应在原始 chunk 索引之外，引入多层文献理解资产：

```text
1. Paper Profile 文献画像
2. Section Summary 章节摘要
3. Raw Chunk Evidence 原文证据片段
4. Claim / Finding Cards 观点卡片
```

对于“优缺点、对比、综述、方法差异”类问题，不应只走全局 topK。建议流程：

```text
用户问题
→ 判断为 comparative / analytical question
→ 对每篇选中文献分别取：
    - paper profile
    - method summary
    - experiment/result summary
    - limitation/future work summary
    - 少量原文证据 chunk
→ 按论文逐篇分析
→ 再做横向比较
→ 输出对比表和结论
```

## 3. 检索总是命中参考文献

### 问题

参考文献区经常包含大量论文标题、作者、年份、DOI、会议和期刊名称。它们在 embedding 空间中可能与用户问题很相似，但通常不是回答论文内容问题所需的证据。

如果参考文献 chunk 进入普通检索，会导致：

- topK 被参考文献占用。
- 回答引用到无关文献标题。
- 模型误以为参考文献内容就是当前论文观点。

### 解决方向

PDF 解析时识别章节标题，将参考文献区标记为：

```text
sectionType = REFERENCES
isReference = true
```

普通问答检索默认过滤：

```text
isReference = false
```

同时增加低价值 chunk 规则过滤，例如 DOI / URL 密集、页眉页脚、Funding、Conflict of Interest 等。

## 4. 单纯相似度匹配不够，需要 Query Rewrite 和 rerank

### 问题

用户问题通常是口语化、中文化、任务化的，例如：

```text
这几篇论文有什么优缺点？
这个方法靠谱吗？
和传统方法比强在哪？
```

但论文原文更可能使用学术表达：

```text
limitations
advantages
ablation study
experimental results
comparative performance
robustness
generalization
```

如果直接对原始问题向量化，可能召回不到真正相关的论文片段。

### 解决方向

Query Rewrite 不只生成一个改写，而是生成多个检索 query：

```text
1. 原始问题
2. 英文学术表达改写
3. section-targeted query
4. HyDE query（假设性答案式检索）
```

向量相似度适合粗召回，最终进入 prompt 的片段应经过重排：

```text
阶段 1：vector score + section boost + reference penalty
阶段 2：接入 reranker 模型
阶段 3：LLM relevance grading / answer-aware selection
```

对于多篇比较问题，还应做 per-paper balance，避免某一篇论文占满所有 topK。

## 5. 直接让大模型读取 PDF 的定位

直接让大模型读 PDF 适合：

```text
单篇论文精读
图表解释
表格分析
公式理解
版面结构相关问题
PDFBox 解析质量较差的论文
```

但它不应替代现有 RAG，因为多篇论文成本高、响应慢，不利于长期沉淀文献画像、章节摘要、claim cards 等结构化资产。它更适合作为后续的一种上下文策略：

```text
FULL_TEXT_PARSED  使用 PDFBox 已解析文本
DIRECT_PDF_LLM    直接把 PDF 文件交给支持 PDF 的模型
HYBRID_RAG        文献画像 + 章节摘要 + 检索片段
VECTOR_RAG        普通向量检索
```

## 6. 文献数据模型和向量索引需要重点设计

论文不是普通长文本，而是有强结构的研究文档：

```text
标题 / 摘要 / 引言 / 相关工作 / 方法 / 实验 / 结果 / 讨论 / 结论 / 参考文献 / 附录
```

MySQL 应作为文献结构化知识主库：

```text
paper_reference           文献基本信息
paper_section             文献章节
paper_chunk               原文片段
paper_profile             文献画像
paper_section_summary     章节摘要
paper_profile_job         画像生成任务状态
paper_claim               研究主张/发现/局限卡片（后续）
paper_vector_task         向量化任务状态（后续）
```

Qdrant 是语义检索索引，payload 至少包括：

```json
{
  "paperId": 1,
  "chunkId": 123,
  "sectionId": 10,
  "sectionType": "METHOD",
  "sectionTitle": "Proposed Method",
  "chunkIndex": 8,
  "isReference": false,
  "isNoise": false,
  "contentType": "RAW_CHUNK"
}
```

未来不应只向量化原文 chunk，而应逐步支持：

```text
RAW_CHUNK        原文证据
SECTION_SUMMARY  章节语义
PAPER_PROFILE    文献整体理解
CLAIM_CARD       观点、发现、局限、idea 线索
```

## 7. 推荐的整体架构方向：Adaptive Hybrid RAG

推荐总流程：

```text
用户问题
  ↓
Query Analyzer 问题分析
  - 问题类型：事实型 / 总结型 / 比较型 / 方法型 / idea 型
  - 目标范围：单篇 / 多篇 / 全库
  - 是否需要全文
  - 是否需要参考文献
  ↓
Context Strategy Router 上下文策略选择
  - FULL_TEXT_PARSED
  - HYBRID_RAG
  - VECTOR_RAG
  ↓
Query Rewrite
  - 原始问题
  - 英文/学术表达改写
  - section-targeted query
  - HyDE query
  ↓
Multi-source Retrieval
  - Paper Profile
  - Section Summary
  - Raw Chunks
  - Claim Cards
  ↓
Filtering / Rerank
  - 排除 references
  - paperIds 范围过滤
  - sectionType boost
  - per-paper balance
  ↓
Prompt Builder
  - 按文献组织上下文
  - 保留 sources
  - 控制 token budget
  ↓
LLM Answer
  - 回答
  - 引用来源
  - 是否建议保存为 Research Idea
```

## 8. 已落地改进记录：paper-structure-v1 结构化分块

更新时间：2026-07-06

### 改进背景

旧方法主要是固定窗口切分：

```text
PDFBox 解析全文
→ 简单清洗换行
→ 按固定字符长度切 chunk
→ 直接向量化 chunk.content
→ Qdrant 全局 topK
→ 回查 chunk 拼 prompt
```

这种方法把论文当成普通长文本处理，没有显式理解论文结构。它的问题是：

```text
1. 不知道 chunk 属于 Method、Experiment、Conclusion 还是 References。
2. 参考文献区会和正文一起进入向量库。
3. 用户问“创新点、论文讲什么、核心方法”这类问题时，topK 可能召回参考文献或低价值内容。
4. Qdrant payload 只有 paperId、chunkId、chunkIndex 和文本预览，缺少章节类型、参考文献标记、分块策略版本等信息。
5. 后续很难做父子召回、章节级摘要、单篇全文上下文和多篇比较。
```

### 新方法

已实现 `paper-structure-v1` 结构化分块基础：

```text
PDFBox 解析全文
→ PaperTextCleaner 文本清洗
→ PaperSectionDetector 章节识别
→ 标记 References / BACK_MATTER
→ StructuredChunkingService 章节内分块
→ MySQL 写入 paper_section + paper_chunk
→ 向量化时使用 indexText
→ Qdrant payload 写入章节元数据
→ RAG 检索扩大召回后回查 MySQL，过滤 isReference/isNoise
```

### 解决的问题

- 解决参考文献误召回。
- 解决 Funding、Conflict of Interest、Author Contributions 等后置低价值内容干扰。
- 增加 `sectionType`、`sectionId`、`isReference`、`isNoise`、`chunkStrategyVersion`，为全文策略、章节摘要和多篇比较打基础。
- 向量化使用 `indexText`，把论文标题、章节类型、章节标题和正文一起编码，提高方法/实验/结论类问题的匹配概率。

### 仍然存在的问题

结构化分块解决了“召回脏内容、参考文献污染、缺少章节元数据”的问题，但对于整篇论文级宏观问题仍然不够，例如：

```text
这篇论文主要讲什么？
这篇论文的核心创新点是什么？
这篇论文整体方法流程是什么？
这篇论文有什么优缺点？
```

这些问题需要模型理解整篇论文结构，而不是只看 topK 的几个 chunk。

## 9. 已落地改进记录：单篇 FULL_TEXT_PARSED

更新时间：2026-07-07

### 相比 paper-structure-v1 的改进

`paper-structure-v1` 解决了参考文献误召回、后置低价值内容干扰和 chunk 缺少章节元数据的问题，但宏观问题仍然只看 topK 局部片段。

`FULL_TEXT_PARSED` 在单篇论文、正文 token 不超预算时，不再走 Qdrant topK，而是从 MySQL 读取结构化 chunk，默认排除 `REFERENCES`、`BACK_MATTER`、`isReference=true`、`isNoise=true` 内容，并按章节组织较完整正文上下文交给大模型回答。

核心变化：

```text
paper-structure-v1：单篇论文问题 → 向量检索 topK chunk → 局部上下文回答
FULL_TEXT_PARSED：单篇论文问题 → 按章节读取全文正文 chunk → 全文结构化上下文回答
```

### 解决的问题

- 改善“这篇论文主要讲了什么？”这类整篇概括问题。
- 改善“这篇论文的创新点 / 整体方法 / 优缺点是什么？”这类需要综合摘要、引言、方法、实验、结果和结论的问题。
- 减少仅靠局部 topK chunk 导致回答片面的情况。
- 继续保留参考文献和后置低价值内容默认排除策略。
- 响应新增 `contextStrategy`、`contextTokenCount`、`contextPaperIds`，便于调试和评测。

## 10. 已落地改进记录：多篇 HYBRID_RAG + 文献画像闭环

更新时间：2026-07-08

### 改进背景

上一版本 `FULL_TEXT_PARSED` 主要解决“单篇论文、正文 token 不超预算”时的全文理解问题。但当用户选择多篇论文进行比较、分析或综述时，系统仍主要回退到 `VECTOR_RAG`：

```text
多篇 paperIds
→ 问题 embedding
→ Qdrant topK 检索少量 raw chunks
→ 拼 prompt
→ 回答
```

这种方式的问题是：

```text
1. 多篇比较只看到每篇论文的零散片段，缺少全局画像。
2. 全局 topK 容易被某一篇论文占满，其他论文覆盖不足。
3. 方法、贡献、实验、局限等信息分散在不同章节，少量 chunk 难以完整覆盖。
4. 用户不知道哪些论文缺少画像，也没有前端入口补齐画像。
5. 画像同步生成耗时长，前端容易 timeout，显示失败但后端其实仍在执行。
6. LLM 返回格式不稳定时，后端解析失败会把字段写成“信息不足”。
```

### 新方法总览

当前版本补齐了“文献画像生成 → 多篇画像齐全 → HYBRID_RAG 比较问答”的闭环：

```text
上传/解析论文
→ 结构化分块生成 paper_section + paper_chunk
→ 用户在文献页启动异步画像任务
→ 后台按章节生成 paper_section_summary
→ 后台结合章节摘要 + 原文证据生成 paper_profile
→ 前端轮询 paper_profile_job 状态并展示画像
→ 问答页选择多篇论文
→ ContextStrategyService 判断画像是否齐全
→ 画像齐全则进入 HYBRID_RAG
→ HybridRagContextService 组织 profile/summary/raw_chunk 三层上下文
→ RagPromptService 构造多篇比较 prompt
→ LLM 生成逐篇分析 + 横向比较回答
```

### 具体实现

#### 1. ContextStrategy 新增 HYBRID_RAG

新增策略：

```java
ContextStrategy.HYBRID_RAG
```

选择规则：

```text
如果 paperIds 去重后数量 >= 2
并且每篇论文都有 paper-profile-v1
    选择 HYBRID_RAG
否则
    回退 VECTOR_RAG
```

这样避免在聊天接口中临时生成画像，防止问答请求变慢和失败点增多。

#### 2. 新增 HybridRagContextService

`HybridRagContextService` 负责按用户选择顺序组织多篇上下文。

每篇论文上下文结构：

```text
[Paper {id}] {title}
一、文献画像
- 研究问题
- 方法概述
- 实验评估
- 主要贡献
- 局限性
- 关键词
- 画像文本

二、相关章节摘要
- 按问题关键词优先选择 METHOD / EXPERIMENT / RESULT / DISCUSSION / CONCLUSION 等章节摘要

三、原文证据片段
- 每篇最多少量 raw chunks，作为具体证据
```

问题关键词到章节优先级示例：

```text
方法类：METHOD, RELATED_WORK, INTRODUCTION
实验结果类：EXPERIMENT, RESULT, DISCUSSION
局限不足类：DISCUSSION, CONCLUSION, RESULT, EXPERIMENT
创新贡献类：ABSTRACT, INTRODUCTION, METHOD, CONCLUSION
比较对比类：METHOD, EXPERIMENT, RESULT, DISCUSSION, CONCLUSION
```

#### 3. RagChatService 接入 HYBRID_RAG 分支

`RagChatServiceImpl` 在策略为 `HYBRID_RAG` 时：

```text
1. 尝试按 paperIds 检索少量 raw chunks。
2. 如果 raw 检索失败，则降级为空 raw chunks，不让整轮问答失败。
3. 调用 HybridRagContextService 构造混合上下文。
4. 调用 RagPromptService.buildHybridPrompt 构造多篇比较 prompt。
5. 调用 LLM 生成回答。
6. 返回 contextStrategy、contextTokenCount、contextPaperIds 和 hybrid sources。
```

raw chunk 在当前版本中是增强证据，不再是 HYBRID_RAG 的硬性前置条件。

#### 4. RagPromptService 新增多篇比较 prompt

HYBRID_RAG prompt 明确要求：

```text
1. 先逐篇分析每篇论文，再做横向比较。
2. 不要把 A 论文的信息归到 B 论文。
3. 优先依据 paper_profile 和 section_summary 形成整体判断。
4. 涉及具体论据时参考 raw_chunk。
5. 如果某篇论文某项信息不足，请明确说明“信息不足”。
6. 回答用户问题，不要泛泛复述上下文。
```

建议回答结构：

```text
1. 简要结论
2. 逐篇分析
3. 对比表
4. 共同点
5. 主要差异
6. 后续研究建议
7. 引用来源说明
```

#### 5. RagSource 扩展来源类型

`RagSource` 新增 hybrid 元数据：

```text
sourceType = paper_profile / section_summary / raw_chunk / full_text
profileId
profileVersion
sectionSummaryId
summaryVersion
sectionId
sectionType
sectionTitle
```

这样前端或调试信息能区分回答依据来自：

```text
全文画像
章节摘要
原文证据
```

#### 6. 文献画像生成质量修复

画像生成不是硬规则生成，而是：

```text
规则筛选正文 + LLM 生成摘要/画像 + 规则解析 LLM 输出
```

本轮修复了三个质量问题：

```text
1. LLM Markdown/半角冒号/加粗/序号格式导致解析失败。
2. 前端画像弹窗字段名和后端 DTO 不一致。
3. 整篇 paper_profile 只基于 section_summary，摘要压缩后遗漏实验/局限/贡献细节。
```

现在后端解析器支持：

```text
研究问题：xxx
研究问题: xxx
### 研究问题
**研究问题**：xxx
1. 研究问题：xxx
- 研究问题：xxx
```

整篇画像生成 prompt 也从：

```text
只基于章节摘要
```

升级为：

```text
章节摘要 + 原文证据片段
```

并明确要求：如果章节摘要信息不足，但原文证据包含对应信息，应优先使用原文证据。

#### 7. 文献画像生成异步化

同步画像生成的问题：

```text
一篇长论文可能有几十个章节
→ 每个章节调用一次 LLM 生成摘要
→ 最后再调用一次 LLM 生成整篇画像
→ 前端 HTTP 请求容易超时
→ 用户看到“生成失败”
→ 但后端实际仍在继续写数据库
```

当前新增：

```text
paper_profile_job
```

记录任务状态：

```text
PROCESSING / COMPLETED / FAILED
errorMessage
startTime
finishTime
```

新增接口：

```http
POST /api/papers/{id}/profile/async
GET  /api/papers/{id}/profile/job
```

前端流程：

```text
点击生成画像
→ POST /profile/async 立即返回 PROCESSING
→ 弹窗显示“文献画像生成中”
→ 每 3 秒轮询 /profile/job
→ COMPLETED 后自动 GET /profile 刷新画像
→ FAILED 后展示 errorMessage
→ 关闭弹窗或离开页面时停止轮询
```

### 相比上一版本的实际提升

```text
上下文组织：零散 raw chunks → paper_profile + section_summary + raw_chunk 三层上下文
多篇比较：全局 topK → 按论文顺序逐篇组织，再横向比较
画像入口：无前端入口 → 文献页可生成/查看/重新生成画像
画像生成体验：同步长请求 → 异步任务 + 轮询状态
失败可见性：前端 timeout 假失败 → FAILED 状态保存真实 errorMessage
画像质量：容易“信息不足” → 支持多格式 LLM 输出解析 + 原文证据补充
稳定性：HYBRID_RAG 强依赖 Qdrant raw 检索 → raw 检索失败仍可用画像/章节摘要回答
调试能力：普通 sources → contextStrategy/contextTokenCount/contextPaperIds/sourceType 可观测
```

### 当前完整流程

```text
1. 用户上传 PDF。
2. 后端解析 PDF，生成 paper_section 和 paper_chunk。
3. 用户点击“画像”。
4. 前端查询 GET /api/papers/{id}/profile 和 GET /api/papers/{id}/profile/job。
5. 用户点击“生成文献画像”。
6. 前端调用 POST /api/papers/{id}/profile/async。
7. 后端写入 paper_profile_job，状态为 PROCESSING。
8. 后端后台执行 PaperProfileService.generateProfile(id)。
9. PaperProfileService 过滤 REFERENCES/BACK_MATTER/reference/noise chunk。
10. 后端按章节调用 LLM 生成 paper_section_summary。
11. 后端结合 section_summary 和 raw chunk 原文证据调用 LLM 生成 paper_profile。
12. 成功后任务状态更新为 COMPLETED，失败则更新为 FAILED 并保存 errorMessage。
13. 前端轮询 GET /api/papers/{id}/profile/job。
14. 任务 COMPLETED 后，前端自动 GET /api/papers/{id}/profile 并展示画像。
15. 用户在问答页选择多篇论文。
16. RagChatService 调用 ContextStrategyService 判断策略。
17. 如果多篇画像齐全，进入 HYBRID_RAG。
18. RagChatService 尝试 raw chunk 检索，失败则降级为空 raw chunks。
19. HybridRagContextService 组合每篇论文的 profile、summary、raw_chunk。
20. RagPromptService 构造多篇比较 prompt。
21. LLM 输出逐篇分析和横向比较回答。
22. 响应返回 answer、sources、contextStrategy、contextTokenCount、contextPaperIds。
```

### 当前边界和限制

```text
1. paper_profile_job 目前只有整体状态，没有章节级进度百分比。
2. 章节摘要仍基本串行生成，长论文耗时仍可能较久，只是前端不再阻塞。
3. HYBRID_RAG 当前对 section_summary 的选择仍是规则关键词，不是语义检索或 rerank。
4. paper_profile 和 paper_section_summary 尚未向量化，不能作为 Qdrant 多粒度召回对象。
5. sources 前端展示还没有充分区分 paper_profile / section_summary / raw_chunk。
6. 当前任务执行线程池是应用内存级，服务重启后 PROCESSING 任务不会自动恢复，需要后续任务恢复机制。
```

### 下一步改进方向

#### 1. 画像任务进度细化

当前只有：

```text
PROCESSING / COMPLETED / FAILED
```

建议增加：

```text
total_sections
finished_sections
current_section_title
progress_percent
```

前端显示：

```text
正在生成第 12 / 51 个章节摘要：Method
```

#### 2. 章节摘要受控并发

当前长论文画像慢的根因是多次 LLM 调用。后续可把章节摘要生成改为受控并发：

```text
每次并发 3~5 个章节
遇到 Qwen 限流时退避重试
```

目标是在不触发限流的前提下降低总耗时。

#### 3. HYBRID_RAG 问题类型识别

当前 section summary 选择主要靠关键词。后续应新增 Query Analyzer：

```text
方法问题 → METHOD / RELATED_WORK / INTRODUCTION
实验问题 → EXPERIMENT / RESULT / DISCUSSION
局限问题 → DISCUSSION / CONCLUSION
贡献问题 → ABSTRACT / INTRODUCTION / CONCLUSION
综述比较 → METHOD / EXPERIMENT / RESULT / DISCUSSION / CONCLUSION
```

#### 4. 多篇比较评测集

在 `docs/evaluation/cases.md` 增加多篇比较问题，例如：

```text
这两篇论文的方法路线有什么区别？
哪篇论文的实验更充分？
它们的主要局限分别是什么？
哪篇更适合作为后续研究基础？
```

指标建议：

```text
paperHitRate
comparisonCoverage
keyPointCoverage
citationSupportRate
answerCompletenessScore
answerNoiseScore
```

#### 5. sources 前端展示增强

前端引用区应区分：

```text
paper_profile：全文画像
section_summary：章节摘要
raw_chunk：原文证据
full_text：单篇全文上下文
```

这样用户能判断回答依据的层级和可信度。

#### 6. 多粒度向量索引

后续可将以下对象都向量化：

```text
paper_profile
paper_section_summary
raw_chunk
claim_card
```

形成多粒度检索：

```text
先召回 profile 判断相关论文
再召回 section_summary 定位章节
最后召回 raw_chunk 提供证据
```

#### 7. 任务恢复和失败重试

当前 PROCESSING 任务如果服务重启，数据库里可能保留处理中状态。后续应增加：

```text
启动时扫描长时间 PROCESSING 任务
标记为 FAILED 或 RETRYABLE
前端提供重新生成按钮
```

## 11. 已落地改进记录：全库 RAG 检索 + 高级 Query Rewrite + 论文发现

更新时间：2026-07-10

### 改进背景

此前四个版本（`paper-structure-v1`、`FULL_TEXT_PARSED`、`HYBRID_RAG`、文献画像闭环）解决了结构化分块、单篇全文上下文、多篇混合上下文和画像生成的问题，但 RAG 始终有一个根本性限制：

```text
必须先选论文，才能提问。
```

当用户不知道哪篇论文相关时——例如"有没有论文用 Transformer 做时间序列预测？"——系统无法自己从全部文献中发现相关内容。同时原有的 `QueryRewriteServiceImpl` 只做中文→英文翻译，不能解决"用户疑问句"与"论文陈述句"之间的语义鸿沟：

```text
用户问："有什么论文用了注意力机制？"        （疑问句，口语化，中文）
论文写："We propose a novel attention-based   （陈述句，学术化，英文）
         architecture for ..."
```

直接拿疑问句嵌入去搜陈述句，embedding 相似度天然偏低。此外，全库检索返回的扁平 chunk 列表没有按论文聚合，前端看不到"哪篇论文最相关"。

### 新方法总览

当前版本新增 `LIBRARY_DISCOVERY` 上下文策略，在用户不指定论文时自动启用：

```text
用户提问（不选论文）
  ↓
AdvancedQueryRewriteService  单次 LLM 调用同时完成
  ├── 疑问句→学术陈述句改写（declarativeQuery）
  ├── 核心技术关键词提取（keywordQuery）
  └── HyDE 假设性答案生成（hydeQuery）
  ↓
RagRetrievalService.retrieveSourcesWithRewrite()
  三路 query 分别 Qdrant 检索
  ↓
RRF（Reciprocal Rank Fusion）三路融合去重
  ↓
MySQL 回查 + isReference/isNoise 过滤
  ↓
PaperDiscoveryService 按论文聚合 + 综合评分排序
  ↓
RagPromptService.buildLibraryDiscoveryPrompt()
  引导模型回答 + 列出最相关论文
  ↓
LLM 生成回答
  ↓
响应返回 answer + sources + paperRelevance 列表
```

### 具体实现

#### 1. 高级 Query Rewrite：单次 LLM 调用完成三件事

**原有 `QueryRewriteServiceImpl` 的局限：**

```text
中文问题 → LLM 翻译为英文 query → 返回一个字符串
```

这只能解决跨语言，不能解决疑问句/陈述句语义鸿沟，也不能提供多路检索用的不同 query。

**新 `AdvancedQueryRewriteServiceImpl`：**

单次 LLM 调用同时完成三步，避免多次调用增加延迟：

```text
Prompt 要求 LLM 一次性输出 JSON：
{
  "declarativeQuery": "Transformer architecture for wind speed time series forecasting",
  "keywordQuery": "Transformer attention time series forecasting wind speed",
  "keywordTerms": "Transformer, 注意力机制, 时间序列, 风速预测",
  "hydeQuery": "This paper proposes a Transformer-based model for wind speed forecasting
                using multi-head attention to capture temporal dependencies..."
}
```

三路 query 各有用处：

| Query 类型 | 用途 | 示例 |
|-----------|------|------|
| `declarativeQuery` | 主力检索——陈述句式与论文原文风格最接近 | "Transformer architecture for wind speed forecasting" |
| `keywordQuery` | 精确匹配——纯关键词拼接，无句式干扰 | "Transformer attention time series forecasting wind speed" |
| `hydeQuery` | 语义桥接——假设性答案的 embedding 通常比问题的 embedding 更接近真实相关 chunk | "This paper proposes a Transformer-based model..." |

**兜底策略：**
- LLM 返回空 → `declarativeQuery` 用原始问题，其他为 null，`allQueries()` 至少返回 1 个
- LLM JSON 解析失败 → 同上，同时 `log.warn` 输出 LLM 前 500 字符
- `allQueries()` 自动去重：如果三路 query 内容重复，只保留不同值
- 复用已有的 `FlexibleStringDeserializer`，防止 LLM 返回数组导致 Jackson 反序列化失败

#### 2. 多路 RRF 融合替代简单双路去重

**原有 `RagRetrievalServiceImpl` 的合并逻辑：**

```text
原始问题 → Qdrant search → 记录 chunkId + score
改写问题 → Qdrant search → 记录 chunkId + score
两路结果：同 chunkId 保留 score 更高的 → 按 score 降序 → topK
```

问题：不同 query 的 score 分布不可比（一个 query 的 score 0.7 可能排第 1，另一个 query 的 score 0.7 可能排第 20），简单取 max 会丢失排名信息。

**新 RRF（Reciprocal Rank Fusion）融合：**

```text
route_1（declarativeQuery）检索 → chunkRank(chunkId→排名)
route_2（keywordQuery）检索     → chunkRank(chunkId→排名)
route_3（hydeQuery）检索         → chunkRank(chunkId→排名)

对每个 chunk：
  RRF_score = Σ 1/(k + rank_i)
  其中 k = 60（经典取值），rank_i 从 1 开始

按 RRF_score 降序 → topK
```

RRF 的优势：
- 不依赖 score 的绝对值，只关心排名
- 一个 chunk 在多个检索路中排名都靠前时，RRF 分自然更高
- 避免单一 query 的 score 分布主导最终结果

**降级策略：**
- 三路检索中任一路失败（Qdrant 超时/异常），跳过该路，用剩余路继续
- 全部失败时返回空列表，上层 `RagChatServiceImpl` 会正常返回错误提示

#### 3. 论文级聚合服务

全库检索返回的 chunks 可能来自多篇论文，扁平列表无法回答"哪篇论文最相关"。

**新增 `PaperDiscoveryService`：**

```text
1. 按 paperId 分组
2. 每组计算：
   - hitCount：该论文命中 chunk 数
   - maxScore：最高 RRF 分数
   - avgScore：平均 RRF 分数
   - topChunks：该论文最相关的 2 个 chunk
3. 综合评分 = hitCount_normalized × 0.5 + maxScore × 0.5
4. 按综合评分降序排列
5. 返回 topPapers（默认 5 篇）
```

**返回 `PaperRelevance` DTO：**

```java
{
  "paperId": 9,
  "paperTitle": "BVMD-PAM-TWGCNet...",
  "hitCount": 5,           // 在全库检索中命中了 5 个 chunk
  "avgScore": 0.0321,
  "maxScore": 0.0502,
  "topChunks": [           // 该论文最相关的 2 个片段
    { "content": "...", "score": 0.0502 },
    { "content": "...", "score": 0.0401 }
  ]
}
```

#### 4. ContextStrategy 扩展与 LIBRARY_DISCOVERY 模式

**策略选择逻辑更新：**

```text
paperIds 为空
  → LIBRARY_DISCOVERY  （新增，此前这里走 VECTOR_RAG）
paperIds size = 1
  → FULL_TEXT_PARSED / VECTOR_RAG  （不变）
paperIds size >= 2
  → HYBRID_RAG / VECTOR_RAG  （不变）
```

**RagChatServiceImpl 新增分支：**

```java
if (ContextStrategy.LIBRARY_DISCOVERY.equals(contextStrategy)) {
    QueryRewriteResult rewriteResult = advancedQueryRewriteService.rewrite(question);
    sources = ragRetrievalService.retrieveSourcesWithRewrite(rewriteResult, topK, paperIds);
    prompt = ragPromptService.buildLibraryDiscoveryPrompt(question, sources);
    // ...
    paperRelevance = paperDiscoveryService.aggregateByPaper(sources, 5);
    response.setPaperRelevance(paperRelevance);
}
```

**`buildLibraryDiscoveryPrompt` prompt 设计：**

```text
你是论文/文献发现助手。用户没有指定具体论文，而是想从所有文献中查找与问题相关的内容。

回答要求：
1. 用中文回答。
2. 先回答用户的问题，基于检索到的片段给出实质答案。
3. 在回答末尾明确列出"最相关的论文"：
   **最相关的论文：**
   - 《论文标题》（来源编号：[X]）：为什么这篇论文与问题相关（一句话说明）。
4. 如果某个论文标题出现了多次，请合并为一条。
5. 不要编造文献片段中没有出现的论文信息。
```

#### 5. 前端接入：全库检索模式 + 论文相关度展示

**问答页改动（`RagChatView.vue`）：**

1. **"全库检索模式"提示**：未选论文时，右侧参考论文面板显示信息提示框——
   ```
   当前为全库检索模式，系统将从所有文献中检索相关内容。
   回答下方会显示最相关的论文及其依据片段。
   ```

2. **论文相关度卡片**：回答下方新增 `paper-relevance-panel` 区域，展示每篇最相关论文——
   ```
   最相关的论文
   ┌─────────────────────────────────────────┐
   │ BVMD-PAM-TWGCNet...                     │
   │ 命中 5 片段 · 最高相关度 0.0502          │
   │ "This paper proposes a novel..."        │
   │ "The BVMD module adaptively..."         │
   ├─────────────────────────────────────────┤
   │ ST-FFTransformer...                     │
   │ 命中 3 片段 · 最高相关度 0.0401          │
   │ ...                                     │
   └─────────────────────────────────────────┘
   ```

3. **状态摘要文案变更**：`formatSelectedPaperSummary` 从"未选择参考论文" → "全库检索模式"

### 相比上一版本的实际提升

```text
检索范围：必须指定论文 → 不选论文时可全库检索，系统自动发现相关文献
检索方式：单路或双路 → 三路改写（陈述句 + 关键词 + HyDE）+ RRF 融合
Query Rewrite：中文→英文翻译 → 疑问句→陈述句改写 + 关键词提取 + HyDE 生成
论文聚合：无 → PaperDiscoveryService 按论文聚合 + 综合评分排序
前端可见性：无 → 回答下方展示"最相关的论文"卡片 + 全库检索模式提示
上下文策略：VECTOR_RAG/FULL_TEXT_PARSED/HYBRID_RAG → 新增 LIBRARY_DISCOVERY
语义鸿沟：疑问句直接检索陈述句 → 陈述句改写 + HyDE 双桥接
调试能力：contextStrategy 新增 LIBRARY_DISCOVERY；RagChatResponse 新增 paperRelevance
降级健壮：无 → 多路中任一路失败跳过；LLM 返回空/JSON 失败用原始问题兜底
```

### 当前完整流程（全库发现模式）

```text
1. 用户在问答页不选任何参考论文。
2. 用户输入问题并发送。
3. RagChatService.resolvePaperIds() 返回空列表。
4. ContextStrategyService.chooseStrategy(empty) 返回 LIBRARY_DISCOVERY。
5. AdvancedQueryRewriteService.rewrite() 单次 LLM 调用：
   a. 疑问句→学术陈述句改写
   b. 核心技术关键词提取
   c. HyDE 假设性答案生成
6. RagRetrievalService.retrieveSourcesWithRewrite()：
   a. declarativeQuery → Qdrant search（retrievalLimit = topK × 3）
   b. keywordQuery → Qdrant search
   c. hydeQuery → Qdrant search
   d. 三路结果 RRF 融合（k=60）
   e. MySQL 回查 + 过滤 reference/noise
   f. 按 RRF score 降序返回 topK
7. RagPromptService.buildLibraryDiscoveryPrompt() 构造发现模式 prompt。
8. LlmService.generateAnswer() 生成回答。
9. PaperDiscoveryService.aggregateByPaper() 按论文聚合相关度。
10. ChatHistoryService.saveRagChat() 保存会话历史。
11. 响应返回 answer、sources、paperRelevance、contextStrategy=LIBRARY_DISCOVERY、contextTokenCount。
12. 前端展示回答 + "最相关的论文"卡片。
```

### 当前边界和限制

```text
1. AdvancedQueryRewrite 每次调用都走 LLM，无缓存；频繁全库检索会增加 LLM 调用量。
2. HyDE 生成质量依赖 LLM 能力，当前只做单次生成，没有做多候选择优。
3. RRF 目前只有 dense vector 三路，没有 BM25 或稀疏向量检索参与融合。
4. 无 Reranker 精排：RRF 融合后的 topK 直接进入 prompt，没有经过 cross-encoder 重排序。
5. paperRelevance 的综合评分是规则型（hitCount × 0.5 + maxScore × 0.5），未做更精细的权重调优。
6. 全库检索时 Qdrant 扫描全部 points，文献数量增多后延迟会上升，后续可考虑添加粗排-精排两阶段。
7. paperRelevance 卡片暂不支持点击后限定该论文继续追问（"仅限这篇论文"功能留到后续）。
```

### 下一步改进方向

#### 1. 混合检索（Dense + BM25）

当前仅 dense 向量检索。学术论文中有大量精确术语（模型名、数据集名、指标名），dense 向量对这些术语的匹配不如 BM25。后续可在 RRF 中增加 BM25 路由：

```text
route_1: declarativeQuery → dense embedding → Qdrant
route_2: keywordQuery     → dense embedding → Qdrant
route_3: hydeQuery        → dense embedding → Qdrant
route_4: keywordQuery     → BM25 keyword     → 待选型（ES / Qdrant 稀疏向量 / 本地 Lucene）
```

#### 2. Reranker 精排

RRF 融合后扩大召回（例如 30 个候选），再用 cross-encoder（如 BGE-Reranker-v2-m3）对候选 chunk 做精排，只把 topK 送入 prompt。

#### 3. HyDE 多候选择优

当前 HyDE 只生成一段假设性答案。可生成 2~3 段不同角度的假设性答案，分别检索后合并，提高覆盖率。

#### 4. "仅限这篇论文"追问

前端 paperRelevance 卡片增加"限定这篇"按钮，点击后将 paperId 加入 selectedPaperIds，后续追问自动改为单篇 FULL_TEXT_PARSED 或多篇 HYBRID_RAG。

#### 5. Query Rewrite 结果缓存

同一问题短时间内重复搜索时，可缓存 QueryRewriteResult（TTL 5 分钟），减少 LLM 调用。

#### 6. 全库检索评测用例

在 `docs/evaluation/cases.md` 增加全库发现用例：

```text
"有哪些论文用了注意力机制？"   → 期望命中所有 attention-based 论文
"有没有论文做多模态情感分析？"   → 期望命中 multi-modal sentiment 论文
"哪篇论文的风速预测效果最好？"   → 期望返回实验对比充分且指标最高的论文
```

## 12. 已落地改进记录：多粒度向量索引（profile/summary 入库 Qdrant）

更新时间：2026-07-10

### 改进背景

此前四条检索路径中，profile 和 summary 只在 HYBRID_RAG 中按规则从 MySQL 读取，LIBRARY_DISCOVERY 模式下 Qdrant 只搜 raw chunk。但 raw chunk 粒度太细——一个 chunk 可能是"学习率设为 0.001"这样的实验细节，和用户"有没有论文用 Transformer 做风速预测"这种宏观问题语义匹配度低。

paper_profile 是一篇论文的"名片"，paper_section_summary 是一个章节的"摘要"，它们的语义粒度更接近用户问题。但此前它们存在于 MySQL 中，不能被向量检索。

### 改进内容

**1. QdrantService 新增 profile/summary 写入能力**

画像生成/更新时，自动将 `paper_profile.profile_text` 和 `paper_section_summary.summary` 写入 Qdrant `paper_chunks` collection：
- `contentType = "PAPER_PROFILE"`，point ID = `-paperId`
- `contentType = "SECTION_SUMMARY"`，point ID = `-(summaryId + 10_000_000)`
- 负数 ID 确保和正数 chunk ID 不冲突
- Qdrant 写入失败不影响画像生成主流程（try-catch + log.warn）

**2. 检索路径分化**

```
VECTOR_RAG / HYBRID_RAG 的 raw chunk 检索
  → searchSimilarChunksRaw(question, topK, paperIds, "RAW_CHUNK")
  → Qdrant 只返回 contentType=RAW_CHUNK 的 points
  → 不受 profile/summary 数据影响

LIBRARY_DISCOVERY
  → searchSimilarChunksRaw(question, topK, paperIds, null)
  → Qdrant 返回 RAW_CHUNK + PAPER_PROFILE + SECTION_SUMMARY
  → 三粒度混合 RRF 融合
```

**3. RagSource 构造区分来源**

Qdrant 返回结果按 payload.contentType 分别解析：
- `RAW_CHUNK`：原有逻辑，查 MySQL chunk + paper
- `PAPER_PROFILE`：从 payload 读 profileId/paperId/profileVersion，content 用 payload text
- `SECTION_SUMMARY`：从 payload 读 sectionSummaryId/sectionId/sectionType/sectionTitle，content 用 payload text

**4. 不影响已有路径**

- `FULL_TEXT_PARSED`：仍从 MySQL 读全文，不走 Qdrant
- `HYBRID_RAG`：profile/summary 仍从 MySQL 规则读取，raw chunk 检索仍只搜 RAW_CHUNK
- `VECTOR_RAG`：Qdrant 检索加了 contentType=RAW_CHUNK filter，因为之前也只有 RAW_CHUNK，行为等价

### 相比上一版本的实际提升

```text
LIBRARY_DISCOVERY 检索范围：raw chunk only → raw_chunk + paper_profile + section_summary 三粒度
论文级语义匹配：只能靠细粒度 chunk → 画像级别的全文语义可以直接匹配用户意图
开销隔离：指定论文时 profile/summary 不参与检索，避免多余 Qdrant 扫描
向量维护：画像生成/更新时自动同步写入 Qdrant，无需额外接口
```

### 当前边界

```text
1. 已存在的 profile/summary 不会自动回填 Qdrant——需要重新生成画像才能写入
2. profile 删除时 Qdrant 向量靠 paperId filter 自动清理（与 chunk 共用 deletePaperPoints）
3. profile point 只包含了 profileText 的前 300 字符作为 payload text 预览
```

## 13. 已落地改进记录：BM25 关键词检索融入 RRF 混合检索

更新时间：2026-07-10

### 改进背景

此前所有检索路由都走 dense 向量（embedding → Qdrant cosine），纯向量检索适合语义匹配但有两个盲区：
1. **精确术语匹配**：论文中大量精确术语（模型名 BVMD-PAM-TWGCNet、数据集名 WindSpeed-9、指标名 RMSE/MAE），dense 向量对这些术语的区分度不如关键词直接命中。
2. **缩写匹配**：用户问 "GNN"，论文写 "Graph Neural Network"，dense embedding 可能匹配，但用户问 "BVMD" 这种自造缩写时，BM25 的精确词匹配更可靠。

### 实现方案

**不用 Qdrant 稀疏向量，不用 ES，不引入外部依赖。**

用内存 BM25（纯 Java 实现），在 Spring 启动时从 MySQL 加载可用 chunk 构建倒排索引：

```text
启动 → MySQL paper_chunk 全量扫描
     → 分词（拆分+小写+去停用词+长度≥2）
     → 统计 term→chunk 倒排表 + 文档长度
     → 888 chunk 约 100~300ms
```

**BM25 公式：**
```
score(d, q) = Σ IDF(qi) × TF_boost(qi, d)

IDF = log((N - df + 0.5) / (df + 0.5) + 1)

TF_boost = (k1 + 1) × tf / (k1 × (1 - b + b × |d| / avgdl) + tf)

k1 = 1.5（词频饱和度），b = 0.75（长度归一化）
```

**检索路由变化：**

```
VECTOR_RAG（之前）:
  route_1: original query  → dense  → Qdrant RAW_CHUNK
  route_2: rewritten query → dense  → Qdrant RAW_CHUNK
  → score merge → topK

VECTOR_RAG（现在）:
  route_1: original query  → dense  → Qdrant RAW_CHUNK
  route_2: rewritten query → dense  → Qdrant RAW_CHUNK
  route_3: rewritten query → BM25   → 内存倒排      ← NEW
  → RRF 三路融合 → topK

LIBRARY_DISCOVERY（之前）:
  route_1~3: 改写 query  → dense  → Qdrant 全粒度

LIBRARY_DISCOVERY（现在）:
  route_1~3: 改写 query  → dense  → Qdrant 全粒度
  route_4: keywordQuery  → BM25   → 内存倒排         ← NEW
  → RRF 四路融合 → topK
```

**VECTOR_RAG 也从 score merge 升级为 RRF**——这是让 BM25 参与融合的必要前提（dense cosine 分数和 BM25 分数不在同一量纲，无法直接合并）。两路 dense 时 RRF 效果与 score merge 高度一致，加入 BM25 后排名更公平。

### 关键设计决策

1. **BM25 只索引 RAW_CHUNK**：不索引 profile/summary，因为 BM25 的价值在于精确术语匹配，profile 是 LLM 生成的中文摘要，不包含论文原文中的精确术语。
2. **BM25 用改写后的英文 query**：VECTOR_RAG 用 `retrievalQuestion`（英文改写），LIBRARY_DISCOVERY 用 `keywordQuery`（空格分隔关键词），保证 BM25 查询词与论文英文原文一致。
3. **无额外基础设施**：纯内存倒排索引，888 条 chunk 内存占用约 2~3MB，检索延迟 <5ms。
4. **启动时自动构建**：@PostConstruct 从 MySQL 加载，无需迁移脚本或手动初始化。

### 验证通过

- 后端 ./mvnw test 通过（119 个测试，0 失败）
- Bm25IndexServiceTest（9 个测试）：覆盖索引构建、精确/多词匹配、空查询、topK 限制、参考文献排除

### 新增文件

- `Bm25IndexService.java`（内存 BM25 索引，启动时构建）
- `Bm25IndexServiceTest.java`（9 个测试）

### 改造文件

- `RagRetrievalServiceImpl.java`（新增 BM25 路由 + VECTOR_RAG 升级为 RRF）
- `RagRetrievalServiceImplTest.java`（适配新构造参数）

## 14. 核心设计原则

```text
能读全文时，不要只看片段。
必须检索时，不要只靠相似度。
需要比较时，不要只按全局 topK，要按论文均衡组织证据。
需要研究洞察时，不要只用原文 chunk，要引入文献画像和章节摘要。
参考文献默认排除，但不要物理删除。
大模型生成的摘要可以作为索引，但不能替代原文证据。
长任务不要让前端同步等待，应使用异步任务和可查询状态。
```
