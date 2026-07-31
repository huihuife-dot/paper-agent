# 测试资产

## RAG 回归

| 文件 | 作用 |
| --- | --- |
| `run-rag-evaluation.mjs` | 调用真实后端执行固定 RAG 回归并生成 JSON |
| `run-rag-streaming-evaluation.mjs` | 执行真实 SSE 用例，统计首段时间、完整时间和答案一致性 |
| `run-rag-multiturn-evaluation.mjs` | 执行连续追问，统计改写锚点、策略和目标论文召回 |
| `rag-evaluation-cases-16.json` | 当前 16 篇/40 题正式用例 |
| `rag-performance-cases.json` | 单篇、多篇、全库性能与流式代表用例 |
| `rag-evaluation-corpus-16.json` | 当前语料元数据快照 |
| `rag-evaluation-cases.json` | 早期三篇论文用例，保留作基线追溯 |
| `results/` | 每轮不可篡改的原始评测结果 |

当前正式结果为 `results/rag-evaluation-16-final-stage3-2026-07-16.json`。指标解释和报告入口见 `docs/evaluation/README.md`。

## 多模态复现证据基线

| 文件 | 作用 |
| --- | --- |
| `multimodal-evaluation-corpus-v1.json` | 8 篇固定 PDF 的相对路径、SHA-256 和覆盖类型 |
| `multimodal-evaluation-annotations-v1.json` | 24 个资产页码锚点与 56 条复现事实金标 |
| `run-multimodal-evaluation-baseline.mjs` | 校验语料/标注，并生成纯文本结构基线 JSON |
| `run-multimodal-vision-anchors.mjs` | 对 24 个固定锚点执行定向 Qwen-VL 分析并记录调用耗时 |
| `run-multimodal-evaluation-candidate.mjs` | 调用真实后端评估资产、事实、来源、数值和冲突发布门槛 |

固定 PDF 位于本地 `ResearchAssistantData/`，不纳入 Git。运行不依赖模型或视觉 Key：

```powershell
node test/run-multimodal-evaluation-baseline.mjs
node test/run-multimodal-vision-anchors.mjs
node test/run-multimodal-evaluation-candidate.mjs
```

流式回归需要 IDEA 后端和真实模型服务处于运行状态：

```powershell
node test/run-rag-streaming-evaluation.mjs
```

历史感知多轮回归同样使用真实服务，会依次执行单篇代词、多篇比较和全库指代追问：

```powershell
node test/run-rag-multiturn-evaluation.mjs
```

## 文档检查

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File test/check-doc-links.ps1
```

脚本检查仓库内 Markdown 相对链接是否指向真实文件。

## 历史手工页面

`archive/manual-pages/` 保存早期文献上传和解析的裸 HTML 调试页。当前正式交互应使用 Vue 前端，这些页面不属于自动回归入口。
