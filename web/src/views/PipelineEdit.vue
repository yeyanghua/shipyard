<script setup lang="ts">
/**
 * PipelineEdit.vue - 流水线模板编辑器 (M6 3).
 *
 * <p>核心功能:
 * <ul>
 *   <li>左侧: YAML 编辑器 (textarea + 等宽字体) + AI 改按钮 + 保存按钮</li>
 *   <li>右侧: 版本列表 (status / version / createdAt) + 审批/激活/驳回/删除 操作</li>
 *   <li>AI 改完: 行级 LCS diff 展示 (绿 + 红行) + 上一版对比</li>
 * </ul>
 *
 * <p>业务规则 (后端强制, 前端 UX 提示):
 * <ul>
 *   <li>approved immutable — 改要新建版本</li>
 *   <li>active 必须是 approved</li>
 *   <li>approved + active 默认不可删 (force=true 跳过业务约束)</li>
 * </ul>
 *
 * <p>M1.5 升级计划: 集成 monaco-editor (vscode 同款), 现在 V1 用 textarea 凑合.
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { projectsApi, pipelinesApi, type Project, type Pipeline, ApiError } from '@/api';

const route = useRoute();
const router = useRouter();
const projectId = computed(() => String(route.params.id));

const project = ref<Project | null>(null);
const activePipeline = ref<Pipeline | null>(null);
const versions = ref<Pipeline[]>([]);
const loading = ref(true);
const error = ref('');

// 当前选中的版本 + 编辑器内容
const selectedId = ref<string | null>(null);
const selected = computed(() => versions.value.find((v) => v.id === selectedId.value) ?? null);
const draftContent = ref('');
const saving = ref(false);

// AI 弹窗
const showAiModal = ref(false);
const aiPrompt = ref('');
const aiProvider = ref<'mock' | 'tongyi' | 'deepseek'>('mock');
const aiGenerating = ref(false);

// diff 展示
const showDiff = ref(false);
const diffBase = ref<Pipeline | null>(null);
const diffLines = ref<DiffLine[]>([]);

interface DiffLine {
  type: 'same' | 'add' | 'del' | 'meta';
  text: string;
}

async function fetchData() {
  loading.value = true;
  error.value = '';
  try {
    project.value = await projectsApi.get(projectId.value);
    activePipeline.value = await pipelinesApi.getActive(projectId.value);
    versions.value = await pipelinesApi.listVersions(projectId.value);
    // 默认选 active, 否则选最新
    const sel = activePipeline.value ?? versions.value[0] ?? null;
    selectedId.value = sel?.id ?? null;
    draftContent.value = sel?.yamlContent ?? defaultYaml();
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}

onMounted(fetchData);

// 切版本时同步编辑器内容
watch(selectedId, (id) => {
  const v = versions.value.find((x) => x.id === id);
  draftContent.value = v?.yamlContent ?? defaultYaml();
  showDiff.value = false;
});

function defaultYaml(): string {
  return [
    `# Pipeline YAML for ${project.value?.name ?? 'new-project'}`,
    '# Format: drone (.drone.yml) compatible',
    'kind: pipeline',
    'name: default',
    '',
    'steps:',
    '  - name: build',
    '    image: alpine:3.19',
    '    commands:',
    '      - echo "Hello from shipyard"',
    '',
  ].join('\n');
}

function statusLabel(p: Pipeline): string {
  if (p.isActive === 1) return 'ACTIVE';
  return p.reviewStatus;
}

function statusClass(p: Pipeline): string {
  if (p.isActive === 1) return 'active';
  if (p.reviewStatus === 'APPROVED') return 'approved';
  if (p.reviewStatus === 'REJECTED') return 'rejected';
  return 'draft';
}

function findPrevVersion(p: Pipeline): Pipeline | null {
  return versions.value
    .filter((v) => v.version < p.version)
    .sort((a, b) => b.version - a.version)[0] ?? null;
}

// ============== 操作 ==============

async function saveDraft() {
  // 没选 → 创建新 draft; 已选且非 approved → 更新; approved → 提示另存为新版本
  if (selected.value && selected.value.reviewStatus === 'APPROVED') {
    await createDraft();
    return;
  }
  if (selected.value) {
    saving.value = true;
    try {
      const updated = await pipelinesApi.update(projectId.value, selected.value.id, {
        yamlContent: draftContent.value,
      });
      refreshList(updated);
    } catch (e) {
      alert(`保存失败: ${(e as ApiError).message}`);
    } finally {
      saving.value = false;
    }
  } else {
    await createDraft();
  }
}

async function createDraft() {
  saving.value = true;
  try {
    const created = await pipelinesApi.create(projectId.value, {
      yamlContent: draftContent.value,
      aiGenerate: false,
    });
    refreshList(created);
  } catch (e) {
    alert(`创建失败: ${(e as ApiError).message}`);
  } finally {
    saving.value = false;
  }
}

async function aiGenerate() {
  aiGenerating.value = true;
  try {
    const created = await pipelinesApi.create(projectId.value, {
      aiGenerate: true,
      aiPrompt: aiPrompt.value || undefined,
      aiProvider: aiProvider.value,
    });
    showAiModal.value = false;
    aiPrompt.value = '';
    refreshList(created);
    // 自动展示 diff (对比当前选中的版本, 没有就对比上一版)
    const base = selected.value && selected.value.id !== created.id
      ? selected.value
      : findPrevVersion(created);
    diffBase.value = base;
    diffLines.value = computeDiff(base?.yamlContent ?? '', created.yamlContent);
    showDiff.value = true;
  } catch (e) {
    alert(`AI 生成失败: ${(e as ApiError).message}`);
  } finally {
    aiGenerating.value = false;
  }
}

async function approve(p: Pipeline) {
  if (!confirm(`审批通过 v${p.version}?`)) return;
  try {
    const updated = await pipelinesApi.approve(projectId.value, p.id);
    refreshList(updated);
  } catch (e) {
    alert(`审批失败: ${(e as ApiError).message}`);
  }
}

async function reject(p: Pipeline) {
  if (!confirm(`驳回 v${p.version}? (终态, 不可逆)`)) return;
  try {
    const updated = await pipelinesApi.reject(projectId.value, p.id);
    refreshList(updated);
  } catch (e) {
    alert(`驳回失败: ${(e as ApiError).message}`);
  }
}

async function activate(p: Pipeline) {
  if (!confirm(`激活 v${p.version}? 同项目其他 active 版本会自动 unactivate`)) return;
  try {
    const updated = await pipelinesApi.activate(projectId.value, p.id);
    refreshList(updated);
    // 重新拉 active
    activePipeline.value = await pipelinesApi.getActive(projectId.value);
  } catch (e) {
    alert(`激活失败: ${(e as ApiError).message}`);
  }
}

async function del(p: Pipeline, force = false) {
  const msg = force
    ? `⚠️ 物理删除 v${p.version}? (跳过业务约束, 不可恢复)`
    : `删除 v${p.version}? (active 或 approved 会拒绝)`;
  if (!confirm(msg)) return;
  try {
    await pipelinesApi.delete(projectId.value, p.id, force);
    await fetchData();
  } catch (e) {
    alert(`删除失败: ${(e as ApiError).message}`);
  }
}

function refreshList(updated: Pipeline) {
  const idx = versions.value.findIndex((v) => v.id === updated.id);
  if (idx >= 0) {
    versions.value[idx] = updated;
  } else {
    versions.value = [updated, ...versions.value];
  }
  versions.value.sort((a, b) => b.version - a.version);
  selectedId.value = updated.id;
  draftContent.value = updated.yamlContent;
}

function showDiffWith(base: Pipeline | null, target: Pipeline) {
  diffBase.value = base;
  diffLines.value = computeDiff(base?.yamlContent ?? '', target.yamlContent);
  showDiff.value = true;
}

/**
 * 行级 LCS diff — 简单实现, M1.5 可换 diff-match-patch.
 */
function computeDiff(a: string, b: string): DiffLine[] {
  const aLines = a.split('\n');
  const bLines = b.split('\n');
  const m = aLines.length;
  const n = bLines.length;
  // dp[i][j] = a[i:] vs b[j:] 的 LCS 长度
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      if (aLines[i] === bLines[j]) {
        dp[i][j] = dp[i + 1][j + 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
    }
  }
  const result: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < m && j < n) {
    if (aLines[i] === bLines[j]) {
      result.push({ type: 'same', text: bLines[j] });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      result.push({ type: 'del', text: aLines[i] });
      i++;
    } else {
      result.push({ type: 'add', text: bLines[j] });
      j++;
    }
  }
  while (i < m) result.push({ type: 'del', text: aLines[i++] });
  while (j < n) result.push({ type: 'add', text: bLines[j++] });
  return result;
}
</script>

<template>
  <div class="page" v-if="!loading && project">
    <div class="header">
      <button class="back" @click="router.push(`/projects/${projectId}`)">← 返回项目</button>
      <h1 class="title">
        🚰 流水线 · <code class="name">{{ project.name }}</code>
        <span v-if="activePipeline" class="active-pill">ACTIVE: v{{ activePipeline.version }}</span>
        <span v-else class="muted">暂无 active 版本</span>
      </h1>
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <div class="layout">
      <!-- 左: YAML 编辑器 -->
      <section class="card editor-card">
        <div class="editor-header">
          <h2>YAML 编辑</h2>
          <div class="editor-actions">
            <button class="ai" @click="showAiModal = true" :disabled="aiGenerating">
              🤖 AI 帮我改
            </button>
            <button class="primary" @click="saveDraft" :disabled="saving">
              {{
                saving
                  ? '保存中...'
                  : selected && selected.reviewStatus === 'APPROVED'
                    ? '💾 另存为新版本'
                    : '💾 保存'
              }}
            </button>
          </div>
        </div>

        <div v-if="selected && selected.aiModifiedBy" class="ai-banner">
          ✨ 本版本由 AI 生成
          <span v-if="selected.aiPrompt"
            >prompt: <code>{{ selected.aiPrompt }}</code></span
          >
          <span>by {{ selected.aiModifiedBy }}</span>
        </div>

        <textarea
          v-model="draftContent"
          class="editor"
          spellcheck="false"
          :placeholder="defaultYaml()"
        />

        <div v-if="showDiff" class="diff">
          <div class="diff-header">
            <strong>Diff</strong>
            <span v-if="diffBase">v{{ diffBase.version }} → v{{ selected?.version }}</span>
            <span v-else>全新版本</span>
            <span class="diff-stats">
              +{{ diffLines.filter((l) => l.type === 'add').length }}
              −{{ diffLines.filter((l) => l.type === 'del').length }}
            </span>
            <button class="link" @click="showDiff = false">关闭</button>
          </div>
          <div class="diff-body">
            <div
              v-for="(line, i) in diffLines"
              :key="i"
              :class="['diff-line', `diff-${line.type}`]"
            >
              <span class="diff-marker">{{
                line.type === 'add' ? '+' : line.type === 'del' ? '-' : ' '
              }}</span>
              <span class="diff-text">{{ line.text }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 右: 版本列表 -->
      <section class="card versions-card">
        <h2>版本历史 <span class="count">{{ versions.length }}</span></h2>
        <ul v-if="versions.length > 0" class="version-list">
          <li
            v-for="v in versions"
            :key="v.id"
            :class="['version-item', { selected: v.id === selectedId }]"
            @click="selectedId = v.id"
          >
            <div class="v-head">
              <span :class="['status', `status-${statusClass(v)}`]">
                {{ statusLabel(v) }}
              </span>
              <code class="v-ver">v{{ v.version }}</code>
              <span class="v-time">{{ v.createdAt }}</span>
            </div>
            <div class="v-by">
              <span>by {{ v.createdBy }}</span>
              <span v-if="v.aiModifiedBy" class="ai-tag">AI/{{ v.aiModifiedBy }}</span>
            </div>
            <div v-if="v.id === selectedId" class="v-actions" @click.stop>
              <button
                v-if="v.reviewStatus === 'DRAFT'"
                class="link ok"
                @click="approve(v)"
              >
                ✓ 审批
              </button>
              <button
                v-if="v.reviewStatus === 'DRAFT'"
                class="link bad"
                @click="reject(v)"
              >
                ✗ 驳回
              </button>
              <button
                v-if="v.reviewStatus === 'APPROVED' && v.isActive !== 1"
                class="link primary"
                @click="activate(v)"
              >
                ⚡ 激活
              </button>
              <button
                v-if="v.isActive !== 1 && v.reviewStatus !== 'APPROVED'"
                class="link bad"
                @click="del(v)"
              >
                🗑 删除
              </button>
              <button
                v-else
                class="link muted"
                @click="del(v, true)"
                title="强制删除 (跳过 approved/active 保护)"
              >
                🗑 强制删
              </button>
              <button
                v-if="findPrevVersion(v)"
                class="link"
                @click="showDiffWith(findPrevVersion(v), v)"
              >
                ↔ 对比上一版
              </button>
            </div>
          </li>
        </ul>
        <div v-else class="empty">
          暂无版本 — 点上面"💾 保存"或"🤖 AI 帮我改"创建第一个
        </div>
      </section>
    </div>

    <!-- AI 弹窗 -->
    <div v-if="showAiModal" class="modal-mask" @click.self="showAiModal = false">
      <div class="modal">
        <h3>🤖 AI 生成 pipeline</h3>
        <p class="hint">
          MockLLM 会按项目类型 ({{ project.projectType }}) 返回 canned YAML.
          真 LLM 留 V1.5 接.
        </p>
        <label>
          <span>补充 prompt (可选)</span>
          <textarea
            v-model="aiPrompt"
            placeholder='例: 加 docker scan step / 用 openjdk:21-jdk / 改成 gradle'
            rows="3"
          />
        </label>
        <label>
          <span>Provider (V1 走 default, 字段留 UI 演示用)</span>
          <select v-model="aiProvider">
            <option value="mock">mock (默认, 离线可用)</option>
            <option value="tongyi">tongyi (V1.5 接入)</option>
            <option value="deepseek">deepseek (V1.5 接入)</option>
          </select>
        </label>
        <div class="modal-actions">
          <button @click="showAiModal = false">取消</button>
          <button class="ai" :disabled="aiGenerating" @click="aiGenerate">
            {{ aiGenerating ? '生成中...' : '✨ 生成' }}
          </button>
        </div>
      </div>
    </div>
  </div>
  <div v-else-if="loading" class="loading">加载中...</div>
</template>

<style scoped>
.page { max-width: 1280px; }
.header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.back { background: none; border: 0; cursor: pointer; color: var(--primary); }
.title { font-size: 20px; margin: 0; flex: 1; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.name { font-size: 14px; color: var(--text-muted); background: #f3f4f6; padding: 2px 8px; border-radius: 4px; }
.active-pill { font-size: 12px; background: #10b981; color: #fff; padding: 4px 10px; border-radius: 12px; }
.muted { color: var(--text-muted); font-size: 13px; }

.layout { display: grid; grid-template-columns: 1.6fr 1fr; gap: 16px; }
@media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }

.card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 20px; }
.card h2 { margin: 0 0 12px; font-size: 15px; }
.count { background: #e0e7ff; color: #4338ca; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }

.editor-card { display: flex; flex-direction: column; }
.editor-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.editor-header h2 { margin: 0; }
.editor-actions { display: flex; gap: 8px; }
.editor-actions button { border: 0; border-radius: 4px; padding: 6px 14px; cursor: pointer; font-size: 13px; }
.editor-actions .ai { background: linear-gradient(135deg, #8b5cf6, #6366f1); color: #fff; }
.editor-actions .ai:hover { background: linear-gradient(135deg, #7c3aed, #4f46e5); }
.editor-actions .primary { background: var(--primary); color: #fff; }
.editor-actions .primary:hover { background: var(--primary-hover, #1e40af); }
.editor-actions button:disabled { background: #9ca3af; cursor: not-allowed; }

.ai-banner {
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  border: 1px solid #c4b5fd; border-radius: 4px; padding: 8px 12px;
  font-size: 13px; margin-bottom: 8px;
  display: flex; gap: 12px; align-items: center; flex-wrap: wrap;
}
.ai-banner code { background: #fff; padding: 1px 6px; border-radius: 3px; font-size: 12px; }

.editor {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  font-size: 13px; line-height: 1.5;
  min-height: 360px; padding: 12px;
  border: 1px solid var(--border); border-radius: 4px;
  background: #1e293b; color: #e2e8f0;
  resize: vertical; tab-size: 2;
}
.editor:focus { outline: 2px solid var(--primary); outline-offset: -2px; }

.diff { margin-top: 12px; border: 1px solid var(--border); border-radius: 4px; overflow: hidden; }
.diff-header {
  display: flex; align-items: center; gap: 12px;
  background: #f9fafb; padding: 8px 12px; font-size: 13px; border-bottom: 1px solid var(--border);
}
.diff-header strong { font-weight: 600; }
.diff-header .link { margin-left: auto; }
.diff-stats { font-family: monospace; color: var(--text-muted); font-size: 12px; }
.diff-body { font-family: 'SF Mono', monospace; font-size: 12px; max-height: 360px; overflow: auto; background: #0f172a; color: #e2e8f0; }
.diff-line { display: flex; padding: 0 12px; line-height: 1.5; }
.diff-marker { width: 16px; color: #6b7280; user-select: none; flex-shrink: 0; }
.diff-text { white-space: pre-wrap; word-break: break-all; }
.diff-same .diff-marker { color: #6b7280; }
.diff-add { background: #064e3b; }
.diff-add .diff-marker { color: #34d399; }
.diff-del { background: #7f1d1d; }
.diff-del .diff-marker { color: #fca5a5; }

.versions-card { max-height: 720px; overflow-y: auto; }
.version-list { list-style: none; padding: 0; margin: 0; }
.version-item {
  border: 1px solid var(--border); border-radius: 4px; padding: 8px 12px;
  margin-bottom: 6px; cursor: pointer; background: #fafafa; transition: all 0.15s;
}
.version-item:hover { background: #f3f4f6; }
.version-item.selected { background: #eff6ff; border-color: #3b82f6; }
.v-head { display: flex; align-items: center; gap: 8px; }
.v-ver { font-size: 13px; font-weight: 500; }
.v-time { font-size: 11px; color: var(--text-muted); margin-left: auto; }
.v-by { font-size: 12px; color: var(--text-muted); margin-top: 4px; display: flex; gap: 8px; }
.ai-tag { background: #ede9fe; color: #6d28d9; padding: 1px 6px; border-radius: 3px; font-size: 11px; }
.v-actions { margin-top: 6px; display: flex; gap: 8px; flex-wrap: wrap; }
.v-actions .link { background: none; border: 0; cursor: pointer; padding: 0; font-size: 12px; }
.v-actions .link.ok { color: #10b981; }
.v-actions .link.bad { color: #dc2626; }
.v-actions .link.primary { color: var(--primary); }
.v-actions .link.muted { color: #6b7280; }

.status { font-size: 10px; padding: 2px 6px; border-radius: 3px; font-weight: 500; }
.status-active { background: #d1fae5; color: #065f46; }
.status-approved { background: #dbeafe; color: #1e40af; }
.status-rejected { background: #fee2e2; color: #991b1b; }
.status-draft { background: #fef3c7; color: #92400e; }

.empty { color: var(--text-muted); font-style: italic; padding: 12px 0; }
.error { color: #dc2626; background: #fee2e2; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; }
.loading { padding: 24px; text-align: center; }

.modal-mask { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: #fff; border-radius: 8px; padding: 24px; min-width: 480px; max-width: 600px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); }
.modal h3 { margin: 0 0 8px; }
.modal label { display: flex; flex-direction: column; gap: 4px; margin-top: 12px; font-size: 13px; }
.modal label span { color: var(--text-muted); font-size: 12px; }
.modal input, .modal select, .modal textarea { padding: 8px 12px; border: 1px solid var(--border); border-radius: 4px; font-size: 13px; font-family: inherit; }
.modal-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 20px; }
.modal-actions button { padding: 6px 16px; border: 1px solid var(--border); border-radius: 4px; cursor: pointer; font-size: 13px; background: #fff; }
.modal-actions .ai { background: linear-gradient(135deg, #8b5cf6, #6366f1); color: #fff; border: 0; }
.modal-actions button:disabled { background: #9ca3af; cursor: not-allowed; border: 0; }
.hint { font-size: 12px; color: var(--text-muted); margin: 0; }
</style>
