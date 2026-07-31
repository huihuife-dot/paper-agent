# 文献删除闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除文献时同步清理本地 PDF、MySQL 派生资产和 Qdrant 向量点，同时保留对话历史与 Research Idea。

**Architecture:** 复用现有 `DELETE /api/papers/{id}`，在 `PaperReferenceServiceImpl.deletePaper` 中统一编排删除流程。Qdrant 先按 `paperId` 严格删除向量点，成功后再在 MySQL 事务内显式删除画像任务、画像、章节摘要、chunk、section 和文献主记录，最后删除本地文件。前端文献页增加删除按钮和二次确认。

**Tech Stack:** Spring Boot, MyBatis-Plus, Qdrant REST API, Vue 3, Element Plus, Node test runner, JUnit 5, Mockito, AssertJ.

## Global Constraints

- 删除范围：`paper_reference`、本地 PDF、`paper_section`、`paper_chunk`、`paper_section_summary`、`paper_profile`、`paper_profile_job`、Qdrant `paper_chunks` 中 `payload.paperId = 当前文献 ID` 的 points。
- 保留范围：`chat_session`、`chat_message`、`research_idea`。
- Qdrant 删除失败时采用严格模式：返回失败，不继续删除 MySQL 和本地文件。
- MySQL 删除使用事务；派生表显式删除，数据库外键级联只作为兜底。
- 每个实质阶段更新 `docs/status/current.md`，避免关机后丢失上下文。
- 后端测试命令需要显式使用 JDK 21：`JAVA_HOME=/path/to/jdk-21 ./mvnw ...`。

---

## File Structure

- Modify: `docs/status/current.md` — 记录删除功能进入、后端完成、前端完成、最终验证状态。
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperReferenceServiceImpl.java` — 注入画像相关 Mapper，给 `deletePaper` 增加严格 Qdrant 删除和显式 MySQL 派生数据删除。
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImplTest.java` — 补充删除闭环单元测试。
- Modify: `frontend/research-assistant-frontend/src/api/papers.js` — 新增 `deletePaper(id)` API helper。
- Modify: `frontend/research-assistant-frontend/src/api/papers.test.js` — 验证 helper 调用 `DELETE /api/papers/{id}`。
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue` — 文献列表增加删除按钮、确认弹窗和刷新逻辑。

---

### Task 1: 记录删除功能进入实施阶段

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: 已批准设计 `docs/archive/specs/2026-07-08-paper-delete-design.md`。
- Produces: 重启后可继续的阶段状态说明。

- [ ] **Step 1: 在进度文档当前任务末尾追加阶段记录**

在 `docs/status/current.md` 的“下一步任务”代码块末尾追加：

```text
2026-07-08 进入“文献删除闭环”阶段：复用 DELETE /api/papers/{id}，删除文献时同步清理本地 PDF、paper_section、paper_chunk、paper_section_summary、paper_profile、paper_profile_job 和 Qdrant paper_chunks 中对应 paperId 的向量点；保留 chat_session、chat_message 和 research_idea，避免误删用户历史研究过程。删除策略采用严格模式：Qdrant 向量删除失败时不继续删除 MySQL 和本地文件。
```

- [ ] **Step 2: 保存后确认文档存在该记录**

检查 `docs/status/current.md` 中包含 “文献删除闭环” 和 “严格模式”。

Expected: 文档能说明当前阶段和删除策略。

---

### Task 2: 后端删除闭环测试先行

**Files:**
- Modify: `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImplTest.java`

**Interfaces:**
- Consumes: `PaperReferenceServiceImpl.deletePaper(Long id)`。
- Produces: 删除功能必须满足的单元测试。

- [ ] **Step 1: 添加 Mapper import**

在测试文件 import 区增加：

```java
import com.myagent.assistant.paper.mapper.PaperProfileJobMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
```

- [ ] **Step 2: 添加删除成功测试**

在 `parsePaperWritesStructuredSectionsAndChunks` 测试后追加：

```java
    @Test
    void deletePaperDeletesQdrantPointsDerivedRowsReferenceAndLocalFile() throws Exception {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperSectionSummaryMapper paperSectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperProfileJobMapper paperProfileJobMapper = mock(PaperProfileJobMapper.class);
        QdrantService qdrantService = mock(QdrantService.class);

        Path file = tempDir.resolve("delete-me.pdf");
        Files.writeString(file, "pdf");

        PaperReference paper = new PaperReference();
        paper.setId(12L);
        paper.setFilePath(file.toString());
        when(paperReferenceMapper.selectById(12L)).thenReturn(paper);
        when(qdrantService.deletePaperPoints(12L)).thenReturn(Map.of("success", true));

        PaperReferenceServiceImpl service = createService(
                paperReferenceMapper,
                mock(PaperCategoryService.class),
                paperChunkMapper,
                paperSectionMapper,
                qdrantService,
                paperSectionSummaryMapper,
                paperProfileMapper,
                paperProfileJobMapper
        );

        service.deletePaper(12L);

        verify(qdrantService).deletePaperPoints(12L);
        verify(paperProfileJobMapper).delete(any(Wrapper.class));
        verify(paperProfileMapper).delete(any(Wrapper.class));
        verify(paperSectionSummaryMapper).delete(any(Wrapper.class));
        verify(paperChunkMapper).delete(any(Wrapper.class));
        verify(paperSectionMapper).delete(any(Wrapper.class));
        verify(paperReferenceMapper).deleteById(12L);
        assertThat(Files.exists(file)).isFalse();
    }
```

- [ ] **Step 3: 添加 Qdrant 失败阻断测试**

继续追加：

```java
    @Test
    void deletePaperStopsWhenQdrantDeleteFails() {
        PaperReferenceMapper paperReferenceMapper = mock(PaperReferenceMapper.class);
        PaperChunkMapper paperChunkMapper = mock(PaperChunkMapper.class);
        PaperSectionMapper paperSectionMapper = mock(PaperSectionMapper.class);
        PaperSectionSummaryMapper paperSectionSummaryMapper = mock(PaperSectionSummaryMapper.class);
        PaperProfileMapper paperProfileMapper = mock(PaperProfileMapper.class);
        PaperProfileJobMapper paperProfileJobMapper = mock(PaperProfileJobMapper.class);
        QdrantService qdrantService = mock(QdrantService.class);

        PaperReference paper = new PaperReference();
        paper.setId(13L);
        paper.setFilePath(tempDir.resolve("missing.pdf").toString());
        when(paperReferenceMapper.selectById(13L)).thenReturn(paper);
        when(qdrantService.deletePaperPoints(13L)).thenReturn(Map.of("success", false, "error", "Qdrant unavailable"));

        PaperReferenceServiceImpl service = createService(
                paperReferenceMapper,
                mock(PaperCategoryService.class),
                paperChunkMapper,
                paperSectionMapper,
                qdrantService,
                paperSectionSummaryMapper,
                paperProfileMapper,
                paperProfileJobMapper
        );

        assertThatThrownBy(() -> service.deletePaper(13L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("删除 Qdrant 向量失败：Qdrant unavailable");

        verify(paperReferenceMapper, never()).deleteById(any());
        verify(paperChunkMapper, never()).delete(any(Wrapper.class));
        verify(paperSectionMapper, never()).delete(any(Wrapper.class));
        verify(paperSectionSummaryMapper, never()).delete(any(Wrapper.class));
        verify(paperProfileMapper, never()).delete(any(Wrapper.class));
        verify(paperProfileJobMapper, never()).delete(any(Wrapper.class));
    }
```

- [ ] **Step 4: 扩展测试 helper 构造函数**

把现有 `createService(PaperReferenceMapper, PaperCategoryService)` 保留，并新增重载：

```java
    private PaperReferenceServiceImpl createService(PaperReferenceMapper paperReferenceMapper,
                                                    PaperCategoryService paperCategoryService,
                                                    PaperChunkMapper paperChunkMapper,
                                                    PaperSectionMapper paperSectionMapper,
                                                    QdrantService qdrantService,
                                                    PaperSectionSummaryMapper paperSectionSummaryMapper,
                                                    PaperProfileMapper paperProfileMapper,
                                                    PaperProfileJobMapper paperProfileJobMapper) {
        PaperReferenceServiceImpl service = new PaperReferenceServiceImpl(
                paperReferenceMapper,
                paperChunkMapper,
                mock(PdfParseService.class),
                qdrantService,
                paperCategoryService,
                paperSectionMapper,
                mock(StructuredChunkingService.class),
                paperSectionSummaryMapper,
                paperProfileMapper,
                paperProfileJobMapper
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        return service;
    }
```

并把旧 helper 中构造函数改为调用新增构造函数：

```java
    private PaperReferenceServiceImpl createService(PaperReferenceMapper paperReferenceMapper,
                                                    PaperCategoryService paperCategoryService) {
        return createService(
                paperReferenceMapper,
                paperCategoryService,
                mock(PaperChunkMapper.class),
                mock(PaperSectionMapper.class),
                mock(QdrantService.class),
                mock(PaperSectionSummaryMapper.class),
                mock(PaperProfileMapper.class),
                mock(PaperProfileJobMapper.class)
        );
    }
```

- [ ] **Step 5: 运行测试确认失败**

Run:

```bash
cd backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperReferenceServiceImplTest test
```

Expected: FAIL，原因是 `PaperReferenceServiceImpl` 构造函数尚未接收新增 Mapper，或删除逻辑尚未调用这些 Mapper。

---

### Task 3: 实现后端删除闭环

**Files:**
- Modify: `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperReferenceServiceImpl.java`

**Interfaces:**
- Consumes: `QdrantService.deletePaperPoints(Long paperId): Map<String, Object>`。
- Produces: `deletePaper(Long id)` 严格删除 Qdrant + 显式清理 MySQL 派生数据 + 删除本地文件。

- [ ] **Step 1: 添加 import**

在 import 区增加：

```java
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperProfileJob;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.mapper.PaperProfileJobMapper;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import org.springframework.transaction.annotation.Transactional;
```

- [ ] **Step 2: 添加字段**

在现有字段后增加：

```java
    private final PaperSectionSummaryMapper paperSectionSummaryMapper;
    private final PaperProfileMapper paperProfileMapper;
    private final PaperProfileJobMapper paperProfileJobMapper;
```

- [ ] **Step 3: 扩展构造函数**

把构造函数改为：

```java
    public PaperReferenceServiceImpl(PaperReferenceMapper paperReferenceMapper,
                                     PaperChunkMapper paperChunkMapper,
                                     PdfParseService pdfParseService,
                                     QdrantService qdrantService,
                                     PaperCategoryService paperCategoryService,
                                     PaperSectionMapper paperSectionMapper,
                                     StructuredChunkingService structuredChunkingService,
                                     PaperSectionSummaryMapper paperSectionSummaryMapper,
                                     PaperProfileMapper paperProfileMapper,
                                     PaperProfileJobMapper paperProfileJobMapper) {
        this.paperReferenceMapper = paperReferenceMapper;
        this.paperChunkMapper = paperChunkMapper;
        this.pdfParseService = pdfParseService;
        this.qdrantService = qdrantService;
        this.paperCategoryService = paperCategoryService;
        this.paperSectionMapper = paperSectionMapper;
        this.structuredChunkingService = structuredChunkingService;
        this.paperSectionSummaryMapper = paperSectionSummaryMapper;
        this.paperProfileMapper = paperProfileMapper;
        this.paperProfileJobMapper = paperProfileJobMapper;
    }
```

- [ ] **Step 4: 替换 deletePaper 实现**

把 `deletePaper(Long id)` 完整替换为：

```java
    @Override
    @Transactional
    public void deletePaper(Long id) {
        PaperReference paper = paperReferenceMapper.selectById(id);

        if (paper == null) {
            throw new RuntimeException("文献不存在");
        }

        Map<String, Object> qdrantDeleteResult = qdrantService.deletePaperPoints(id);
        if (!Boolean.TRUE.equals(qdrantDeleteResult.get("success"))) {
            throw new RuntimeException("删除 Qdrant 向量失败：" + qdrantDeleteResult.getOrDefault("error", "未知错误"));
        }

        paperProfileJobMapper.delete(new QueryWrapper<PaperProfileJob>().eq("paper_id", id));
        paperProfileMapper.delete(new QueryWrapper<PaperProfile>().eq("paper_id", id));
        paperSectionSummaryMapper.delete(new QueryWrapper<PaperSectionSummary>().eq("paper_id", id));
        paperChunkMapper.delete(new QueryWrapper<PaperChunk>().eq("paper_id", id));
        paperSectionMapper.delete(new QueryWrapper<PaperSection>().eq("paper_id", id));
        paperReferenceMapper.deleteById(id);

        deleteLocalFileIfExists(paper.getFilePath());
    }
```

- [ ] **Step 5: 添加本地文件删除 helper**

在 `previewText` 方法前增加：

```java
    private void deleteLocalFileIfExists(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException e) {
            throw new RuntimeException("删除本地文献文件失败", e);
        }
    }
```

- [ ] **Step 6: 运行后端目标测试**

Run:

```bash
cd backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperReferenceServiceImplTest test
```

Expected: PASS。

- [ ] **Step 7: 更新进度文档记录后端完成**

在 `docs/status/current.md` 当前任务代码块末尾追加：

```text
文献删除闭环后端阶段已完成：DELETE /api/papers/{id} 删除前会先按 paperId 删除 Qdrant paper_chunks 向量点；Qdrant 删除失败时阻断后续删除；成功后在事务内显式清理 paper_profile_job、paper_profile、paper_section_summary、paper_chunk、paper_section 和 paper_reference，并删除本地 PDF 文件；chat_session、chat_message、research_idea 保留不动。PaperReferenceServiceImplTest 已覆盖删除成功和 Qdrant 删除失败阻断场景。
```

---

### Task 4: 前端 API helper 测试先行

**Files:**
- Modify: `frontend/research-assistant-frontend/src/api/papers.test.js`
- Modify: `frontend/research-assistant-frontend/src/api/papers.js`

**Interfaces:**
- Consumes: 后端 `DELETE /api/papers/{id}`。
- Produces: `deletePaper(id, client = apiClient): Promise<any>`。

- [ ] **Step 1: 测试文件 import 增加 deletePaper**

把 import 列表改为包含：

```js
  deletePaper,
```

- [ ] **Step 2: fake client 增加 delete 方法**

在 `createFakeClient()` 返回对象中 `patch` 后增加：

```js
    delete(path) {
      calls.push({ method: 'delete', path })
      return Promise.resolve({ data: null })
    },
```

- [ ] **Step 3: 添加 API 测试**

在 `vectorizePaper` 测试后追加：

```js
test('deletePaper deletes one paper', async () => {
  const client = createFakeClient()

  await deletePaper(12, client)

  assert.deepEqual(client.calls, [{ method: 'delete', path: '/api/papers/12' }])
})
```

- [ ] **Step 4: 运行测试确认失败**

Run:

```bash
cd frontend/research-assistant-frontend && node --test src/api/papers.test.js
```

Expected: FAIL，原因是 `deletePaper` 尚未导出。

- [ ] **Step 5: 实现 deletePaper helper**

在 `frontend/research-assistant-frontend/src/api/papers.js` 的 `vectorizePaper` 后增加：

```js
export function deletePaper(id, client = apiClient) {
  return client.delete(`/api/papers/${id}`).then((result) => result.data ?? result)
}
```

- [ ] **Step 6: 运行测试确认通过**

Run:

```bash
cd frontend/research-assistant-frontend && node --test src/api/papers.test.js
```

Expected: PASS。

---

### Task 5: 前端文献页删除入口

**Files:**
- Modify: `frontend/research-assistant-frontend/src/views/PaperManagementView.vue`

**Interfaces:**
- Consumes: `deletePaper(id)` API helper。
- Produces: 文献列表中单篇删除按钮、确认弹窗、删除后刷新。

- [ ] **Step 1: import deletePaper**

在 `../api/papers.js` import 列表中加入：

```js
  deletePaper,
```

- [ ] **Step 2: 操作列增加删除按钮**

在“问答”按钮后增加：

```vue
                    <el-button
                      size="small"
                      type="danger"
                      plain
                      :loading="activeAction === `delete-${row.id}`"
                      @click="handleDeletePaper(row)"
                    >
                      删除
                    </el-button>
```

如果按钮过多导致操作列拥挤，把操作列宽度从 `400` 改为 `460`：

```vue
              <el-table-column label="操作" width="460" fixed="right">
```

- [ ] **Step 3: 添加删除处理函数**

在 `handleVectorize(row)` 后增加：

```js
async function handleDeletePaper(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除“${row.title || row.fileName || '未命名文献'}”吗？\n\n系统会同步删除本地 PDF、解析 chunk、Qdrant 向量、文献画像、章节摘要和画像任务；不会删除对话历史和 Research Idea。`,
      '删除文献',
      { type: 'warning', confirmButtonText: '删除文献', cancelButtonText: '取消' },
    )

    activeAction.value = `delete-${row.id}`
    await deletePaper(row.id)
    ElMessage.success('文献及其解析/向量/画像数据已删除')
    await reloadLibrary()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`删除文献失败：${error.message || error}`)
    }
  } finally {
    if (activeAction.value === `delete-${row.id}`) {
      activeAction.value = ''
    }
  }
}
```

- [ ] **Step 4: 运行前端 API 测试和构建**

Run:

```bash
cd frontend/research-assistant-frontend && node --test src/api/papers.test.js && npm run build
```

Expected: tests PASS，build PASS。Vite 可能仍提示既有第三方 PURE 注释或 chunk size warning，不视为失败。

- [ ] **Step 5: 更新进度文档记录前端完成**

在 `docs/status/current.md` 当前任务代码块末尾追加：

```text
文献删除闭环前端阶段已完成：papers API helper 新增 deletePaper(id)，文献管理页操作列新增“删除”按钮；删除前二次确认会明确提示将删除本地 PDF、chunk、Qdrant 向量、文献画像、章节摘要和画像任务，但不会删除对话历史和 Research Idea；删除成功后刷新文献列表和分类统计。
```

---

### Task 6: 整体验证和最终进度记录

**Files:**
- Modify: `docs/status/current.md`

**Interfaces:**
- Consumes: 后端删除闭环和前端删除入口。
- Produces: 可交付状态和人工 Apifox/前端验证步骤。

- [ ] **Step 1: 运行后端相关测试**

Run:

```bash
cd backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperReferenceServiceImplTest,QdrantServiceTest test
```

Expected: PASS。

- [ ] **Step 2: 运行后端完整测试**

Run:

```bash
cd backend/research-assistant-backend && JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

Expected: PASS。

- [ ] **Step 3: 运行前端测试和构建**

Run:

```bash
cd frontend/research-assistant-frontend && node --test src/api/papers.test.js && npm run build
```

Expected: PASS。Vite 既有 warning 不视为失败。

- [ ] **Step 4: 给用户 Apifox 验证步骤**

准备以下验证说明：

```text
1. 选择一篇可删除的测试文献，记录 paperId。
2. 如该文献已解析/向量化/生成画像，直接请求 DELETE /api/papers/{paperId}。
3. 预期返回 code=200。
4. 请求 GET /api/papers/{paperId}，预期返回“文献不存在”。
5. 请求 GET /api/papers，预期列表不再包含该文献。
6. 如 Qdrant 可用，可再用相同 paperId 作为问答参考范围验证不会再召回该文献 chunk。
7. 对话历史和 Research Idea 页面应仍保留原有记录。
```

- [ ] **Step 5: 更新最终进度记录**

在 `docs/status/current.md` 当前任务代码块末尾追加：

```text
文献删除闭环已完成：后端 DELETE /api/papers/{id} 已实现严格删除 Qdrant 向量点并清理 MySQL 文献派生资产和本地 PDF；前端文献页已提供删除入口和二次确认；删除保留 chat_session、chat_message、research_idea，避免误删历史研究过程。最终验证：后端 PaperReferenceServiceImplTest/QdrantServiceTest 通过，后端整体 ./mvnw test 通过，前端 papers.test.js 和 npm run build 通过。建议人工用 Apifox 或前端选择一篇测试文献执行删除，确认文献列表消失且历史对话/Research Idea 保留。
```

- [ ] **Step 6: 查看 git diff**

Run:

```bash
git diff -- docs/status/current.md docs/archive/specs/2026-07-08-paper-delete-design.md docs/archive/plans/2026-07-08-paper-delete.md backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/service/Impl/PaperReferenceServiceImpl.java backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/service/impl/PaperReferenceServiceImplTest.java frontend/research-assistant-frontend/src/api/papers.js frontend/research-assistant-frontend/src/api/papers.test.js frontend/research-assistant-frontend/src/views/PaperManagementView.vue
```

Expected: diff 只包含文献删除闭环、设计/计划和进度记录相关变更。
