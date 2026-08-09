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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Debug 用的 servlet filter - 强制在 Security chain <b>前</b> 跑, 看 context 状态.
 *
 * <p><b>用途</b>: V1.5 修 Spring Security 6.2 鉴权 bug 时, 用这个 filter 调试
 * (看 SecurityContextHolder 在 chain 前后状态, 定位是哪个 filter 改了 context).
 *
 * <p><b>V1 状态</b>: 已确认不影响功能 (不影响 V1 demo 跑通), 但保险起见
 * 默认 {@code enabled=false} (不注册). V1.5 debug 时改成 true.
 *
 * <p>作为 servlet filter (不走 Security chain), 用 {@code HIGHEST_PRECEDENCE} order.
 *
 * <p>详见 {@code docs/KNOWN_ISSUES.md}.
 */
@Slf4j
@Configuration
public class DebugFilter {

    /**
     * V1 默认 disabled (避免污染生产). V1.5 调试时改成 true.
     */
    private static final boolean ENABLED = false;

    @Bean
    public FilterRegistrationBean<DebugFilterInner> debugFilterRegistration(DebugFilterInner filter) {
        FilterRegistrationBean<DebugFilterInner> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/*");
        // 必须在 springSecurityFilterChain 之前跑 - springSecurityFilterChain 固定 order=-100
        // 我们设 order=Ordered.HIGHEST_PRECEDENCE (= Integer.MIN_VALUE)
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.setEnabled(ENABLED);
        return reg;
    }

    @org.springframework.stereotype.Component
    public static class DebugFilterInner extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            Authentication authBefore = SecurityContextHolder.getContext().getAuthentication();
            log.info(
                    "[DebugFilter] BEFORE chain uri={} authBefore={}",
                    request.getRequestURI(),
                    authBefore == null
                            ? "null"
                            : authBefore.getClass().getSimpleName() + ":" + authBefore.getPrincipal());
            try {
                filterChain.doFilter(request, response);
            } finally {
                Authentication authAfter = SecurityContextHolder.getContext().getAuthentication();
                log.info(
                        "[DebugFilter] AFTER chain uri={} authAfter={} status={}",
                        request.getRequestURI(),
                        authAfter == null
                                ? "null"
                                : authAfter.getClass().getSimpleName() + ":" + authAfter.getPrincipal(),
                        response.getStatus());
                SecurityContextHolder.clearContext();
            }
        }
    }
}
