<script setup lang="ts">
/**
 * 项目详情页 - 基本信息 + 关联环境列表.
 *
 * <p>M4 接真后端.
 */
import { onMounted, ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { projectsApi, envsApi, type Project, type Env, ApiError } from '@/api';
import { useEnvStore } from '@/stores/env';
import SecretInput from '@/components/SecretInput.vue';

const route = useRoute();
const router = useRouter();
const envStore = useEnvStore();

const project = ref<Project | null>(null);
const envs = ref<Env[]>([]);
const linkedIds = ref<Set<number>>(new Set());
const loading = ref(true);
const error = ref('');
const showAddEnv = ref(false);

const projectId = computed(() => Number(route.params.id));

async function fetchData() {
  loading.value = true;
  error.value = '';
  try {
    project.value = await projectsApi.get(projectId.value);
    const links = await envsApi.listByProject(projectId.value);
    linkedIds.value = new Set(links.map((l) => l.envId));
    // 列出所有 env 用于选择
    await envStore.fetchList();
    envs.value = envStore.list;
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}

onMounted(fetchData);

async function linkEnv(envId: number) {
  try {
    await envsApi.associate(projectId.value, envId);
    linkedIds.value = new Set([...linkedIds.value, envId]);
    showAddEnv.value = false;
  } catch (e) {
    alert(`关联失败: ${(e as ApiError).message}`);
  }
}

async function unlinkEnv(envId: number) {
  if (!confirm('确认取消关联?')) return;
  try {
    await envsApi.unassociate(projectId.value, envId);
    const next = new Set(linkedIds.value);
    next.delete(envId);
    linkedIds.value = next;
  } catch (e) {
    alert(`取消失败: ${(e as ApiError).message}`);
  }
}

const linkedEnvs = computed(() => envs.value.filter((e) => linkedIds.value.has(e.id)));
const unlinkedEnvs = computed(() => envs.value.filter((e) => !linkedIds.value.has(e.id)));
</script>

<template>
  <div class="page" v-if="!loading && project">
    <div class="header">
      <button class="back" @click="router.push('/projects')">← 返回</button>
      <h1 class="title">{{ project.displayName }} <code class="name">{{ project.name }}</code></h1>
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <section class="card">
      <h2>基本信息</h2>
      <dl>
        <dt>Name</dt><dd><code>{{ project.name }}</code></dd>
        <dt>显示名</dt><dd>{{ project.displayName }}</dd>
        <dt>仓库</dt><dd>{{ project.repoProvider }} · <code>{{ project.repoUrl }}</code></dd>
        <dt>Token</dt><dd>
          <SecretInput :has-secret="project.hasRepoToken" :fetcher="() => projectsApi.get(projectId).then(() => null) as any" />
          <span v-if="project.hasRepoToken" class="muted">已设 (前端不展示明文)</span>
        </dd>
        <dt>默认分支</dt><dd>{{ project.defaultBranch }}</dd>
        <dt>项目类型</dt><dd><span class="tag">{{ project.projectType }}</span></dd>
        <dt>Project Meta</dt><dd><pre class="meta">{{ project.projectMeta ? JSON.stringify(project.projectMeta, null, 2) : '(空)' }}</pre></dd>
        <dt>描述</dt><dd>{{ project.description || '(空)' }}</dd>
        <dt>创建时间</dt><dd>{{ project.createdAt }}</dd>
        <dt>更新时间</dt><dd>{{ project.updatedAt }}</dd>
      </dl>
    </section>

    <section class="card">
      <div class="section-header">
        <h2>关联环境</h2>
        <button v-if="unlinkedEnvs.length > 0" @click="showAddEnv = !showAddEnv">
          {{ showAddEnv ? '取消' : '+ 关联环境' }}
        </button>
      </div>

      <div v-if="showAddEnv" class="add-env">
        <p class="hint">选择要关联的环境:</p>
        <ul class="env-list">
          <li v-for="e in unlinkedEnvs" :key="e.id" @click="linkEnv(e.id)">
            <code>{{ e.name }}</code> {{ e.displayName }}
            <span v-if="e.isProduction" class="tag danger">PROD</span>
          </li>
        </ul>
      </div>

      <div v-if="linkedEnvs.length === 0" class="empty">未关联任何环境</div>
      <ul v-else class="env-list">
        <li v-for="e in linkedEnvs" :key="e.id">
          <RouterLink :to="`/envs/${e.id}/variables?projectId=${projectId}`">
            <code>{{ e.name }}</code> {{ e.displayName }}
            <span v-if="e.isProduction" class="tag danger">PROD</span>
          </RouterLink>
          <button class="link danger" @click="unlinkEnv(e.id)">取消关联</button>
        </li>
      </ul>
    </section>
  </div>
  <div v-else-if="loading" class="loading">加载中...</div>
</template>

<style scoped>
.page { max-width: 920px; }
.header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.back { background: none; border: 0; cursor: pointer; color: var(--primary); }
.title { font-size: 22px; margin: 0; }
.name { font-size: 14px; color: var(--text-muted); background: #f3f4f6; padding: 2px 8px; border-radius: 4px; }
.card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 20px; margin-bottom: 16px; }
.card h2 { margin: 0 0 16px; font-size: 16px; }
dl { display: grid; grid-template-columns: 140px 1fr; gap: 8px 16px; margin: 0; font-size: 14px; }
dt { color: var(--text-muted); font-weight: 500; }
dd { margin: 0; }
.tag { padding: 2px 8px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 12px; }
.tag.danger { background: #fee2e2; color: #dc2626; }
.meta { background: #f3f4f6; padding: 8px; border-radius: 4px; font-size: 12px; margin: 0; overflow-x: auto; }
.muted { color: var(--text-soft); margin-left: 8px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.section-header h2 { margin: 0; }
.section-header button { padding: 4px 12px; background: #fff; border: 1px solid var(--border); border-radius: 4px; cursor: pointer; }
.env-list { list-style: none; padding: 0; margin: 0; }
.env-list li { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; border: 1px solid var(--border); border-radius: 4px; margin-bottom: 4px; background: #fafafa; }
.env-list a { color: var(--text); text-decoration: none; }
.env-list a:hover code { color: var(--primary); }
.link { background: none; border: 0; cursor: pointer; padding: 0; }
.link.danger { color: #dc2626; }
.empty { color: var(--text-muted); font-style: italic; }
.add-env { background: #f9fafb; border: 1px solid var(--border); border-radius: 4px; padding: 12px; margin-bottom: 12px; }
.add-env .hint { margin: 0 0 8px; font-size: 13px; color: var(--text-muted); }
.add-env ul { margin: 0; }
.add-env li { cursor: pointer; }
.add-env li:hover { background: var(--primary-soft); }
.error { color: #dc2626; background: #fee2e2; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; }
.loading { padding: 24px; text-align: center; }
</style>
