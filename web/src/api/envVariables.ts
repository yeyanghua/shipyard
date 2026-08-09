/**
 * 环境变量 API - 对应后端 /api/envs/{id}/variables.
 */
import { http } from './client';

export interface EnvVariable {
  id: number;
  envId: number;
  projectId: number | null;   // null = 全局, 非空 = 项目级
  key: string;
  value: string;               // secret 时是 "***", 非 secret 时是明文
  isSecret: number;            // 1=隐藏, 0=明文
  description?: string;
  updatedBy: string;
  updatedAt: string;
}

export interface EnvVariableUpsertItem {
  key: string;
  value: string;
  isSecret?: number;
  description?: string;
}

export const envVariablesApi = {
  list: (envId: number, projectId?: number) =>
    http
      .get<EnvVariable[]>(`/envs/${envId}/variables`, {
        params: projectId ? { projectId } : {},
      })
      .then((r) => r.data),

  batchUpsert: (
    envId: number,
    items: EnvVariableUpsertItem[],
    projectId?: number,
  ) =>
    http
      .put<EnvVariable[]>(`/envs/${envId}/variables`, { items }, {
        params: projectId ? { projectId } : {},
      })
      .then((r) => r.data),

  getDecrypted: (envId: number, key: string, projectId?: number) =>
    http
      .get<{ value: string }>(`/envs/${envId}/variables/${key}`, {
        params: projectId ? { projectId } : {},
      })
      .then((r) => r.data),

  delete: (envId: number, key: string, projectId?: number) =>
    http
      .delete<void>(`/envs/${envId}/variables/${key}`, {
        params: projectId ? { projectId } : {},
      })
      .then((r) => r.data),
};
