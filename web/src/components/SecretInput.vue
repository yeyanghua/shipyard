<script setup lang="ts">
/**
 * SecretInput - 密文输入框 (默认显示 ***, 点击展开改 value).
 *
 * <p>前端不应该主动展示密文, 此组件仅显示"已设"占位, 不带解密功能
 * (解密走独立 GET /variables/{key} 端点, 用户在 EnvVars.vue 主动触发).
 */
import { ref } from 'vue';

const props = defineProps<{
  hasSecret: boolean;
}>();

const expanded = ref(false);
</script>

<template>
  <div class="secret-input">
    <span v-if="props.hasSecret && !expanded" class="mask">
      <code>***</code>
      <button class="link" @click="expanded = true">修改</button>
    </span>
    <input
      v-else
      type="password"
      :placeholder="props.hasSecret ? '留空保持不变, 填新值覆盖' : '选填, 加密存储'"
      @blur="expanded = false"
    />
  </div>
</template>

<style scoped>
.secret-input { display: inline-flex; align-items: center; gap: 8px; }
.mask { display: inline-flex; align-items: center; gap: 8px; }
.mask code { background: #f3f4f6; padding: 2px 8px; border-radius: 4px; font-family: monospace; }
input[type="password"] { padding: 6px 10px; border: 1px solid var(--border); border-radius: 4px; min-width: 240px; }
.link { background: none; border: 0; cursor: pointer; color: var(--primary); font-size: 12px; padding: 0; }
</style>
