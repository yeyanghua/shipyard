/**
 * 环境变量 API - 对应后端 /api/envs/{id}/variables.
 */
import { http } from './client';

export interface EnvVariable {
  id: string;
  envId: string;
  projectId: string | null;   // null = 全局, 非空 = 项目级
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
  list: (envId: string, projectId?: string) =>
    http
      .get<EnvVariable[]>(`/envs/${envId}/variables`, {
        params: projectId ? { projectId } : {},
      }),

  batchUpsert: (
    envId: string,
    items: EnvVariableUpsertItem[],
    projectId?: string,
  ) =>
    http
      .put<EnvVariable[]>(`/envs/${envId}/variables`, { items }, {
        params: projectId ? { projectId } : {},
      }),

  getDecrypted: (envId: string, key: string, projectId?: string) =>
    http
      .get<{ value: string }>(`/envs/${envId}/variables/${key}`, {
        params: projectId ? { projectId } : {},
      }),

  delete: (envId: string, key: string, projectId?: string) =>
    http
      .delete<void>(`/envs/${envId}/variables/${key}`, {
        params: projectId ? { projectId } : {},
      }),
};
