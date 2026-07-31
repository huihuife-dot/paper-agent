# 文献分类文件夹功能设计

更新时间：2026-07-06

## 1. 功能目标

为论文/文献 AI 研究助手系统新增“文献分类文件夹”能力，使用户可以自定义文献分类，并在上传、管理、筛选和问答选文献时看到分类信息。

本功能解决的问题：

- 当前文献库只能按解析/向量化状态查看，无法按研究主题、阅读阶段或用途组织文献。
- 用户希望先创建分类，再在上传文献时从已有分类中选择。
- 已上传文献需要支持后续移动到其他分类。
- 删除分类时不能删除文献，应将文献自动转入“未分类”。

## 2. 已确认需求

- 分类由用户自定义命名。
- 一篇文献只属于一个分类，采用文件夹式分类模型，不采用多标签模型。
- 系统需要保留默认分类“未分类”。
- 上传文献时从已有分类中选择；不选择时进入“未分类”。
- 上传后可以修改某篇文献所属分类。
- 删除普通分类时，该分类下的文献自动转入“未分类”。
- “未分类”是系统分类，不能删除。
- 每个实质小阶段开始前，需要先更新 `docs/status/current.md` 记录当前阶段计划。
- 每个实质小阶段完成后，需要更新 `docs/status/current.md` 记录完成内容、验证结果和下一步。
- 整个功能完成并验证后，需要提交一个功能版本 commit。

## 3. 数据模型设计

### 3.1 新增文献分类表

新增 `paper_category` 表：

```sql
CREATE TABLE IF NOT EXISTS paper_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    description VARCHAR(500) COMMENT '分类说明',
    sort_order INT DEFAULT 0 COMMENT '排序值',
    system_flag TINYINT(1) DEFAULT 0 COMMENT '是否系统分类：0否/1是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_paper_category_name (name)
) COMMENT='文献分类表';
```

默认插入系统分类：

```sql
INSERT INTO paper_category (name, description, sort_order, system_flag)
SELECT '未分类', '系统默认分类，不能删除', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM paper_category WHERE name = '未分类');
```

### 3.2 增强文献表

`paper_reference` 增加 `category_id` 字段：

```sql
ALTER TABLE paper_reference
    ADD COLUMN category_id BIGINT COMMENT '文献分类ID';
```

已有文献迁移规则：

- 为所有 `category_id IS NULL` 的旧文献设置默认“未分类”分类 ID。
- 新上传文献如果没有传 `categoryId`，也进入“未分类”。

建议添加索引：

```sql
ALTER TABLE paper_reference
    ADD INDEX idx_paper_category_id (category_id);
```

外键可以在项目稳定后再加。当前项目已有外键，但本功能更关注兼容已有数据和本地开发便利，实施时可根据 MySQL 现状选择是否添加外键。

## 4. 后端设计

### 4.1 新增分类领域对象

新增后端文件：

- `paper/entity/PaperCategory.java`
- `paper/mapper/PaperCategoryMapper.java`
- `paper/dto/PaperCategoryCreateRequest.java`
- `paper/dto/PaperCategoryUpdateRequest.java`
- `paper/dto/PaperCategoryResponse.java`
- `paper/service/PaperCategoryService.java`
- `paper/service/impl/PaperCategoryServiceImpl.java`
- `paper/controller/PaperCategoryController.java`

`PaperCategoryResponse` 需要包含：

```text
id
name
description
sortOrder
systemFlag
paperCount
createTime
updateTime
```

`paperCount` 用于前端分类文件夹显示数量。

### 4.2 分类接口

#### 查询分类列表

```http
GET /api/paper-categories
```

返回按 `sort_order ASC, id ASC` 排序的分类列表，并带 `paperCount`。

#### 新建分类

```http
POST /api/paper-categories
Content-Type: application/json

{
  "name": "RAG 核心论文",
  "description": "重点阅读的 RAG 论文"
}
```

规则：

- `name` 不能为空。
- `name` 不能重复。
- 新建分类 `systemFlag = false`。
- `sortOrder` 自动设置为当前最大排序值 + 10。

#### 更新分类

```http
PUT /api/paper-categories/{id}
Content-Type: application/json

{
  "name": "RAG 重点论文",
  "description": "已经精读或准备精读的 RAG 论文"
}
```

规则：

- 分类必须存在。
- 系统分类“未分类”不允许重命名，避免默认语义被破坏。
- 普通分类名称不能为空。
- 普通分类名称不能与其他分类重复。

#### 删除分类

```http
DELETE /api/paper-categories/{id}
```

规则：

- 分类必须存在。
- 不允许删除系统分类“未分类”。
- 删除普通分类前，先将该分类下所有文献的 `category_id` 更新为“未分类”的 ID。
- 再删除该分类。

### 4.3 增强文献接口

#### 文献列表支持分类筛选

```http
GET /api/papers?categoryId=2
```

规则：

- 不传 `categoryId`：返回全部文献。
- 传 `categoryId`：返回该分类下文献。
- 返回每篇文献时包含 `categoryId` 和 `categoryName`。

#### 上传文献支持分类

```http
POST /api/papers/upload
Content-Type: multipart/form-data

file=<PDF>
title=<标题>
categoryId=2
```

规则：

- 传入合法 `categoryId`：文献进入该分类。
- 不传 `categoryId`：文献进入“未分类”。
- 传入不存在的 `categoryId`：返回错误。

#### 修改文献分类

```http
PATCH /api/papers/{id}/category
Content-Type: application/json

{
  "categoryId": 2
}
```

规则：

- 文献必须存在。
- 分类必须存在。
- 修改分类不影响解析状态、向量化状态、chunk 或 Qdrant 数据。

## 5. 前端设计

### 5.1 文献页分类文件夹

`/papers` 左侧侧栏增加“分类文件夹”：

```text
添加文献
新建分类
刷新文献

分类文件夹
- 全部文献        12
- 未分类          2
- RAG 核心论文    5
- 对比实验论文     3

状态筛选
- 全部状态
- 待解析
- 待向量化
- 已向量化
```

筛选规则：

- 分类筛选和状态筛选组合生效。
- 选择“全部文献”时不传 `categoryId`。
- 选择具体分类时调用 `GET /api/papers?categoryId=<id>` 或前端本地过滤；推荐调用后端筛选，保持数据规模扩大后的可用性。

### 5.2 新建、编辑、删除分类

新增分类弹窗字段：

```text
分类名称
分类说明
```

普通分类支持编辑和删除。

删除确认文案：

```text
删除分类不会删除文献，该分类下的文献会自动移动到“未分类”。
```

“未分类”不展示删除入口。

### 5.3 上传文献选择分类

上传弹窗新增：

```text
文献分类：[选择已有分类]
```

默认选中“未分类”。上传时将 `categoryId` 加入 `FormData`。

### 5.4 文献列表展示和修改分类

文献列表标题区域显示分类标签：

```text
标题
作者 · 年份 · 期刊 · 文件大小
[分类名称]
```

操作区增加“改分类”，弹窗选择目标分类并调用：

```http
PATCH /api/papers/{id}/category
```

### 5.5 Dashboard 和问答页增强

Dashboard：

- 加载分类列表。
- 展示前几个分类及文献数量，形成分类概览。

问答页：

- 右侧参考论文列表显示分类信息：

```text
标题
分类 · 作者 · 年份
```

本阶段不做“按分类一键选择所有论文”，避免扩大范围。

## 6. 验证方案

后端验证：

```bash
# 注意：当前命令行默认 JAVA_HOME 可能是 JDK 8，运行 Spring Boot 测试前需要切到 JDK 21。
./mvnw test
```

分类接口 Apifox 验证：

```http
POST /api/paper-categories
GET /api/paper-categories
PUT /api/paper-categories/{id}
DELETE /api/paper-categories/{id}
```

文献接口 Apifox 验证：

```http
POST /api/papers/upload  # form-data 带 categoryId
GET /api/papers?categoryId=2
PATCH /api/papers/{id}/category
```

前端验证：

```bash
node --test src/api/papers.test.js
node --test src/api/paperCategories.test.js
node --test src/api/rag.test.js
node --test src/views/ragChatState.test.js
npm run build
```

人工验收：

1. 创建分类 A。
2. 创建分类 B。
3. 上传文献时选择分类 A。
4. 文献列表按分类 A 可看到该文献。
5. 修改该文献到分类 B。
6. 分类 B 下可看到该文献，分类 A 下不再显示。
7. 删除分类 B。
8. 该文献自动进入“未分类”。
9. 在 `/chat` 参考论文列表中可看到该文献分类为“未分类”。
10. 选择该文献进行问答，RAG 流程正常。

## 7. 分阶段实施与进度记录

### 阶段 1：数据库与后端分类基础能力

开始前更新 `docs/status/current.md`，记录阶段计划。

完成内容：

- 新增 `paper_category` 表设计。
- 增加默认“未分类”。
- 新增分类实体、Mapper、Service、Controller 和 DTO。
- 实现分类增删改查。

完成后更新 `docs/status/current.md`，记录完成内容、验证结果和下一步。

### 阶段 2：文献上传、列表、修改分类后端增强

开始前更新 `docs/status/current.md`，记录阶段计划。

完成内容：

- `PaperReference` 增加分类字段。
- `/api/papers` 支持分类筛选。
- `/api/papers/upload` 支持 `categoryId`。
- 新增 `/api/papers/{id}/category`。

完成后更新 `docs/status/current.md`，记录完成内容、验证结果和下一步。

### 阶段 3：前端文献页分类管理

开始前更新 `docs/status/current.md`，记录阶段计划。

完成内容：

- 新增分类 API helper 和测试。
- 文献页支持分类文件夹、新建、编辑、删除、筛选、上传选择分类和修改文献分类。

完成后更新 `docs/status/current.md`，记录完成内容、验证结果和下一步。

### 阶段 4：Dashboard 和问答页展示增强

开始前更新 `docs/status/current.md`，记录阶段计划。

完成内容：

- Dashboard 展示分类概览。
- 问答页参考论文列表展示分类名称。

完成后更新 `docs/status/current.md`，记录完成内容、验证结果和下一步。

### 阶段 5：整体验证与版本提交

开始前更新 `docs/status/current.md`，记录阶段计划。

完成内容：

- 运行后端测试。
- 运行前端 API helper 测试。
- 运行前端构建。
- 整理 Apifox 和人工验收步骤。
- 更新 `docs/status/current.md` 为功能完成状态。
- 提交功能版本 commit。

建议 commit message：

```text
Add literature category folder management

Co-Authored-By: Claude <noreply@anthropic.com>
```
