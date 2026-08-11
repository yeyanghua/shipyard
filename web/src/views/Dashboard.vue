<script setup lang="ts">
/**
 * 总览页 — stat 卡片 + 快捷入口 + 最近构建.
 *
 * <p>V1 demo 简化: 4 个 stat 卡片 (全 0 也展示) + 4 个快捷入口卡片.
 * <p>设计语言: 大字 tabular-nums 数字 + 几何 icon + skeleton 加载.
 *
 * <p>V1.5 接 Prometheus 后: stat 接真实 metric (今日构建成功率 / 待处理告警数).
 */
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { projectsApi, envsApi, workersApi, type Project, type Env, type Worker, ApiError } from '@/api';

const projects = ref<Project[]>([]);
const envs = ref<Env[]>([]);
const workers = ref<Worker[]>([]);
const loading = ref(true);

const stats = ref({
  projects: 0,
  envs: 0,
  workersOnline: 0,
  workersHealthy: 0,
  successRate: 0,
});

onMounted(async () => {
  try {
    const [pResp, eResp, wResp] = await Promise.all([
      projectsApi.list({ page: 1, size: 100 }),
      envsApi.list({ page: 1, size: 100 }),
      workersApi.list({ page: 1, size: 100 }).catch(() => ({ records: [], total: 0 })),
    ]);
    projects.value = pResp.records;
    envs.value = eResp.records;
    workers.value = wResp.records;
    stats.value.projects = pResp.total;
    stats.value.envs = eResp.total;
    // 在线 worker: 90s 内有心跳的
    stats.value.workersOnline = workers.value.filter((w) => w.heartbeatFresh).length;
    // 健康 worker: 自检 HEALTHY (M9 commit-12)
    stats.value.workersHealthy = workers.value.filter((w) => w.health === 'HEALTHY').length;
    // success rate V1 demo: 0 (没后端聚合接口)
    stats.value.successRate = 0;
  } catch (e) {
    // 静默失败, 展示 0
    console.warn('[Dashboard] load failed:', (e as ApiError).message);
  } finally {
    loading.value = false;
  }
});

// --- SVG icons (24x24) ---
const IconFolder = 'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z';
const IconServer = 'M2 3h20v8H2z M2 13h20v8H2z M6 7h.01 M6 17h.01';
const IconRocket = 'M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z M12 15l-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0 M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5';
const IconAlert = 'M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z M12 9v4 M12 17h.01';
const IconCpu = 'M4 4h16v16H4z M9 9h6v6H9z M2 9h2 M2 15h2 M20 9h2 M20 15h2 M9 2v2 M15 2v2 M9 20v2 M15 20v2';
const IconPlus = 'M12 5v14 M5 12h14';
const IconArrow = 'M5 12h14 M12 5l7 7-7 7';

// 快捷入口
const quickLinks = [
  { to: '/projects/new',     label: '新建项目',    desc: '从 GitLab / Gitee 仓库开始', icon: IconPlus },
  { to: '/projects',         label: '管理项目',    desc: '查看所有项目 / 触发构建',  icon: IconFolder },
  { to: '/envs',             label: '管理环境',    desc: 'Dev / Staging / Prod 配置',  icon: IconServer },
  { to: '/workers',          label: 'Worker 管理', desc: '查看注册的部署执行器',      icon: IconCpu },
  { to: '/ai/diagnosis',     label: 'AI 诊断',     desc: '智能分析构建失败原因',      icon: IconAlert },
];
</script>

<template>
  <div class="page">
    <!-- ===== Hero: 欢迎 + 大标题 ===== -->
    <section class="hero slide-up">
      <div>
        <p class="hero-eyebrow">Welcome back</p>
        <h1 class="hero-title">统一构建发布平台</h1>
        <p class="hero-desc">从代码到部署，一站式 DevOps 流水线。drone CI · 容器化 · k8s · AI 增强。</p>
      </div>
      <div class="hero-cta">
        <RouterLink to="/projects/new" class="btn btn-primary btn-lg">
          <svg viewBox="0 0 24 24" width="16" height="16"><path :d="IconPlus" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" /></svg>
          新建项目
        </RouterLink>
        <RouterLink to="/projects" class="btn btn-ghost btn-lg">
          查看项目
          <svg viewBox="0 0 24 24" width="14" height="14"><path :d="IconArrow" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </RouterLink>
      </div>
    </section>

    <!-- ===== Stat 卡片 (4 个, 大字 tabular-nums) ===== -->
    <section class="stats-grid">
      <div class="stat-card slide-up" style="animation-delay: 0.05s">
        <div class="stat-label">项目总数</div>
        <div class="stat-value">
          <span v-if="loading" class="skeleton" style="width: 60px; height: 32px; display: inline-block;"></span>
          <span v-else>{{ stats.projects }}</span>
        </div>
        <div class="stat-trend">本季度 +{{ stats.projects }}</div>
      </div>

      <div class="stat-card slide-up" style="animation-delay: 0.1s">
        <div class="stat-label">环境数量</div>
        <div class="stat-value">
          <span v-if="loading" class="skeleton" style="width: 60px; height: 32px; display: inline-block;"></span>
          <span v-else>{{ stats.envs }}</span>
        </div>
        <div class="stat-trend">Dev / Staging / Prod</div>
      </div>

      <div class="stat-card slide-up" style="animation-delay: 0.15s">
        <div class="stat-label">在线 Worker</div>
        <div class="stat-value">
          <span v-if="loading" class="skeleton" style="width: 60px; height: 32px; display: inline-block;"></span>
          <span v-else>
            {{ stats.workersOnline }}<span class="stat-unit">/{{ workers.length }}</span>
            <span class="stat-sub">· {{ stats.workersHealthy }} 健康</span>
          </span>
        </div>
        <div class="stat-trend">90s 内有心跳 + 自检 HEALTHY</div>
      </div>

      <div class="stat-card slide-up" style="animation-delay: 0.2s">
        <div class="stat-label">构建成功率</div>
        <div class="stat-value">
          <span v-if="loading" class="skeleton" style="width: 80px; height: 32px; display: inline-block;"></span>
          <span v-else>{{ stats.successRate }}<span class="stat-unit">%</span></span>
        </div>
        <div class="stat-trend">最近 7 天</div>
      </div>
    </section>

    <!-- ===== 快捷入口 ===== -->
    <section class="quick-section">
      <div class="section-header">
        <h2>快捷入口</h2>
        <span class="muted">{{ quickLinks.length }} 项</span>
      </div>
      <div class="quick-grid">
        <RouterLink
          v-for="(link, i) in quickLinks"
          :key="link.to"
          :to="link.to"
          class="quick-card slide-up"
          :style="{ animationDelay: `${0.25 + i * 0.05}s` }"
        >
          <div class="quick-icon">
            <svg viewBox="0 0 24 24" width="22" height="22">
              <path :d="link.icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </div>
          <div class="quick-text">
            <div class="quick-label">{{ link.label }}</div>
            <div class="quick-desc">{{ link.desc }}</div>
          </div>
          <svg class="quick-arrow" viewBox="0 0 24 24" width="16" height="16">
            <path d="M9 18l6-6-6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </RouterLink>
      </div>
    </section>

    <!-- ===== 最近构建 (V1 demo: 引导空状态) ===== -->
    <section class="card recent-section">
      <div class="section-header">
        <h2>最近构建</h2>
        <RouterLink to="/projects" class="link">查看全部 →</RouterLink>
      </div>
      <div v-if="!loading && projects.length === 0" class="empty">
        <svg class="empty-icon" viewBox="0 0 64 64" width="64" height="64" aria-hidden="true">
          <rect x="6" y="6" width="52" height="52" rx="4" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="4 4" opacity="0.5" />
          <path d="M22 32h20 M32 22v20" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.7" />
        </svg>
        <div class="empty-title">还没有构建记录</div>
        <div class="empty-desc">创建一个项目并触发你的第一次构建，构建日志和状态会在这里实时展示。</div>
        <RouterLink to="/projects/new" class="btn btn-primary">
          <svg viewBox="0 0 24 24" width="14" height="14"><path :d="IconRocket" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
          创建第一个项目
        </RouterLink>
      </div>
      <div v-else-if="loading" class="loading-skeletons">
        <div v-for="i in 3" :key="i" class="skeleton" style="height: 48px; margin-bottom: 8px;"></div>
      </div>
      <div v-else class="recent-list">
        <div v-for="p in projects.slice(0, 5)" :key="p.id" class="recent-row">
          <div class="recent-project">
            <code class="project-name">{{ p.name }}</code>
            <span class="project-display">{{ p.displayName }}</span>
          </div>
          <span class="badge badge-neutral">{{ p.projectType }}</span>
          <span class="muted recent-time">{{ p.createdAt }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

/* ===== Hero ===== */
.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-8) 0;
  flex-wrap: wrap;
}
.hero-eyebrow {
  font-family: var(--font-mono);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--color-accent);
  margin: 0 0 var(--space-2);
  font-weight: 500;
}
.hero-title {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0 0 var(--space-2);
  background: linear-gradient(135deg, var(--color-text-primary) 0%, var(--color-text-secondary) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.hero-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0;
  max-width: 600px;
}
.hero-cta { display: flex; gap: var(--space-3); flex-shrink: 0; }

/* ===== Stats ===== */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
}
@media (max-width: 900px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }

.stat-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: relative;
  overflow: hidden;
  transition: all var(--transition-fast);
}
.stat-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: var(--color-accent);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.3s ease-out;
}
.stat-card:hover { border-color: var(--color-border-strong); }
.stat-card:hover::before { transform: scaleX(1); }

.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 500;
}
.stat-value {
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 700;
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  margin-top: 4px;
}
.stat-unit { font-size: 18px; color: var(--color-text-muted); margin-left: 2px; }
.stat-trend { font-size: 11px; color: var(--color-text-muted); margin-top: 2px; font-family: var(--font-mono); }

/* ===== Quick Links ===== */
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-3);
}
.section-header h2 { font-size: 16px; font-weight: 600; }
.muted { color: var(--color-text-muted); font-size: 12px; font-family: var(--font-mono); }

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
}
@media (max-width: 700px) { .quick-grid { grid-template-columns: 1fr; } }

.quick-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-primary);
  text-decoration: none;
  transition: all var(--transition-fast);
}
.quick-card:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
  transform: translateY(-1px);
}
.quick-icon {
  width: 40px; height: 40px;
  display: flex; align-items: center; justify-content: center;
  background: var(--color-accent-soft);
  color: var(--color-accent);
  border-radius: var(--radius-md);
  flex-shrink: 0;
}
.quick-text { flex: 1; min-width: 0; }
.quick-label { font-size: 14px; font-weight: 600; color: var(--color-text-primary); }
.quick-desc { font-size: 12px; color: var(--color-text-muted); margin-top: 2px; }
.quick-arrow { color: var(--color-text-muted); flex-shrink: 0; transition: transform var(--transition-fast); }
.quick-card:hover .quick-arrow { color: var(--color-accent); transform: translateX(2px); }

/* ===== Recent builds ===== */
.recent-section .section-header h2 { font-size: 16px; }

.loading-skeletons { padding: var(--space-2) 0; }

.recent-list { display: flex; flex-direction: column; gap: 2px; }
.recent-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 12px 14px;
  border-radius: var(--radius-md);
  transition: background var(--transition-fast);
}
.recent-row:hover { background: var(--color-accent-soft); }
.recent-project { flex: 1; display: flex; align-items: center; gap: 10px; min-width: 0; }
.project-name {
  font-family: var(--font-mono);
  font-size: 13px;
  background: var(--color-bg-elevated);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  color: var(--color-text-primary);
  flex-shrink: 0;
}
.project-display { color: var(--color-text-secondary); font-size: 13px; }
.recent-time { font-size: 11px; flex-shrink: 0; }
</style>
