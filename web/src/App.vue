<script setup lang="ts">
/**
 * shipyard App shell — sidebar + topbar + RouterView + CommandPalette.
 *
 * <p>布局 (M3 polish + M13 Phase 2):
 * <ul>
 *   <li>Left sidebar 64px 折叠, hover 展开到 240px (工程单手操作友好)</li>
 *   <li>Top bar 56px: breadcrumb + 全局命令面板触发器 + 通知 + 登录状态</li>
 *   <li>Main 滚动区, 暗色主题</li>
 *   <li>全局 Cmd+K 调出命令面板 (类似 Linear / GitHub)</li>
 * </ul>
 */
import { onMounted, ref } from 'vue';
import { RouterLink, RouterView, useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import CommandPalette from '@/components/CommandPalette.vue';

const auth = useAuthStore();
const route = useRoute();

// sidebar hover 展开
const sidebarHovered = ref(false);

// --- SVG 路径 (Feather 风格, 24x24) ---
const IconHome = 'M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10';
const IconFolder = 'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z';
const IconServer = 'M2 3h20v8H2z M2 13h20v8H2z M6 7h.01 M6 17h.01';
const IconSparkles = 'M12 3l1.9 5.5L19 10l-5.1 1.5L12 17l-1.9-5.5L5 10l5.1-1.5z M19 4l.7 1.7L21 6l-1.3.3L19 8l-.7-1.7L17 6l1.3-.3z M5 16l.7 1.7L7 18l-1.3.3L5 20l-.7-1.7L3 18l1.3-.3z';
const IconActivity = 'M22 12h-4l-3 9L9 3l-3 9H2';
const IconHistory = 'M3 12a9 9 0 1 0 9-9 9.74 9.74 0 0 0-6.74 2.74L3 8 M3 3v5h5 M12 7v5l4 2';
const IconLogo = 'M2 20a4 4 0 0 0 4-4V8a2 2 0 0 1 4 0v8a4 4 0 0 0 8 0V6 M6 12h.01 M18 18h.01';
const IconBook = 'M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z';

const navItems = [
  { to: '/',             label: '总览',     icon: IconHome,     shortcut: 'G H' },
  { to: '/projects',     label: '项目',     icon: IconFolder,   shortcut: 'G P' },
  { to: '/envs',         label: '环境',     icon: IconServer,   shortcut: 'G E' },
  { to: '/monitoring',   label: '监控',     icon: IconActivity, shortcut: 'G M' },
  { to: '/activity',     label: '活动',     icon: IconHistory,  shortcut: 'G L' },
  { to: '/ai/diagnosis', label: 'AI 助手',  icon: IconSparkles, shortcut: 'G A' },
];

/** 路由对应 sidebar 选中状态 */
function isActive(to: string): boolean {
  if (to === '/') return route.path === '/';
  return route.path === to || route.path.startsWith(to + '/');
}

// 命令面板引用 + 打开方法
const paletteRef = ref<InstanceType<typeof CommandPalette> | null>(null);
function openCommandPalette() {
  if (paletteRef.value) {
    (paletteRef.value as unknown as { visible: boolean }).visible = true;
  }
}

onMounted(async () => {
  auth.ensureToken().catch(() => {
    // 失败不打扰 UI, 各页面调 API 时会再触发
  });
});
</script>

<template>
  <div class="app-shell">
    <!-- ===== Sidebar ===== -->
    <aside
      class="sidebar"
      :class="{ expanded: sidebarHovered }"
      @mouseenter="sidebarHovered = true"
      @mouseleave="sidebarHovered = false"
    >
      <RouterLink to="/" class="brand" :title="'shipyard'">
        <svg class="brand-mark" viewBox="0 0 24 24" width="28" height="28" aria-hidden="true">
          <path :d="IconLogo" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span class="brand-text">
          <span class="brand-name">shipyard</span>
          <span class="brand-sub">v1.0 · demo</span>
        </span>
      </RouterLink>

      <nav class="nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          :class="['nav-item', { active: isActive(item.to) }]"
        >
          <svg class="nav-icon" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
            <path :d="item.icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span class="nav-label">{{ item.label }}</span>
          <kbd class="nav-shortcut">{{ item.shortcut }}</kbd>
        </RouterLink>
      </nav>

      <div class="sidebar-footer">
        <a class="nav-item" href="https://github.com/yeyanghua/shipyard" target="_blank" rel="noopener" title="GitHub">
          <svg class="nav-icon" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
            <path :d="IconBook" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span class="nav-label">文档</span>
        </a>
      </div>
    </aside>

    <!-- ===== Main ===== -->
    <div class="main">
      <!-- Topbar -->
      <header class="topbar">
        <div class="breadcrumb">
          <span class="crumb-root">shipyard</span>
          <span class="crumb-sep">/</span>
          <span class="crumb-current">{{ (route.meta?.title as string) || 'Page' }}</span>
        </div>
        <div class="topbar-right">
          <button class="cmd-trigger" @click="openCommandPalette" title="命令面板 (Cmd+K)">
            <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">
              <circle cx="11" cy="11" r="8" fill="none" stroke="currentColor" stroke-width="2" />
              <path d="M21 21l-4.35-4.35" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
            <span>搜索命令 / 项目...</span>
            <kbd>⌘K</kbd>
          </button>
          <RouterLink to="/notifications" class="topbar-icon-btn" title="通知">
            <svg viewBox="0 0 24 24" width="16" height="16">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9 M13.73 21a2 2 0 0 1-3.46 0"
                fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </RouterLink>
          <div class="status-pill" :class="auth.tokenInfo ? 'ok' : 'warn'">
            <span class="status-dot" :class="auth.tokenInfo ? 'success' : 'warning'"></span>
            <span v-if="auth.tokenInfo">已登录 · {{ auth.tokenInfo.userId }}</span>
            <span v-else>未登录</span>
          </div>
        </div>
      </header>

      <!-- Router view + fade transition -->
      <main class="content">
        <RouterView v-slot="{ Component }">
          <transition name="route-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </RouterView>
      </main>
    </div>

    <!-- 全局命令面板 (Cmd+K) -->
    <CommandPalette ref="paletteRef" />
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  background: var(--color-bg-base);
}

/* ===== Sidebar ===== */
.sidebar {
  width: var(--height-sidebar);
  background: var(--color-bg-surface);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
  z-index: 10;
}
.sidebar.expanded { width: var(--height-sidebar-expanded); }

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 18px 18px 20px;
  color: var(--color-text-primary);
  border-bottom: 1px solid var(--color-divider);
  flex-shrink: 0;
}
.brand-mark { color: var(--color-accent); flex-shrink: 0; }
.brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
  white-space: nowrap;
}
.sidebar.expanded .brand-text { opacity: 1; }
.brand-name {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 16px;
  letter-spacing: -0.01em;
  color: var(--color-text-primary);
}
.brand-sub {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-muted);
  letter-spacing: 0.05em;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-3) 8px;
  flex: 1;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  transition: all var(--transition-fast);
  position: relative;
  white-space: nowrap;
  overflow: hidden;
}
.nav-item:hover {
  background: var(--color-accent-soft);
  color: var(--color-text-primary);
}
.nav-item.active {
  background: var(--color-accent-soft);
  color: var(--color-accent);
}
.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 60%;
  background: var(--color-accent);
  border-radius: 0 2px 2px 0;
}
.nav-icon { flex-shrink: 0; }
.nav-label {
  opacity: 0;
  transition: opacity 0.15s;
  flex: 1;
}
.nav-shortcut {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-text-muted);
  background: var(--color-bg-elevated);
  padding: 1px 5px;
  border-radius: 3px;
  border: 1px solid var(--color-border);
  opacity: 0;
  transition: opacity 0.15s;
}
.sidebar.expanded .nav-label,
.sidebar.expanded .nav-shortcut { opacity: 1; }

.sidebar-footer {
  padding: var(--space-3) 8px;
  border-top: 1px solid var(--color-divider);
  flex-shrink: 0;
}

/* ===== Main ===== */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--height-topbar);
  padding: 0 var(--space-6);
  background: var(--color-bg-surface);
  border-bottom: 1px solid var(--color-border);
  position: sticky;
  top: 0;
  z-index: 5;
  flex-shrink: 0;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.crumb-root { color: var(--color-text-muted); }
.crumb-sep { color: var(--color-text-muted); font-family: var(--font-mono); }
.crumb-current { color: var(--color-text-primary); font-weight: 500; }

.topbar-right { display: flex; align-items: center; gap: 12px; }

.cmd-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px 6px 12px;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition-fast);
  min-width: 240px;
}
.cmd-trigger:hover {
  border-color: var(--color-border-strong);
  color: var(--color-text-secondary);
}
.cmd-trigger span { flex: 1; text-align: left; }
.cmd-trigger kbd {
  font-family: var(--font-mono);
  font-size: 10px;
  background: var(--color-bg-base);
  padding: 1px 6px;
  border-radius: 3px;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}

.topbar-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px; height: 32px;
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
}
.topbar-icon-btn:hover {
  background: var(--color-accent-soft);
  color: var(--color-text-primary);
}

.status-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-family: var(--font-mono);
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
}
.status-pill.ok { color: var(--color-success); }
.status-pill.warn { color: var(--color-warning); }

@media (max-width: 900px) {
  .cmd-trigger { min-width: 0; }
  .cmd-trigger span { display: none; }
}

.content {
  flex: 1;
  padding: var(--space-6);
  min-width: 0;
  overflow-x: auto;
}

/* 响应式: 小屏 sidebar 默认展开 */
@media (max-width: 768px) {
  .sidebar { width: var(--height-sidebar-expanded); }
  .brand-text, .nav-label, .nav-shortcut { opacity: 1; }
}
</style>
