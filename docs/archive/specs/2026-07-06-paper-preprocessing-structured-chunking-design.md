# 论文 PDF 预处理与结构化分块设计

更新时间：2026-07-06

## 1. 背景

当前 RAG 链路已经完成“PDF 解析 → chunk 入库 → embedding → Qdrant 检索 → LLM 回答”的主流程，但召回和回答质量仍受原始 chunk 质量限制。

论文不是普通长文本，而是半结构化研究文档，通常包含摘要、引言、相关工作、方法、实验、结果、讨论、结论、参考文献等章节。如果继续使用粗粒度文本切分，后续无论做单篇全文问答、Hybrid RAG、rerank，都会受到噪声文本、参考文献误召回和章节语义缺失的影响。

本阶段目标是先把 PDF 解析出的普通文本升级为带论文结构的半结构化数据，为后续单篇论文精读、父子召回、多篇比较和文献画像打基础。

## 2. 本阶段目标

本阶段名称：论文 PDF 预处理与结构化分块优化。

目标：

```text
PDF 原文
→ PDFBox 解析文本
→ 文本清洗
→ 章节识别
→ References 标记
→ 按章节/段落生成 chunk
→ MySQL 保存真实内容
→ Qdrant 保存检索索引文本和结构化 payload
→ RAG 默认过滤参考文献 chunk
```

本阶段优先处理新解析论文，不强制迁移旧论文。旧论文后续通过“重新解析 / 重新向量化”入口处理。

## 3. 设计原则

1. MySQL 是事实主库，保存真实论文结构和真实 chunk 内容。
2. Qdrant 是语义检索索引，保存 embedding、chunkId 和检索所需 payload。
3. content 保存真实原文，indexText 用于 embedding 检索。
4. 先规则化识别章节和 References，不引入复杂外部解析器。
5. 先建立父子结构基础，不在第一阶段实现完整父子召回。
6. 参考文献默认排除，但不物理删除。
7. 每次 RAG 优化都需要可量化评测，避免只凭感觉判断效果。

## 4. 数据模型设计

### 4.1 新增 paper_section

新增 `paper_section` 表，表示论文的章节父级结构。

建议字段：

```text
id                    章节 ID
paper_id              所属论文 ID
section_title          章节原始标题，例如 Proposed Method
section_type           标准章节类型，例如 METHOD
section_index          章节顺序
page_start             起始页，可先为空
page_end               结束页，可先为空
content_preview        章节内容预览，便于调试和前端展示
create_time            创建时间
```

第一阶段至少需要：

```text
paper_id
section_title
section_type
section_index
content_preview
```

### 4.2 增强 paper_chunk

在现有 `paper_chunk` 上增加结构化字段。

建议字段：

```text
section_id              所属章节 ID
section_title           冗余章节标题，便于展示和调试
section_type            标准章节类型
page_start              chunk 起始页，可先为空
page_end                chunk 结束页，可先为空
token_count             估算 token 数
char_count              字符数
is_reference            是否参考文献 chunk
is_noise                是否噪声 chunk
quality_score           chunk 质量分，可先默认 1.0
index_text              用于 embedding 的检索文本
chunk_strategy_version  分块策略版本，例如 paper-structure-v1
```

第一阶段优先实现：

```text
section_id
section_title
section_type
token_count
char_count
is_reference
is_noise
quality_score
index_text
chunk_strategy_version
```

## 5. 章节类型设计

标准 `sectionType`：

```text
TITLE
ABSTRACT
INTRODUCTION
RELATED_WORK
METHOD
EXPERIMENT
RESULT
DISCUSSION
CONCLUSION
REFERENCES
APPENDIX
BACK_MATTER
UNKNOWN
```

常见标题映射：

```text
Abstract / 摘要                         → ABSTRACT
Introduction / 引言                     → INTRODUCTION
Related Work / Literature Review        → RELATED_WORK
Method / Methodology / Approach         → METHOD
Proposed Method / Framework             → METHOD
Experiments / Evaluation                → EXPERIMENT
Results / Analysis                      → RESULT
Discussion                              → DISCUSSION
Conclusion / Conclusions                → CONCLUSION
References / Bibliography / 参考文献     → REFERENCES
Appendix / Supplementary                → APPENDIX
Acknowledgements / Author Contributions / Conflict of Interest / Data Availability / Funding → BACK_MATTER
```

章节识别第一阶段采用规则方式：

1. 按行扫描 PDFBox 解析文本。
2. 对短行、编号行和明显标题行进行判断。
3. 支持 `1 Introduction`、`2. Related Work`、`III. Methodology` 等常见编号格式。
4. 命中 References 后，后续内容默认归入 `REFERENCES`，`isReference = true`。
5. 未识别章节时使用 `UNKNOWN`。

## 6. 文本清洗与分块逻辑

### 6.1 文本清洗

第一阶段做轻量清洗，不追求解决所有 PDF 排版问题。

处理内容：

```text
去除连续空白
合并异常换行
修复英文断词换行，例如 predic-\ntion → prediction
过滤明显页码行
过滤过短重复行
保留段落边界
```

暂不处理：

```text
复杂双栏重排
表格结构恢复
公式结构恢复
图片内容理解
```

### 6.2 分块顺序

建议流程：

```text
PDFBox 原始文本
→ 清洗为段落列表
→ 识别章节边界
→ 生成 paper_section
→ 章节内按段落合并为 chunk
→ 超长 chunk 再按 token/字符长度切分
→ 每个 chunk 继承 sectionId / sectionType
```

### 6.3 chunk 大小

第一阶段建议使用保守配置：

```text
目标 chunk 字符数：1200 ~ 1800
最大 chunk 字符数：2200
overlap 字符数：150 ~ 250
过短段落合并阈值：300
```

后续可以通过配置项调整，并写入 `chunkStrategyVersion`。

## 7. indexText 设计

`content` 保存真实原文，不做过度改写。

`indexText` 用于 embedding，可以包含结构信息：

```text
Paper Title: {paperTitle}
Section Type: {sectionType}
Section Title: {sectionTitle}
Content:
{cleanedChunkContent}
```

这样用户问“方法”“实验”“结论”时，embedding 更容易命中对应章节。

第一阶段不使用大模型为 chunk 生成 hypothetical questions，避免引入成本、异步任务和质量不可控问题。后续如果需要，可以增加 `index_text_type` 或单独的多向量索引。

## 8. Qdrant payload 设计

写入 Qdrant 时，payload 至少包含：

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
  "contentType": "RAW_CHUNK",
  "chunkStrategyVersion": "paper-structure-v1"
}
```

后续如果增加章节摘要、文献画像或 claim card，可以通过 `contentType` 扩展：

```text
RAW_CHUNK
SECTION_SUMMARY
PAPER_PROFILE
CLAIM_CARD
```

## 9. RAG 检索第一阶段调整

普通 RAG 检索默认排除：

```text
isReference = true
isNoise = true
```

如果问题明确询问参考文献，例如：

```text
这篇论文引用了哪些关键工作？
参考文献中有哪些 Transformer 相关论文？
```

后续可以允许开启 References 检索。本阶段先默认过滤，不做复杂问题类型判断。

RAG sources 返回时建议携带：

```text
sectionType
sectionTitle
isReference
chunkStrategyVersion
```

便于前端和调试时判断召回来源。

## 10. RAG 效果评测机制

为了量化每次 RAG 优化是否有效，本阶段同步建立轻量评测机制。

### 10.1 评测目标

每次改进前后都能回答：

```text
召回是否更准？
参考文献误召回是否减少？
回答是否覆盖更多关键点？
sources 是否来自正确论文和正确章节？
单篇论文问题是否比之前更完整？
```

### 10.2 建议评测集结构

先建立小规模人工评测集，不一开始做复杂自动评分平台。

每条评测用例包含：

```text
caseId                  用例 ID
name                    用例名称
question                用户问题
paperIds                目标论文范围
questionType            METHOD / EXPERIMENT / SUMMARY / LIMITATION / FACT / REFERENCES
expectedKeyPoints       期望回答覆盖的关键点
expectedSectionTypes    期望 sources 来自哪些章节
shouldExcludeReferences 是否应排除参考文献
notes                   人工备注
```

示例：

```json
{
  "caseId": "single-paper-method-001",
  "question": "这篇论文的核心方法是什么？",
  "paperIds": [1],
  "questionType": "METHOD",
  "expectedSectionTypes": ["METHOD"],
  "shouldExcludeReferences": true,
  "expectedKeyPoints": [
    "说明模型或方法框架",
    "说明关键模块",
    "说明方法解决的问题"
  ]
}
```

### 10.3 评测指标

第一阶段建议统计这些指标：

#### 检索指标

```text
referenceHitRate
召回 sources 中 isReference=true 的比例，越低越好。

expectedSectionHitRate
召回 sources 命中期望 sectionType 的比例，越高越好。

paperHitRate
召回 sources 来自目标 paperIds 的比例，越高越好。

topKUsefulRate
人工判断 topK sources 中有用片段比例，越高越好。
```

#### 回答指标

```text
keyPointCoverage
回答覆盖 expectedKeyPoints 的比例，可先人工评分。

citationSupportRate
回答中的主要结论是否能被 sources 支撑，可先人工评分。

answerCompletenessScore
人工 1~5 分，评价回答完整性。

answerNoiseScore
人工 1~5 分，评价是否混入参考文献或无关内容，越低越好。
```

### 10.4 第一阶段落地方式

先做最小可用评测：

1. 在 `docs/evaluation/cases.md` 记录 5~10 个固定问题。
2. 每次优化前，记录 baseline 的 sources 和回答表现。
3. 每次优化后，用同一批问题重新测试。
4. 用表格记录 referenceHitRate、expectedSectionHitRate、keyPointCoverage、人工评分。

后续再考虑把评测集做成数据库表或测试脚本。

### 10.5 建议第一批评测问题类型

```text
单篇方法问题：这篇论文的核心方法是什么？
单篇实验问题：这篇论文做了哪些实验，结果如何？
单篇贡献问题：这篇论文的主要创新点是什么？
单篇局限问题：这篇论文有什么不足？
参考文献干扰测试：这篇论文的方法是否基于 Transformer？
多篇比较问题：这几篇论文的方法路线有什么区别？
```

第一阶段重点观察：

```text
References 是否还会被普通问题召回。
METHOD / EXPERIMENT / RESULT 是否更容易命中。
sources 是否能显示章节类型。
回答是否更少跑偏。
```

## 11. 分阶段实施建议

### 阶段 1：结构化分块基础

```text
新增 paper_section
增强 paper_chunk 字段
实现 SectionType 枚举和章节识别规则
实现文本清洗和章节内分块
新解析论文写入 section/chunk 结构
```

### 阶段 2：向量化和检索接入

```text
embedding 使用 indexText
Qdrant payload 写入 sectionType / isReference / sectionId
RAG 默认过滤 isReference / isNoise
sources 返回章节信息
```

### 阶段 3：评测机制最小闭环

```text
建立 rag-evaluation-cases.md
固定 5~10 个评测问题
记录 baseline 与优化后结果
形成 referenceHitRate、expectedSectionHitRate、keyPointCoverage 等指标
```

### 阶段 4：后续增强

```text
父子召回：命中 chunk 后扩展同 section 相邻 chunk
单篇 FULL_TEXT_PARSED：单篇论文在 token 预算内直接按章节组织上下文
基础 rerank：根据 sectionType、isReference、qualityScore 调整分数
文献画像和章节摘要：用于多篇比较和 Research Idea 生成
```

## 12. 验证方式

后端单元测试：

```text
章节标题识别测试
References 标记测试
文本清洗测试
章节内分块测试
chunk 字段写入测试
Qdrant payload 构造测试
RAG 默认过滤 References 测试
```

人工验证：

```text
上传或重新解析一篇论文
检查 paper_section 是否生成
检查 paper_chunk 是否带 sectionType / isReference
向量化后检查 Qdrant payload
用固定评测问题测试 sources 和回答
记录优化前后指标变化
```

## 13. 暂不纳入本阶段的内容

```text
不引入 GROBID / MinerU / Nougat 等外部解析器
不实现复杂双栏重排
不实现图表/公式理解
不实现 LLM 生成文献画像
不实现章节摘要
不实现 claim cards
不实现完整父子召回
不实现直接 PDF LLM 阅读
不强制迁移旧论文
```

这些内容在结构化分块稳定后再逐步推进。
