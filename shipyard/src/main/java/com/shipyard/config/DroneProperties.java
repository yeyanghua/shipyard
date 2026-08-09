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
 * drone CI 集成配置 — 跟 {@code shipyard.drone.*} 对应.
 *
 * <p>V1 demo 用 Mock drone, shipyard 端这套配置全部都生效 (流程跑通);
 * V1.5 接真实 drone 时换实现, 配置项不变.
 */
@Data
@ConfigurationProperties(prefix = "shipyard.drone")
public class DroneProperties {

    /**
     * drone webhook HMAC-SHA256 验签 secret.
     *
     * <p>drone 端在每个 webhook 回调 {@code X-Drone-Signature} 头里塞 HMAC(body, secret),
     * shipyard 用同一 secret 重算, 一致才放行.
     *
     * <p>V1 demo 用 dev 默认值; V1.5 真实 drone 时改 {@code SHIPYARD_DRONE_WEBHOOK_SECRET} env var.
     */
    private String webhookSecret;

    /**
     * Mock 模式开关 — {@code true} 时用 {@code MockDroneClient} (本地异步模拟),
     * {@code false} 时用 {@code RealDroneClient} (调真实 drone REST API, V1.5 实现).
     */
    private boolean mockEnabled = true;

    /**
     * Mock 模式下单个 step 模拟耗时 (毫秒) — demo 看到实时日志流.
     */
    private long mockStepDelayMs = 1500;
}
