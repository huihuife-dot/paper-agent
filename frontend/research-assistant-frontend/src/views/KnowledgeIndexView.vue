<template>
  <section class="page-panel knowledge-page">
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">Research knowledge</p>
        <h1>科研知识索引</h1>
        <p>把论文加工成目录、知识单元和原文证据。问答会优先使用这些结构化资料，必要时才调用 RAG 补漏。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-dropdown trigger="click" @command="downloadDataset">
          <el-button plain>导出可信数据</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="SILVER">Gold + Silver</el-dropdown-item>
              <el-dropdown-item command="GOLD">仅 Gold</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="knowledge-summary-strip" aria-label="知识库概况">
      <div><strong>{{ completedCount }} / {{ catalogs.length }}</strong><span>已建立目录</span></div>
      <div><strong>{{ totalKnowledgeCount }}</strong><span>知识单元</span></div>
      <div><strong>{{ totalVerifiedCount }}</strong><span>人工确认</span></div>
      <div><strong>SQL → RAG</strong><span>当前取证顺序</span></div>
    </div>

    <nav class="section-tab-bar knowledge-tabs" aria-label="知识工作区切换">
      <button type="button" :class="{ active: activeView === 'catalog' }" @click="activeView = 'catalog'">
        论文目录 <span>{{ catalogs.length }}</span>
      </button>
      <button type="button" :class="{ active: activeView === 'topics' }" @click="activeView = 'topics'">
        主题导航 <span>{{ topics.length }}</span>
      </button>
      <button type="button" :disabled="!selectedCatalog" :class="{ active: activeView === 'detail' }" @click="activeView = 'detail'">
        知识详情 <span>{{ units.length }}</span>
      </button>
    </nav>

    <div class="knowledge-focus-panel">
    <el-card v-if="activeView === 'topics'" class="knowledge-topic-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>全库主题导航</strong>
            <p>这些标签来自已经构建的知识单元，点击后可以查看关联论文。</p>
          </div>
          <div class="topic-type-tabs">
            <button
              v-for="type in topicTypes"
              :key="type.value"
              type="button"
              :class="{ active: topicType === type.value }"
              @click="topicType = type.value; loadTopics()"
            >
              {{ type.label }}
            </button>
          </div>
        </div>
      </template>
      <div v-loading="topicsLoading" class="topic-cloud">
        <button
          v-for="topic in topics"
          :key="`${topic.knowledgeType}-${topic.label}`"
          type="button"
          class="topic-pill"
          @click="focusTopic(topic)"
        >
          <span>{{ topic.label }}</span>
          <small>{{ topic.paperCount }} 篇 · {{ topic.unitCount }} 条</small>
        </button>
        <el-empty v-if="!topicsLoading && topics.length === 0" description="构建论文知识后，这里会形成主题导航。" />
      </div>
    </el-card>

    <el-card v-else-if="activeView === 'catalog'" class="knowledge-catalog-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>论文目录</strong>
            <p>每篇论文只在入库或内容变化时构建一次，后续问答可以重复使用。</p>
          </div>
          <el-input v-model="keyword" clearable placeholder="搜索论文或知识标签" class="catalog-search" />
        </div>
      </template>

      <el-table v-loading="loading" :data="filteredCatalogs" class="knowledge-table" empty-text="暂无论文">
        <el-table-column label="论文" min-width="260">
          <template #default="{ row }">
            <strong>{{ row.paperTitle || `Paper #${row.paperId}` }}</strong>
            <p class="catalog-meta">{{ row.publishYear || '年份未知' }} · {{ catalogSummary(row) }}</p>
          </template>
        </el-table-column>
        <el-table-column label="知识标签" min-width="300">
          <template #default="{ row }">
            <div class="catalog-tags">
              <el-tag v-for="tag in catalogTags(row)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
              <span v-if="catalogTags(row).length === 0" class="muted-text">尚未构建</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="160">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
            <p class="catalog-meta">{{ row.knowledgeCount || 0 }} 条 · {{ row.verifiedCount || 0 }} 条确认</p>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <div class="compact-action-row">
              <el-button size="small" plain :disabled="!row.knowledgeCount" @click="selectCatalog(row)">查看</el-button>
              <el-button
                size="small"
                type="primary"
                :loading="buildingPaperId === row.paperId || row.status === 'BUILDING'"
                @click="handleBuild(row)"
              >
                {{ row.knowledgeCount ? '重建' : '构建' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-else-if="selectedCatalog" class="knowledge-unit-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>{{ selectedCatalog.paperTitle }}</strong>
            <p>知识内容必须保留证据来源。Bronze 需要谨慎，Silver 有原文匹配，Gold 已人工确认。</p>
          </div>
          <div class="unit-filter-row">
            <el-select v-model="unitType" clearable placeholder="全部知识类型" @change="loadUnits">
              <el-option v-for="type in unitTypes" :key="type" :label="knowledgeTypeLabel(type)" :value="type" />
            </el-select>
            <el-button text @click="selectedCatalog = null; units = []; activeView = 'catalog'">返回目录</el-button>
          </div>
        </div>
      </template>

      <div v-loading="unitsLoading" class="knowledge-unit-list">
        <article v-for="unit in units" :key="unit.id" class="knowledge-unit-item">
          <header>
            <div>
              <el-tag size="small" effect="plain">{{ knowledgeTypeLabel(unit.knowledgeType) }}</el-tag>
              <el-tag size="small" :type="confidenceType(unit.confidenceLevel)" effect="plain">{{ unit.confidenceLevel }}</el-tag>
              <el-tag v-if="unit.hasConflict" size="small" type="danger" effect="plain">存在冲突</el-tag>
            </div>
            <span>{{ unit.sectionTitle || unit.sourceType }}<template v-if="unit.pageNumber"> · 第 {{ unit.pageNumber }} 页</template></span>
          </header>
          <h3>{{ unit.subjectText || '论文' }} · {{ unit.predicateText || '说明' }}</h3>
          <p class="unit-value">{{ unit.objectValue }}<template v-if="unit.valueUnit"> {{ unit.valueUnit }}</template></p>
          <p v-if="unit.applicableCondition" class="unit-condition">适用条件：{{ unit.applicableCondition }}</p>
          <blockquote v-if="unit.evidenceText">{{ unit.evidenceText }}</blockquote>
          <footer>
            <span>{{ feedbackLabel(unit.verificationStatus) }}</span>
            <div>
              <el-button size="small" text type="success" :disabled="unit.verificationStatus === 'CONFIRMED'" @click="handleFeedback(unit, 'CONFIRM')">确认</el-button>
              <el-button size="small" text type="primary" @click="openCorrection(unit)">纠正</el-button>
              <el-button size="small" text type="danger" @click="handleFeedback(unit, 'REJECT')">拒绝</el-button>
            </div>
          </footer>
        </article>
        <el-empty v-if="!unitsLoading && units.length === 0" description="当前筛选条件下没有知识单元。" />
      </div>
    </el-card>
    <el-empty v-else description="请先从论文目录选择一篇已构建知识的论文。" />
    </div>

    <el-dialog v-model="correctionVisible" title="纠正知识单元" width="620px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="知识内容">
          <el-input v-model="correctionForm.objectValue" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="适用条件">
          <el-input v-model="correctionForm.applicableCondition" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="correctionForm.comment" placeholder="说明为什么需要纠正，便于后续形成高质量反馈数据" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="correctionVisible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackLoading" @click="submitCorrection">保存纠正</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  buildPaperKnowledge,
  exportKnowledgeDataset,
  listKnowledgeCatalogs,
  listKnowledgeTopics,
  listPaperKnowledge,
  submitKnowledgeFeedback,
} from '../api/knowledge.js'

const catalogs = ref([])
const topics = ref([])
const units = ref([])
const loading = ref(false)
const topicsLoading = ref(false)
const unitsLoading = ref(false)
const feedbackLoading = ref(false)
const buildingPaperId = ref(null)
const selectedCatalog = ref(null)
const activeView = ref('catalog')
const keyword = ref('')
const topicType = ref('')
const unitType = ref('')
const correctionVisible = ref(false)
const correctionUnit = ref(null)
const correctionForm = reactive({ objectValue: '', applicableCondition: '', comment: '' })

const topicTypes = [
  { label: '全部', value: '' },
  { label: '研究任务', value: 'RESEARCH_TASK' },
  { label: '方法', value: 'METHOD' },
  { label: '数据集', value: 'DATASET' },
  { label: '指标', value: 'METRIC' },
]
const unitTypes = [
  'RESEARCH_PROBLEM', 'RESEARCH_TASK', 'METHOD', 'MODEL_COMPONENT', 'DATASET',
  'INPUT_VARIABLE', 'EXPERIMENT_SETTING', 'METRIC', 'RESULT', 'COMPARISON',
  'CONTRIBUTION', 'LIMITATION', 'CONCLUSION', 'FUTURE_WORK',
]
const typeLabels = {
  RESEARCH_DOMAIN: '研究领域', RESEARCH_TASK: '研究任务', RESEARCH_PROBLEM: '研究问题',
  BACKGROUND: '技术背景', METHOD: '方法', MODEL_COMPONENT: '模型组件', DATASET: '数据集',
  INPUT_VARIABLE: '输入变量', EXPERIMENT_SETTING: '实验设置', METRIC: '评价指标',
  RESULT: '实验结果', COMPARISON: '对比结论', CONTRIBUTION: '主要贡献',
  LIMITATION: '局限', CONCLUSION: '结论', FUTURE_WORK: '未来工作', KEYWORD: '关键词',
}

const completedCount = computed(() => catalogs.value.filter((item) => ['COMPLETED', 'PARTIAL'].includes(item.status)).length)
const totalKnowledgeCount = computed(() => catalogs.value.reduce((sum, item) => sum + Number(item.knowledgeCount || 0), 0))
const totalVerifiedCount = computed(() => catalogs.value.reduce((sum, item) => sum + Number(item.verifiedCount || 0), 0))
const filteredCatalogs = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  if (!normalized) return catalogs.value
  return catalogs.value.filter((item) => [
    item.paperTitle,
    ...(item.researchDomains || []), ...(item.researchTasks || []), ...(item.methodTags || []),
    ...(item.datasetTags || []), ...(item.metricTags || []),
  ].filter(Boolean).join(' ').toLowerCase().includes(normalized))
})

async function loadAll() {
  loading.value = true
  try {
    catalogs.value = await listKnowledgeCatalogs()
    await loadTopics()
    if (selectedCatalog.value) {
      selectedCatalog.value = catalogs.value.find((item) => item.paperId === selectedCatalog.value.paperId) || null
      if (selectedCatalog.value) await loadUnits()
    }
  } catch (error) {
    ElMessage.error(`知识索引加载失败：${error.message}`)
  } finally {
    loading.value = false
  }
}

async function loadTopics() {
  topicsLoading.value = true
  try {
    topics.value = await listKnowledgeTopics({ knowledgeType: topicType.value, limit: 60 })
  } catch (error) {
    ElMessage.error(`主题导航加载失败：${error.message}`)
  } finally {
    topicsLoading.value = false
  }
}

async function handleBuild(catalog) {
  if (catalog.knowledgeCount) {
    try {
      await ElMessageBox.confirm('重建会删除当前模型派生知识并重新抽取；历史反馈快照仍会保留。是否继续？', '重建知识索引', {
        confirmButtonText: '继续重建', cancelButtonText: '取消', type: 'warning',
      })
    } catch {
      return
    }
  }
  buildingPaperId.value = catalog.paperId
  try {
    await buildPaperKnowledge(catalog.paperId)
    catalog.status = 'BUILDING'
    ElMessage.success('知识构建已经启动，完成后刷新即可查看')
  } catch (error) {
    ElMessage.error(`启动构建失败：${error.message}`)
  } finally {
    buildingPaperId.value = null
  }
}

async function selectCatalog(catalog) {
  selectedCatalog.value = catalog
  activeView.value = 'detail'
  unitType.value = ''
  await loadUnits()
}

async function loadUnits() {
  if (!selectedCatalog.value) return
  unitsLoading.value = true
  try {
    units.value = await listPaperKnowledge(selectedCatalog.value.paperId, { knowledgeType: unitType.value })
  } catch (error) {
    ElMessage.error(`知识单元加载失败：${error.message}`)
  } finally {
    unitsLoading.value = false
  }
}

async function handleFeedback(unit, action) {
  feedbackLoading.value = true
  try {
    const updated = await submitKnowledgeFeedback(unit.id, { action })
    replaceUnit(updated)
    ElMessage.success(action === 'CONFIRM' ? '已确认为 Gold 知识' : '已拒绝该知识')
    if (action === 'REJECT') units.value = units.value.filter((item) => item.id !== unit.id)
    await loadAll()
  } catch (error) {
    ElMessage.error(`反馈保存失败：${error.message}`)
  } finally {
    feedbackLoading.value = false
  }
}

function openCorrection(unit) {
  correctionUnit.value = unit
  correctionForm.objectValue = unit.objectValue || ''
  correctionForm.applicableCondition = unit.applicableCondition || ''
  correctionForm.comment = ''
  correctionVisible.value = true
}

async function submitCorrection() {
  if (!correctionForm.objectValue.trim()) {
    ElMessage.warning('知识内容不能为空')
    return
  }
  feedbackLoading.value = true
  try {
    const updated = await submitKnowledgeFeedback(correctionUnit.value.id, {
      action: 'CORRECT',
      objectValue: correctionForm.objectValue,
      applicableCondition: correctionForm.applicableCondition,
      comment: correctionForm.comment,
    })
    replaceUnit(updated)
    correctionVisible.value = false
    ElMessage.success('纠正已保存为 Gold 知识，并记录反馈快照')
    await loadAll()
  } catch (error) {
    ElMessage.error(`纠正保存失败：${error.message}`)
  } finally {
    feedbackLoading.value = false
  }
}

function replaceUnit(updated) {
  const index = units.value.findIndex((item) => item.id === updated.id)
  if (index >= 0) units.value.splice(index, 1, updated)
}

function focusTopic(topic) {
  keyword.value = topic.label
  const firstPaper = catalogs.value.find((item) => topic.paperIds?.includes(item.paperId))
  if (firstPaper) selectCatalog(firstPaper)
}

async function downloadDataset(confidence) {
  try {
    const data = await exportKnowledgeDataset(confidence)
    const blob = new Blob([JSON.stringify({ version: 'paper-knowledge-v1', minimumConfidence: confidence, items: data }, null, 2)], { type: 'application/json' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `paper-knowledge-${confidence.toLowerCase()}.json`
    link.click()
    URL.revokeObjectURL(link.href)
    ElMessage.success(`已导出 ${data.length} 条可信知识样本`)
  } catch (error) {
    ElMessage.error(`可信数据导出失败：${error.message}`)
  }
}

function catalogTags(row) {
  return [...(row.researchTasks || []), ...(row.methodTags || []), ...(row.datasetTags || []), ...(row.metricTags || [])].slice(0, 6)
}
function catalogSummary(row) {
  const tags = catalogTags(row)
  return tags.length ? tags.slice(0, 3).join(' / ') : '等待知识加工'
}
function knowledgeTypeLabel(type) { return typeLabels[type] || type }
function statusLabel(status) { return ({ NOT_BUILT: '未构建', BUILDING: '构建中', COMPLETED: '已完成', PARTIAL: '部分可用', FAILED: '失败', STALE: '待重建' })[status] || status }
function statusType(status) { return ({ COMPLETED: 'success', PARTIAL: 'warning', BUILDING: 'primary', FAILED: 'danger', STALE: 'warning' })[status] || 'info' }
function confidenceType(level) { return ({ GOLD: 'success', SILVER: 'primary', BRONZE: 'warning' })[level] || 'info' }
function feedbackLabel(status) { return ({ AUTO: '尚未人工确认', CONFIRMED: '已人工确认', CORRECTED: '已人工纠正', REJECTED: '已拒绝' })[status] || status }

onMounted(loadAll)
</script>

<style scoped>
.knowledge-page { display: flex; gap: 12px; }
.knowledge-summary-strip { flex: 0 0 auto; display: flex; align-items: center; gap: 0; padding: 10px 0; overflow-x: auto; border: 0; border-radius: 0; background: transparent; }
.knowledge-summary-strip > div { flex: 0 0 auto; display: flex; align-items: baseline; gap: 6px; padding: 0 20px; border-right: 1px solid var(--line); }
.knowledge-summary-strip > div:first-child { padding-left: 0; }
.knowledge-summary-strip > div:last-child { border-right: 0; }
.knowledge-summary-strip strong { color: var(--ink); font-size: 16px; }
.knowledge-summary-strip span, .card-header p, .catalog-meta, .muted-text { color: #6b7280; font-size: 12px; }
.knowledge-tabs { flex: 0 0 auto; padding-top: 0; }
.knowledge-tabs button:disabled { color: #bbb; cursor: not-allowed; }
.knowledge-focus-panel { flex: 1 1 auto; min-height: 0; overflow: visible; }
.knowledge-focus-panel > .el-card { height: auto; display: flex; flex-direction: column; overflow: visible; border: 0; border-radius: 0; }
.knowledge-focus-panel :deep(.el-card__header) { flex: 0 0 auto; padding: 13px 0; background: transparent; }
.knowledge-focus-panel :deep(.el-card__body) { flex: 1 1 auto; min-height: 0; overflow: visible; padding: 16px 0; }
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.card-header p { margin: 4px 0 0; font-size: 13px; }
.topic-type-tabs { display: flex; flex-wrap: wrap; gap: 6px; }
.topic-type-tabs button, .topic-pill { border: 1px solid #e5e7eb; background: #fff; color: #374151; cursor: pointer; }
.topic-type-tabs button { border-radius: 999px; padding: 7px 12px; }
.topic-type-tabs button.active { background: #111827; color: #fff; border-color: #111827; }
.topic-cloud { min-height: 90px; display: flex; flex-wrap: wrap; gap: 10px; }
.topic-pill { border-radius: 14px; padding: 10px 12px; display: grid; gap: 3px; text-align: left; max-width: 260px; }
.topic-pill:hover { border-color: #9ca3af; background: #f9fafb; }
.topic-pill small { color: #6b7280; }
.catalog-search { width: min(320px, 40vw); }
.catalog-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.catalog-meta { margin: 5px 0 0; font-size: 12px; }
.knowledge-unit-list { display: grid; gap: 0; }
.knowledge-unit-item { border: 0; border-bottom: 1px solid #e5e7eb; border-radius: 0; padding: 16px 0; background: transparent; }
.knowledge-unit-item header, .knowledge-unit-item footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #6b7280; font-size: 12px; }
.knowledge-unit-item header > div { display: flex; flex-wrap: wrap; gap: 5px; }
.knowledge-unit-item h3 { margin: 12px 0 6px; font-size: 14px; color: #374151; }
.unit-value { margin: 0; color: #111827; line-height: 1.7; white-space: pre-wrap; }
.unit-condition { color: #4b5563; font-size: 13px; }
.knowledge-unit-item blockquote { margin: 12px 0; padding: 10px 12px; border-left: 3px solid #d1d5db; background: #f9fafb; color: #4b5563; line-height: 1.6; }
.unit-filter-row { display: flex; align-items: center; gap: 8px; }
.unit-filter-row .el-select { width: 190px; }
@media (max-width: 900px) {
  .card-header { align-items: flex-start; flex-direction: column; }
  .catalog-search { width: 100%; }
}
@media (max-width: 560px) {
  .knowledge-summary-strip > div { padding-inline: 12px; }
  .knowledge-table :deep(.el-table__cell) { padding: 10px 0; }
  .knowledge-unit-item header, .knowledge-unit-item footer { align-items: flex-start; flex-direction: column; }
  .unit-filter-row { width: 100%; }
  .unit-filter-row .el-select { flex: 1; width: auto; }
}
</style>
