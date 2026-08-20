<template>
  <div class="app-shell">
    <header class="app-topbar">
      <div class="topbar-inner">
        <RouterLink to="/" class="app-brand">
          <span class="brand-mark">RA</span>
          <span class="brand-copy">
            <strong>Research Desk</strong>
            <small>论文研究助手</small>
          </span>
        </RouterLink>

        <nav class="top-nav scroll-clean" aria-label="主导航">
          <RouterLink
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            :class="{ 'router-link-active': isWorkspaceActive(item) }"
          >
            <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </RouterLink>
        </nav>

        <div class="topbar-status" aria-label="当前知识库规模">
          <span></span>
          16 篇文献
        </div>
      </div>
    </header>

    <main class="workspace">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
const navItems = [
  { to: '/', label: '首页', icon: '⌂' },
  { to: '/papers', label: '文献', icon: '▤' },
  { to: '/knowledge', label: '知识', icon: '◇' },
  { to: '/chat', label: '问答', icon: '◉' },
  { to: '/ideas', label: '想法', icon: '✦' },
  { to: '/agent-projects', label: '复现', icon: '⌘' },
  { to: '/evaluation', label: '评测', icon: '✓' },
]

function isWorkspaceActive(item) {
  return item.to === '/chat' && route.path === '/writing'
}
</script>
