# 文档维护规范

## 1. 单一入口与单一事实源

- `docs/README.md` 是所有项目文档的总入口。
- `docs/status/current.md` 只描述当前事实、阻塞点和下一步，不保存完整时间流水。
- `docs/product/roadmap.md` 只描述阶段和优先级，不重复写每次测试细节。
- RAG 数字只从 `docs/evaluation/reports/` 的正式报告引用。
- 已完成方案和旧判断统一进入 `docs/archive/`，不与当前方案并列展示。

## 2. 目录选择

| 内容 | 目录 |
| --- | --- |
| 当前项目状态 | `status/` |
| 产品范围和路线 | `product/` |
| 当前架构与接口 | `architecture/` |
| Schema 和 migration | `database/` |
| 评测方法、语料和报告 | `evaluation/` |
| 已批准且未完成的任务计划 | `plans/` |
| 维护规则 | `maintenance/` |
| 已完成计划、旧设计、历史流水 | `archive/` |

## 3. 命名规则

- 长期维护的当前文档使用稳定名称，例如 `requirements.md`、`current.md`。
- 时间相关报告使用 `type-scope-YYYY-MM-DD.md`。
- 活动计划使用 `YYYY-MM-DD-short-task-name.md`。
- 数据库增量脚本使用 `YYYY-MM-DD-short-description.sql`；已有历史脚本保留当前名称。
- 文件名统一英文小写和连字符，避免 `final-final-v2` 一类不可判断的名称。

## 4. 进度更新规则

完成实质阶段后，直接修改 `status/current.md` 的以下区域：

1. 已完成能力或最近完成；
2. 当前运行注意事项；
3. 当前任务与下一步；
4. 最近验证。

不要继续按日期把所有过程追加到当前状态。需要保留长过程时，任务完成后将计划和专项报告归档；重要历史节点可按季度生成一次 `archive/progress/` 快照。

## 5. 链接规则

- Markdown 内部链接优先使用相对路径。
- 移动文件后必须全库检查旧路径引用。
- 链接到原始评测 JSON 时使用项目根相对路径 `test/results/...`。
- 归档文档允许保留历史语气，但路径应尽量保持可访问。

移动或新增文档后，在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File test/check-doc-links.ps1
```

## 6. 新任务生命周期

```text
用户批准任务
→ plans/ 新建活动计划
→ status/current.md 标记当前任务
→ 实现与验证
→ 更新当前状态和正式专项文档
→ 计划移入 archive/plans/
```

小型修复不强制建立计划；只有会跨多个文件、包含阶段验收或需要长期衔接的任务才创建计划文档。

## 7. 禁止事项

- 不在 `docs/` 根目录随手增加无分类文件。
- 不把临时命令输出、重复编译记录和一次性排查过程写入当前状态。
- 不修改原始评测 JSON 来美化指标。
- 不删除仍能解释重要设计决策的历史文档；移动到归档并在索引中降级即可。
- 不让 `AGENTS.md`、`CLAUDE.md` 和其他说明重复维护三份不同规则。
