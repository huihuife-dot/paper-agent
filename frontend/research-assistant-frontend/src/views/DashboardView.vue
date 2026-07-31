<template>
  <section class="page-panel dashboard-page">
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">Overview</p>
        <h1>研究工作台首页</h1>
        <p>查看当前研究资产状态和最近活动，再进入文献、问答或想法模块继续工作。</p>
      </div>
      <div class="console-toolbar-actions">
        <el-button plain @click="router.push('/papers')">文献库</el-button>
        <el-button plain @click="router.push('/chat')">论文问答</el-button>
        <el-button plain @click="router.push('/evaluation')">评测结果</el-button>
        <el-button type="primary" plain :loading="loading" @click="loadDashboard">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" class="workbench-card dashboard-overview-card">
      <div class="panel-scroll">
        <div class="overview-stat-strip">
          <div v-for="item in summaryStats" :key="item.label" class="overview-stat-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <div class="overview-lower-grid">
          <section class="overview-panel">
            <div class="card-header">
              <span>最近会话</span>
              <el-tag type="info" effect="plain">{{ recentSessions.length }} 条</el-tag>
            </div>
            <div class="dashboard-list">
              <el-empty v-if="recentSessions.length === 0" description="暂无会话，先进入论文问答。" />
              <button
                v-for="session in recentSessions"
                v-else
                :key="session.id"
                class="dashboard-list-item dashboard-link-item"
                type="button"
                @click="router.push({ path: '/chat', query: { sessionId: session.id } })"
              >
                <strong>{{ session.title || `Session #${session.id}` }}</strong>
                <span>#{{ session.id }} · {{ formatDateTime(session.updateTime || session.createTime) }}</span>
              </button>
            </div>
          </section>

          <section class="overview-panel">
            <div class="card-header">
              <span>最近想法</span>
              <el-tag type="info" effect="plain">{{ recentIdeas.length }} 条</el-tag>
            </div>
            <div class="dashboard-list">
              <el-empty v-if="recentIdeas.length === 0" description="暂无研究想法，可从论文问答中保存。" />
              <button
                v-for="idea in recentIdeas"
                v-else
                :key="idea.id"
                class="dashboard-list-item dashboard-link-item"
                type="button"
                @click="router.push('/ideas')"
              >
                <strong>{{ idea.title || '未命名想法' }}</strong>
                <span>{{ saveTypeLabel(idea.saveType) }} · {{ formatDateTime(idea.updateTime || idea.createTime) }}</span>
              </button>
            </div>
          </section>

          <section class="overview-panel">
            <div class="card-header">
              <span>文献分类</span>
              <el-tag type="info" effect="plain">{{ categories.length }} 类</el-tag>
            </div>
            <div class="dashboard-list">
              <el-empty v-if="categories.length === 0" description="暂无分类，先进入文献库创建。" />
              <button
                v-for="category in topCategories"
                v-else
                :key="category.id"
                class="dashboard-list-item dashboard-link-item"
                type="button"
                @click="router.push('/papers')"
              >
                <strong>{{ category.name }}</strong>
                <span>{{ category.paperCount ?? 0 }} 篇文献</span>
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listPapers } from '../api/papers.js'
import { listPaperCategories } from '../api/paperCategories.js'
import { listChatSessions } from '../api/chatHistory.js'
import { countResearchIdeaSaveTypes, listResearchIdeas } from '../api/researchIdeas.js'

const router = useRouter()

const loading = ref(false)
const papers = ref([])
const sessions = ref([])
const ideas = ref([])
const categories = ref([])
const ideaStats = ref({ draft: 0, idea: 0, todo: 0, implemented: 0, total: 0 })

const paperStats = computed(() => ({
  total: papers.value.length,
  parsed: papers.value.filter((paper) => paper.parseStatus === 'parsed' || paper.parseStatus === 'success').length,
  vectorized: papers.value.filter((paper) => paper.vectorStatus === 'vectorized' || paper.vectorStatus === 'success').length,
}))

const summaryStats = computed(() => [
  { label: '文献总数', value: paperStats.value.total },
  { label: '已解析', value: paperStats.value.parsed },
  { label: '已向量化', value: paperStats.value.vectorized },
  { label: '历史会话', value: sessions.value.length },
  { label: '研究想法', value: ideaStats.value.total },
])

const recentSessions = computed(() => sessions.value.slice(0, 8))
const recentIdeas = computed(() => ideas.value.slice(0, 8))
const topCategories = computed(() => categories.value.slice(0, 8))

async function loadDashboard() {
  loading.value = true

  try {
    const [paperResult, categoryResult, sessionResult, ideaResult, ideaStatsResult] = await Promise.all([
      listPapers(),
      listPaperCategories(),
      listChatSessions(),
      listResearchIdeas(),
      countResearchIdeaSaveTypes(),
    ])

    papers.value = Array.isArray(paperResult) ? paperResult : []
    categories.value = Array.isArray(categoryResult) ? categoryResult : []
    sessions.value = Array.isArray(sessionResult) ? sessionResult : []
    ideas.value = Array.isArray(ideaResult) ? ideaResult : []
    ideaStats.value = {
      draft: ideaStatsResult?.draft ?? 0,
      idea: ideaStatsResult?.idea ?? 0,
      todo: ideaStatsResult?.todo ?? 0,
      implemented: ideaStatsResult?.implemented ?? 0,
      total: ideaStatsResult?.total ?? 0,
    }
  } catch (error) {
    ElMessage.error(`首页数据加载失败：${error.message}`)
  } finally {
    loading.value = false
  }
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
    implemented: '已完成',
  }

  return labels[value] || value || '未知'
}

onMounted(loadDashboard)
</script>
