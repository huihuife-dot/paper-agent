<template>
  <section class="page-panel dashboard-page">
    <header class="dashboard-hero">
      <p class="eyebrow">Research workspace</p>
      <h1>今天想研究什么？</h1>
      <p>从论文资料出发，继续阅读、提问、写作或验证一个研究想法。</p>
      <div class="dashboard-quick-actions">
        <button type="button" @click="router.push('/chat')"><span>◉</span>开始论文问答</button>
        <button type="button" @click="router.push('/papers')"><span>＋</span>添加一篇文献</button>
        <button type="button" @click="router.push('/writing')"><span>✎</span>进入论文写作</button>
      </div>
    </header>

    <div v-loading="loading" class="dashboard-workspace">
      <div class="dashboard-summary" aria-label="知识库概况">
        <div v-for="item in summaryStats" :key="item.label">
          <strong>{{ item.value }}</strong>
          <span>{{ item.label }}</span>
        </div>
      </div>

      <nav class="section-tab-bar" aria-label="首页内容切换">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          :class="{ active: activeTab === tab.value }"
          @click="activeTab = tab.value"
        >
          {{ tab.label }}
          <span>{{ tab.count }}</span>
        </button>
        <button class="section-tab-action" type="button" :disabled="loading" @click="loadDashboard">刷新</button>
      </nav>

      <section class="dashboard-focus-panel">
        <div v-if="activeTab === 'sessions'" class="dashboard-list">
          <el-empty v-if="recentSessions.length === 0" description="暂无会话，先进入论文问答。" />
          <button
            v-for="session in recentSessions"
            v-else
            :key="session.id"
            class="dashboard-list-item dashboard-link-item"
            type="button"
            @click="router.push({ path: '/chat', query: { sessionId: session.id } })"
          >
            <span class="dashboard-item-icon">◉</span>
            <span class="dashboard-item-copy">
              <strong>{{ session.title || `Session #${session.id}` }}</strong>
              <small>#{{ session.id }} · {{ formatDateTime(session.updateTime || session.createTime) }}</small>
            </span>
            <span class="dashboard-item-arrow">→</span>
          </button>
        </div>

        <div v-else-if="activeTab === 'ideas'" class="dashboard-list">
          <el-empty v-if="recentIdeas.length === 0" description="暂无研究想法，可从论文问答中保存。" />
          <button
            v-for="idea in recentIdeas"
            v-else
            :key="idea.id"
            class="dashboard-list-item dashboard-link-item"
            type="button"
            @click="router.push('/ideas')"
          >
            <span class="dashboard-item-icon">✦</span>
            <span class="dashboard-item-copy">
              <strong>{{ idea.title || '未命名想法' }}</strong>
              <small>{{ saveTypeLabel(idea.saveType) }} · {{ formatDateTime(idea.updateTime || idea.createTime) }}</small>
            </span>
            <span class="dashboard-item-arrow">→</span>
          </button>
        </div>

        <div v-else class="dashboard-list">
          <el-empty v-if="categories.length === 0" description="暂无分类，先进入文献库创建。" />
          <button
            v-for="category in topCategories"
            v-else
            :key="category.id"
            class="dashboard-list-item dashboard-link-item"
            type="button"
            @click="router.push('/papers')"
          >
            <span class="dashboard-item-icon">▤</span>
            <span class="dashboard-item-copy">
              <strong>{{ category.name }}</strong>
              <small>{{ category.paperCount ?? 0 }} 篇文献</small>
            </span>
            <span class="dashboard-item-arrow">→</span>
          </button>
        </div>
      </section>
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
const activeTab = ref('sessions')
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
  { label: '篇文献', value: paperStats.value.total },
  { label: '已解析', value: paperStats.value.parsed },
  { label: '已向量化', value: paperStats.value.vectorized },
  { label: '段历史会话', value: sessions.value.length },
  { label: '个研究想法', value: ideaStats.value.total },
])

const recentSessions = computed(() => sessions.value.slice(0, 8))
const recentIdeas = computed(() => ideas.value.slice(0, 8))
const topCategories = computed(() => categories.value.slice(0, 8))
const tabs = computed(() => [
  { value: 'sessions', label: '最近会话', count: recentSessions.value.length },
  { value: 'ideas', label: '研究想法', count: recentIdeas.value.length },
  { value: 'categories', label: '文献分类', count: categories.value.length },
])

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
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-'
}

function saveTypeLabel(value) {
  return { draft: '草稿', idea: '想法', todo: '待办', implemented: '已完成' }[value] || value || '未知'
}

onMounted(loadDashboard)
</script>
