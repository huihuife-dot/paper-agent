<template>
  <el-dialog v-model="visible" :title="title" width="920px">
    <el-alert type="info" :closable="false" show-icon>
      <template #title>网页不会直接启动本机 Agent。请在本机 PowerShell 依次执行下面命令；每个项目都保存在独立工作目录。</template>
    </el-alert>
    <p v-if="mode === 'idea'" class="launch-note">Idea 改进模式还需要你先准备 `repository-baseline.json`，它声明 Agent 可修改的目录和可执行的基础验证命令。</p>
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
      <el-button type="primary" size="large" :loading="bootstrapping" :disabled="loadingSpec || !spec" @click="bootstrapPaper">开始准备</el-button>
      <el-alert v-if="bridgeResult" type="success" :closable="false" title="准备完成。你现在可以开始让 Agent 复现这篇论文。" />
      <el-button v-if="bridgeResult" type="success" :loading="running" @click="startRun">打开复现助手</el-button>
      <p v-if="running">复现助手已在本机窗口打开，正在自动复现代码。</p>
      <el-alert v-if="run?.status === 'CHAT_READY'" type="success" :closable="false" title="首轮代码已完成。本地助手正等待你在 CLI 窗口中继续提出修改。" />
      <el-alert v-if="run?.status === 'COMPLETED'" type="success" :closable="false" title="本次对话已结束，代码已保留在本地项目中。" />
    </div>
    <div v-else class="bridge-box">
      <h3>按想法改进代码</h3>
      <el-button type="primary" :loading="bootstrapping" @click="bootstrapIdea">选择本地代码项目</el-button>
      <el-button v-if="bridgeResult" type="success" :loading="running" @click="startIdeaRun">开始按 Idea 改进</el-button>
      <p>这个功能下一步会让你在本机窗口中选择已有项目，再由 Agent 根据想法提出修改方案。为了避免误改你的代码，当前不会自动开始。</p>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { bootstrapPaperWithBridge } from '../researchEngineering/localBridge.js'
import { runPaperWithBridge, selectIdeaWorkspace, runIdeaWithBridge } from '../researchEngineering/localBridge.js'
import { getPaperReproductionContext, getPaperReproductionSpec, rebuildPaperReproductionSpec } from '../api/papers.js'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  mode: { type: String, required: true },
  sourceId: { type: Number, required: true },
})
const emit = defineEmits(['update:modelValue'])
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
const title = computed(() => props.mode === 'paper' ? `复现论文 #${props.sourceId}` : `按 Idea #${props.sourceId} 改进代码`)
const bridgeUrl = ref('http://127.0.0.1:8765')
const bridgeToken = ref('')
const bootstrapping = ref(false)
const bridgeResult = ref(null)
const run = ref(null); const running = ref(false); let poller
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

watch(() => [props.modelValue, props.mode, props.sourceId], loadSpecAndPreview, { immediate: true })

async function startRun() { running.value = true; run.value = await runPaperWithBridge(bridgeUrl.value, props.sourceId); poller = window.setInterval(async () => { const response = await fetch(`${bridgeUrl.value}/v1/paper-projects/${props.sourceId}/run`); run.value = await response.json(); if (run.value.status !== 'RUNNING') { clearInterval(poller); running.value = false } }, 1200) }

async function bootstrapPaper() {
  bootstrapping.value = true
  bridgeResult.value = null
  try {
    bridgeResult.value = await bootstrapPaperWithBridge({ bridgeUrl: bridgeUrl.value, token: bridgeToken.value, paperId: props.sourceId })
    bridgeToken.value = ''
    ElMessage.success('复现项目已准备完成')
  } catch (error) {
    ElMessage.error('本地助手尚未启动。请先双击“启动本地科研Agent”，再回到这里点击开始准备。')
  } finally {
    bootstrapping.value = false
  }
}
async function bootstrapIdea(){ bootstrapping.value=true; try{ bridgeResult.value=await selectIdeaWorkspace(bridgeUrl.value,props.sourceId); ElMessage.success('代码项目和 Idea 已准备完成') }catch(e){ ElMessage.error('未能选择代码目录，请确认本地科研 Agent 已启动') }finally{bootstrapping.value=false} }
async function startIdeaRun(){ running.value=true; try{ run.value=await runIdeaWithBridge(bridgeUrl.value,props.sourceId) }finally{ running.value=false } }
</script>

<style scoped>
.launch-note { margin: 12px 0 0; color: var(--el-text-color-regular); }
.command-list { padding-left: 22px; }
.command-row { display: flex; gap: 8px; align-items: flex-start; }
.bridge-box { display: grid; gap: 8px; margin: 14px 0; padding: 12px; border: 1px solid var(--el-border-color); border-radius: 6px; }
.readiness-panel { min-height: 96px; padding: 12px; border-radius: 6px; background: var(--el-fill-color-lighter); }
.readiness-heading, .count-grid { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 18px; }
.readiness-heading { margin-bottom: 12px; }
.readiness-heading .el-button { margin-left: auto; }
.count-grid { color: var(--el-text-color-regular); font-size: 14px; }
.compact-list { margin-top: 10px; color: var(--el-text-color-regular); font-size: 14px; }
.evidence-item { padding: 8px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.evidence-item:last-child { border-bottom: 0; }
.evidence-item p { margin: 5px 0 0; color: var(--el-text-color-regular); white-space: pre-wrap; }
code { flex: 1; padding: 10px; border-radius: 4px; background: var(--el-fill-color-light); overflow-wrap: anywhere; white-space: pre-wrap; }
</style>
