# 小标题识别恢复优化设计

日期：2026-07-10

## 背景

当前 PDF 结构化分块已经支持章节识别、段落分块、table/figure_caption 标记和噪声过滤，但用户反馈当前版本对“大标题下面的小标题/分标题”识别效果变差。结合最近工作记录，标题识别规则经历过收紧、词数阈值实验和再放宽，当前问题不是要整体回退分块能力，而是需要恢复旧版本较好的小标题召回能力，同时继续控制表格行、长句、模型名和指标行误判为 section title。

## 目标

1. 恢复并增强二级/三级小标题识别，例如 `3.1 Data preprocessing`、`4.2 Evaluation metrics`、`Model Complexity and Efficiency Analysis`。
2. 保留当前已有的 PDF 清洗、chunk 粒度、chunk_type 标记和画像任务相关能力，不做整体回退。
3. 避免常见负例被误识别为章节标题，例如表格表头、图表标题、正文长句、指标行、单个模型名和数据集描述行。

## 非目标

1. 不重构整个解析链路。
2. 不改画像任务进度、前端文献处理队列或 Qdrant 逻辑。
3. 不追求一次性解决所有 PDFBox 抽取乱序问题；本轮聚焦标题候选规则。

## 方案

采用“宽松召回 + 后置过滤”的标题识别策略：

1. 编号标题优先识别：支持 `3.1`、`3.1.1`、`4.2` 等常见小节编号形式，只要后续文本不是明显句子或表格/指标内容，就识别为 section title。
2. 无编号短标题适度放宽：允许论文中常见 Title Case 或技术短语小标题，例如 `Ablation Study`、`Parameter Sensitivity and Optimization Analysis`、`Encoder and Decoder Stacks`。
3. 增加小标题正向关键词：dataset、evaluation、metric、baseline、ablation、parameter、sensitivity、complexity、efficiency、architecture、module、training、implementation、preprocessing、feature、attention、encoder、decoder、optimizer、loss 等。
4. 增加负例过滤：过滤 Table/Figure 行、表格表头、指标密集行、正文句式开头、过长句子、单个模型名/缩写和数据集描述行。

## 修改范围

主要修改：

- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/PaperSectionDetector.java`
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/PaperSectionDetectorTest.java`

必要时小改：

- `backend/research-assistant-backend/src/main/java/com/myagent/assistant/paper/structure/SectionType.java`
- `backend/research-assistant-backend/src/test/java/com/myagent/assistant/paper/structure/StructuredChunkingServiceTest.java`

## 测试与验证

新增或增强测试：

1. 小标题正例：验证 `3.1 Data preprocessing`、`3.2 Model architecture`、`4.2 Evaluation metrics`、`Ablation Study` 等能被识别。
2. 误判负例：验证表格表头、指标行、正文长句、单个模型名不会被识别为 section title。

验证命令：

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest test
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PaperSectionDetectorTest,StructuredChunkingServiceTest test
JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

## 成功标准

1. 单元测试覆盖的小标题均能识别为 section。
2. 单元测试覆盖的表格/正文/模型名负例不会被识别为 section。
3. 不破坏当前结构化分块、chunk_type 和已有测试。
4. 后续可用真实论文重新解析验证 section 数量和标题质量是否改善。
