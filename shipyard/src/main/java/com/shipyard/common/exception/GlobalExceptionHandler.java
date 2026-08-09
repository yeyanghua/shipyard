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

import com.shipyard.dto.ApiResponse;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理 — 把所有异常统一转 {@link ApiResponse}.
 *
 * <p>HTTP 状态码永远 200, 业务码在 body.code 字段.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 (Service 层抛的). */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        log.debug("业务异常: {}", e.getMessage());
        return ApiResponse.error(e);
    }

    /** Bean Validation 失败 (@Valid 校验). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return ApiResponse.error(ErrorCode.BAD_REQUEST, msg);
    }

    /** JSON 解析失败 (请求体格式错). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return ApiResponse.error(
                ErrorCode.BAD_REQUEST, "请求体格式错误: " + e.getMostSpecificCause().getMessage());
    }

    /** 路径变量类型转换失败 (e.g. /api/projects/abc 期望 Long). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ApiResponse.error(
                ErrorCode.BAD_REQUEST,
                "参数类型错误: " + e.getName() + " 期望 "
                        + (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知"));
    }

    /** 兜底: 任何其他异常. */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleGeneric(Exception e) {
        log.error("服务器异常", e);
        return ApiResponse.error(
                ErrorCode.INTERNAL_ERROR, "服务器内部错误: " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    private String formatFieldError(FieldError f) {
        return f.getField() + ": " + f.getDefaultMessage();
    }
}
