/**
 * 鉴权 API - V1 demo 硬编码 JWT.
 *
 * <p>启动时 GET 一次拿到 token, 存 localStorage, 后续请求都带 Bearer.
 */
import { http } from './client';

export interface DemoTokenResponse {
  token: string;
  userId: string;
  role: string;
  expiresInSeconds: number;
}

const TOKEN_KEY = 'shipyard.token';
const TOKEN_EXP_KEY = 'shipyard.token.expiresAt';

export const auth = {
  /** 从 localStorage 拿 token (如果还没过期) */
  getToken(): string | null {
    const token = localStorage.getItem(TOKEN_KEY);
    const exp = localStorage.getItem(TOKEN_EXP_KEY);
    if (!token || !exp) return null;
    if (Date.now() >= Number(exp)) {
      auth.clear();
      return null;
    }
    return token;
  },

  /** 调用 /api/auth/demo-token 拿 token 并存 localStorage */
  async fetchDemoToken(): Promise<DemoTokenResponse> {
    const r = await http.get<DemoTokenResponse>('/auth/demo-token');
    localStorage.setItem(TOKEN_KEY, r.token);
    localStorage.setItem(
      TOKEN_EXP_KEY,
      String(Date.now() + r.expiresInSeconds * 1000),
    );
    return r;
  },

  /** 清掉 token */
  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(TOKEN_EXP_KEY);
  },

  /** 是否已登录 (有未过期 token) */
  isAuthenticated(): boolean {
    return auth.getToken() !== null;
  },
};
