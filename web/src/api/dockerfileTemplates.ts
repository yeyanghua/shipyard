/**
 * Dockerfile 模板 API - 对应后端 /api/dockerfile-templates + /api/projects/{id}/dockerfile.
 *
 * <p>M12 — 5 套内置模板 (java_maven_jdk21 / java_gradle_jdk21 / node_pnpm_20 /
 * python_poetry_312 / generic_alpine). 启动时 shipyard 幂等插入.
 */
import { http } from './client';

export interface DockerfileTemplate {
  id: string;
  name: string;
  displayName: string;
  language: string;
  buildTool: string;
  variableSchema: string;  // JSON string: [{key, type, default, description, required}]
  version: number;
  isBuiltin: number;       // 1=内置
}

/** variableSchema 解析后的 UI 字段定义 */
export interface TemplateVariableDef {
  key: string;
  type: 'string' | 'int' | 'boolean';
  default?: string;
  description?: string;
  required: boolean;
}

export interface DockerfileGenerateRequest {
  templateName: string;
  variables?: Record<string, string>;
  repoBranch?: string;
  commitMessage?: string;
}

export interface DockerfileGenerateResponse {
  projectDockerfileId: string;
  projectId: string;
  templateId: string;
  templateName: string;
  renderedContent: string;
  status: string;          // preview / draft / pushed / rejected
  repoBranch: string;
  commitMessage: string;
  repoCommitSha: string | null;
  createdAt: string;
}

export const dockerfileTemplatesApi = {
  /** 列出所有可用模板 (按 language, build_tool 排序) */
  listTemplates: () => http.get<DockerfileTemplate[]>('/dockerfile-templates'),

  /** 预览渲染 — 不存数据库 */
  preview: (projectId: string, req: DockerfileGenerateRequest) =>
    http.post<DockerfileGenerateResponse>(`/projects/${projectId}/dockerfile/preview`, req),

  /** 真生成 — 渲染 + 写 project_dockerfile (status=draft) */
  generate: (projectId: string, req: DockerfileGenerateRequest) =>
    http.post<DockerfileGenerateResponse>(`/projects/${projectId}/dockerfile/generate`, req),
};

/** 解析 variableSchema JSON 字符串成 UI 字段 (容错, 解析失败返空数组) */
export function parseVariableSchema(schema: string | undefined): TemplateVariableDef[] {
  if (!schema) return [];
  try {
    const arr = JSON.parse(schema);
    if (!Array.isArray(arr)) return [];
    return arr.map((v: any) => ({
      key: String(v.key ?? ''),
      type: (v.type ?? 'string') as TemplateVariableDef['type'],
      default: v.default != null ? String(v.default) : undefined,
      description: v.description,
      required: Boolean(v.required),
    }));
  } catch {
    return [];
  }
}

/** 按项目类型推荐模板 (启发式映射, 找不到返第一个 generic 模板) */
export function recommendTemplate(templates: DockerfileTemplate[], projectType: string): DockerfileTemplate | null {
  if (templates.length === 0) return null;
  const map: Record<string, string> = {
    java_maven: 'java_maven_jdk21',
    java_gradle: 'java_gradle_jdk21',
    node_pnpm: 'node_pnpm_20',
    python_poetry: 'python_poetry_312',
  };
  const target = map[projectType];
  if (target) {
    const found = templates.find((t) => t.name === target);
    if (found) return found;
  }
  // 兜底: 找 generic, 再没有就返第一个
  return templates.find((t) => t.name === 'generic_alpine') ?? templates[0] ?? null;
}
