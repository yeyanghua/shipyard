<script setup lang="ts">
/**
 * 项目详情页 - 基本信息 + 关联环境 + 触发构建 + 构建历史.
 *
 * <p>M4 接真后端 + M5 加 build 触发 + 历史列表.
 */
import { onMounted, ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { projectsApi, envsApi, buildsApi, pipelinesApi, dockerfileTemplatesApi, parseVariableSchema, recommendTemplate, type Project, type Env, type Build, type Pipeline, type DockerfileTemplate, type TemplateVariableDef, ApiError } from '@/api';
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
const activePipeline = ref<Pipeline | null>(null);
const pipelineCount = ref(0);
const dockerfileTemplates = ref<DockerfileTemplate[]>([]);
const loading = ref(true);
const error = ref('');
const showAddEnv = ref(false);
const showTrigger = ref(false);
const showDockerfileModal = ref(false);
const dockerfilePreview = ref('');
const dockerfilePreviewing = ref(false);
const dockerfileGenerating = ref(false);
const dockerfileForm = ref<{
  templateName: string;
  variables: Record<string, string>;
  repoBranch: string;
  commitMessage: string;
}>({
  templateName: '',
  variables: {},
  repoBranch: 'main',
  commitMessage: 'chore: add Dockerfile via shipyard',
});

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
    // 加载 active pipeline (M6 3) + 总版本数
    try {
      activePipeline.value = await pipelinesApi.getActive(projectId.value);
      const versions = await pipelinesApi.listVersions(projectId.value);
      pipelineCount.value = versions.length;
    } catch (e) {
      // pipeline 拉失败不影响项目详情展示
      console.warn('[ProjectDetail] pipeline load failed:', (e as ApiError).message);
    }
    // 加载 Dockerfile 模板 (M12) — 失败不影响项目详情
    try {
      dockerfileTemplates.value = await dockerfileTemplatesApi.listTemplates();
    } catch (e) {
      console.warn('[ProjectDetail] dockerfile templates load failed:', (e as ApiError).message);
    }
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

// ===== M12 Dockerfile =====

const recommendedTemplate = computed(() => {
  if (!project.value || dockerfileTemplates.value.length === 0) return null;
  return recommendTemplate(dockerfileTemplates.value, project.value.projectType);
});

const selectedTemplate = computed(() => {
  if (!dockerfileForm.value.templateName) return null;
  return dockerfileTemplates.value.find((t) => t.name === dockerfileForm.value.templateName) ?? null;
});

const templateVariableDefs = computed<TemplateVariableDef[]>(() => {
  if (!selectedTemplate.value) return [];
  return parseVariableSchema(selectedTemplate.value.variableSchema);
});

function openDockerfileModal() {
  if (!recommendedTemplate.value) {
    alert('没有可用的 Dockerfile 模板');
    return;
  }
  // 预填推荐模板 + 默认值
  dockerfileForm.value.templateName = recommendedTemplate.value.name;
  const defaults: Record<string, string> = {};
  for (const v of parseVariableSchema(recommendedTemplate.value.variableSchema)) {
    if (v.default != null) defaults[v.key] = v.default;
  }
  dockerfileForm.value.variables = defaults;
  dockerfilePreview.value = '';
  showDockerfileModal.value = true;
  // 立刻预渲染一份给用户看
  refreshPreview();
}

async function refreshPreview() {
  if (!project.value || !dockerfileForm.value.templateName) return;
  dockerfilePreviewing.value = true;
  try {
    const r = await dockerfileTemplatesApi.preview(projectId.value, {
      templateName: dockerfileForm.value.templateName,
      variables: dockerfileForm.value.variables,
      repoBranch: dockerfileForm.value.repoBranch,
      commitMessage: dockerfileForm.value.commitMessage,
    });
    dockerfilePreview.value = r.renderedContent;
  } catch (e) {
    dockerfilePreview.value = `// 预览失败: ${(e as ApiError).message}`;
  } finally {
    dockerfilePreviewing.value = false;
  }
}

function onTemplateChange(newName: string) {
  dockerfileForm.value.templateName = newName;
  // 切模板时, 用新模板的默认值重置 variables
  const tpl = dockerfileTemplates.value.find((t) => t.name === newName);
  if (tpl) {
    const defaults: Record<string, string> = {};
    for (const v of parseVariableSchema(tpl.variableSchema)) {
      if (v.default != null) defaults[v.key] = v.default;
    }
    dockerfileForm.value.variables = defaults;
  }
  refreshPreview();
}

async function doGenerate() {
  if (!project.value) return;
  dockerfileGenerating.value = true;
  try {
    const r = await dockerfileTemplatesApi.generate(projectId.value, {
      templateName: dockerfileForm.value.templateName,
      variables: dockerfileForm.value.variables,
      repoBranch: dockerfileForm.value.repoBranch,
      commitMessage: dockerfileForm.value.commitMessage,
    });
    alert(
      `✅ Dockerfile 已生成\n\n` +
      `记录 ID: ${r.projectDockerfileId}\n` +
      `状态: ${r.status}\n` +
      `分支: ${r.repoBranch}\n` +
      `Commit: ${r.commitMessage}\n\n` +
      `⚠️ V1 demo: 状态卡 draft, 未真推 Gitea. V1.5 接 Gitea adapter 后转 pushed.`
    );
    showDockerfileModal.value = false;
  } catch (e) {
    alert(`生成失败: ${(e as ApiError).message}`);
  } finally {
    dockerfileGenerating.value = false;
  }
}

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
      <div class="section-header">
        <h2>流水线 <span class="count">{{ pipelineCount }}</span></h2>
        <button class="link primary" @click="router.push(`/projects/${projectId}/pipeline`)">
          🚰 进入编辑器
        </button>
      </div>
      <div v-if="activePipeline" class="pipeline-active">
        <span class="status-dot" :style="{ background: '#10b981' }"></span>
        <span>当前 ACTIVE: <code>v{{ activePipeline.version }}</code></span>
        <span v-if="activePipeline.aiModifiedBy" class="ai-tag">AI/{{ activePipeline.aiModifiedBy }}</span>
        <span class="muted">{{ activePipeline.createdAt }}</span>
      </div>
      <div v-else class="empty">暂无 active 流水线 — 进入编辑器用 AI 生成一个</div>
    </section>

    <section class="card">
      <div class="section-header">
        <h2>Dockerfile <span class="count">{{ dockerfileTemplates.length }} 模板</span></h2>
        <button class="link primary" @click="openDockerfileModal">
          🐳 生成 Dockerfile
        </button>
      </div>
      <div v-if="recommendedTemplate" class="dockerfile-info">
        <span>项目类型 <code>{{ project.projectType }}</code> → 推荐模板:</span>
        <code class="template-name">{{ recommendedTemplate.name }}</code>
        <span class="muted">{{ recommendedTemplate.displayName }}</span>
      </div>
      <div v-else class="empty">无可用模板</div>
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

    <!-- M12 Dockerfile 弹窗 -->
    <div v-if="showDockerfileModal" class="modal-mask" @click.self="showDockerfileModal = false">
      <div class="modal modal-wide">
        <h3>🐳 生成 Dockerfile</h3>
        <p class="hint">
          ⚠️ V1 demo: 生成后状态卡 draft, 不真推 Gitea.
          V1.5 接 Gitea adapter 后转 pushed + 填 commit SHA.
        </p>

        <div class="form-row">
          <label>
            <span>模板</span>
            <select :value="dockerfileForm.templateName" @change="(e: any) => onTemplateChange(e.target.value)">
              <option v-for="t in dockerfileTemplates" :key="t.id" :value="t.name">
                {{ t.displayName }} ({{ t.language }} / {{ t.buildTool }})
              </option>
            </select>
          </label>
          <label>
            <span>目标分支</span>
            <input v-model="dockerfileForm.repoBranch" @blur="refreshPreview" />
          </label>
        </div>

        <div v-if="templateVariableDefs.length > 0" class="vars-block">
          <h4>模板变量</h4>
          <div class="vars-grid">
            <label v-for="v in templateVariableDefs" :key="v.key">
              <span>
                {{ v.description ?? v.key }}
                <em v-if="v.required">*</em>
                <code class="var-key">{{ v.key }}</code>
              </span>
              <input
                v-model="dockerfileForm.variables[v.key]"
                :placeholder="v.default ?? ''"
                @input="refreshPreview"
              />
            </label>
          </div>
        </div>

        <label class="full-row">
          <span>Commit message</span>
          <input v-model="dockerfileForm.commitMessage" @blur="refreshPreview" />
        </label>

        <h4 class="preview-title">
          预览 (渲染后)
          <span v-if="dockerfilePreviewing" class="muted">渲染中...</span>
          <button v-else class="link small" @click="refreshPreview">🔄 刷新</button>
        </h4>
        <pre class="dockerfile-preview">{{ dockerfilePreview || '// 填变量后自动预览' }}</pre>

        <div class="modal-actions">
          <button @click="showDockerfileModal = false">取消</button>
          <button class="btn-primary" :disabled="dockerfileGenerating" @click="doGenerate">
            {{ dockerfileGenerating ? '生成中...' : '💾 生成 (写 draft 记录)' }}
          </button>
        </div>
      </div>
    </div>
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

.pipeline-active {
  display: flex; align-items: center; gap: 12px; padding: 8px 12px;
  background: #f0fdf4; border: 1px solid #86efac; border-radius: 4px; font-size: 14px;
}
.ai-tag { background: #ede9fe; color: #6d28d9; padding: 1px 6px; border-radius: 3px; font-size: 11px; }
.section-header .link.primary { color: var(--primary); background: none; border: 0; cursor: pointer; font-size: 13px; }

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

/* ===== M12 Dockerfile ===== */
.dockerfile-info {
  display: flex; align-items: center; gap: 12px; padding: 8px 12px;
  background: #eff6ff; border: 1px solid #93c5fd; border-radius: 4px; font-size: 14px;
}
.template-name { background: #fff; padding: 2px 8px; border-radius: 3px; font-size: 12px; }

.modal-mask { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: #fff; border-radius: 8px; padding: 24px; min-width: 560px; max-width: 720px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); }
.modal-wide { min-width: 800px; max-width: 1000px; max-height: 90vh; overflow-y: auto; }
.modal h3 { margin: 0 0 8px; }
.modal .form-row { display: grid; grid-template-columns: 2fr 1fr; gap: 12px; margin-top: 12px; }
.modal label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.modal label span { color: var(--text-muted); font-size: 12px; display: flex; align-items: center; gap: 6px; }
.modal label em { color: #dc2626; font-style: normal; }
.modal .var-key { background: #f3f4f6; padding: 1px 6px; border-radius: 3px; font-size: 11px; color: var(--text-muted); }
.modal input, .modal select { padding: 8px 12px; border: 1px solid var(--border); border-radius: 4px; font-size: 13px; font-family: inherit; }
.modal .full-row { margin-top: 12px; }
.vars-block { margin-top: 16px; }
.vars-block h4 { margin: 0 0 8px; font-size: 13px; color: var(--text-muted); font-weight: 500; }
.vars-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.preview-title { margin: 16px 0 8px; font-size: 13px; color: var(--text-muted); font-weight: 500; display: flex; align-items: center; gap: 8px; }
.preview-title .link.small { font-size: 12px; background: none; border: 0; cursor: pointer; color: var(--primary); }
.dockerfile-preview {
  background: #1e293b; color: #e2e8f0;
  font-family: 'SF Mono', monospace; font-size: 12px; line-height: 1.5;
  padding: 16px; border-radius: 4px;
  max-height: 320px; overflow: auto; margin: 0 0 12px;
  white-space: pre-wrap; word-break: break-all;
}
.modal-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px; }
.modal-actions button { padding: 6px 16px; border: 1px solid var(--border); border-radius: 4px; cursor: pointer; font-size: 13px; background: #fff; }
.modal-actions .btn-primary { background: var(--primary); color: #fff; border-color: var(--primary); }
.modal-actions button:disabled { background: #9ca3af; cursor: not-allowed; border: 0; }
</style>
