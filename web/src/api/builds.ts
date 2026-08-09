/**
 * 构建 API - 对应后端 /api/builds + /api/projects/{id}/builds + SSE /stream.
 *
 * <p>M5 6 接入: 触发构建 / 查详情 / 列 step / 取消 + EventSource 实时日志.
 */
import { http, sseBaseURL, ApiError } from './client';
import type { PageResponse } from './types';

export type BuildStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'TIMEOUT'
  | 'CANCELED';

export interface Build {
  id: number;
  projectId: number;
  commitSha: string;
  commitMessage?: string;
  triggeredBy: string;
  triggerType: string;
  droneBuildId: string;
  status: BuildStatus;
  imageTag?: string;
  harborImageUrl?: string;
  startedAt?: string;
  finishedAt?: string;
  logPersisted: number;
  createdAt: string;
}

export interface CreateBuildRequest {
  projectId: number;
  envId?: number;            // 选填, V1 demo 自动用项目第一个关联 env
  commitSha: string;
  commitMessage?: string;
  triggeredBy?: string;
}

export interface BuildLog {
  id: number;
  buildRecordId: number;
  stepName: string;
  stepOrder: number;
  logSizeBytes: number;
  startedAt?: string;
  finishedAt?: string;
  createdAt: string;
}

/**
 * SSE 事件 — 跟后端 BuildLogEvent 对应.
 *
 * 客户端用 EventSource.addEventListener('step' | 'build', ...) 区分.
 */
export interface SseStepEvent {
  buildId: number;
  eventType: 'step';
  stepName: string;
  stepOrder: number;
  logContent: string;
  logSizeBytes: number;
  stepStartedAt?: string;
  stepFinishedAt?: string;
}

export interface SseBuildEvent {
  buildId: number;
  eventType: 'build';
  status: BuildStatus;
  imageTag?: string;
  harborImageUrl?: string;
}

export type SseEvent = SseStepEvent | SseBuildEvent;

export const buildsApi = {
  list: (projectId: number, params: { pageNum?: number; pageSize?: number; status?: string } = {}) =>
    http
      .get<PageResponse<Build>>(`/projects/${projectId}/builds`, {
        params: { pageNum: 1, pageSize: 20, ...params },
      })
      .then((r) => r.data),

  get: (id: number) => http.get<Build>(`/builds/${id}`).then((r) => r.data),

  create: (req: CreateBuildRequest) =>
    http.post<Build>('/builds', req).then((r) => r.data),

  cancel: (id: number) => http.post<Build>(`/builds/${id}/cancel`).then((r) => r.data),

  listSteps: (id: number) =>
    http.get<BuildLog[]>(`/builds/${id}/steps`).then((r) => r.data),

  getStepLog: (id: number, stepName: string) =>
    http
      .get<string>(`/builds/${id}/steps/${encodeURIComponent(stepName)}`)
      .then((r) => r.data),

  /**
   * 订阅 build 实时日志 — 返回 EventSource, 调用方负责 addEventListener + close.
   *
   * <p>用法:
   * <pre>{@code
   * const es = buildsApi.subscribeStream(buildId);
   * es.addEventListener('step', (e) => { ... });    // 新 step
   * es.addEventListener('build', (e) => { ... });   // 终态
   * es.onerror = () => es.close();
   * }</pre>
   */
  subscribeStream: (buildId: number): EventSource => {
    return new EventSource(`${sseBaseURL}/api/builds/${buildId}/stream`, {
      withCredentials: false,
    });
  },
};

/** ApiError re-export for callers */
export { ApiError };
