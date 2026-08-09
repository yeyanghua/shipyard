/**
 * 项目 API - 对应后端 /api/projects.
 *
 * <p>M4: 接后端真实接口, 适配 PageResponse + hasRepoToken (不回显明文).
 */
import { http } from './client';
import type { PageResponse } from './types';

export type RepoProvider = 'gitlab' | 'gitee';
export type ProjectType =
  | 'java_maven'
  | 'java_gradle'
  | 'node_pnpm'
  | 'python_poetry'
  | 'other';

export interface Project {
  id: number;
  name: string;
  displayName: string;
  repoProvider: RepoProvider;
  repoUrl: string;
  hasRepoToken: boolean;   // 替代 repoToken, 不回显明文
  defaultBranch: string;
  projectType: ProjectType;
  projectMeta?: Record<string, unknown>;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProjectRequest {
  name: string;
  displayName: string;
  repoProvider: RepoProvider;
  repoUrl: string;
  repoToken?: string;       // 选填, 填了则加密
  defaultBranch?: string;
  projectType: ProjectType;
  projectMeta?: Record<string, unknown>;
  description?: string;
}

export interface UpdateProjectRequest {
  name?: string;
  displayName?: string;
  repoProvider?: RepoProvider;
  repoUrl?: string;
  repoToken?: string;       // 填了则覆盖+重新加密
  defaultBranch?: string;
  projectType?: ProjectType;
  projectMeta?: Record<string, unknown>;
  description?: string;
}

export const projectsApi = {
  list: (params: { page?: number; size?: number; keyword?: string } = {}) =>
    http
      .get<PageResponse<Project>>('/projects', {
        params: { page: 1, size: 20, ...params },
      })
      .then((r) => r.data),

  get: (id: number) => http.get<Project>(`/projects/${id}`).then((r) => r.data),

  create: (req: CreateProjectRequest) =>
    http.post<Project>('/projects', req).then((r) => r.data),

  update: (id: number, req: UpdateProjectRequest) =>
    http.put<Project>(`/projects/${id}`, req).then((r) => r.data),

  delete: (id: number) =>
    http.delete<void>(`/projects/${id}`).then((r) => r.data),
};
