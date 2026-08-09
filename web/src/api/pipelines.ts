/**
 * Pipeline 模板 API - 对应后端 /api/projects/{projectId}/pipeline.
 *
 * <p>M6 3 前端接入 — 8 个端点 (CRUD + 审批/激活/驳回 + AI 生成).
 *
 * <p>响应拦截器在 code=0 时已 unwrap body.data, 所以
 * {@code pipelinesApi.getActive()} resolve value 是 {@code Pipeline | null}.
 */
import { http } from './client';

export type ReviewStatus = 'DRAFT' | 'APPROVED' | 'REJECTED';

export interface Pipeline {
  id: string;
  projectId: string;
  version: number;
  yamlContent: string;
  reviewStatus: ReviewStatus;
  isActive: number; // 0/1
  createdBy: string;
  aiModifiedBy?: string;
  aiPrompt?: string;
  createdAt: string;
}

/** 创建请求 — 两种用法按 aiGenerate 区分 */
export interface CreatePipelineRequest {
  /** 用户手动填的 YAML. aiGenerate=false 时必填. */
  yamlContent?: string;
  /** true 走 AI 生成 (Mock LLM 默认). */
  aiGenerate?: boolean;
  /** AI 改写时的额外 prompt. */
  aiPrompt?: string;
  /** 单次 provider 覆盖 (V1 仅记录, 业务走 default). */
  aiProvider?: string;
}

/** 更新请求 — 只能更新 draft / rejected 状态 */
export interface UpdatePipelineRequest {
  yamlContent?: string;
  aiPrompt?: string;
  /** 标记本次修改由 AI 触发. */
  aiModified?: boolean;
}

export const pipelinesApi = {
  /** 查项目当前 active 版本 (可能为 null) */
  getActive: (projectId: string) =>
    http.get<Pipeline | null>(`/projects/${projectId}/pipeline`),

  /** 列项目所有版本 (按 version DESC). includeDeleted=true 返软删行 (E2E 用) */
  listVersions: (projectId: string, includeDeleted = false) =>
    http.get<Pipeline[]>(`/projects/${projectId}/pipeline/versions`, {
      params: { includeDeleted },
    }),

  /** 创建新版本 (DRAFT). 支持 AI 生成 (aiGenerate=true) */
  create: (projectId: string, req: CreatePipelineRequest) =>
    http.post<Pipeline>(`/projects/${projectId}/pipeline`, req),

  /** 更新 draft / rejected 版本 (approved immutable) */
  update: (projectId: string, versionId: string, req: UpdatePipelineRequest) =>
    http.put<Pipeline>(`/projects/${projectId}/pipeline/${versionId}`, req),

  /** 审批: DRAFT → APPROVED */
  approve: (projectId: string, versionId: string) =>
    http.post<Pipeline>(`/projects/${projectId}/pipeline/${versionId}/approve`),

  /** 驳回: DRAFT → REJECTED (终态) */
  reject: (projectId: string, versionId: string) =>
    http.post<Pipeline>(`/projects/${projectId}/pipeline/${versionId}/reject`),

  /** 激活: APPROVED → ACTIVE, 同项目其他 active 自动 unactivate */
  activate: (projectId: string, versionId: string) =>
    http.post<Pipeline>(`/projects/${projectId}/pipeline/${versionId}/activate`),

  /** 删除. force=true 走物理删 (跳过业务约束, E2E 用) */
  delete: (projectId: string, versionId: string, force = false) =>
    http.delete<void>(`/projects/${projectId}/pipeline/${versionId}`, {
      params: { force },
    }),
};
