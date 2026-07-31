<template>
  <section class="page-panel idea-page">
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">Ideas</p>
        <h1>想法</h1>
        <p>整理从论文问答中沉淀出的研究线索，跟踪草稿、待办和已完成状态。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" @click="openCreate">新建想法</el-button>
        <el-button type="primary" plain :loading="loading || statsLoading" @click="refreshAll">刷新</el-button>
      </div>
    </div>

    <div class="workbench-grid side-main">
      <el-card class="workflow-card workbench-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>状态概览</span>
            <el-tag type="info" effect="plain">{{ statsSummary.total }} 条</el-tag>
          </div>
        </template>

        <div class="panel-scroll sidebar-panel">
          <div class="stats-grid compact-stats-grid">
            <div v-for="item in stats" :key="item.label" class="stat-tile">
              <span>{{ saveTypeLabel(item.label) }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>

          <div class="sidebar-section">
            <p class="sidebar-section-title">关键词</p>
            <el-input v-model="keyword" placeholder="搜索标题、内容或标签" clearable />
          </div>

          <div class="sidebar-section">
            <p class="sidebar-section-title">状态筛选</p>
            <button
              v-for="item in saveTypeFilterOptions"
              :key="item.value || 'all'"
              class="sidebar-filter-item"
              :class="{ active: saveType === item.value }"
              type="button"
              @click="saveType = item.value"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </button>
          </div>

          <el-button plain class="full-width-button" :loading="loading || statsLoading" @click="refreshAll">刷新想法</el-button>
        </div>
      </el-card>

      <el-card class="workflow-card workbench-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>想法列表</span>
            <el-tag type="info" effect="plain">{{ ideas.length }} 条</el-tag>
          </div>
        </template>

        <div class="panel-scroll">
          <el-table v-loading="loading" :data="ideas" border height="100%" empty-text="暂无研究想法，可先在论文问答页保存一条草稿。">
            <el-table-column prop="title" label="标题" min-width="220">
              <template #default="{ row }">
                <strong>{{ row.title || '未命名想法' }}</strong>
                <p class="paper-meta">{{ summarizeIdea(row) }}</p>
              </template>
            </el-table-column>

            <el-table-column prop="saveType" label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="saveTypeTagType(row.saveType)" effect="plain">
                  {{ saveTypeLabel(row.saveType) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="tags" label="标签" min-width="180">
              <template #default="{ row }">
                {{ formatTags(row.tags) }}
              </template>
            </el-table-column>

            <el-table-column prop="sourceType" label="来源" width="130">
              <template #default="{ row }">
                {{ row.sourceType || '-' }}
              </template>
            </el-table-column>

            <el-table-column label="操作" width="350" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button size="small" type="success" plain @click="openAgentLaunch(row)">按此想法改进代码</el-button>
                  <el-button size="small" @click="openDetail(row)">查看</el-button>
                  <el-button size="small" type="primary" plain @click="openEdit(row)">编辑</el-button>
                  <el-dropdown trigger="click" @command="(nextSaveType) => handleSaveTypeChange(row, nextSaveType)">
                    <el-button size="small" type="primary" plain :loading="activeAction === `status-${row.id}`">
                      推进状态
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="draft" :disabled="row.saveType === 'draft'">草稿</el-dropdown-item>
                        <el-dropdown-item command="idea" :disabled="row.saveType === 'idea'">想法</el-dropdown-item>
                        <el-dropdown-item command="todo" :disabled="row.saveType === 'todo'">待办</el-dropdown-item>
                        <el-dropdown-item command="implemented" :disabled="row.saveType === 'implemented'">
                          已实现
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-button
                    size="small"
                    type="danger"
                    plain
                    :loading="activeAction === `delete-${row.id}`"
                    @click="handleDelete(row)"
                  >
                    删除
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="detailVisible" title="研究想法详情" width="720px">
      <template v-if="selectedIdea">
        <div class="source-item idea-detail-block">
          <div class="source-title-row">
            <span class="source-route">{{ selectedIdea.sourceType || 'manual' }}</span>
            <el-tag :type="saveTypeTagType(selectedIdea.saveType)" effect="plain">
              {{ saveTypeLabel(selectedIdea.saveType) }}
            </el-tag>
          </div>
          <strong>{{ selectedIdea.title || '未命名想法' }}</strong>
          <small>
            Session #{{ selectedIdea.sourceSessionId || '-' }} · Message #{{ selectedIdea.sourceMessageId || '-' }}
          </small>
          <p>{{ selectedIdea.refinedContent || selectedIdea.originalContent || '暂无内容' }}</p>
          <div class="table-actions">
            <el-button
              v-if="selectedIdea.sourceSessionId"
              size="small"
              type="primary"
              plain
              @click="openSourceSession(selectedIdea.sourceSessionId)"
            >
              打开原对话
            </el-button>
            <el-button size="small" @click="openEdit(selectedIdea)">编辑想法</el-button>
          </div>
        </div>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="研究问题">
            {{ selectedIdea.researchQuestion || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创新点">
            {{ selectedIdea.innovationPoints || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="可能方法">
            {{ selectedIdea.possibleMethod || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="标签">
            {{ formatTags(selectedIdea.tags) }}
          </el-descriptions-item>
          <el-descriptions-item label="关联论文">
            <div v-if="relatedPaperIds(selectedIdea).length" class="table-actions">
              <el-button
                v-for="paperId in relatedPaperIds(selectedIdea)"
                :key="paperId"
                size="small"
                text
                type="primary"
                @click="openRelatedPaper(paperId)"
              >
                {{ paperTitle(paperId) }}（#{{ paperId }}）
              </el-button>
            </div>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ formatDateTime(selectedIdea.updateTime) }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>

    <el-dialog
      v-model="editVisible"
      :title="editingIdea ? '编辑研究想法' : '新建研究想法'"
      width="780px"
      :close-on-click-modal="false"
    >
      <el-form :model="editForm" label-position="top">
        <div class="form-grid two-columns">
          <el-form-item label="标题" required>
            <el-input v-model="editForm.title" maxlength="255" show-word-limit placeholder="一句话概括研究想法" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="editForm.saveType" class="full-width-button">
              <el-option label="草稿" value="draft" />
              <el-option label="想法" value="idea" />
              <el-option label="待办" value="todo" />
              <el-option label="已实现" value="implemented" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="整理后的想法正文">
          <el-input v-model="editForm.refinedContent" type="textarea" :rows="5" placeholder="说明问题背景、核心设想和预期价值" />
        </el-form-item>

        <el-form-item label="研究问题">
          <el-input v-model="editForm.researchQuestion" type="textarea" :rows="3" placeholder="这个想法具体希望回答什么问题？" />
        </el-form-item>

        <div class="form-grid two-columns">
          <el-form-item label="创新点">
            <el-input v-model="editForm.innovationPoints" type="textarea" :rows="4" placeholder="多个创新点可用分号分隔" />
          </el-form-item>
          <el-form-item label="可能的方法">
            <el-input v-model="editForm.possibleMethod" type="textarea" :rows="4" placeholder="实验设计、模型或数据方案" />
          </el-form-item>
        </div>

        <div class="form-grid two-columns">
          <el-form-item label="标签">
            <el-input v-model="editForm.tags" placeholder="例如：RAG,图学习,消融实验" />
          </el-form-item>
          <el-form-item label="关联论文 ID">
            <el-input v-model="editForm.relatedPaperIds" placeholder="例如：13,15,30" />
          </el-form-item>
        </div>

        <el-form-item label="原始讨论摘要">
          <el-input v-model="editForm.originalContent" type="textarea" :rows="3" placeholder="保留想法最初来自哪段讨论" />
        </el-form-item>

        <el-alert
          v-if="editingIdea?.sourceSessionId"
          type="info"
          :closable="false"
          show-icon
          :title="`来源会话 #${editingIdea.sourceSessionId} 会被保留，编辑不会切断来源关系。`"
        />
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingEdit" @click="handleSaveIdea">
          {{ editingIdea ? '保存修改' : '创建想法' }}
        </el-button>
      </template>
    </el-dialog>
    <ResearchEngineeringLaunchDialog
      v-if="agentLaunchIdea"
      v-model="agentLaunchVisible"
      mode="idea"
      :source-id="Number(agentLaunchIdea.id)"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  countResearchIdeaSaveTypes,
  createResearchIdea,
  deleteResearchIdea,
  listResearchIdeas,
  updateResearchIdea,
  updateResearchIdeaSaveType,
} from '../api/researchIdeas.js'
import { getPaperContentUrl, listPapers } from '../api/papers.js'
import {
  buildResearchIdeaUpdatePayload,
  parseRelatedPaperIds,
} from './researchIdeaState.js'
import ResearchEngineeringLaunchDialog from '../components/ResearchEngineeringLaunchDialog.vue'

const router = useRouter()

const keyword = ref('')
const saveType = ref('')
const ideas = ref([])
const statsSummary = ref({ draft: 0, idea: 0, todo: 0, implemented: 0, total: 0 })
const loading = ref(false)
const statsLoading = ref(false)
const activeAction = ref('')
const selectedIdea = ref(null)
const detailVisible = ref(false)
const editVisible = ref(false)
const editingIdea = ref(null)
const savingEdit = ref(false)
const agentLaunchVisible = ref(false)
const agentLaunchIdea = ref(null)
const papers = ref([])
const editForm = reactive({
  title: '',
  originalContent: '',
  refinedContent: '',
  innovationPoints: '',
  researchQuestion: '',
  possibleMethod: '',
  tags: '',
  saveType: 'draft',
  relatedPaperIds: '',
})

const stats = computed(() => [
  { label: 'draft', value: statsSummary.value.draft },
  { label: 'idea', value: statsSummary.value.idea },
  { label: 'todo', value: statsSummary.value.todo },
  { label: 'implemented', value: statsSummary.value.implemented },
])

const saveTypeFilterOptions = computed(() => [
  { label: '全部', value: '', count: statsSummary.value.total },
  { label: '草稿', value: 'draft', count: statsSummary.value.draft },
  { label: '想法', value: 'idea', count: statsSummary.value.idea },
  { label: '待办', value: 'todo', count: statsSummary.value.todo },
  { label: '已实现', value: 'implemented', count: statsSummary.value.implemented },
])

function normalizeIdeaList(result) {
  if (Array.isArray(result)) {
    return result
  }

  return result?.records || result?.list || result?.data || []
}

async function loadIdeas() {
  loading.value = true

  try {
    const result = await listResearchIdeas({
      keyword: keyword.value.trim(),
      saveType: saveType.value,
    })
    ideas.value = normalizeIdeaList(result)
  } catch (error) {
    ElMessage.error(`Research Idea 列表加载失败：${error.message}`)
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  statsLoading.value = true

  try {
    const result = await countResearchIdeaSaveTypes()
    statsSummary.value = {
      draft: result?.draft ?? 0,
      idea: result?.idea ?? 0,
      todo: result?.todo ?? 0,
      implemented: result?.implemented ?? 0,
      total: result?.total ?? 0,
    }
  } catch (error) {
    ElMessage.error(`Research Idea 状态统计加载失败：${error.message}`)
  } finally {
    statsLoading.value = false
  }
}

async function loadPaperCatalog() {
  try {
    papers.value = await listPapers()
  } catch (error) {
    // 论文标题只用于增强来源展示，加载失败不应阻断 Idea 主工作流。
    papers.value = []
  }
}

async function refreshAll() {
  await Promise.all([loadIdeas(), loadStats(), loadPaperCatalog()])
}

async function handleSaveTypeChange(row, nextSaveType) {
  if (row.saveType === nextSaveType) {
    return
  }

  activeAction.value = `status-${row.id}`

  try {
    const updatedIdea = await updateResearchIdeaSaveType(row.id, nextSaveType)
    row.saveType = updatedIdea?.saveType || nextSaveType
    row.updateTime = updatedIdea?.updateTime || row.updateTime

    if (selectedIdea.value?.id === row.id) {
      selectedIdea.value = { ...selectedIdea.value, ...row, ...updatedIdea }
    }

    ElMessage.success(`状态已更新为${saveTypeLabel(row.saveType)}`)
    await loadStats()

    if (saveType.value && saveType.value !== row.saveType) {
      await loadIdeas()
    }
  } catch (error) {
    ElMessage.error(`状态更新失败：${error.message}`)
  } finally {
    activeAction.value = ''
  }
}

function openDetail(row) {
  selectedIdea.value = row
  detailVisible.value = true
}

function openAgentLaunch(row) {
  agentLaunchIdea.value = row
  agentLaunchVisible.value = true
}

function openEdit(row) {
  editingIdea.value = { ...row }
  Object.assign(editForm, {
    title: row.title || '',
    originalContent: row.originalContent || '',
    refinedContent: row.refinedContent || '',
    innovationPoints: row.innovationPoints || '',
    researchQuestion: row.researchQuestion || '',
    possibleMethod: row.possibleMethod || '',
    tags: row.tags || '',
    saveType: row.saveType || 'draft',
    relatedPaperIds: row.relatedPaperIds || '',
  })
  editVisible.value = true
}

function openCreate() {
  editingIdea.value = null
  Object.assign(editForm, {
    title: '',
    originalContent: '',
    refinedContent: '',
    innovationPoints: '',
    researchQuestion: '',
    possibleMethod: '',
    tags: '',
    saveType: 'draft',
    relatedPaperIds: '',
  })
  editVisible.value = true
}

async function handleSaveIdea() {
  const payload = buildResearchIdeaUpdatePayload(editForm, editingIdea.value)

  if (!payload.title) {
    ElMessage.warning('Research Idea 标题不能为空')
    return
  }

  savingEdit.value = true
  try {
    if (editingIdea.value) {
      const updatedIdea = await updateResearchIdea(editingIdea.value.id, payload)
      const nextIdea = { ...editingIdea.value, ...payload, ...updatedIdea }
      const index = ideas.value.findIndex((item) => item.id === nextIdea.id)
      if (index >= 0) {
        ideas.value[index] = nextIdea
      }
      if (selectedIdea.value?.id === nextIdea.id) {
        selectedIdea.value = nextIdea
      }
      ElMessage.success('研究想法已更新')
    } else {
      await createResearchIdea(payload)
      ElMessage.success('研究想法已创建')
    }
    editVisible.value = false
    await Promise.all([loadIdeas(), loadStats()])
  } catch (error) {
    ElMessage.error(`研究想法保存失败：${error.message}`)
  } finally {
    savingEdit.value = false
  }
}

function openSourceSession(sourceSessionId) {
  detailVisible.value = false
  router.push({ path: '/chat', query: { sessionId: sourceSessionId } })
}

function relatedPaperIds(idea) {
  return parseRelatedPaperIds(idea?.relatedPaperIds)
}

function paperTitle(paperId) {
  return papers.value.find((paper) => Number(paper.id) === Number(paperId))?.title || '关联论文'
}

function openRelatedPaper(paperId) {
  window.open(getPaperContentUrl(paperId), '_blank', 'noopener,noreferrer')
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title || '未命名 Idea'}」吗？`, '删除 Research Idea', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  activeAction.value = `delete-${row.id}`

  try {
    await deleteResearchIdea(row.id)
    ElMessage.success('已删除')
    await refreshAll()
  } catch (error) {
    ElMessage.error(`删除失败：${error.message}`)
  } finally {
    activeAction.value = ''
  }
}

function summarizeIdea(row) {
  const content = row.refinedContent || row.originalContent || row.researchQuestion || ''

  if (!content) {
    return '暂无摘要内容'
  }

  return content.length > 72 ? `${content.slice(0, 72)}...` : content
}

function formatTags(tags) {
  if (Array.isArray(tags)) {
    return tags.length > 0 ? tags.join(', ') : '-'
  }

  return tags || '-'
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return String(value).replace('T', ' ').slice(0, 19)
}

function saveTypeLabel(value) {
  const labels = {
    draft: '草稿',
    idea: '想法',
    todo: '待办',
    implemented: '已实现',
  }

  return labels[value] || value || '未知'
}

function saveTypeTagType(value) {
  const types = {
    draft: 'info',
    idea: 'success',
    todo: 'warning',
    implemented: 'primary',
  }

  return types[value] || 'info'
}

let filterTimer
watch([keyword, saveType], () => {
  clearTimeout(filterTimer)
  filterTimer = setTimeout(loadIdeas, 300)
})

onMounted(refreshAll)
</script>
