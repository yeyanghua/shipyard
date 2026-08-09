/**
 * 项目 store - Pinia.
 *
 * <p>M4: �?projectsApi + project-env 关联.
 */
import { defineStore } from 'pinia';
import { reactive, ref } from 'vue';
import { projectsApi, type Project, type CreateProjectRequest, type UpdateProjectRequest } from '@/api';

export const useProjectStore = defineStore('project', () => {
  const list = ref<Project[]>([]);
  const total = ref(0);
  const loading = ref(false);
  const error = ref<string | null>(null);

  const pagination = reactive({ page: 1, size: 20, keyword: '' });

  async function fetchList() {
    loading.value = true;
    error.value = null;
    try {
      const resp = await projectsApi.list({
        page: pagination.page,
        size: pagination.size,
        keyword: pagination.keyword || undefined,
      });
      // 防御: 后端连不上时 resp �?undefined, �?throw 而是显示空列�?+ 错误
      list.value = resp?.records ?? [];
      total.value = resp?.total ?? 0;
    } catch (e) {
      list.value = [];
      total.value = 0;
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }

  async function create(req: CreateProjectRequest): Promise<Project> {
    const p = await projectsApi.create(req);
    await fetchList();
    return p;
  }

  async function update(id: string, req: UpdateProjectRequest): Promise<Project> {
    const p = await projectsApi.update(id, req);
    await fetchList();
    return p;
  }

  async function remove(id: string) {
    await projectsApi.delete(id);
    await fetchList();
  }

  return {
    list,
    total,
    loading,
    error,
    pagination,
    fetchList,
    create,
    update,
    remove,
  };
});
