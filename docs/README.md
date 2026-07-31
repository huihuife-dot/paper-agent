# MyAgent 文档中心

这里是项目文档的唯一入口。阅读项目时先看“当前状态”，理解系统时看“架构”，准备开发时再进入“产品路线”和“计划”。

## 快速入口

| 我想了解 | 入口 |
| --- | --- |
| 项目现在做到哪里、下一步是什么 | [当前状态](status/current.md) |
| 产品目标、范围和开发路线 | [产品文档](product/README.md) |
| RAG、API 和系统实现 | [架构文档](architecture/README.md) |
| 如何从文献/Idea 进入本地代码复现与改进 | [Research Engineering Agent](architecture/research-engineering-agent.md) |
| 数据库初始化和迁移 | [数据库文档](database/README.md) |
| 评测方法、语料和最终结果 | [评测文档](evaluation/README.md) |
| 正在实施的任务方案 | [开发计划](plans/README.md) |
| 如何新增和维护文档 | [文档维护规范](maintenance/documentation-conventions.md) |
| 早期设计、完成计划和完整历史 | [历史归档](archive/README.md) |

## 目录职责

```text
docs/
├── README.md                 # 文档总入口
├── status/                   # 当前状态，只保存短小、持续更新的快照
├── product/                  # 产品需求、路线图和范围
├── architecture/             # 当前系统架构、API 和关键技术说明
├── database/                 # 全量 schema 与增量 migration
├── evaluation/               # 评测方法、用例、语料和报告
├── plans/                    # 尚未完成的实施计划
├── maintenance/              # 文档及工程维护规范
└── archive/                  # 已完成计划、旧设计、历史进度和研究笔记
```

## 信息优先级

同一问题出现多份描述时，按以下优先级判断：

1. 当前运行代码与自动测试；
2. `status/current.md` 当前状态；
3. `architecture/` 和 `product/` 的当前文档；
4. `evaluation/` 中标记为最终版本的报告；
5. `archive/` 中的历史记录。

归档文档用于解释“当时为什么这么做”，不代表当前仍按其中的计划继续实施。
