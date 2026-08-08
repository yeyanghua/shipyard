/**
 * 项目 API — 对应后端 spec §4.2 /api/projects.
 *
 * <p>M3 stub: 类型定义就位,M4 接实际接口.
 */
import { http } from './client';

export type RepoProvider = 'gitlab' | 'gitee';
export type ProjectType = 'java_maven' | 'java_gradle' | 'node_pnpm' | 'python_poetry' | 'other';

export interface Project {
  id: number;
  name: string;
  displayName: string;
  repoProvider: RepoProvider;
  repoUrl: string;
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
  repoToken: string;
  defaultBranch?: string;
  projectType: ProjectType;
  projectMeta?: Record<string, unknown>;
  description?: string;
}

export const projectsApi = {
  list: () => http.get<Project[]>('/projects').then((r) => r.data),
  get: (id: number) => http.get<Project>(`/projects/${id}`).then((r) => r.data),
  create: (req: CreateProjectRequest) => http.post<Project>('/projects', req).then((r) => r.data),
  update: (id: number, req: Partial<CreateProjectRequest>) =>
    http.put<Project>(`/projects/${id}`, req).then((r) => r.data),
  delete: (id: number) => http.delete<void>(`/projects/${id}`).then((r) => r.data),
};
