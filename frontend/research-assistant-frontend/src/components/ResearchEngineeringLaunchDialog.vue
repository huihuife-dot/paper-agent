<template>
  <el-dialog
    v-model="visible"
    class="agent-launch-dialog"
    :title="title"
    width="min(920px, calc(100vw - 32px))"
    append-to-body
    align-center
  >
    <el-alert type="info" :closable="false" show-icon>
      <template #title>同一份结构化任务可以交给平台 Agent、下载给其他 Agent，或发布到 Gitee 供其他终端协作。</template>
    </el-alert>
    <div class="execution-choice">
      <strong>选择执行方式</strong>
      <el-radio-group v-model="executionMode">
        <el-radio value="PLATFORM">使用平台 Agent</el-radio>
        <el-radio value="PACKAGE">下载任务包 ZIP</el-radio>
        <el-radio value="GITEE">发布到 Gitee，使用外部 Agent</el-radio>
      </el-radio-group>
    </div>
    <p v-if="mode === 'idea'" class="launch-note">Idea 改进需要填写服务器上已有代码目录；该目录必须位于配置的根目录内，并包含 `repository-baseline.json`，用来声明允许修改的路径和基础验证命令。</p>
    <div v-if="mode === 'paper'" class="bridge-box">
      <h3>准备复现项目</h3>
      <p>先检查论文事实是否足够，再把带原文出处的证据交给本机 Agent。模型推断和冲突项只会作为提醒，不会冒充确定参数。</p>
      <div v-loading="loadingSpec" class="readiness-panel">
        <template v-if="spec">
          <div class="readiness-heading">
            <el-tag :type="statusType">{{ statusText }}</el-tag>
            <span>关键项覆盖 {{ percent(spec.criticalCoverage) }}</span>
            <span>来源完整 {{ percent(spec.provenanceCoverage) }}</span>
            <el-button size="small" :loading="rebuildingSpec" @click="rebuildSpec">重新计算</el-button>
          </div>
          <div class="count-grid">
            <span>可信事实 {{ spec.trustedFacts?.length || 0 }}</span>
            <span>待复核 {{ spec.reviewRequiredFacts?.length || 0 }}</span>
            <span>模型推断 {{ spec.modelInferredFacts?.length || 0 }}</span>
            <span>冲突 {{ spec.conflicts?.length || 0 }}</span>
            <span>缺失 {{ spec.missingInformation?.length || 0 }}</span>
          </div>
          <el-alert
            v-if="spec.conflicts?.length || spec.missingInformation?.length"
            type="warning"
            :closable="false"
            title="Agent 会先报告这些冲突和缺口，不会自动猜一个值。"
          />
          <div v-if="spec.missingInformation?.length" class="compact-list">
            <strong>仍缺少：</strong>{{ spec.missingInformation.join('；') }}
          </div>
          <el-collapse v-if="evidencePreview.length">
            <el-collapse-item title="查看将交给 Agent 的证据预览">
              <div v-for="item in evidencePreview" :key="`${item.evidenceType}-${item.sourceId}-${item.pageNumber}`" class="evidence-item">
                <div>
                  <el-tag size="small" effect="plain">{{ evidenceLabel(item) }}</el-tag>
                  <span v-if="item.pageNumber">第 {{ item.pageNumber }} 页</span>
                  <span v-if="item.verificationStatus"> · {{ item.verificationStatus }}</span>
                </div>
                <p>{{ item.content }}</p>
              </div>
            </el-collapse-item>
          </el-collapse>
        </template>
        <el-empty v-else :image-size="64" description="尚未生成复现规格">
          <el-button type="primary" :loading="rebuildingSpec" @click="rebuildSpec">生成复现规格</el-button>
        </el-empty>
      </div>
      <el-button v-if="executionMode === 'PLATFORM'" class="primary-action" type="primary" size="large" :loading="bootstrapping" :disabled="loadingSpec || !spec" @click="startPaperRun">使用平台 Agent 开始复现</el-button>
      <p v-if="run?.status === 'RUNNING'">{{ run.phase }}，Agent 正在服务器工作区运行。</p>
      <el-alert v-if="run?.status === 'COMPLETED'" type="success" :closable="false" title="本轮复现已完成，代码和交接已保留在服务器工作区。" />
      <el-alert v-if="run?.status === 'FAILED'" type="error" :closable="false" :title="run.message" />
      <template v-if="executionMode === 'PLATFORM'">
        <el-button v-if="run?.stoppable" type="warning" plain :loading="stopping" @click="stopRun">停止当前任务</el-button>
        <pre v-if="run?.recentOutput?.length" class="agent-output">{{ run.recentOutput.join('\n') }}</pre>
      </template>
    </div>
    <div v-else class="bridge-box">
      <h3>按想法改进代码</h3>
      <el-input v-if="executionMode !== 'PACKAGE'" v-model="ideaWorkspacePath" placeholder="例如 E:\\AgentIdeaWorkspaces\\existing-project" clearable />
      <el-button v-if="executionMode === 'PLATFORM'" class="primary-action" type="primary" :loading="bootstrapping" @click="startIdeaRun">使用平台 Agent 开始改进</el-button>
      <p v-if="run?.status === 'RUNNING'">{{ run.phase }}，Agent 正在服务器工作区运行。</p>
      <el-alert v-if="run?.status === 'COMPLETED'" type="success" :closable="false" title="本轮代码改进已完成，代码和交接已保留在服务器工作区。" />
      <el-alert v-if="run?.status === 'FAILED'" type="error" :closable="false" :title="run.message" />
      <template v-if="executionMode === 'PLATFORM'">
        <el-button v-if="run?.stoppable" type="warning" plain :loading="stopping" @click="stopRun">停止当前任务</el-button>
        <pre v-if="run?.recentOutput?.length" class="agent-output">{{ run.recentOutput.join('\n') }}</pre>
      </template>
    </div>
    <div v-if="executionMode === 'PACKAGE'" class="delivery-box">
      <h3>交给其他 Agent 的结构化任务包</h3>
      <p>ZIP 包含任务、证据及中文交付说明。已发布外部任务时会附仓库、分支和起始版本；未绑定时仅离线交付。发布或更换任务后请重新生成，旧包不会自动更新。</p>
      <el-button class="primary-action" type="primary" :loading="exporting" :disabled="mode === 'paper' && !spec" @click="exportPackage">生成并下载任务包</el-button>
      <el-link v-if="packageResult" type="success" :href="packageHref" target="_blank">重新下载 {{ packageResult.fileName }}</el-link>
    </div>
    <div v-if="executionMode === 'GITEE'" class="delivery-box">
      <h3>发布到 Gitee</h3>
      <p>系统先建立本地 Git 基线和外部 Agent 分支，再创建私有 Gitee 仓库并推送。外部 Agent 克隆后只修改自己的分支。</p>
      <el-input v-if="!gitProject?.remoteUrl" v-model="giteeRepositoryName" placeholder="Gitee 仓库名" />
      <el-button v-if="!gitProject?.remoteUrl" class="primary-action" type="primary" :loading="publishing || loadingGitProject" :disabled="gitLookupFailed || (mode === 'paper' && !spec)" @click="publishGitee">创建仓库并推送任务</el-button>
      <p v-if="gitLookupFailed">读取已有仓库失败，请重新打开窗口后再发布，避免重复创建任务。</p>
      <el-alert v-if="gitProject" :type="['PUSHED', 'SYNCED'].includes(gitProject.remoteStatus) ? 'success' : 'warning'" :closable="false" :title="gitProject.message" />
      <div v-if="gitProject?.remoteUrl" class="repo-actions">
        <el-link type="primary" :href="gitProject.remoteUrl" target="_blank">打开 Gitee 仓库</el-link>
        <el-button v-if="gitProject.remoteStatus === 'PUSH_PENDING'" size="small" type="warning" @click="retryPush">重试推送</el-button>
        <el-button size="small" @click="refreshRemote">刷新外部 Agent 分支</el-button>
        <el-button size="small" :loading="exporting" :disabled="!['PUSHED', 'SYNCED'].includes(gitProject.remoteStatus)" @click="exportPackage">下载任务包与交付说明</el-button>
      </div>
      <p v-if="gitProject?.agentBranch">任务分支：{{ gitProject.agentBranch }}。外部电脑需自行配置仓库权限，推送后再回平台刷新。</p>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getPaperReproductionContext, getPaperReproductionSpec, rebuildPaperReproductionSpec } from '../api/papers.js'
import { getIdeaAgentStatus, getPaperAgentStatus, startIdeaAgent, startPaperAgent, stopIdeaAgent, stopPaperAgent } from '../api/agentExecutions.js'
import { createAgentTaskPackage, packageDownloadUrl, prepareExternalGitProject, publishGitProjectToGitee, refreshGitProject, retryGitProjectPush, listAgentGitProjects } from '../api/agentDelivery.js'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  mode: { type: String, required: true },
  sourceId: { type: Number, required: true },
})
const emit = defineEmits(['update:modelValue'])
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
const title = computed(() => props.mode === 'paper' ? `复现论文 #${props.sourceId}` : `按 Idea #${props.sourceId} 改进代码`)
const bootstrapping = ref(false)
const executionMode = ref('PLATFORM')
const exporting = ref(false)
const publishing = ref(false)
const packageResult = ref(null)
const packageHref = computed(() => packageResult.value ? packageDownloadUrl(packageResult.value) : '')
const gitProject = ref(null)
const loadingGitProject = ref(false)
const gitLookupFailed = ref(false)
const giteeRepositoryName = ref('')
const run = ref(null); const stopping = ref(false); let poller
const ideaWorkspacePath = ref('')
const spec = ref(null)
const context = ref(null)
const loadingSpec = ref(false)
const rebuildingSpec = ref(false)
const evidencePreview = computed(() => (context.value?.evidence || [])
  .filter((item) => ['paper_raw_chunk', 'paper_reproduction_fact'].includes(item.kind))
  .slice(0, 8))
const statusText = computed(() => ({
  READY: '可以开始复现',
  PARTIAL: '信息部分齐全',
  NEEDS_REVIEW: '需要人工留意',
  NOT_READY: '暂不具备复现条件',
}[spec.value?.status] || spec.value?.status || '未知状态'))
const statusType = computed(() => ({
  READY: 'success',
  PARTIAL: 'warning',
  NEEDS_REVIEW: 'warning',
  NOT_READY: 'danger',
}[spec.value?.status] || 'info'))

function percent(value) {
  return `${Math.round(Number(value || 0) * 100)}%`
}

function evidenceLabel(item) {
  return item.kind === 'paper_raw_chunk' ? '关键原文' : '可信事实'
}

async function loadSpecAndPreview() {
  if (!visible.value || props.mode !== 'paper') return
  loadingSpec.value = true
  try {
    spec.value = await getPaperReproductionSpec(props.sourceId)
    context.value = spec.value ? await getPaperReproductionContext(props.sourceId, 2) : null
  } catch {
    spec.value = null
    context.value = null
  } finally {
    loadingSpec.value = false
  }
}

async function rebuildSpec() {
  rebuildingSpec.value = true
  try {
    spec.value = await rebuildPaperReproductionSpec(props.sourceId)
    context.value = await getPaperReproductionContext(props.sourceId, 2)
    ElMessage.success('复现规格和证据预览已更新')
  } catch {
    ElMessage.error('生成失败，请先在论文页面完成解析并生成复现事实')
  } finally {
    rebuildingSpec.value = false
  }
}

watch(() => [props.modelValue, props.mode, props.sourceId], async (_, __, onCleanup) => {
  let stale = false
  onCleanup(() => { stale = true })
  packageResult.value = null
  gitProject.value = null
  gitLookupFailed.value = false
  loadingGitProject.value = false
  giteeRepositoryName.value = `${props.mode === 'paper' ? 'paper-reproduction' : 'idea-improvement'}-${props.sourceId}`
  if (!props.modelValue) return
  const preview = loadSpecAndPreview()
  loadingGitProject.value = true
  try {
    const projects = await listAgentGitProjects()
    if (!stale) gitProject.value = projects.find((project) => project.mode === props.mode && Number(project.sourceId) === props.sourceId) || null
  } catch {
    if (!stale) gitLookupFailed.value = true
  } finally {
    if (!stale) loadingGitProject.value = false
  }
  if (stale) return
  await preview
}, { immediate: true })

function stopPolling() { if (poller) { window.clearInterval(poller); poller = undefined } }

function pollRun() {
  stopPolling()
  poller = window.setInterval(async () => {
    try {
      run.value = props.mode === 'paper' ? await getPaperAgentStatus(props.sourceId) : await getIdeaAgentStatus(props.sourceId)
      if (run.value.status !== 'RUNNING') stopPolling()
    } catch (error) {
      stopPolling()
      ElMessage.error(error.message || '读取 Agent 状态失败')
    }
  }, 1200)
}

async function startPaperRun() {
  bootstrapping.value = true
  try {
    run.value = await startPaperAgent(props.sourceId)
    pollRun()
    ElMessage.success('后端已开始准备论文复现任务')
  } catch (error) {
    ElMessage.error(error.message || '启动 Agent 失败，请检查服务器 Agent 配置')
  } finally {
    bootstrapping.value = false
  }
}
async function startIdeaRun() {
  bootstrapping.value = true
  try {
    run.value = await startIdeaAgent(props.sourceId, ideaWorkspacePath.value)
    pollRun()
    ElMessage.success('后端已开始 Idea 代码改进任务')
  } catch (error) {
    ElMessage.error(error.message || '启动 Agent 失败，请检查服务器目录和配置')
  } finally {
    bootstrapping.value = false
  }
}
async function stopRun() {
  stopping.value = true
  try {
    run.value = props.mode === 'paper' ? await stopPaperAgent(props.sourceId) : await stopIdeaAgent(props.sourceId)
    stopPolling()
  } catch (error) {
    ElMessage.error(error.message || '停止任务失败')
  } finally {
    stopping.value = false
  }
}
async function exportPackage() {
  exporting.value = true
  try {
    packageResult.value = await createAgentTaskPackage(props.mode, props.sourceId)
    window.open(packageDownloadUrl(packageResult.value), '_blank', 'noopener')
    ElMessage.success('结构化任务包已生成')
  } catch (error) {
    ElMessage.error(error.message || '生成任务包失败')
  } finally {
    exporting.value = false
  }
}
async function publishGitee() {
  publishing.value = true
  packageResult.value = null
  try {
    gitProject.value = await prepareExternalGitProject(props.mode, props.sourceId, ideaWorkspacePath.value)
    gitProject.value = await publishGitProjectToGitee(props.mode, props.sourceId, giteeRepositoryName.value, `MyAgent ${props.mode} #${props.sourceId}`)
    if (gitProject.value.remoteStatus === 'PUSHED') ElMessage.success('仓库已推送，请下载含交付地址的新任务包')
    else ElMessage.warning('仓库已创建但推送未完成，请先重试推送')
  } catch (error) {
    ElMessage.error(error.message || '发布 Gitee 失败，请检查 Token 和服务器 SSH Key')
  } finally {
    publishing.value = false
  }
}
async function refreshRemote() {
  try {
    packageResult.value = null
    gitProject.value = await refreshGitProject(props.mode, props.sourceId)
    ElMessage.success('已刷新远程分支')
  } catch (error) {
    ElMessage.error(error.message || '刷新 Gitee 失败')
  }
}
async function retryPush() {
  try {
    packageResult.value = null
    gitProject.value = await retryGitProjectPush(props.mode, props.sourceId)
    ElMessage.success('本地提交已推送到 Gitee')
  } catch (error) {
    ElMessage.error(error.message || '推送仍然失败，请检查服务器 SSH Key')
  }
}
</script>

<style scoped>
.launch-note { margin: 14px 0 0; color: var(--el-text-color-regular); line-height: 1.65; }
.command-list { padding-left: 22px; }
.command-row { display: flex; gap: 8px; align-items: flex-start; }
.bridge-box { display: grid; gap: 12px; margin: 14px 0; padding: 16px; border: 1px solid var(--line); border-radius: 16px; background: var(--surface); }
.bridge-box h3, .delivery-box h3 { margin: 0; color: var(--ink); }
.bridge-box > p, .delivery-box > p { margin: 0; color: var(--muted); line-height: 1.65; }
.readiness-panel { min-height: 96px; padding: 14px; border-radius: 12px; background: var(--surface-soft); }
.readiness-heading, .count-grid { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 18px; }
.readiness-heading { margin-bottom: 12px; }
.readiness-heading .el-button { margin-left: auto; }
.count-grid { color: var(--el-text-color-regular); font-size: 14px; }
.compact-list { margin-top: 10px; color: var(--el-text-color-regular); font-size: 14px; }
.evidence-item { padding: 8px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.evidence-item:last-child { border-bottom: 0; }
.evidence-item p { margin: 5px 0 0; color: var(--el-text-color-regular); white-space: pre-wrap; }
code { flex: 1; padding: 10px; border-radius: 8px; background: var(--el-fill-color-light); overflow-wrap: anywhere; white-space: pre-wrap; }
.agent-output { max-height: 220px; overflow: auto; margin: 0; padding: 12px; border-radius: 10px; background: #202123; color: #f7f7f8; white-space: pre-wrap; font-size: 12px; }
.execution-choice, .delivery-box { display: grid; gap: 12px; margin-top: 14px; padding: 16px; border: 1px solid var(--line); border-radius: 16px; background: var(--surface); }
.execution-choice .el-radio-group { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.execution-choice :deep(.el-radio) {
  width: 100%;
  height: auto;
  min-height: 54px;
  margin-right: 0;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-soft);
  white-space: normal;
}
.execution-choice :deep(.el-radio.is-checked) { border-color: var(--brand); background: var(--surface-blue); }
.execution-choice :deep(.el-radio__label) { padding-left: 8px; line-height: 1.45; white-space: normal; }
.repo-actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.repo-actions .el-button { margin-left: 0; }
.primary-action { justify-self: start; min-height: 40px; margin-left: 0; }
:deep(.agent-launch-dialog .el-dialog__body) { max-height: calc(100vh - 150px); overflow-y: auto; padding-top: 10px; }

@media (max-width: 720px) {
  .execution-choice .el-radio-group { grid-template-columns: 1fr; }
  .readiness-heading { align-items: flex-start; }
  .readiness-heading .el-button { width: 100%; margin-left: 0; }
  .count-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .primary-action { width: 100%; justify-self: stretch; }
  .repo-actions { align-items: stretch; flex-direction: column; }
  .repo-actions .el-button, .repo-actions .el-link { width: 100%; justify-content: center; }
  :deep(.agent-launch-dialog) { margin: 8px auto; }
  :deep(.agent-launch-dialog .el-dialog__header) { padding: 16px 18px 10px; }
  :deep(.agent-launch-dialog .el-dialog__body) { max-height: calc(100vh - 92px); padding: 8px 14px 18px; }
}
</style>
