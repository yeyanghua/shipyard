/**
 * 环境 API - 对应后端 /api/envs + /api/projects/{id}/envs.
 *
 * <p>V1 阶段 (V5 撤回后 V3 模式): env 表自管 workerUrl / k8sNamespace (workerTokenEnc 不返前端, shipyard 后端 AES-256 加密存).
 * 创 env 时 shipyard 自动生成 workerToken, 不暴露给前端.
 */
import { http } from './client';
import type { PageResponse } from './types';

export interface Env {
  id: string;
  name: string;
  displayName: string;
  clusterType: string;
  isProduction: number;       // 0=dev, 1=prod
  createdAt: string;
  updatedAt: string;

  /**
   * V1 阶段 (V5 撤回后): 该 env 下的 worker 服务 URL.
   * shipyard 调 K8s API 走这个 (V1 阶段 shipyard 自指, 演示用).
   */
  workerUrl?: string | null;

  /**
   * V1 阶段: 该 env 对应 k8s namespace. 创 env 时默认 = env.name.
   * shipyard 调 K8s API 时指定这个 ns.
   */
  k8sNamespace?: string | null;

  /**
   * V1 阶段: 该 env 下的 worker 数量 (in-process 模拟, 创 env 时 0).
   */
  workerCount?: number;
}

export interface CreateEnvRequest {
  name: string;
  displayName: string;
  clusterType?: string;
  isProduction?: number;
  /** V1 阶段可选, 留空走 shipyard 默认 (shipyard-tunnel 跳板). */
  workerUrl?: string;
  /** V1 阶段可选, 留空走默认 = env.name. */
  k8sNamespace?: string;
}

export interface UpdateEnvRequest {
  name?: string;
  displayName?: string;
  clusterType?: string;
  isProduction?: number;
  workerUrl?: string;
  k8sNamespace?: string;
}

export interface ProjectEnvLink {
  projectId: string;
  envId: string;
}

export const envsApi = {
  list: (params: { page?: number; size?: number; keyword?: string; production?: boolean } = {}) =>
    http
      .get<PageResponse<Env>>('/envs', {
        params: { page: 1, size: 20, ...params },
      }),

  get: (id: string) => http.get<Env>(`/envs/${id}`),

  create: (req: CreateEnvRequest) =>
    http.post<Env>('/envs', req),

  update: (id: string, req: UpdateEnvRequest) =>
    http.put<Env>(`/envs/${id}`, req),

  delete: (id: string) =>
    http.delete<void>(`/envs/${id}`),

  /** 项目-环境关联. */
  listByProject: (projectId: string) =>
    http
      .get<ProjectEnvLink[]>(`/projects/${projectId}/envs`),

  associate: (projectId: string, envId: string) =>
    http
      .post<ProjectEnvLink>(`/projects/${projectId}/envs`, { envId }),

  unassociate: (projectId: string, envId: string) =>
    http
      .delete<void>(`/projects/${projectId}/envs/${envId}`),
};
