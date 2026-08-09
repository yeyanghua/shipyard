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

package com.shipyard.config;

import com.shipyard.common.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置 - shipyard V1 "白名单"模式.
 *
 * <p><b>V1 简化策略</b>: 不用完整 RBAC,只用:
 * <ul>
 *   <li>公开 path(白名单)→ 匿名访问</li>
 *   <li>其他 path → 必须带 JWT</li>
 *   <li>JWT 解析由 {@link JwtAuthFilter} 负责</li>
 * </ul>
 *
 * <p><b>V1.5 升级</b>: 把 path 分组成角色 (admin / developer / viewer),
 * 加 {@code .authorizeHttpRequests(authz -> authz...)} 用 @PreAuthorize 注解.
 *
 * <p><b>关键白名单</b>(这些不需要 JWT):
 * <ul>
 *   <li>{@code /api/auth/**} - 登录注册</li>
 *   <li>{@code /actuator/**} - 健康检查 + Prometheus(K8s 探针 + Prometheus 抓取)</li>
 *   <li>{@code /webhook/drone} - drone CI 回调(用 HMAC 验签保护,不靠 JWT)</li>
 *   <li>{@code /api/health} - 公开健康检查</li>
 *   <li>{@code /v3/api-docs/**} + {@code /swagger-ui/**} - OpenAPI 文档</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtProperties jwtProperties;

    /**
     * 白名单 path 列表 - 从 application.yml 读,这样可以不改代码调白名单.
     * (用 @ConfigurationProperties 而不是 @Value,因为 @Value 解析不了 YAML list)
     */
    private List<String> getWhitelist() {
        return jwtProperties.getWhitelist();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 关闭 CSRF - shipyard 是 API server,不用 cookie
            .csrf(AbstractHttpConfigurer::disable)
            // 开启 CORS - 让 Vue 前端(localhost:5173)能调
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 不用 session - 纯 stateless,token-based
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // path 授权规则
            .authorizeHttpRequests(authz -> {
                // 1. 白名单全部放行
                getWhitelist().forEach(path ->
                    authz.requestMatchers(path).permitAll());
                // 2. OPTIONS 请求(CORS 预检)放行
                authz.requestMatchers("OPTIONS", "/**").permitAll();
                // 3. 其他都要认证
                authz.anyRequest().authenticated();
            })
            // JWT 过滤器(在 UsernamePasswordAuthenticationFilter 之前)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 配置 - V1 允许 Vue dev server (localhost:5173) 调 API.
     * V1.5 改成根据环境配置.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:5173",   // Vite dev server
            "http://localhost:8080",   // 同源
            "http://localhost:3000"    // 备用
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(Arrays.asList("Authorization", "X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * BCrypt 密码编码器 - V1.5 加用户表时用,V1 暂时没用(白名单模式没用户).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
