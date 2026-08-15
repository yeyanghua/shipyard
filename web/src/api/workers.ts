/**
 * Worker API - 对应后端 /api/workers + /api/envs/{envId}/workers.
 *
 * <p>M9.5 redesign 后的端点 (11 个):
 * <ul>
 *   <li>UI 调 (CRUD):</li>
 *   <ul>
 *     <li>POST   /api/envs/{envId}/workers              创建 worker (预登记), 返明文 token (一次性)</li>
 *     <li>GET    /api/workers                           列表 (分页, envId 可选过滤)</li>
 *     <li>GET    /api/workers/{id}                      详情</li>
 *     <li>PUT    /api/workers/{id}                      更新 (V1 只能改 description)</li>
 *     <li>DELETE /api/workers/{id}                      软删</li>
 *     <li>POST   /api/workers/{id}/regenerate-token     重新生成 token, 旧 token 立即失效</li>
 *   </ul>
 *   <li>Worker 主动调 shipyard:</li>
 *   <ul>
 *     <li>POST   /api/workers/register                  worker 主动注册 (M9.5 严格模式)</li>
 *     <li>POST   /api/workers/{id}/heartbeat            30s 上报</li>
 *   </ul>
 *   <li>shipyard Web 调 (集群信息代理):</li>
 *   <ul>
 *     <li>GET    /api/workers/{id}/cluster/namespaces  代理 worker 拿 ns</li>
 *     <li>GET    /api/workers/{id}/cluster/pods         代理 worker 拿 pod</li>
 *     <li>GET    /api/workers/{id}/cluster/deployments  代理 worker 拿 deployment</li>
 *     <li>GET    /api/workers/{id}/cluster/worker-pods  兼容老 M9 commit-16</li>
 *   </ul>
 * </ul>
 */
import { http } from './client';
import type { PageResponse } from './types';

/**
 * Worker 实体 (M9.5 redesign, 跟后端 com.shipyard.worker.entity.Worker 对齐).
 *
 * <p>1 worker = 1 pod, 状态机: PLANNED / PROVISIONING / ONLINE / OFFLINE / UNHEALTHY.
 */
export interface Worker {
  id: string;
  envId: string;

  /** shipyard 内部展示名. */
  name: string;

  /** k8s pod metadata.name (跟 register 严格匹配). */
  podName: string;

  /** 备注. */
  description: string | null;

  /** worker 服务 URL (worker register 时上报, shipyard 调 worker 用这个). */
  workerUrl: string | null;

  /** 是否已生成 token (UI 调 regenerate-token 时才返明文, 列表只返标志位). */
  hasToken: boolean;

  /** 状态机. */
  status: 'PLANNED' | 'PROVISIONING' | 'ONLINE' | 'OFFLINE' | 'UNHEALTHY';

  /** worker 自检状态. */
  health?: 'HEALTHY' | 'UNHEALTHY' | null;
  healthDetail?: string | null;

  lastHeartbeatAt: string | null;
  version: string | null;

  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;

  /** 心跳是否新鲜 (last_heartbeat_at 在 90s 内). */
  heartbeatFresh: boolean;
}

/** 创建 worker 请求体. */
export interface CreateWorkerRequest {
  name: string;
  podName: string;
  description?: string;
}

/** 更新 worker 请求体 (V1 阶段只能改 description). */
export interface UpdateWorkerRequest {
  description?: string;
}

/** 创建 / 重新生成 token 响应 (一次性明文). */
export interface WorkerTokenResponse {
  workerId: string;
  name: string;
  token: string;
  notice: string;
}

export interface WorkerListParams {
  page?: number;
  size?: number;
  envId?: string;
}

export const workersApi = {
  // ==================== UI 调 (CRUD) ====================

  /**
   * 创建 worker (预登记). 返明文 token (一次性, 用户复制到 k8s manifest).
   * @param envId 所属环境 ID
   */
  create: (envId: string, req: CreateWorkerRequest) =>
    http.post<WorkerTokenResponse>(`/envs/${envId}/workers`, req),

  list: (params: WorkerListParams = {}) => {
    const query: Record<string, string | number> = {
      page: params.page ?? 1,
      size: params.size ?? 50,
    };
    if (params.envId) query.envId = params.envId;
    return http.get<PageResponse<Worker>>('/workers', { params: query });
  },

  get: (id: string) => http.get<Worker>(`/workers/${id}`),

  /** V1 阶段只能改 description. */
  update: (id: string, req: UpdateWorkerRequest) =>
    http.put<Worker>(`/workers/${id}`, req),

  delete: (id: string) => http.delete<void>(`/workers/${id}`),

  /** 重新生成 token, 旧 token 立即失效. 返新 token 明文. */
  regenerateToken: (id: string) =>
    http.post<WorkerTokenResponse>(`/workers/${id}/regenerate-token`, {}),

  // ==================== 集群读类代理 (透传 worker 响应) ====================

  /** 代理: 拿这个 worker 所在集群的 namespaces. */
  listNamespaces: (id: string) =>
    http.get<Array<Record<string, unknown>>>(`/workers/${id}/cluster/namespaces`),

  /** 代理: 拿 pods (?namespace=xxx). */
  listPods: (id: string, namespace = 'default') =>
    http.get<Array<Record<string, unknown>>>(`/workers/${id}/cluster/pods`, {
      params: { namespace },
    }),

  /** 代理: 拿 deployments (?namespace=xxx). */
  listDeployments: (id: string, namespace = 'default') =>
    http.get<Array<Record<string, unknown>>>(`/workers/${id}/cluster/deployments`, {
      params: { namespace },
    }),

  /**
   * M9 commit-16 兼容: 拿 worker 自己的 deployment 状态 (replicas + pod 列表).
   * <p>M9.5: 1 worker = 1 pod, 返 1 个 pod 数组.
   */
  listWorkerPods: (id: string) =>
    http.get<Record<string, unknown>>(`/workers/${id}/cluster/worker-pods`),
};

/**
 * Worker status 颜色 + 标签 (前端用).
 * M9.5: 状态机扩展 (PLANNED / PROVISIONING / ONLINE / OFFLINE / UNHEALTHY).
 */
export function workerStatusBadge(
  status: string,
): { color: string; label: string; severity: 'success' | 'warning' | 'danger' | 'info' | 'neutral' } {
  switch (status) {
    case 'ONLINE':        return { color: 'var(--color-success)',     label: '在线',     severity: 'success' };
    case 'PROVISIONING':  return { color: 'var(--color-info)',        label: '启动中',   severity: 'info' };
    case 'PLANNED':       return { color: 'var(--color-text-muted)',  label: '已规划',   severity: 'neutral' };
    case 'OFFLINE':       return { color: 'var(--color-text-muted)',  label: '离线',     severity: 'neutral' };
    case 'UNHEALTHY':     return { color: 'var(--color-warning)',     label: '不健康',   severity: 'warning' };
    default:              return { color: 'var(--color-text-muted)',  label: status,    severity: 'neutral' };
  }
}

/** M9 commit-4: worker pod 信息 (K8s pod 简化). */
export interface WorkerPod {
  name: string;
  namespace: string;
  node?: string;
  ip?: string;
  phase: string;            // Running / Pending / Failed
  ready?: string;           // "1/1"
  createdAt?: string;
}

/** M9 commit-16 兼容: worker deployment 状态 (replicas + pod 列表). */
export interface WorkerInfo {
  workerName: string;
  namespace: string;
  replicas: number;
  readyReplicas: number;
  pods: WorkerPod[];
}

/** M9 commit-4: worker health 颜色 + 标签 (HEALTHY/UNHEALTHY 二元). */
export function workerHealthBadge(
  health: string | undefined | null,
): { color: string; label: string; severity: 'success' | 'warning' | 'danger' | 'info' | 'neutral' } {
  switch (health) {
    case 'HEALTHY':   return { color: 'var(--color-success)',    label: '健康', severity: 'success' };
    case 'UNHEALTHY': return { color: 'var(--color-warning)',    label: '不健康', severity: 'warning' };
    default:          return { color: 'var(--color-text-muted)', label: health || '—', severity: 'neutral' };
  }
}

/** 相对时间 (last_heartbeat_at). */
export function relativeTime(iso: string | null): string {
  if (!iso) return '从未';
  const then = new Date(iso).getTime();
  const now = Date.now();
  const diff = Math.floor((now - then) / 1000);
  if (diff < 0) return '未来';
  if (diff < 60) return `${diff} 秒前`;
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`;
  return `${Math.floor(diff / 86400)} 天前`;
}
