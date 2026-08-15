/**
 * 环境 API - 对应后端 /api/envs + /api/projects/{id}/envs.
 *
 * <p>M9.5 redesign: env 表删 workerUrl / workerToken / k8sNamespace 字段, env 只管集群元数据.
 * worker 创建走 POST /api/envs/{envId}/workers (见 workersApi.create).
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
  workerCount?: number;       // M9.5: 该 env 下的 worker 数量
}

export interface CreateEnvRequest {
  name: string;
  displayName: string;
  clusterType?: string;
  isProduction?: number;
}

export interface UpdateEnvRequest {
  name?: string;
  displayName?: string;
  clusterType?: string;
  isProduction?: number;
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
