/**
 * 环境 store - Pinia.
 */
import { defineStore } from 'pinia';
import { reactive, ref } from 'vue';
import { envsApi, type Env, type CreateEnvRequest, type UpdateEnvRequest, type ProjectEnvLink } from '@/api';

export const useEnvStore = defineStore('env', () => {
  const list = ref<Env[]>([]);
  const total = ref(0);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const pagination = reactive({ page: 1, size: 20, keyword: '', production: undefined as boolean | undefined });

  async function fetchList() {
    loading.value = true;
    error.value = null;
    try {
      const resp = await envsApi.list({
        page: pagination.page,
        size: pagination.size,
        keyword: pagination.keyword || undefined,
        production: pagination.production,
      });
      list.value = resp.records;
      total.value = resp.total;
    } catch (e) {
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }

  async function create(req: CreateEnvRequest): Promise<Env> {
    const e = await envsApi.create(req);
    await fetchList();
    return e;
  }

  async function update(id: number, req: UpdateEnvRequest): Promise<Env> {
    const e = await envsApi.update(id, req);
    await fetchList();
    return e;
  }

  async function remove(id: number) {
    await envsApi.delete(id);
    await fetchList();
  }

  async function listByProject(projectId: number): Promise<ProjectEnvLink[]> {
    return envsApi.listByProject(projectId);
  }

  async function associate(projectId: number, envId: number) {
    await envsApi.associate(projectId, envId);
  }

  async function unassociate(projectId: number, envId: number) {
    await envsApi.unassociate(projectId, envId);
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
    listByProject,
    associate,
    unassociate,
  };
});
