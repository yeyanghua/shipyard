<script setup lang="ts">
/**
 * 通知中心 (M13 Phase 3) — toast 通知聚合页.
 *
 * <p>V1 demo mock 6 条, V1.5 接后端聚合 (构建失败 / 审批请求 / 部署完成).
 */
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';

interface Notification {
  id: string;
  type: 'build_failed' | 'approval' | 'deploy' | 'mention' | 'system';
  title: string;
  detail: string;
  timestamp: string;
  read: boolean;
  actionUrl?: string;
}

const notifications = ref<Notification[]>([
  { id: '1', type: 'build_failed', title: '构建失败',     detail: 'shipyard-backend #43 在 maven compile 阶段失败', timestamp: '3 分钟前', read: false, actionUrl: '/builds/43' },
  { id: '2', type: 'approval',     title: '审批请求',     detail: 'ml-experiment v2 等待你的审批',                  timestamp: '12 分钟前', read: false, actionUrl: '/projects/2/pipeline' },
  { id: '3', type: 'deploy',       title: '部署完成',     detail: 'shipyard-web → staging 部署成功 (3m 12s)',        timestamp: '1 小时前', read: true },
  { id: '4', type: 'mention',      title: '@ 提及你',     detail: 'admin 在 Activity 中提到了你',                   timestamp: '2 小时前', read: true },
  { id: '5', type: 'system',       title: '系统消息',     detail: 'shipyard v1.0.0 发布: 详见更新日志',              timestamp: '昨天 18:00', read: true },
  { id: '6', type: 'build_failed', title: '构建超时',     detail: 'legacy-app #15 构建超时 (timeout 30m)',          timestamp: '昨天 16:30', read: true, actionUrl: '/builds/15' },
]);

const filter = ref<'all' | 'unread'>('all');

const filtered = computed(() => {
  if (filter.value === 'unread') return notifications.value.filter((n) => !n.read);
  return notifications.value;
});

const unreadCount = computed(() => notifications.value.filter((n) => !n.read).length);

function markAllRead() {
  notifications.value.forEach((n) => (n.read = true));
  ElMessage.success('已全部标记为已读');
}

function markRead(n: Notification) {
  n.read = true;
}

const typeMeta: Record<Notification['type'], { icon: string; color: string; label: string }> = {
  build_failed: { icon: '✕', color: 'var(--color-danger)',  label: '失败' },
  approval:     { icon: '✓', color: 'var(--color-warning)', label: '审批' },
  deploy:       { icon: '🚀', color: 'var(--color-success)', label: '部署' },
  mention:      { icon: '@', color: 'var(--color-accent)',  label: '提及' },
  system:       { icon: 'ℹ', color: 'var(--color-info)',    label: '系统' },
};
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>通知中心 <span v-if="unreadCount > 0" class="unread-badge">{{ unreadCount }}</span></h1>
        <p class="muted">构建失败 / 审批 / 部署 / @ 提及 全部聚合</p>
      </div>
      <div class="header-actions">
        <el-radio-group v-model="filter" size="small">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="unread">未读 ({{ unreadCount }})</el-radio-button>
        </el-radio-group>
        <el-button :disabled="unreadCount === 0" @click="markAllRead">全部已读</el-button>
      </div>
    </header>

    <div v-if="filtered.length === 0" class="empty card">
      <p>📭</p>
      <p>没有通知</p>
    </div>
    <ul v-else class="notif-list">
      <li
        v-for="n in filtered"
        :key="n.id"
        :class="['notif-item', { unread: !n.read }]"
        @click="markRead(n)"
      >
        <div class="notif-marker" :style="{ background: typeMeta[n.type].color + '20', color: typeMeta[n.type].color }">
          {{ typeMeta[n.type].icon }}
        </div>
        <div class="notif-body">
          <div class="notif-head">
            <span class="notif-type" :style="{ color: typeMeta[n.type].color }">
              {{ typeMeta[n.type].label }}
            </span>
            <span class="notif-title">{{ n.title }}</span>
            <span class="notif-time">{{ n.timestamp }}</span>
          </div>
          <div class="notif-detail">{{ n.detail }}</div>
          <RouterLink v-if="n.actionUrl" :to="n.actionUrl" class="notif-action">查看 →</RouterLink>
        </div>
        <div v-if="!n.read" class="notif-dot"></div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.page { max-width: 960px; margin: 0 auto; display: flex; flex-direction: column; gap: var(--space-5); }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--space-4); flex-wrap: wrap; }
.page-header h1 { font-size: 24px; font-weight: 700; margin: 0; display: flex; align-items: center; gap: 8px; }
.muted { color: var(--color-text-muted); font-size: 12px; margin-top: 4px; }
.header-actions { display: flex; gap: 8px; align-items: center; }
.unread-badge {
  background: var(--color-danger);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  font-weight: 500;
}

.empty { text-align: center; padding: var(--space-10); color: var(--color-text-muted); }
.empty p:first-child { font-size: 48px; margin-bottom: 8px; }

.notif-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 1px; background: var(--color-border); border-radius: var(--radius-md); overflow: hidden; }
.notif-item {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--color-bg-surface);
  cursor: pointer;
  position: relative;
  transition: background var(--transition-fast);
}
.notif-item:hover { background: var(--color-accent-soft); }
.notif-item.unread { background: var(--color-bg-elevated); }
.notif-item.unread:hover { background: var(--color-accent-soft); }

.notif-marker {
  width: 36px; height: 36px;
  border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 600;
  flex-shrink: 0;
}
.notif-body { flex: 1; min-width: 0; }
.notif-head { display: flex; align-items: center; gap: 8px; font-size: 13px; flex-wrap: wrap; }
.notif-type { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; }
.notif-title { color: var(--color-text-primary); font-weight: 500; }
.notif-time { color: var(--color-text-muted); font-size: 11px; font-family: var(--font-mono); margin-left: auto; }
.notif-detail { margin-top: 4px; font-size: 12px; color: var(--color-text-secondary); }
.notif-action { display: inline-block; margin-top: 6px; font-size: 12px; color: var(--color-accent); }
.notif-dot {
  position: absolute; right: 12px; top: 50%; transform: translateY(-50%);
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--color-accent);
  box-shadow: 0 0 8px var(--color-accent);
}
</style>
