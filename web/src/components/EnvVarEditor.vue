<script setup lang="ts">
/**
 * EnvVarEditor - Key-Value 列表编辑器.
 *
 * <p>每行: Key / Value / Secret? / Description / 删除按钮.
 * <p>"+ 新增" 加行. v-model 双向绑定 items 数组.
 */
import type { EnvVariableUpsertItem } from '@/api';

const props = defineProps<{
  modelValue: EnvVariableUpsertItem[];
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
    <div v-for="(item, idx) in modelValue" :key="idx" class="row">
      <input
        :value="item.key"
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
.row span { font-size: 12px; color: var(--text-muted); }
.row input { padding: 6px 8px; border: 1px solid var(--border); border-radius: 4px; font-size: 13px; font-family: inherit; }
.secret-toggle { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: var(--text-muted); }
.secret-toggle input { margin: 0; }
.add-row { width: 100%; padding: 8px; background: #f9fafb; border: 1px dashed var(--border); border-radius: 4px; cursor: pointer; color: var(--text-muted); }
.add-row:hover { background: #f3f4f6; color: var(--primary); }
.add-row.empty-state { padding: 16px; }
.link { background: none; border: 0; cursor: pointer; padding: 0; }
.link.danger { color: #dc2626; font-size: 12px; }
.header-row { padding: 0 4px; margin-bottom: 4px; }
</style>
