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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Demo Token 响应 — GET /api/auth/demo-token.
 *
 * <p>V1 demo 用: 不做用户系统, 直接给前端一个 7 天有效的硬编码 JWT.
 * 前端启动时拉一次, 存 localStorage, 后续请求都带 {@code Authorization: Bearer xxx}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemoTokenResponse {

    private String token;
    private String userId;
    private String role;
    private long expiresInSeconds;
}
