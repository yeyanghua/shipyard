<script setup lang="ts">
/**
 * 活动日志 (M13 Phase 3) — event log 中心.
 *
 * <p>V1 demo 全部 mock (V1.5 接 audit_log 表 + 后端聚合).
 *
 * <p>设计:
 * <ul>
 *   <li>左侧: 时间线 (按时间倒序)</li>
 *   <li>事件类型: 触发构建 / 部署 / Pipeline 更新 / 审批 / AI 调用</li>
 *   <li>每条事件: icon + actor + 动作 + 目标 + 相对时间</li>
 *   <li>右侧: 过滤器 (actor / 类型 / 时间范围)</li>
 * </ul>
 */
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';

interface ActivityEvent {
  id: string;
  type: 'build' | 'deploy' | 'pipeline' | 'approval' | 'ai' | 'env' | 'dockerfile';
  actor: string;
  action: string;
  target: string;
  detail?: string;
  timestamp: string;     // 相对时间显示
  absolute: string;     // 绝对时间 ISO
  status?: 'success' | 'failed' | 'pending';
}

const events = ref<ActivityEvent[]>([
  { id: '1', type: 'build',     actor: 'demo-user',   action: '触发构建',     target: 'shipyard-backend', detail: 'commit a1b2c3d · env=dev',         timestamp: '5 分钟前',  absolute: '2026-08-09T19:42:00Z', status: 'success' },
  { id: '2', type: 'ai',        actor: 'demo-user',   action: '调用 AI',      target: 'PipelineGenHandler', detail: '生成 java_maven pipeline YAML',  timestamp: '8 分钟前',  absolute: '2026-08-09T19:39:00Z', status: 'success' },
  { id: '3', type: 'pipeline',  actor: 'demo-user',   action: '审批通过',     target: 'shipyard-backend v3', detail: 'DRAFT → APPROVED',                 timestamp: '12 分钟前', absolute: '2026-08-09T19:35:00Z', status: 'success' },
  { id: '4', type: 'build',     actor: 'system',      action: '构建完成',     target: 'shipyard-web', detail: '#42 SUCCESS (3m 12s)',             timestamp: '24 分钟前', absolute: '2026-08-09T19:23:00Z', status: 'success' },
  { id: '5', type: 'deploy',    actor: 'demo-user',   action: '发布',         target: 'shipyard-backend → dev', detail: 'k8s deploy #18',                   timestamp: '38 分钟前', absolute: '2026-08-09T19:09:00Z', status: 'success' },
  { id: '6', type: 'build',     actor: 'system',      action: '构建失败',     target: 'ml-experiment', detail: 'compile error in src/main.py',    timestamp: '1 小时前',  absolute: '2026-08-09T18:42:00Z', status: 'failed' },
  { id: '7', type: 'dockerfile', actor: 'demo-user',  action: '生成 Dockerfile', target: 'shipyard-web (node_pnpm_20)', detail: 'preview rendered (47 lines)',   timestamp: '2 小时前',  absolute: '2026-08-09T17:48:00Z', status: 'success' },
  { id: '8', type: 'env',       actor: 'demo-user',   action: '更新环境变量', target: 'production · DB_PASSWORD', detail: 'encrypted, written',          timestamp: '3 小时前',  absolute: '2026-08-09T16:31:00Z', status: 'success' },
  { id: '9', type: 'build',     actor: 'demo-user',   action: '触发构建',     target: 'docs-site', detail: 'commit 7a8b9c0 · env=staging',    timestamp: '4 小时前',  absolute: '2026-08-09T15:20:00Z', status: 'success' },
  { id: '10', type: 'approval', actor: 'admin',       action: '驳回',         target: 'ml-experiment v2', detail: 'REJECTED, reason: 测试不充分',  timestamp: '5 小时前',  absolute: '2026-08-09T14:15:00Z', status: 'failed' },
  { id: '11', type: 'ai',       actor: 'demo-user',   action: '调用 AI',      target: 'DiagnosisHandler', detail: '分析构建 #38 失败原因',         timestamp: '6 小时前',  absolute: '2026-08-09T13:08:00Z', status: 'success' },
  { id: '12', type: 'build',    actor: 'system',      action: '构建超时',     target: 'legacy-app', detail: '#15 TIMEOUT (timeout 30m)',      timestamp: '昨天 18:30', absolute: '2026-08-08T18:30:00Z', status: 'failed' },
]);

// 过滤器
const filterType = ref<string>('all');
const filterActor = ref<string>('');
const filterRange = ref<string>('all');

const filtered = computed(() => {
  let list = events.value;
  if (filterType.value !== 'all') list = list.filter((e) => e.type === filterType.value);
  if (filterActor.value) list = list.filter((e) => e.actor.includes(filterActor.value));
  if (filterRange.value === 'today') list = list.filter((e) => e.timestamp.includes('分钟') || e.timestamp.includes('小时'));
  if (filterRange.value === 'yesterday') list = list.filter((e) => e.timestamp.includes('昨天'));
  return list;
});

const typeMeta: Record<ActivityEvent['type'], { icon: string; color: string; label: string }> = {
  build:     { icon: '🏗️',  color: '#06b6d4', label: '构建' },
  deploy:    { icon: '🚀',  color: '#10b981', label: '发布' },
  pipeline:  { icon: '📜',  color: '#8b5cf6', label: '流水线' },
  approval:  { icon: '✓',  color: '#3b82f6', label: '审批' },
  ai:        { icon: '✨', color: '#f59e0b', label: 'AI' },
  env:       { icon: '🌐',  color: '#06b6d4', label: '环境' },
  dockerfile:{ icon: '🐳', color: '#06b6d4', label: 'Dockerfile' },
};

const statusMeta: Record<NonNullable<ActivityEvent['status']>, { color: string; label: string }> = {
  success: { color: 'var(--color-success)', label: '成功' },
  failed:  { color: 'var(--color-danger)',  label: '失败' },
  pending: { color: 'var(--color-warning)', label: '进行中' },
};

// 导出 (V1 mock — V1.5 调 /api/activity/export)
function exportEvents() {
  ElMessage.success(`已导出 ${filtered.value.length} 条活动 (mock, V1.5 接后端)`);
}
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>活动日志</h1>
        <p class="muted">所有系统事件的统一时间线 · V1 mock (V1.5 接 audit_log + 后端聚合)</p>
      </div>
      <div class="header-actions">
        <el-button @click="exportEvents">导出</el-button>
      </div>
    </header>

    <div class="layout">
      <!-- ===== 过滤器侧栏 ===== -->
      <aside class="filter-sidebar">
        <h3>过滤器</h3>
        <div class="filter-group">
          <label class="filter-label">事件类型</label>
          <el-select v-model="filterType" placeholder="全部" style="width: 100%">
            <el-option label="全部" value="all" />
            <el-option label="构建" value="build" />
            <el-option label="发布" value="deploy" />
            <el-option label="流水线" value="pipeline" />
            <el-option label="审批" value="approval" />
            <el-option label="AI" value="ai" />
            <el-option label="环境" value="env" />
            <el-option label="Dockerfile" value="dockerfile" />
          </el-select>
        </div>
        <div class="filter-group">
          <label class="filter-label">操作人</label>
          <el-input v-model="filterActor" placeholder="按用户名筛选" clearable />
        </div>
        <div class="filter-group">
          <label class="filter-label">时间范围</label>
          <el-radio-group v-model="filterRange" style="display: flex; flex-direction: column; gap: 6px;">
            <el-radio value="all">全部</el-radio>
            <el-radio value="today">今天</el-radio>
            <el-radio value="yesterday">昨天</el-radio>
          </el-radio-group>
        </div>
        <div class="filter-stats">
          <div class="stat">
            <div class="stat-label">总计</div>
            <div class="stat-value">{{ events.length }}</div>
          </div>
          <div class="stat">
            <div class="stat-label">已过滤</div>
            <div class="stat-value text-accent">{{ filtered.length }}</div>
          </div>
        </div>
      </aside>

      <!-- ===== 时间线 ===== -->
      <main class="timeline">
        <div v-if="filtered.length === 0" class="empty">
          <p>没有匹配的事件</p>
          <p class="muted">试试调整过滤器</p>
        </div>
        <ol v-else class="event-list">
          <li v-for="event in filtered" :key="event.id" class="event">
            <div class="event-marker" :style="{ background: typeMeta[event.type].color + '20', color: typeMeta[event.type].color }">
              <span>{{ typeMeta[event.type].icon }}</span>
            </div>
            <div class="event-body">
              <div class="event-head">
                <span class="event-type" :style="{ color: typeMeta[event.type].color }">
                  {{ typeMeta[event.type].label }}
                </span>
                <span class="event-actor">@{{ event.actor }}</span>
                <span class="event-action">{{ event.action }}</span>
                <code class="event-target">{{ event.target }}</code>
                <span v-if="event.status" class="event-status" :style="{ color: statusMeta[event.status].color }">
                  · {{ statusMeta[event.status].label }}
                </span>
                <span class="event-time">{{ event.timestamp }}</span>
              </div>
              <div v-if="event.detail" class="event-detail">{{ event.detail }}</div>
            </div>
          </li>
        </ol>
      </main>
    </div>
  </div>
</template>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; display: flex; flex-direction: column; gap: var(--space-5); }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--space-4); flex-wrap: wrap; }
.page-header h1 { font-size: 24px; font-weight: 700; margin: 0; }
.muted { color: var(--color-text-muted); font-size: 12px; margin-top: 4px; }
.header-actions { display: flex; gap: 8px; }

.layout { display: grid; grid-template-columns: 240px 1fr; gap: var(--space-5); }
@media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }

.filter-sidebar {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  height: fit-content;
  position: sticky;
  top: calc(var(--height-topbar) + var(--space-4));
}
.filter-sidebar h3 { font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--color-text-secondary); margin: 0 0 16px; }
.filter-group { margin-bottom: 16px; }
.filter-label { display: block; font-size: 11px; color: var(--color-text-muted); margin-bottom: 6px; }
.filter-stats {
  margin-top: 24px; padding-top: 16px; border-top: 1px solid var(--color-border);
  display: grid; grid-template-columns: 1fr 1fr; gap: 12px;
}
.stat-label { font-size: 10px; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.05em; }
.stat-value { font-size: 20px; font-weight: 700; font-variant-numeric: tabular-nums; margin-top: 2px; }

.timeline { min-width: 0; }
.event-list { list-style: none; padding: 0; margin: 0; }
.event {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--color-divider);
  position: relative;
}
.event:last-child { border-bottom: 0; }
.event-marker {
  width: 32px; height: 32px;
  border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  font-size: 14px;
}
.event-body { flex: 1; min-width: 0; }
.event-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  flex-wrap: wrap;
}
.event-type { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; }
.event-actor { color: var(--color-text-secondary); font-family: var(--font-mono); font-size: 12px; }
.event-action { color: var(--color-text-primary); }
.event-target {
  font-family: var(--font-mono);
  font-size: 12px;
  background: var(--color-bg-elevated);
  padding: 1px 6px;
  border-radius: 3px;
  color: var(--color-text-primary);
}
.event-status { font-size: 12px; font-weight: 500; }
.event-time { color: var(--color-text-muted); font-size: 11px; font-family: var(--font-mono); margin-left: auto; }
.event-detail {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
}
.empty {
  text-align: center;
  padding: var(--space-10);
  color: var(--color-text-muted);
}
</style>
