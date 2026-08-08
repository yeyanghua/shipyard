/**
 * 项目状态 store — Pinia.
 *
 * <p>M3 stub: 状态 + actions 占位, M4 接 projectsApi.
 */
import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { Project } from '@/api';

export const useProjectStore = defineStore('project', () => {
  const projects = ref<Project[]>([]);
  const current = ref<Project | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  // M4 时实现:
  // async function fetchAll() { ... }
  // async function create(req: CreateProjectRequest) { ... }

  return { projects, current, loading, error };
});
