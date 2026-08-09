<script setup lang="ts">
/**
 * EnvVarEditor - Key-Value 列表编辑器 (M7 polish 扩展).
 *
 * <p>每行: Key / Value / Secret? / Description / 删除按钮.
 * <p>"+ 新增" 加行. v-model 双向绑定 items 数组.
 *
 * <p>M7 新增 props:
 * <ul>
 *   <li><b>conflictingKeys</b>: Set&lt;string&gt; — 标记这些 key 冲突 (红框 + 警告样式)</li>
 *   <li><b>visibleIdxSet</b>: Set&lt;number&gt; — 搜索过滤后哪些 idx 可见, 不可见的隐藏</li>
 * </ul>
 */
import type { EnvVariableUpsertItem } from '@/api';

const props = defineProps<{
  modelValue: EnvVariableUpsertItem[];
  /** M7: 冲突 key 集合 (来自 EnvVars.vue 计算) */
  conflictingKeys?: Set<string>;
  /** M7: 搜索过滤后可见的 idx 集合 (undefined = 全可见) */
  visibleIdxSet?: Set<number>;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', v: EnvVariableUpsertItem[]): void;
  (e: 'dirty'): void;
}>();

function update(items: EnvVariableUpsertItem[]) {
  emit('update:modelValue', items);
  emit('dirty');
}

function addRow() {
  update([...props.modelValue, { key: '', value: '', isSecret: 1 }]);
}

function removeRow(idx: number) {
  const next = [...props.modelValue];
  next.splice(idx, 1);
  update(next);
}

function patch(idx: number, field: keyof EnvVariableUpsertItem, value: any) {
  const next = [...props.modelValue];
  next[idx] = { ...next[idx], [field]: value };
  update(next);
}

function isVisible(idx: number): boolean {
  if (!props.visibleIdxSet) return true;
  return props.visibleIdxSet.has(idx);
}

function isConflicting(idx: number): boolean {
  if (!props.conflictingKeys || props.conflictingKeys.size === 0) return false;
  const key = props.modelValue[idx]?.key;
  return !!(key && props.conflictingKeys.has(key));
}
</script>

<template>
  <div class="editor">
    <div class="row header-row">
      <span>Key</span>
      <span>Value</span>
      <span>Secret</span>
      <span>Description</span>
      <span></span>
    </div>
    <div
      v-for="(item, idx) in modelValue"
      v-show="isVisible(idx)"
      :key="idx"
      :class="['row', { 'row-conflict': isConflicting(idx) }]"
    >
      <input
        :value="item.key"
        :class="{ 'input-conflict': isConflicting(idx) }"
        placeholder="DB_URL"
        @input="(e: any) => patch(idx, 'key', e.target.value)"
      />
      <input
        :value="item.value"
        :type="item.isSecret ? 'password' : 'text'"
        :placeholder="item.isSecret ? '**** (secret)' : 'value'"
        @input="(e: any) => patch(idx, 'value', e.target.value)"
      />
      <label class="secret-toggle">
        <input
          type="checkbox"
          :checked="item.isSecret === 1"
          @change="(e: any) => patch(idx, 'isSecret', e.target.checked ? 1 : 0)"
        />
        {{ item.isSecret ? '是' : '否' }}
      </label>
      <input
        :value="item.description"
        placeholder="选填"
        @input="(e: any) => patch(idx, 'description', e.target.value)"
      />
      <button class="link danger" @click="removeRow(idx)">删除</button>
    </div>
    <button v-if="modelValue.length === 0" class="add-row empty-state" @click="addRow">
      + 添加第一个变量
    </button>
    <button v-else class="add-row" @click="addRow">+ 新增变量</button>
  </div>
</template>

<style scoped>
.editor { border: 1px solid var(--border); border-radius: 4px; padding: 8px; }
.row { display: grid; grid-template-columns: 1.2fr 1.8fr 0.7fr 1.4fr 0.5fr; gap: 8px; align-items: center; margin-bottom: 6px; }
.row:last-child { margin-bottom: 0; }
.row.row-conflict { background: #fef2f2; border-radius: 4px; padding: 4px; margin: -4px -4px 6px; }
.row span { font-size: 12px; color: var(--text-muted); }
.row input { padding: 6px 8px; border: 1px solid var(--border); border-radius: 4px; font-size: 13px; font-family: inherit; }
.row input.input-conflict { border-color: #ef4444; background: #fff5f5; }
.row input:focus { outline: 2px solid var(--primary); outline-offset: -2px; }
.row input.input-conflict:focus { outline-color: #ef4444; }
.secret-toggle { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: var(--text-muted); }
.secret-toggle input { margin: 0; }
.add-row { width: 100%; padding: 8px; background: #f9fafb; border: 1px dashed var(--border); border-radius: 4px; cursor: pointer; color: var(--text-muted); }
.add-row:hover { background: #f3f4f6; color: var(--primary); }
.add-row.empty-state { padding: 16px; }
.link { background: none; border: 0; cursor: pointer; padding: 0; }
.link.danger { color: #dc2626; font-size: 12px; }
.header-row { padding: 0 4px; margin-bottom: 4px; }
</style>
