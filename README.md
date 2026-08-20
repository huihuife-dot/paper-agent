# MyAgent：论文 AI 研究助手

MyAgent 是一个面向个人论文库的 AI 研究助手。它能把 PDF 解析成可检索的结构化知识，支持单篇精读、多篇对比、全库论文发现和第一章辅助写作；还能从对话中沉淀 Research Idea，并把论文里的实验参数、图表结论和原文依据整理成可交给本地代码 Agent 的复现任务。

项目重点不只是“让大模型回答问题”，而是让回答和复现参数都能追溯到论文原文，并把信息不足、来源冲突和模型推断明确标出来，避免把不确定内容直接当成实验事实。

## 主要功能

- 文献管理：上传、分类、编辑、删除和在线查看 PDF。
- 结构化解析：使用 PDFBox 识别章节并切分原文，过滤引用区噪声。
- 多粒度 RAG：支持单篇全文问答、多篇对比和全库论文发现。
- 结构化优先问答：将方法、数据集、实验设置、指标和结果沉淀为带原文来源的知识单元；明确问题优先查 MySQL，语义模糊或资料缺失时才用 RAG 补漏。
- 混合检索：结合 Query Rewrite、HyDE、向量检索、BM25、RRF 和论文级重排。
- 可追溯问答：回答附带论文、章节和原文块来源，并保存对话历史。
- 论文写作模式：从用户选中的论文生成引言、研究现状或文献综述大纲，按章节形成可编辑草稿，并提供引用核查、局部 AI 修改和版本恢复。
- Research Idea：通过规则初筛和 LLM 判断捕获研究想法，过滤无关对话，并保留论文及原始会话来源。
- 多模态复现证据：定位 PDF 中的图、表、公式和算法，用 Qwen-VL 定向分析，提取带原文出处的实验事实；通过可信度分级和冲突检测生成安全的 `ReproductionSpec`。
- 证据感知代码交付：可由同服务器 Agent 执行，也可导出结构化任务包 ZIP 或发布到 Gitee 交给外部 Agent；本地 Git 保存基线、任务分支和结果提交。
- 简洁研究工作台：使用顶部主导航和全宽无框布局，问答历史位于左侧；桌面端与窄屏均可完成文献、知识、问答、写作、想法、复现和评测操作。
- 质量验证：后端和前端均包含单元测试，系统内提供只读评测页面。

> Research Engineering Agent 是独立 CLI。单机模式由 Spring Boot 以受控独立进程启动；不使用平台 Agent 时可以下载任务包或通过 Gitee 交给其他终端 Agent。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Spring Boot、MyBatis-Plus、MySQL、PDFBox |
| 检索 | Qdrant、Qwen Embedding、BM25、RRF |
| 模型 | Qwen / Qwen-VL，也可通过 `LlmService` 切换 DeepSeek、智谱或 Fake Provider |
| 前端 | Vue 3、Vite、Element Plus |
| 测试 | JUnit、Node.js Test Runner、固定语料回归评测脚本 |

## 页面入口

| 页面 | 用途 |
| --- | --- |
| 文献 | 上传和管理 PDF，查看解析、画像、图表资产与复现准备度 |
| 知识 | 构建论文目录和知识单元，查看来源、可信等级及主题导航 |
| 问答 | 选择单篇、多篇或全库范围提问，查看历史会话和本轮取证路径 |
| 论文写作 | 基于所选论文生成大纲和草稿，核查引用并恢复历史版本 |
| 想法 | 保存 Research Idea，跟踪状态并发起代码改进任务 |
| 复现 | 查看本地 Agent、任务包、Git/Gitee 分支和代码成果 |
| 评测 | 查看固定语料上的检索质量与系统限制 |

## 运行前准备

请先安装：

- JDK 21
- MySQL 8
- Node.js 20 或更高版本
- Qdrant（本地进程或 Docker）
- 一个阿里云百炼 `DASHSCOPE_API_KEY`

同一个 `DASHSCOPE_API_KEY` 同时用于 Qwen 对话、Embedding 和 Qwen-VL，不需要分别申请三个 Key。所有密钥都通过环境变量读取，仓库不保存真实值。

### 1. 创建数据库

在 MySQL 中创建名为 `research_assistant` 的数据库，然后执行 [完整建表脚本](docs/database/schema.sql)。已有数据库请按 [数据库升级说明](docs/database/README.md) 执行增量脚本。

### 2. 启动 Qdrant

如果本机已安装 Docker，可执行：

```powershell
docker run --name myagent-qdrant -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage qdrant/qdrant
```

Qdrant 默认地址为 `http://localhost:6333`。

### 3. 配置环境变量

PowerShell 示例：

```powershell
$env:DASHSCOPE_API_KEY="填入你的百炼 API Key"
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="填入你的 MySQL 密码"
```

常用可选配置：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/research_assistant?...` | MySQL 连接地址 |
| `MYSQL_USERNAME` | `root` | MySQL 用户名 |
| `MYSQL_PASSWORD` | 空 | MySQL 密码 |
| `QDRANT_URL` | `http://localhost:6333` | Qdrant 地址 |
| `APP_DATA_DIR` | `./ResearchAssistantData` | 本地解析数据目录 |
| `APP_UPLOAD_DIR` | `./ResearchAssistantData/papers` | PDF 保存目录 |
| `LLM_PROVIDER` | `qwen` | 对话模型提供方 |
| `EMBEDDING_PROVIDER` | `qwen` | 向量模型提供方 |
| `QWEN_MODEL` | `qwen-plus` | Qwen 对话模型 |
| `QWEN_VISION_MODEL` | `qwen-vl-max` | Qwen-VL 视觉模型 |
| `RAG_STRUCTURED_FIRST_ENABLED` | `true` | 是否启用目录/画像/知识单元/章节优先的问答路由 |
| `AGENT_EXECUTION_COMMAND` | `research-engineering` | 同服务器 Agent CLI 命令或完整路径 |
| `GITEE_ENABLED` | `false` | 是否启用可选 Gitee 远程发布 |
| `GITEE_ACCESS_TOKEN` | 空 | 仅由后端读取，用于创建私有仓库 |
| `GITEE_OWNER` | 空 | Gitee 个人用户名，用于生成仓库地址 |

需要离线体验界面或运行不依赖真实模型的测试时，可设置 `LLM_PROVIDER=fake` 和 `EMBEDDING_PROVIDER=fake`。

## 启动项目

先启动 MySQL 和 Qdrant，再分别启动后端和前端。

后端：

```powershell
cd backend/research-assistant-backend
./mvnw.cmd spring-boot:run
```

前端：

```powershell
cd frontend/research-assistant-frontend
npm install
npm run dev
```

浏览器访问 Vite 控制台显示的地址，通常是 `http://localhost:5173`；后端默认运行在 `http://localhost:8080`。

## 基本使用流程

1. 在“文献管理”中上传 PDF，并等待解析和向量化完成。
2. 打开“知识索引”，为已生成画像和章节摘要的论文构建目录与知识单元；可以查看来源、可信等级并确认、纠正或拒绝自动知识。
3. 打开单篇论文进行问答，或在对话页选择多篇论文进行比较；执行详情会说明本轮直接读取了画像、知识单元还是章节，以及是否调用 RAG 补漏。
4. 不指定论文时，可先通过知识目录寻找候选论文，目录覆盖不足时系统再从整个文献库执行语义检索。
5. 对话中出现明确研究问题或改进方法时，系统可将其保存为 Research Idea。
6. 在论文工作区切换到“论文写作”，选择已有论文和内容类型，依次生成大纲与草稿；在引用核查中查看论文、原文块和页码，也可以选中文字让 AI 局部修改并随时恢复旧版本。
7. 在论文详情中查看图表资产、触发 Qwen-VL 分析，并检查实验事实的原文证据、可信度和冲突状态。
8. 需要代码复现时，预览 `ReproductionSpec`，选择平台 Agent、下载结构化任务包 ZIP，或创建 Gitee 仓库交给外部 Agent。平台执行结果由本地 Git 保存，并可下载代码成果 ZIP。

### 可选 Gitee 同步

启用前设置：

```powershell
$env:GITEE_ENABLED="true"
$env:GITEE_ACCESS_TOKEN="填入 Gitee Token"
$env:GITEE_OWNER="填入 Gitee 个人用户名"
```

Token 只用于后端调用 Gitee 创建仓库；代码推送使用运行后端账号已经配置的 Gitee SSH Key。若暂不配置 Gitee，本地 Git、平台 Agent、任务包 ZIP 和代码成果 ZIP 都可以正常使用。

## 测试

后端单元测试：

```powershell
cd backend/research-assistant-backend
./mvnw.cmd test
```

前端测试和生产构建：

```powershell
cd frontend/research-assistant-frontend
npm install
npm test
npm run build
```

## 项目结构

```text
MyAgent/
├── backend/research-assistant-backend/   # Spring Boot 后端
├── frontend/research-assistant-frontend/ # Vue 3 前端
├── docs/database/                        # 建表和数据库升级脚本
├── ResearchAssistantData/                # 本地论文数据，不提交到 Git
└── README.md                              # 项目介绍与使用入口
```
