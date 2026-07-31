# Research Engineering Agent：最小上下文 API 计划

## 目标

为本地 CodeAgent v3 提供只读、模式明确的上下文接口，使用户不必手工导出 JSON：

```text
GET /api/research-ideas/{id}/improvement-context
GET /api/papers/{id}/reproduction-context
```

本阶段只聚合现有 MyAgent 数据，不创建任务队列、不保存本机绝对路径、不执行代码、不修改论文或 Idea 数据。

## 输出边界

- Idea 接口返回 Idea 本体、关联论文基础信息和可回溯 evidence；本地 Agent 再补本机 `RepositoryBaseline`。
- Paper 接口返回单篇论文的基础信息、已存在画像/章节摘要与可回溯 evidence。
- `project_id` 不由 MyAgent 生成；本地 CLI 初始化项目后把它注入上下文快照，避免线上保存本机执行身份。
- 所有结果使用现有 `Result<T>` 包装。

## 实施步骤

1. 阅读现有 Paper/Idea Service、Mapper 与实体，确认可稳定返回的字段。
2. 新增 DTO 和一个聚合 Service，避免 Controller 直接拼装数据库数据。
3. 新增两个只读 Controller 路由和针对服务的单元测试。
4. 启动后端后使用真实已有 Idea/Paper ID 进行 HTTP 冒烟验证。
5. 在 CodeAgent 侧增加下载命令，转换为本地 `IdeaContext/PaperContext` 并由用户补充本机仓库基线。

## 验收

- 对不存在 ID 返回当前系统一致的错误；
- Idea 与 Paper 上下文的来源类型不混淆；
- 不泄漏本机路径、密钥或原始数据库连接信息；
- CodeAgent 能通过 HTTP 获取并保存一份可解析的上下文快照。

## 实施记录（2026-07-22）

- [x] 新增 DTO、聚合服务与只读 Controller；Controller 不直接拼装数据库数据。
- [x] 提供 `GET /api/research-ideas/{id}/improvement-context` 与 `GET /api/papers/{id}/reproduction-context`。
- [x] 论文上下文复用现有元数据、论文画像和章节摘要；Idea 上下文复用 Idea 及关联论文的可回溯 evidence。
- [x] 后端编译通过：`mvnw.cmd -q -DskipTests compile`。
- [x] CodeAgent 的 `fetch-context` 已能把上述 HTTP DTO 转换为本地不可变快照；Idea 的本地仓库基线仍由本机提供。
- [x] 使用真实 Paper ID `38` 完成 HTTP 冒烟验证；本地 CLI 已成功拉取并将项目推进到 `CONTEXT_READY`。
- [x] 为避免大论文摘要一次性占满 Agent 上下文，论文接口现限制为 16 条优先级章节 evidence，并对单条/画像/元数据设置字符上限和明确截断标记。
