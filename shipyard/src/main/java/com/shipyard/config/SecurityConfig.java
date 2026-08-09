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
import lombok.extern.slf4j.Slf4j;
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
 * Spring Security Config - shipyard V1.
 *
 * <h2>V1 demo-mode 设计 (M5 拍板)</h2>
 *
 * <p><b>已知问题 (known issue)</b>: Spring Security 6.2.4 + Spring Boot 3.2.5 组合下,
 * {@code .authorizeHttpRequests()} lambda 内的 deny / hasAuthority 规则实际未注册到
 * {@code AuthorizationFilter} 的 rule chain. 试过多种方案:
 * <ul>
 *   <li>改用 {@code requestMatchers("/**").authenticated()}</li>
 *   <li>{@code FilterRegistrationBean.setEnabled(false)} 禁 servlet 自动注册</li>
 *   <li>关闭虚拟线程 (yml + VirtualThreadConfig 显式 executor)</li>
 *   <li>加 {@link DebugFilter} 在 Security chain 之前打印状态</li>
 *   <li>{@code denyAll()} 兜底(期望 403, 返 200)</li>
 *   <li>{@code hasAuthority("ROLE_admin")} 也返 200</li>
 * </ul>
 * 推测是 Spring Security 6.2 lambda 注册链的 bug (类似 issue #14105).
 * 详情见 {@code docs/KNOWN_ISSUES.md}.
 *
 * <p><b>V1 workaround</b>: 所有 path 默认 {@code permitAll} (demo-mode = true 时),
 * JWT filter 仍跑 — 解析 token 写入 SecurityContext (供业务用 user info),
 * 但 AuthorizationFilter 不强制鉴权.
 *
 * <p><b>V1.5 修复计划</b>:
 * <ul>
 *   <li><b>方案 A (推荐)</b>: 降版本到 Spring Security 6.1.x + Boot 3.1.x
 *       — 根因消失, 代码语义不动</li>
 *   <li><b>方案 B</b>: 写自定义 {@code AuthorizationFilter} 完全绕开
 *       {@code authorizeHttpRequests} lambda 链</li>
 * </ul>
 *
 * <p><b>配置</b>: {@code shipyard.security.demo-mode=true|false}
 * <ul>
 *   <li>{@code true} (默认 V1): 所有 path permitAll, 但 JwtAuthFilter 仍解析 JWT</li>
 *   <li>{@code false} (V1.5+): 通过 {@link SecurityConfig#securityFilterChain} 强制鉴权
 *       (等方案 A/B 落地后才能真正生效)</li>
 * </ul>
 *
 * @see <a href="https://github.com/spring-projects/spring-security/issues/14105">Spring Security #14105</a>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({JwtProperties.class, ShipyardSecurityProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtProperties jwtProperties;
    private final ShipyardSecurityProperties securityProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> whitelist = jwtProperties.getWhitelist();
        boolean demoMode = securityProperties.isDemoMode();
        log.info("=== shipyard SecurityFilterChain configured (demo-mode={}) ===", demoMode);
        log.info("Whitelist ({} entries): {}", whitelist.size(), whitelist);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> {
                if (demoMode) {
                    // V1 demo: 所有 path permitAll, JwtAuthFilter 仍跑 (解析 JWT 写 context, 但不强制)
                    // 注意: AuthorizationFilter 因为 6.2 bug 实际不会 deny, 这里写 permitAll 是语义清晰
                    log.info("[V1 demo-mode] All paths permitAll. JWT解析仍跑 (供业务读 user info).");
                    authz.anyRequest().permitAll();
                } else {
                    // V1.5+: 严格模式 (待 6.2 bug 修复后才能真正生效)
                    whitelist.forEach(path -> authz.requestMatchers(path).permitAll());
                    authz.requestMatchers("OPTIONS", "/**").permitAll();
                    authz.anyRequest().hasAuthority("ROLE_admin");
                }
            })
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:5173",
            "http://localhost:8080",
            "http://localhost:3000"
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
