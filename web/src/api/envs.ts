/**
 * 环境 API - 对应后端 /api/envs + /api/projects/{id}/envs.
 */
import { http } from './client';
import type { PageResponse } from './types';

export interface Env {
  id: number;
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
  projectId: number;
  envId: number;
}

export const envsApi = {
  list: (params: { page?: number; size?: number; keyword?: string; production?: boolean } = {}) =>
    http
      .get<PageResponse<Env>>('/envs', {
        params: { page: 1, size: 20, ...params },
      })
      .then((r) => r.data),

  get: (id: number) => http.get<Env>(`/envs/${id}`).then((r) => r.data),

  create: (req: CreateEnvRequest) =>
    http.post<Env>('/envs', req).then((r) => r.data),

  update: (id: number, req: UpdateEnvRequest) =>
    http.put<Env>(`/envs/${id}`, req).then((r) => r.data),

  delete: (id: number) =>
    http.delete<void>(`/envs/${id}`).then((r) => r.data),

  /** 项目-环境关联 */
  listByProject: (projectId: number) =>
    http
      .get<ProjectEnvLink[]>(`/projects/${projectId}/envs`)
      .then((r) => r.data),

  associate: (projectId: number, envId: number) =>
    http
      .post<ProjectEnvLink>(`/projects/${projectId}/envs`, { envId })
      .then((r) => r.data),

  unassociate: (projectId: number, envId: number) =>
    http
      .delete<void>(`/projects/${projectId}/envs/${envId}`)
      .then((r) => r.data),
};
