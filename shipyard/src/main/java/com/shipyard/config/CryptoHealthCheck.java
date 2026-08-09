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

import com.shipyard.service.EnvVariableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时加密健康检查 — shipyard 启动时全量校验 {@code env_variable} 加密值能否解密.
 *
 * <p><b>为什么必须有</b>: 防止"运行到一半 500" —
 * <ul>
 *   <li>密钥被改过 (envelope encryption 轮换时)</li>
 *   <li>密文被外部脚本篡改</li>
 *   <li>DB charset 转换导致密文损坏</li>
 * </ul>
 * 这些情况如果运行时才发现, 已经发布到一半了 — 业务影响大, 排查难.
 * 启动时一次性校验, 列出损坏变量, 让运维立即修, 启动失败早暴露.
 *
 * <p><b>顺序</b>: {@code @Order(0)} 最早跑, 在 ApplicationReadyEvent 之前完成 —
 * 失败抛 {@code CryptoException} → Spring 启动中止 → k8s readiness probe 失败 → pod 不接流量.
 *
 * <p><b>空库情况</b>: {@code validateAllOnStartup()} 直接 return 0, 不报错 (V1 demo 初始空库 OK).
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class CryptoHealthCheck implements ApplicationRunner {

    private final EnvVariableService envVariableService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== shipyard 启动加密健康检查 ===");
        try {
            int count = envVariableService.validateAllOnStartup();
            log.info("=== 加密健康检查通过 ({} 条 env_variable) ===", count);
        } catch (Exception e) {
            log.error("=== 加密健康检查失败, shipyard 启动中止 ===", e);
            throw e; // Spring 捕获后启动失败
        }
    }
}
