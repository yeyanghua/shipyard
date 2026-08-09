/**
 * Build Pinia Store — 构建详情 + 实时日志状态.
 *
 * <p>职责:
 * <ul>
 *   <li>缓存单个 build 详情</li>
 *   <li>维护 step 列表 (log content 按需 fetch)</li>
 *   <li>管理 EventSource 订阅生命周期</li>
 *   <li>暴露状态机 (PENDING / RUNNING / 终态) 供 UI 颜色 + 文案</li>
 * </ul>
 */
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { buildsApi, type Build, type BuildLog, type SseEvent, type BuildStatus } from '@/api';

export const useBuildStore = defineStore('build', () => {
  const current = ref<Build | null>(null);
  const steps = ref<BuildLog[]>([]);
  const stepLogs = ref<Record<string, string>>({});  // stepName → logContent
  const status = ref<BuildStatus | null>(null);
  const sseConnected = ref(false);
  const sseError = ref<string | null>(null);

  let eventSource: EventSource | null = null;

  /** 当前 build 终态?  终态: SUCCESS / FAILED / TIMEOUT / CANCELED */
  const isTerminal = computed(() => {
    if (!status.value) return false;
    return ['SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELED'].includes(status.value);
  });

  /** UI 状态颜色 */
  const statusColor = computed(() => {
    switch (status.value) {
      case 'PENDING': return '#9ca3af';  // 灰
      case 'RUNNING': return '#3b82f6';  // 蓝
      case 'SUCCESS': return '#10b981';  // 绿
      case 'FAILED': return '#ef4444';   // 红
      case 'TIMEOUT': return '#f97316';  // 橙
      case 'CANCELED': return '#6b7280'; // 深灰
      default: return '#9ca3af';
    }
  });

  /** 加载 build 详情 (含 step 列表) */
  async function load(buildId: number) {
    current.value = await buildsApi.get(buildId);
    status.value = current.value.status;
    steps.value = await buildsApi.listSteps(buildId);
    // 预拉每个 step 的 log content (mock build 跑得快,通常都已经落库)
    for (const s of steps.value) {
      try {
        stepLogs.value[s.stepName] = await buildsApi.getStepLog(buildId, s.stepName);
      } catch {
        // step 可能还没落 (build 还在跑)
        stepLogs.value[s.stepName] = '';
      }
    }
  }

  /** 订阅 SSE 实时日志 — 自动管理 EventSource 生命周期 */
  function subscribeStream(buildId: number) {
    // 清掉旧的
    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }
    sseError.value = null;

    eventSource = buildsApi.subscribeStream(buildId);
    sseConnected.value = true;

    eventSource.addEventListener('open', () => {
      sseConnected.value = true;
      sseError.value = null;
    });

    eventSource.addEventListener('step', (e: MessageEvent) => {
      try {
        const event: SseEvent = JSON.parse(e.data);
        if (event.eventType !== 'step') return;
        // 加进 steps (去重: buildLogEntity 已建过的不重复)
        const existing = steps.value.find((s) => s.stepName === event.stepName);
        if (existing) {
          existing.logSizeBytes = event.logSizeBytes;
          existing.finishedAt = event.stepFinishedAt;
        } else {
          steps.value.push({
            id: 0,  // 服务端不返
            buildRecordId: buildId,
            stepName: event.stepName,
            stepOrder: event.stepOrder,
            logSizeBytes: event.logSizeBytes,
            startedAt: event.stepStartedAt,
            finishedAt: event.stepFinishedAt,
            createdAt: new Date().toISOString(),
          });
        }
        stepLogs.value[event.stepName] = event.logContent;
        // RUNNING 状态
        if (status.value === 'PENDING') {
          status.value = 'RUNNING';
        }
      } catch (err) {
        console.error('SSE step event parse failed', err);
      }
    });

    eventSource.addEventListener('build', (e: MessageEvent) => {
      try {
        const event: SseEvent = JSON.parse(e.data);
        if (event.eventType !== 'build') return;
        status.value = event.status;
        if (current.value) {
          current.value.status = event.status;
          if (event.imageTag) current.value.imageTag = event.imageTag;
          if (event.harborImageUrl) current.value.harborImageUrl = event.harborImageUrl;
        }
        // 关掉 SSE, 服务端已主动 complete
        if (eventSource) {
          eventSource.close();
          eventSource = null;
        }
        sseConnected.value = false;
      } catch (err) {
        console.error('SSE build event parse failed', err);
      }
    });

    eventSource.onerror = () => {
      sseConnected.value = false;
      sseError.value = 'SSE 连接断开';
      // 不断重连 — 服务端 close 之后 EventSource 会自动重连, 屏蔽掉
      if (eventSource) {
        eventSource.close();
        eventSource = null;
      }
    };
  }

  /** 取消订阅 */
  function unsubscribeStream() {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }
    sseConnected.value = false;
  }

  /** 取消构建 */
  async function cancel(buildId: number) {
    const updated = await buildsApi.cancel(buildId);
    if (current.value) {
      current.value = updated;
    }
    status.value = updated.status;
  }

  /** 触发新构建 */
  async function trigger(req: { projectId: number; commitSha: string; envId?: number }) {
    return await buildsApi.create({
      projectId: req.projectId,
      commitSha: req.commitSha,
      envId: req.envId,
      triggeredBy: 'web-user',
    });
  }

  function reset() {
    current.value = null;
    steps.value = [];
    stepLogs.value = {};
    status.value = null;
    sseError.value = null;
    unsubscribeStream();
  }

  return {
    current,
    steps,
    stepLogs,
    status,
    sseConnected,
    sseError,
    isTerminal,
    statusColor,
    load,
    subscribeStream,
    unsubscribeStream,
    cancel,
    trigger,
    reset,
  };
});
