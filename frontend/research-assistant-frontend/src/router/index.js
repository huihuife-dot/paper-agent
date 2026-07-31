import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import PaperManagementView from '../views/PaperManagementView.vue'
import RagChatView from '../views/RagChatView.vue'
import ChatHistoryView from '../views/ChatHistoryView.vue'
import ResearchIdeasView from '../views/ResearchIdeasView.vue'
import RagEvaluationView from '../views/RagEvaluationView.vue'
import AgentProjectsView from '../views/AgentProjectsView.vue'

const routes = [
  {
    path: '/agent-projects', name: 'agent-projects', component: AgentProjectsView, meta: { title: '复现项目' },
  },
  {
    path: '/',
    name: 'dashboard',
    component: DashboardView,
    meta: {
      title: '首页',
    },
  },
  {
    path: '/papers',
    name: 'papers',
    component: PaperManagementView,
    meta: {
      title: '文献管理',
    },
  },
  {
    path: '/chat',
    name: 'chat',
    component: RagChatView,
    meta: {
      title: 'RAG 问答',
    },
  },
  {
    path: '/chat-history',
    name: 'chat-history',
    component: ChatHistoryView,
    meta: {
      title: '对话历史',
    },
  },
  {
    path: '/ideas',
    name: 'ideas',
    component: ResearchIdeasView,
    meta: {
      title: 'Research Idea',
    },
  },
  {
    path: '/evaluation',
    name: 'evaluation',
    component: RagEvaluationView,
    meta: {
      title: 'RAG 评测',
    },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
