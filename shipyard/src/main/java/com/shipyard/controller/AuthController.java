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

package com.shipyard.controller;

import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.dto.ApiResponse;
import com.shipyard.dto.DemoTokenResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Auth Controller — /api/auth/**.
 *
 * <p>V1 demo: 不用用户系统, 硬编码一个 JWT 走个形式.
 * 前端启动时 GET 一次, 存 localStorage, 后续请求都带 {@code Authorization: Bearer xxx}.
 *
 * <p>注意: {@code /api/auth/**} 已加进 SecurityConfig 白名单, 不需要 JWT 也能调.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @Value("${shipyard.jwt.secret}")
    private String secret;

    @Value("${shipyard.jwt.issuer:shipyard}")
    private String issuer;

    /** V1 demo: 7 天有效. */
    private static final long EXPIRES_IN_SECONDS = 7 * 24 * 3600;

    /**
     * GET /api/auth/demo-token — 返回硬编码的 demo JWT.
     *
     * <p>硬编码: subject="demo-user", role="admin".
     * V1.5 接用户系统后, 这个端点删除, 改用 /api/auth/login (用户名密码换 JWT).
     */
    @GetMapping("/demo-token")
    public ApiResponse<DemoTokenResponse> demoToken() {
        SecretKey signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date exp = new Date(now.getTime() + EXPIRES_IN_SECONDS * 1000);

        String token;
        try {
            token = Jwts.builder()
                .subject("demo-user")
                .claim("role", "admin")
                .issuer(issuer)
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey)
                .compact();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成 demo token 失败", e);
        }

        return ApiResponse.ok(new DemoTokenResponse(
            token, "demo-user", "admin", EXPIRES_IN_SECONDS
        ));
    }

    // 留口子, V1.5 加 /api/auth/login (username/password → JWT)
    @SuppressWarnings("unused")
    private Instant now() {
        return Instant.now();
    }
}
