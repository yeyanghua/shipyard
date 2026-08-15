<script setup lang="ts">
/**
 * Workers.vue - Worker 管理页 (M9.5 redesign).
 *
 * <p>展示所有注册到 shipyard 的 worker + 状态 / 心跳 / 集群代理测试.
 *
 * <p>M9.5 redesign:
 * <ul>
 *   <li>1 worker = 1 pod (旧 "2 pod 1 row" 模型废弃, 删 "实例数" 列)</li>
 *   <li>状态机: PLANNED / PROVISIONING / ONLINE / OFFLINE / UNHEALTHY</li>
 *   <li>UI 新增 "创建 worker" 按钮 (弹窗, 填 name + podName → 返 token 明文一次性)</li>
 *   <li>UI 新增 "重新生成 token" 按钮 (旧 token 立即失效)</li>
 *   <li>每条 worker row 显示 podName (跟 k8s pod metadata.name 严格匹配)</li>
 * </ul>
 *
 * <p>后端 11 个端点 (M9.5):
 * <ul>
 *   <li>POST   /api/envs/{envId}/workers              创建 (UI 调)</li>
 *   <li>GET    /api/workers                           列表 (UI 调)</li>
 *   <li>GET    /api/workers/{id}                      详情 (UI 调)</li>
 *   <li>PUT    /api/workers/{id}                      更新 description (UI 调)</li>
 *   <li>DELETE /api/workers/{id}                      软删 (UI 调)</li>
 *   <li>POST   /api/workers/{id}/regenerate-token     重新生成 token (UI 调)</li>
 *   <li>POST   /api/workers/register                  worker 主动注册</li>
 *   <li>POST   /api/workers/{id}/heartbeat            心跳</li>
 *   <li>GET    /api/workers/{id}/cluster/*           集群代理 4 端点</li>
 * </ul>
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh, Delete, Search, Cpu, View, Key, Plus } from '@element-plus/icons-vue';
import {
  workersApi,
  envsApi,
  workerStatusBadge,
  workerHealthBadge,
  relativeTime,
  type Worker,
  type WorkerInfo,
  type CreateWorkerRequest,
  type WorkerTokenResponse,
  type Env,
} from '@/api';

const loading = ref(false);
const workers = ref<Worker[]>([]);
const envs = ref<Env[]>([]);
const detailVisible = ref(false);
const detailWorker = ref<Worker | null>(null);

// 集群代理测试数据
const clusterNs = ref<NamespaceRow[]>([]);
const clusterPods = ref<PodRow[]>([]);
const clusterDeployments = ref<DeploymentRow[]>([]);
const clusterLoading = ref(false);
const clusterErrors = ref<Record<string, string>>({});
const workerInfo = ref<WorkerInfo | null>(null);

// 集群代理读类返回 Map, TS 端用 unknown + 强转拿字段
interface NamespaceRow {
  name: string;
  status?: string;
  age?: string;
  [key: string]: unknown;
}
interface PodRow {
  name: string;
  namespace?: string;
  ready?: string;
  status?: string;
  restarts?: number;
  age?: string;
  node?: string;
  phase?: string;
  [key: string]: unknown;
}
interface DeploymentRow {
  name: string;
  namespace?: string;
  ready?: string;
  upToDate?: number;
  available?: number;
  age?: string;
  replicas?: number;
  readyReplicas?: number;
  [key: string]: unknown;
}

// 过滤
const filterStatus = ref<string>('all');
const searchQuery = ref('');

const filtered = computed(() => {
  let list = workers.value;
  if (filterStatus.value !== 'all') {
    list = list.filter((w) => w.status === filterStatus.value);
  }
  const q = searchQuery.value.trim().toLowerCase();
  if (q) {
    list = list.filter(
      (w) =>
        (w.workerUrl ?? '').toLowerCase().includes(q) ||
        (w.podName ?? '').toLowerCase().includes(q) ||
        (w.name ?? '').toLowerCase().includes(q) ||
        String(w.id).includes(q) ||
        String(w.envId).includes(q),
    );
  }
  return list;
});

const stats = computed(() => {
  const total = workers.value.length;
  const online = workers.value.filter((w) => w.status === 'ONLINE').length;
  const fresh = workers.value.filter((w) => w.heartbeatFresh).length;
  const healthy = workers.value.filter((w) => w.health === 'HEALTHY').length;
  const offline = workers.value.filter((w) => w.status === 'OFFLINE' || w.status === 'UNHEALTHY').length;
  return {
    total,
    online,
    fresh,
    healthy,
    offline,
    freshRate: total ? Math.round((fresh / total) * 100) : 0,
    healthyRate: total ? Math.round((healthy / total) * 100) : 0,
  };
});

async function fetchEnvs() {
  // M9.5: 创建 worker 时需要选 env
  try {
    const r = await envsApi.list({ page: 1, size: 100 });
    envs.value = r.records;
  } catch (e) {
    // 静默失败, 创建 worker 时再让用户重新选
    console.warn('fetchEnvs failed:', e);
  }
}

async function fetchList() {
  loading.value = true;
  try {
    const r = await workersApi.list({ page: 1, size: 100 });
    workers.value = r.records;
  } catch (e) {
    ElMessage.error(`加载 worker 列表失败: ${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await fetchEnvs();
  await fetchList();
});

async function openDetail(w: Worker) {
  detailWorker.value = w;
  detailVisible.value = true;
  clusterNs.value = [];
  clusterPods.value = [];
  clusterDeployments.value = [];
  clusterErrors.value = {};
  workerInfo.value = null;
  await loadClusterData();
}

async function loadClusterData() {
  if (!detailWorker.value) return;
  const id = detailWorker.value.id;
  clusterLoading.value = true;
  clusterErrors.value = {};

  const tasks: Array<{ key: string; run: () => Promise<unknown> }> = [
    { key: 'ns', run: () => workersApi.listNamespaces(id) },
    { key: 'pods', run: () => workersApi.listPods(id, 'default') },
    { key: 'deployments', run: () => workersApi.listDeployments(id, 'default') },
    { key: 'info', run: () => workersApi.listWorkerPods(id) },
  ];
  const results = await Promise.allSettled(tasks.map((t) => t.run()));

  results.forEach((r, idx) => {
    const key = tasks[idx].key;
    if (r.status === 'fulfilled') {
      if (key === 'ns') clusterNs.value = r.value as NamespaceRow[];
      else if (key === 'pods') clusterPods.value = r.value as PodRow[];
      else if (key === 'deployments') clusterDeployments.value = r.value as DeploymentRow[];
      else if (key === 'info') workerInfo.value = r.value as unknown as WorkerInfo;
    } else {
      const reason = (r.reason as Error)?.message ?? String(r.reason);
      clusterErrors.value[key] = `集群代理测试失败 (worker 可能未连上 shipyard): ${reason}`;
      console.warn(`[Workers.loadClusterData] ${key} failed:`, reason);
    }
  });

  clusterLoading.value = false;
}

async function deleteWorker(w: Worker) {
  try {
    await ElMessageBox.confirm(
      `确认删除 worker ${w.name} (id=${w.id})?\n软删后 shipyard 不再调度, 但已 register 的 worker 实例仍跑.\n注意: 此操作不可撤销, 需重新创建 worker + register.`,
      '删除 worker',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    await workersApi.delete(w.id);
    ElMessage.success(`已删除 worker ${w.name}`);
    await fetchList();
  } catch (e) {
    ElMessage.error(`删除失败: ${(e as Error).message}`);
  }
}

// ==================== 创建 worker (M9.5 新增) ====================

const createDialogVisible = ref(false);
const createForm = reactive<CreateWorkerRequest>({
  name: '',
  podName: '',
  description: '',
});
const createFormRef = ref();

function openCreateDialog() {
  createForm.name = '';
  createForm.podName = '';
  createForm.description = '';
  createDialogVisible.value = true;
}

async function submitCreate() {
  if (!createFormRef.value) return;
  try {
    await createFormRef.value.validate();
  } catch {
    return;
  }
  if (envs.value.length === 0) {
    ElMessage.error('请先创建环境 (env 列表为空)');
    return;
  }
  // 选第一个 env (M9.5 demo 默认 dev, 生产应该是 UI 让用户选)
  const envId = envs.value[0].id;
  try {
    const resp = await workersApi.create(envId, createForm);
    createDialogVisible.value = false;
    showTokenDialog(resp);
    await fetchList();
  } catch (e) {
    ElMessage.error(`创建失败: ${(e as Error).message}`);
  }
}

// 选 env 的版本 (M9.5: 严格选, 不默认第一个)
const selectedEnvId = ref<string>('');

// 选 env 校验
const envRules = {
  required: true,
  message: '请选择环境',
  trigger: 'change',
};
const nameRules = {
  required: true,
  pattern: /^[a-z0-9-]+$/,
  message: '只能包含小写字母/数字/中划线',
  trigger: 'blur',
};
const podNameRules = {
  required: true,
  pattern: /^[a-z0-9-]+$/,
  message: '只能包含小写字母/数字/中划线 (跟 k8s pod name 规则一致)',
  trigger: 'blur',
};

// ==================== 显示 token 一次性 (M9.5 新增) ====================

const tokenDialogVisible = ref(false);
const tokenData = ref<WorkerTokenResponse | null>(null);

function showTokenDialog(data: WorkerTokenResponse) {
  tokenData.value = data;
  tokenDialogVisible.value = true;
}

async function copyToken() {
  if (!tokenData.value) return;
  try {
    await navigator.clipboard.writeText(tokenData.value.token);
    ElMessage.success('token 已复制到剪贴板');
  } catch {
    ElMessage.warning('复制失败, 请手动复制');
  }
}

// ==================== 重新生成 token (M9.5 新增) ====================

async function regenerateToken(w: Worker) {
  try {
    await ElMessageBox.confirm(
      `重新生成 worker ${w.name} 的 token?\n\n旧 token 立即失效, k8s worker pod 的 register / heartbeat 会失败, 需更新 WORKER_TOKEN env 后重启 pod.`,
      '重新生成 token',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    const resp = await workersApi.regenerateToken(w.id);
    showTokenDialog(resp);
  } catch (e) {
    ElMessage.error(`重新生成失败: ${(e as Error).message}`);
  }
}

function statusBadge(status: string) {
  return workerStatusBadge(status);
}

function podPhaseClass(phase: string) {
  return {
    'pod-running': phase === 'Running',
    'pod-pending': phase === 'Pending',
    'pod-failed': phase === 'Failed' || phase === 'Unknown',
  };
}
function podPhaseTagType(phase: string): 'success' | 'warning' | 'danger' | 'info' {
  if (phase === 'Running') return 'success';
  if (phase === 'Pending') return 'warning';
  if (phase === 'Failed' || phase === 'Unknown') return 'danger';
  return 'info';
}

function fmtTime(iso: string | null): string {
  return relativeTime(iso);
}
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>Worker 管理</h1>
        <p class="muted">所有注册到 shipyard 的 worker 实例 · 状态 / 心跳 / 集群代理测试</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchQuery"
          placeholder="搜索 name / pod / URL / env..."
          clearable
          size="default"
          style="width: 240px;"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterStatus" size="default" style="width: 160px;">
          <el-option label="全部" value="all" />
          <el-option label="在线" value="ONLINE" />
          <el-option label="启动中" value="PROVISIONING" />
          <el-option label="已规划" value="PLANNED" />
          <el-option label="离线" value="OFFLINE" />
          <el-option label="不健康" value="UNHEALTHY" />
        </el-select>
        <el-button :icon="Refresh" @click="fetchList" :loading="loading">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          创建 worker
        </el-button>
      </div>
    </header>

    <!-- ===== KPI 卡片 ===== -->
    <section class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-label">在线 Worker</div>
        <div class="kpi-value">
          <span class="status-dot success" v-if="stats.online > 0"></span>
          {{ stats.online }}
        </div>
        <div class="kpi-trend muted">总 {{ stats.total }} 个</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">心跳新鲜</div>
        <div class="kpi-value">{{ stats.fresh }}<span class="unit">/{{ stats.total }}</span></div>
        <div class="kpi-trend" :class="stats.freshRate >= 80 ? 'up' : stats.freshRate >= 50 ? 'muted' : 'down'">
          {{ stats.freshRate }}% 在线
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">Worker 健康</div>
        <div class="kpi-value">
          <span class="status-dot success" v-if="stats.healthy === stats.total && stats.total > 0"></span>
          {{ stats.healthy }}<span class="unit">/{{ stats.total }}</span>
        </div>
        <div class="kpi-trend" :class="stats.healthyRate >= 80 ? 'up' : stats.healthyRate >= 50 ? 'muted' : 'down'">
          {{ stats.healthyRate }}% 自检过
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">离线 / 不健康</div>
        <div class="kpi-value">{{ stats.offline }}</div>
        <div class="kpi-trend muted">需排查</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">总 Worker</div>
        <div class="kpi-value">{{ stats.total }}</div>
        <div class="kpi-trend muted">所有环境</div>
      </div>
    </section>

    <!-- ===== 列表表格 ===== -->
    <section class="card list-card">
      <el-table
        :data="filtered"
        v-loading="loading"
        stripe
        :empty-text="workers.length === 0 ? '还没有 worker 注册, 点击右上角「创建 worker」' : '无匹配结果'"
        @row-click="openDetail"
        row-class-name="clickable-row"
      >
        <el-table-column prop="id" label="ID" width="100">
          <template #default="{ row }">
            <code class="text-mono">#{{ row.id }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="envId" label="环境" width="100">
          <template #default="{ row }">
            <span v-if="row.envId">
              <code class="text-mono">env-{{ row.envId }}</code>
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="Worker 名" width="160">
          <template #default="{ row }">
            <code class="text-mono text-sm">{{ row.name }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="podName" label="Pod 名 (k8s)" width="180">
          <template #default="{ row }">
            <code class="text-mono text-sm" :title="row.podName">{{ row.podName }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="workerUrl" label="Worker URL" min-width="200">
          <template #default="{ row }">
            <code v-if="row.workerUrl" class="text-mono text-sm">{{ row.workerUrl }}</code>
            <span v-else class="muted text-xs">(未 register)</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusBadge(row.status).severity" size="small" effect="dark">
              <span class="status-dot" :class="row.status === 'ONLINE' ? 'success' : row.status === 'UNHEALTHY' ? 'warning' : 'muted'"></span>
              {{ statusBadge(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="health" label="健康" width="120">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.healthDetail"
              :content="row.healthDetail"
              placement="top"
            >
              <el-tag :type="workerHealthBadge(row.health).severity" size="small" effect="plain">
                <span class="status-dot" :class="row.health === 'HEALTHY' ? 'success' : 'warning'"></span>
                {{ workerHealthBadge(row.health).label }}
              </el-tag>
            </el-tooltip>
            <el-tag v-else :type="workerHealthBadge(row.health).severity" size="small" effect="plain">
              <span class="status-dot" :class="row.health === 'HEALTHY' ? 'success' : 'warning'"></span>
              {{ workerHealthBadge(row.health).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="100">
          <template #default="{ row }">
            <code class="text-mono text-sm">{{ row.version || 'dev' }}</code>
          </template>
        </el-table-column>
        <el-table-column label="心跳" width="120">
          <template #default="{ row }">
            <el-tooltip :content="row.lastHeartbeatAt || '-'" placement="top" :show-after="200">
              <span :class="row.heartbeatFresh ? 'heartbeat-ok' : 'heartbeat-stale'">
                <span class="status-dot" :class="row.heartbeatFresh ? 'success' : 'warning'"></span>
                {{ fmtTime(row.lastHeartbeatAt) }}
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="120">
          <template #default="{ row }">
            <el-tooltip :content="row.createdAt || '-'" placement="top" :show-after="200">
              <span class="muted text-sm">{{ fmtTime(row.createdAt) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click.stop="openDetail(row)">
              <el-icon><View /></el-icon>
              详情
            </el-button>
            <el-button size="small" link type="warning" @click.stop="regenerateToken(row)">
              <el-icon><Key /></el-icon>
              重新生成 token
            </el-button>
            <el-button size="small" link type="danger" @click.stop="deleteWorker(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ===== 详情 Drawer ===== -->
    <el-drawer
      v-model="detailVisible"
      :title="`Worker #${detailWorker?.id} 详情`"
      direction="rtl"
      size="720px"
    >
      <div v-if="detailWorker" class="detail-content">
        <!-- 基本信息 -->
        <section class="detail-section">
          <h3>基本信息</h3>
          <dl class="detail-list">
            <dt>Worker ID</dt><dd><code>{{ detailWorker.id }}</code></dd>
            <dt>环境 ID</dt>
            <dd>
              <code v-if="detailWorker.envId">env-{{ detailWorker.envId }}</code>
              <span v-else class="muted">—</span>
            </dd>
            <dt>Worker 名</dt>
            <dd><code class="text-mono">{{ detailWorker.name }}</code></dd>
            <dt>Pod 名 (k8s)</dt>
            <dd><code class="text-mono">{{ detailWorker.podName }}</code></dd>
            <dt>Worker URL</dt>
            <dd>
              <code v-if="detailWorker.workerUrl" class="text-sm">{{ detailWorker.workerUrl }}</code>
              <span v-else class="muted">(未 register)</span>
            </dd>
            <dt>状态</dt>
            <dd>
              <el-tag :type="statusBadge(detailWorker.status).severity" size="small" effect="dark">
                {{ statusBadge(detailWorker.status).label }}
              </el-tag>
            </dd>
            <dt>健康</dt>
            <dd>
              <el-tooltip
                v-if="detailWorker.healthDetail"
                :content="detailWorker.healthDetail"
                placement="top"
              >
                <el-tag :type="workerHealthBadge(detailWorker.health).severity" size="small" effect="plain">
                  {{ workerHealthBadge(detailWorker.health).label }}
                </el-tag>
              </el-tooltip>
              <el-tag v-else :type="workerHealthBadge(detailWorker.health).severity" size="small" effect="plain">
                {{ workerHealthBadge(detailWorker.health).label }}
              </el-tag>
              <span v-if="detailWorker.healthDetail" class="muted text-xs" style="margin-left: 8px;">
                {{ detailWorker.healthDetail }}
              </span>
            </dd>
            <dt>版本</dt><dd><code class="text-sm">{{ detailWorker.version || 'dev' }}</code></dd>
            <dt>最后心跳</dt>
            <dd>
              <span :class="detailWorker.heartbeatFresh ? 'heartbeat-ok' : 'heartbeat-stale'">
                <span class="status-dot" :class="detailWorker.heartbeatFresh ? 'success' : 'warning'"></span>
                {{ fmtTime(detailWorker.lastHeartbeatAt) }}
              </span>
              <span v-if="detailWorker.lastHeartbeatAt" class="muted text-sm">
                ({{ detailWorker.lastHeartbeatAt }})
              </span>
            </dd>
            <dt>注册时间</dt><dd class="text-sm">{{ detailWorker.createdAt }}</dd>
            <dt>更新时间</dt><dd class="text-sm">{{ detailWorker.updatedAt }}</dd>
            <dt>创建人</dt><dd><code class="text-mono text-sm">{{ detailWorker.createdBy }}</code></dd>
            <dt>更新人</dt><dd><code class="text-mono text-sm">{{ detailWorker.updatedBy }}</code></dd>
          </dl>
        </section>

        <!-- 集群代理测试 -->
        <section class="detail-section">
          <div class="section-header">
            <h3>集群代理测试 <span class="muted">· shipyard → worker → k8s</span></h3>
            <el-button size="small" :icon="Refresh" @click="loadClusterData" :loading="clusterLoading">
              重测
            </el-button>
          </div>

          <el-alert
            v-for="(msg, key) in clusterErrors"
            :key="key"
            :title="`集群代理 · ${key} 接口失败`"
            :description="msg"
            type="warning"
            show-icon
            :closable="false"
            class="cluster-error"
          />

          <div v-if="workerInfo" class="replica-banner">
            <div class="replica-stats">
              <el-tag
                :type="workerInfo.readyReplicas === workerInfo.replicas ? 'success' : 'warning'"
                size="default"
                effect="dark"
              >
                {{ workerInfo.readyReplicas }} / {{ workerInfo.replicas }} Pod
              </el-tag>
              <span class="muted text-sm">
                deployment <code class="text-mono">{{ workerInfo.workerName }}</code> @ ns <code class="text-mono">{{ workerInfo.namespace }}</code>
              </span>
            </div>
            <ul v-if="workerInfo.pods.length > 0" class="pod-list">
              <li v-for="p in workerInfo.pods" :key="p.name" :class="podPhaseClass(p.phase)">
                <span class="pod-name">
                  <code class="text-mono text-sm">{{ p.name }}</code>
                </span>
                <span class="pod-meta">
                  <el-tag size="small" :type="podPhaseTagType(p.phase)" effect="plain">{{ p.phase }}</el-tag>
                  <span v-if="p.ready" class="text-xs muted">ready {{ p.ready }}</span>
                  <span v-if="p.node" class="text-xs muted">node {{ p.node }}</span>
                  <span v-if="p.ip" class="text-xs muted">ip {{ p.ip }}</span>
                </span>
              </li>
            </ul>
            <p v-else class="muted text-sm">未查询到 pod (worker fake mode 或 deployment 还没起)</p>
          </div>

          <div class="cluster-grid">
            <div class="cluster-card">
              <div class="cluster-label">
                <el-icon><Cpu /></el-icon> Namespaces
                <el-tag size="small" effect="plain">{{ clusterNs.length }}</el-tag>
              </div>
              <ul class="cluster-list">
                <li v-for="ns in clusterNs" :key="String(ns.name)">
                  <code class="text-sm">{{ ns.name }}</code>
                  <span class="muted text-xs">{{ ns.status }}</span>
                </li>
                <li v-if="clusterNs.length === 0 && !clusterLoading" class="empty">无数据</li>
              </ul>
            </div>

            <div class="cluster-card">
              <div class="cluster-label">
                <el-icon><Cpu /></el-icon> Pods (default ns)
                <el-tag size="small" effect="plain">{{ clusterPods.length }}</el-tag>
              </div>
              <ul class="cluster-list">
                <li v-for="p in clusterPods" :key="String(p.name)">
                  <code class="text-sm">{{ p.name }}</code>
                  <span class="muted text-xs">{{ p.phase || p.status }}</span>
                </li>
                <li v-if="clusterPods.length === 0 && !clusterLoading" class="empty">无数据</li>
              </ul>
            </div>

            <div class="cluster-card">
              <div class="cluster-label">
                <el-icon><Cpu /></el-icon> Deployments (default ns)
                <el-tag size="small" effect="plain">{{ clusterDeployments.length }}</el-tag>
              </div>
              <ul class="cluster-list">
                <li v-for="d in clusterDeployments" :key="String(d.name)">
                  <code class="text-sm">{{ d.name }}</code>
                  <span class="muted text-xs">
                    {{ d.readyReplicas || 0 }}/{{ d.replicas || 0 }} ready
                  </span>
                </li>
                <li v-if="clusterDeployments.length === 0 && !clusterLoading" class="empty">无数据</li>
              </ul>
            </div>
          </div>

          <p class="hint">
            💡 这是 shipyard 通过 worker 拿到的 k8s 真实数据 (worker 调 k8s API).
            如果列表为空, 检查 worker 是否连上 shipyard (心跳状态 + URL 可达).
          </p>
        </section>

        <div class="detail-actions">
          <el-button type="warning" :icon="Key" @click="regenerateToken(detailWorker)">
            重新生成 token
          </el-button>
          <el-button type="danger" :icon="Delete" @click="deleteWorker(detailWorker)">
            删除 worker
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- ===== 创建 worker 对话框 (M9.5 新增) ===== -->
    <el-dialog
      v-model="createDialogVisible"
      title="创建 Worker (预登记)"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="createFormRef" :model="createForm" label-width="120px">
        <el-form-item label="环境" :rules="[envRules]">
          <el-select v-model="selectedEnvId" placeholder="选择 worker 所属环境" style="width: 100%;">
            <el-option
              v-for="e in envs"
              :key="e.id"
              :label="`${e.name} (${e.displayName})`"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Worker 名" :rules="[nameRules]">
          <el-input
            v-model="createForm.name"
            placeholder="e.g. shipyard-worker-dev-1"
            maxlength="64"
            show-word-limit
          />
          <div class="form-hint">shipyard 内部展示名, 同 env 下唯一</div>
        </el-form-item>
        <el-form-item label="Pod 名 (k8s)" :rules="[podNameRules]">
          <el-input
            v-model="createForm.podName"
            placeholder="跟 k8s manifest 的 metadata.name 一致"
            maxlength="128"
            show-word-limit
          />
          <div class="form-hint">严格匹配, register 时 shipyard 用这个找预登记 row</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="2"
            maxlength="256"
            show-word-limit
            placeholder="可选, 这个 worker 的用途/owner"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建并获取 token</el-button>
      </template>
    </el-dialog>

    <!-- ===== Token 一次性展示对话框 (M9.5 新增) ===== -->
    <el-dialog
      v-model="tokenDialogVisible"
      title="Worker Token (一次性展示)"
      width="640px"
      :close-on-click-modal="false"
      :show-close="false"
    >
      <el-alert
        v-if="tokenData"
        :title="tokenData.notice"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px;"
      />
      <div v-if="tokenData" class="token-display">
        <code class="token-value">{{ tokenData.token }}</code>
        <el-button :icon="Key" @click="copyToken" style="margin-left: 12px;">复制</el-button>
      </div>
      <p v-if="tokenData" class="hint">
        <strong>用法</strong>: 把这个 token 配到 k8s worker manifest:
      </p>
      <pre v-if="tokenData" class="yaml-hint">env:
  - name: SHIPYARD_URL
    value: "http://shipyard:8080"
  - name: WORKER_TOKEN
    value: "{{ tokenData.token }}"</pre>
      <template #footer>
        <el-button type="primary" @click="tokenDialogVisible = false">我已保存 token, 关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { max-width: 1400px; margin: 0 auto; display: flex; flex-direction: column; gap: var(--space-5); }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--space-4); flex-wrap: wrap; }
.page-header h1 { font-size: 24px; font-weight: 700; margin: 0; }
.muted { color: var(--color-text-muted); font-size: 12px; margin-top: 4px; }
.header-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

.kpi-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: var(--space-4); }
@media (max-width: 1100px) { .kpi-grid { grid-template-columns: repeat(2, 1fr); } }
.kpi-card { background: var(--color-bg-surface); border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: var(--space-5); display: flex; flex-direction: column; gap: 4px; }
.kpi-label { font-size: 11px; color: var(--color-text-secondary); text-transform: uppercase; letter-spacing: 0.05em; font-weight: 500; }
.kpi-value { font-size: 32px; font-weight: 700; line-height: 1.2; color: var(--color-text-primary); display: flex; align-items: center; gap: 8px; }
.kpi-value .unit { font-size: 14px; font-weight: 500; color: var(--color-text-muted); }
.kpi-trend { font-size: 12px; }
.kpi-trend.up { color: var(--color-success); }
.kpi-trend.down { color: var(--color-danger); }

.card { background: var(--color-bg-surface); border: 1px solid var(--color-border); border-radius: var(--radius-md); }
.list-card { overflow: hidden; }

.detail-content { display: flex; flex-direction: column; gap: var(--space-5); padding: 0 0 24px 0; }
.detail-section { display: flex; flex-direction: column; gap: var(--space-3); }
.detail-section h3 { margin: 0; font-size: 16px; font-weight: 600; color: var(--color-text-primary); }
.section-header { display: flex; align-items: center; justify-content: space-between; }
.detail-list { display: grid; grid-template-columns: 120px 1fr; gap: 8px 16px; margin: 0; }
.detail-list dt { color: var(--color-text-muted); font-size: 12px; padding-top: 4px; }
.detail-list dd { margin: 0; font-size: 14px; color: var(--color-text-primary); word-break: break-all; }

.cluster-error { margin-bottom: 8px; }
.cluster-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: var(--space-3); }
@media (max-width: 720px) { .cluster-grid { grid-template-columns: 1fr; } }
.cluster-card { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: var(--space-3); display: flex; flex-direction: column; gap: 8px; }
.cluster-label { display: flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 600; color: var(--color-text-secondary); }
.cluster-list { list-style: none; padding: 0; margin: 0; max-height: 200px; overflow-y: auto; }
.cluster-list li { display: flex; align-items: center; gap: 6px; padding: 4px 0; border-bottom: 1px solid var(--color-border); font-size: 12px; }
.cluster-list li:last-child { border-bottom: none; }
.cluster-list .empty { color: var(--color-text-muted); text-align: center; padding: 12px; }

.replica-banner { display: flex; flex-direction: column; gap: 8px; padding: 12px; background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.replica-stats { display: flex; align-items: center; gap: 12px; }
.pod-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 4px; }
.pod-list li { display: flex; align-items: center; gap: 8px; padding: 6px; background: var(--color-bg-surface); border-radius: var(--radius-sm); }
.pod-list .pod-running { border-left: 3px solid var(--color-success); }
.pod-list .pod-pending { border-left: 3px solid var(--color-warning); }
.pod-list .pod-failed { border-left: 3px solid var(--color-danger); }
.pod-name { flex: 1; }
.pod-meta { display: flex; align-items: center; gap: 6px; }

.detail-actions { display: flex; gap: 8px; justify-content: flex-end; padding-top: 12px; border-top: 1px solid var(--color-border); }

.hint { font-size: 12px; color: var(--color-text-muted); margin: 8px 0 0 0; line-height: 1.5; }
.form-hint { font-size: 11px; color: var(--color-text-muted); margin-top: 4px; line-height: 1.4; }

.token-display { display: flex; align-items: center; padding: 12px; background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.token-value { flex: 1; font-family: var(--font-mono); font-size: 12px; word-break: break-all; color: var(--color-text-primary); user-select: all; }
.yaml-hint { padding: 12px; background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-family: var(--font-mono); font-size: 12px; line-height: 1.5; overflow-x: auto; margin: 0; }

.text-mono { font-family: var(--font-mono); }
.text-sm { font-size: 12px; }
.text-xs { font-size: 11px; }
.status-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; margin-right: 4px; vertical-align: middle; }
.status-dot.success { background: var(--color-success); }
.status-dot.warning { background: var(--color-warning); }
.status-dot.muted { background: var(--color-text-muted); }
.heartbeat-ok { color: var(--color-success); }
.heartbeat-stale { color: var(--color-warning); }
.clickable-row { cursor: pointer; }
.clickable-row:hover { background-color: var(--color-bg-hover); }
</style>
