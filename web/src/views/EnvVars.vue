<script setup lang="ts">
/**
 * EnvVars.vue - 环境变量编辑页 (M7 polish).
 *
 * <p>M4 接真后端 + M5 5 加 project scope + drone 注入. M7 polish 4 个增强:
 * <ul>
 *   <li><b>批量导入 .env 格式</b> — 粘贴 KEY=VALUE 文本自动解析成多行</li>
 *   <li><b>YAML 预览</b> — K8s Secret / ConfigMap 风格 YAML, 方便复制走</li>
 *   <li><b>key 冲突检查</b> — 同 env 下重复 key 红色标记 + 顶部警告</li>
 *   <li><b>搜索/过滤</b> — 列表顶部 search box, 实时过滤 key/value/description</li>
 * </ul>
 *
 * <p>业务规则 (后端强制, 前端 UX 提示):
 * <ul>
 *   <li>secret 字段不回显明文, 需"显示明文"按钮主动调 GET /variables/{key} 解密</li>
 *   <li>批量 upsert 用空 value 过滤 (没填就不提交, 避免覆盖)</li>
 *   <li>key 冲突时仍可保存, 后端 upsert 语义是 "同 key 覆盖"</li>
 * </ul>
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { envVariablesApi, type EnvVariable, type EnvVariableUpsertItem, ApiError } from '@/api';
import EnvVarEditor from '@/components/EnvVarEditor.vue';

const route = useRoute();
const envId = computed(() => String(route.params.id));
const projectId = computed(() => {
  const p = route.query.projectId;
  return p ? String(p) : undefined;
});

const list = ref<EnvVariable[]>([]);
const loading = ref(false);
const error = ref('');
const dirty = ref(false);

const editorItems = ref<EnvVariableUpsertItem[]>([]);
const saving = ref(false);
const saveMsg = ref('');

// M7 polish: 搜索 + 弹窗状态
const searchKey = ref('');
const showImport = ref(false);
const importText = ref('');
const showYamlPreview = ref(false);

// ============== 数据加载 ==============

async function fetchList() {
  loading.value = true;
  error.value = '';
  try {
    list.value = await envVariablesApi.list(envId.value, projectId.value);
    if (!dirty.value) {
      editorItems.value = list.value.map((v) => ({
        key: v.key,
        value: '',
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

// ============== 搜索过滤 ==============

/** 同时过滤已有变量表 + 编辑器行 (按 key / description 命中) */
const filteredList = computed(() => {
  const q = searchKey.value.trim().toLowerCase();
  if (!q) return list.value;
  return list.value.filter(
    (v) =>
      v.key.toLowerCase().includes(q) ||
      (v.description ?? '').toLowerCase().includes(q),
  );
});

const filteredEditorItems = computed<{ item: EnvVariableUpsertItem; idx: number }[]>(() => {
  const q = searchKey.value.trim().toLowerCase();
  if (!q) {
    return editorItems.value.map((item, idx) => ({ item, idx }));
  }
  return editorItems.value
    .map((item, idx) => ({ item, idx }))
    .filter(({ item }) =>
      item.key.toLowerCase().includes(q) ||
      (item.description ?? '').toLowerCase().includes(q),
    );
});

/** 编辑器里哪些 idx 实际可见 (用于冲突高亮联动到具体行) */
const visibleEditorIdxSet = computed(() => {
  return new Set(filteredEditorItems.value.map((e) => e.idx));
});

// ============== 冲突检查 ==============

/** 编辑器内 key 冲突 (相同 key 出现多次) */
const editorKeyConflicts = computed(() => {
  const counts = new Map<string, number>();
  for (const item of editorItems.value) {
    if (item.key) counts.set(item.key, (counts.get(item.key) ?? 0) + 1);
  }
  return new Map([...counts.entries()].filter(([, c]) => c > 1));
});

/** 编辑器 key 跟已有变量 key 冲突 (会覆盖) */
const editorVsListConflicts = computed(() => {
  const existingKeys = new Set(list.value.map((v) => v.key));
  return new Set(editorItems.value.map((i) => i.key).filter((k) => k && existingKeys.has(k)));
});

const hasConflict = computed(
  () => editorKeyConflicts.value.size > 0 || editorVsListConflicts.value.size > 0,
);

// ============== .env 导入 ==============

/**
 * 解析 .env 格式:
 *   - 忽略空行 + # 开头的注释
 *   - KEY=VALUE 形式
 *   - VALUE 可能有引号 (' " 任意), 自动去除
 *   - 同一行 KEY 重复, 后者覆盖前者
 */
function parseEnvText(text: string): EnvVariableUpsertItem[] {
  const out = new Map<string, EnvVariableUpsertItem>();
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq < 0) continue;
    const key = trimmed.slice(0, eq).trim();
    let val = trimmed.slice(eq + 1).trim();
    // 去引号
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    if (!key) continue;
    out.set(key, { key, value: val, isSecret: 1 }); // 默认 secret, 用户后续可改
  }
  return [...out.values()];
}

function doImport() {
  const items = parseEnvText(importText.value);
  if (items.length === 0) {
    alert('没解析到任何 KEY=VALUE 行 (检查格式)');
    return;
  }
  // 合并到编辑器: 已有 key 跳过, 新 key 追加
  const existing = new Set(editorItems.value.map((i) => i.key));
  const toAdd = items.filter((i) => !existing.has(i.key));
  const skipped = items.length - toAdd.length;
  editorItems.value = [...editorItems.value, ...toAdd];
  dirty.value = true;
  saveMsg.value = `✅ 导入 ${toAdd.length} 个变量${skipped > 0 ? `, 跳过 ${skipped} 个重复` : ''}`;
  showImport.value = false;
  importText.value = '';
}

function loadImportTemplate() {
  // 给用户一个示例, 避免空 modal
  importText.value = [
    '# 粘贴 .env 文件内容 (KEY=VALUE 每行一个, # 开头是注释)',
    'DB_URL=jdbc:mysql://localhost:3306/app',
    'DB_PASSWORD=changeme',
    'REDIS_HOST=redis.local',
    'REDIS_PORT=6379',
  ].join('\n');
}

// ============== YAML 预览 ==============

/**
 * 生成 K8s Secret YAML 预览.
 *
 * <p>注意: 真实 K8s Secret data 字段要 base64 编码, 这里用纯文本方便人眼 review.
 * 真用的时候前端用 btoa() 或后端 btoa() 包一层即可 (V1 demo 简化).
 */
function buildYamlPreview(): string {
  const visible = searchKey.value.trim()
    ? filteredList.value
    : list.value;
  const lines: string[] = [];
  const envName = envId.value;
  const scope = projectId.value ? `project-${projectId.value}` : 'global';
  lines.push('# shipyard env vars preview');
  lines.push('# 真用 K8s Secret 时 data 字段要 base64 编码');
  lines.push('apiVersion: v1');
  lines.push('kind: Secret');
  lines.push('metadata:');
  lines.push(`  name: ${envName}-${scope}-env`);
  lines.push(`  labels:`);
  lines.push(`    app: shipyard`);
  lines.push(`    scope: ${scope}`);
  lines.push('type: Opaque');
  lines.push('stringData:');
  if (visible.length === 0) {
    lines.push('  # (no variables)');
  } else {
    for (const v of visible) {
      // 简单 YAML 转义
      const escaped = v.value.replace(/'/g, "''");
      lines.push(`  ${v.key}: '${escaped}'`);
    }
  }
  return lines.join('\n');
}

async function copyYaml() {
  const yaml = buildYamlPreview();
  try {
    await navigator.clipboard.writeText(yaml);
    saveMsg.value = '✅ YAML 已复制到剪贴板';
  } catch {
    // clipboard API 不可用 (file:// or insecure context), 用 fallback
    const ta = document.createElement('textarea');
    ta.value = yaml;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
    saveMsg.value = '✅ YAML 已复制 (fallback)';
  }
}

// ============== 保存 / 删除 ==============

async function onSave() {
  saving.value = true;
  saveMsg.value = '';
  try {
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

const onDirty = () => {
  dirty.value = true;
  saveMsg.value = '有未保存的修改';
};

async function showPlaintext(v: EnvVariable) {
  if (v.isSecret !== 1) return;
  try {
    const r = await envVariablesApi.getDecrypted(envId.value, v.key, projectId.value);
    alert(`变量 ${v.key} 的明文值:\n\n${r.value}`);
  } catch (e) {
    alert(`解密失败: ${(e as ApiError).message}`);
  }
}

// 冲突检查已下放到 EnvVarEditor.isConflicting (通过 conflictingKeys prop 传 set 进去).
</script>

<template>
  <div class="page">
    <div class="header">
      <RouterLink :to="`/envs`" class="back">← 返回环境列表</RouterLink>
      <h1 class="title">
        环境变量
        <span v-if="projectId" class="scope">项目级 (projectId={{ projectId }})</span>
        <span v-else class="scope">全局</span>
        <span class="count">{{ list.length }} 个变量</span>
      </h1>
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <!-- 工具栏: 搜索 + 导入 + YAML 预览 -->
    <div class="toolbar">
      <div class="search-box">
        <span class="search-icon">🔍</span>
        <input
          v-model="searchKey"
          placeholder="搜索 key 或 description..."
        />
        <button v-if="searchKey" class="clear-btn" @click="searchKey = ''" title="清空">✕</button>
      </div>
      <div class="toolbar-actions">
        <button @click="showImport = true" title="粘贴 .env 文件内容批量导入">📥 导入 .env</button>
        <button @click="showYamlPreview = true" title="预览 K8s Secret YAML">📄 YAML 预览</button>
      </div>
    </div>

    <!-- 冲突警告 banner -->
    <div v-if="hasConflict" class="conflict-banner">
      <strong>⚠️ Key 冲突</strong>
      <span v-if="editorKeyConflicts.size > 0">
        编辑器内重复: {{ [...editorKeyConflicts.keys()].join(', ') }}
      </span>
      <span v-if="editorVsListConflicts.size > 0" class="conflict-sep">|</span>
      <span v-if="editorVsListConflicts.size > 0">
        与已有变量重复 (会覆盖): {{ [...editorVsListConflicts].join(', ') }}
      </span>
    </div>

    <section class="card">
      <h2>已有变量 <span class="sub-count">({{ filteredList.length }} / {{ list.length }})</span></h2>
      <div v-if="loading" class="loading">加载中...</div>
      <table v-else-if="filteredList.length > 0" class="table">
        <thead>
          <tr>
            <th>Key</th><th>Value</th><th>Secret</th><th>Updated By</th><th>Updated At</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="v in filteredList" :key="v.id">
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
      <div v-else-if="list.length > 0" class="empty">无匹配变量 (清空搜索框看全部)</div>
      <div v-else class="empty">暂无变量, 在下方编辑器添加</div>
    </section>

    <section class="card">
      <h2>编辑器</h2>
      <p class="hint">
        输入 key 和 value 后保存; 空 value 不会被提交; 已有变量重复提交会覆盖.
        <span v-if="dirty" class="dirty-mark">● 有未保存的修改</span>
      </p>
      <EnvVarEditor
        v-model="editorItems"
        @dirty="onDirty"
        :conflicting-keys="hasConflict ? new Set([...editorKeyConflicts.keys(), ...editorVsListConflicts]) : new Set()"
        :visible-idx-set="visibleEditorIdxSet"
      />
      <div v-if="saveMsg" class="save-msg">{{ saveMsg }}</div>
      <div class="actions">
        <button @click="fetchList" :disabled="saving">重置</button>
        <button class="btn-primary" @click="onSave" :disabled="saving">
          {{ saving ? '保存中...' : '批量保存' }}
        </button>
      </div>
    </section>

    <!-- 导入 .env 弹窗 -->
    <div v-if="showImport" class="modal-mask" @click.self="showImport = false">
      <div class="modal">
        <h3>📥 导入 .env 文件</h3>
        <p class="hint">
          粘贴 KEY=VALUE 格式 (每行一个, # 开头是注释). 已存在的 key 会被跳过, 默认全标为 secret.
        </p>
        <textarea
          v-model="importText"
          class="import-text"
          rows="12"
          placeholder="DB_URL=jdbc:mysql://localhost:3306/app&#10;DB_PASSWORD=changeme&#10;..."
        />
        <div class="modal-actions">
          <button v-if="!importText" class="link" @click="loadImportTemplate">填入示例</button>
          <span v-else class="parsed-preview">
            解析到 {{ parseEnvText(importText).length }} 个变量
          </span>
          <div class="right">
            <button @click="showImport = false">取消</button>
            <button class="btn-primary" @click="doImport" :disabled="!importText">
              导入
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- YAML 预览弹窗 -->
    <div v-if="showYamlPreview" class="modal-mask" @click.self="showYamlPreview = false">
      <div class="modal modal-wide">
        <div class="modal-header">
          <h3>📄 K8s Secret YAML 预览</h3>
          <span class="hint">
            {{ filteredList.length }} 个变量
            <span v-if="searchKey">(搜索: {{ searchKey }})</span>
          </span>
        </div>
        <pre class="yaml-preview">{{ buildYamlPreview() }}</pre>
        <div class="modal-actions">
          <span class="hint">⚠️ 真实 K8s Secret data 字段需 base64 编码</span>
          <div class="right">
            <button @click="showYamlPreview = false">关闭</button>
            <button class="btn-primary" @click="copyYaml">📋 复制</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { max-width: 1100px; }
.header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.back { color: var(--primary); text-decoration: none; }
.title { font-size: 20px; margin: 0; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.scope { font-size: 12px; color: var(--text-muted); font-weight: 400; }
.count { font-size: 12px; background: #e0e7ff; color: #4338ca; padding: 2px 8px; border-radius: 4px; font-weight: 500; }
.sub-count { font-size: 12px; color: var(--text-muted); font-weight: 400; }

.toolbar {
  display: flex; align-items: center; gap: 12px; margin-bottom: 12px;
  background: var(--card); border: 1px solid var(--border); border-radius: 8px;
  padding: 8px 12px;
}
.search-box { position: relative; flex: 1; display: flex; align-items: center; }
.search-icon { position: absolute; left: 8px; font-size: 14px; color: var(--text-muted); }
.search-box input { width: 100%; padding: 6px 32px 6px 32px; border: 1px solid var(--border); border-radius: 4px; font-size: 13px; }
.search-box input:focus { outline: 2px solid var(--primary); outline-offset: -2px; }
.clear-btn { position: absolute; right: 4px; background: none; border: 0; cursor: pointer; color: var(--text-muted); padding: 4px 8px; }
.toolbar-actions { display: flex; gap: 8px; }
.toolbar-actions button { padding: 6px 12px; background: #fff; border: 1px solid var(--border); border-radius: 4px; cursor: pointer; font-size: 13px; }
.toolbar-actions button:hover { background: #f3f4f6; }

.conflict-banner {
  background: #fef3c7; border: 1px solid #fbbf24; color: #92400e;
  border-radius: 4px; padding: 8px 12px; margin-bottom: 12px; font-size: 13px;
  display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
}
.conflict-banner strong { font-weight: 600; }
.conflict-sep { color: #d97706; }

.card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 20px; margin-bottom: 16px; }
.card h2 { margin: 0 0 12px; font-size: 16px; }
.hint { color: var(--text-muted); font-size: 13px; margin: 0 0 12px; }
.dirty-mark { color: #f59e0b; font-weight: 500; margin-left: 8px; }
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

.modal-mask { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: #fff; border-radius: 8px; padding: 24px; min-width: 560px; max-width: 720px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); }
.modal-wide { min-width: 720px; max-width: 900px; }
.modal h3 { margin: 0 0 8px; }
.modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.modal-header h3 { margin: 0; }
.import-text {
  width: 100%; padding: 12px; border: 1px solid var(--border); border-radius: 4px;
  font-family: 'SF Mono', monospace; font-size: 12px; line-height: 1.5;
  resize: vertical; min-height: 240px;
}
.yaml-preview {
  background: #1e293b; color: #e2e8f0;
  font-family: 'SF Mono', monospace; font-size: 12px; line-height: 1.5;
  padding: 16px; border-radius: 4px;
  max-height: 480px; overflow: auto; margin: 0 0 12px;
  white-space: pre-wrap; word-break: break-all;
}
.modal-actions { display: flex; gap: 12px; justify-content: space-between; align-items: center; margin-top: 12px; }
.modal-actions .right { display: flex; gap: 8px; margin-left: auto; }
.modal-actions button { padding: 6px 16px; border: 1px solid var(--border); border-radius: 4px; cursor: pointer; font-size: 13px; background: #fff; }
.modal-actions .btn-primary { background: var(--primary); color: #fff; border-color: var(--primary); }
.parsed-preview { color: var(--text-muted); font-size: 13px; }
</style>
