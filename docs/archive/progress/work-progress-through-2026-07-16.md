# MyAgent 工作进度记录

更新时间：2026-07-10

项目路径：`<project-root>`

## 1. 用户协作偏好

- 用户希望按“一个开发任务 / 一个阶段”推进，而不是每个极小步骤都停下来。
- 每个任务需要说明：
  - 当前要做什么
  - 为什么要做
  - 执行什么命令或修改什么代码
  - 命令/代码需要带解释和注释
- 电脑可能重启，因此每次重新进入项目后，Claude 应先阅读本文件再继续。

## 2. 当前系统目标

构建一个论文/文献 AI 研究助手系统。

MVP 主链路：

```text
文献上传
→ 文献列表
→ PDF 解析
→ 文本 chunk 入库
→ chunk 向量化
→ 向量检索
→ RAG 问答
→ 对话历史 / Research Idea 管理
```

当前技术路线暂定：

- 后端：Spring Boot
- 数据库：MySQL
- ORM：MyBatis-Plus
- PDF 解析：PDFBox
- 向量数据库：Qdrant
- 前端：后续再创建 Vue 项目

## 3. 已完成后端功能

根据项目扫描和用户说明，当前后端已完成：

1. Spring Boot 后端已创建并能启动
2. MySQL 已连接成功
3. 已创建以下表：
   - `paper_reference`
   - `paper_chunk`
   - `chat_session`
   - `chat_message`
   - `research_idea`
4. 已实现 `/api/health`
5. 已实现 `/api/papers` 查询
6. 已实现 `/api/papers/upload` 上传
7. 已实现 `/api/papers/{id}/download` 下载
8. 已实现 `/api/papers/{id}` 删除
9. 已实现统一 `Result` 返回和全局异常处理
10. 已实现 `/api/papers/{id}/parse`
11. PDF 解析和 chunk 入库已经成功

## 4. 项目当前结构摘要

主要目录：

```text
<project-root>
├── backend/research-assistant-backend
├── docs
├── frontend
├── test
└── ResearchAssistantData
```

后端主要代码位于：

```text
backend/research-assistant-backend/src/main/java/com/myagent/assistant
```

已看到的关键模块：

```text
paper/common/Result.java
paper/common/GlobalExceptionHandler.java
paper/config/WebConfig.java
paper/controller/PaperController.java
paper/entity/PaperReference.java
paper/entity/PaperChunk.java
paper/mapper/PaperReferenceMapper.java
paper/mapper/PaperChunkMapper.java
paper/service/PaperReferenceService.java
paper/service/PdfParseService.java
paper/service/Impl/PaperReferenceServiceImpl.java
paper/service/Impl/PdfParseServiceImpl.java
```

配置文件：

```text
backend/research-assistant-backend/src/main/resources/application.properties
```

当前已配置 MySQL 和文件上传目录。

## 5. 当前阻塞点：Docker / WSL2 未就绪

用户原本要启动 Qdrant，但执行 Docker 命令时报错。

最早错误：

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine;
check if the path is correct and if the daemon is running:
open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

用户启动 Docker Desktop 后，执行创建 volume 又出现：

```text
request returned 500 Internal Server Error for API route and version
http://%2F%2F.%2Fpipe%2FdockerDesktopLinuxEngine/v1.54/volumes/create,
check if the server supports the requested API version
```

已执行过的检查：

```bash
docker context ls
DOCKER_API_VERSION=1.51 docker version
DOCKER_CONTEXT=default docker version
wsl.exe --status
wsl.exe -l -v
```

诊断结果：

- Docker Desktop 进程存在。
- 当前 Docker context 是 `desktop-linux`。
- `desktop-linux` 和 `default` context 都无法连接 Docker Engine。
- 临时降级 Docker API 到 `1.51` 仍然失败。
- `wsl.exe --status` 输出乱码，但内容大意是 Windows Subsystem for Linux 未安装/未启用，并提示运行 `wsl.exe --install`。
- 因此判断：Docker Desktop Linux Engine 起不来，根因大概率是 WSL2 / 虚拟机平台未启用或未安装。

## 6. 重启前建议用户执行的系统命令

需要用户用“管理员权限 PowerShell”执行以下命令：

```powershell
# 启用 WSL：Windows Subsystem for Linux
# Docker Desktop 在 Windows 上需要用它来运行 Linux 容器
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart

# 启用虚拟机平台
# WSL2 需要这个功能提供轻量级虚拟化能力
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart

# 安装或更新 WSL 组件
# 如果系统已经有 WSL，会更新；如果没有，会安装
wsl.exe --install --no-distribution

# 设置 WSL 默认版本为 2
# Docker Desktop 推荐使用 WSL2
wsl.exe --set-default-version 2
```

执行完成后需要重启电脑。

## 7. 重启后的下一步任务

重启电脑后，Claude 应先让用户或自动执行以下检查：

```bash
# 查看 Docker 版本，确认 Client 和 Server 都能正常输出
# 如果 Server 部分能显示，说明 Docker Engine 已连接成功
docker version

# 查看当前运行的容器
# 正常情况下即使没有容器，也应该输出表头而不是报错
docker ps

# 查看 WSL 状态
# 应能看到 WSL 默认版本为 2，或至少不再提示未安装 WSL
wsl.exe --status
```

如果 Docker 正常，再启动 Qdrant。

## 8. 下一阶段：启动 Qdrant 容器

Docker 恢复后执行：

```bash
# 创建 Docker 数据卷，用于持久化保存 Qdrant 的向量数据
# 即使容器删除，volume 中的数据也可以保留
docker volume create qdrant_storage

# 后台启动 Qdrant 容器
# -d 表示后台运行
# --name qdrant 给容器命名
# -p 6333:6333 暴露 REST API 端口
# -p 6334:6334 暴露 gRPC 端口
# -v qdrant_storage:/qdrant/storage 将数据保存到 Docker volume
docker run -d \
  --name qdrant \
  -p 6333:6333 \
  -p 6334:6334 \
  -v qdrant_storage:/qdrant/storage \
  qdrant/qdrant:latest
```

如果提示容器名已存在：

```bash
# 启动已存在的 Qdrant 容器
docker start qdrant
```

验证 Qdrant：

```bash
# 查看容器是否运行
docker ps

# 访问 Qdrant REST API
curl http://localhost:6333
```

正常返回类似：

```json
{
  "title": "qdrant - vector search engine",
  "version": "...",
  "commit": "..."
}
```

## 9. Qdrant 启动后的开发计划

Qdrant 成功后，继续推进：

1. 给 Spring Boot 项目添加 Qdrant 配置和依赖。
2. 创建 Qdrant collection，例如 `paper_chunks`。
3. 接入 Embedding 服务。
4. 实现：

```http
POST /api/papers/{id}/vectorize
```

用于将某篇论文的 chunks 向量化并写入 Qdrant。

5. 实现最小 RAG 问答接口：

```http
POST /api/rag/chat
```

流程：

```text
用户问题
→ 问题 embedding
→ Qdrant topK 检索
→ 根据 chunkId 回查 MySQL chunk 原文
→ 拼 prompt
→ 调用大模型
→ 返回 answer + sources
```

## 10. 后续可补的后端基础接口

在进入完整 RAG 前，也建议补充：

```http
GET /api/papers/{id}
GET /api/papers/{id}/chunks
GET /api/papers?page=1&size=10&keyword=xxx
```

理由：这些接口方便查看文献详情、检查 chunk 质量，并给后续前端页面使用。

## 11. 当前任务状态

当前状态：

```text
Qdrant 已通过 Docker 正常运行。
Spring Boot -> Qdrant 链路已打通。
假 embedding 版论文向量化已完成并验证成功。
Embedding 抽象服务已完成并接入 QdrantService。
Embedding 向量维度已统一由 EmbeddingService 提供。
最小向量检索链路已完成。
RAG sources 检索链路已完成。
最小 RAG 问答接口骨架已完成并验证成功。
RAG Prompt 构造结构已完成并验证成功。
LlmService 抽象层和占位实现已完成并验证成功。
DeepSeek 真实大模型调用已接入并验证成功。
LlmService 已扩展为 provider-neutral 结构，DeepSeek、Qwen、Zhipu 均可通过配置切换并调用成功。
真实 Qwen Embedding 已接入并验证成功，paper_chunks collection 已重建为真实向量维度。
RAG 已完成真实 embedding 检索 + 真实大模型回答链路验证。
对话历史模块已完成最小闭环：会话创建、会话列表、消息列表、RAG 问答落库均已验证通过。
Research Idea 管理模块已完成最小闭环：创建、列表、关键词搜索、详情、更新、删除均已验证通过。
跨语言 Query Rewrite 已完成并验证，中文问题检索英文 chunk 的 score 约提升 5%。
跨语言双路召回已完成并验证：原始问题与英文改写 query 分别召回，按 chunkId 去重并按 score 返回 topK。
RAG Prompt 压缩与 sources 去噪已完成并验证：source 内容裁剪、低价值 chunk 过滤、总长度上限和 retrievalRoute 展示均已生效。
Research Idea 草稿预览接口已完成并通过 Apifox 验证：可根据 sessionId 生成草稿且不自动入库。
RAG 回答主动建议保留 Idea 能力已完成并通过 Apifox 验证：/api/rag/chat 可返回 suggestSaveAsIdea 和 ideaSuggestionReason。
RAG 对话一键保存 Research Idea 接口已完成并通过 Apifox 验证：可根据 sessionId 生成草稿并保存为 draft。
Research Idea 一键保存最小去重已完成并通过 Apifox 验证：同一 sourceSessionId 重复保存时直接返回已有 rag_chat Idea。
Research Idea 按来源会话查询接口已完成并通过 Apifox 验证：可根据 sessionId 查询已保存的 rag_chat Idea。
Research Idea 列表组合筛选已完成并通过单元测试：GET /api/research-ideas 支持 keyword、sourceType、saveType、sourceSessionId。
Research Idea 四状态 saveType 流转接口已完成并通过单元测试：支持 draft、idea、todo、implemented。
Research Idea saveType 状态统计接口已完成并通过单元测试：可统计 draft、idea、todo、implemented 和 total 数量。
前端 Vue MVP 项目骨架已创建：使用 Vue 3、Vite 5、Axios、Element Plus、Vue Router 4，项目位于 frontend/research-assistant-frontend。
前端文献管理页已接入真实后端接口：支持文献列表加载、PDF 上传、解析触发和向量化触发。
前端 RAG 问答页已接入真实后端接口：支持发送问题、展示 answer/sources/model/session 信息，并可将会话保存为 Research Idea 草稿。
前端已完成 Research Desk 视觉改造：全局外壳、文献页、RAG 问答页和 Research Idea 页统一为深蓝纸张风研究工作台样式。
前端 Research Idea 页已接入真实后端接口：支持列表加载、关键词/状态筛选、状态统计、saveType 流转、详情查看和删除。
Research Idea saveType 更新的浏览器 Network Error 已定位并修复：后端 CORS 配置原本未允许 PATCH，现已补充 PATCH；前端状态推进入口按用户偏好保留在操作栏。
前端对话历史页已接入真实后端接口：支持会话列表、消息列表、sourcesJson 查看，并可从历史会话跳转回 RAG 问答继续追问。
前端已完成稳定工作台 UI 重构：应用外壳固定为 100vh，页面改为紧凑工具栏 + 内部滚动面板，侧边栏补充工作流和阶段信息，文案改为更贴近论文研究流程。
前端论文问答页已改为 session-based 会话工作台：会话列表、当前会话消息、继续提问、最新引用片段和保存研究想法集中在 /chat 页面；侧边栏不再单独展示对话历史入口。
前端已完成 Dashboard + 模块侧边栏布局改造：首页聚合统计和最近活动；对话、文献、想法页采用模块侧边栏；文献上传改为弹窗入口，页面主区域更聚焦。
前端页面重设计已完成并验证：全局保留顶部导航，首页聚焦概览与最近活动，文献/想法页使用平衡型模块侧边栏，问答页改为主流网页 AI 式会话布局，左侧显示新建会话和所有会话标题，保存 Research Idea 位于当前会话内容区，右侧参考论文/引用片段按需打开且参考论文支持多选。
对话会话删除能力已完成：新增 DELETE /api/chat/sessions/{sessionId}，删除会话时先删除该会话下的 chat_message，再删除 chat_session；前端问答页左侧会话列表新增删除入口，删除当前会话后自动切换到下一条或清空当前聊天区。
选中文献限定 RAG 检索范围已完成：/api/rag/sources 支持 paperIds 查询参数，/api/rag/chat 支持 paperIds 请求体字段；前端问答页会把右侧多选参考论文作为 paperIds 传给后端，后端通过 Qdrant payload filter 只检索指定论文 chunks。
项目已初始化 Git 仓库，根目录 .gitignore 已补充 Java/Spring Boot、Vue/Vite、本地数据、IDE 配置和 Claude 本地状态忽略规则；首次 commit 已由用户确认执行，用于保存当前项目版本。
文献分类文件夹功能已进入设计阶段：用户确认采用“用户自定义分类 + 一篇文献只属于一个分类 + 删除分类后文献转入未分类 + 上传后可修改分类”的方案；实现前需先完成设计文档、实施计划和分阶段进度记录。
文献分类功能进入阶段 1：新增 paper_category 数据表、paper_reference.category_id 字段、分类实体/Mapper/DTO，为分类增删改查打基础。
文献分类功能阶段 1 已完成：已新增 paper_category 数据表设计、默认“未分类”种子数据、paper_reference.category_id、分类实体/Mapper/DTO/Service/Controller，并通过 PaperCategoryServiceImplTest（4 个测试）验证分类创建校验、重复名称校验、系统分类删除保护和删除分类转入未分类逻辑。
文献分类功能进入阶段 2：增强文献上传、文献列表和文献分类修改接口，使文献可归入用户自定义分类。
文献分类功能阶段 2 已完成：/api/papers 支持 categoryId 分类筛选，/api/papers/upload 支持上传时选择分类，新增 PATCH /api/papers/{id}/category 修改文献分类，文献返回 categoryId/categoryName；PaperReferenceServiceImplTest（4 个测试）已通过，验证默认未分类、指定分类校验、缺失文献拒绝和修改分类不影响解析/向量状态。
文献分类功能进入阶段 3：前端接入分类 API helper，为文献页分类文件夹管理做准备。
文献分类功能阶段 3 已完成：新增前端 paperCategories API helper 与测试，papers API 支持 categoryId 查询和 PATCH 修改分类；文献页已接入分类文件夹管理、新建/编辑/删除分类、上传选择分类、分类筛选、分类标签展示和单篇文献改分类。已通过 node --test src/api/papers.test.js src/api/paperCategories.test.js（10 个测试）和 npm run build，Vite 仅提示第三方 PURE 注释与 chunk 大小警告。
文献分类功能进入阶段 4：增强 Dashboard 和论文问答页的分类展示，使文献分类在主要使用场景中可见。
文献分类功能阶段 4 已完成：Dashboard 已加载 paperCategories 并展示文献分类概览，论文问答页右侧参考论文列表已展示 categoryName/未分类。已通过 node --test src/api/rag.test.js src/views/ragChatState.test.js src/api/paperCategories.test.js（13 个测试）和 npm run build，Vite 仅提示第三方 PURE 注释与 chunk 大小警告。
文献分类功能进入阶段 5：进行后端、前端和人工流程的整体回归验证，准备提交功能版本。
文献分类文件夹功能已完成：支持用户自定义分类、上传选择分类、文献列表分类筛选、上传后修改分类、删除分类转入未分类，并已贯通文献页、Dashboard 和论文问答参考论文展示。代码审查反馈已修复：删除分类加事务、修改文献分类改为只更新 category_id、PATCH 空请求拒绝、迁移脚本补强默认未分类升级、旧数据修复、category_id 非空和外键约束。最终验证通过：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（21 个测试，0 失败）；前端 node --test src/api/papers.test.js src/api/paperCategories.test.js src/api/rag.test.js src/views/ragChatState.test.js 通过（19 个测试，0 失败），npm run build 通过，Vite 仅提示第三方 PURE 注释与 chunk 大小警告。
文献页一直加载问题已定位并修复：本地 MySQL 已执行 docs/database/migrations/literature-category.sql，补齐 paper_reference.category_id 并把旧文献迁移到“未分类”；已验证 GET /api/paper-categories 和 GET /api/papers 均返回 200。前端 API 拦截器已补充 Result.code 非 200 时抛错，避免后端业务失败对象被当作列表渲染；已通过 node --test src/api/papers.test.js src/api/paperCategories.test.js（10 个测试）和 npm run build。
```

已完成接口：

```http
GET /api/qdrant/health
POST /api/qdrant/collections/paper-chunks
GET /api/qdrant/search
GET /api/rag/sources
GET /api/papers/{id}
GET /api/papers/{id}/chunks
POST /api/papers/{id}/vectorize
POST /api/rag/chat
POST /api/research-ideas/draft-from-session/{sessionId}
POST /api/research-ideas/save-draft-from-session/{sessionId}
GET /api/research-ideas/by-session/{sessionId}
GET /api/research-ideas?keyword=xxx&sourceType=rag_chat&saveType=draft&sourceSessionId=123
PATCH /api/research-ideas/{id}/save-type
GET /api/research-ideas/stats/save-type
DELETE /api/chat/sessions/{sessionId}
GET /api/rag/sources?question=xxx&topK=5&paperIds=1,2,3
POST /api/rag/chat  # 请求体支持 paperIds: [1,2,3]
```

本次模块进展：

```text
前端 Vue MVP 项目骨架已完成。
新增 frontend/research-assistant-frontend，采用 Vue 3 + Vite 5 + Axios + Element Plus + Vue Router 4。
前端文献管理页已接入真实后端接口，可加载文献列表、上传 PDF、触发解析和触发向量化。
前端 RAG 问答页已接入真实后端接口，可发送问题、展示回答和来源片段，并支持一键保存 Research Idea 草稿。
前端已完成 Research Desk 深蓝纸张风视觉改造，降低默认 Element Plus 后台感，增强论文研究工作台氛围。
前端 Research Idea 页已接入真实后端接口，可加载 Idea 列表、按关键词和 saveType 筛选、展示状态统计、推进状态、查看详情并删除记录。
Research Idea saveType 更新的浏览器 Network Error 已修复：后端 CORS 允许方法补充 PATCH；前端状态推进入口按用户偏好放回操作栏下拉按钮。
前端对话历史页已完成：新增 History / 对话历史导航，可查看会话列表、消息记录、assistant sourcesJson，并支持继续已有 RAG session。
前端已完成稳定工作台 UI 重构：App 外壳固定高度，文献库、论文问答、对话历史、研究想法页统一改为紧凑工具栏和固定面板布局，长表格、长回答、长消息列表改为面板内部滚动。
前端论文问答页已完成会话式重构：/chat 页面直接展示会话列表、当前会话完整消息、继续提问输入框和本轮引用片段，避免问答与历史割裂。
前端已完成 Dashboard + 模块侧边栏布局改造：首页改为统计与最近活动聚合页，文献上传改为弹窗，文献和想法页以侧边栏承载筛选与操作入口，整体视觉改为更简洁的浅色 dashboard 风格。
前端页面重设计已完成：全局保留顶部导航；首页只展示概览统计、最近会话、最近想法和模块入口；问答页改为左侧会话列表 + 中间会话内容 + 按需右侧上下文面板，参考论文支持点击多选。
对话会话删除能力已完成：后端提供 DELETE /api/chat/sessions/{sessionId}，前端问答页会话列表支持删除会话并刷新当前会话状态。
选中文献限定 RAG 检索范围已完成：前端问答页多选参考论文后会随 /api/rag/chat 传入 paperIds；后端 retrieval 和 Qdrant 检索均使用 paperIds 作为文献范围过滤条件，同时兼容旧 paperId 单篇问答请求。
Git 管理初始化已完成：项目根目录已执行 git init，并补充根目录 .gitignore，避免提交依赖、构建产物、本地论文数据和 Claude 本地状态文件。
```

本次验证：

```text
用户已完成 DeepSeek、Qwen、Zhipu 调用测试，均可通过 /api/rag/chat 返回真实模型回答。
用户已完成 Qwen Embedding 接入测试，编译、启动、collection 重建、论文 vectorize、sources 检索和 RAG 问答均通过。
Claude 已完成对话历史 API 验证：创建会话、查询会话、RAG 写入已有会话、RAG 自动创建会话、查询消息均通过。
用户已完成 Research Idea API 验证：创建、列表、关键词搜索、详情、更新、删除均通过。
用户已完成跨语言 Query Rewrite 验证，中文问题检索英文 chunk 的 score 约提升 5%。
用户已完成双路召回测试，/api/rag/sources 与 /api/rag/chat 可正常返回双路合并后的 sources。
用户已完成 RAG Prompt 压缩与 sources 去噪测试，/api/rag/chat 可正常返回，prompt 已明显变短并包含 retrievalRoute。
用户已使用 Apifox 完成 Research Idea 草稿预览接口验证：可根据 sessionId 生成草稿，且未自动新增 research_idea 记录。
用户已使用 Apifox 完成 RAG 回答主动建议保留 Idea 字段验证：suggestSaveAsIdea 和 ideaSuggestionReason 可正常返回。
Claude 已完成 RAG 对话一键保存 Research Idea 的单元测试验证：ResearchIdeaServiceImplTest 通过，确认 session 草稿可保存为 draft。
用户已使用 Apifox 完成 RAG 对话一键保存 Research Idea 接口验证：可根据 sessionId 保存为 research_idea 草稿记录。
Claude 已完成 Research Idea 一键保存最小去重单元测试验证：重复保存同一 sessionId 时返回已有记录，且不调用 LLM、不插入新记录。
用户已使用 Apifox 完成 Research Idea 一键保存最小去重验证：同一 sessionId 连续保存返回相同 id，列表无重复记录。
Claude 已完成 Research Idea 按来源会话查询接口单元测试验证：getBySourceSessionId 可返回指定 sessionId 对应的 rag_chat Idea。
用户已使用 Apifox 完成 Research Idea 按来源会话查询接口验证：已保存 sessionId 可返回 Idea，未保存 sessionId 返回 null。
Claude 已完成 Research Idea 列表组合筛选单元测试验证：list 可接收 keyword、sourceType、saveType、sourceSessionId 并正常查询。
Claude 已完成 Research Idea 四状态 saveType 流转单元测试验证：可更新为 implemented，并会拒绝非法 saveType。
Claude 已完成 Research Idea saveType 状态统计单元测试验证：draft、idea、todo、implemented 和 total 数量计算正确。
Claude 已完成前端 Vue MVP 项目骨架验证：npm run build 通过，npm run dev 启动成功，首页 http://127.0.0.1:5173/ 返回 200。
Claude 已完成前端文献管理页 API helper 测试：node --test src/api/papers.test.js 通过，4 个测试全部通过。
Claude 已完成前端文献管理页构建验证：npm run build 通过；Vite 仅提示 chunk 大小警告，不影响构建成功。
Claude 已完成前端 RAG API helper 测试：node --test src/api/rag.test.js 通过，2 个测试全部通过。
Claude 已完成前端 RAG 问答页构建验证：npm run build 通过；Vite 仅提示 chunk 大小警告，不影响构建成功。
Claude 已完成 Research Desk 视觉改造构建验证：npm run build 通过；Vite 仅提示 chunk 大小警告，不影响构建成功。
Claude 已完成前端 Research Idea API helper 测试：node --test src/api/researchIdeas.test.js 通过，5 个测试全部通过。
Claude 已完成前端 Research Idea 页真实接口接入构建验证：npm run build 通过；Vite 仅提示第三方 PURE 注释和 chunk 大小警告，不影响构建成功。
Claude 已完成 Research Idea saveType 推进交互修复验证：node --test src/api/researchIdeas.test.js 通过，6 个测试全部通过；npm run build 通过，Vite 仅提示第三方 PURE 注释和 chunk 大小警告。
Claude 已完成后端 CORS PATCH 配置验证：./mvnw -Dtest=WebConfigTest test 通过，确认 /api/** 跨域预检允许 PATCH。
Claude 已完成前端 Chat History API helper 测试：node --test src/api/chatHistory.test.js 通过，3 个测试全部通过。
Claude 已完成前端对话历史页构建验证：npm run build 通过；Vite 仅提示第三方 PURE 注释和 chunk 大小警告。
Claude 已完成稳定工作台 UI 重构验证：node --test src/api/papers.test.js、src/api/rag.test.js、src/api/researchIdeas.test.js、src/api/chatHistory.test.js 均通过；npm run build 通过，Vite 仅提示第三方 PURE 注释和 chunk 大小警告。
Claude 已完成 session-based 论文问答页验证：node --test src/api/rag.test.js 和 node --test src/api/chatHistory.test.js 均通过；npm run build 通过，Vite 仅提示第三方 PURE 注释和 chunk 大小警告。
Claude 已完成 Dashboard + 模块侧边栏布局改造验证：node --test src/api/papers.test.js、src/api/rag.test.js、src/api/researchIdeas.test.js、src/api/chatHistory.test.js 均通过；npm run build 通过，Vite 仅提示第三方 PURE 注释和 chunk 大小警告。
Claude 已完成前端页面重设计验证：node --test src/api/papers.test.js src/api/rag.test.js src/api/researchIdeas.test.js src/api/chatHistory.test.js src/views/ragChatState.test.js 通过，19 个测试全部通过；npm run build 通过；Vite dev server 启动成功，/、/papers、/chat、/ideas 路由均返回 200 且 SPA app shell 正常。
用户已完成人工验收：前端页面重设计效果验收通过，可进入下一阶段。
Claude 已完成对话会话删除能力验证：ChatHistoryServiceImplTest 通过，确认删除会话会先删除消息再删除会话，且会拒绝不存在的 sessionId；前端 chatHistory.test.js 通过，确认 deleteChatSession 调用 DELETE /api/chat/sessions/{sessionId}；npm run build 通过。
Claude 已完成选中文献限定 RAG 检索范围验证：RagChatServiceImplTest 和 RagRetrievalServiceImplTest 通过，确认 paperIds 会覆盖旧 paperId 并传入 retrieval/Qdrant 检索；前端 ragChatState.test.js 通过，确认多选参考论文会构造成 paperIds；后端 ./mvnw test 通过，前端 npm run build 通过。
Claude 已完成 Git 初始化验证：git status --short --ignored 确认 .claude/、.superpowers/、ResearchAssistantData/、node_modules/、dist/、target/、根目录 test/ 均被忽略；git check-ignore 确认后端 src/test 测试源码未被误忽略。
```

当前注意点：

```text
项目定位是论文/文献 AI 研究助手，应围绕文献上传、解析、chunk、向量化、检索、RAG 问答、对话历史和 Research Idea 管理推进，不要偏离成通用聊天机器人。
命令行默认 JAVA_HOME 仍指向 JDK 8，编译 Spring Boot 项目需要切到 JDK 21。
本次使用 C:/path/to/jdk-21 编译通过。
当前 paper_chunks collection 已切换为真实 Qwen Embedding 向量维度；如果后续更换 embedding 模型或维度，需要删除并重建 collection，再重新 vectorize 论文。
Claude Bash 环境直接发送中文 JSON 时出现过 UTF-8 编码问题；用户 PowerShell 端可继续用 UTF-8 JSON 验证中文问题。
当前系统 Node.js 为 v21.5.0，新版 create-vite 与该版本不兼容；本次使用 create-vite@5.5.5 和 Vite 5 创建前端项目，vue-router 需使用 4.x。
RAG 后续优化问题和方案已记录到 docs/archive/notes/rag-improvement-notes.md，核心方向是 Adaptive Hybrid RAG：全文阈值、直接 PDF 阅读可选策略、文献数据模型与向量索引设计、文献画像/章节摘要、参考文献过滤、多路 Query Rewrite、rerank 和多篇比较型召回。
```

下一步任务：

```text
下一阶段进入“论文 PDF 预处理与结构化分块优化”，已形成设计文档 docs/archive/specs/2026-07-06-paper-preprocessing-structured-chunking-design.md 和实施计划 docs/archive/plans/2026-07-06-paper-preprocessing-structured-chunking.md。该阶段先处理新解析论文，不强制迁移旧论文；目标是新增 paper_section 章节层，增强 paper_chunk 的 sectionType、isReference、tokenCount、indexText、chunkStrategyVersion 等字段；解析时先清洗文本、识别章节和 References，再按章节/段落生成 chunk；向量化时使用 indexText，MySQL 保留真实 content，Qdrant payload 同步 sectionType、sectionId、isReference 等元数据；RAG 普通检索默认排除参考文献 chunk。
该阶段同步建立轻量 RAG 评测机制：先用 docs/evaluation/cases.md 维护 5~10 个固定评测问题，记录优化前后的 referenceHitRate、expectedSectionHitRate、paperHitRate、keyPointCoverage、answerCompletenessScore 等指标，后续每次 RAG 改进都用同一批问题量化对比效果。实施计划已拆为 6 个阶段：数据库和实体基础、文本清洗与章节识别、结构化分块与解析接入、向量化 payload 与 RAG 过滤、轻量评测基线、最终验证与提交。
论文 PDF 预处理与结构化分块优化进入阶段 1：扩展数据库结构和后端实体，新增 paper_section 章节层，并增强 paper_chunk 的章节、参考文献、索引文本和分块策略字段。
论文 PDF 预处理与结构化分块优化阶段 1 已完成：新增 paper_section 数据模型和 Mapper，增强 paper_chunk 章节、参考文献、索引文本和分块策略字段，数据库初始化脚本和迁移脚本已补充；后端 JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile 编译通过。
论文 PDF 预处理与结构化分块优化进入阶段 2：实现 PDF 文本清洗、章节标题识别和 References 区域标记；章节类型采用“原始标题保留 + 标准 sectionType 推断 + UNKNOWN 兜底”，并加入内容关键词辅助推断，避免只靠固定标题硬匹配。
论文 PDF 预处理与结构化分块优化阶段 2 已完成：新增 PDF 文本清洗器和规则化章节识别器，可识别 Abstract、Introduction、Method、Experiment、References 等章节；章节类型支持标题规则优先、内容关键词辅助推断和 UNKNOWN 兜底；新增 BACK_MATTER 标记结论后的 Author Contributions、Conflict of Interest、Data Availability、Funding 等低价值后置内容；相关单元测试 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperTextCleanerTest,PaperSectionDetectorTest test 通过（8 个测试，0 失败）。
论文 PDF 预处理与结构化分块优化进入阶段 3：实现章节内结构化分块，并把文献解析流程从固定长度切片切换为 paper_section + paper_chunk 入库。
论文 PDF 预处理与结构化分块优化阶段 3 已完成：新增 StructuredChunk 和 StructuredChunkingService，文献解析流程已由固定长度切片升级为结构化分块，解析时会生成 paper_section 章节记录，并写入带 sectionType、isReference、isNoise、indexText、tokenCount 和 chunkStrategyVersion 的 paper_chunk；BACK_MATTER 后置内容会标记为低质量噪声；相关单元测试 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=StructuredChunkingServiceTest,PaperReferenceServiceImplTest test 通过（9 个测试，0 失败）。
论文 PDF 预处理与结构化分块优化进入阶段 4：向量化改用 indexText，Qdrant payload 写入章节元数据，RAG 检索默认过滤参考文献和噪声 chunk。
论文 PDF 预处理与结构化分块优化阶段 4 已完成：向量化写入 Qdrant 时优先使用 indexText，并在 payload 中写入 sectionId、sectionType、sectionTitle、isReference、isNoise、contentType 和 chunkStrategyVersion；RAG 检索改为扩大召回后回查 MySQL 过滤参考文献/噪声 chunk，兼容旧 Qdrant payload；sources 已可返回章节信息。相关单元测试 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=QdrantServiceTest,RagRetrievalServiceImplTest test 通过（3 个测试，0 失败）。
论文 PDF 预处理与结构化分块优化进入阶段 5：建立轻量 RAG 评测基线，用固定问题和指标量化后续召回与回答质量改进。
论文 PDF 预处理与结构化分块优化阶段 5 已完成：已建立轻量 RAG 评测文档 docs/evaluation/cases.md，固定单篇方法、实验、创新点、局限、参考文献干扰、后置低价值内容干扰和多篇比较等用例，并定义 referenceHitRate、expectedSectionHitRate、paperHitRate、topKUsefulRate、keyPointCoverage、citationSupportRate、answerCompletenessScore、answerNoiseScore 等指标，后续 RAG 改进可按同一批问题量化对比。
新增 PDF 后解析失败问题已定位并修复：根因是后端已切换到结构化分块解析流程，但本地 MySQL 尚未执行 docs/database/migrations/paper-structured-chunking.sql，导致解析时报 paper_section 表不存在。已执行结构化分块迁移，删除并重建 Qdrant paper_chunks collection（1024 维 Cosine），确认 points_count 从 0 开始；重新解析文献 id=7 成功生成 80 个结构化 chunk，并完成向量化写入 Qdrant，paper_reference 状态为 parse_status=COMPLETED、vector_status=COMPLETED。
论文 PDF 预处理与结构化分块优化进入阶段 6：进行后端整体测试、数据库/Qdrant 状态检查和功能状态汇总，准备提交阶段版本；由于旧 MySQL 数据和 Qdrant collection 已清空，后续 RAG 评测以当前 paper-structure-v1 作为新 baseline，旧固定切片对比后续可通过 legacy-fixed-window-v1 离线实验单独补充。
论文 PDF 预处理与结构化分块优化已完成阶段性实现：新增 paper_section 章节层，paper_chunk 已增强 sectionType、isReference、isNoise、indexText、tokenCount、chunkStrategyVersion 等字段；PDF 解析流程已升级为文本清洗、章节识别、References/BACK_MATTER 标记和章节内结构化分块；向量化使用 indexText，Qdrant payload 携带章节元数据；RAG 检索默认过滤参考文献/噪声 chunk；轻量 RAG 评测文档 docs/evaluation/cases.md 已建立。最终验证：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（36 个测试，0 失败）；本地 MySQL 已执行 docs/database/migrations/paper-structured-chunking.sql，文献 id=7 解析生成 61 个 paper_section 和 80 个 paper_chunk；Qdrant paper_chunks 已重建为 1024 维 Cosine collection 并写入 80 个 points；GET /api/rag/sources?question=这篇论文的核心方法是什么？&topK=5&paperIds=7 返回 200，sources 携带 sectionType/isReference/isNoise/chunkStrategyVersion 且本次 topK 未包含 reference/noise。用户体验反馈和下一步改进已记录到 docs/archive/notes/rag-improvement-notes.md：本次结构化分块相比旧固定切片主要解决“论文创新点/论文讲什么”等问题召回参考文献导致无法回答的问题；当前仍存在整篇宏观问题只看部分 chunk 不够完整的限制，下一步建议进入单篇/少数论文 FULL_TEXT_PARSED，上下文按章节组织全篇内容并默认排除 REFERENCES/BACK_MATTER。
单篇论文 FULL_TEXT_PARSED 阶段进入设计：已形成设计文档 docs/archive/specs/2026-07-06-single-paper-full-text-context-design.md，范围限定为单篇论文在 token 预算内时使用按章节组织的全文解析上下文，其他情况回退 VECTOR_RAG；该阶段需记录实施步骤、验证结果、相对上一版本的改进效果和当前遗留问题，并在完成后提交 Git 版本。
单篇论文 FULL_TEXT_PARSED 已形成实施计划 docs/archive/plans/2026-07-06-single-paper-full-text-context.md，计划拆为 4 个阶段：上下文策略基础、全文上下文构造、RAG prompt/response 接入、最终验证与改进笔记/Git 提交。
单篇论文 FULL_TEXT_PARSED 进入阶段 1：新增上下文策略基础，支持根据单篇论文范围、解析状态和 token 预算自动选择 FULL_TEXT_PARSED 或回退 VECTOR_RAG；本次大任务完成前不再中途提交 Git，完成后统一提交版本。
单篇论文 FULL_TEXT_PARSED 阶段 1 已完成：新增 ContextStrategy 和 ContextStrategyService，可在单篇已解析论文且 token 不超预算时选择 FULL_TEXT_PARSED，其余情况回退 VECTOR_RAG；相关单元测试 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=ContextStrategyServiceImplTest test 通过（4 个测试，0 失败）。
单篇论文 FULL_TEXT_PARSED 进入阶段 2：实现 FullTextContextService，从 MySQL 按章节组织单篇论文正文上下文，并默认排除 REFERENCES、BACK_MATTER、reference/noise chunk。
单篇论文 FULL_TEXT_PARSED 阶段 2 已完成：新增 FullTextContextService，可按章节组织单篇论文正文上下文，默认排除 REFERENCES、BACK_MATTER、reference/noise chunk，并返回 full_text sources；相关单元测试 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=FullTextContextServiceImplTest test 通过（1 个测试，0 失败）。
单篇论文 FULL_TEXT_PARSED 进入阶段 3：接入 RAG 问答流程，新增全文上下文 prompt 和响应中的 contextStrategy/contextTokenCount/contextPaperIds 调试字段。
单篇论文 FULL_TEXT_PARSED 阶段 3 已完成：RAG 问答已接入上下文策略，单篇论文在预算内会使用按章节组织的全文解析上下文，响应返回 contextStrategy、contextTokenCount、contextPaperIds；VECTOR_RAG 分支保持多选文献检索兼容。相关单元测试 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=RagChatServiceImplTest,RagPromptServiceImplTest test 通过（4 个测试，0 失败）。
单篇论文 FULL_TEXT_PARSED 进入阶段 4：进行后端整体测试、接口验证、改进效果记录和 Git 版本提交。
单篇论文 FULL_TEXT_PARSED 已完成：当用户选择 1 篇已解析论文且正文 token 不超预算时，RAG 问答会使用按章节组织的全文解析上下文；否则回退 VECTOR_RAG。响应已返回 contextStrategy、contextTokenCount、contextPaperIds。最终验证：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（44 个测试，0 失败）；/api/rag/chat 单篇论文验证返回 contextStrategy=FULL_TEXT_PARSED、contextTokenCount=17576、sourceCount=20，answer 正常生成；多篇 paperIds=[7,8] 探测按预期回退 VECTOR_RAG。改进效果和当前遗留问题已记录到 docs/archive/notes/rag-improvement-notes.md。
下一阶段建议进入多篇/长论文 HYBRID_RAG 设计：为多篇比较、超预算长论文和章节摘要/文献画像能力提供上下文组织方案。
文献画像与章节摘要基础层进入设计：用户确认优先走更利于长期全面改进的路线，先沉淀 paper_section_summary 与 paper_profile，再作为后续 HYBRID_RAG、多篇比较、Research Idea 提炼和 Dashboard 展示的复用基础；设计文档已形成 docs/archive/specs/2026-07-07-paper-profile-section-summary-design.md。
文献画像与章节摘要基础层已形成实施计划 docs/archive/plans/2026-07-07-paper-profile-section-summary.md，计划拆为 4 个阶段：数据库和实体基础、章节摘要与文献画像生成服务、手动生成/查询接口、整体测试与接口验证。
文献画像与章节摘要基础层进入阶段 1：新增 paper_section_summary 和 paper_profile 数据表、实体、Mapper 和响应 DTO，为手动生成章节摘要和整篇画像打基础。
文献画像与章节摘要基础层阶段 1 已完成：数据库全量脚本与迁移脚本已补充，后端实体、Mapper 和 DTO 已新增；JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile 编译通过。
文献画像与章节摘要基础层进入阶段 2：实现 PaperProfileService，完成有效 chunk 过滤、章节摘要生成、文献画像生成和同版本 upsert。
文献画像与章节摘要基础层阶段 2 已完成：新增 PaperProfileService，支持校验已解析论文、过滤参考文献/噪声 chunk、按章节生成摘要、汇总章节摘要生成文献画像，并按固定版本 upsert 保存；PaperProfileServiceImplTest 通过（4 个测试，0 失败），验证未解析拒绝、无有效正文拒绝、过滤 reference/noise 和查询空画像。
文献画像与章节摘要基础层进入阶段 3：新增手动生成和查询文献画像接口，并补充接口层编译验证。
文献画像与章节摘要基础层阶段 3 已完成：PaperController 新增 POST /api/papers/{id}/profile 手动生成/更新画像和 GET /api/papers/{id}/profile 查询画像接口，返回统一 Result<PaperProfileResult>；后端编译和 PaperProfileServiceImplTest 通过。
文献画像与章节摘要基础层进入阶段 4：执行数据库迁移、整体测试、Apifox 接口验证建议和后续 HYBRID_RAG 衔接记录。
文献画像与章节摘要基础层已完成阶段性实现：新增 paper_section_summary 章节摘要表和 paper_profile 文献画像表；新增 PaperProfileService，可从已解析论文中排除 REFERENCES/BACK_MATTER/reference/noise chunk，按章节生成中文摘要和关键点，再汇总生成研究问题、方法概述、实验评估、主要贡献、局限性、关键词和 RAG 画像文本；新增 POST /api/papers/{id}/profile 和 GET /api/papers/{id}/profile。最终验证：本地 MySQL 已执行 docs/database/migrations/paper-profile-section-summary.sql；后端 JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（48 个测试，0 失败）；使用 llm.provider=fake 启动后端后，GET /api/papers/7/profile 初始返回空画像，POST /api/papers/7/profile 返回 code=200、profileVersion=paper-profile-v1 和 51 条 section-summary-v1 章节摘要，重复 POST 后 profile id 保持 1，数据库 paper_profile 为 1 条、paper_section_summary 为 51 条，确认同版本 upsert 生效。用户已完成真实 Qwen 验证，POST/GET /api/papers/{id}/profile 可正常生成和查询真实模型输出的文献画像与章节摘要；未配置 DASHSCOPE_API_KEY 时接口会返回“Qwen API Key 未配置”。下一阶段建议进入 HYBRID_RAG 设计：让多篇/长论文问答组合使用 paper_profile、section_summary 和少量 raw chunks。
多篇论文 HYBRID_RAG 已形成设计文档 docs/archive/specs/2026-07-07-hybrid-rag-design.md；第一版聚焦用户选择 2 篇及以上论文后的比较型/分析型问答，采用规则型方案复用 paper_profile、paper_section_summary 和少量 raw chunks，不新增 Qdrant 多类型索引，不自动生成缺失画像；后续进入实施计划并直接改代码，期间每个实质阶段继续更新本进度文档。
多篇论文 HYBRID_RAG 已形成实施计划 docs/archive/plans/2026-07-08-hybrid-rag.md，计划拆为 4 个阶段：策略枚举和选择规则、HybridRagContextService 上下文构造、RAG chat/prompt 接入、整体测试与 Apifox 验证。
多篇论文 HYBRID_RAG 进入阶段 1：扩展 ContextStrategy 和 ContextStrategyService，使多篇且 paper-profile-v1 画像齐全时选择 HYBRID_RAG，任一画像缺失时回退 VECTOR_RAG。
多篇论文 HYBRID_RAG 阶段 1 已完成：新增 ContextStrategy.HYBRID_RAG，ContextStrategyService 支持多篇画像齐全选择 HYBRID_RAG、任一画像缺失回退 VECTOR_RAG；相关单元测试 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=ContextStrategyServiceImplTest test 通过（5 个测试，0 失败）。
多篇论文 HYBRID_RAG 进入阶段 2：实现 HybridRagContextService，上下文按论文组织 paper_profile、section_summary 和少量 raw chunks。
多篇论文 HYBRID_RAG 阶段 2 已完成：新增 HybridRagContextService 和上下文 DTO，RagSource 扩展 hybrid 来源元数据；上下文构造可按论文顺序组织 paper_profile、按问题优先选择 section_summary，并按论文限制 raw chunks 数量；相关单元测试 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=HybridRagContextServiceImplTest test 通过（3 个测试，0 失败）。
多篇论文 HYBRID_RAG 进入阶段 3：接入 RAG chat 和 prompt，使 /api/rag/chat 在 HYBRID_RAG 策略下使用混合上下文生成回答。
多篇论文 HYBRID_RAG 阶段 3 已完成：RAG chat 已新增 HYBRID_RAG 分支，会先按 paperIds 检索少量 raw chunks，再用 HybridRagContextService 组合 paper_profile、section_summary 和 raw_chunk 构造上下文；RagPromptService 已新增多篇混合上下文 prompt，响应会返回 contextStrategy=HYBRID_RAG、contextTokenCount、contextPaperIds 和 hybrid sources。相关单元测试 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=RagPromptServiceImplTest,RagChatServiceImplTest test 通过（6 个测试，0 失败）。
多篇论文 HYBRID_RAG 进入阶段 4：执行后端整体测试、编译验证，并整理 Apifox 多篇比较问答和画像缺失回退验证步骤。
多篇论文 HYBRID_RAG 阶段 4 后端验证已完成：JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（54 个测试，0 失败），JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile 编译通过。下一步建议由用户使用 Apifox 验证：先确保至少两篇文献已生成 paper-profile-v1，再请求 POST /api/rag/chat 且 paperIds 选择这两篇，预期返回 contextStrategy=HYBRID_RAG、contextPaperIds、contextTokenCount，以及 sourceType=paper_profile/section_summary/raw_chunk 的 sources；再选择任一缺失画像的论文组合，确认不会自动生成画像并按预期回退 VECTOR_RAG 或返回现有文献校验错误。
多篇论文 HYBRID_RAG 相较上一版本的具体改进措施与实现方式：上一版本 FULL_TEXT_PARSED 主要解决“单篇论文、正文 token 不超预算”时的全文理解问题；当用户选择多篇论文进行比较/分析，或单篇论文过长超出预算时，系统仍主要回退 VECTOR_RAG，只能依赖向量检索召回的少量 raw chunks，容易缺少每篇论文的整体研究问题、方法、贡献、局限和章节级上下文。当前版本新增 HYBRID_RAG 策略：第一，在 ContextStrategy 中新增 HYBRID_RAG 枚举，并在 ContextStrategyService 中实现选择规则——当 paperIds 为多篇且每篇都有 paper-profile-v1 画像时自动选择 HYBRID_RAG，任一画像缺失时回退 VECTOR_RAG，避免问答接口里临时生成画像导致响应过慢或失败；第二，新增 HybridRagContextService 和上下文 DTO，按用户选择的论文顺序组织上下文，每篇论文先放 paper_profile，提供研究问题、方法概述、实验评估、主要贡献、局限性和关键词等全文级信息，再按问题关键词优先选择相关 paper_section_summary，补充章节级摘要，最后为每篇论文附加少量向量召回 raw_chunk 作为原文证据；第三，扩展 RagSource，使 sources 能标识 paper_profile、section_summary、raw_chunk 等 hybrid 来源元数据，便于前端/调试区分“全文画像信息、章节摘要信息、原文证据片段”；第四，在 RagChatService 中新增 HYBRID_RAG 分支，先按 paperIds 做少量 raw chunk 检索，再调用 HybridRagContextService 组合混合上下文，并把 contextStrategy、contextTokenCount、contextPaperIds 和 hybrid sources 写入响应；第五，在 RagPromptService 中新增多篇混合上下文 prompt，引导大模型按论文逐篇比较，优先利用 paper_profile 和 section_summary 建立全局判断，再用 raw_chunk 作为证据支撑。该版本的实际效果是：多篇论文比较型问题不再只靠零散 chunk 拼答案，而是形成“文献画像 + 章节摘要 + 原文证据”的三层上下文；既提升多篇比较的完整性和结构化程度，又保留可追溯证据；同时通过画像齐全才启用 HYBRID_RAG、画像缺失安全回退 VECTOR_RAG，保证兼容旧数据和未生成画像的论文。
多篇论文 HYBRID_RAG 当前版本准备提交：后端策略选择、混合上下文构造、prompt 接入、响应调试字段和单元测试已完成；人工接口验收仍建议按 Apifox 步骤补充验证。
2026-07-08 进入“文献画像前端入口 + 多篇 HYBRID_RAG 可用性修复”阶段：先按系统化调试排查数据库画像为空、多篇问答仍回退零散 VECTOR_RAG 或上下文不足的根因，再补齐前端文献画像生成/查看入口和问答页画像状态提示；后端优先保证画像查询能返回清晰空状态、手动生成画像接口可被前端稳定调用，多篇问答在画像缺失时给出可操作提示或引导生成画像，画像齐全时稳定启用 HYBRID_RAG。执行顺序：先确认画像数据链路与 HYBRID_RAG 选择条件，再补前端画像 API/操作入口与问答页状态展示，最后用后端测试、前端测试/构建和前端人工闭环验证。阶段验证目标：人工可在前端完成“选择论文 → 生成/查看画像 → 多篇问答”，并能明确看到画像缺失或 HYBRID_RAG 启用状态。
文献画像前端入口与多篇 HYBRID_RAG 可用性提示已完成：根因是后端已有 GET/POST /api/papers/{id}/profile，但前端缺少画像 API helper、文献页生成/查看入口和问答页多篇画像依赖提示；现已新增 getPaperProfile/generatePaperProfile，文献页新增“画像”按钮和画像弹窗，可查看研究问题、方法、实验、贡献、局限、关键词和章节摘要数量，也可手动生成/重新生成画像；问答页选择 2 篇及以上参考论文时会提示画像齐全才启用 HYBRID_RAG，并提供跳转文献页生成画像入口。
本阶段验证：前端 node --test src/api/papers.test.js src/api/rag.test.js src/views/ragChatState.test.js 通过（17 个测试，0 失败）；npm run build 通过，Vite 仅提示既有第三方 PURE 注释和 chunk 大小警告；后端 JAVA_HOME=/path/to/jdk-21 ./mvnw -DskipTests compile 通过。下一步建议启动前后端后进行人工闭环验收：在文献页为至少两篇已解析论文生成画像，再到问答页多选这两篇论文提问，确认返回 contextStrategy=HYBRID_RAG；若某篇缺失画像，应看到前端提示并按后端策略回退 VECTOR_RAG。
文献画像生成失败与“信息不足”问题已定位并修复：前端超时根因是画像生成会按章节多次调用大模型，原 30 秒默认超时过短；数据库大量“信息不足”的根因是后端只按精确中文全角冒号字段名解析 LLM 输出，遇到 Qwen 常见 Markdown 标题、半角冒号、序号/加粗字段名时解析失败并写入默认“信息不足”；前端画像弹窗还存在字段名与后端 DTO 不一致的问题。现已将 generatePaperProfile 单次请求超时放宽到 180 秒，PaperProfileServiceImpl 增强为可解析 Markdown 标题、半角/全角冒号、序号、列表和加粗字段名，文献页画像弹窗改用后端真实字段 researchProblem/methodSummary/experimentSummary/keyContributions/profileText。验证通过：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileServiceImplTest test 通过（5 个测试，0 失败），前端 node --test src/api/papers.test.js 通过（8 个测试，0 失败），npm run build 通过。
文献画像部分字段“论文中有但仍显示信息不足”的原因已定位并修复：整篇 paper_profile 原本只基于已压缩的 paper_section_summary 生成，章节摘要可能遗漏实验数据、局限性或贡献细节，导致最终画像没有看到原文中的对应信息；现已在最终整篇画像 prompt 中加入可用正文 raw chunk 的“原文证据片段”，并明确要求当章节摘要信息不足但原文证据包含对应信息时优先使用原文证据。验证通过：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileServiceImplTest test 通过（6 个测试，0 失败）。
多篇论文问答“发送失败”问题已定位并修复：HYBRID_RAG 分支虽然主要依赖 paper_profile 和 section_summary，但此前仍先强依赖一次 Qdrant raw chunk 检索；当 Qdrant/embedding/向量检索临时失败或响应过慢时，整轮多篇问答会直接失败。现已改为 HYBRID_RAG raw chunk 检索失败时降级为空 raw chunks，继续使用文献画像和章节摘要生成回答；前端 /api/rag/chat 单次请求超时放宽到 120 秒，适配多篇混合上下文和大模型生成耗时。验证通过：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=RagChatServiceImplTest test 通过（4 个测试，0 失败）；前端 node --test src/api/rag.test.js 通过（2 个测试，0 失败）；npm run build 通过。
文献画像生成已改为异步任务模式：新增 paper_profile_job 表、PaperProfileJob 实体/Mapper/Service 和任务状态 DTO，新增 POST /api/papers/{id}/profile/async 启动后台生成、GET /api/papers/{id}/profile/job 查询任务状态；同一篇论文已有 PROCESSING 任务时会复用任务，避免重复调用大模型。前端文献画像弹窗改为点击后立即显示“文献画像生成中”，每 3 秒轮询任务状态，COMPLETED 后自动刷新画像，FAILED 时展示后端 errorMessage；关闭弹窗或离开页面会停止轮询。新增迁移脚本 docs/database/migrations/paper-profile-job.sql，并同步更新 docs/database/schema.sql。验证通过：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileJobServiceImplTest,PaperProfileServiceImplTest test 通过（9 个测试，0 失败）；前端 node --test src/api/papers.test.js 通过（10 个测试，0 失败）；npm run build 通过。
异步画像任务后端启动报错已修复：Spring Boot 上下文加载失败原因是 PaperProfileJobServiceImpl 同时存在生产用两参构造函数和测试用三参构造函数，Spring 未明确选择可注入构造函数并尝试无参构造，报 No default constructor found；现已在生产构造函数上显式添加 @Autowired，保留三参构造函数给单元测试注入同步执行器。最终后端验证 JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（60 个测试，0 失败）。
本地 MySQL 已执行 docs/database/migrations/paper-profile-job.sql，创建 paper_profile_job 表；验证命令 mysql --default-character-set=utf8mb4 -uroot -p123456 research_assistant -e "SHOW TABLES LIKE 'paper_profile_job';" 已返回 paper_profile_job。前端点击画像时查询 /api/papers/{id}/profile/job 不应再因任务表缺失报错。
本轮大改相较上一版本 RAG 的整体提升：上一版本主要是 VECTOR_RAG 和单篇 FULL_TEXT_PARSED，适合单篇论文全文理解或基于少量向量片段回答；但多篇比较型问题容易只拿到零散 chunk，缺少每篇论文的全局研究问题、方法、实验、贡献和局限，画像缺失时前端也没有明确入口和提示。当前版本补齐“文献画像生成 → 多篇画像齐全 → HYBRID_RAG 比较问答”的完整闭环：第一，用户可在文献页直接生成/查看画像，画像由章节摘要 + 原文证据生成，并修复了 LLM Markdown 输出解析、字段名不匹配、摘要过度压缩导致信息不足等问题；第二，画像生成改为 paper_profile_job 异步任务，点击后立即返回 PROCESSING，前端轮询状态，避免长请求假失败，并能展示真实失败原因；第三，多篇问答策略从单纯 VECTOR_RAG 升级为 ContextStrategyService 自动选择，paperIds 为多篇且每篇有 paper-profile-v1 时进入 HYBRID_RAG，缺失画像则回退 VECTOR_RAG；第四，HYBRID_RAG 上下文由 HybridRagContextService 按用户选择顺序组织，每篇论文依次放入 paper_profile、按问题优先筛选的 paper_section_summary、以及可用 raw_chunk 证据，RagPromptService 引导模型先逐篇分析再横向比较；第五，HYBRID_RAG 不再强依赖 Qdrant raw chunk 检索，raw 检索失败时降级为空 raw chunks，仍可用画像和章节摘要回答，提高多篇问答稳定性。当前完整流程为：上传/解析论文 → 生成结构化 section/chunk → 用户在文献页启动异步画像任务 → 后台按章节调用 LLM 生成 section_summary，再结合章节摘要和原文证据生成 paper_profile → 前端轮询任务状态并展示画像 → 问答页选择多篇论文 → 若多篇画像齐全，RagChatService 选择 HYBRID_RAG → 可选检索少量 raw chunks → 组合 profile/summary/raw_chunk 三层上下文 → 构造多篇比较 prompt → 调用大模型生成回答并返回 contextStrategy、contextPaperIds、contextTokenCount 和 hybrid sources。下一步改进方向：1）把画像任务状态做得更完整，例如进度百分比、当前正在生成第几个章节、失败后重试按钮；2）把章节摘要生成改成受控并发或批量摘要，降低长论文画像耗时，同时避免触发 Qwen 限流；3）为 HYBRID_RAG 增加问题类型识别和字段定向取证，例如方法问题优先 METHOD，实验问题优先 EXPERIMENT/RESULT，局限问题优先 DISCUSSION/CONCLUSION；4）增加多篇比较评测用例，把回答完整性、论文覆盖率、引用支持度纳入 docs/evaluation/cases.md；5）前端 sources 展示进一步区分 paper_profile、section_summary、raw_chunk，让用户能看到回答依据来自画像、章节摘要还是原文证据；6）后续可考虑将 paper_profile 和 section_summary 也向量化，形成 profile/summary/raw_chunk 多粒度检索，而不是仅规则选择 summary。
2026-07-08 进入“文献删除闭环”阶段：复用 DELETE /api/papers/{id}，删除文献时同步清理本地 PDF、paper_section、paper_chunk、paper_section_summary、paper_profile、paper_profile_job 和 Qdrant paper_chunks 中对应 paperId 的向量点；保留 chat_session、chat_message 和 research_idea，避免误删用户历史研究过程。删除策略采用严格模式：Qdrant 向量删除失败时不继续删除 MySQL 和本地文件。
文献删除闭环后端阶段已完成：DELETE /api/papers/{id} 删除前会先按 paperId 删除 Qdrant paper_chunks 向量点；Qdrant 删除失败时阻断后续删除；成功后在事务内显式清理 paper_profile_job、paper_profile、paper_section_summary、paper_chunk、paper_section 和 paper_reference，并删除本地 PDF 文件；chat_session、chat_message、research_idea 保留不动。PaperReferenceServiceImplTest 已覆盖删除成功和 Qdrant 删除失败阻断场景，JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperReferenceServiceImplTest test 通过（7 个测试，0 失败）。
文献删除闭环前端阶段已完成：papers API helper 新增 deletePaper(id)，文献管理页操作列新增“删除”按钮；删除前二次确认会明确提示将删除本地 PDF、chunk、Qdrant 向量、文献画像、章节摘要和画像任务，但不会删除对话历史和 Research Idea；删除成功后刷新文献列表和分类统计。验证通过：node --test src/api/papers.test.js 通过（11 个测试，0 失败），npm run build 通过，Vite 仅提示既有第三方 PURE 注释和 chunk 大小警告。
文献删除闭环已完成：后端 DELETE /api/papers/{id} 已实现严格删除 Qdrant 向量点并清理 MySQL 文献派生资产和本地 PDF；前端文献页已提供删除入口和二次确认；删除保留 chat_session、chat_message、research_idea，避免误删历史研究过程。最终验证：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperReferenceServiceImplTest,QdrantServiceTest test 通过（10 个测试，0 失败），后端 JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（64 个测试，0 失败），前端 node --test src/api/papers.test.js 通过（11 个测试，0 失败），npm run build 通过。建议人工用 Apifox 或前端选择一篇测试文献执行删除，确认文献列表消失且历史对话/Research Idea 保留。
文献画像任务进度与重试机制进入阶段 1：后端 paper_profile_job 已增强 currentStep、progressPercent、processedSections、totalSections 和 retryCount，画像生成过程中通过 PaperProfileProgressListener 持续更新准备、章节摘要、整篇画像和保存步骤；失败任务记录 FAILED 和错误原因，失败后再次启动会创建递增 retryCount 的重试任务。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileJobServiceImplTest,PaperProfileServiceImplTest test 通过（12 个测试，0 失败）。
文献画像任务进度与重试机制阶段 2 已完成：新增 GET /api/papers/profile-status 批量画像状态接口，可一次返回每篇文献是否已有 paper-profile-v1、章节摘要数量、最近画像任务状态、当前步骤、进度百分比、章节进度、重试次数和失败原因，前端文献页后续可避免逐篇请求画像状态。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileStatusServiceImplTest test 通过（1 个测试，0 失败），./mvnw -DskipTests compile 通过。
文献处理队列前端阶段已完成：文献页主区域拆为“待处理文献”和“已入库文献”，待处理文献按解析、向量化、画像三步状态显示一个主按钮并在画像生成中展示章节进度和百分比；已入库文献只保留问答和更多菜单，查看画像、重新生成画像、改分类和删除收纳到更多菜单；点击生成画像后关闭弹窗并回到列表轮询进度。验证通过：node --test src/api/papers.test.js src/views/paperWorkflowState.test.js src/api/paperCategories.test.js src/api/rag.test.js src/views/ragChatState.test.js 通过（32 个测试，0 失败），npm run build 通过。
文献画像任务进度与文献处理队列已完成阶段性实现：后端 paper_profile_job 支持 currentStep、progressPercent、processedSections、totalSections 和 retryCount，画像生成过程可展示准备、章节摘要、整篇画像、保存、完成和失败状态，失败后可通过原 async 接口重试；新增 GET /api/papers/profile-status 批量返回画像状态；前端文献页拆为待处理文献和已入库文献，三步完成才进入已入库，待处理列表使用一个主按钮推进解析、向量化、生成/重试画像，画像生成中在列表展示百分比和章节进度，已入库列表将低频操作收纳到更多菜单。最终验证：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（68 个测试，0 失败）；前端 node --test src/api/papers.test.js src/views/paperWorkflowState.test.js src/api/paperCategories.test.js src/api/rag.test.js src/views/ragChatState.test.js src/api/researchIdeas.test.js src/api/chatHistory.test.js 通过（42 个测试，0 失败）；npm run build 通过。运行本地已有数据库前需要执行 docs/database/migrations/paper-profile-job.sql 补齐 paper_profile_job 新进度字段。建议人工验证：上传新 PDF 后确认进入待处理列表，依次点击解析、向量化、生成画像，确认生成画像后弹窗关闭且列表显示进度，完成后自动进入已入库列表；再临时制造画像失败，确认显示失败原因和重试画像按钮。
本地画像任务启动失败问题已定位并修复：报错 Unknown column 'current_step' 的根因是后端实体已读取 paper_profile_job 新进度字段，但本地 MySQL 表尚未迁移；已手动补齐 current_step、progress_percent、processed_sections、total_sections、retry_count，并将 docs/database/migrations/paper-profile-job.sql 改为兼容旧 MySQL 的 information_schema + PREPARE 幂等迁移写法，重复执行脚本已验证可安全跳过已有字段。
PDF 数据清洗、章节识别与段落分块质量升级阶段 1 已完成：PaperTextCleaner 已增强常见 PDF 噪声过滤，可过滤 journal homepage、Available online、DOI、版权/出版声明、收稿日期、通讯作者、邮箱和首页作者单位行，减少页眉页脚、期刊信息和作者单位进入后续 section/chunk。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperTextCleanerTest test。
PDF 数据清洗、章节识别与段落分块质量升级阶段 2 已完成：PaperSectionDetector 已收紧标题候选规则，避免 Table/Figure、期刊信息、数值密集表格行、单个模型名和表格表头误判为 section；支持 3.1/4.2 等小章节标题，并增强 Method/Experiment/Result 等 sectionType 推断。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest test。
PDF 数据清洗、章节识别与段落分块质量升级进入阶段 3：调整结构化 chunk 粒度并标记 table/figure_caption 内容类型，解析入库时同步写入 paper_chunk.chunk_type。
PDF 数据清洗、章节识别与段落分块质量升级阶段 3 已完成：StructuredChunkingService 已调整为更小的段落优先 chunk 粒度，目标约 1100 字符、最大 1600 字符、重叠 120 字符；StructuredChunk 新增 chunkType，并可初步标记 table 与 figure_caption，解析入库时写入 paper_chunk.chunk_type，避免表格和图注被当作普通正文处理。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=StructuredChunkingServiceTest test。
PDF 数据清洗、章节识别与段落分块质量升级进入阶段 4：增强章节摘要质量，跳过低质量/table/figure_caption 内容，并增加 LLM 摘要输出解析兜底。
PDF 数据清洗、章节识别与段落分块质量升级阶段 4 已完成：PaperProfileService 生成章节摘要时会跳过 reference/noise/table/figure_caption/过短低质量 chunk，章节摘要 prompt 已改为适配小章节和段落集合，并增加 LLM 输出解析兜底，避免有效输出因格式不匹配被误写为“信息不足”。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperProfileServiceImplTest test。
PDF 数据清洗、章节识别与段落分块质量升级下一步进入阶段 5：新增指定论文重建解析派生资产能力，保留 paper_reference 和原始 PDF，清理旧 section/chunk/summary/profile/job 和 Qdrant 向量后重新解析与向量化。
PDF 数据清洗、章节识别与段落分块质量升级阶段 5 已完成：新增指定论文重建解析派生资产能力 POST /api/papers/{id}/reprocess-assets，可保留 paper_reference 和原始 PDF，清理 Qdrant 向量、paper_profile_job、paper_profile、paper_section_summary、paper_chunk 和 paper_section 后重新解析并向量化；该流程不删除 chat_session、chat_message 和 research_idea。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperReferenceServiceImplTest test。
PDF 数据清洗、章节识别与段落分块质量升级进入阶段 6：进行后端整体测试和前端相关测试/构建验证；真实本地论文重建需用户确认具体 paperId 后再执行。
PDF 数据清洗、章节识别与段落分块质量升级已完成阶段性实现：文本清洗已过滤常见 PDF 元信息和作者单位噪声，章节识别改为更严格的标题候选规则并支持小章节，分块调整为更小的段落优先 chunk 并标记 table/figure_caption，章节摘要生成会跳过低质量内容并增加解析兜底，指定论文重建流程可清理旧 section/chunk/summary/profile/job 和 Qdrant 向量后重新解析与向量化。最终验证：后端 JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（77 个测试，0 失败）；前端 node --test src/api/papers.test.js src/views/paperWorkflowState.test.js src/api/paperCategories.test.js src/api/rag.test.js src/views/ragChatState.test.js src/api/researchIdeas.test.js src/api/chatHistory.test.js 通过（42 个测试，0 失败）；npm run build 通过。真实本地论文重建尚未执行，需用户确认具体 paperId 后再调用 POST /api/papers/{id}/reprocess-assets。
PDF 严格短标题章节识别修复已完成：PaperSectionDetector 改为先判断独立短标题形态，再进行 sectionType 分类，避免 Abstract/Introduction 中包含 propose、architecture、experiment、result 等关键词的正文断行被误判为新章节；无编号标题采用 1~8 个词、Title Case、非句子片段的严格规则，保留标准标题和编号标题；SectionType 补充 Transformer 类小标题分类规则，Multi-Head Attention、Encoder/Decoder、Feed-Forward、Embedding、Positional Encoding 归入 METHOD，Training、Optimizer、Regularization 等归入 EXPERIMENT；PaperTextCleaner 补充过滤 NIPS/Transformer 首页授权声明、equal contribution、work performed、会议页脚和 arXiv 标记。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest,PaperTextCleanerTest,StructuredChunkingServiceTest test 通过（22 个测试，0 失败）；后端 JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（81 个测试，0 失败）。真实运行验证：由于验证后端未配置 DASHSCOPE_API_KEY，POST /api/papers/11/reprocess-assets 在向量化阶段返回“Qwen Embedding API Key 未配置”并回滚；改用同一解析链路的 POST /api/papers/11/parse 验证解析入库成功，section 从旧结果 47 条降为 26 条，chunk 从 92 条降为 70 条，Abstract 不再被 mechanism/Experiments 等正文断行切成 METHOD/EXPERIMENT，Multi-Head Attention、Optimizer、Regularization 等小标题可被识别并分类；重复 parse 后 section/chunk 数量保持 26/70，确认不会重复堆积。剩余问题：真实 PDF 中首页授权声明仍进入 Unknown chunk，说明首页噪声清洗还需继续按真实 PDFBox 输出形态增强；完整 reprocess-assets 验证需在配置 DASHSCOPE_API_KEY 后再执行。
标题词数阈值实验已完成并已还原：按用户建议先将无编号和编号标题均收紧为最多 5 个词，单元测试可通过，但真实解析 paperId=9 风速预测论文时效果明显变差；随后尝试将标题词数放宽到 10 个词，paperId=9 仍只识别 6 个 section，说明该论文的问题不是词数阈值，而是 PDFBox 抽取后很多章节标题没有稳定保留为独立行。当前恢复到更接近早期版本的宽松 section_title 识别：短 Title Case 行和编号短行可作为标题，同时保留 SectionType 分类增强、文本清洗、段落分块和 table/figure_caption 标记；在此基础上按 TDD 增加明显表格/模型/指标行过滤，避免 Table 1、Method Category Model Advantages Disadvantages、ST-MLP、ST-LSTM、WeatherGCNet、Params、Training Time、Inference Latency 等直接成为 section_title。验证通过：JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest,StructuredChunkingServiceTest test 通过（17 个测试，0 失败）；JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（82 个测试，0 失败）。真实运行验证：POST /api/papers/9/parse 返回 code=200、data=238，数据库结果为 paper_section=43、paper_chunk=238，相比此前 6 section/1395 chunk 明显改善，可识别 Introduction、Datasets、Evaluation metrics、Results and discussion、Model complexity and efficiency analysis、Parameter sensitivity and optimization analysis、Conclusion and future research 等主体章节；但仍存在部分表格/长句被误识别为 section_title，例如 Dataset 1(...)、Pathformer 超参数行、Performance comparison...、As shown in Table 3...、BVMD-PAMST-MLP 等。下一步应继续针对 paperId=9 做表格区域隔离、页眉页脚过滤和”长句式标题候选”过滤。

2026-07-10 RAG 分块逻辑回退与噪声过滤器保留：
经过阶段 4~5 的分块粒度调整和章节标题识别收紧实验后，paperId=9 真实解析的 section 识别效果反而下降（从 43→6），说明当前 PDFBox 抽取质量下过度严格的标题规则和不稳定的分块粒度调整会引入更多回归而非提升。本次回退将 RAG 分块核心逻辑恢复到 git HEAD（即阶段 3 完成时的版本），仅保留 PaperTextCleaner 噪声行过滤作为已验证的稳定改进。

具体操作：
- 回退到 HEAD 的 5 个 structure 源文件：PaperSectionDetector、PaperTextCleaner（后又手动加回噪声过滤）、SectionType、StructuredChunk、StructuredChunkingService
- 回退 3 个对应测试文件
- 兼容性修复：PaperReferenceServiceImpl 第 275 行 structuredChunk.getChunkType() 改回 “text”（因 StructuredChunk 不再有 chunkType 字段）

保留不变的 diff 文件（与分块逻辑无关）：
- PaperProfileService/Impl：画像进度监听器、章节摘要质量增强、LLM 解析兜底
- PaperProfileJobServiceImpl：异步任务进度字段和重试
- PaperProfileJob/PaperProfileJobResponse：currentStep、progressPercent、processedSections、totalSections、retryCount
- QdrantService：sanitizeForJson + safeString JSON 序列化安全清理
- PaperReferenceService/Impl：reprocessPaperAssets 重建派生资产
- PaperController：profile-status 批量状态、reprocess-assets 重建、PaperProfileStatusService 注入
- 前端文件、docs、迁移脚本

最终保留的结构改进：
- PaperTextCleaner.isNoiseLine()：过滤 DOI、版权、收稿日期、通讯作者、邮箱、作者单位等 PDF 元信息噪声行

验证通过：
- JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（81 个测试，0 失败）

当前分块策略状态（与 git HEAD 一致）：
- TARGET_CHARS=1600、MAX_CHARS=2200、OVERLAP_CHARS=200
- 段落合并优先填满 target，超出 target 的段落按 MAX_CHARS 切分并保留 overlap
- 不标记 chunkType、不做 table/figure_caption 特殊处理
- SectionType 分类不含 Transformer 子类关键词，不含 section_title 误判过滤

经验教训：
- 章节识别和分块粒度的修改应在更充分的真实 PDF 测试集上验证后再合并，单篇 paperId=9 的回归已表明过度 rules 优化在当前 PDFBox 抽取质量下容易适得其反
- 噪声行过滤（PaperTextCleaner）属于独立的前置清洗阶段，与后续章节识别和分块解耦，因此可以作为独立改进保留
- 后续如需改进章节识别和分块，建议先积累 3~5 篇不同类型真实论文的 PDFBox 抽取文本作为回归测试 fixture，再按 TDD 方式迭代规则

2026-07-10 Research Idea 保存触发条件优化：
原有 IdeaSuggestionServiceImpl 使用 45 个宽松关键词（如"方法"、"实验"、"改进"）判断是否弹窗建议保存 Research Idea，导致几乎每轮 RAG 对话都弹出"保存想法"提示，但大部分对话只是知识问答，并没有实质研究 idea。

本次优化将触发条件从宽泛关键词改为三种精准触发方式：
- 触发 1（零成本）：正则匹配问题是否明确寻求改进/优化/建议/未来方向，如"怎么改进"、"如何优化"、"有什么建议"、"下一步可以做什么"、"有哪些局限"等 29 个模式。
- 触发 2（轻量 LLM 分类）：问题包含研究导向词（改进/优化/方向/探索/未来等）时，用小 LLM 调用（~120 token）判断回答是否包含实质研究 idea；回答不通过门控则不调用 LLM，避免每轮对话都额外调用。
- 触发 3（已有，不变）：前端问答页工具栏的"保存想法"按钮始终可用。

后端新增 IdeaSuggestionServiceImplTest（18 个测试），覆盖触发 1 命中/拒绝、触发 2 门控/LLM 分类/失败兜底、空回答、闲聊等场景。

前端 RagChatView.vue 建议提示标题从"这轮回答适合保存为研究想法"改为"发现可保存的研究想法"。

验证通过：
- JAVA_HOME=/path/to/jdk-21 ./mvnw test 通过（89 个测试，0 失败）
- node --test 通过（42 个测试，0 失败）
- npm run build 通过

2026-07-10 Research Idea JSON 解析失败修复：
原有 IdeaSuggestionService LLM 返回 JSON 中 innovationPoints 和 possibleMethod 为数组，但 Java DTO 定义为 String，Jackson 反序列化失败后走兜底逻辑，
导致 title/originalContent/refinedContent 存入了 LLM 原始 JSON，researchQuestion/innovationPoints/possibleMethod/tags 全为 null。
前端看到的就是原始 LLM 输出截断，所有结构化字段显示 "-"。

修复：
- 新增 FlexibleStringDeserializer：Jackson 自定义反序列化器，String 原样返回，Array→中文分号拼接
- ResearchIdeaDraftResponse 中 4 个字段加 @JsonDeserialize 防御
- prompt 加"禁止 JSON 数组"约束，catch 块加 log.warn
- 数据库清理了 2 条脏数据，会话仍存在，可重新保存

验证通过：后端 ./mvnw test 通过（94 个测试）；前端 42 个测试通过；npm run build 通过。

2026-07-10 全库 RAG 检索 + 高级 Query Rewrite + 论文发现已完成：
此前 RAG 必须指定论文才能用，用户不能在"不知道哪篇论文相关"时从全库检索。原有 QueryRewrite 只做中文→英文翻译，无法解决疑问句/陈述词语义鸿沟，全库检索无论文级聚合。

本次新增 LIBRARY_DISCOVERY 策略（paperIds 为空时自动启用），链路为：
- AdvancedQueryRewriteService 单次 LLM 调用完成陈述句改写 + 关键词提取 + HyDE 生成
- 三路 query 分别 Qdrant 检索后 RRF 融合去重
- PaperDiscoveryService 按论文聚合 hitCount/maxScore 做综合评分排序
- RagPromptService 新增 buildLibraryDiscoveryPrompt，引导模型回答+列出最相关论文
- 前端问答页支持全库检索模式提示 + 回答下方展示"最相关论文"卡片

验证通过：后端 ./mvnw test 通过（110 个测试，0 失败）；前端 42 个测试通过；npm run build 通过。

新增文件：QueryRewriteResult、AdvancedQueryRewriteService/Impl、PaperRelevance、PaperDiscoveryService/Impl
改造文件：RagRetrievalService/Impl（多路 RRF）、ContextStrategy（+LIBRARY_DISCOVERY）、ContextStrategyServiceImpl、RagChatServiceImpl、RagChatResponse、RagPromptService/Impl、RagChatView.vue、ragChatState.js
新增测试：AdvancedQueryRewriteServiceImplTest（9）、PaperDiscoveryServiceImplTest（7）

详细改进说明见 docs/archive/notes/rag-improvement-notes.md 第 11 节。
下一步建议：启动前后端后在问答页不选论文直接提问，验证 contextStrategy=LIBRARY_DISCOVERY 和 paperRelevance 卡片展示。

2026-07-10 多粒度向量索引已完成：
在 LIBRARY_DISCOVERY 模式下，paper_profile 和 paper_section_summary 现在也会参与 Qdrant 检索。
生成画像时自动写入 Qdrant（contentType=PAPER_PROFILE/SECTION_SUMMARY），
LIBRARY_DISCOVERY 检索时不限类型全粒度搜索，VECTOR_RAG 仍只检索 RAW_CHUNK。
FULL_TEXT_PARSED 和 HYBRID_RAG 仍从 MySQL 直接读取，不受影响。

改造文件：QdrantService（新增 upsertProfilePoint/upsertSummaryPoint + contentType 检索过滤）、
PaperProfileServiceImpl（生成后写 Qdrant，失败不影响主流程）、
RagRetrievalServiceImpl（VECTOR_RAG 过滤 RAW_CHUNK，LIBRARY_DISCOVERY 全粒度 + 解析 profile/summary）
验证通过：后端 ./mvnw test 通过（110 个测试，0 失败）。

详细设计见 docs/archive/notes/rag-improvement-notes.md 第 12 节。

2026-07-10 BM25 关键词混合检索已完成：
新增内存 BM25 倒排索引（Bm25IndexService），Spring 启动时从 MySQL 加载可用 chunk 自动构建，
888 条 chunk 约 100~300ms，内存占用 2~3MB。VECTOR_RAG 新增 BM25 路由（dense×2 + BM25 → RRF 三路融合），
LIBRARY_DISCOVERY 新增 BM25 路由（dense×3 + BM25 → RRF 四路融合）。
无需 Qdrant 改 collection、无需 ES、无外部依赖。

新增文件：Bm25IndexService.java、Bm25IndexServiceTest.java（9 个测试）
改造文件：RagRetrievalServiceImpl.java（VECTOR_RAG 升级为 RRF + BM25 路由）
验证通过：后端 ./mvnw test 通过（119 个测试，0 失败）。

详细设计见 docs/archive/notes/rag-improvement-notes.md 第 13 节。

2026-07-10 异步任务规范化（Spring @Async）已完成：
将 PaperProfileJobServiceImpl 中手动 `Executors.newFixedThreadPool(2)` 替换为 Spring @Async + ThreadPoolTaskExecutor。
线程池由 Spring 管理生命周期，可通过 application.properties 调整 core/max pool 和队列容量，
新增 ProfileJobAsyncExecutor 解决 @Async 自调用代理失效问题。
新增文件：AsyncConfig.java、ProfileJobAsyncExecutor.java
改造文件：PaperProfileJobServiceImpl.java、PaperProfileJobServiceImplTest.java、application.properties
验证通过：后端 ./mvnw test 通过（119 个测试，0 失败）。
```

2026-07-15 全库 RAG 与混合检索验收阶段 1（代码审查与风险修复）已完成：
- 修复指定论文问答中的 BM25 越界检索：BM25 命中现在会按有效 paperIds 二次过滤，避免未选择论文的 chunk 混入 VECTOR_RAG/HYBRID_RAG 上下文。
- 修复多粒度 RRF 去重键类型判断：统一兼容 paper_profile、section_summary 的小写 sourceType，避免画像和章节摘要因类型大小写不一致而被当成无 chunkId 数据丢弃。
- 新增 2 个 RagRetrievalServiceImpl 回归测试，分别覆盖 BM25 论文范围过滤、多粒度画像/章节摘要保留；与 Bm25IndexServiceTest 合计 13 个测试通过，0 失败。
- 修复 Windows Maven Wrapper 在普通 .m2 目录 Target 为空时访问 Target[0] 导致无法启动的问题，后续可继续使用 mvnw.cmd 执行项目验证。
下一步：运行后端全量测试、前端全量测试与生产构建，确认当前未提交版本不存在回归。

2026-07-15 全库 RAG 与混合检索验收阶段 2（自动化回归）已完成：
- 后端全量测试通过：122 个测试，0 失败、0 错误、1 跳过；跳过项为依赖真实 DASHSCOPE_API_KEY、Qwen Embedding 和 Qdrant 的 RagRetrievalComparisonTest。
- 将 RagRetrievalComparisonTest 明确标记为仅在配置 DASHSCOPE_API_KEY 时运行，避免外部 API 环境缺失导致日常回归测试假失败，同时保留显式真实检索对比入口。
- 前端 7 个测试文件共 42 个测试全部通过，0 失败。
- 前端 npm run build 成功，Vite 仅保留既有第三方 PURE 注释与主 chunk 超过 500kB 的非阻断警告。
下一步：检查本地 MySQL、Qdrant、API Key 和运行端口，在可用条件下执行真实 LIBRARY_DISCOVERY 问答闭环；无法自动验证的外部条件需明确记录。

2026-07-15 全库 RAG 与混合检索验收阶段 3（真实链路验收）已完成：
- IDEA 携带真实 DASHSCOPE_API_KEY 启动后端，本地 MySQL、Qdrant 1.18.2 和 paper_chunks collection 均正常；Qdrant collection 为 1024 维 Cosine、888 个点。
- 全库问答真实验证通过：不传 paperIds 请求 POST /api/rag/chat，返回 contextStrategy=LIBRARY_DISCOVERY、modelProvider=qwen、sourceCount=8、sessionId=57，并返回 paperRelevance 论文卡片数据；本次召回覆盖 paperId=15、17。
- 指定范围隔离真实验证通过：paperIds=[13,22] 返回 contextStrategy=VECTOR_RAG、6 条 sources，sourcePaperIds 仅为 13、22，确认 dense + BM25 融合未越过用户选择范围；保存为 sessionId=58。
- 多篇比较真实验证通过：paperIds=[15,20] 返回 contextStrategy=HYBRID_RAG、14 条 sources、contextTokenCount=2579，sources 同时包含 paper_profile、section_summary、raw_chunk，且 sourcePaperIds/contextPaperIds 均严格为 15、20；Qwen 返回正式比较回答并保存为 sessionId=59。
- 对话历史验证通过：sessionId=57 查询得到 user、assistant 两条消息。
- 发现数据迁移待办：当前 Qdrant 888 个点均为 RAW_CHUNK；MySQL 中 paperId=13、15、20 已有画像和章节摘要，但它们生成于多粒度索引功能之前，尚未回填 PAPER_PROFILE/SECTION_SUMMARY 向量。因此代码和新画像写入链路已支持多粒度检索，旧数据仍需后续提供安全的批量回填任务，不在本次验收中擅自重算。
下一步：更新 README/roadmap 与当前实现保持一致，检查待提交文件和敏感配置，形成全库 RAG 功能稳定提交。

2026-07-15 全库 RAG 与混合检索验收阶段 4（文档与版本固化）已完成：
- README 已更新为当前真实能力，补齐 Vue 前端、Qwen/DeepSeek/智谱 provider、全库发现、混合检索、文献画像和 Research Idea 工作流，删除“前端后续创建”等过时说明。
- docs/product/roadmap.md 已将真实 Embedding、指定论文问答、对话历史、Research Idea、Vue 前端和高级 RAG 标记为完成，并把后续优先级调整为旧画像多粒度向量回填、RAG 评测基线、问题类型定向检索和引用增强。
- 已检查 application.properties 改动，API Key 仍只引用环境变量，没有把 IDEA 中的真实 Key 写入项目；git diff --check 通过。
- 本阶段将全库发现、Query Rewrite/HyDE、BM25+RRF、多粒度检索、论文推荐、Idea JSON 兼容、Spring @Async、前端展示、测试和文档作为同一稳定功能版本提交。
当前状态：全库 RAG 与混合检索第一轮真实验收完成，可进入“旧画像/章节摘要多粒度向量安全回填”或“RAG 自动评测基线”阶段。

2026-07-15 画像与章节摘要显式索引阶段 1（后端）已完成：
- 新增 POST /api/papers/{id}/profile/index，将 MySQL 中已有 paper-profile-v1 和 section-summary-v1 生成 Embedding 并写入 Qdrant，不重新调用 LLM 生成画像。
- 新增 GET /api/papers/{id}/profile/index/status，按 paperId/contentType 精确统计 Qdrant 中 PAPER_PROFILE 与 SECTION_SUMMARY 点数，并与 MySQL 摘要数量核对。
- 修复原多粒度点使用负数 ID 的设计风险；画像和摘要改用由 contentType + 数据库ID生成的稳定 UUID，重复执行会覆盖同一点，不产生重复向量，也不会与正数 RAW_CHUNK ID 冲突。
- 批量摘要写入允许记录单条失败，接口返回画像状态、摘要总数、成功数和失败明细；只有全部成功才返回 success=true。
- 批量画像状态响应新增 profileIndexed、indexedSectionSummaryCount、profileIndexComplete，供前端展示第四步状态。
- 定向验证通过：PaperProfileIndexServiceImplTest、PaperProfileStatusServiceImplTest、QdrantServiceTest 共 6 个测试，0 失败。
下一步：前端文献工作流增加“索引画像”第四步、API helper、筛选和状态测试。

2026-07-15 画像与章节摘要显式索引阶段 2（前端）已完成：
- 文献处理主流程从三步扩展为四步：解析 → 原文向量化 → 生成画像 → 索引画像；画像存在但Qdrant画像/摘要点不完整时显示“待索引画像”和“索引画像”主按钮。
- 新增 indexPaperProfile 与 getPaperProfileIndexStatus API helper；索引操作使用 300 秒超时并展示画像数、章节摘要成功数或失败明细。
- 已入库文献“更多”菜单新增“重新索引画像”，可在画像或Embedding模型更新后显式覆盖索引。
- 待处理筛选与统计已纳入 pendingProfileIndex；只有画像与全部章节摘要索引完整时才进入“已入库文献”。
- 前端定向验证通过：papers.test.js 与 paperWorkflowState.test.js 共 22 个测试，0 失败；npm run build 成功，仅保留既有第三方PURE注释和chunk大小警告。
下一步：运行后端/前端全量回归；重启携带真实 DASHSCOPE_API_KEY 的后端后，为 paperId=13、15、20 执行真实索引并核对Qdrant点数、类型和幂等性。

2026-07-15 画像与章节摘要显式索引阶段 3（真实数据与回归验收）已完成：
- 使用 IDEA 中真实 DASHSCOPE_API_KEY 分别执行 paperId=13、15、20 的 POST /api/papers/{id}/profile/index，成功写入 3 个 PAPER_PROFILE 和 215 个 SECTION_SUMMARY；对应摘要数为 50、85、80，全部成功、0 失败。
- Qdrant points_count 从 888 增加到 1106，符合 888 RAW_CHUNK + 3 PAPER_PROFILE + 215 SECTION_SUMMARY 的预期。
- 重复索引 paperId=13 后 points_count 仍保持 1106，确认稳定UUID upsert具备幂等性，不产生重复点。
- 三篇状态接口均返回 success=true、profileIndexed=true，indexedSectionSummaries 分别等于 50、85、80。
- 全库真实问答再次验证通过：contextStrategy=LIBRARY_DISCOVERY、sessionId=60、12条sources，实际包含4条raw_chunk、1条paper_profile、7条section_summary，证明多粒度索引已真正参与检索。
- 全量验证通过：后端124个测试，0失败、0错误、1个真实外部对比测试按条件跳过；前端45个测试全部通过；前端生产构建通过。
当前状态：前端画像索引设计缺口已补齐，三篇核心测试论文的画像和章节摘要均已进入Qdrant，多粒度全库发现链路完成真实验收。

2026-07-15 三篇论文RAG评测基线阶段 1（事实基准）已完成：
- 固定评测语料为 paperId=13、15、20，三篇均已完成解析、原文向量、画像、章节摘要和多粒度Qdrant索引。
- paperId=13 作为时频增强时空图网络案例；paperId=15 作为GNN + FFTransformer/FFT-Attention多步风速预测案例；paperId=20 作为风速-风向联合预测的ARMA/VAR统计方法案例。
- 评测问题将覆盖单篇理解、多篇比较、全库论文发现、精确事实和无答案拒答；预期论文、策略、关键词均基于MySQL画像中的已知事实定义。
- 评测原则：自动指标只判断可客观复现的策略选择、论文召回、范围越界、source噪声、source类型和关键词覆盖；回答完整性/引用充分性保留人工评分，避免使用同类LLM自评造成虚高。
下一步：形成独立评测方法文档、固定JSON用例和指标公式，作为后续每次RAG优化的统一对照基线。

2026-07-15 三篇论文RAG评测基线阶段 2（方案与工具）已完成：
- 新增 docs/evaluation/methodology.md，明确路由、检索、回答、性能四层评测框架，以及 strategyAccuracy、expectedPaperRecall、scopePurity、referenceNoiseRate、keywordCoverage、refusalAccuracy、多粒度使用率和延迟指标公式。
- 明确自动指标与人工评分边界：自动化负责可复现结构指标，人工1~5分负责事实正确性、完整性、引用支持度和幻觉控制，不使用同类LLM自评包装结果。
- 新增 test/rag-evaluation-cases.json 固定15题：6题单篇理解、4题三篇比较、4题全库发现、1题无答案拒答；固定paperId、预期策略、预期论文和答案关键词。
- 新增 test/run-rag-evaluation.mjs，无额外依赖，顺序调用真实POST /api/rag/chat并输出逐题结果、总体指标、P50/P95耗时和答案摘要；支持MYAGENT_BASE_URL和RAG_EVAL_TOP_K环境变量。
- 后续版本必须复用同一用例、topK和模型配置；准确率变化同时报告“百分点”和“相对提升率”。
下一步：使用当前Qwen + Qdrant + MySQL真实环境运行15题，保存原始基线、生成量化报告并分析失败案例。

2026-07-15 三篇论文RAG评测基线阶段 3（真实运行与量化报告）已完成：
- 使用真实Qwen、Qwen text-embedding-v4、Qdrant 1.18.2、topK=12顺序运行15个固定问题；首轮14/15成功，唯一失败为S13-METHOD遇到Qwen Connection reset，单独重试后成功，确认是瞬时外部服务错误而非RAG能力失败。
- 基线指标：首轮执行成功率93.33%、策略准确率100%、目标论文宏平均Recall 96.43%、指定范围纯度100%、reference/noise污染率0%、关键词覆盖率94.87%、无答案拒答准确率100%。
- 性能指标：平均23.353秒、P50 19.679秒、P95 44.335秒；多篇HYBRID_RAG约34秒，是主要延迟来源。
- 全库发现四题论文Precision分别为50.00%、33.33%、100.00%、66.67%，宏平均62.50%；注意力问题漏掉paperId=13，是唯一目标论文召回缺口。
- 多篇比较4/4覆盖三篇，sources稳定包含3个paper_profile、12个section_summary、5~6个raw_chunk；但全库发现没有任何一题同时使用profile/summary/raw三层，严格多粒度使用率为0%。
- 新增 docs/evaluation/reports/baseline-3-paper-2026-07-15.md，完整记录环境、公式、分类结果、失败案例、优化假设、后续对比阈值和简历可用表述。
当前评测结论：系统范围隔离、路由、噪声过滤和多篇比较较稳定；下一步优先提升全库论文发现Precision、多粒度分类型召回和外部API瞬时失败恢复能力。

2026-07-15 文献信息维护与原文查看阶段 1（后端能力）已完成：
- 新增 PATCH /api/papers/{id} 文献信息编辑接口，支持修改标题、作者、发表年份、期刊/会议、关键词、摘要和备注；接口不接收文件路径、分类、解析状态或向量状态，普通资料编辑不会破坏已有处理进度。
- 编辑接口支持清空可选字段，并校验标题非空、标题/期刊长度和发表年份范围；修改后返回带分类名称的最新文献信息。
- 新增 GET /api/papers/{id}/content 原文预览接口，以 application/pdf 和 inline 方式返回本地 PDF，供浏览器内置阅读器直接打开；原下载接口保持不变。
- 使用项目 JDK 21 完成 PaperReferenceServiceImplTest 定向验证：10个测试全部通过，包含编辑字段、状态保持、空标题和非法年份用例。

2026-07-15 文献信息维护与原文查看阶段 2（前端交互）已完成：
- 待处理文献和已入库文献表格均新增“查看原文”按钮，在新页面打开后端 PDF 预览地址，不离开当前文献管理工作台。
- 两张表格的“更多”菜单均新增“编辑信息”，弹窗支持编辑标题、作者、发表年份、期刊/会议、关键词、摘要和备注，保存成功后自动刷新文献列表。
- 新增 updatePaperMetadata 与 getPaperContentUrl 前端API helper及测试；papers API 与文献工作流定向测试共24项全部通过，前端生产构建成功。
当前状态：代码实现和定向自动验证已完成；需要在 IDEA 中重启后端，使新增 Java 接口生效，再进行浏览器端编辑保存和PDF打开的真实联调验收。
- 全量回归已通过：后端126个测试，0失败、0错误、1个依赖真实外部环境的对比测试按条件跳过；前端47个测试全部通过；前端生产构建通过，仅保留既有第三方PURE注释和主chunk体积警告。

2026-07-16 十六篇文献评测语料准备阶段 1（数据完整性审计）已完成：
- 用户确认当前正式语料总数为16篇；真实接口核验16/16均为parseStatus=COMPLETED、vectorStatus=COMPLETED、hasProfile=true、profileIndexed=true、profileIndexComplete=true。
- Qdrant paper_chunks collection状态为green，共2,922个点并覆盖16个paperId：1,579个RAW_CHUNK、16个PAPER_PROFILE、1,327个SECTION_SUMMARY；各篇Qdrant摘要数与MySQL章节摘要数一致。
- 对16个本地PDF执行SHA-256检查，没有重复文件；先前重复记录paperId=20已不在当前文献库中，paperId=33作为唯一有效记录保留。
- 发现paperId=15的authors疑似误填年份2023、paperId=35的journal疑似误填年份2020，已标记为待根据PDF首页人工核对，未擅自修改元数据。

2026-07-16 十六篇文献评测语料准备阶段 2（语料固化与用例设计）已完成：
- 新增test/rag-evaluation-corpus-16.json，固定16个paperId及每篇标题、年份、主题、方法标签和客观评测事实，语料版本为rag-corpus-16-v1。
- 新增test/rag-evaluation-cases-16.json，共40题：16题单篇理解、8题多篇比较、10题全库发现、4题困难干扰、2题无答案拒答。
- 新用例专门覆盖相似方法区分、综述与原创模型区分、风能与光伏跨主题干扰、图模型差异及经典统计模型扩展方式。
- run-rag-evaluation.mjs新增RAG_EVAL_CASES_FILE配置，报告中的caseFile和corpusPaperIds根据用例自动生成，保留旧三篇基线兼容性。
- 新增docs/evaluation/corpus-16.md，完整记录数据审计、Qdrant点数、语料组成、元数据问题、40题结构、运行方式和下一阶段阈值。
当前状态：16篇正式开发评测语料与40题用例已经固定；下一步使用真实Qwen + Qdrant + MySQL运行40题基线，保存原始结果并按类别分析失败案例。

2026-07-16 十六篇文献RAG基线阶段 1（元数据修正与结果落盘准备）已完成：
- 经用户确认，paperId=15已将误填在authors中的2023移至publishYear并清空authors；paperId=35已将误填在journal中的2020移至publishYear并清空journal。
- 修正后两篇文献parseStatus与vectorStatus仍为COMPLETED，未改变PDF、画像、章节摘要或Qdrant数据。
- run-rag-evaluation.mjs新增RAG_EVAL_OUTPUT_FILE配置，可自动创建结果目录并保存完整JSON，同时继续向标准输出打印报告。
- .gitignore已显式纳入16篇语料、40题用例和test/results下的JSON基线结果，避免关键评测资产被/test忽略规则遗漏。
下一步：使用16篇40题、topK=12运行真实Qwen基线，保存原始结果并统计分类指标与失败案例。

2026-07-16 十六篇文献RAG基线阶段 2（40题真实运行）已完成：
- 使用真实Qwen qwen-plus、Qwen text-embedding-v4、Qdrant 1.18.2和topK=12顺序运行40题，40/40首轮成功，没有连接重置、HTTP错误或重试。
- 原始结果保存为test/results/rag-evaluation-16-baseline-2026-07-16.json，并从对话历史补齐40个完整回答与804条完整sources，文件可用于后续人工评分和引用复核。
- 总体指标：目标论文宏平均Recall 95.35%、宏平均Precision 96.18%、策略准确率100%、范围纯度100%、reference/noise污染率0%、关键词覆盖93.42%、拒答准确率100%、严格多粒度使用率10%。
- 性能指标：平均21.582秒、P50 18.300秒、P95 34.947秒；总墙钟时间约866秒，最慢题47.214秒。
- 分类指标：单篇16题与多篇8题Recall/Precision均为100%；全库发现10题Recall 82.33%、Precision 93.00%；困难干扰4题Recall 100%、source Precision 81.25%；2道无答案题全部正确拒答。

2026-07-16 十六篇文献RAG基线阶段 3（失败分析与报告）已完成：
- 新增docs/evaluation/reports/baseline-16-paper-2026-07-16.md，记录环境、总体/分类指标、性能、失败案例、三篇基线谨慎对照、人工抽查、下一轮阈值和简历可用表述。
- 主要召回缺口集中在全库发现：D-GRAPH漏13/21/30，D-DECOMPOSITION漏13/22并混入综述29，D-STATISTICAL漏33，D-LONG-TERM漏28且造成最终回答错误判断无年度长期论文。
- D-PV与H-PV显示source Precision可能低估最终答案Precision：检索包含干扰论文，但答案仍正确只选择38；后续需分开统计source集合与回答实际提及论文。
- 10道全库发现仅D-ATTENTION同时使用画像、章节摘要、原文三层，9/10没有画像进入最终topK；画像数据完整但共享topK竞争下利用不足。
- 评测脚本已进一步保存完整answer/sources，并增加按类别自动汇总指标，后续复跑无需人工二次计算分类结果。
当前状态：16篇/40题正式开发基线已建立；下一步优先实现全库发现的分类型候选召回、paperId级聚合重排和论文覆盖配额，目标把全库发现Recall从82.33%提升到至少90%，同时保持Precision≥90%、范围纯度100%和拒答准确率100%。

2026-07-16 全库论文发现优化阶段 0（实施规划）已完成：
- 新增docs/archive/plans/library-discovery-retrieval-optimization-plan.md，基于16篇/40题真实基线明确下一轮只优化LIBRARY_DISCOVERY，不改变FULL_TEXT_PARSED、VECTOR_RAG和HYBRID_RAG路径。
- 确认主要根因为PAPER_PROFILE、SECTION_SUMMARY、RAW_CHUNK共享块级topK，数量较少的画像和其他相关论文容易被同一论文的多个章节/原文块挤出；现有PaperDiscoveryService又在回答生成后才聚合，无法补回漏召回论文。
- 固定实施方案为分类型候选召回、paperId级聚合重排、论文覆盖配额和候选确定后的证据补全；画像负责候选判断，摘要/原文负责事实支撑，不新增LLM检索调用，不重建Qdrant数据。
- 固定验收目标：全库发现Recall从82.33%提升至至少90%、Precision保持至少90%、严格多粒度使用率从10%提升至至少50%；策略准确率、范围纯度、噪声过滤、拒答准确率和单篇/多篇指标不得退化，P95不高于34.947秒。
- 规划分为三个可记录阶段：分类型检索与论文聚合基础、论文覆盖配额与证据补全、完整回归与量化报告；每个阶段完成后继续同步本进度文档和原始评测结果。
下一步：实施阶段1，先完成LIBRARY_DISCOVERY分类型检索和paperId级候选聚合，并用单元测试确认指定论文问答路径不受影响。

2026-07-16 全库论文发现优化阶段 1（分类型召回与论文级聚合）已完成：
- QdrantService新增同一查询向量的分类型检索入口；每条Query Rewrite结果只调用一次Embedding，再复用向量分别查询PAPER_PROFILE、SECTION_SUMMARY和RAW_CHUNK，避免Embedding调用从3次放大到9次。
- RagRetrievalServiceImpl将LIBRARY_DISCOVERY从共享块级RRF改为画像、章节摘要、原文三个独立候选池；BM25继续进入原文候选池，各池分别完成RRF后再进入论文级排序。
- PaperDiscoveryService新增paperId级证据聚合：各内容类型独立归一化，采用画像0.40、摘要0.35、原文0.25的可解释权重；同类第二条证据收益衰减，多种粒度共同命中获得覆盖加分，避免按原文块数量线性堆分。
- 最终sources先覆盖最多6篇候选论文，再按论文轮询补充后续证据，解决单篇论文多个高分块连续占满topK的结构性问题；更细的画像/摘要/原文配额和候选后的证据补查仍留在阶段2。
- 新增4项论文聚合/候选覆盖回归测试，并修复多通道重复证据导致轮询提前结束的边界问题；定向18项测试全部通过。
- 使用JDK 21执行后端全量测试：130个测试通过，0失败、0错误、1个依赖真实Qwen/Qdrant的外部对比测试按条件跳过；Spring上下文注入正常，指定论文检索路径原有范围过滤测试通过。
- 本阶段没有改变前端或API响应结构，也尚未用重启后的真实后端运行discovery用例，因此不提前记录Recall/Precision提升值。
当前状态：阶段1代码与自动回归完成；下一步重启后端使新检索链生效，进入阶段2的内容类型配额与候选证据补全，并先对D-GRAPH、D-DECOMPOSITION、D-STATISTICAL、D-LONG-TERM、D-PV、H-PV做真实定向对比。

2026-07-16 全库论文发现优化阶段 1（真实定向评测）已完成：
- 用户在IDEA中重启后端后，确认接口正常返回16篇文献；使用真实Qwen、Qwen text-embedding-v4、Qdrant、topK=12顺序运行D-GRAPH、D-DECOMPOSITION、D-STATISTICAL、D-LONG-TERM、D-PV、H-PV-DISTRACTOR共6题，6/6首轮成功。
- 相同6题source宏平均Recall从旧基线70.56%提升到94.45%，提升23.89个百分点、相对提升33.86%；D-GRAPH从2/5提升到5/5，D-STATISTICAL从2/3提升到3/3，D-LONG-TERM从1/2提升到2/2。
- 5道discovery严格三粒度使用率从0%提升到80%，6/6均有paper_profile进入最终topK，确认分类型召回已真实生效。
- source宏平均Precision从75.83%下降到44.45%；根因为固定最多6篇候选使D-PV等单目标问题也携带6篇source。D-DECOMPOSITION仍漏13和22，source Recall保持66.67%，最终回答只明确推荐38、17、18。
- 最终答案人工复核：D-STATISTICAL、D-LONG-TERM、D-PV、H-PV均正确；D-GRAPH虽然source已召回30，但回答将其判为证据不足，只明确推荐4/5；证明后续必须区分source召回与回答实际推荐。
- 性能从相同6题平均14.577秒增至15.294秒，P95从18.300秒增至19.630秒，仍低于完整基线P95 34.947秒；reference/noise污染率继续为0%。
- 原始结果保存为test/results/rag-evaluation-16-discovery-focus-stage1-2026-07-16.json；新增docs/evaluation/reports/discovery-stage1-2026-07-16.md记录逐题对比、指标变化和阶段2决策；评测脚本新增RAG_EVAL_CASE_IDS定向筛选能力。
当前状态：阶段1证明召回和多粒度利用显著改善，但Precision尚未达到验收线，不立即把结果包装为最终提升；下一步实施阶段2的动态候选截止、候选后分类型证据配额，并扩展source/推荐卡片/回答论文三层指标。

2026-07-16 全库论文发现优化阶段 2（动态候选与证据组合代码）已完成：
- 新增PaperCandidate论文级候选DTO，保留聚合分数、内容粒度覆盖数和明确查询术语覆盖率，为动态截止提供可解释数据，不再只返回paperId列表。
- 候选流程改为“最多8篇宽候选 → 根据问题意图和相对分数动态截止”：唯一/单目标问题最多2至3篇，双“哪篇”问题至少保留2篇，列举型问题最多6篇；弱相关论文不再因为topK有空位而强制进入上下文。
- 论文级重排新增明确术语覆盖加分，且只从section_summary/raw_chunk事实证据计算；针对未主动询问综述的原创方法问题，对标题含review/survey/综述的论文做0.18可解释降权，降低综述29挤占原创论文候选的概率。
- 候选证据改为两轮组合：每篇先放1条paper_profile说明研究主题，再放1条section_summary或raw_chunk支撑事实，剩余预算继续按paperId轮询回流，减少“召回论文但回答认为证据不足”的情况。
- 全库发现paperRelevance卡片上限从5篇扩展为6篇；评测脚本新增recommendedPaperIds、recommendedPaperRecall和recommendedPaperPrecision，后续可分开比较source集合与推荐卡片集合，回答论文仍保留人工复核。
- 新增3项阶段2回归测试，覆盖明确术语加分、原创问题综述降权/综述问题不降权、单目标问题过滤弱候选及画像+事实证据组合；阶段2相关定向22项全部通过。
- 使用JDK21执行后端全量测试：133个测试通过，0失败、0错误、1个真实外部对比测试按条件跳过；评测脚本node --check通过。本阶段未改变前端API结构，不需要前端改造。
当前状态：阶段2代码和自动回归完成，但IDEA中正在运行的后端仍需重启才能加载新代码；下一步重启后复跑相同6道真实题，验收重点6题Recall≥90%、Precision≥70%、D-GRAPH回答5/5、D-DECOMPOSITION至少5/6及多粒度使用率≥50%。

2026-07-16 全库论文发现优化阶段 2（首轮真实评测与证据修正）已完成：
- 重启后使用相同6题、topK=12和真实Qwen/Qdrant复测，6/6首轮成功；source宏平均Recall 97.22%、Precision 84.72%，相较阶段1的94.45%/44.45%在保持高召回的同时恢复40.27个百分点Precision。
- 5道discovery Recall 96.67%、Precision 91.67%；推荐卡片Recall/Precision同为96.67%/91.67%；严格三粒度使用率60%，策略准确率100%，reference/noise污染率0%。
- 平均延迟15.146秒、P95 19.266秒，与阶段1基本持平；D-LONG-TERM只保留27/28，D-PV只保留38，D-DECOMPOSITION最终正确推荐5/6并排除综述29，动态候选和术语重排方向得到真实验证。
- D-GRAPH source和推荐卡片均为5/5且无干扰，但最终回答仍错误排除30。复核paperId=30画像确认其基于球面距离/夹角构建k近邻图、显式定义G=(V,E,A)和25站点节点；失败根因是选入prompt的事实片段过于泛化，而不是标签或召回错误。
- 原始结果保存为test/results/rag-evaluation-16-discovery-focus-stage2-2026-07-16.json；新增docs/evaluation/reports/discovery-stage2-2026-07-16.md记录三阶段对比、逐题结果、推荐卡片指标和paperId=30事实审计。
- 根据真实失败新增小幅修正：候选内事实证据按查询术语命中数优先、图问题扩展“图结构/节点/近邻”证据词、“唯一一篇”最多保留1篇、原创方法问题综述降权由0.18增至0.30。
- 修正后定向22项与后端全量133项测试通过，0失败、0错误、1个外部测试跳过；当前运行后端尚未加载这次小幅修正。
当前状态：阶段2主体指标已达定向门槛；下一步重启后只复测D-GRAPH、D-DECOMPOSITION、H-PV-DISTRACTOR三道边界题，通过后进入完整40题最终回归。

2026-07-16 全库论文发现优化阶段 2（三题边界回归与二次修正）已完成：
- 重启后完成D-GRAPH、D-DECOMPOSITION、H-PV-DISTRACTOR三题真实回归，3/3请求成功；原始结果保存为test/results/rag-evaluation-16-discovery-boundary-stage2-2026-07-16.json。
- D-GRAPH检索与推荐卡片保持5/5，但模型仍违背已进入上下文的paperId=30图结构证据；D-DECOMPOSITION保持最终5/6并排除综述29，但把来源编号9误写为论文ID 9；H-PV-DISTRACTOR因唯一问题硬截断为综合分第一名，只保留错误的风速论文19并漏掉光伏论文38。
- 唯一答案候选选择已改为明确术语覆盖率优先、综合分次优，避免通用多路命中压过判别性主题证据；全库发现prompt新增来源编号/论文ID强约束和图结构证据判定规则。
- D-GRAPH评测问题补充“图结构”口径，使包含k近邻图与图局部卷积的paperId=30和expectedPaperIds定义一致；该调整仅校正评测语义，不作为算法提升数据。
- 新增2项边界回归测试；定向11项全部通过。JDK21后端全量135项中134项通过、0失败、0错误、1项真实外部测试按条件跳过。
当前状态：二次修正代码、自动回归和失败记录已完成；IDEA当前后端仍是修正前进程。下一步重启后再次只运行上述3题，三题通过后进入10道discovery三轮稳定性测试和40题完整回归。

2026-07-16 全库论文发现优化阶段 2（第二次边界回归与直接方法证据排序）已完成：
- 加载二次修正后再次运行三题，3/3请求成功；H-PV-DISTRACTOR已恢复为只检索和推荐光伏论文38，D-DECOMPOSITION保持最终5/6且不再混淆来源编号与论文ID。
- D-GRAPH检索和推荐卡片继续无噪声覆盖5/5，但最终回答仍排除30。审计确认本轮选中的是“综述现有GNN局限”的背景摘要，而同一论文中“k近邻构图并生成邻接矩阵”的直接方法摘要未获得事实证据名额。
- 事实证据排序新增背景内容降级：仅讨论existing/current methods、related work、本节综述、现有方法且没有本文提出/构图/邻接矩阵等直接方法标记的证据，不再依靠较多查询词命中压过论文自身方法证据。
- 原始结果保存为test/results/rag-evaluation-16-discovery-boundary-stage2-rerun-2026-07-16.json；三题source与推荐宏平均Recall/Precision均94.44%，严格三粒度使用率100%，平均/P95延迟17.226/21.059秒，reference/noise污染率0%。
- 新增1项直接方法证据优先测试；定向12项全部通过。JDK21后端全量136项中135项通过、0失败、0错误、1项真实外部测试按条件跳过。
当前状态：光伏唯一答案和来源ID语义问题已真实修复；D-GRAPH直接证据排序代码已完成但运行后端尚未加载。下一步重启后先单独复测D-GRAPH，通过后再运行10道discovery三轮稳定性测试与40题完整回归。

2026-07-16 全库论文发现优化阶段 3（完整40题终验）已完成：
- 用户明确要求避免无限优化后，将验收收敛为一次完整40题真实终验，不再执行10道discovery三轮重复测试；停止门槛固定为全库发现Recall≥90%、Precision≥90%，并保持策略、范围隔离和噪声过滤不退化。
- 40/40请求成功；总体source Recall/Precision为99.12%/96.49%，全库发现Recall/Precision为96.67%/91.67%，相较旧基线82.33%/93.00%，Recall提高14.34个百分点且Precision仍保持在90%门槛以上。
- 全库发现推荐卡片Recall/Precision为96.67%/91.67%；严格三粒度使用率由10%提升到70%；策略准确率100%、指定论文范围纯度100%、reference/noise污染率0%，单篇和多篇source Recall/Precision均为100%。
- 答案关键词覆盖率由93.42%提升到96.93%；平均延迟由21.582秒增至23.265秒，完整P95由34.947秒增至48.098秒。P95主要由未改动检索链的多篇Qwen长回答主导，本阶段不宣称延迟优化；全库发现平均/P95为14.578/23.494秒。
- 两道无答案题人工复核均正确拒答；原始自动拒答50%是旧正则未识别“无一篇”措辞导致，评测脚本已补充拒答表达，但原始JSON保持不可篡改。
- D-GRAPH检索/推荐5/5但Qwen仍把paperId=30归为非图模型；D-DECOMPOSITION本轮4/6、此前定向轮次5/6，记录为方法边界和外部检索/生成波动，不再继续为个别题堆规则。
- 原始结果保存为test/results/rag-evaluation-16-final-stage3-2026-07-16.json；最终报告保存为docs/evaluation/reports/final-16-paper-2026-07-16.md，包含完整口径、分类指标、已知限制和简历可用表述。
当前状态：全库论文发现优化达到既定效果门槛并正式结束，不再继续调参。下一阶段建议转向评测结果展示产品化或Research Idea工作流增强；扩充到50篇以上语料时再重新建立大语料基线。

2026-07-16 RAG评测结果前端展示产品化阶段已完成：
- 新增前端`/evaluation`检索质量评测页面，将固定16篇/40题终验结果展示为基线与最终指标对比、质量护栏、分类结果、性能说明、已知限制和项目量化描述；页面为只读固定版本，不会触发Qwen/Qdrant调用。
- 新增顶部“评测”导航和首页“评测结果”入口，应用状态标签展示“16篇 · 40题”；项目量化描述支持一键复制，便于演示和整理简历。
- 新增rag-corpus-16-v1前端评测摘要数据，保留评测日期、语料规模、topK、核心指标、分类指标、延迟和限制；明确展示全库发现Recall 82.33%→96.67%、Precision 93.00%→91.67%、三粒度使用率10%→70%，同时不把P95包装为优化成果。
- 新增2项评测数据测试，固定停止门槛和指标差值；前端全部49项Node测试通过，0失败。`npm run build`生产构建成功，只有既有第三方PURE注释和大包体提示。
- 新增docs/archive/plans/rag-evaluation-frontend-productization-plan.md，记录页面目标、信息结构、固定数据策略、实现结果和阶段结束条件。
当前状态：检索优化成果已经能够在项目界面中直接展示，本阶段结束，不继续增加装饰性图表。下一阶段建议进入Research Idea工作流增强，优先改善想法从问答生成后缺少结构化编辑与来源回溯的问题。

2026-07-16 RAG链路实现与评测体系文档化阶段已完成：
- 新增docs/architecture/rag-system-implementation-and-evaluation-guide.md，以当前代码和16篇/40题最终原始结果为准，系统整理离线知识加工、MySQL/Qdrant职责、多粒度索引、四类上下文策略、Query Rewrite、Dense+BM25+RRF、论文级聚合、动态候选截止、证据配额、Prompt生成、历史保存等完整链路。
- 文档明确画像、章节摘要、原文块为何需要同时保存在MySQL和Qdrant：MySQL作为权威事实与业务主库，Qdrant作为可重建候选索引；并说明指定论文与未指定论文时三类数据的不同使用方式。
- 记录40题评测集的五类题型、真实运行环境、逐项指标定义与计算公式，明确总体指标采用逐题宏平均；同时解释推荐卡片指标、范围纯度、噪声污染率、关键词覆盖、拒答人工复核、多粒度使用率和延迟指标的适用边界。
- 汇总最终可复现量化结果、PowerShell运行方式、失败分层排查方法、停止门槛、已知限制和大语料扩展建议，并提供代码/材料索引与基于真实数字的项目介绍口径。
当前状态：RAG实现、优化过程、评测方法和量化结论已形成统一学习文档；原定下一阶段仍为Research Idea工作流增强，本次文档整理不改变既有开发任务计划。

2026-07-16 Research Idea工作流增强阶段0（实施规划）已完成：
- 新增docs/archive/plans/research-idea-workflow-enhancement-plan.md，确认本阶段只解决结构化编辑与可靠来源回溯，不扩展为复杂项目管理系统。
- 当前前端只能查看、流转状态和删除Idea，后端已有更新接口但未接入；详情中的会话和关联论文也只是文本ID，无法一键回到原对话或打开论文。
- 确认自动草稿的relatedPaperIds不应继续由大模型决定，后端将从聊天消息真实sourcesJson中确定性提取并去重paperId，防止模型漏填、错填或混淆来源编号。
当前状态：阶段边界和验收标准已固定；下一步实现后端可靠来源提取、前端结构化编辑与来源跳转，并完成前后端自动验证。

2026-07-16 Research Idea工作流增强阶段1（结构化编辑与来源回溯）已完成：
- 想法列表和详情新增编辑入口，支持标题、正文、原始讨论、研究问题、创新点、可能方法、标签、关联论文和状态的完整编辑；保存时保留sourceType、sourceSessionId和sourceMessageId，避免正文修改切断来源关系。
- 新增前端Research Idea完整PUT更新API和表单状态模块；标题为空时阻止提交，关联论文ID统一清理为去重的正整数逗号串，编辑完成后列表、详情和状态统计同步刷新。
- Idea详情新增来源操作：有sourceSessionId时可一键跳回原RAG会话；关联论文按真实文献目录显示标题和paperId，并可直接打开后端PDF内容接口查看原文。
- 自动草稿的relatedPaperIds改为由后端递归解析聊天消息真实sourcesJson确定性提取，按首次出现顺序去重；不再采信LLM返回的论文ID，避免模型漏填、错填或把来源编号当成paperId。单条损坏sourcesJson只跳过该消息，不阻断整个草稿生成。
- 新增1项后端来源提取测试、1项前端更新API测试和2项前端表单/ID归一化测试；Research Idea后端定向8项测试通过，前端全部52项Node测试通过，生产构建成功。
- 使用JDK21完成后端全量回归：137项中136项通过、0失败、0错误、1项依赖真实外部服务的对比测试按条件跳过；Spring上下文、RAG、文献和对话模块未退化。前端构建仅保留既有第三方PURE注释和大包体提示。
当前状态：Research Idea已经具备从RAG会话沉淀、可靠关联来源、结构化编辑、状态流转和原文回溯的完整基本闭环；后端需重启后新建的会话草稿才会启用确定性paperId提取。下一阶段建议补齐“手动新建Idea”入口并复用现有编辑表单，使非RAG来源的研究想法也能在前端直接录入。

2026-07-16 Research Idea工作流增强阶段2（手动创建规划）已完成：
- 下一项范围固定为复用结构化编辑表单增加“新建想法”入口，接通后端已有POST创建接口；手动想法固定sourceType=manual、默认saveType=draft，不伪造会话来源。
- 本阶段不增加数据库字段，不扩展多人协作或任务管理，只补齐非RAG研究灵感进入Idea列表、状态统计和后续编辑流转的入口。
当前状态：开始实现前端手动创建闭环，并复用现有表单归一化和自动测试。

2026-07-16 Research Idea工作流增强阶段2（手动创建闭环）已完成：
- 想法页顶部新增“新建想法”入口，复用结构化编辑弹窗，可在前端直接录入标题、正文、研究问题、创新点、可能方法、标签、关联论文和初始状态。
- 新增前端createResearchIdea API并接通后端已有POST /api/research-ideas；同一弹窗根据是否存在editingIdea自动切换创建/编辑行为，创建成功后同步刷新列表和状态统计。
- 手动创建默认sourceType=manual、saveType=draft，sourceSessionId/sourceMessageId保持null；不会为了复用RAG表单而伪造来源关系。标题空值和关联论文ID继续使用阶段1的统一校验与归一化。
- 新增手动创建API测试和无来源表单测试；前端全部54项Node测试通过、0失败，生产构建成功，仅保留既有第三方PURE注释和大包体提示。
当前状态：Research Idea基础工作流已经完整覆盖RAG会话自动沉淀和人工灵感录入，并支持可靠来源回溯、结构化编辑、筛选、状态流转和删除；本轮Idea功能增强到此收敛，不继续扩展为复杂任务管理系统。后端仍需用户重启后，未来新建的RAG Idea才会使用确定性paperId来源提取。
