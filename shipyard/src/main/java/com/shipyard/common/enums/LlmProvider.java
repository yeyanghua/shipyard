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

package com.shipyard.common.enums;

import lombok.Getter;

/**
 * LLM 提供方 — 跟 {@code ai_interaction.llm_provider} 字段对应.
 *
 * <p>shipyard V1 支持 3 个 LLM provider:
 * <ul>
 *   <li>{@link #MOCK}    — 离线 canned data, 单元测试 + V1 demo 默认</li>
 *   <li>{@link #TONGYI}  — 阿里云通义千问 (DashScope SDK), 需要 {@code TONGYI_API_KEY}</li>
 *   <li>{@link #DEEPSEEK} — DeepSeek, 兼容 OpenAI API 协议, 需要 {@code DEEPSEEK_API_KEY}</li>
 * </ul>
 *
 * <p>V1.5 可加 {@code OPENAI} / {@code CLAUDE} / 自托管 (vLLM + OpenAI 协议).
 *
 * <p>3 个 provider 都实现 {@code LlmAdapter} 接口, 区别仅在 HTTP client 配置和鉴权方式.
 */
@Getter
public enum LlmProvider {

    /** Mock provider — 单元测试 + V1 demo 默认, 无需 env var */
    MOCK("mock", null),

    /** 阿里云通义千问 (DashScope SDK) */
    TONGYI("tongyi", "TONGYI_API_KEY"),

    /** DeepSeek (OpenAI 协议兼容) */
    DEEPSEEK("deepseek", "DEEPSEEK_API_KEY");

    /**
     * 字符串值 — 跟 {@code ai_interaction.llm_provider} 字段对应.
     * 用小写, 跟 {@link AiCapability} 风格一致.
     */
    private final String value;

    /**
     * 必填 env var 名 — null 表示不需要 (mock).
     *
     * <p>启动时如果选了真实 provider 但没配 env var, 直接 fail-fast 启动失败,
     * 比让请求时 401 / 500 友好.
     */
    private final String envVar;

    LlmProvider(String value, String envVar) {
        this.value = value;
        this.envVar = envVar;
    }

    /**
     * 从字符串反查 — 大小写不敏感.
     *
     * @throws IllegalArgumentException 找不到时抛
     */
    public static LlmProvider fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("LlmProvider value 不能为 null");
        }
        for (LlmProvider p : values()) {
            if (p.value.equalsIgnoreCase(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("未知的 LlmProvider: " + value);
    }
}
