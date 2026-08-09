<script setup lang="ts">
/**
 * 环境变量编辑页 - 列表 + 批量 upsert + 单查解密.
 *
 * <p>M4 接真后端, 关键功能:
 * <ul>
 *   <li>列表显示 (secret 是 ***, 非 secret 是明文)</li>
 *   <li>"显示明文" 按钮调 GET /variables/{key} 拿解密值</li>
 *   <li>Key-Value 编辑器 (行内新增/删除/复制)</li>
 *   <li>批量 upsert 提交</li>
 * </ul>
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { envVariablesApi, type EnvVariable, type EnvVariableUpsertItem, ApiError } from '@/api';
import EnvVarEditor from '@/components/EnvVarEditor.vue';

const route = useRoute();
const envId = computed(() => Number(route.params.id));
const projectId = computed(() => {
  const p = route.query.projectId;
  return p ? Number(p) : undefined;
});

const list = ref<EnvVariable[]>([]);
const loading = ref(false);
const error = ref('');
const dirty = ref(false);

const editorItems = ref<EnvVariableUpsertItem[]>([]);
const saving = ref(false);
const saveMsg = ref('');

async function fetchList() {
  loading.value = true;
  error.value = '';
  try {
    list.value = await envVariablesApi.list(envId.value, projectId.value);
    // 初次加载时,把现有变量塞进编辑器 (key + isSecret + description, value 留空待用户填)
    if (!dirty.value) {
      editorItems.value = list.value.map((v) => ({
        key: v.key,
        value: '',  // 不回显, 用户要"显示明文"再填
        isSecret: v.isSecret,
        description: v.description,
      }));
    }
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}

onMounted(fetchList);

watch([envId, projectId], () => {
  dirty.value = false;
  fetchList();
});

async function onSave() {
  saving.value = true;
  saveMsg.value = '';
  try {
    // 过滤空 value (没填就不提交, 避免覆盖)
    const toSave = editorItems.value.filter((i) => i.key && i.value);
    if (toSave.length === 0) {
      saveMsg.value = '没有需要保存的变量 (空 value 不提交)';
      return;
    }
    await envVariablesApi.batchUpsert(envId.value, toSave, projectId.value);
    saveMsg.value = `✅ 保存了 ${toSave.length} 个变量`;
    dirty.value = false;
    await fetchList();
  } catch (e) {
    saveMsg.value = `❌ 保存失败: ${(e as ApiError).message}`;
  } finally {
    saving.value = false;
  }
}

async function onDelete(key: string) {
  if (!confirm(`确认删除变量 ${key}?`)) return;
  try {
    await envVariablesApi.delete(envId.value, key, projectId.value);
    await fetchList();
  } catch (e) {
    alert(`删除失败: ${(e as ApiError).message}`);
  }
}

const onDirty = () => { dirty.value = true; saveMsg.value = '有未保存的修改'; };

async function showPlaintext(v: EnvVariable) {
  if (v.isSecret !== 1) return;
  try {
    const r = await envVariablesApi.getDecrypted(envId.value, v.key, projectId.value);
    alert(`变量 ${v.key} 的明文值:\n\n${r.value}`);
  } catch (e) {
    alert(`解密失败: ${(e as ApiError).message}`);
  }
}
</script>

<template>
  <div class="page">
    <div class="header">
      <RouterLink :to="`/envs`" class="back">← 返回环境列表</RouterLink>
      <h1 class="title">
        环境变量 <span v-if="projectId" class="scope">项目级 (projectId={{ projectId }})</span><span v-else class="scope">全局</span>
      </h1>
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <section class="card">
      <h2>已有变量</h2>
      <div v-if="loading" class="loading">加载中...</div>
      <table v-else-if="list.length > 0" class="table">
        <thead>
          <tr>
            <th>Key</th>
            <th>Value</th>
            <th>Secret</th>
            <th>Updated By</th>
            <th>Updated At</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="v in list" :key="v.id">
            <td><code>{{ v.key }}</code></td>
            <td>
              <span v-if="v.isSecret === 1" class="secret-mask">
                <code>{{ v.value }}</code>
                <button class="link" @click="showPlaintext(v)">显示明文</button>
              </span>
              <span v-else><code>{{ v.value }}</code></span>
            </td>
            <td>{{ v.isSecret === 1 ? '是' : '否' }}</td>
            <td>{{ v.updatedBy }}</td>
            <td>{{ v.updatedAt }}</td>
            <td>
              <button class="link danger" @click="onDelete(v.key)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无变量, 在下方编辑器添加</div>
    </section>

    <section class="card">
      <h2>编辑器</h2>
      <p class="hint">输入 key 和 value 后保存; 空 value 不会被提交; 已有变量重复提交会覆盖.</p>
      <EnvVarEditor v-model="editorItems" @dirty="onDirty" />
      <div v-if="saveMsg" class="save-msg">{{ saveMsg }}</div>
      <div class="actions">
        <button @click="fetchList" :disabled="saving">重置</button>
        <button class="btn-primary" @click="onSave" :disabled="saving">
          {{ saving ? '保存中...' : '批量保存' }}
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page { max-width: 1100px; }
.header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.back { color: var(--primary); text-decoration: none; }
.title { font-size: 20px; margin: 0; }
.scope { font-size: 12px; color: var(--text-muted); margin-left: 8px; font-weight: 400; }
.card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 20px; margin-bottom: 16px; }
.card h2 { margin: 0 0 12px; font-size: 16px; }
.hint { color: var(--text-muted); font-size: 13px; margin: 0 0 12px; }
.table { width: 100%; border-collapse: collapse; }
.table th, .table td { padding: 8px 12px; text-align: left; border-bottom: 1px solid var(--border); font-size: 14px; }
.table th { background: #f3f4f6; font-weight: 600; color: var(--text-muted); }
.secret-mask { display: inline-flex; align-items: center; gap: 8px; }
.link { background: none; border: 0; cursor: pointer; padding: 0; color: var(--primary); font-size: 12px; }
.link.danger { color: #dc2626; }
.empty, .loading { color: var(--text-muted); font-style: italic; padding: 12px; }
.error { color: #dc2626; background: #fee2e2; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; }
.save-msg { padding: 8px 12px; background: #f0fdf4; border-radius: 4px; margin: 12px 0; font-size: 14px; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
.actions button { padding: 6px 14px; border: 1px solid var(--border); background: #fff; border-radius: 4px; cursor: pointer; }
.btn-primary { background: var(--primary) !important; color: #fff !important; border-color: var(--primary) !important; }
</style>
