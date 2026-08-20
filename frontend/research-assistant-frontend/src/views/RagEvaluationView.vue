<template>
  <section class="page-panel evaluation-page">
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">RAG Evaluation</p>
        <h1>检索质量评测</h1>
        <p>固定 16 篇论文与 40 道真实问题，展示优化前后效果、验收门槛和已知限制。</p>
      </div>
      <div class="console-toolbar-actions">
        <el-tag type="success" effect="dark">已达到停止门槛</el-tag>
        <el-button plain @click="router.push('/chat')">进入论文问答</el-button>
      </div>
    </div>

    <nav class="section-tab-bar evaluation-tabs" aria-label="评测内容切换">
      <button type="button" :class="{ active: activeTab === 'overview' }" @click="activeTab = 'overview'">评测概览</button>
      <button type="button" :class="{ active: activeTab === 'metrics' }" @click="activeTab = 'metrics'">核心指标</button>
      <button type="button" :class="{ active: activeTab === 'breakdown' }" @click="activeTab = 'breakdown'">分类结果</button>
      <button type="button" :class="{ active: activeTab === 'notes' }" @click="activeTab = 'notes'">限制与简历描述</button>
    </nav>

    <div class="workbench-card evaluation-card">
      <div class="panel-scroll evaluation-scroll">
        <section v-if="activeTab === 'overview'" class="evaluation-hero">
          <div>
            <p class="eyebrow">Final Stage · {{ summary.evaluatedAt }}</p>
            <h2>全库发现 Recall 提升 {{ signedDelta(discoveryRecall) }}</h2>
            <p>检索优化已结束。当前页面只展示固定终验结果，不触发新的模型调用。</p>
          </div>
          <div class="evaluation-scope">
            <strong>{{ summary.corpusSize }}</strong><span>篇论文</span>
            <strong>{{ summary.caseCount }}</strong><span>道问题</span>
            <strong>{{ summary.executionSuccessRate }}%</strong><span>请求成功</span>
          </div>
        </section>

        <section v-if="activeTab === 'metrics'" class="evaluation-section evaluation-single-view">
          <div class="evaluation-section-heading">
            <div>
              <p class="eyebrow">Before / After</p>
              <h2>核心指标对比</h2>
            </div>
            <span>蓝色为最终值，浅色为优化前基线</span>
          </div>

          <div class="evaluation-metric-grid">
            <article v-for="metric in summary.metrics" :key="metric.key" class="evaluation-metric-card">
              <div class="metric-title-row">
                <strong>{{ metric.label }}</strong>
                <el-tag :type="metricPassed(metric) ? 'success' : 'warning'" effect="plain">
                  {{ signedDelta(metric) }}
                </el-tag>
              </div>
              <div class="metric-value-row">
                <span>{{ formatMetric(metric.baseline, metric.unit) }}</span>
                <b>→</b>
                <strong>{{ formatMetric(metric.final, metric.unit) }}</strong>
              </div>
              <div class="metric-track" aria-hidden="true">
                <span class="metric-baseline" :style="{ width: `${metric.baseline}%` }"></span>
                <span class="metric-final" :style="{ width: `${metric.final}%` }"></span>
              </div>
              <small v-if="metric.target != null">验收门槛：{{ metric.target }}{{ metric.unit }}</small>
              <small v-else>总体质量指标</small>
            </article>
          </div>
        </section>

        <section v-if="activeTab === 'overview'" class="evaluation-two-column evaluation-overview-panels">
          <div class="evaluation-section evaluation-panel-block">
            <div class="evaluation-section-heading">
              <div>
                <p class="eyebrow">Quality Gates</p>
                <h2>质量护栏</h2>
              </div>
            </div>
            <div class="quality-gate-grid">
              <div v-for="gate in summary.qualityGates" :key="gate.label" class="quality-gate-item">
                <span>{{ gate.label }}</span>
                <strong>{{ gate.value }}{{ gate.unit }}</strong>
                <el-tag type="success" size="small">通过</el-tag>
              </div>
            </div>
          </div>

          <div class="evaluation-section evaluation-panel-block">
            <div class="evaluation-section-heading">
              <div>
                <p class="eyebrow">Latency</p>
                <h2>性能说明</h2>
              </div>
            </div>
            <div class="latency-summary">
              <div><span>最终平均延迟</span><strong>{{ summary.latency.finalAverageSeconds }}s</strong></div>
              <div><span>完整 P95</span><strong>{{ summary.latency.finalP95Seconds }}s</strong></div>
              <div><span>全库发现 P95</span><strong>{{ summary.latency.discoveryP95Seconds }}s</strong></div>
            </div>
            <el-alert type="warning" :closable="false" show-icon>
              完整 P95 受多篇长回答生成影响，本轮不把延迟包装为优化成果。
            </el-alert>
          </div>
        </section>

        <section v-if="activeTab === 'breakdown'" class="evaluation-section evaluation-single-view">
          <div class="evaluation-section-heading">
            <div>
              <p class="eyebrow">Breakdown</p>
              <h2>分类结果</h2>
            </div>
          </div>
          <el-table :data="summary.categories" stripe>
            <el-table-column prop="label" label="分类" min-width="150" />
            <el-table-column prop="cases" label="题数" width="90" />
            <el-table-column label="Recall" width="120">
              <template #default="scope">{{ nullablePercent(scope.row.recall) }}</template>
            </el-table-column>
            <el-table-column label="Precision" width="120">
              <template #default="scope">{{ nullablePercent(scope.row.precision) }}</template>
            </el-table-column>
            <el-table-column label="平均延迟" width="130">
              <template #default="scope">{{ scope.row.averageLatencySeconds }}s</template>
            </el-table-column>
          </el-table>
        </section>

        <section v-if="activeTab === 'notes'" class="evaluation-two-column evaluation-single-view">
          <div class="evaluation-section evaluation-panel-block">
            <div class="evaluation-section-heading">
              <div>
                <p class="eyebrow">Known Limits</p>
                <h2>已知限制</h2>
              </div>
            </div>
            <ol class="evaluation-limit-list">
              <li v-for="item in summary.limitations" :key="item">{{ item }}</li>
            </ol>
          </div>

          <div class="evaluation-section evaluation-panel-block resume-copy-card">
            <div class="evaluation-section-heading">
              <div>
                <p class="eyebrow">Project Summary</p>
                <h2>项目量化描述</h2>
              </div>
              <el-button size="small" plain @click="copyResumeDescription">复制</el-button>
            </div>
            <p>{{ summary.resumeDescription }}</p>
          </div>
        </section>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { metricDelta, metricPassed, ragEvaluationSummary } from '../evaluation/ragEvaluationSummary.js'

const router = useRouter()
const activeTab = ref('overview')
const summary = ragEvaluationSummary
const discoveryRecall = computed(() => summary.metrics.find((metric) => metric.key === 'discoveryRecall'))

function formatMetric(value, unit) {
  return `${Number(value).toFixed(2)}${unit}`
}

function nullablePercent(value) {
  return value == null ? '人工拒答 100%' : `${Number(value).toFixed(2)}%`
}

function signedDelta(metric) {
  const delta = metricDelta(metric)
  return `${delta > 0 ? '+' : ''}${delta.toFixed(2)} 个百分点`
}

async function copyResumeDescription() {
  try {
    await navigator.clipboard.writeText(summary.resumeDescription)
    ElMessage.success('项目描述已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}
</script>
