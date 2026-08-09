<script setup lang="ts">
/**
 * 根组件 - 顶部导航 + RouterView.
 *
 * <p>V1 简单布局, 后续 milestone 加 sidebar / breadcrumbs / 暗色主题.
 * <p>启动时确保有 demo token (调 /api/auth/demo-token 一次).
 */
import { onMounted } from 'vue';
import { RouterLink, RouterView } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
onMounted(async () => {
  // 后台静默拉 token, 不阻塞首屏
  auth.ensureToken().catch(() => {
    // 失败不打扰 UI, 各页面调 API 时会再触发
  });
});
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="brand">
        <span class="logo">⚓</span>
        <span class="title">shipyard</span>
        <span class="subtitle">统一构建发布平台</span>
      </div>
      <nav class="nav">
        <RouterLink to="/" exact-active-class="active">总览</RouterLink>
        <RouterLink to="/projects" active-class="active">项目</RouterLink>
        <RouterLink to="/envs" active-class="active">环境</RouterLink>
        <RouterLink to="/ai" active-class="active">AI</RouterLink>
      </nav>
      <div class="status">
        <span v-if="auth.tokenInfo" class="ok">✓ 已登录 ({{ auth.tokenInfo.userId }})</span>
        <span v-else class="warn">未登录</span>
      </div>
    </header>
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: #1f2937;
  color: #f9fafb;
  border-bottom: 1px solid #374151;
}
.brand {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.brand .logo {
  font-size: 20px;
}
.brand .title {
  font-weight: 700;
  font-size: 18px;
}
.brand .subtitle {
  font-size: 12px;
  color: #9ca3af;
  margin-left: 4px;
}
.nav {
  display: flex;
  gap: 16px;
  flex: 1;
  margin-left: 32px;
}
.nav a {
  color: #d1d5db;
  text-decoration: none;
  font-size: 14px;
  padding: 4px 8px;
  border-radius: 4px;
}
.nav a:hover {
  background: #374151;
}
.nav a.active {
  background: #3b82f6;
  color: #fff;
}
.status {
  font-size: 12px;
}
.status .ok {
  color: #6ee7b7;
}
.status .warn {
  color: #fcd34d;
}
.app-main {
  flex: 1;
  padding: 24px;
  background: #fafafa;
}
</style>
