# 架构文档

| 文档 | 作用 |
| --- | --- |
| [RAG 链路实现与评测完整指南](rag-system-implementation-and-evaluation-guide.md) | 当前最完整的系统说明，覆盖数据加工、存储、路由、检索、生成和评测 |
| [API 设计](api-design.md) | 接口分组与请求响应设计；实际行为以当前控制器代码为准 |
| [Research Engineering Agent](research-engineering-agent.md) | 文献复现、Idea 驱动代码改进、结构化任务包与本地安全边界 |

未来需要记录不可逆或影响多个模块的架构决策时，在 `architecture/decisions/` 新增 ADR，而不是把决策过程继续追加到当前状态文档。
