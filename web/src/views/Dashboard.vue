<script setup lang="ts">
/**
 * 总览页 — 展示项目/环境/构建状态统计.
 * 接入计划: M4 (CRUD) + M13 (监控指标) + M14 (回滚统计)
 */
import PagePlaceholder from './PagePlaceholder.vue';
</script>

<template>
  <PagePlaceholder
    title="总览"
    milestone="M4 + M13"
    description="展示项目数 / 环境数 / 今日构建成功率 / 待处理告警数"
  />
</template>
