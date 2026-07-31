# 多模态论文复现评测基线

评测版本：`multimodal-baseline-text-v1`<br>
语料版本：`multimodal-reproduction-corpus-v1`<br>
固定日期：2026-07-27

## 目的与范围

本评测服务于“多模态论文复现证据层”实施计划的阶段 0。它量化现有纯文本链路在**可溯源复现证据**方面的结构性缺口，不评价现有 RAG 的问答质量，也不替代 `rag-corpus-16-v1` 的 16 篇/40 题回归。

对比组固定为：

```text
Baseline  = PDFBox 全文文本 → Chunk → 论文画像/章节摘要
Candidate = 页面与资产 → 原文/表图公式事实 → ReproductionSpec
```

只有事实值、来源类型和 PDF 页码均吻合时，Candidate 才能计为命中；模型推断和缺少定位的信息不能被计为确定论文事实。

## 固定语料与标注

- 8 篇本地真实 PDF：paperId `13, 15, 17, 21, 30, 31, 34, 38`；
- 覆盖原生表格、架构图、流程图、实验图、公式、双栏版式，以及风速/风功率/光伏任务；
- 24 个已标注资产锚点、56 条最小复现事实；
- 每个资产保存类型、标签、PDF 页码和角色；每个事实回链到一个资产锚点。

机器可读文件：

| 文件 | 用途 |
| --- | --- |
| `test/multimodal-evaluation-corpus-v1.json` | 固定论文 ID、受控相对路径、SHA-256 和覆盖类型 |
| `test/multimodal-evaluation-annotations-v1.json` | 资产页码锚点与复现事实金标 |
| `test/results/multimodal-evaluation-baseline-text-v1-2026-07-27.json` | 不可美化的原始基线结果 |

PDF 是用户本地数据，不进入 Git。评测脚本会对每篇 PDF 的 SHA-256 进行校验；文件变化时必须创建新的 corpus version，而不是覆盖当前基线。

## 口径

| 指标 | 计算方式 | 阶段 0 基线含义 |
| --- | --- | --- |
| 资产检测 Recall | 检出的金标资产 / 金标资产 | 现有链路没有资产记录，因此为 0/24 |
| 来源定位准确率 | 来源类型和页码均正确的事实 / 金标事实 | Chunk 尚未填充页码，因此为 0/56 |
| 关键复现事实 Recall | 有匹配值、来源类型和页码的事实 / 金标事实 | 尚无原子事实存储，因此为 0/56 |
| 事实来源完整率 | 可回链的任务包事实 / 进入任务包的事实 | 尚未产生多模态任务包事实，按结构基线记录 0 |
| 未支持事实率 | 被标成论文事实却无证据的事实 / 论文事实 | 本轮不生成新事实，记录 0，不可解读为系统已有多模态能力 |

资产 Precision 在 Baseline 中为 `null`：系统没有检测结果，不能把“无输出”伪装为 100% 精度。

## 已记录基线

原始结果已经由校验脚本生成。结论是：现有纯文本链路可能含有相同的文字片段，但没有资产对象、页级来源或原子复现事实，故不能满足本评测的严格证据要求。这是后续阶段的比较起点，不是对现有 RAG 能力的否定。

## 复跑方法

前置条件：固定 PDF 仍在 `ResearchAssistantData/` 中。无需 MySQL、Qdrant、LLM 或视觉模型 Key。

```powershell
node test/run-multimodal-evaluation-baseline.mjs
```

脚本只校验 corpus/annotation 的关联、页码合法性和 PDF 哈希，然后写入同名原始 JSON。若需要保留另一轮时间戳，设置 `MULTIMODAL_EVAL_OUTPUT_FILE` 为新的结果路径；不要覆盖已记录的正式基线。

## 后续比较规则

阶段 1 起，评测实现应在新结果中逐项记录 detected asset、事实值、来源类型、页码、验证状态和失败原因；严禁从摘要中猜测页码。阶段 2/3 的候选结果必须使用同一份 corpus 和 annotations，且继续运行既有 16 篇/40 题 RAG 回归。
