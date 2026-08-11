<script setup lang="ts">
/**
 * DeployDetail.vue - 单次部署详情页 (M9 commit-11).
 *
 * <p>三段式布局:
 * <ul>
 *   <li>头部: 状态 / 镜像 / worker / 时间轴 (started → finished)</li>
 *   <li>左侧: snapshot 列表 (点选看 yaml 预览) + 回滚按钮</li>
 *   <li>右侧: 当前选中的 yaml 预览 (简单模式: 直接看; 高级模式: 跟 live-manifest diff)
 *       — 高级模式本期只读, V1.5 加编辑</li>
 * </ul>
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh, Back, RefreshLeft, Position } from '@element-plus/icons-vue';
import {
  deploymentsApi,
  deployStatusBadge,
  type Deploy,
  type DeploySnapshot,
} from '@/api';

const route = useRoute();
const router = useRouter();

const deploy = ref<Deploy | null>(null);
const snapshots = ref<DeploySnapshot[]>([]);
const selectedSnapshotId = ref<string | null>(null);
const liveManifest = ref<string>('');
const loading = ref(false);
const rollingBack = ref(false);
const advancedMode = ref(false);

const selectedSnapshot = computed(() =>
  snapshots.value.find((s) => s.id === selectedSnapshotId.value) ?? null,
);

const deployId = computed(() => String(route.params.id));

async function fetchDeploy() {
  loading.value = true;
  try {
    deploy.value = await deploymentsApi.get(deployId.value);
  } catch (e) {
    ElMessage.error(`加载部署详情失败: ${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function fetchSnapshots() {
  try {
    snapshots.value = await deploymentsApi.listSnapshots(deployId.value);
    if (deploy.value?.currentSnapshotId) {
      selectedSnapshotId.value = deploy.value.currentSnapshotId;
    } else if (snapshots.value.length > 0) {
      // 默认选最新 (按 id 降序)
      selectedSnapshotId.value = snapshots.value[0].id;
    }
  } catch (e) {
    ElMessage.warning(`加载 snapshot 列表失败: ${(e as Error).message}`);
  }
}

async function fetchLiveManifest() {
  if (!deploy.value || deploy.value.status !== 'SUCCESS') {
    liveManifest.value = '';
    return;
  }
  try {
    liveManifest.value = await deploymentsApi.getLiveManifest(deployId.value);
  } catch (e) {
    liveManifest.value = `# 加载 live-manifest 失败: ${(e as Error).message}`;
  }
}

onMounted(async () => {
  await fetchDeploy();
  await fetchSnapshots();
  if (advancedMode.value) {
    await fetchLiveManifest();
  }
});

function back() {
  if (window.history.length > 1) {
    router.back();
  } else {
    router.push({ name: 'deployments' });
  }
}

function selectSnapshot(s: DeploySnapshot) {
  selectedSnapshotId.value = s.id;
}

async function rollbackTo(s: DeploySnapshot) {
  if (!deploy.value) return;
  try {
    await ElMessageBox.confirm(
      `确认回滚到 snapshot #${s.id}? (会触发一次新 deploy, 镜像用原 snapshot 的 yaml)`,
      '一键回滚',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  rollingBack.value = true;
  try {
    const newDeploy = await deploymentsApi.rollback(
      deployId.value,
      s.id,
      'user',
    );
    ElMessage.success(`已触发回滚部署: #${newDeploy.id} (status=${newDeploy.status})`);
    // 跳转到新部署详情
    router.replace({ name: 'deploy-detail', params: { id: newDeploy.id } });
  } catch (e) {
    ElMessage.error(`回滚失败: ${(e as Error).message}`);
  } finally {
    rollingBack.value = false;
  }
}

async function toggleAdvanced() {
  advancedMode.value = !advancedMode.value;
  if (advancedMode.value && !liveManifest.value) {
    await fetchLiveManifest();
  }
}

function fmtTime(iso: string | null): string {
  if (!iso) return '—';
  return iso.replace('T', ' ').substring(0, 19);
}

function shortId(id: string | null | undefined): string {
  if (!id) return '—';
  return `#${id}`;
}
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="header-left">
        <el-button :icon="Back" link @click="back">返回</el-button>
        <h1 v-if="deploy">
          部署详情
          <el-tag :type="deployStatusBadge(deploy.status).severity" size="default" effect="dark">
            {{ deployStatusBadge(deploy.status).label }}
          </el-tag>
        </h1>
        <h1 v-else>部署详情</h1>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="fetchDeploy" :loading="loading">刷新</el-button>
        <el-button :icon="Position" @click="toggleAdvanced">
          {{ advancedMode ? '简单模式' : '高级模式' }}
        </el-button>
      </div>
    </header>

    <div v-if="deploy" v-loading="loading" class="content">
      <!-- ===== 概览卡片 ===== -->
      <section class="overview-card">
        <div class="overview-grid">
          <div class="overview-item">
            <span class="overview-label">部署 ID</span>
            <code class="text-mono">{{ shortId(deploy.id) }}</code>
          </div>
          <div class="overview-item">
            <span class="overview-label">项目</span>
            <code class="text-mono">proj-{{ deploy.projectId }}</code>
          </div>
          <div class="overview-item">
            <span class="overview-label">环境</span>
            <code class="text-mono">env-{{ deploy.envId }}</code>
          </div>
          <div class="overview-item">
            <span class="overview-label">Namespace</span>
            <code class="text-mono text-sm">{{ deploy.namespace }}</code>
          </div>
          <div class="overview-item">
            <span class="overview-label">镜像</span>
            <code class="text-mono text-sm">{{ deploy.imageTag || '—' }}</code>
          </div>
          <div class="overview-item">
            <span class="overview-label">Worker</span>
            <code class="text-mono text-sm">
              {{ deploy.workerId ? `w-${deploy.workerId}` : '—' }}
            </code>
          </div>
          <div class="overview-item">
            <span class="overview-label">SHA-256</span>
            <code class="text-mono text-sm">{{ deploy.deployYamlSha256?.substring(0, 16) || '—' }}</code>
          </div>
          <div class="overview-item">
            <span class="overview-label">触发人</span>
            <span class="text-sm">{{ deploy.triggeredBy || 'unknown' }}</span>
          </div>
        </div>

        <div class="timeline">
          <div class="timeline-item">
            <span class="timeline-label">创建</span>
            <span class="timeline-value">{{ fmtTime(deploy.createdAt) }}</span>
          </div>
          <div class="timeline-arrow">→</div>
          <div class="timeline-item" :class="{ active: deploy.startedAt }">
            <span class="timeline-label">开始</span>
            <span class="timeline-value">{{ fmtTime(deploy.startedAt) }}</span>
          </div>
          <div class="timeline-arrow">→</div>
          <div class="timeline-item" :class="{ active: deploy.finishedAt }">
            <span class="timeline-label">完成</span>
            <span class="timeline-value">{{ fmtTime(deploy.finishedAt) }}</span>
          </div>
        </div>

        <div v-if="deploy.errorMessage" class="error-box">
          <strong>错误信息:</strong>
          <pre>{{ deploy.errorMessage }}</pre>
        </div>
      </section>

      <!-- ===== 主区: snapshot 列表 + yaml 预览 ===== -->
      <div class="main-grid">
        <!-- 左侧 snapshot 列表 -->
        <section class="card snapshot-card">
          <div class="card-header">
            <h3>Snapshot 列表 <span class="muted">· {{ snapshots.length }} 条</span></h3>
          </div>
          <ul class="snapshot-list">
            <li
              v-for="s in snapshots"
              :key="s.id"
              :class="{ active: s.id === selectedSnapshotId }"
              @click="selectSnapshot(s)"
            >
              <div class="snapshot-meta">
                <code class="text-mono">#{{ s.id }}</code>
                <span class="text-sm muted">{{ fmtTime(s.createdAt) }}</span>
              </div>
              <div class="snapshot-detail">
                <code class="text-xs text-mono">{{ s.deployYamlSha256?.substring(0, 12) }}</code>
                <span class="text-xs muted">by {{ s.createdBy || 'unknown' }}</span>
              </div>
              <el-button
                v-if="s.id !== deploy.currentSnapshotId"
                size="small"
                type="primary"
                :icon="RefreshLeft"
                :loading="rollingBack"
                @click.stop="rollbackTo(s)"
              >
                回滚到此
              </el-button>
              <el-tag v-else type="success" size="small" effect="plain">当前</el-tag>
            </li>
            <li v-if="snapshots.length === 0" class="empty">还没有 snapshot</li>
          </ul>
        </section>

        <!-- 右侧 yaml 预览 -->
        <section class="card yaml-card">
          <div class="card-header">
            <h3>
              {{ advancedMode ? '高级模式: 提交的 yaml + k8s live manifest' : 'Snapshot YAML 预览' }}
            </h3>
            <span v-if="selectedSnapshot" class="muted text-sm">
              {{ advancedMode ? '提交 / 现网' : `Snapshot #${selectedSnapshot.id}` }}
            </span>
          </div>

          <div v-if="selectedSnapshot" class="yaml-content">
            <pre v-if="!advancedMode">{{ selectedSnapshot.deployYaml }}</pre>
            <div v-else class="diff-grid">
              <div class="diff-pane">
                <h4>提交 YAML <span class="muted">· snapshot #{{ selectedSnapshot.id }}</span></h4>
                <pre>{{ selectedSnapshot.deployYaml }}</pre>
              </div>
              <div class="diff-pane">
                <h4>Live Manifest <span class="muted">· k8s 实际生效</span></h4>
                <pre>{{ liveManifest || '# 加载中或无 (非 SUCCESS 状态?) ...' }}</pre>
              </div>
            </div>
          </div>
          <div v-else class="yaml-empty">
            选中左侧 snapshot 查看 yaml
          </div>
        </section>
      </div>
    </div>

    <div v-else v-loading="loading" class="empty-page">加载中...</div>
  </div>
</template>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; display: flex; flex-direction: column; gap: var(--space-5); }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-4); flex-wrap: wrap; }
.header-left { display: flex; align-items: center; gap: 12px; }
.page-header h1 { font-size: 22px; font-weight: 700; margin: 0; display: flex; align-items: center; gap: 12px; }
.header-actions { display: flex; gap: 8px; }
.muted { color: var(--color-text-muted); font-size: 12px; }
.text-mono { font-family: var(--font-mono); }
.text-sm { font-size: 12px; }
.text-xs { font-size: 11px; }

.content { display: flex; flex-direction: column; gap: var(--space-5); }

/* ===== 概览 ===== */
.overview-card { background: var(--color-bg-surface); border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: var(--space-5); }
.overview-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px 24px; margin-bottom: 16px; }
.overview-item { display: flex; flex-direction: column; gap: 4px; }
.overview-label { font-size: 11px; color: var(--color-text-secondary); text-transform: uppercase; letter-spacing: 0.05em; font-weight: 500; }

.timeline { display: flex; align-items: center; gap: 12px; padding-top: 12px; border-top: 1px solid var(--color-border); }
.timeline-item { display: flex; flex-direction: column; gap: 2px; }
.timeline-item.active .timeline-value { color: var(--color-success); }
.timeline-label { font-size: 11px; color: var(--color-text-muted); }
.timeline-value { font-family: var(--font-mono); font-size: 13px; }
.timeline-arrow { color: var(--color-text-muted); font-size: 16px; }

.error-box { margin-top: 12px; padding: 12px; background: rgba(245, 34, 45, 0.08); border-left: 3px solid var(--color-danger); border-radius: 4px; }
.error-box pre { margin: 4px 0 0; white-space: pre-wrap; word-break: break-word; font-family: var(--font-mono); font-size: 12px; color: var(--color-danger); }

/* ===== 主区 ===== */
.main-grid { display: grid; grid-template-columns: 360px 1fr; gap: var(--space-4); }
@media (max-width: 1024px) { .main-grid { grid-template-columns: 1fr; } }

.card { background: var(--color-bg-surface); border: 1px solid var(--color-border); border-radius: var(--radius-md); display: flex; flex-direction: column; }
.card-header { padding: var(--space-4); border-bottom: 1px solid var(--color-border); display: flex; justify-content: space-between; align-items: center; }
.card-header h3 { font-size: 14px; font-weight: 600; margin: 0; }

/* snapshot 列表 */
.snapshot-list { list-style: none; padding: 0; margin: 0; max-height: 600px; overflow-y: auto; }
.snapshot-list li { padding: 12px var(--space-4); border-bottom: 1px solid var(--color-border); display: flex; flex-direction: column; gap: 6px; cursor: pointer; }
.snapshot-list li:hover:not(.empty) { background: var(--color-accent-soft); }
.snapshot-list li.active { background: var(--color-accent-soft); border-left: 3px solid var(--color-accent); padding-left: calc(var(--space-4) - 3px); }
.snapshot-list li.empty { color: var(--color-text-muted); font-style: italic; padding: 24px; text-align: center; cursor: default; }
.snapshot-meta { display: flex; align-items: center; gap: 8px; }
.snapshot-detail { display: flex; align-items: center; gap: 8px; }
.snapshot-list li > :last-child { margin-top: 6px; align-self: flex-start; }

/* yaml 预览 */
.yaml-content { padding: var(--space-4); }
.yaml-content pre { background: var(--color-bg-elevated); padding: 12px; border-radius: 4px; font-family: var(--font-mono); font-size: 12px; line-height: 1.5; overflow-x: auto; max-height: 600px; overflow-y: auto; margin: 0; white-space: pre-wrap; word-break: break-word; }
.yaml-empty { padding: 60px 24px; text-align: center; color: var(--color-text-muted); font-style: italic; }

.diff-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
@media (max-width: 1200px) { .diff-grid { grid-template-columns: 1fr; } }
.diff-pane h4 { font-size: 12px; font-weight: 600; margin: 0 0 8px; color: var(--color-text-secondary); text-transform: uppercase; letter-spacing: 0.05em; }
.diff-pane pre { max-height: 500px; }

.empty-page { padding: 80px 24px; text-align: center; color: var(--color-text-muted); }
</style>
