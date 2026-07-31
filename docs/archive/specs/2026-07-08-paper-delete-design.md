# 文献删除闭环设计

日期：2026-07-08

## 目标

为文献管理补齐删除能力：用户删除一篇文献时，系统要同步删除这篇文献产生的派生资产，包括本地 PDF、结构化章节、chunk、文献画像、章节摘要、画像任务和 Qdrant 向量点；同时保留对话历史和 Research Idea，避免误删用户已经沉淀的研究过程。

## 删除范围

删除：

- `paper_reference` 文献主记录
- 本地上传的 PDF 文件
- `paper_section` 论文章节
- `paper_chunk` 文本块
- `paper_section_summary` 章节摘要
- `paper_profile` 文献画像
- `paper_profile_job` 画像异步任务
- Qdrant `paper_chunks` collection 中 `payload.paperId = 当前文献 ID` 的向量点

保留：

- `chat_session`
- `chat_message`
- `research_idea`

保留原因：对话历史和 Research Idea 属于用户研究过程资产，不应因为删除文献而自动清空。后续如果需要更强一致性，可追加“清理历史引用关系”的独立功能。

## 后端设计

复用现有接口：

```http
DELETE /api/papers/{id}
```

`PaperReferenceService.deletePaper(id)` 负责统一编排删除流程。

推荐删除顺序：

```text
确认文献存在
→ 删除 Qdrant 向量点
→ 删除 paper_profile_job
→ 删除 paper_profile
→ 删除 paper_section_summary
→ 删除 paper_chunk
→ 删除 paper_section
→ 删除 paper_reference
→ 删除本地 PDF 文件
```

设计说明：

1. 先确认文献存在，避免误删空数据。
2. Qdrant 按 `paperId` 删除，不依赖 MySQL chunk 是否仍存在。
3. 派生表显式删除，同时保留数据库外键级联作为兜底。
4. MySQL 删除使用事务包住，保证数据库侧清理一致。
5. 本地文件删除放在最后；如果文件不存在，不阻塞删除。
6. Qdrant 删除失败时采用严格模式：接口返回失败，不继续删除 MySQL 和本地文件，避免“文献已删但向量残留”。

## 前端设计

文献管理页新增单篇文献删除入口。

交互：

1. 用户点击“删除”。
2. 前端弹出二次确认，说明会删除 PDF、chunk、向量、画像和画像任务，但不会删除对话历史和 Research Idea。
3. 用户确认后调用 `DELETE /api/papers/{id}`。
4. 删除成功后刷新文献列表，并提示删除成功。
5. 删除失败时展示后端错误信息。

## 测试设计

后端测试：

- 删除不存在文献时报“文献不存在”。
- 删除文献时会调用 `QdrantService.deletePaperPoints(paperId)`。
- Qdrant 删除失败时不删除 MySQL 文献记录。
- 删除成功时清理 chunk、section、section summary、profile、profile job 等派生数据。
- 本地文件不存在时不影响删除。

前端测试：

- `deletePaper(id)` API helper 调用 `DELETE /api/papers/{id}`。
- 文献页删除成功后刷新列表。

## 进度记录要求

实施期间持续更新 `docs/status/current.md`，只记录实质阶段状态：进入删除功能阶段、后端删除闭环完成、前端删除入口完成、最终验证结果和下一步任务。临时编译、小型重命名等不单独记录。
