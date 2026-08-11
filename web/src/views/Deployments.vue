<script setup lang="ts">
/**
 * Deployments.vue - 部署列表页 (M9 commit-11).
 *
 * <p>展示按 project + env 过滤的部署记录, 每条可触发 / 查看详情 / 回滚 / 取消.
 *
 * <p>设计:
 * <ul>
 *   <li>顶部 4 stat: 今日部署 / 成功率 / 进行中 / 总数</li>
 *   <li>过滤: projectId / envId / status / 触发人</li>
 *   <li>列表: ID / project / env / image / status / 触发人 / 时间 / 操作</li>
 *   <li>行内操作: 查看详情 (router push /deploys/{id}) / 取消 (PENDING|RUNNING) / 触发新部署</li>
 *   <li>触发部署 modal: env 选 + buildRecordId 选 + replicas (选填)</li>
 * </ul>
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh, Search, VideoPlay, View, CircleClose } from '@element-plus/icons-vue';
import {
  deploymentsApi,
  deployStatusBadge,
  type Deploy,
  type DeployStatus,
  type CreateDeployRequest,
} from '@/api';
import { envsApi, projectsApi, buildsApi, type Env, type Project, type Build } from '@/api';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const deploys = ref<Deploy[]>([]);
const envs = ref<Env[]>([]);
const projects = ref<Project[]>([]);
const builds = ref<Build[]>([]);

// 过滤
const filterProjectId = ref<string>(String(route.params.id ?? ''));
const filterEnvId = ref<string>('');
const filterStatus = ref<DeployStatus | ''>('');
const searchQuery = ref('');

const showCreate = ref(false);
const createForm = ref<{
  envId: string | number;
  buildRecordId: string | number | '';
  replicas: number | '';
  triggeredBy: string;
}>({
  envId: '',
  buildRecordId: '',
  replicas: '',
  triggeredBy: 'unknown',
});
const creating = ref(false);

const filtered = computed(() => {
  let list = deploys.value;
  if (filterStatus.value) {
    list = list.filter((d) => d.status === filterStatus.value);
  }
  const q = searchQuery.value.trim().toLowerCase();
  if (q) {
    list = list.filter(
      (d) =>
        String(d.id).includes(q) ||
        String(d.imageTag ?? '').toLowerCase().includes(q) ||
        String(d.triggeredBy ?? '').toLowerCase().includes(q),
    );
  }
  return list;
});

const stats = computed(() => {
  const total = deploys.value.length;
  const success = deploys.value.filter((d) => d.status === 'SUCCESS').length;
  const running = deploys.value.filter((d) => d.status === 'RUNNING' || d.status === 'PENDING').length;
  const today = deploys.value.filter((d) => {
    if (!d.createdAt) return false;
    const dDate = new Date(d.createdAt);
    const now = new Date();
    return dDate.toDateString() === now.toDateString();
  }).length;
  return {
    total,
    success,
    running,
    today,
    successRate: total ? Math.round((success / total) * 100) : 0,
  };
});

async function fetchList() {
  loading.value = true;
  try {
    const params: { projectId?: string | number; envId?: string | number } = {};
    if (filterProjectId.value) params.projectId = filterProjectId.value;
    if (filterEnvId.value) params.envId = filterEnvId.value;
    deploys.value = await deploymentsApi.list(params);
  } catch (e) {
    ElMessage.error(`加载部署列表失败: ${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function fetchEnvs() {
  try {
    const page = await envsApi.list({ page: 1, size: 100 });
    envs.value = page.records;
  } catch (e) {
    ElMessage.warning(`加载 env 列表失败: ${(e as Error).message}`);
  }
}

async function fetchProjects() {
  try {
    const page = await projectsApi.list({ page: 1, size: 100 });
    projects.value = page.records;
  } catch {
    // 列表失败不致命
  }
}

async function fetchBuilds() {
  if (!filterProjectId.value) {
    builds.value = [];
    return;
  }
  try {
    const page = await buildsApi.list(filterProjectId.value, { pageNum: 1, pageSize: 20 });
    builds.value = page.records;
  } catch {
    builds.value = [];
  }
}

onMounted(async () => {
  await Promise.all([fetchEnvs(), fetchProjects()]);
  if (filterProjectId.value) {
    await fetchBuilds();
  }
  await fetchList();
});

async function openCreate() {
  if (!filterProjectId.value) {
    ElMessage.warning('请先选择项目');
    return;
  }
  showCreate.value = true;
  await fetchBuilds();
  if (builds.value.length > 0 && !createForm.value.buildRecordId) {
    createForm.value.buildRecordId = builds.value[0].id;
  }
}

async function submitCreate() {
  if (!createForm.value.envId) {
    ElMessage.warning('请选择环境');
    return;
  }
  if (!createForm.value.buildRecordId && builds.value.length > 0) {
    ElMessage.warning('请选择 build 记录');
    return;
  }
  creating.value = true;
  try {
    const req: CreateDeployRequest = {
      envId: createForm.value.envId,
      buildRecordId: createForm.value.buildRecordId || undefined,
      triggeredBy: createForm.value.triggeredBy || 'unknown',
    };
    if (createForm.value.replicas !== '') {
      req.replicas = Number(createForm.value.replicas);
    }
    const d = await deploymentsApi.create(filterProjectId.value, req);
    ElMessage.success(`部署已触发: #${d.id} (status=${d.status})`);
    showCreate.value = false;
    await fetchList();
  } catch (e) {
    ElMessage.error(`触发部署失败: ${(e as Error).message}`);
  } finally {
    creating.value = false;
  }
}

async function cancelDeploy(d: Deploy) {
  try {
    await ElMessageBox.confirm(
      `确认取消部署 #${d.id}? (仅 PENDING/RUNNING 状态可取消)`,
      '取消部署',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    const updated = await deploymentsApi.cancel(d.id);
    ElMessage.success(`已取消: 状态 ${updated.status}`);
    await fetchList();
  } catch (e) {
    ElMessage.error(`取消失败: ${(e as Error).message}`);
  }
}

function openDetail(d: Deploy) {
  router.push({ name: 'deploy-detail', params: { id: d.id } });
}

async function onFilterChange() {
  await fetchBuilds();
  await fetchList();
}

function fmtTime(iso: string | null): string {
  if (!iso) return '—';
  return iso.replace('T', ' ').substring(0, 19);
}
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>部署记录</h1>
        <p class="muted">所有部署任务 · 触发 / 状态 / 详情 / 回滚</p>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchQuery"
          placeholder="搜索 ID / 镜像 / 触发人..."
          clearable
          size="default"
          style="width: 240px;"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select
          v-model="filterProjectId"
          placeholder="项目"
          size="default"
          style="width: 180px;"
          @change="onFilterChange"
        >
          <el-option
            v-for="p in projects"
            :key="p.id"
            :label="p.displayName"
            :value="p.id"
          />
        </el-select>
        <el-select
          v-model="filterEnvId"
          placeholder="环境"
          size="default"
          clearable
          style="width: 140px;"
          @change="fetchList"
        >
          <el-option
            v-for="e in envs"
            :key="e.id"
            :label="e.name"
            :value="e.id"
          />
        </el-select>
        <el-select
          v-model="filterStatus"
          placeholder="状态"
          size="default"
          clearable
          style="width: 120px;"
          @change="fetchList"
        >
          <el-option label="排队中" value="PENDING" />
          <el-option label="执行中" value="RUNNING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="超时" value="TIMEOUT" />
          <el-option label="已取消" value="CANCELED" />
        </el-select>
        <el-button :icon="VideoPlay" type="primary" @click="openCreate">触发部署</el-button>
        <el-button :icon="Refresh" @click="fetchList" :loading="loading">刷新</el-button>
      </div>
    </header>

    <!-- ===== KPI 卡片 ===== -->
    <section class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-label">总部署数</div>
        <div class="kpi-value">{{ stats.total }}</div>
        <div class="kpi-trend muted">全项目 / 全环境</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">今日部署</div>
        <div class="kpi-value">{{ stats.today }}</div>
        <div class="kpi-trend muted">过去 24h</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">成功率</div>
        <div class="kpi-value">{{ stats.successRate }}<span class="unit">%</span></div>
        <div class="kpi-trend" :class="stats.successRate >= 80 ? 'up' : 'down'">
          {{ stats.success }}/{{ stats.total }} 成功
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">进行中</div>
        <div class="kpi-value">
          <span class="status-dot warning" v-if="stats.running > 0"></span>
          {{ stats.running }}
        </div>
        <div class="kpi-trend muted">PENDING + RUNNING</div>
      </div>
    </section>

    <!-- ===== 列表表格 ===== -->
    <section class="card list-card">
      <el-table
        :data="filtered"
        v-loading="loading"
        stripe
        :empty-text="deploys.length === 0 ? '还没有部署记录' : '无匹配结果'"
        @row-click="openDetail"
        row-class-name="clickable-row"
      >
        <el-table-column prop="id" label="ID" width="80">
          <template #default="{ row }">
            <code class="text-mono">#{{ row.id }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="projectId" label="项目" width="120">
          <template #default="{ row }">
            <code class="text-mono">proj-{{ row.projectId }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="envId" label="环境" width="100">
          <template #default="{ row }">
            <code class="text-mono">env-{{ row.envId }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="namespace" label="namespace" width="160">
          <template #default="{ row }">
            <code class="text-mono text-sm">{{ row.namespace }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="imageTag" label="镜像" min-width="200">
          <template #default="{ row }">
            <code v-if="row.imageTag" class="text-mono text-sm">{{ row.imageTag }}</code>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="deployStatusBadge(row.status).severity" size="small" effect="dark">
              {{ deployStatusBadge(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="workerId" label="Worker" width="100">
          <template #default="{ row }">
            <code v-if="row.workerId" class="text-mono text-sm">w-{{ row.workerId }}</code>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="triggeredBy" label="触发人" width="120">
          <template #default="{ row }">
            <span class="text-sm">{{ row.triggeredBy || 'unknown' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="muted text-sm">{{ fmtTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click.stop="openDetail(row)">
              <el-icon><View /></el-icon>
              详情
            </el-button>
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'RUNNING'"
              size="small"
              link
              type="danger"
              @click.stop="cancelDeploy(row)"
            >
              <el-icon><CircleClose /></el-icon>
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ===== 触发部署 modal ===== -->
    <el-dialog v-model="showCreate" title="触发部署" width="520px">
      <el-form label-width="100px">
        <el-form-item label="环境" required>
          <el-select v-model="createForm.envId" placeholder="选择环境" style="width: 100%;">
            <el-option
              v-for="e in envs"
              :key="e.id"
              :label="`${e.name} (env-${e.id})`"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Build 记录">
          <el-select
            v-model="createForm.buildRecordId"
            placeholder="选择已有 build (默认最新)"
            style="width: 100%;"
            clearable
          >
            <el-option
              v-for="b in builds"
              :key="b.id"
              :label="`#${b.id} - ${b.commitSha?.substring(0, 7) ?? '—'} (${b.status})`"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="副本数">
          <el-input-number
            v-model="createForm.replicas"
            :min="0"
            :max="100"
            placeholder="留空走 template 默认"
            controls-position="right"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="触发人">
          <el-input v-model="createForm.triggeredBy" placeholder="可选, 默认 unknown" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">触发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; display: flex; flex-direction: column; gap: var(--space-5); }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--space-4); flex-wrap: wrap; }
.page-header h1 { font-size: 24px; font-weight: 700; margin: 0; }
.muted { color: var(--color-text-muted); font-size: 12px; margin-top: 4px; }
.header-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

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
.status-dot.warning { background: var(--color-warning); }
.text-mono { font-family: var(--font-mono); }
.text-sm { font-size: 12px; }
</style>
