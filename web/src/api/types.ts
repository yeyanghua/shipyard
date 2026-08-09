/**
 * API 共享类型 - 后端统一响应包装.
 */
export interface PageResponse<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

/** 业务错误码 - 对应后端 ErrorCode 枚举 */
export const ErrorCode = {
  SUCCESS: 0,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  RESOURCE_CONFLICT: 409,
  CRYPTO_ERROR: 500,
  INTERNAL_ERROR: 500,
} as const;
