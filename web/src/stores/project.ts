/**
 * 项目 store - Pinia.
 *
 * <p>M4: 接 projectsApi + project-env 关联.
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
      list.value = resp.records;
      total.value = resp.total;
    } catch (e) {
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

  async function update(id: number, req: UpdateProjectRequest): Promise<Project> {
    const p = await projectsApi.update(id, req);
    await fetchList();
    return p;
  }

  async function remove(id: number) {
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
