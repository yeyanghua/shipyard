<script setup lang="ts">
/**
 * 环境列表页 - 表格 + 搜索 + 新建按钮.
 */
import { onMounted, ref } from 'vue';
import { useEnvStore } from '@/stores/env';
import { ApiError } from '@/api';

const store = useEnvStore();
const showCreate = ref(false);
const form = ref({
  name: '',
  displayName: '',
  k8sNamespace: '',
  workerUrl: '',
  workerToken: '',
  isProduction: false,
});
const error = ref('');

onMounted(() => store.fetchList());

async function onCreate() {
  error.value = '';
  if (!form.value.name || !/^[a-z0-9-]+$/.test(form.value.name)) {
    error.value = 'name 只能包含小写字母/数字/中划线';
    return;
  }
  if (!form.value.displayName) { error.value = 'displayName 必填'; return; }
  if (!form.value.k8sNamespace) { error.value = 'k8sNamespace 必填'; return; }
  if (!form.value.workerUrl) { error.value = 'workerUrl 必填'; return; }
  try {
    await store.create({
      name: form.value.name,
      displayName: form.value.displayName,
      k8sNamespace: form.value.k8sNamespace,
      workerUrl: form.value.workerUrl,
      workerToken: form.value.workerToken || undefined,
      isProduction: form.value.isProduction ? 1 : 0,
    });
    showCreate.value = false;
    form.value = { name: '', displayName: '', k8sNamespace: '', workerUrl: '', workerToken: '', isProduction: false };
  } catch (e) {
    error.value = (e as ApiError).message;
  }
}

function onFilterProduction(v: boolean | undefined) {
  store.pagination.production = v;
  store.pagination.page = 1;
  store.fetchList();
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <h1 class="title">环境列表</h1>
      <div class="actions">
        <select :value="store.pagination.production" @change="(e: any) => onFilterProduction(e.target.value === '' ? undefined : e.target.value === 'true')">
          <option value="">全部</option>
          <option value="false">仅 Dev</option>
          <option value="true">仅 Prod</option>
        </select>
        <button class="btn-primary" @click="showCreate = !showCreate">
          {{ showCreate ? '取消' : '+ 新建环境' }}
        </button>
      </div>
    </div>

    <div v-if="showCreate" class="card form-card">
      <h3>新建环境</h3>
      <div v-if="error" class="error">{{ error }}</div>
      <div class="form-grid">
        <div class="field">
          <label>Name *</label>
          <input v-model="form.name" placeholder="dev" />
        </div>
        <div class="field">
          <label>显示名 *</label>
          <input v-model="form.displayName" placeholder="开发环境" />
        </div>
        <div class="field">
          <label>k8s Namespace *</label>
          <input v-model="form.k8sNamespace" placeholder="demo-app-dev" />
        </div>
        <div class="field">
          <label>Worker URL *</label>
          <input v-model="form.workerUrl" placeholder="http://worker-dev:8081" />
        </div>
        <div class="field full">
          <label>Worker Token<span class="hint">(选填, 加密)</span></label>
          <input v-model="form.workerToken" type="password" />
        </div>
        <div class="field full">
          <label>
            <input v-model="form.isProduction" type="checkbox" /> 生产环境
          </label>
        </div>
      </div>
      <button class="btn-primary" @click="onCreate">创建</button>
    </div>

    <div v-if="store.error" class="error">{{ store.error }}</div>
    <div v-if="store.loading" class="loading">加载中...</div>

    <table v-if="!store.loading && store.list.length > 0" class="table">
      <thead>
        <tr>
          <th>Name</th><th>显示名</th><th>Namespace</th><th>Worker</th><th>Token</th><th>类型</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="e in store.list" :key="e.id">
          <td><code>{{ e.name }}</code></td>
          <td>{{ e.displayName }}</td>
          <td><code>{{ e.k8sNamespace }}</code></td>
          <td><code class="url">{{ e.workerUrl }}</code></td>
          <td>
            <span v-if="e.hasWorkerToken" class="secret">已设</span>
            <span v-else class="muted">未设</span>
          </td>
          <td>
            <span v-if="e.isProduction" class="tag danger">PROD</span>
            <span v-else class="tag">DEV</span>
          </td>
          <td class="ops">
            <RouterLink :to="`/envs/${e.id}/variables`">变量</RouterLink>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-else-if="!store.loading" class="empty">
      <p>暂无环境, 点击右上角"新建环境"创建</p>
    </div>
  </div>
</template>

<style scoped>
.page { max-width: 1100px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.title { font-size: 22px; margin: 0; }
.actions { display: flex; gap: 8px; }
.actions select { padding: 6px 10px; border: 1px solid var(--border); border-radius: 4px; }
.btn-primary { padding: 6px 14px; background: var(--primary); color: #fff; border: 0; border-radius: 4px; cursor: pointer; }
.card.form-card { margin-bottom: 16px; padding: 16px; }
.form-card h3 { margin: 0 0 12px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field.full { grid-column: 1 / -1; }
.field label { font-size: 13px; color: var(--text-muted); }
.field input { padding: 6px 10px; border: 1px solid var(--border); border-radius: 4px; }
.error { color: #dc2626; background: #fee2e2; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; }
.table { width: 100%; background: var(--card); border: 1px solid var(--border); border-radius: 8px; border-collapse: collapse; overflow: hidden; }
.table th, .table td { padding: 10px 14px; text-align: left; border-bottom: 1px solid var(--border); font-size: 14px; }
.table th { background: #f3f4f6; font-weight: 600; color: var(--text-muted); }
.url { font-size: 12px; color: var(--text-muted); }
.tag { padding: 2px 8px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 12px; }
.tag.danger { background: #fee2e2; color: #dc2626; }
.secret { color: var(--primary); font-size: 12px; }
.muted { color: var(--text-soft); font-size: 12px; }
.ops a { color: var(--primary); text-decoration: none; margin-right: 12px; }
.empty, .loading { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 24px; text-align: center; color: var(--text-muted); }
</style>
