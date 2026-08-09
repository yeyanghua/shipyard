<script setup lang="ts">
/**
 * 项目列表页 - 表格 + 搜索 + 新建按钮 + 分页.
 *
 * <p>M4 接真后端 (projectsApi).
 */
import { onMounted, ref } from 'vue';
import { useProjectStore } from '@/stores/project';
import { ApiError } from '@/api';

const store = useProjectStore();
const confirmDeleteId = ref<number | null>(null);

onMounted(() => store.fetchList());

async function onDelete(id: number) {
  try {
    await store.remove(id);
    confirmDeleteId.value = null;
  } catch (e) {
    alert(`删除失败: ${(e as ApiError).message}`);
  }
}

function onSearch() {
  store.pagination.page = 1;
  store.fetchList();
}

function onPageChange(p: number) {
  store.pagination.page = p;
  store.fetchList();
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <h1 class="title">项目列表</h1>
      <div class="actions">
        <input
          v-model="store.pagination.keyword"
          class="search"
          placeholder="搜索 name / displayName"
          @keyup.enter="onSearch"
        />
        <button class="btn-primary" @click="onSearch">搜索</button>
        <RouterLink class="btn-primary" to="/projects/new">+ 新建项目</RouterLink>
      </div>
    </div>

    <div v-if="store.error" class="error">{{ store.error }}</div>
    <div v-if="store.loading" class="loading">加载中...</div>

    <table v-if="!store.loading && store.list.length > 0" class="table">
      <thead>
        <tr>
          <th>Name</th>
          <th>显示名</th>
          <th>仓库</th>
          <th>类型</th>
          <th>Token</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in store.list" :key="p.id">
          <td><code>{{ p.name }}</code></td>
          <td>{{ p.displayName }}</td>
          <td>{{ p.repoProvider }}</td>
          <td><span class="tag">{{ p.projectType }}</span></td>
          <td>
            <span v-if="p.hasRepoToken" class="secret">已设</span>
            <span v-else class="muted">未设</span>
          </td>
          <td>{{ p.updatedAt }}</td>
          <td class="ops">
            <RouterLink :to="`/projects/${p.id}`">查看</RouterLink>
            <button class="link danger" @click="confirmDeleteId = p.id">删除</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-else-if="!store.loading" class="empty">
      <p>暂无项目, <RouterLink to="/projects/new">创建第一个项目</RouterLink></p>
    </div>

    <div v-if="store.total > store.pagination.size" class="pager">
      <button
        :disabled="store.pagination.page === 1"
        @click="onPageChange(store.pagination.page - 1)"
      >上一页</button>
      <span>第 {{ store.pagination.page }} 页 / 共 {{ Math.ceil(store.total / store.pagination.size) }} 页</span>
      <button
        :disabled="store.pagination.page >= Math.ceil(store.total / store.pagination.size)"
        @click="onPageChange(store.pagination.page + 1)"
      >下一页</button>
    </div>

    <!-- 删除确认 -->
    <div v-if="confirmDeleteId !== null" class="modal-mask" @click.self="confirmDeleteId = null">
      <div class="modal">
        <h3>确认删除</h3>
        <p>项目 ID {{ confirmDeleteId }} 会被软删,确定吗?</p>
        <div class="modal-actions">
          <button @click="confirmDeleteId = null">取消</button>
          <button class="btn-danger" @click="onDelete(confirmDeleteId)">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { max-width: 1100px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.title { font-size: 22px; margin: 0; }
.actions { display: flex; gap: 8px; }
.search { padding: 6px 10px; border: 1px solid var(--border); border-radius: 4px; width: 240px; }
.btn-primary { padding: 6px 14px; background: var(--primary); color: #fff; border: 0; border-radius: 4px; cursor: pointer; text-decoration: none; }
.btn-primary:hover { background: #2563eb; }
.table { width: 100%; background: var(--card); border: 1px solid var(--border); border-radius: 8px; border-collapse: collapse; overflow: hidden; }
.table th, .table td { padding: 10px 14px; text-align: left; border-bottom: 1px solid var(--border); font-size: 14px; }
.table th { background: #f3f4f6; font-weight: 600; color: var(--text-muted); }
.tag { padding: 2px 8px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 12px; }
.secret { color: var(--primary); font-size: 12px; }
.muted { color: var(--text-soft); font-size: 12px; }
.ops { display: flex; gap: 12px; }
.ops a { color: var(--primary); text-decoration: none; }
.ops .link { background: none; border: 0; cursor: pointer; padding: 0; }
.ops .danger { color: #dc2626; }
.empty, .loading, .error { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 24px; text-align: center; color: var(--text-muted); }
.error { color: #dc2626; border-color: #fecaca; background: #fee2e2; }
.pager { display: flex; justify-content: center; align-items: center; gap: 12px; margin-top: 16px; }
.pager button { padding: 4px 12px; border: 1px solid var(--border); background: #fff; border-radius: 4px; cursor: pointer; }
.pager button:disabled { opacity: 0.5; cursor: not-allowed; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: #fff; border-radius: 8px; padding: 24px; min-width: 360px; }
.modal h3 { margin: 0 0 12px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.btn-danger { background: #dc2626; color: #fff; border: 0; padding: 6px 14px; border-radius: 4px; cursor: pointer; }
</style>
