/*
 * Copyright 2026 The shipyard Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shipyard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import java.time.Instant;
import lombok.Data;

/**
 * 统一 API 响应体.
 *
 * <p>所有 Controller 端点都返回这个结构, HTTP 状态码永远是 200, 业务码在 body 里.
 *
 * <pre>{@code
 * {
 *   "code": 0,            // 0=成功, 非 0=业务错误
 *   "message": "OK",      // 人类可读消息
 *   "data": { ... },      // 业务数据 (成功时填, 失败时 null)
 *   "timestamp": 1691568000000
 * }
 * }</pre>
 *
 * <p>前端 axios 拦截器一刀切: {@code res.code !== 0} → 弹错, 不依赖 HTTP 状态码.
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS) // 即使 null 也输出, 前端好判
public class ApiResponse<T> {

    /** 业务码: 0=成功, 4xx/5xx 见 {@link ErrorCode}. */
    private int code;

    /** 人类可读消息. */
    private String message;

    /** 业务数据. */
    private T data;

    /** 响应时间 (毫秒 Unix timestamp). */
    private long timestamp;

    public ApiResponse() {}

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toEpochMilli();
    }

    // ==================== 工厂方法 ====================

    /** 成功响应 (data 可空, message 默认 "OK"). */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /** 成功响应 (无 data). */
    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    /** 错误响应 (用 ErrorCode 默认 message). */
    public static <T> ApiResponse<T> error(ErrorCode ec) {
        return new ApiResponse<>(ec.getCode(), ec.getMessage(), null);
    }

    /** 错误响应 (覆盖 message). */
    public static <T> ApiResponse<T> error(ErrorCode ec, String message) {
        return new ApiResponse<>(ec.getCode(), message, null);
    }

    /** 错误响应 (用 BusinessException 直接传). */
    public static <T> ApiResponse<T> error(BusinessException e) {
        return new ApiResponse<>(e.getErrorCode().getCode(), e.getMessage(), null);
    }
}
