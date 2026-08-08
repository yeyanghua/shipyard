<script setup lang="ts">
/**
 * 404 兜底页.
 */
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();
</script>

<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面 <code>{{ route.fullPath }}</code> 不存在</p>
    <button @click="router.push('/')">回总览</button>
  </div>
</template>

<style scoped>
.not-found {
  text-align: center;
  padding: 80px 20px;
}
.not-found h1 {
  font-size: 72px;
  color: var(--text-soft);
  margin: 0;
}
.not-found p {
  color: var(--text-muted);
  margin: 12px 0 24px;
}
.not-found button {
  padding: 8px 16px;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: 4px;
}
.not-found button:hover {
  background: #2563eb;
}
</style>
