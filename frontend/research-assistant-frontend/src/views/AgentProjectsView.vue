<template>
  <section class="page-panel">
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">Agent</p>
        <h1>复现项目</h1>
        <p>查看文献复现和 Idea 代码改进的当前状态、最近交接和会话记录。</p>
      </div>
      <el-button type="primary" @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="projects" border>
      <el-table-column label="来源" width="150">
        <template #default="{ row }">{{ sourceLabel(row) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="130">
        <template #default="{ row }"><el-tag :type="row.status === 'COMPLETED' ? 'success' : 'info'">{{ label(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="sessionCount" label="已完成轮次" width="120">
        <template #default="{ row }">{{ row.sessionCount || 0 }}</template>
      </el-table-column>
      <el-table-column label="复现证据" width="190">
        <template #default="{ row }">
          <div v-if="row.evidenceReadiness" class="evidence-state">
            <el-tag size="small" :type="evidenceTag(row.evidenceReadiness)">{{ evidenceLabel(row.evidenceReadiness) }}</el-tag>
            <span>{{ decisionSummary(row.evidenceDecisionCounts) }}</span>
          </div>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="summary" label="最近交接" min-width="360">
        <template #default="{ row }">{{ row.summary || '项目已准备完成，等待本地 Agent 开始。' }}</template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="最近更新" width="190" />
      <el-table-column prop="summaryPath" label="本地交接记录" min-width="220" />
    </el-table>
    <el-empty v-if="!loading && !projects.length" description="还没有复现项目。请先在文献或 Idea 页面启动本地 Agent。" />
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import apiClient from '../api/client.js'

const projects = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await apiClient.get('/api/agent-projects')
    projects.value = result.data ?? result ?? []
  } finally {
    loading.value = false
  }
}

function sourceLabel(row) {
  const type = row.sourceType === 'IDEA' ? 'Idea' : '文献'
  const id = row.sourceId ?? row.paperId ?? row.ideaId ?? '-'
  return `${type} #${id}`
}

function label(value) {
  return value === 'COMPLETED' ? '已完成'
    : value === 'RUNNING' ? '正在执行'
      : value === 'CONTEXT_READY' ? '准备完成'
        : value === 'PAUSED' ? '等待继续'
        : value || '等待中'
}

function evidenceLabel(value) {
  return value === 'READY' ? '证据就绪'
    : value === 'PARTIAL' ? '部分就绪'
      : value === 'NEEDS_REVIEW' ? '存在冲突'
        : value === 'NOT_READY' ? '证据不足'
          : value
}

function evidenceTag(value) {
  return value === 'READY' ? 'success'
    : value === 'NEEDS_REVIEW' || value === 'NOT_READY' ? 'warning'
      : 'info'
}

function decisionSummary(counts) {
  if (!counts) return '尚无代码决策记录'
  const backed = counts.EVIDENCE_BACKED || 0
  const defaults = counts.SAFE_DEFAULT || 0
  const blocked = counts.BLOCKED || 0
  return `证据实现 ${backed} · 默认 ${defaults} · 阻塞 ${blocked}`
}

onMounted(load)
</script>

<style scoped>
.evidence-state {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
