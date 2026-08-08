<script setup lang="ts">
/**
 * 页面占位组件 — M3 阶段所有 view 都用这个,展示页面名 + 接入 milestone.
 *
 * <p>M4 之后每个 view 替换成实际 UI.
 */
defineProps<{
  title: string;
  milestone: string;
  description?: string;
}>();
</script>

<template>
  <div class="placeholder">
    <h1>{{ title }}</h1>
    <div class="badge">M3 stub · 接入计划: {{ milestone }}</div>
    <p v-if="description">{{ description }}</p>
    <p class="hint">
      页面骨架已就位 (路由 + 组件加载),具体 UI 在后续 milestone 实现.
    </p>
  </div>
</template>

<style scoped>
.placeholder {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 32px;
  max-width: 720px;
}
.placeholder h1 {
  margin: 0 0 12px;
  font-size: 24px;
  color: var(--text);
}
.badge {
  display: inline-block;
  padding: 4px 10px;
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;
  margin-bottom: 16px;
}
.placeholder p {
  color: var(--text-muted);
  margin: 8px 0;
}
.hint {
  color: var(--text-soft);
  font-size: 13px;
  font-style: italic;
}
</style>
