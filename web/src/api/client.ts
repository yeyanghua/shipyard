/**
 * Axios 客户端 - 调 shipyard 后端 (M2 端口 8080).
 *
 * <p>dev: Vite proxy /api -> http://localhost:8080
 * <p>prod: 通过 VITE_SHIPYARD_API_URL 注入.
 *
 * <p>V1 范围: 鉴权 (JWT 注入) + 拦截器 + 错误归一化.
 */
import axios, { AxiosError, type AxiosInstance, type AxiosResponse } from 'axios';
import { auth } from './auth';

/** 后端统一响应包装: { code, message, data } */
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp?: number;
}

/** 业务错误 - 从后端 code != 0 抛出 */
export class ApiError extends Error {
  constructor(
    public readonly code: number,
    message: string,
    public readonly httpStatus?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

const baseURL = import.meta.env.VITE_SHIPYARD_API_URL || '/api';

export const http: AxiosInstance = axios.create({
  baseURL,
  timeout: 30_000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// ---- 请求拦截器: 自动注入 JWT ----
http.interceptors.request.use((config) => {
  const token = auth.getToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

// ---- 响应拦截器: 业务码归一化 / 错误处理 ----
http.interceptors.response.use(
  (response: AxiosResponse) => {
    const body = response.data as ApiResponse<unknown> | undefined;
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        // 业务错误
        return Promise.reject(new ApiError(body.code, body.message, response.status));
      }
      return body.data as unknown as AxiosResponse;
    }
    // 没包装的 (如 actuator/health) 直接返回
    return response;
  },
  (error: AxiosError) => {
    if (error.response) {
      const body = error.response.data as ApiResponse<unknown> | undefined;
      const message = body?.message ?? error.message;
      return Promise.reject(new ApiError(body?.code ?? -1, message, error.response.status));
    }
    // 网络错误 / 超时
    return Promise.reject(new ApiError(-1, error.message));
  },
);

/** 用于 EventSource (SSE) 的基础 URL - Vite proxy 不支持 SSE, 用全 URL */
export const sseBaseURL = import.meta.env.VITE_SHIPYARD_API_URL || 'http://localhost:8080';
