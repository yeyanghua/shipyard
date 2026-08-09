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

import com.shipyard.common.enums.LlmProvider;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * shipyard AI 配置 — 跟 {@code shipyard.ai.*} 对应.
 *
 * <p>用法:
 * <pre>{@code
 * shipyard:
 *   ai:
 *     default-provider: mock     # mock / tongyi / deepseek
 *     mock:
 *       model: v1                # mock 内部版本号, V1 demo
 *     tongyi:
 *       model: qwen-turbo        # 通义千问模型名
 *     deepseek:
 *       model: deepseek-chat     # DeepSeek 模型名
 * }</pre>
 *
 * <p>V1 demo 默认走 mock, 无需 env var. 接真实 LLM 时:
 * <ol>
 *   <li>配 env var: {@code TONGYI_API_KEY} / {@code DEEPSEEK_API_KEY}</li>
 *   <li>改 {@code default-provider} 到对应 provider</li>
 * </ol>
 *
 * <p>启动时 {@link #validate()} 会 fail-fast 检查: 如果选真实 provider 但 env var 没配,
 * 直接启动失败 (比请求时 401 友好).
 */
@Data
@ConfigurationProperties(prefix = "shipyard.ai")
@Slf4j
public class ShipyardAiProperties {

    /**
     * 默认 LLM provider — V1 demo 默认 {@code mock}.
     *
     * <p>运行时也可以通过 {@code AiRequest.provider} 单次覆盖.
     */
    private LlmProvider defaultProvider = LlmProvider.MOCK;

    /** Mock provider 配置 */
    private Mock mock = new Mock();

    /** 通义千问 provider 配置 */
    private Tongyi tongyi = new Tongyi();

    /** DeepSeek provider 配置 */
    private Deepseek deepseek = new Deepseek();

    @Data
    public static class Mock {
        /** Mock 模型名 — V1 写死 "v1", 用来在 ai_interaction 表留痕 */
        private String model = "v1";
    }

    @Data
    public static class Tongyi {
        /** 通义千问模型名 (DashScope SDK) */
        private String model = "qwen-turbo";
    }

    @Data
    public static class Deepseek {
        /** DeepSeek 模型名 (OpenAI 协议兼容) */
        private String model = "deepseek-chat";
    }

    /**
     * 启动校验 — 检查 default-provider 选真实时 env var 是否配了.
     *
     * <p>用 {@code @PostConstruct} 而不是 {@code @Value} 注入, 这样失败时机在
     * 启动阶段, 不是第一次 LLM 调用时.
     */
    @PostConstruct
    public void validate() {
        log.info("[AI] default-provider={}", defaultProvider);
        if (defaultProvider == LlmProvider.MOCK) {
            log.info("[AI] 走 mock, 无需 env var");
            return;
        }
        String envVar = defaultProvider.getEnvVar();
        String envValue = System.getenv(envVar);
        if (envValue == null || envValue.isBlank()) {
            throw new IllegalStateException("shipyard.ai.default-provider=" + defaultProvider.getValue()
                    + " 但环境变量 " + envVar + " 未配置. "
                    + "要切换到真实 LLM 请先配 " + envVar + " env var, "
                    + "或把 default-provider 改回 'mock'.");
        }
        log.info(
                "[AI] 走真实 provider={} envVar={} 已配置 (length={})",
                defaultProvider.getValue(),
                envVar,
                envValue.length());
    }

    /**
     * 按 provider 拿默认模型名.
     */
    public String getDefaultModelFor(LlmProvider provider) {
        return switch (provider) {
            case MOCK -> mock.getModel();
            case TONGYI -> tongyi.getModel();
            case DEEPSEEK -> deepseek.getModel();
        };
    }
}
