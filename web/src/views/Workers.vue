<script setup lang="ts">
/**
 * Workers.vue - Worker 管理页 (M13 Phase 5).
 *
 * <p>展示注册到 shipyard 的所有 worker + 健康状态 + 集群信息代理测试.
 *
 * <p>设计:
 * <ul>
 *   <li>顶部 3 stat: 在线 worker / 总数 / 心跳新鲜率</li>
 *   <li>列表表格: env / workerUrl / status / 心跳 / 注册时间 + 行内操作</li>
 *   <li>详情侧栏 (Drawer): 完整字段 + 集群代理测试 (拉 ns/pods/deployments)</li>
 *   <li>删除按钮 (二次确认)</li>
 * </ul>
 *
 * <p>M8.2 后端 8 端点已就绪: list / get / delete / listNamespaces / listPods / listDeployments.
 */
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh, Delete, Search, Cpu, View } from '@element-plus/icons-vue';
import { workersApi, workerStatusBadge, workerHealthBadge, relativeTime, type Worker } from '@/api';

const loading = ref(false);
const workers = ref<Worker[]>([]);
const detailVisible = ref(false);
const detailWorker = ref<Worker | null>(null);

// 集群代理测试数据
const clusterNs = ref<Array<Record<string, unknown>>>([]);
const clusterPods = ref<Array<Record<string, unknown>>>([]);
const clusterDeployments = ref<Array<Record<string, unknown>>>([]);
const clusterLoading = ref(false);

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
        String(w.workerUrl).toLowerCase().includes(q) ||
        String(w.id).includes(q) ||
        String(w.envId).includes(q),
    );
  }
  return list;
});

const stats = computed(() => {
  const total = workers.value.length;
  const active = workers.value.filter((w) => w.status === 'ACTIVE').length;
  const fresh = workers.value.filter((w) => w.heartbeatFresh).length;
  const healthy = workers.value.filter((w) => w.health === 'HEALTHY').length;
  return {
    total,
    active,
    fresh,
    healthy,
    freshRate: total ? Math.round((fresh / total) * 100) : 0,
    healthyRate: total ? Math.round((healthy / total) * 100) : 0,
  };
});

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

onMounted(fetchList);

async function openDetail(w: Worker) {
  detailWorker.value = w;
  detailVisible.value = true;
  clusterNs.value = [];
  clusterPods.value = [];
  clusterDeployments.value = [];
  await loadClusterData();
}

async function loadClusterData() {
  if (!detailWorker.value) return;
  clusterLoading.value = true;
  try {
    const [ns, pods, deps] = await Promise.all([
      workersApi.listNamespaces(detailWorker.value.id),
      workersApi.listPods(detailWorker.value.id, 'default'),
      workersApi.listDeployments(detailWorker.value.id, 'default'),
    ]);
    clusterNs.value = ns;
    clusterPods.value = pods;
    clusterDeployments.value = deps;
  } catch (e) {
    ElMessage.warning(`集群代理测试失败 (worker 可能未连上 shipyard): ${(e as Error).message}`);
  } finally {
    clusterLoading.value = false;
  }
}

async function deleteWorker(w: Worker) {
  try {
    await ElMessageBox.confirm(
      `确认删除 worker ${w.id}? (软删, shipyard 不再调度, 但已注册的 worker 实例仍跑)`,
      '删除 worker',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    await workersApi.delete(w.id);
    ElMessage.success(`已删除 worker ${w.id}`);
    await fetchList();
  } catch (e) {
    ElMessage.error(`删除失败: ${(e as Error).message}`);
  }
}

function statusBadge(status: string) {
  return workerStatusBadge(status);
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
          placeholder="搜索 URL / ID / env..."
          clearable
          size="default"
          style="width: 220px;"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterStatus" size="default" style="width: 140px;">
          <el-option label="全部" value="all" />
          <el-option label="活跃" value="ACTIVE" />
          <el-option label="排空中" value="DRAINING" />
          <el-option label="离线" value="INACTIVE" />
        </el-select>
        <el-button :icon="Refresh" @click="fetchList" :loading="loading">刷新</el-button>
      </div>
    </header>

    <!-- ===== KPI 卡片 ===== -->
    <section class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-label">在线 Worker</div>
        <div class="kpi-value">
          <span class="status-dot success" v-if="stats.active > 0"></span>
          {{ stats.active }}
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
        <div class="kpi-label">离线</div>
        <div class="kpi-value">{{ stats.total - stats.active }}</div>
        <div class="kpi-trend muted">30s+ 未上报</div>
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
        :empty-text="workers.length === 0 ? '还没有 worker 注册' : '无匹配结果'"
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
        <el-table-column prop="workerUrl" label="Worker URL" min-width="280">
          <template #default="{ row }">
            <code class="text-mono text-sm">{{ row.workerUrl }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusBadge(row.status).severity" size="small" effect="dark">
              <span class="status-dot" :class="row.status === 'ACTIVE' ? 'success' : 'muted'"></span>
              {{ statusBadge(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="health" label="健康" width="140">
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
        <el-table-column prop="version" label="版本" width="120">
          <template #default="{ row }">
            <code class="text-mono text-sm">{{ row.version || 'dev' }}</code>
          </template>
        </el-table-column>
        <el-table-column label="心跳" width="160">
          <template #default="{ row }">
            <span :class="row.heartbeatFresh ? 'heartbeat-ok' : 'heartbeat-stale'">
              <span class="status-dot" :class="row.heartbeatFresh ? 'success' : 'warning'"></span>
              {{ fmtTime(row.lastHeartbeatAt) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180">
          <template #default="{ row }">
            <span class="muted text-sm">{{ row.createdAt }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click.stop="openDetail(row)">
          <el-icon><View /></el-icon>
              详情
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
      size="640px"
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
            <dt>Worker URL</dt>
            <dd><code class="text-sm">{{ detailWorker.workerUrl }}</code></dd>
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
          <el-button type="danger" :icon="Delete" @click="deleteWorker(detailWorker)">
            删除 worker
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; display: flex; flex-direction: column; gap: var(--space-5); }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--space-4); flex-wrap: wrap; }
.page-header h1 { font-size: 24px; font-weight: 700; margin: 0; }
.muted { color: var(--color-text-muted); font-size: 12px; margin-top: 4px; }
.header-actions { display: flex; gap: 8px; align-items: center; }

.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--space-4); }
@media (max-width: 900px) { .kpi-grid { grid-template-columns: repeat(2, 1fr); } }
.kpi-card { background: var(--color-bg-surface); border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: var(--space-5); display: flex; flex-direction: column; gap: 4px; }
.kpi-label { font-size: 11px; color: var(--color-text-secondary); text-transform: uppercase; letter-spacing: 0.05em; font-weight: 500; }
.kpi-value { font-family: var(--font-display); font-size: 32px; font-weight: 700; font-variant-numeric: tabular-nums; line-height: 1.2; display: flex; align-items: center; gap: 8px; }
.kpi-value .unit { font-size: 16px; color: var(--color-text-muted); font-weight: 500; }
.kpi-trend { font-size: 11px; margin-top: 4px; font-family: var(--font-mono); }
.kpi-trend.up { color: var(--color-success); }
.kpi-trend.down { color: var(--color-danger); }
.kpi-trend.muted { color: var(--color-text-muted); }

.list-card { padding: 0; }
:deep(.el-table) { background: transparent; }
:deep(.clickable-row) { cursor: pointer; }

.status-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; margin-right: 6px; }
.status-dot.success { background: var(--color-success); }
.status-dot.warning { background: var(--color-warning); }
.status-dot.muted   { background: var(--color-text-muted); }
.heartbeat-ok    { color: var(--color-success); font-size: 12px; }
.heartbeat-stale { color: var(--color-warning); font-size: 12px; }
.text-mono { font-family: var(--font-mono); }
.text-sm { font-size: 12px; }
.text-xs { font-size: 11px; }

/* ===== Drawer 详情 ===== */
.detail-content { display: flex; flex-direction: column; gap: var(--space-5); }
.detail-section h3 { font-size: 14px; font-weight: 600; margin: 0 0 var(--space-3); color: var(--color-text-primary); }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-3); }
.section-header h3 { margin: 0; }

.detail-list { display: grid; grid-template-columns: 110px 1fr; gap: 8px 16px; font-size: 13px; margin: 0; }
.detail-list dt { color: var(--color-text-muted); font-weight: 500; }
.detail-list dd { margin: 0; color: var(--color-text-primary); word-break: break-all; }

.cluster-grid { display: grid; grid-template-columns: 1fr; gap: 8px; }
.cluster-card { background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 12px; }
.cluster-label { font-size: 12px; color: var(--color-text-secondary); font-weight: 500; display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.cluster-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 4px; max-height: 180px; overflow-y: auto; }
.cluster-list li { display: flex; align-items: center; gap: 8px; padding: 4px 8px; border-radius: 3px; }
.cluster-list li.empty { color: var(--color-text-muted); font-style: italic; padding: 8px; justify-content: center; }
.cluster-list li:hover:not(.empty) { background: var(--color-accent-soft); }

.hint { color: var(--color-text-muted); font-size: 12px; margin: var(--space-3) 0 0; padding: 8px 12px; background: var(--color-bg-elevated); border-radius: 4px; }

.detail-actions { display: flex; justify-content: flex-end; padding-top: var(--space-3); border-top: 1px solid var(--color-border); }
</style>
