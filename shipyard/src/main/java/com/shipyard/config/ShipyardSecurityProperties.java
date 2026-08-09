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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * shipyard 安全配置 - V1 demo 模式开关.
 *
 * <p>对应 {@code shipyard.security.*} 配置. 用法:
 * <pre>{@code
 * shipyard:
 *   security:
 *     demo-mode: true   # V1 默认; V1.5 后改成 false
 * }</pre>
 *
 * <p>详见 {@link SecurityConfig} 类注释.
 */
@Data
@ConfigurationProperties(prefix = "shipyard.security")
public class ShipyardSecurityProperties {

    /**
     * V1 demo 模式开关.
     *
     * <p>{@code true} (V1 默认): 所有 API permitAll, JwtAuthFilter 仅解析 JWT 不强制鉴权.
     * <br>{@code false} (V1.5+): 等 Spring Security 6.2 鉴权 bug 修好才能真正生效.
     */
    private boolean demoMode = true;
}
