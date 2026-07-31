# 开发路线

> 唯一的开发阶段规划。已完成、进行中、计划中的工作均以此文档为准。
> 当前具体进度和阻塞点见[项目当前状态](../status/current.md)。

## 阶段 1：后端基础和文献管理 ✅

**目标**：Spring Boot 后端启动、MySQL 连接、文献 CRUD。

- [x] Spring Boot 项目创建
- [x] MySQL 连接 + 表结构创建
- [x] 统一返回结构 `Result` + 全局异常处理
- [x] 文献上传 `/api/papers/upload`
- [x] 文献列表 `/api/papers`
- [x] 文献详情 `/api/papers/{id}`
- [x] 文献下载 `/api/papers/{id}/download`
- [x] 文献删除 `/api/papers/{id}`

## 阶段 2：PDF 解析和 chunk 入库 ✅

**目标**：上传的 PDF 转成可检索文本块。

- [x] PDFBox 文本解析
- [x] `paper_chunk` 表写入
- [x] `/api/papers/{id}/parse`
- [x] `/api/papers/{id}/chunks`

## 阶段 3：向量库和检索链路 ✅

**目标**：打通 Spring Boot → Qdrant 的向量写入和检索。

- [x] Docker 启动 Qdrant
- [x] Spring Boot 连接 Qdrant
- [x] `paper_chunks` collection 创建
- [x] `EmbeddingService` 抽象 + `FakeEmbeddingServiceImpl`
- [x] `/api/papers/{id}/vectorize`
- [x] `/api/qdrant/health`
- [x] `/api/qdrant/search`
- [x] `/api/rag/sources`

- [x] 接入 Qwen text-embedding-v4
- [x] collection 重建为 1024 维 Cosine
- [x] BM25 + dense 多路 RRF 混合检索

## 阶段 4：RAG 问答主链路 ✅

**目标**：返回基于文献 sources 的真实大模型回答。

- [x] `/api/rag/chat`
- [x] RAG sources 回查 MySQL
- [x] `RagPromptService` 构造 prompt
- [x] `LlmService` 抽象
- [x] `FakeLlmServiceImpl` 占位
- [x] `DeepSeekLlmServiceImpl` 真实调用
- [x] 响应中包含 modelProvider、modelName、sourceCount 等元信息

- [x] 扩展 Qwen、智谱等 provider-neutral 实现

## 阶段 5：指定文献问答 ✅

**目标**：用户选择一篇或多篇文献，限定问答范围。

**实现接口**：`POST /api/rag/chat`，请求体通过 `paperIds` 限定范围。

**核心能力**：
- 请求携带 `paperIds`
- Qdrant 检索按 `paperId` 过滤
- 回答只基于指定文献 sources

## 阶段 6：对话历史 ✅

**目标**：持久化保存 RAG 问答和科研对话。

**计划能力**：
- 新建 `chat_session`
- 保存 `chat_message`（含 sources_json）
- 记录 modelProvider、modelName
- 查看历史会话和消息列表

## 阶段 7：Research Idea 管理 ✅

**目标**：把 RAG 对话中出现的研究想法保存为结构化 Idea，并保留来源会话和来源消息。

**计划能力**：
- Idea 列表和详情
- Idea 编辑和删除
- 用户主动点击“保留 Idea”：将当前聊天会话发送给大模型，总结为 Research Idea 草稿，用户确认后保存
- 大模型主动建议保留：在交流过程中判断当前讨论可能形成研究想法时，提醒用户可以保留为 Idea；用户确认后复用同一套总结和保存流程
- 大模型整理 Idea：提炼标题、关键词、创新点、研究问题、可能方法和关联来源
- 关联文献、会话和消息

## 阶段 8：前端 Vue 页面 ✅

**目标**：创建本地 Web 管理界面。

**优先页面**：
1. 文献列表 + 上传
2. 文献详情 + chunk 查看
3. RAG 问答页
4. 指定文献问答页
5. 对话历史页
6. Research Idea 管理页

## 阶段 9：高级 RAG 与论文发现 ✅

- [x] 单篇 FULL_TEXT_PARSED
- [x] 多篇 HYBRID_RAG（画像 + 章节摘要 + 原文证据）
- [x] 全库 LIBRARY_DISCOVERY
- [x] Advanced Query Rewrite + HyDE
- [x] Qdrant dense + 内存 BM25 + RRF
- [x] 论文级相关度聚合与前端推荐卡片
- [x] 显式画像/章节摘要索引接口与前端第四步
- [x] 三篇核心论文多粒度向量入库与幂等验收
- [x] 全库、指定范围、多篇比较真实链路验收

## 阶段 10：可观测流式与多轮追问 ✅

- [x] RAG 分阶段耗时与前端执行详情
- [x] 模型原生增量生成、SSE 转发和首段耗时评测
- [x] 最近 3 轮历史感知追问改写与失败回退
- [x] 原问题留存、实际检索问题可解释展示
- [x] 单篇代词、多篇比较和全库指代真实回归

## 阶段 11：Research Engineering Agent 接入 ✅

- [x] 论文复现与 Idea 代码改进的只读上下文 API
- [x] evidence、缺失信息、代码范围和验收项组成的结构化 `taskPackage`
- [x] 文献与 Idea 页面本地 Agent 入口
- [x] localhost Bridge 创建论文隔离工作区、选择 Idea 现有仓库
- [x] Claude Code / Research Engineering Agent 首轮代码交付与连续 CLI 改进
- [x] RepositoryBaseline、路径权限、短检查和失败恢复交接
- [x] MyAgent 复现项目索引与最近 50 条安全活动记录
- [x] 使用真实 Paper 38 完成论文复现端到端联调

## 阶段 12：多模态论文复现证据层（已完成）

**目标**：补齐纯文本解析无法覆盖的表格、图片、公式和算法证据，为论文最小代码复现提供有来源、有验证状态的结构化事实。

- [x] 建立固定多模态论文语料、人工标注和纯文本基线
- [x] 建立页面级文本块、页码、来源修订和原始资产存储
- [x] 确定性提取表格、公式和算法块
- [x] 通过 `VisionModelService` 定向分析架构图、关键表格和低置信度公式
- [x] 构建复现事实、独立 Qdrant collection 和冲突检测
- [x] 构建 `ReproductionSpec`、复现准备度和 Agent Context v2
- [x] 完成多模态评测、现有 RAG 回归及两篇真实 Agent 复现验收

最终指标、限制和原始结果入口见[多模态复现证据层发布评测](../evaluation/reports/multimodal-reproduction-release-2026-07-28.md)，已完成计划保存在归档。

## 条件式后续路线

```text
当前版本保持稳定并收集真实使用问题
→ 语料达到50篇以上时重建大语料评测基线
→ 流式使用出现真实断线或并发压力时再规划可靠性增强
→ 真实使用发现阻断问题时进行针对性修复
```

## 开发原则

1. 每次只推进一个阶段。
2. 业务主流程不绑定具体大模型供应商（通过 `LlmService` 抽象）。
3. RAG 回答必须围绕文献 sources，不做泛泛聊天。
4. 后续新增功能优先复用现有服务边界。
5. 不把临时测试、小型配置验证写入进度文档；只记录实质模块进展。
