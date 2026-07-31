# PDF 数据清洗、章节识别与段落分块质量升级设计

日期：2026-07-09

## 1. 背景

当前系统已经支持 PDF 解析、结构化分块、向量化、章节摘要、文献画像和 HYBRID_RAG。但实际检查本地数据库后发现：

1. `paper_section` 中大量 sectionTitle 不是论文真实章节标题，而是期刊名、正文半句、表格标题、模型名、页面元信息等。
2. `paper_section_summary.summary` 在当前 3 篇论文中全部为“信息不足”，导致 HYBRID_RAG 的 `hybrid_summary` 来源几乎不可用。
3. `paper_profile` 能正常生成，主要是因为整篇画像 prompt 已加入 raw chunk 原文证据兜底，而不是因为章节摘要质量已经可用。
4. 当前 `PaperTextCleaner` 清洗较轻，只处理换行、断词、页码和空白，未充分处理页眉页脚、期刊信息、作者单位、基金声明、版权、DOI、表格和图注。
5. 当前 `PaperSectionDetector` 的标题判断较宽松，短英文 Title Case 行容易被识别为章节标题。
6. 当前 `StructuredChunkingService` 已经按章节内段落和长度分块，但如果章节识别错误，后续分块仍会继承错误章节归属。

本阶段目标是升级 PDF 入库前的数据质量，让后续 chunk、section_summary、paper_profile、HYBRID_RAG 都建立在更干净、更稳定的结构化数据之上。

## 2. 目标

本阶段实现以下目标：

1. 提高 PDF 文本清洗质量，过滤或标记页眉页脚、版权、DOI、期刊信息、作者单位、基金声明等低价值内容。
2. 改进章节标题识别，减少把正文句子、表格行、模型名、期刊名误判为 section 的情况。
3. 支持小章节和段落优先分块：优先按真实章节/小章节组织，再按段落合并，最后用长度兜底。
4. 初步识别表格文本和图注：不做复杂表格结构抽取或图像理解，但避免它们污染章节标题和普通正文摘要。
5. 改进章节摘要生成：只对高质量正文 section 生成摘要，跳过低质量、过短、后置声明、参考文献和表格密集 section。
6. 增强章节摘要解析兜底：LLM 输出有有效内容但格式不完全匹配时，不应直接写入“信息不足”。
7. 提供旧数据重建流程，让当前已有论文能用新规则重新生成 section、chunk、summary、profile 和向量。
8. 每个实质阶段完成后继续更新 `docs/status/current.md`，保留项目进度和验证结果。

## 3. 非目标

本阶段不做以下事情：

1. 不引入 OCR 或多模态模型识别图片内容。
2. 不做完整表格结构抽取，不引入 Camelot/Tabula 等额外工具。
3. 不改变当前 MySQL + Qdrant 的总体架构。
4. 不重做整个 RAG 策略，只提升 RAG 输入数据质量。
5. 不自动删除用户历史对话和 Research Idea。
6. 不默认批量重建所有论文，先支持指定论文重建，避免误删和耗时不可控。

## 4. 当前分块方式说明

当前 `StructuredChunkingService` 的分块规则是：

```text
PDF 原文
→ PaperTextCleaner.clean()
→ PaperSectionDetector.detect()
→ 对每个 DetectedSection 执行 splitSectionContent()
```

当前分块参数：

```text
TARGET_CHARS = 1600
MAX_CHARS = 2200
OVERLAP_CHARS = 200
```

当前策略已经是“章节内按段落和长度分块”，不是纯固定窗口切分。但问题在于前置章节识别不稳，导致后续 chunk 被归属到错误 section 下。

本阶段建议调整为：

```text
目标 chunk 长度：800~1200 字符
最大 chunk 长度：1600 字符
重叠长度：100~150 字符
```

分块优先级：

```text
真实大章节
→ 小章节
→ 段落
→ 长度兜底
```

## 5. 数据清洗设计

### 5.1 直接过滤内容

`PaperTextCleaner` 增强过滤规则，直接过滤明显低价值行：

```text
纯页码
DOI 行
copyright / © 行
journal homepage 行
Available online 行
Received / Revised / Accepted 日期行
邮箱行
Corresponding author 行
过短且重复出现的页眉/页脚
```

### 5.2 标记低价值内容

以下内容不一定直接删除，但应该标记为低价值，默认不参与 RAG 普通检索和章节摘要：

```text
Funding
Acknowledgements
Conflict of Interest
Data Availability
Author Contributions
Ethics Statement
CRediT authorship contribution statement
```

这些内容可归为：

```text
BACK_MATTER
isNoise = true 或低 qualityScore
```

### 5.3 作者和单位处理

论文首页常见作者单位、邮箱、机构、脚注不应进入正文 chunk。短期策略：

1. 在 Abstract/Introduction 之前的 front matter 中，过滤邮箱、机构脚注和通讯作者行。
2. 保留论文标题，但不把作者单位作为正文 chunk。
3. 作者、年份、期刊等元数据仍由 `paper_reference` 管理，不混入 RAG 正文上下文。

## 6. 章节识别设计

### 6.1 标题候选打分

将当前“像标题就切 section”的规则改为“标题候选打分”。候选标题需要满足多个条件：

```text
行长度合理
不是完整句子
不是 DOI/期刊/日期/版权行
不是表格行
不是数值密集行
不是单个模型名
不是参考文献条目
具有章节关键词或编号结构
```

优先识别：

```text
Abstract
1 Introduction
2 Related Work
3 Methodology
3.1 Feature Refinement
3.2 Model Architecture
4 Experiments
5 Results and Discussion
6 Conclusion
References
```

### 6.2 标准 sectionType

保留现有 sectionType 思路：

```text
ABSTRACT
INTRODUCTION
RELATED_WORK
METHOD
EXPERIMENT
RESULT
DISCUSSION
CONCLUSION
REFERENCES
BACK_MATTER
APPENDIX
UNKNOWN
```

但改进规则：

1. 标题规则优先。
2. 内容关键词只用于辅助，不应把普通正文半句强行提升为标题。
3. 表格标题和图注不作为 section 标题。
4. 无法确认的内容归入当前最近的有效 section，而不是新建 UNKNOWN section。

### 6.3 小章节处理

支持 `3.1`、`3.2`、`4.1` 等小章节。小章节可以作为 section，但必须满足：

```text
有明确编号结构
标题长度合理
后续有足够正文内容
不是表格序号或列表项
```

小章节的 sectionType 可以继承父章节语义，例如：

```text
3 Method
3.1 Feature Refinement -> METHOD
3.2 Proposed Model -> METHOD
4 Experiments
4.1 Datasets -> EXPERIMENT
4.2 Metrics -> EXPERIMENT
```

## 7. 表格和图注处理设计

### 7.1 表格文本

短期不做复杂表格结构抽取，但需要识别表格密集文本。判断规则包括：

```text
包含 Table / Tab. 开头
包含大量指标词：MAE, RMSE, MAPE, Accuracy, F1, BLEU, ROUGE
数字比例高
多列模型名 + 数值
行内分隔符或连续数字较多
```

处理方式：

1. 不作为章节标题。
2. chunkType 标记为 `table`。
3. 可以作为 raw evidence 检索。
4. 默认不参与普通章节摘要，或后续单独生成“表格说明”。

### 7.2 图注文本

识别：

```text
Fig. 1
Figure 2
图 1
```

处理方式：

1. 不作为章节标题。
2. chunkType 标记为 `figure_caption`。
3. 保留为可检索证据。
4. 不进行图像 OCR 或视觉理解。

## 8. 章节摘要质量设计

### 8.1 只对高质量 section 生成摘要

跳过以下 section：

```text
REFERENCES
BACK_MATTER
APPENDIX 中明显低价值内容
内容过短 section
表格密集 section
纯图注 section
页眉页脚/作者单位/版权信息 section
```

### 8.2 摘要 prompt 改进

章节摘要 prompt 改为适配“小章节/段落集合”：

```text
你正在总结论文中的一个章节或小章节。
如果内容包含有效方法、实验、结果、讨论或结论，请正常总结。
只有当内容完全是页眉、版权、作者单位、参考文献、无上下文表格数字时，才输出信息不足。
```

### 8.3 解析兜底

如果 LLM 输出没有严格包含：

```text
摘要：
关键点：
```

但输出本身包含有效中文/英文解释，则：

1. 使用清洗后的第一段作为 summary。
2. 使用项目符号、换行列表或剩余文本作为 keyPoints。
3. 只有输出为空或明确表示无法总结时，才写入“信息不足”。

### 8.4 避免写入大量无效 summary

如果某 section 不适合摘要，不应写入 `summary = 信息不足` 的记录污染 HYBRID_RAG。更好的策略是：

```text
低质量 section 不生成 paper_section_summary
或生成时标记 qualityScore 较低，HYBRID_RAG 默认不选
```

由于当前表没有 summary quality 字段，本阶段优先采用“不生成低质量 section summary”的方案，必要时再扩展字段。

## 9. 旧数据重建设计

由于当前已有论文的 `paper_section` 已经切坏，仅重新生成 `paper_section_summary` 不够。建议提供指定论文重建流程：

```text
指定 paperId
→ 删除该 paperId 的 Qdrant 向量点
→ 删除 paper_profile_job
→ 删除 paper_profile
→ 删除 paper_section_summary
→ 删除 paper_chunk
→ 删除 paper_section
→ 保留 paper_reference 和原始 PDF
→ 使用新规则重新 parse
→ 重新 vectorize
→ 重新生成 profile/section_summary
```

短期可通过后端服务方法或临时管理接口实现。前端按钮可以后续再做，避免误操作。

重建不删除：

```text
chat_session
chat_message
research_idea
```

避免丢失用户研究过程。

## 10. 验证设计

### 10.1 数据库验证

重建后检查：

```sql
SELECT paper_id, section_type, COUNT(*)
FROM paper_section
GROUP BY paper_id, section_type;

SELECT paper_id, COUNT(*) AS total,
       SUM(summary LIKE '%信息不足%') AS info_insufficient
FROM paper_section_summary
GROUP BY paper_id;
```

目标：

```text
paper_section 不再大量出现期刊名、表格行、模型名作为标题。
paper_section_summary 不再大面积“信息不足”。
```

### 10.2 后端测试

新增或增强测试：

1. `PaperTextCleanerTest`：过滤 DOI、copyright、Available online、邮箱、页码、重复页眉。
2. `PaperSectionDetectorTest`：避免 Table、期刊名、模型名、正文半句误判为标题。
3. `StructuredChunkingServiceTest`：验证小章节/段落分块和 table/figure_caption 标记。
4. `PaperProfileServiceImplTest`：验证章节摘要解析兜底，不把有效输出误写为“信息不足”。

### 10.3 人工验证

选择当前 3 篇论文中的至少 1 篇执行重建：

1. 重新解析。
2. 重新向量化。
3. 重新生成画像。
4. 查看 `paper_section` 标题质量。
5. 查看 `paper_section_summary` 摘要质量。
6. 在问答页多选文献，检查 `hybrid_summary` 是否显示有效摘要。

## 11. 实施阶段建议

建议拆成 5 个阶段：

1. 文本清洗增强：过滤页眉页脚、版权、DOI、作者单位、低价值 front/back matter。
2. 章节识别增强：标题候选打分、小章节识别、表格/图注不作为标题。
3. 分块和内容类型增强：段落/小章节优先，初步标记 table 和 figure_caption，降低 chunk 长度。
4. 章节摘要质量增强：跳过低质量 section，改进 prompt 和解析兜底。
5. 指定论文重建与验证：重建现有坏数据，记录数据库验证、RAG 验证和人工验证结果。

每个阶段完成后，如属于实质功能进展，应同步更新 `docs/status/current.md`。
