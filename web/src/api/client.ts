/**
 * Axios 客户端 - 调 shipyard 后端 (M2 端口 8080).
 *
 * <p>dev: Vite proxy /api -> http://localhost:8080
 * <p>prod: 通过 VITE_SHIPYARD_API_URL 注入.
 *
 * <p>V1 范围: 鉴权 (JWT 注入) + 拦截器 + 错误归一化.
 *
 * <h2>类型契约</h2>
 * <p>响应拦截器在 code=0 时 <b>unwrap body.data</b>, 所以 {@code http.get<Project>('/x')}
 * resolve value 是 {@code Project} (不是 {@code AxiosResponse<Project>}).
 * view / store 直接 {@code const p = await http.get<Project>(...)} 拿 data 即可.
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

/**
 * 内部 axios 实例, 不对外暴露 — 避免调用方拿到 AxiosResponse 类型自己 unwrap.
 * 外部用 {@link http}.
 */
const innerHttp: AxiosInstance = axios.create({
  baseURL,
  timeout: 30_000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/** 与 AxiosResponse 等价的"已 unwrap" 包装 — 让 TS 跟运行时一致 */
type Unwrapped<T> = T;

/**
 * 公开 http client — 包装 axios 让 {@code http.get<T>('/x')} resolve {@code T} 而不是 {@code AxiosResponse<T>}.
 */
export const http = {
  get: <T>(url: string, config?: Parameters<AxiosInstance['get']>[1]): Promise<Unwrapped<T>> =>
    innerHttp.get<T>(url, config).then(unwrapResponse<T>),

  post: <T, B = unknown>(url: string, data?: B, config?: Parameters<AxiosInstance['post']>[2]): Promise<Unwrapped<T>> =>
    innerHttp.post<T>(url, data, config).then(unwrapResponse<T>),

  put: <T, B = unknown>(url: string, data?: B, config?: Parameters<AxiosInstance['put']>[2]): Promise<Unwrapped<T>> =>
    innerHttp.put<T>(url, data, config).then(unwrapResponse<T>),

  delete: <T>(url: string, config?: Parameters<AxiosInstance['delete']>[1]): Promise<Unwrapped<T>> =>
    innerHttp.delete<T>(url, config).then(unwrapResponse<T>),
};

/**
 * 把 AxiosResponse 拆成 backend body.data (业务码 0 时), 否则 throw ApiError.
 */
function unwrapResponse<T>(response: AxiosResponse): T {
  const body = response.data as ApiResponse<unknown> | undefined;
  if (body && typeof body === 'object' && 'code' in body) {
    if (body.code !== 0) {
      throw new ApiError(body.code, body.message, response.status);
    }
    return body.data as T;
  }
  // 没包装的 (如 actuator/health) 直接返 response.data
  return response.data as T;
}

/**
 * @deprecated 旧 raw axios 实例, 仅 {@link unwrapResponse} 内部用.
 * 不要在 view / store 直接 import innerHttp.
 */

// ---- 请求拦截器: 自动注入 JWT (挂在 innerHttp) ----
innerHttp.interceptors.request.use((config) => {
  const token = auth.getToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

// ---- 响应拦截器: 网络/4xx 错误转 ApiError ----
innerHttp.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response) {
      const body = error.response.data as ApiResponse<unknown> | undefined;
      const message = body?.message ?? error.message;
      return Promise.reject(new ApiError(body?.code ?? -1, message, error.response.status));
    }
    return Promise.reject(new ApiError(-1, error.message));
  },
);

/** 用于 EventSource (SSE) 的基础 URL - Vite proxy 不支持 SSE, 用全 URL */
export const sseBaseURL = import.meta.env.VITE_SHIPYARD_API_URL || 'http://localhost:8080';
