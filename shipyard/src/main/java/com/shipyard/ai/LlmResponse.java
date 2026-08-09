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

package com.shipyard.ai;

import com.shipyard.common.enums.LlmProvider;

/**
 * LLM 调用响应 — 跨 provider 统一的输出.
 *
 * <p>关键字段:
 * <ul>
 *   <li>{@code content} — provider 原始文本输出 (Tongyi/DeepSeek 走 OpenAI 协议的 {@code choices[0].message.content})</li>
 *   <li>{@code provider} / {@code model} — 实际响应的 provider + model (落痕用)</li>
 *   <li>{@code promptTokens} / {@code completionTokens} — 真实 provider 返的, mock 是估算</li>
 *   <li>{@code latencyMs} — 调 adapter 实测耗时 (mock 设 0, 真实 LLM 几百到几千 ms)</li>
 * </ul>
 *
 * <p>业务侧 (例 pipeline_gen) 还要把 {@code content} (YAML 文本) 解析成结构化对象,
 * 解析在 {@code AiCapabilityHandler} 里做, adapter 只负责拿 raw text.
 */
public record LlmResponse(
        LlmProvider provider,
        String model,
        String content,
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs) {

    /**
     * Mock 响应的便捷构造 — token 数 0, 延迟 0.
     */
    public static LlmResponse mock(String model, String content) {
        return new LlmResponse(LlmProvider.MOCK, model, content, 0, 0, 0L);
    }
}
