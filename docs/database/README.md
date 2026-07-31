# 数据库文档

## 文件说明

- [schema.sql](schema.sql)：新环境使用的完整 MySQL 建表脚本。
- `migrations/`：已有数据库按功能演进时使用的增量脚本。

## 历史迁移顺序

1. [文献分类](migrations/literature-category.sql)
2. [结构化切块](migrations/paper-structured-chunking.sql)
3. [论文画像与章节摘要](migrations/paper-profile-section-summary.sql)
4. [画像异步任务](migrations/paper-profile-job.sql)
5. [页面与基础资产](../../backend/research-assistant-backend/src/main/resources/db/migration/2026-07-27-paper-asset.sql)
6. [资产结构化内容](../../backend/research-assistant-backend/src/main/resources/db/migration/2026-07-27-paper-asset-structured-content.sql)
7. [资产人工复核](../../backend/research-assistant-backend/src/main/resources/db/migration/2026-07-27-paper-asset-manual-review.sql)
8. [视觉分析版本与任务](../../backend/research-assistant-backend/src/main/resources/db/migration/2026-07-27-vision-analysis.sql)
9. [复现事实主库](../../backend/research-assistant-backend/src/main/resources/db/migration/2026-07-27-reproduction-facts.sql)
10. [复现规格与准备度](../../backend/research-assistant-backend/src/main/resources/db/migration/2026-07-27-reproduction-spec.sql)

新环境优先执行完整 `schema.sql`，不要在完整 schema 之后机械重复执行全部历史 migration。已有环境升级前应先备份，并按缺失功能选择迁移脚本。

迁移脚本一旦用于真实环境就不覆盖原有语义；需要修正时新增 migration，文件名使用 `YYYY-MM-DD-short-description.sql`。
