# 文献画像任务进度与文献处理队列设计

日期：2026-07-09

## 1. 背景

当前系统已经支持文献上传、解析、向量化、文献画像生成和多篇 HYBRID_RAG。画像生成已经改为异步任务模式，前端也能通过弹窗启动画像任务并轮询状态。

但当前体验仍有几个问题：

1. 文献列表把新上传、未解析、未向量化、未画像和已可问答文献混在一个表格里。
2. 点击生成画像后仍主要围绕画像弹窗展示状态，用户更希望弹窗关闭后在列表中看到进度。
3. 画像任务只有 `PROCESSING / COMPLETED / FAILED`，缺少真实步骤、百分比、章节处理进度和重试次数。
4. 文献列表操作列同时显示解析、向量化、画像、改分类、问答、删除，按钮太多，不美观。

本阶段目标是把文献页升级为“待处理文献队列 + 已入库文献列表”，并把文献画像任务做成可观察、可重试的后台进度任务。

## 2. 目标

本阶段实现以下能力：

1. 文献页拆分为“待处理文献”和“已入库文献”两个列表。
2. 只有解析、向量化、画像三步全部完成的文献才进入已入库列表。
3. 待处理列表使用一个主按钮引导下一步操作：解析、向量化、生成画像、重试画像。
4. 点击生成画像后立即关闭弹窗或不打开弹窗，回到列表中展示画像任务进度。
5. 后端画像任务记录当前步骤、百分比、已处理章节数、总章节数、重试次数和失败原因。
6. 文献列表操作按钮改为“主按钮/问答 + 更多菜单”，减少横向按钮拥挤。
7. 保持现有文献分类、删除、问答跳转、画像查看能力可用。

## 3. 非目标

本阶段不做以下事情：

1. 不把解析和向量化也改成完整异步任务系统。
2. 不做“一键自动处理全部待处理文献”。
3. 不引入 WebSocket 或 SSE，前端继续使用定时轮询。
4. 不改变现有 paper_profile 和 paper_section_summary 的画像生成结果结构。
5. 不改变 HYBRID_RAG 的策略规则，只改善画像生成和文献处理入口。

## 4. 后端设计

### 4.1 画像任务表增强

在现有 `paper_profile_job` 基础上新增字段：

```text
current_step          当前任务步骤
progress_percent      进度百分比
processed_sections    已处理章节数
total_sections        总章节数
retry_count           已重试次数
last_error_message    最近失败原因
```

保留现有字段：

```text
id
paper_id
status
error_message
start_time
finish_time
create_time
update_time
```

`error_message` 可继续作为当前失败原因字段使用；`last_error_message` 用于保留最近失败信息。如果实现时发现两者重复，可只保留并复用 `error_message`，但响应 DTO 要稳定返回 `errorMessage`。

### 4.2 任务状态和步骤

任务状态继续保持三类：

```text
PROCESSING
COMPLETED
FAILED
```

用 `current_step` 表达具体进度：

```text
WAITING               等待开始
PREPARING             准备正文和章节
GENERATING_SECTIONS   正在生成章节摘要
GENERATING_PROFILE    正在生成整篇画像
SAVING_RESULT         正在保存画像结果
COMPLETED             已完成
FAILED                已失败
```

章节摘要生成期间，后端持续更新：

```text
current_step = GENERATING_SECTIONS
processed_sections = 已完成章节摘要数
total_sections = 总章节数
progress_percent = 按步骤权重和章节数计算出的百分比
```

建议进度权重：

```text
PREPARING             0% - 5%
GENERATING_SECTIONS   5% - 80%
GENERATING_PROFILE    80% - 95%
SAVING_RESULT         95% - 99%
COMPLETED             100%
FAILED                保留失败时进度
```

### 4.3 进度更新方式

`PaperProfileService.generateProfile(paperId)` 当前负责生成章节摘要和整篇画像。为了避免画像服务直接依赖任务表，建议增加轻量回调接口，例如：

```java
public interface PaperProfileProgressListener {
    void onPreparing();
    void onSectionProgress(int processedSections, int totalSections);
    void onGeneratingProfile();
    void onSavingResult();
}
```

`PaperProfileJobServiceImpl` 创建任务后，调用带 listener 的画像生成方法，listener 内部更新 `paper_profile_job`。

保留现有 `generateProfile(paperId)`，让它调用新重载方法并传入空 listener，避免影响原接口和单元测试。

### 4.4 重试规则

用户点击“重试画像”仍调用：

```http
POST /api/papers/{id}/profile/async
```

后端规则：

1. 如果该文献已有 `PROCESSING` 任务，直接返回该任务，避免重复调用大模型。
2. 如果最近任务是 `FAILED`，创建新任务，`retry_count = 上一次 retry_count + 1`。
3. 如果最近任务是 `COMPLETED`，允许重新生成画像，创建新任务，`retry_count` 沿用最近值或递增为重新生成次数；最终仍由 profile upsert 保证同版本画像只有一份。
4. 新任务初始值：`status=PROCESSING`，`current_step=WAITING`，`progress_percent=0`，`processed_sections=0`。
5. 任务失败时写入 `FAILED`、`current_step=FAILED`、`error_message`、`finish_time`。
6. 任务成功时写入 `COMPLETED`、`current_step=COMPLETED`、`progress_percent=100`、`finish_time`。

## 5. 后端接口设计

### 5.1 启动或重试画像任务

继续使用现有接口：

```http
POST /api/papers/{id}/profile/async
```

返回增强后的任务 DTO：

```json
{
  "id": 12,
  "paperId": 7,
  "status": "PROCESSING",
  "currentStep": "GENERATING_SECTIONS",
  "progressPercent": 35,
  "processedSections": 12,
  "totalSections": 51,
  "retryCount": 1,
  "errorMessage": null,
  "startTime": "2026-07-09T10:00:00",
  "finishTime": null,
  "createTime": "2026-07-09T10:00:00",
  "updateTime": "2026-07-09T10:03:00"
}
```

### 5.2 查询单篇画像任务状态

继续使用现有接口：

```http
GET /api/papers/{id}/profile/job
```

返回最近任务，字段同上。

### 5.3 批量查询文献画像状态

新增轻量接口：

```http
GET /api/papers/profile-status
```

用于文献页一次性获取画像存在情况、章节摘要数量和最近任务状态，避免前端对每篇文献循环请求。

响应示例：

```json
[
  {
    "paperId": 7,
    "hasProfile": true,
    "profileVersion": "paper-profile-v1",
    "sectionSummaryCount": 51,
    "jobStatus": "COMPLETED",
    "currentStep": "COMPLETED",
    "progressPercent": 100,
    "processedSections": 51,
    "totalSections": 51,
    "retryCount": 0,
    "errorMessage": null
  }
]
```

如果某篇文献没有画像也没有任务，返回：

```json
{
  "paperId": 8,
  "hasProfile": false,
  "jobStatus": null,
  "currentStep": null,
  "progressPercent": 0
}
```

## 6. 前端设计

### 6.1 页面结构

文献页主区域拆成两个卡片列表：

```text
待处理文献
已入库文献
```

待处理文献：

```text
解析未完成
或 向量化未完成
或 画像未完成
或 任一步失败
```

已入库文献：

```text
parseStatus = COMPLETED
vectorStatus = COMPLETED
hasProfile = true
且最近画像任务不是 PROCESSING / FAILED
```

### 6.2 待处理文献列表

待处理列表每行显示：

```text
标题 / 作者 / 分类
当前阶段
进度说明
主按钮
更多菜单
```

主按钮规则：

```text
未解析        -> 解析
已解析未向量化 -> 向量化
已向量化未画像 -> 生成画像
画像失败      -> 重试画像
画像生成中    -> 生成中，按钮禁用
```

进度说明示例：

```text
还没有解析 PDF
已解析，等待写入 Qdrant
章节摘要 12/51 · 35%
Qwen API Key 未配置
```

### 6.3 已入库文献列表

已入库列表每行显示：

```text
标题 / 作者 / 分类
画像版本
章节摘要数量
上传时间
问答按钮
更多菜单
```

外露按钮只保留：

```text
问答
更多
```

更多菜单收纳：

```text
查看画像
重新生成画像
改分类
删除
```

### 6.4 画像生成交互

用户可以从两个入口启动画像生成：

1. 待处理列表主按钮“生成画像 / 重试画像”。
2. 画像弹窗中的“生成画像 / 重新生成画像”。

启动成功后：

```text
调用 POST /api/papers/{id}/profile/async
关闭画像弹窗
回到文献列表
列表行内显示画像进度
启动轮询 profile-status
```

如果任务完成：

```text
刷新 profile-status
文献自动从待处理列表进入已入库文献
显示成功提示
```

如果任务失败：

```text
停留在待处理列表
显示失败原因
主按钮变为“重试画像”
```

### 6.5 轮询策略

页面加载时请求：

```text
GET /api/papers
GET /api/papers/profile-status
GET /api/paper-categories
```

如果存在任一 `PROCESSING` 画像任务，则每 3 秒轮询一次 `GET /api/papers/profile-status`。

当没有处理中的画像任务时停止轮询。

离开页面时清理轮询定时器。

## 7. 测试设计

### 7.1 后端测试

重点测试：

1. 新任务初始进度字段正确。
2. 已有 `PROCESSING` 任务时复用任务，不重复创建。
3. 章节摘要生成进度会更新 `processedSections / totalSections / progressPercent`。
4. 失败任务记录 `FAILED / currentStep=FAILED / errorMessage`。
5. 失败后再次启动会创建新任务并递增 `retryCount`。
6. 成功后任务进度为 100。
7. 批量画像状态接口能返回 hasProfile、sectionSummaryCount、jobStatus 和进度字段。

### 7.2 前端测试

重点测试：

1. `listPaperProfileStatuses()` 请求 `GET /api/papers/profile-status`。
2. `startPaperProfileJob()` 继续请求 async 接口。
3. 文献工作流状态计算函数覆盖：待解析、待向量化、待画像、画像生成中、画像失败、已入库。
4. 构建通过：`npm run build`。

### 7.3 人工验证步骤

推荐人工验证：

1. 上传一篇新 PDF，确认出现在“待处理文献”。
2. 点击主按钮“解析”，完成后变为“向量化”。
3. 点击主按钮“向量化”，完成后变为“生成画像”。
4. 点击“生成画像”，确认弹窗关闭或不打开弹窗，列表显示画像进度。
5. 等待画像完成，确认文献自动进入“已入库文献”。
6. 临时制造失败，确认失败原因和“重试画像”出现。
7. 恢复配置后点击“重试画像”，确认 retryCount 增加并最终成功。
8. 在已入库文献点击“问答”，确认仍能跳转到论文问答页。
9. 在更多菜单中测试查看画像、重新生成画像、改分类和删除。

## 8. 实施阶段建议

实施拆为四个阶段：

1. 后端画像任务进度字段和重试机制。
2. 批量画像状态接口和状态 DTO。
3. 前端文献页双列表、主按钮、更多菜单和行内进度。
4. 后端测试、前端测试、构建验证和人工验证步骤整理。

每个阶段完成后，如属于实质功能进展，应同步更新 `docs/status/current.md`。
