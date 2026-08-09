/*
 * Copyright 2026 The shipyard Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.shipyard.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * JWT 认证过滤器 - shipyard V1 用.
 *
 * <p>每个请求都过这个 filter:
 * <ol>
 *   <li>从 {@code Authorization: Bearer xxx} 头拿 token</li>
 *   <li>用 JJWT 解析 + 验签(失败就当匿名,SecurityConfig 决定放不放行)</li>
 *   <li>把 user 信息塞进 {@code SecurityContext},Spring Security 后面自动用</li>
 * </ol>
 *
 * <p><b>关于虚拟线程</b>: OncePerRequestFilter 跑在虚拟线程上没问题,
 * 因为它不做长时间阻塞 I/O,JJWT 解析是纯 CPU 操作.
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${shipyard.jwt.secret}")
    private String secret;

    @Value("${shipyard.jwt.issuer:shipyard}")
    private String issuer;

    private SecretKey signingKey;

    @jakarta.annotation.PostConstruct
    public void init() {
        // JJWT 0.12+ 用 Keys.hmacShaKeyFor 自动选 HS256/384/512
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("JwtAuthFilter initialized with HS256/HS384/HS512 (size={} bits)",
            secret.getBytes(StandardCharsets.UTF_8).length * 8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

                String userId = claims.getSubject();
                String role = claims.get("role", String.class);

                // V1 简化: 角色直接当 authority
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    role != null
                        ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                        : Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // 解析失败 - 不塞 context,后面会返回 401
                log.debug("JWT 解析失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
