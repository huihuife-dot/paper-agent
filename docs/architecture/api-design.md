# API 设计

> 已实现的接口标注 ✅，计划中的接口标注 📋。

## 基础说明

- 基础地址：`http://localhost:8080`
- 统一前缀：`/api`
- 统一响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

---

## 1. 系统接口

### 1.1 健康检查 ✅

```text
GET /api/health
```

---

## 2. 文献管理接口

### 2.1 上传文献 ✅

```text
POST /api/papers/upload
Content-Type: multipart/form-data
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| file | File | 是 | 文献文件 |
| title | String | 否 | 文献标题 |
| authors | String | 否 | 作者 |
| publishYear | Integer | 否 | 发表年份 |
| journal | String | 否 | 期刊或会议 |
| keywords | String | 否 | 关键词 |
| remark | String | 否 | 备注 |

### 2.2 文献列表 ✅

```text
GET /api/papers?page=1&size=10&keyword=xxx
```

### 2.3 文献详情 ✅

```text
GET /api/papers/{id}
```

### 2.4 删除文献 ✅

```text
DELETE /api/papers/{id}
```

### 2.5 下载文献 ✅

```text
GET /api/papers/{id}/download
```

### 2.6 解析文献 ✅

```text
POST /api/papers/{id}/parse
```

解析 PDF 文本并切分成 chunk 写入 `paper_chunk` 表。

### 2.7 查看文献 chunks ✅

```text
GET /api/papers/{id}/chunks
```

### 2.8 向量化文献 ✅

```text
POST /api/papers/{id}/vectorize
```

对文献的 chunks 生成 embedding 并写入 Qdrant。

---

## 3. Qdrant 接口

### 3.1 健康检查 ✅

```text
GET /api/qdrant/health
```

### 3.2 创建 collection ✅

```text
POST /api/qdrant/collections/paper-chunks
```

### 3.3 向量检索 ✅

```text
GET /api/qdrant/search?query=xxx&topK=5
```

---

## 4. RAG 问答接口

### 4.1 RAG 问答 ✅

```text
POST /api/rag/chat
```

请求：

```json
{
  "question": "这些文献中常见的研究方法有哪些？",
  "topK": 5
}
```

响应：

```json
{
  "code": 200,
  "data": {
    "answer": "基于文献内容生成的回答...",
    "sources": [
      {
        "paperId": 1,
        "paperTitle": "示例论文标题",
        "chunkId": 10,
        "chunkIndex": 0,
        "score": 0.82,
        "content": "相关文献片段..."
      }
    ],
    "modelProvider": "deepseek",
    "modelName": "deepseek-chat",
    "sourceCount": 5
  }
}
```

### 4.2 RAG 流式问答 ✅

```text
POST /api/rag/chat/stream
Accept: text/event-stream
```

请求结构与 `POST /api/rag/chat` 相同。响应依次使用 `phase`、`metadata`、`delta`、`complete`、`error` SSE 事件；前端通过 `fetch` 读取 POST 响应流，不使用只支持 GET 的 `EventSource`。

### 4.3 RAG sources 检索 ✅

```text
GET /api/rag/sources?question=xxx&topK=5
```

只返回检索到的 sources，不调用大模型。

### 4.4 指定文献范围问答 ✅

同步和流式接口都通过同一个请求体的 `paperIds` 指定范围：

```json
{
  "question": "请比较这三篇论文的方法差异。",
  "paperIds": [1, 2, 3],
  "topK": 8
}
```

---

## 5. 对话接口 📋

### 5.1 新建会话

```text
POST /api/chat/sessions
```

```json
{
  "title": "新的科研讨论",
  "chatType": "normal"
}
```

### 5.2 会话列表

```text
GET /api/chat/sessions
```

### 5.3 会话消息

```text
GET /api/chat/sessions/{sessionId}/messages
```

### 5.4 发送消息

```text
POST /api/chat/sessions/{sessionId}/messages
```

```json
{
  "content": "帮我整理一下这个研究方向的思路。"
}
```

---

## 6. Idea 管理接口 📋

### 6.1 创建 Idea

```text
POST /api/ideas
```

```json
{
  "title": "基于 RAG 的科研助手",
  "originalContent": "原始想法内容...",
  "sourceType": "chat",
  "saveType": "manual",
  "sourceSessionId": 1,
  "sourceMessageId": 10,
  "relatedPaperIds": [1, 2]
}
```

### 6.2 Idea 列表

```text
GET /api/ideas
```

### 6.3 Idea 详情

```text
GET /api/ideas/{id}
```

### 6.4 更新 Idea

```text
PUT /api/ideas/{id}
```

### 6.5 删除 Idea

```text
DELETE /api/ideas/{id}
```

### 6.6 大模型精简 Idea

```text
POST /api/ideas/refine
```

```json
{
  "content": "原始科研想法内容..."
}
```

---

## 7. Research Engineering Agent 接口 ✅

### 7.1 获取论文复现上下文

```text
GET /api/papers/{id}/reproduction-context?version=2
```

默认返回向后兼容的 Agent Context v2：除论文画像和章节摘要外，直接加入最多 8 条关键原文块、最多 40 条可信复现事实，并携带 `paperId`、`sourceKind`、`sourceId`、页码、验证状态和置信度。`taskPackage` 同时返回复现规格、冲突、缺失信息和安全默认值。旧 Agent 可显式请求 `version=1`，继续读取原有字段和任务包。

### 多模态论文资产

```text
POST /api/papers/{paperId}/assets/extract
GET  /api/papers/{paperId}/assets
GET  /api/papers/{paperId}/assets/{assetId}
GET  /api/papers/{paperId}/assets/{assetId}/content
PUT  /api/papers/{paperId}/assets/{assetId}/analysis
POST /api/papers/{paperId}/assets/{assetId}/analyze
POST /api/papers/{paperId}/assets/{assetId}/confirm
POST /api/papers/{paperId}/assets/{assetId}/reject
POST /api/papers/{paperId}/assets/analyze
GET  /api/papers/{paperId}/assets/status
```

单条 `analyze` 是用户显式分析；批量 `assets/analyze` 只选择架构/流程图和包含复现关键词的低置信度表格、公式。视觉输出先保存为 `MODEL_INFERRED`，人工确认后才变为 `USER_CONFIRMED`。

当前确定性解析版本为 `page-assets-v3`，支持页眉与图注粘连、冒号式 Figure 图注和 encode-process-decode 流程块。视觉版本为 `qwen-vl-max:vision-schema-v2`；描述性单项会规范成数组，模型漏报不确定性时会显式标记为需要人工复核。复杂表格单次读取超时为 120 秒，同分析版本有缓存。

### 复现事实与独立索引

```text
POST /api/papers/{paperId}/reproduction-facts/rebuild
GET  /api/papers/{paperId}/reproduction-facts
GET  /api/papers/{paperId}/reproduction-facts/search?query={query}&topK=10
```

`rebuild` 从方法/实验原文块及图、表、公式、算法资产生成带来源的原子事实，合并完全相同的事实，并用 `conflictGroup` 暴露同一事实键的不同值。复现事实写入 MySQL 主库，语义向量写入独立的 `paper_reproduction_facts` collection；普通 RAG 不查询该 collection。

列表和检索默认排除 `MODEL_INFERRED`。显式传入 `includeModelInferred=true` 才能查看模型推断。Qdrant 检索命中后，服务端必须按 `factId` 回查 MySQL，再检查论文、验证状态和记录是否仍存在；Qdrant 孤立点不能直接成为返回事实。

### ReproductionSpec

```text
GET  /api/papers/{paperId}/reproduction-spec
POST /api/papers/{paperId}/reproduction-spec/rebuild
```

`rebuild` 把原子事实分为可信事实、待复核事实、模型推断和冲突组，并计算 8 类关键复现信息覆盖率、来源完整率、缺失项和准备状态。冲突事实不会进入可信事实；缺失超参数只会生成“可配置占位符”安全默认值，不会伪装成论文参数。事实重建或失效时，对应规格同步失效。

返回选中论文、画像/章节摘要、关键原文和可信复现事实等可追溯 evidence，以及 `taskType=PAPER_REPRODUCTION` 的结构化 `taskPackage`。接口只读取科研资料，不创建工作区、不执行代码。

### 7.2 获取 Idea 代码改进上下文

```text
GET /api/research-ideas/{id}/improvement-context
```

返回 Idea 本体、关联论文 evidence，以及 `taskType=IDEA_CODE_IMPROVEMENT` 的结构化 `taskPackage`。本地仓库路径和 RepositoryBaseline 由 localhost Bridge 补充，不进入此接口。

关联论文 evidence 会优先选择与 Idea 标题、研究问题、方法和标签相关的可信复现事实，并限量补充关键原文；事实项使用 `idea_related_paper_fact`，原文使用 `idea_related_paper_raw_chunk`。论文摘要仍可作为背景，但不作为精确实现参数。Idea 定义改进目标，本地 RepositoryBaseline 定义代码事实，关联论文只支持方法细节。

### 7.3 查询 Agent 项目摘要

```text
GET /api/agent-projects
```

返回论文复现与 Idea 改进项目卡片、状态和最近活动，不返回本地源代码或绝对路径。

### 7.4 同步 Agent 项目摘要

```text
POST /api/agent-projects
```

由本地 Claude Code / Research Engineering Agent 项目在每轮交付后同步安全摘要。系统按 `sourceId + mode` 归并项目，活动列表最多保留 50 条，并通过临时文件原子替换保存。证据决策升级后，活动摘要还可携带 `evidenceReadiness`、`evidenceStrategy`、`evidenceTracePath` 和 `evidenceDecisionCounts`；这些字段只描述状态与本地相对索引，不上传源代码、密钥或绝对路径。

> `/v1/paper-projects/**` 与 `/v1/idea-projects/**` 属于本机 localhost Bridge，不属于 Spring Boot `/api` 接口。
