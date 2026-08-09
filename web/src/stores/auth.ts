/**
 * 鉴权 store - 启动时拉 demo token.
 */
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { auth, type DemoTokenResponse } from '@/api';

export const useAuthStore = defineStore('auth', () => {
  const tokenInfo = ref<DemoTokenResponse | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function ensureToken(): Promise<string | null> {
    if (auth.isAuthenticated()) {
      // 已经登录, 不需要再拉
      return auth.getToken();
    }
    loading.value = true;
    error.value = null;
    try {
      tokenInfo.value = await auth.fetchDemoToken();
      return tokenInfo.value.token;
    } catch (e) {
      error.value = (e as Error).message;
      return null;
    } finally {
      loading.value = false;
    }
  }

  return { tokenInfo, loading, error, ensureToken };
});
