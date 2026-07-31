# 产品需求文档

> 本文维护产品目标与业务边界；实现进度请查看[项目当前状态](../status/current.md)，技术细节请查看[架构文档](../architecture/README.md)。

> 唯一的需求规格说明。项目定位、使用场景、功能范围、业务规则均以此文档为准。

## 1. 项目定位

MyAgent 是一个面向个人本地使用的**论文/文献 AI 研究助手**。

核心目标不是做通用聊天机器人，而是围绕本地文献库完成：

- 文献管理和本地存储
- PDF 文本解析
- 文献知识库构建（chunk + 向量化）
- 基于文献 sources 的 RAG 问答
- 指定文献范围问答
- 对话历史管理
- Research Idea 记录和整理
- 从论文或 Idea 启动本地代码复现/改进，并追踪交接状态

## 2. 使用场景

### 2.1 文献管理

用户上传收集的论文、报告等资料。系统保存文件，在数据库中记录标题、作者、年份、文件路径、处理状态等。用户可在页面中搜索、筛选、管理文献。

### 2.2 RAG 知识库问答

用户上传文献后将其加入知识库。系统解析 PDF → 文本切分 → 向量化 → 写入向量库。用户提问时检索相关片段，结合大模型生成带来源引用的回答。

### 2.3 指定文献对话

用户勾选一篇或多篇文献，开启限定范围的对话。问答只在指定文献的 chunks 中检索。支持多文献比较、总结、归纳，并在回答中标注文献来源。

### 2.4 科研 Idea 记录

对话过程中产生的研究想法可以保存为 Idea。系统支持两种触发方式：

1. 用户在聊天界面点击“保留 Idea”按钮，系统将当前聊天会话内容发送给大模型，总结成 Research Idea 草稿；用户确认后保存。
2. 在 RAG 交流过程中，大模型如果判断当前讨论中出现了有潜力的研究想法，可以提醒用户“这可能值得保留为 Idea”；用户确认后，走同样的大模型总结和保存流程。

Research Idea 保存前需要经过用户确认。大模型主要负责把聊天内容整理为结构化草稿，包括标题、原始来源摘要、整理后的内容、创新点、研究问题、可能方法、标签和关联来源。

### 2.5 多模态论文复现证据

系统把 PDF 页面、图片、表格、公式和算法块保存为带页码、来源修订和验证状态的资产。确定性解析优先，Qwen-VL 只分析架构/流程图、关键表格和低置信度公式；模型推断与可信论文事实分开。资产进一步生成复现事实、冲突组和 ReproductionSpec，并通过 Agent Context v2 提供给本地复现 Agent。普通 RAG 不自动混入独立复现事实索引。

### 2.6 论文复现与 Idea 代码改进

用户可以从单篇论文启动一个隔离的最小代码复现，也可以从 Research Idea 出发，在用户选择的已有仓库中完成一次聚焦改进。MyAgent 负责输出结构化任务包和证据；接入的 Claude Code / Research Engineering Agent 项目负责本地代码执行、短检查和交接。

该能力不承诺自动重现论文实验指标。数据下载、依赖安装、长时间训练、调参、完整实验和研究结论仍由用户决定并执行。

## 3. MVP 主链路

```text
文献上传
→ 文献信息保存
→ PDF 解析
→ 文本 chunk 入库
→ chunk embedding 向量化
→ 向量写入 Qdrant
→ 用户提问
→ 问题向量化 → Qdrant 检索
→ 回查 MySQL chunk 原文
→ 构造 RAG prompt
→ 调用大模型
→ 返回 answer + sources
```

## 4. 当前技术路线

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.x + Java 21 |
| 数据库 | MySQL 8（业务数据 + chunk 原文） |
| ORM | MyBatis-Plus |
| PDF 解析 | PDFBox |
| 向量库 | Qdrant（Docker 部署） |
| 大模型 | LlmService 抽象，已接入 DeepSeek；后续扩展智谱 GLM、通义千问 Qwen |
| Embedding | EmbeddingService 抽象，当前使用 Qwen text-embedding-v4 |
| 前端 | Vue 3 + Vite + Element Plus |
| 本地代码 Agent | 独立 Claude Code / Research Engineering Agent 项目，通过 localhost Bridge 接入 |

## 5. 第一阶段功能范围（MVP）

**已实现：**

1. 文献上传、列表、详情、下载、删除
2. PDF 文本解析 + chunk 入库
3. chunk 向量化写入 Qdrant
4. Qdrant 向量检索
5. RAG sources 回查
6. RAG prompt 构造
7. LlmService 抽象（fake / deepseek 可切换）
8. 基于真实大模型的 RAG 问答

**计划中：**

9. 指定文献范围问答
10. 对话会话和消息保存
11. Research Idea 保留流程：用户主动点击保留，或大模型在交流中建议保留；统一由大模型总结为草稿，用户确认后保存
12. 真实 embedding 模型接入
13. 前端 Vue 页面

## 6. 暂不做的内容

- 多用户权限系统
- 云端部署
- 桌面客户端
- OCR 扫描版 PDF
- 扫描版 PDF 的通用 OCR 流水线
- 自动识别 Idea
- 复杂任务队列

## 7. 业务规则

### 7.1 RAG 回答边界

回答必须优先基于检索到的文献 sources。如果 sources 不足以回答问题，应明确说明信息不足，不得编造论文中没有的内容。

### 7.2 sources 可追溯

回答接口必须返回 sources，至少包含：paperId、paperTitle、chunkId、chunkIndex、score、content。后续可扩展 pageNumber、章节、引用格式等。

### 7.3 LLM 调用边界

业务层只依赖 `LlmService` 接口，不得直接依赖具体供应商。供应商通过配置切换：

```properties
llm.provider=fake      # 占位实现
llm.provider=deepseek  # DeepSeek 真实调用
```

后续扩展 Qwen、智谱 GLM 时，不改动 RAG 主流程。

### 7.4 本地数据安全

文献文件、MySQL 数据、Qdrant 数据、API Key 均为本地敏感数据，不得提交到仓库。

## 8. 本地部署

系统运行在用户个人电脑上。建议本地数据目录：

```text
E:/ResearchAssistantData
├── papers/     # 文献文件
├── figures/    # 图表提取
├── logs/       # 运行日志
├── backups/    # 数据备份
└── config/     # 本地配置
```

当前项目使用 `<project-root>/ResearchAssistantData/papers/` 作为文献存储目录。
