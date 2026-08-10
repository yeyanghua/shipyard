/**
 * Worker API - 对应后端 /api/workers.
 *
 * <p>M8.2 后端提供 8 端点:
 * <ul>
 *   <li>POST   /api/workers/register                  worker 主动注册</li>
 *   <li>POST   /api/workers/{id}/heartbeat            30s 心跳</li>
 *   <li>GET    /api/workers                           列表 (分页, envId 可选)</li>
 *   <li>GET    /api/workers/{id}                      详情</li>
 *   <li>DELETE /api/workers/{id}                      软删</li>
 *   <li>GET    /api/workers/{id}/cluster/namespaces  代理 worker 拿 ns</li>
 *   <li>GET    /api/workers/{id}/cluster/pods         代理 worker 拿 pod</li>
 *   <li>GET    /api/workers/{id}/cluster/deployments  代理 worker 拿 deployment</li>
 * </ul>
 */
import { http } from './client';

export interface Worker {
  id: string;
  envId: string;
  workerUrl: string;
  status: string;            // ACTIVE / INACTIVE / DRAINING
  version: string;
  lastHeartbeatAt: string | null;
  createdAt: string;
  updatedAt: string;
  heartbeatFresh: boolean;   // 90s 内算 fresh
}

export interface WorkerListParams {
  page?: number;
  size?: number;
  envId?: string;
}

export const workersApi = {
  list: (params: WorkerListParams = {}) => {
    const query: Record<string, string | number> = {
      page: params.page ?? 1,
      size: params.size ?? 50,
    };
    if (params.envId) query.envId = params.envId;
    return http.get<import('./types').PageResponse<Worker>>('/workers', { params: query });
  },

  get: (id: string) => http.get<Worker>(`/workers/${id}`),

  delete: (id: string) => http.delete<void>(`/workers/${id}`),

  /** 代理: 拿这个 worker 所在集群的 namespaces (调 worker k8s API) */
  listNamespaces: (id: string) =>
    http.get<Array<Record<string, unknown>>>(`/workers/${id}/cluster/namespaces`),

  /** 代理: 拿 pods (?namespace=xxx) */
  listPods: (id: string, namespace = 'default') =>
    http.get<Array<Record<string, unknown>>>(`/workers/${id}/cluster/pods`, {
      params: { namespace },
    }),

  /** 代理: 拿 deployments (?namespace=xxx) */
  listDeployments: (id: string, namespace = 'default') =>
    http.get<Array<Record<string, unknown>>>(`/workers/${id}/cluster/deployments`, {
      params: { namespace },
    }),
};

/** Worker status 颜色 + 标签 (前端用) */
export function workerStatusBadge(status: string): { color: string; label: string; severity: 'success' | 'warning' | 'danger' | 'info' | 'neutral' } {
  switch (status) {
    case 'ACTIVE':   return { color: 'var(--color-success)', label: '活跃',   severity: 'success' };
    case 'DRAINING': return { color: 'var(--color-warning)', label: '排空中', severity: 'warning' };
    case 'INACTIVE': return { color: 'var(--color-text-muted)', label: '离线', severity: 'neutral' };
    default:         return { color: 'var(--color-text-muted)', label: status,   severity: 'neutral' };
  }
}

/** 相对时间 (last_heartbeat_at) */
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
