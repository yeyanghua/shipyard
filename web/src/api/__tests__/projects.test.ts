/**
 * projects API 客户端层 mock 测试.
 *
 * <p>真实 API 调用 (M4+ 集成测试) 在 E2E 阶段做. 这里只验证:
 * - CRUD 方法都暴露
 * - 类型导出存在
 */
import { describe, it, expect } from 'vitest';
import { projectsApi } from '../projects';

describe('projectsApi', () => {
  it('exposes all CRUD methods', () => {
    expect(projectsApi.list).toBeDefined();
    expect(projectsApi.get).toBeDefined();
    expect(projectsApi.create).toBeDefined();
    expect(projectsApi.update).toBeDefined();
    expect(projectsApi.delete).toBeDefined();
  });

  it('methods are functions (not undefined)', () => {
    expect(typeof projectsApi.list).toBe('function');
    expect(typeof projectsApi.get).toBe('function');
    expect(typeof projectsApi.create).toBe('function');
    expect(typeof projectsApi.update).toBe('function');
    expect(typeof projectsApi.delete).toBe('function');
  });
});
