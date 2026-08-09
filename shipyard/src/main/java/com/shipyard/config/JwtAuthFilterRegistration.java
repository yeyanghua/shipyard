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
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * JwtAuthFilter servlet 注册控制.
 *
 * <p><b>背景</b>: JwtAuthFilter 是 {@code @Component}, Spring Boot 会自动注册为 servlet filter
 * (在 {@code springSecurityFilterChain} 之前), 但 SecurityConfig 里又通过 {@code addFilterBefore}
 * 把它加到 SecurityFilterChain 内部 — 不显式禁掉会跑 2 次.
 *
 * <p><b>修法</b>: 用 {@link FilterRegistrationBean} 显式禁掉 @Component 自动注册,
 * 只走 SecurityConfig 里的 addFilterBefore 一条路径.
 *
 * <p><b>V1 状态</b>: 保留 — V1.5 修鉴权 bug 时会用到. 详见 {@code docs/KNOWN_ISSUES.md}.
 */
@Configuration
public class JwtAuthFilterRegistration {

    @Bean(name = "jwtAuthFilterServletRegistration")
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterServletRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);  // 禁掉 servlet 自动注册, 只让 SecurityConfig.addFilterBefore 跑
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }
}
