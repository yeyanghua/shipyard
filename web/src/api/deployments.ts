/**
 * Deployments API - 对应后端 /api/deployments (M9 commit-6).
 *
 * <p>8 端点:
 * <ul>
 *   <li>POST /api/projects/{id}/deployments                 触发部署</li>
 *   <li>GET  /api/deployments/{id}                          部署详情</li>
 *   <li>GET  /api/deployments?projectId=&envId=             列表 (分页)</li>
 *   <li>GET  /api/deployments/{id}/snapshots                某 deploy 的 snapshot</li>
 *   <li>GET  /api/deployments/snapshots?projectId=&envId=   跨 deploy snapshot</li>
 *   <li>POST /api/deployments/{id}/rollback/{snapshotId}    一键回滚</li>
 *   <li>POST /api/deployments/{id}/cancel                   取消 (PENDING/RUNNING)</li>
 *   <li>GET  /api/deployments/{id}/live-manifest            k8s 真生效 manifest (raw yaml)</li>
 * </ul>
 */
import { http } from './client';

export interface Deploy {
  id: string;
  projectId: string;
  envId: string;
  buildRecordId: string | null;
  imageTag: string | null;
  namespace: string;
  deployYamlSha256: string;
  currentSnapshotId: string | null;
  status: DeployStatus;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  triggeredBy: string;
  triggerType: string;
  createdAt: string;
  workerId: string | null;  // 选中执行 deploy 的 worker
}

export type DeployStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'TIMEOUT'
  | 'CANCELED';

export interface DeploySnapshot {
  id: string;
  deployRecordId: string;
  envId: string;
  projectId: string;
  deployYaml: string;        // 完整 yaml (LONGTEXT)
  deployYamlSha256: string;
  createdBy: string;
  createdAt: string;
}

export interface CreateDeployRequest {
  envId: string | number;
  buildRecordId?: string | number | null;
  imageTag?: string;
  replicas?: number;
  triggeredBy?: string;
}

export interface DeployListParams {
  projectId?: string | number;
  envId?: string | number;
  page?: number;
  size?: number;
}

export const deploymentsApi = {
  // 触发部署
  create: (projectId: string | number, req: CreateDeployRequest) =>
    http.post<Deploy>(`/projects/${projectId}/deployments`, req),

  // 部署详情
  get: (id: string | number) => http.get<Deploy>(`/deployments/${id}`),

  // 列表 (不分页, 一次拿 20 条, M9.5 加 cursor)
  list: (params: DeployListParams = {}) => {
    const query: Record<string, string | number> = {
      page: params.page ?? 1,
      size: params.size ?? 20,
    };
    if (params.projectId != null) query.projectId = params.projectId;
    if (params.envId != null) query.envId = params.envId;
    return http.get<Deploy[]>(`/deployments`, { params: query });
  },

  // 某 deploy 的 snapshot 列表
  listSnapshots: (id: string | number) =>
    http.get<DeploySnapshot[]>(`/deployments/${id}/snapshots`),

  // 跨 deploy snapshot 列表 (按 project + env)
  listSnapshotsByProjectEnv: (projectId: string | number, envId: string | number) =>
    http.get<DeploySnapshot[]>(`/deployments/snapshots`, {
      params: { projectId, envId },
    }),

  // 一键回滚
  rollback: (
    id: string | number,
    snapshotId: string | number,
    triggeredBy = 'unknown',
  ) =>
    http.post<Deploy>(
      `/deployments/${id}/rollback/${snapshotId}?triggeredBy=${encodeURIComponent(triggeredBy)}`,
    ),

  // 取消部署
  cancel: (id: string | number) =>
    http.post<Deploy>(`/deployments/${id}/cancel`),

  // k8s 真生效 manifest (raw yaml 字符串, 不是 ApiResponse 包装)
  // 走 raw fetch 绕过 http 拦截器 (返回不拆 code/data)
  getLiveManifest: async (id: string | number): Promise<string> => {
    const { auth } = await import('./auth');
    const baseURL = (import.meta.env.VITE_SHIPYARD_API_URL as string) || '/api';
    const token = auth.getToken();
    const resp = await fetch(`${baseURL}/deployments/${id}/live-manifest`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!resp.ok) {
      throw new Error(`live-manifest HTTP ${resp.status}: ${await resp.text()}`);
    }
    return resp.text();
  },
};

/** Deploy 状态 badge (颜色 + 标签) */
export function deployStatusBadge(status: DeployStatus | string): {
  color: string;
  label: string;
  severity: 'success' | 'warning' | 'danger' | 'info' | 'neutral';
} {
  switch (status) {
    case 'PENDING':   return { color: 'var(--color-text-muted)',  label: '排队中', severity: 'info' };
    case 'RUNNING':   return { color: 'var(--color-accent)',      label: '执行中', severity: 'warning' };
    case 'SUCCESS':   return { color: 'var(--color-success)',     label: '成功',   severity: 'success' };
    case 'FAILED':    return { color: 'var(--color-danger)',      label: '失败',   severity: 'danger' };
    case 'TIMEOUT':   return { color: 'var(--color-danger)',      label: '超时',   severity: 'danger' };
    case 'CANCELED':  return { color: 'var(--color-text-muted)',  label: '已取消', severity: 'neutral' };
    default:          return { color: 'var(--color-text-muted)',  label: status,  severity: 'neutral' };
  }
}
