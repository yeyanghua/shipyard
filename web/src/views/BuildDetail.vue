<script setup lang="ts">
/**
 * 构建详情页 — 左侧元信息 + step 列表, 右侧实时日志 (SSE).
 *
 * <p>M5 6 完整版:
 * <ul>
 *   <li>左: build 元信息 (commit / trigger / 耗时 / 状态机)</li>
 *   <li>左下: 3 个 step 卡片 (compile / test / docker-push) + 状态色 + 耗时</li>
 *   <li>右: 选中 step 的 log, 大字等宽, 自动滚到底</li>
 *   <li>PENDING / RUNNING 时显示 [取消构建] 按钮 + [实时连接中…] 状态</li>
 *   <li>SUCCESS 显示 [镜像 tag] + Harbor URL</li>
 *   <li>FAILED 顶部红条 + 后续接 AI 诊断 (M12)</li>
 * </ul>
 */
import { onMounted, onUnmounted, ref, computed, nextTick, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useBuildStore } from '@/stores/build';
import { ApiError } from '@/api';

const route = useRoute();
const router = useRouter();
const store = useBuildStore();

const buildId = computed(() => String(route.params.id));
const selectedStep = ref<string | null>(null);
const logEl = ref<HTMLElement | null>(null);
const loading = ref(true);
const error = ref('');

const sortedSteps = computed(() =>
  [...store.steps].sort((a, b) => a.stepOrder - b.stepOrder)
);

const selectedLog = computed(() => {
  if (!selectedStep.value) return '';
  return store.stepLogs[selectedStep.value] ?? '';
});

const duration = computed(() => {
  const c = store.current;
  if (!c?.startedAt) return null;
  const start = new Date(c.startedAt).getTime();
  const end = c.finishedAt ? new Date(c.finishedAt).getTime() : Date.now();
  const sec = Math.floor((end - start) / 1000);
  if (sec < 60) return `${sec}s`;
  return `${Math.floor(sec / 60)}m ${sec % 60}s`;
});

const statusLabel = computed(() => {
  switch (store.status) {
    case 'PENDING': return '排队中';
    case 'RUNNING': return '构建中';
    case 'SUCCESS': return '成功';
    case 'FAILED': return '失败';
    case 'TIMEOUT': return '超时';
    case 'CANCELED': return '已取消';
    default: return store.status ?? '未知';
  }
});

async function init() {
  loading.value = true;
  error.value = '';
  try {
    await store.load(buildId.value);
    if (sortedSteps.value.length > 0) {
      selectedStep.value = sortedSteps.value[sortedSteps.value.length - 1].stepName;
    }
    // 订阅 SSE — 实时推后续 step + 终态
    if (!store.isTerminal) {
      store.subscribeStream(buildId.value);
    }
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}

async function doCancel() {
  if (!confirm('确认取消该构建?')) return;
  try {
    await store.cancel(buildId.value);
  } catch (e) {
    alert(`取消失败: ${(e as ApiError).message}`);
  }
}

// 自动滚到底 — 选中的 step log 变化时
watch(selectedLog, async () => {
  await nextTick();
  if (logEl.value) {
    logEl.value.scrollTop = logEl.value.scrollHeight;
  }
});

// 终态变化时停 SSE (兜底, SSE 自身也会关)
watch(() => store.isTerminal, (terminal) => {
  if (terminal) {
    store.unsubscribeStream();
  }
});

onMounted(init);
onUnmounted(() => {
  store.unsubscribeStream();
});
</script>

<template>
  <div class="page" v-if="!loading && store.current">
    <div class="header">
      <button class="back" @click="router.push(`/projects/${store.current.projectId}`)">← 返回项目</button>
      <h1 class="title">
        构建 #{{ store.current.id }}
        <span class="status" :style="{ background: store.statusColor }">{{ statusLabel }}</span>
      </h1>
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <div class="layout">
      <!-- 左侧: 元信息 + step 列表 -->
      <aside class="left">
        <section class="card">
          <h2>元信息</h2>
          <dl>
            <dt>Project</dt>
            <dd>
              <RouterLink :to="`/projects/${store.current.projectId}`">
                #{{ store.current.projectId }}
              </RouterLink>
            </dd>
            <dt>Commit</dt>
            <dd><code>{{ store.current.commitSha }}</code></dd>
            <dt v-if="store.current.commitMessage">Message</dt>
            <dd v-if="store.current.commitMessage" class="msg">{{ store.current.commitMessage }}</dd>
            <dt>Trigger</dt>
            <dd>
              <span class="tag">{{ store.current.triggerType }}</span>
              <span class="muted">{{ store.current.triggeredBy }}</span>
            </dd>
            <dt>Drone</dt>
            <dd><code class="drone">{{ store.current.droneBuildId }}</code></dd>
            <dt>创建</dt>
            <dd>{{ store.current.createdAt }}</dd>
            <dt v-if="store.current.startedAt">开始</dt>
            <dd v-if="store.current.startedAt">{{ store.current.startedAt }}</dd>
            <dt v-if="duration">耗时</dt>
            <dd v-if="duration" class="duration">{{ duration }}</dd>
          </dl>

          <div v-if="store.status === 'SUCCESS'" class="result success">
            <div class="label">镜像</div>
            <code class="image-tag">{{ store.current.imageTag }}</code>
            <div class="label">Harbor URL</div>
            <code class="harbor">{{ store.current.harborImageUrl }}</code>
          </div>

          <div v-if="!store.isTerminal" class="actions">
            <button class="cancel" @click="doCancel">⏹ 取消构建</button>
            <span v-if="store.sseConnected" class="sse-status connected">● 实时连接中</span>
            <span v-else class="sse-status disconnected">○ 未连接</span>
          </div>
          <div v-if="store.sseError" class="sse-error">{{ store.sseError }}</div>
        </section>

        <section class="card">
          <h2>Steps <span class="count">{{ sortedSteps.length }}</span></h2>
          <div v-if="sortedSteps.length === 0" class="empty">暂无 step</div>
          <ul v-else class="step-list">
            <li
              v-for="s in sortedSteps"
              :key="s.stepName"
              :class="{ active: selectedStep === s.stepName }"
              @click="selectedStep = s.stepName"
            >
              <span class="order">#{{ s.stepOrder }}</span>
              <span class="name">{{ s.stepName }}</span>
              <span class="size">{{ Math.round(s.logSizeBytes / 1024 * 10) / 10 }} KB</span>
            </li>
          </ul>
        </section>
      </aside>

      <!-- 右侧: log 区 -->
      <main class="right">
        <div class="log-toolbar">
          <span v-if="selectedStep" class="log-title">
            📋 {{ selectedStep }}.log
          </span>
          <span v-else class="log-title muted">选择左侧 step 查看日志</span>
          <span v-if="!store.isTerminal && store.sseConnected" class="live">⚡ 实时</span>
        </div>
        <pre v-if="selectedStep" class="log" ref="logEl">{{ selectedLog }}</pre>
        <div v-else class="log-empty">从左侧选一个 step 看日志</div>
      </main>
    </div>
  </div>
  <div v-else-if="loading" class="loading">加载中...</div>
  <div v-else-if="error" class="loading error">{{ error }}</div>
</template>

<style scoped>
.page { max-width: 1280px; }
.header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.back { background: none; border: 0; cursor: pointer; color: var(--primary); font-size: 14px; }
.title { font-size: 22px; margin: 0; display: flex; align-items: center; gap: 12px; }
.status { font-size: 12px; padding: 4px 10px; border-radius: 4px; color: #fff; font-weight: 500; }

.layout { display: grid; grid-template-columns: 380px 1fr; gap: 16px; }
@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
}

.left { display: flex; flex-direction: column; gap: 16px; }
.right { display: flex; flex-direction: column; }

.card { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 16px; }
.card h2 { margin: 0 0 12px; font-size: 14px; display: flex; align-items: center; gap: 8px; }
.card h2 .count {
  background: #e0e7ff; color: #4338ca; padding: 2px 6px; border-radius: 4px; font-size: 12px; font-weight: 500;
}

dl { display: grid; grid-template-columns: 80px 1fr; gap: 6px 12px; margin: 0; font-size: 13px; }
dt { color: var(--text-muted); font-weight: 500; }
dd { margin: 0; word-break: break-all; }
.msg { color: var(--text); font-style: italic; }
.duration { color: var(--primary); font-weight: 500; }
.drone { font-size: 11px; color: var(--text-soft); }
.tag { display: inline-block; padding: 2px 6px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 11px; margin-right: 6px; }
.muted { color: var(--text-soft); font-size: 12px; }

.result { background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 4px; padding: 10px; margin-top: 12px; }
.result .label { font-size: 11px; color: #047857; text-transform: uppercase; margin-top: 4px; }
.result .image-tag { color: #047857; font-weight: 600; }
.result .harbor { font-size: 11px; word-break: break-all; }

.actions { display: flex; align-items: center; gap: 12px; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border); }
.cancel { background: #fee2e2; color: #dc2626; border: 1px solid #fecaca; border-radius: 4px; padding: 6px 12px; cursor: pointer; font-size: 13px; }
.cancel:hover { background: #fecaca; }
.sse-status { font-size: 12px; }
.sse-status.connected { color: #10b981; }
.sse-status.disconnected { color: var(--text-soft); }
.sse-error { color: #dc2626; font-size: 12px; margin-top: 4px; }

.step-list { list-style: none; padding: 0; margin: 0; }
.step-list li {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border: 1px solid var(--border); border-radius: 4px;
  margin-bottom: 4px; background: #fafafa; cursor: pointer; font-size: 13px;
}
.step-list li:hover { background: var(--primary-soft); }
.step-list li.active { background: var(--primary-soft); border-color: var(--primary); }
.step-list .order { color: var(--text-soft); font-size: 12px; }
.step-list .name { flex: 1; font-weight: 500; }
.step-list .size { color: var(--text-soft); font-size: 12px; }

.log-toolbar { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; background: var(--card); border: 1px solid var(--border); border-radius: 8px 8px 0 0; border-bottom: 0; }
.log-title { font-size: 13px; font-weight: 500; }
.log-title.muted { color: var(--text-soft); font-weight: 400; }
.live { color: #10b981; font-size: 12px; font-weight: 500; }

.log {
  background: #0f172a; color: #e2e8f0;
  padding: 16px; margin: 0;
  font-family: 'Consolas', 'Monaco', monospace; font-size: 12px; line-height: 1.6;
  white-space: pre-wrap; word-break: break-all;
  height: 600px; overflow-y: auto;
  border: 1px solid var(--border); border-radius: 0 0 8px 8px;
}
.log-empty { padding: 60px; text-align: center; color: var(--text-soft); background: var(--card); border: 1px solid var(--border); border-radius: 0 0 8px 8px; }

.error { color: #dc2626; background: #fee2e2; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; }
.loading { padding: 24px; text-align: center; color: var(--text-soft); }
</style>
