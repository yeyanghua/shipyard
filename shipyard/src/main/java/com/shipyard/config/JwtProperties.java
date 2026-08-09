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

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * shipyard JWT 配置 - 用 @ConfigurationProperties 绑 shipyard.jwt.* 配置段.
 *
 * <p><b>为什么不用 @Value</b>:
 * <ul>
 *   <li>{@code @Value("${shipyard.jwt.whitelist}")} 拿不到 YAML list —
 *       Spring 期望 "value1,value2" 字符串, YAML list 解析失败</li>
 *   <li>{@code @ConfigurationProperties} 是 Spring Boot 推荐的
 *       类型安全配置绑法,自动处理 list/map/嵌套</li>
 *   <li>所有 jwt 相关配置集中一处,加新字段不用改多个类</li>
 * </ul>
 *
 * <p><b>对应 application.yml 段</b>:
 * <pre>{@code
 * shipyard:
 *   jwt:
 *     secret: ${SHIPYARD_JWT_SECRET:change-me}
 *     expiration-seconds: 86400
 *     issuer: shipyard
 *     whitelist:
 *       - /api/auth/**
 *       - /actuator/**
 *       - /webhook/drone
 *       - /api/health
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "shipyard.jwt")
public class JwtProperties {

    /**
     * JWT 签名密钥 (HS256/384/512 算法).
     * 生产环境必须通过 SHIPYARD_JWT_SECRET 环境变量覆盖,绝不能用默认值.
     */
    private String secret;

    /**
     * Token 有效期 (秒). 默认 24 小时.
     */
    private int expirationSeconds = 86400;

    /**
     * JWT 签发者, 用于校验 iss claim.
     */
    private String issuer = "shipyard";

    /**
     * 不需要 JWT 认证的 path 列表 (白名单).
     * Spring Security 的 AntPathRequestMatcher 支持 ** 通配符.
     */
    private List<String> whitelist = List.of();
}
