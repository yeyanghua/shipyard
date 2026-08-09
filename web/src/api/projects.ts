/**
 * 项目 API - 对应后端 /api/projects.
 *
 * <p>M4: 接后端真实接�? 适配 PageResponse + hasRepoToken (不回显明�?.
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
  id: string;
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
  repoToken?: string;       // 填了则覆�?重新加密
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
      }),

  get: (id: string) => http.get<Project>(`/projects/${id}`),

  create: (req: CreateProjectRequest) =>
    http.post<Project>('/projects', req),

  update: (id: string, req: UpdateProjectRequest) =>
    http.put<Project>(`/projects/${id}`, req),

  delete: (id: string) =>
    http.delete<void>(`/projects/${id}`),
};
