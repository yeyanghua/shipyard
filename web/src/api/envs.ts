/**
 * 环境 API - 对应后端 /api/envs + /api/projects/{id}/envs.
 */
import { http } from './client';
import type { PageResponse } from './types';

export interface Env {
  id: string;
  name: string;
  displayName: string;
  clusterType: string;
  k8sNamespace: string;
  workerUrl: string;
  hasWorkerToken: boolean;    // 替代 workerToken, 不回显
  isProduction: number;       // 0=dev, 1=prod
  createdAt: string;
  updatedAt: string;
}

export interface CreateEnvRequest {
  name: string;
  displayName: string;
  clusterType?: string;
  k8sNamespace: string;
  workerUrl: string;
  workerToken?: string;
  isProduction?: number;
}

export interface UpdateEnvRequest {
  name?: string;
  displayName?: string;
  clusterType?: string;
  k8sNamespace?: string;
  workerUrl?: string;
  workerToken?: string;
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

  /** 项目-环境关联 */
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
