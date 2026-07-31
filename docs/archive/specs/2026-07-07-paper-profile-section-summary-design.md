# 文献画像与章节摘要基础层设计

更新时间：2026-07-07

## 1. 背景

当前系统已经完成论文结构化分块和单篇论文 `FULL_TEXT_PARSED` 上下文策略：

```text
PDF 解析
→ paper_section 章节层
→ paper_chunk 结构化 chunk
→ 单篇预算内 FULL_TEXT_PARSED
→ 多篇或超预算时回退 VECTOR_RAG
```

这已经改善了单篇论文宏观问题的回答完整度，但多篇比较、超预算长论文问答、综述式总结和 Research Idea 提炼仍缺少一个可复用的“论文级理解资产层”。如果下一步直接做 `HYBRID_RAG`，系统仍主要依赖每次问题临时向量召回，长期复用价值有限。

本阶段先沉淀两类稳定资产：

```text
章节摘要 paper_section_summary
整篇文献画像 paper_profile
```

后续 `HYBRID_RAG`、多篇比较、Dashboard 展示、Research Idea 生成和轻量评测都可以复用这些资产。

## 2. 本阶段目标

本阶段名称：文献画像与章节摘要基础层。

目标：

```text
已解析论文
→ 按章节聚合有效正文 chunk
→ 生成章节摘要
→ 汇总章节摘要生成整篇论文画像
→ 保存到 MySQL
→ 提供后端接口供后续 HYBRID_RAG 复用
```

核心原则：

```text
先沉淀可复用理解资产，再做更复杂的混合上下文组织。
```

## 3. 本阶段范围

### 3.1 本阶段做

```text
1. 新增 paper_section_summary 数据模型，用于保存章节摘要和关键点。
2. 新增 paper_profile 数据模型，用于保存整篇论文画像。
3. 新增 Mapper / Entity / DTO / Service / Controller 基础能力。
4. 新增 POST /api/papers/{id}/profile，手动触发生成或更新文献画像。
5. 新增 GET /api/papers/{id}/profile，查看已生成文献画像。
6. 生成流程默认排除 REFERENCES、BACK_MATTER、isReference=true、isNoise=true 的 chunk。
7. 第一版复用当前 provider-neutral LlmService，不绑定 DeepSeek/Qwen/Zhipu 具体实现。
8. 重复生成默认覆盖更新同一版本画像，避免重复数据堆积。
9. 增加单元测试覆盖核心流程。
10. 每个实质阶段更新 docs/status/current.md，保证关机后可衔接。
```

### 3.2 本阶段不做

```text
1. 不直接重写 /api/rag/chat 主链路。
2. 不直接实现完整 HYBRID_RAG。
3. 不把 paper_profile / section_summary 写入 Qdrant。
4. 不做异步任务队列。
5. 不做前端 UI 大改。
6. 不自动在 PDF 解析后生成画像，先保留手动触发。
7. 不做复杂 LLM JSON 强约束解析，第一版用清晰 prompt + 后端容错解析。
```

这些内容留给后续阶段：

```text
paper_profile / section_summary 入向量库
→ HYBRID_RAG 使用画像 + 摘要 + chunk
→ 多篇比较上下文组织
→ 前端展示文献画像
→ 解析后自动生成画像
```

## 4. 数据模型设计

### 4.1 新增 paper_section_summary

用于保存每篇论文每个章节的摘要。

建议字段：

```text
id                     主键
paper_id               论文 ID
section_id             章节 ID
section_type           标准章节类型，例如 METHOD / EXPERIMENT
section_title          原始章节标题
summary                章节摘要
key_points             章节关键点，第一版用文本保存，可用换行或 JSON 字符串
source_chunk_ids       参与摘要的 chunk ID 列表，第一版用字符串保存
source_token_count     参与摘要的估算 token 数
summary_version        摘要策略版本，例如 section-summary-v1
create_time            创建时间
update_time            更新时间
```

唯一性建议：

```text
paper_id + section_id + summary_version 唯一
```

重复生成时按唯一键更新。

### 4.2 新增 paper_profile

用于保存整篇论文画像。

建议字段：

```text
id                         主键
paper_id                   论文 ID
title                      论文标题冗余
research_problem           研究问题
method_summary             方法概述
experiment_summary         实验与评估概述
key_contributions          主要贡献
limitations                局限性
keywords                   关键词，第一版用文本保存
profile_text               面向 RAG 的完整画像文本
source_section_summary_ids 参与生成的章节摘要 ID 列表
profile_version            画像策略版本，例如 paper-profile-v1
create_time                创建时间
update_time                更新时间
```

唯一性建议：

```text
paper_id + profile_version 唯一
```

重复生成时更新旧画像。

## 5. 后端接口设计

### 5.1 生成 / 更新文献画像

```http
POST /api/papers/{id}/profile
```

职责：

```text
1. 校验论文存在。
2. 校验 parse_status = COMPLETED。
3. 读取 paper_section 和 paper_chunk。
4. 过滤参考文献和噪声 chunk。
5. 按章节生成 section summary。
6. 根据章节摘要生成 paper profile。
7. 保存或更新 MySQL。
8. 返回生成后的 profile 和章节摘要数量。
```

第一版不要求请求体。后续如果需要，可以扩展：

```json
{
  "forceRegenerate": true,
  "profileVersion": "paper-profile-v1"
}
```

### 5.2 查询文献画像

```http
GET /api/papers/{id}/profile
```

返回：

```text
paperProfile
sectionSummaries
```

如果尚未生成，返回 null 或业务提示，由前端后续决定是否展示“尚未生成”。

## 6. 生成流程设计

### 6.1 总流程

```text
PaperProfileService.generateProfile(paperId)
→ 查询 PaperReference
→ 校验 parse_status
→ 查询 PaperSection
→ 查询 PaperChunk
→ 过滤无效 chunk
→ group by sectionId
→ 对重要章节逐个生成 PaperSectionSummary
→ 汇总章节摘要生成 PaperProfile
→ upsert section summaries
→ upsert paper profile
→ 返回 PaperProfileResult
```

### 6.2 有效 chunk 过滤

默认排除：

```text
isReference = true
isNoise = true
sectionType = REFERENCES
sectionType = BACK_MATTER
content 为空
```

默认保留：

```text
ABSTRACT
INTRODUCTION
RELATED_WORK
METHOD
EXPERIMENT
RESULT
DISCUSSION
CONCLUSION
UNKNOWN
```

`UNKNOWN` 第一版保留，因为部分 PDF 章节识别可能不完整。

### 6.3 章节摘要 prompt

章节摘要 prompt 应要求输出稳定、短小、可复用的内容：

```text
你是论文阅读助手。请根据下面某篇论文的一个章节内容，生成章节摘要。

要求：
1. 用中文。
2. 不要编造章节中没有的信息。
3. 摘要控制在 150~300 字。
4. 提取 3~6 条关键点。
5. 如果该章节内容不足以总结，请说明信息不足。

论文标题：...
章节类型：METHOD
章节标题：...
章节正文：...
```

第一版可以让 LLM 输出文本格式：

```text
摘要：...
关键点：
- ...
- ...
```

后端先保存原始文本或进行轻量解析，不把 JSON 格式作为硬依赖。

### 6.4 文献画像 prompt

文献画像 prompt 使用章节摘要作为输入：

```text
你是论文/文献 AI 研究助手。请根据下面的章节摘要，生成整篇论文画像。

需要覆盖：
1. 研究问题
2. 方法概述
3. 实验与评估
4. 主要贡献
5. 局限性
6. 关键词
7. 一段适合 RAG 使用的完整画像文本

要求：
- 用中文。
- 不要使用 References 或后置声明作为论文正文结论。
- 如果某项信息不足，请写“信息不足”，不要编造。
```

第一版使用规则分段解析，例如识别：

```text
研究问题：
方法概述：
实验与评估：
主要贡献：
局限性：
关键词：
完整画像：
```

如果解析失败，至少保存 `profileText`，并把结构化字段填入“信息不足”或截取结果。

## 7. 服务边界设计

新增服务建议：

```text
PaperProfileService
- generateProfile(Long paperId): PaperProfileResult
- getProfile(Long paperId): PaperProfileResult
```

内部可拆分私有方法：

```text
buildSectionInput(...)
generateSectionSummary(...)
generatePaperProfile(...)
upsertSectionSummary(...)
upsertPaperProfile(...)
```

本阶段先不抽独立异步任务，也不引入复杂 pipeline 类，避免过早抽象。

## 8. DTO 设计

建议新增：

```text
PaperSectionSummaryResponse
- id
- paperId
- sectionId
- sectionType
- sectionTitle
- summary
- keyPoints
- sourceTokenCount
- summaryVersion

PaperProfileResponse
- id
- paperId
- title
- researchProblem
- methodSummary
- experimentSummary
- keyContributions
- limitations
- keywords
- profileText
- profileVersion
- sectionSummaries

PaperProfileResult
- profile
- sectionSummaries
```

Controller 继续使用现有统一 `Result` 包装。

## 9. 错误处理

```text
论文不存在：抛出业务异常，提示文献不存在。
论文未解析：提示请先解析 PDF。
无有效正文 chunk：提示当前论文没有可用于生成画像的正文内容。
LLM 调用失败：不写入不完整画像，返回失败原因。
单个章节摘要失败：第一版直接让整个生成失败，避免保存半成品。
重复调用：更新同版本记录，不插入重复画像。
```

后续如果加入异步任务，可改为“部分成功 + 状态字段”。

## 10. 版本设计

第一版固定版本：

```text
section-summary-v1
paper-profile-v1
```

版本号写入数据库，用于后续升级摘要策略时重新生成而不混淆旧结果。

如果后续 prompt 或字段变化明显，可以新增：

```text
section-summary-v2
paper-profile-v2
```

## 11. 测试设计

单元测试重点：

```text
1. 未解析论文不能生成 profile。
2. 文献不存在时返回明确错误。
3. REFERENCES / BACK_MATTER / isReference / isNoise chunk 不参与生成。
4. 可按 sectionId 聚合 chunk 并调用 LlmService 生成章节摘要。
5. 可根据章节摘要生成 paper profile。
6. 重复生成同版本时更新旧记录而不是重复插入。
7. GET profile 能返回 profile 和 section summaries。
```

接口验证建议使用 Apifox：

```http
POST /api/papers/7/profile
GET /api/papers/7/profile
```

验证点：

```text
返回 code = 200
paperId 正确
sectionSummaries 数量 > 0
profileVersion = paper-profile-v1
summaryVersion = section-summary-v1
研究问题 / 方法概述 / 主要贡献等字段有内容或明确“信息不足”
```

## 12. 与后续 HYBRID_RAG 的衔接

本阶段完成后，后续 HYBRID_RAG 可以按以下方式使用：

```text
多篇问题：
→ 读取每篇 paper_profile 作为论文级上下文
→ 根据问题召回相关 section_summary
→ 再补充少量 topK raw chunks
→ 组合成 HYBRID_RAG prompt
```

未来 Qdrant 可增加：

```text
contentType = PAPER_PROFILE
contentType = SECTION_SUMMARY
```

这样检索层可以同时召回：

```text
论文画像
章节摘要
原始 chunk
```

形成多粒度 RAG。

## 13. 进度记录要求

本阶段每个实质阶段都要更新 `docs/status/current.md`：

```text
阶段 1：数据库和实体基础
阶段 2：章节摘要生成服务
阶段 3：文献画像生成和接口
阶段 4：测试、接口验证、后续 HYBRID_RAG 衔接记录
```

记录内容只包含项目实质进展、关键验证结果、当前状态和下一步任务，不记录临时命令细节。