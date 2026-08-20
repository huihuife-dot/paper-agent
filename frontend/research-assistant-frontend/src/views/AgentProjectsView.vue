<template>
  <section class="page-panel agent-projects-page">
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">Agent</p>
        <h1>复现项目</h1>
        <p>查看文献复现和 Idea 代码改进的当前状态、最近交接和会话记录。</p>
      </div>
      <el-button type="primary" @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="projects" class="agent-project-table" border>
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
      <el-table-column label="代码版本" min-width="250">
        <template #default="{ row }">
          <div v-if="row.gitProject" class="git-state">
            <span>分支：{{ row.gitProject.agentBranch || '—' }}</span>
            <span>提交：{{ shortCommit(row.gitProject.latestCommit) }}</span>
          </div>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="远程与下载" width="250" fixed="right">
        <template #default="{ row }">
          <div v-if="row.gitProject" class="project-actions">
            <el-link v-if="row.gitProject.remoteUrl" type="primary" :href="row.gitProject.remoteUrl" target="_blank">Gitee</el-link>
            <el-link v-if="row.gitProject.deliveryDownloadUrl" type="success" :href="deliveryDownloadUrl(row.gitProject)" target="_blank">代码 ZIP</el-link>
            <el-button v-if="row.gitProject.remoteUrl" link type="primary" @click="refreshRow(row)">同步</el-button>
          </div>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="最近更新" width="190" />
      <el-table-column prop="summaryPath" label="本地交接记录" min-width="220" />
    </el-table>
    <div v-loading="loading" class="agent-project-list">
      <article v-for="row in projects" :key="row.projectId || `${row.mode}-${row.sourceId}`" class="agent-project-card">
        <div class="project-card-heading">
          <div>
            <span class="project-card-label">来源</span>
            <strong>{{ sourceLabel(row) }}</strong>
          </div>
          <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'info'">{{ label(row.status) }}</el-tag>
        </div>

        <div class="project-card-grid">
          <div>
            <span class="project-card-label">已完成轮次</span>
            <strong>{{ row.sessionCount || 0 }}</strong>
          </div>
          <div>
            <span class="project-card-label">最近更新</span>
            <strong>{{ row.updatedAt || '—' }}</strong>
          </div>
        </div>

        <div v-if="row.evidenceReadiness" class="project-card-section">
          <span class="project-card-label">复现证据</span>
          <div class="evidence-state">
            <el-tag size="small" :type="evidenceTag(row.evidenceReadiness)">{{ evidenceLabel(row.evidenceReadiness) }}</el-tag>
            <span>{{ decisionSummary(row.evidenceDecisionCounts) }}</span>
          </div>
        </div>

        <div class="project-card-section">
          <span class="project-card-label">最近交接</span>
          <p>{{ row.summary || '项目已准备完成，等待本地 Agent 开始。' }}</p>
        </div>

        <div v-if="row.gitProject" class="project-card-section">
          <span class="project-card-label">代码版本</span>
          <div class="git-state">
            <span>分支：{{ row.gitProject.agentBranch || '—' }}</span>
            <span>提交：{{ shortCommit(row.gitProject.latestCommit) }}</span>
          </div>
        </div>

        <div v-if="row.summaryPath" class="project-card-section">
          <span class="project-card-label">本地交接记录</span>
          <p class="path-value">{{ row.summaryPath }}</p>
        </div>

        <div v-if="row.gitProject" class="project-card-actions">
          <el-button v-if="row.gitProject.remoteUrl" tag="a" :href="row.gitProject.remoteUrl" target="_blank">打开 Gitee</el-button>
          <el-button v-if="row.gitProject.deliveryDownloadUrl" tag="a" type="success" plain :href="deliveryDownloadUrl(row.gitProject)" target="_blank">下载代码 ZIP</el-button>
          <el-button v-if="row.gitProject.remoteUrl" type="primary" plain @click="refreshRow(row)">同步版本</el-button>
        </div>
      </article>
    </div>
    <el-empty v-if="!loading && !projects.length" description="还没有复现项目。请先在文献或 Idea 页面启动本地 Agent。" />
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import apiClient from '../api/client.js'
import { deliveryDownloadUrl, listAgentGitProjects, refreshGitProject } from '../api/agentDelivery.js'

const projects = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [projectResult, gitProjects] = await Promise.all([
      apiClient.get('/api/agent-projects'),
      listAgentGitProjects().catch(() => []),
    ])
    const base = projectResult.data ?? projectResult ?? []
    const bySource = new Map((gitProjects || []).map((item) => [`${item.mode}:${item.sourceId}`, item]))
    const merged = base.map((item) => ({ ...item, gitProject: bySource.get(`${String(item.mode || '').toLowerCase()}:${item.sourceId ?? item.paperId ?? item.ideaId}`) }))
    const known = new Set(merged.map((item) => `${String(item.mode || '').toLowerCase()}:${item.sourceId ?? item.paperId ?? item.ideaId}`))
    const gitOnly = (gitProjects || []).filter((item) => !known.has(`${item.mode}:${item.sourceId}`)).map((item) => ({
      projectId: item.projectId, mode: item.mode, sourceId: item.sourceId, status: item.status,
      summary: item.message, updatedAt: item.updatedAt, gitProject: item,
    }))
    projects.value = [...gitOnly, ...merged]
  } finally {
    loading.value = false
  }
}

function sourceLabel(row) {
  const type = row.sourceType === 'IDEA' || String(row.mode).toLowerCase() === 'idea' ? 'Idea' : '文献'
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

function shortCommit(value) { return value ? value.slice(0, 8) : '—' }

async function refreshRow(row) {
  try {
    await refreshGitProject(row.gitProject.mode, row.gitProject.sourceId)
    await load()
    ElMessage.success('已同步 Gitee 外部 Agent 分支')
  } catch (error) {
    ElMessage.error(error.message || '同步失败')
  }
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
.git-state { display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.project-actions { display: flex; gap: 12px; }
.agent-project-list { display: none; }

@media (max-width: 1200px) {
  .agent-project-table { display: none; }
  .agent-project-list { display: grid; gap: 12px; }
  .agent-project-card {
    display: grid;
    gap: 14px;
    padding: 16px 0;
    border: 0;
    border-bottom: 1px solid var(--line);
    border-radius: 0;
    background: transparent;
  }
  .project-card-heading,
  .project-card-grid {
    display: flex;
    justify-content: space-between;
    gap: 16px;
  }
  .project-card-heading > div,
  .project-card-grid > div { min-width: 0; }
  .project-card-grid { padding: 12px 0; border-block: 1px solid var(--line); }
  .project-card-label { display: block; margin-bottom: 5px; color: var(--muted); font-size: 12px; }
  .project-card-section { min-width: 0; }
  .project-card-section p { margin: 0; color: var(--ink-soft); line-height: 1.6; }
  .path-value { overflow-wrap: anywhere; font-size: 12px; }
  .project-card-actions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
  .project-card-actions .el-button { width: 100%; margin-left: 0; }
}

@media (max-width: 520px) {
  .project-card-grid { flex-direction: column; }
  .project-card-actions { grid-template-columns: 1fr; }
}
</style>
