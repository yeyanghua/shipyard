<script setup lang="ts">
/**
 * 项目详情页 - 基本信息 + 关联环境 + 触发构建 + 构建历史.
 *
 * <p>M4 接真后端 + M5 加 build 触发 + 历史列表.
 */
import { onMounted, ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { projectsApi, envsApi, buildsApi, type Project, type Env, type Build, ApiError } from '@/api';
import { useEnvStore } from '@/stores/env';
import { useBuildStore } from '@/stores/build';
import SecretInput from '@/components/SecretInput.vue';

const route = useRoute();
const router = useRouter();
const envStore = useEnvStore();
const buildStore = useBuildStore();

const project = ref<Project | null>(null);
const envs = ref<Env[]>([]);
const linkedIds = ref<Set<string>>(new Set());
const builds = ref<Build[]>([]);
const loading = ref(true);
const error = ref('');
const showAddEnv = ref(false);
const showTrigger = ref(false);

// 触发构建表单
const triggerForm = ref({ commitSha: '', envId: '' as string | number });
const triggering = ref(false);

const projectId = computed(() => String(route.params.id));

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
    // 加载 build 历史
    const buildRes = await buildsApi.list(projectId.value, { pageNum: 1, pageSize: 10 });
    builds.value = buildRes.records;
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}

onMounted(fetchData);

async function linkEnv(envId: string) {
  try {
    await envsApi.associate(projectId.value, envId);
    linkedIds.value = new Set([...linkedIds.value, envId]);
    showAddEnv.value = false;
  } catch (e) {
    alert(`关联失败: ${(e as ApiError).message}`);
  }
}

async function unlinkEnv(envId: string) {
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

async function triggerBuild() {
  if (!triggerForm.value.commitSha) {
    alert('请输入 commit SHA');
    return;
  }
  triggering.value = true;
  try {
    const build = await buildStore.trigger({
      projectId: projectId.value,
      commitSha: triggerForm.value.commitSha,
      envId: triggerForm.value.envId ? String(triggerForm.value.envId) : undefined,
    });
    // 跳到 build 详情看实时日志
    // 跳到 build 详情看实时日志
    router.push(`/builds/${build.id}`);
  } catch (e) {
    alert(`触发失败: ${(e as ApiError).message}`);
    triggering.value = false;
  }
}

const linkedEnvs = computed(() => envs.value.filter((e) => linkedIds.value.has(e.id)));
const unlinkedEnvs = computed(() => envs.value.filter((e) => !linkedIds.value.has(e.id)));

function statusColor(status: string): string {
  switch (status) {
    case 'PENDING': return '#9ca3af';
    case 'RUNNING': return '#3b82f6';
    case 'SUCCESS': return '#10b981';
    case 'FAILED': return '#ef4444';
    case 'TIMEOUT': return '#f97316';
    case 'CANCELED': return '#6b7280';
    default: return '#9ca3af';
  }
}
</script>

<template>
  <div class="page" v-if="!loading && project">
    <div class="header">
      <button class="back" @click="router.push('/projects')">← 返回</button>
      <h1 class="title">{{ project.displayName }} <code class="name">{{ project.name }}</code></h1>
      <button class="trigger-btn" @click="showTrigger = !showTrigger">
        {{ showTrigger ? '取消' : '🚀 触发构建' }}
      </button>
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <!-- 触发构建表单 -->
    <section v-if="showTrigger" class="card trigger-card">
      <h2>触发新构建</h2>
      <div class="trigger-form">
        <label>
          <span>Commit SHA <em>*</em></span>
          <input
            v-model="triggerForm.commitSha"
            placeholder="例: a1b2c3d4e5f6 (V1 mock 任意填)"
          />
        </label>
        <label>
          <span>目标环境 (选填)</span>
          <select v-model="triggerForm.envId">
            <option value="">自动 (项目第一个关联 env)</option>
            <option v-for="e in linkedEnvs" :key="e.id" :value="e.id">
              {{ e.name }} ({{ e.displayName }})
            </option>
          </select>
        </label>
        <button class="primary" :disabled="triggering" @click="triggerBuild">
          {{ triggering ? '触发中...' : '确认触发' }}
        </button>
      </div>
      <p v-if="linkedEnvs.length === 0" class="hint">
        ⚠️ 项目未关联任何环境, env vars 注入将为空 (V1 mock 仍可跑, 但 env 变量解不到)
      </p>
    </section>

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

    <section class="card">
      <h2>构建历史 <span class="count">{{ builds.length }}</span></h2>
      <div v-if="builds.length === 0" class="empty">暂无构建记录 — 点上面"🚀 触发构建"试试</div>
      <table v-else class="build-table">
        <thead>
          <tr>
            <th>#</th><th>状态</th><th>Commit</th><th>触发人</th><th>镜像</th><th>创建</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in builds" :key="b.id" class="build-row" @click="router.push(`/builds/${b.id}`)">
            <td><code>#{{ b.id }}</code></td>
            <td>
              <span class="status-dot" :style="{ background: statusColor(b.status) }"></span>
              <span class="status-text">{{ b.status }}</span>
            </td>
            <td><code class="commit">{{ b.commitSha.substring(0, 12) }}</code></td>
            <td>{{ b.triggeredBy }}</td>
            <td>
              <code v-if="b.imageTag">{{ b.imageTag }}</code>
              <span v-else class="muted">—</span>
            </td>
            <td class="muted">{{ b.createdAt }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
  <div v-else-if="loading" class="loading">加载中...</div>
</template>

<style scoped>
.page { max-width: 1080px; }
.header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.back { background: none; border: 0; cursor: pointer; color: var(--primary); }
.title { font-size: 22px; margin: 0; flex: 1; }
.name { font-size: 14px; color: var(--text-muted); background: #f3f4f6; padding: 2px 8px; border-radius: 4px; }
.trigger-btn {
  background: var(--primary); color: #fff; border: 0; border-radius: 4px;
  padding: 8px 16px; cursor: pointer; font-size: 14px; font-weight: 500;
}
.trigger-btn:hover { background: var(--primary-hover, #1e40af); }
.card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 20px; margin-bottom: 16px; }
.card h2 { margin: 0 0 16px; font-size: 16px; display: flex; align-items: center; gap: 8px; }
.count { background: #e0e7ff; color: #4338ca; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }

.trigger-card { background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%); }
.trigger-form { display: grid; grid-template-columns: 2fr 1fr auto; gap: 12px; align-items: end; }
@media (max-width: 700px) {
  .trigger-form { grid-template-columns: 1fr; }
}
.trigger-form label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.trigger-form em { color: #dc2626; font-style: normal; }
.trigger-form input, .trigger-form select {
  padding: 8px 12px; border: 1px solid var(--border); border-radius: 4px; font-size: 13px;
}
.trigger-form .primary {
  background: var(--primary); color: #fff; border: 0; border-radius: 4px;
  padding: 8px 20px; cursor: pointer; font-size: 14px; font-weight: 500;
}
.trigger-form .primary:disabled { background: #9ca3af; cursor: not-allowed; }
.hint { color: var(--text-muted); font-size: 12px; margin: 8px 0 0; }

dl { display: grid; grid-template-columns: 140px 1fr; gap: 8px 16px; margin: 0; font-size: 14px; }
dt { color: var(--text-muted); font-weight: 500; }
dd { margin: 0; }
.tag { padding: 2px 8px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 12px; }
.tag.danger { background: #fee2e2; color: #dc2626; }
.meta { background: #f3f4f6; padding: 8px; border-radius: 4px; font-size: 12px; margin: 0; overflow-x: auto; }
.muted { color: var(--text-soft); margin-left: 8px; font-size: 12px; }

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

.build-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.build-table th, .build-table td { padding: 8px 12px; text-align: left; border-bottom: 1px solid var(--border); }
.build-table th { color: var(--text-muted); font-weight: 500; font-size: 12px; }
.build-table tr.build-row { cursor: pointer; }
.build-table tr.build-row:hover { background: var(--primary-soft); }
.commit { font-family: monospace; font-size: 12px; }
.status-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; }
.status-text { font-size: 12px; font-weight: 500; }

.error { color: #dc2626; background: #fee2e2; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; }
.loading { padding: 24px; text-align: center; }
</style>
