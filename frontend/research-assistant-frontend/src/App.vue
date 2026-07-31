<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink to="/" class="top-brand">
        <span class="brand-mark">RA</span>
        <span>
          <strong>Research Desk</strong>
          <small>论文研究助手</small>
        </span>
      </RouterLink>

      <nav class="top-nav" aria-label="主导航">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/papers">文献</RouterLink>
        <RouterLink to="/chat">问答</RouterLink>
        <RouterLink to="/ideas">想法</RouterLink>
        <RouterLink to="/agent-projects">复现项目</RouterLink>
        <RouterLink to="/evaluation">评测</RouterLink>
      </nav>

      <el-tag type="primary" effect="plain">16 篇 · 40 题</el-tag>
    </header>

    <main class="workspace">
      <RouterView />
    </main>
  </div>
</template>
