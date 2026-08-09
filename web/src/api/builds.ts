/**
 * 构建 API - 对应后端 /api/builds + /api/projects/{id}/builds + SSE /stream.
 *
 * <p>M5 6 接入: 触发构建 / 查详�?/ �?step / 取消 + EventSource 实时日志.
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
  id: string;
  projectId: string;
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
  projectId: string;
  envId?: string;            // 选填, V1 demo 自动用项目第一个关联 env
  commitSha: string;
  commitMessage?: string;
  triggeredBy?: string;
}

export interface BuildLog {
  id: string;
  buildRecordId: string;
  stepName: string;
  stepOrder: number;
  logSizeBytes: number;
  startedAt?: string;
  finishedAt?: string;
  createdAt: string;
}

/**
 * SSE 事件 �?跟后�?BuildLogEvent 对应.
 *
 * 客户端用 EventSource.addEventListener('step' | 'build', ...) 区分.
 */
export interface SseStepEvent {
  buildId: string;
  eventType: 'step';
  stepName: string;
  stepOrder: number;
  logContent: string;
  logSizeBytes: number;
  stepStartedAt?: string;
  stepFinishedAt?: string;
}

export interface SseBuildEvent {
  buildId: string;
  eventType: 'build';
  status: BuildStatus;
  imageTag?: string;
  harborImageUrl?: string;
}

export type SseEvent = SseStepEvent | SseBuildEvent;

export const buildsApi = {
  list: (projectId: string, params: { pageNum?: number; pageSize?: number; status?: string } = {}) =>
    http
      .get<PageResponse<Build>>(`/projects/${projectId}/builds`, {
        params: { pageNum: 1, pageSize: 20, ...params },
      }),

  get: (id: string) => http.get<Build>(`/builds/${id}`),

  create: (req: CreateBuildRequest) =>
    http.post<Build>('/builds', req),

  cancel: (id: string) => http.post<Build>(`/builds/${id}/cancel`),

  listSteps: (id: string) =>
    http.get<BuildLog[]>(`/builds/${id}/steps`),

  getStepLog: (id: string, stepName: string) =>
    http
      .get<string>(`/builds/${id}/steps/${encodeURIComponent(stepName)}`),

  /**
   * 订阅 build 实时日志 �?返回 EventSource, 调用方负�?addEventListener + close.
   *
   * <p>用法:
   * <pre>{@code
   * const es = buildsApi.subscribeStream(buildId);
   * es.addEventListener('step', (e) => { ... });    // �?step
   * es.addEventListener('build', (e) => { ... });   // 终�?   * es.onerror = () => es.close();
   * }</pre>
   */
  subscribeStream: (buildId: string): EventSource => {
    return new EventSource(`${sseBaseURL}/api/builds/${buildId}/stream`, {
      withCredentials: false,
    });
  },
};

/** ApiError re-export for callers */
export { ApiError };
