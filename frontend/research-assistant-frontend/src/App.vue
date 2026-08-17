<template>
  <div class="app-shell">
    <aside class="desktop-sidebar">
      <RouterLink to="/" class="app-brand">
        <span class="brand-mark">RA</span>
        <span class="brand-copy">
          <strong>Research Desk</strong>
          <small>论文研究助手</small>
        </span>
      </RouterLink>

      <nav class="side-nav" aria-label="主导航">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to">
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-footer">
        <span>当前知识库</span>
        <strong>16 篇文献 · 40 道评测题</strong>
      </div>
    </aside>

    <section class="app-stage">
      <header class="mobile-header">
        <RouterLink to="/" class="app-brand compact-brand">
          <span class="brand-mark">RA</span>
          <strong>Research Desk</strong>
        </RouterLink>
        <span class="mobile-status">16 篇文献</span>
      </header>

      <main class="workspace">
        <RouterView />
      </main>

      <nav class="mobile-bottom-nav" aria-label="移动端主导航">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to">
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>
          <span>{{ item.shortLabel }}</span>
        </RouterLink>
      </nav>
    </section>
  </div>
</template>

<script setup>
const navItems = [
  { to: '/', label: '工作台', shortLabel: '首页', icon: '⌂' },
  { to: '/papers', label: '文献管理', shortLabel: '文献', icon: '▤' },
  { to: '/chat', label: '论文问答', shortLabel: '问答', icon: '◉' },
  { to: '/ideas', label: 'Research Idea', shortLabel: '想法', icon: '✦' },
  { to: '/agent-projects', label: '复现项目', shortLabel: '项目', icon: '⌘' },
  { to: '/evaluation', label: '系统评测', shortLabel: '评测', icon: '✓' },
]
</script>
