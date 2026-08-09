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

package com.shipyard.common.exception;

import lombok.Getter;

/**
 * 业务错误码枚举 — shipyard V1 统一错误码体系.
 *
 * <p>设计: HTTP 状态码永远 200, body {@code {code, message, data}} 里 {@code code} 字段是真正的业务码.
 * <ul>
 *   <li>0 = 成功</li>
 *   <li>400-499 = 客户端错 (参数错 / 未登录 / 无权限 / 资源不存在 / 资源冲突)</li>
 *   <li>500+ = 服务端错 (加密失败 / 内部错误)</li>
 * </ul>
 *
 * <p>前端 axios 拦截器一刀切: {@code res.code !== 0} → 弹错误, 不依赖 HTTP 状态码.
 */
@Getter
public enum ErrorCode {
    SUCCESS(0, "OK"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    RESOURCE_CONFLICT(409, "资源冲突"),

    CRYPTO_ERROR(500, "加密失败"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
