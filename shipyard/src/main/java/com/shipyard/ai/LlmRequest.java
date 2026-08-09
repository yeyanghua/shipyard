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

import com.shipyard.common.enums.AiCapability;
import com.shipyard.common.enums.LlmProvider;
import java.util.Map;

/**
 * LLM 调用请求 — 统一的输入 DTO, 跨 provider / capability.
 *
 * <p>设计原则:
 * <ul>
 *   <li><b>不绑定 provider 协议</b> — Mock / Tongyi / DeepSeek adapter 各自把 {@code LlmRequest} 转成自家 HTTP body</li>
 *   <li><b>capability 必填</b> — adapter 可以按 capability 选不同 prompt 模板 (V1 不实现, 预留)</li>
 *   <li><b>context</b> — 业务侧额外参数 (例: pipeline_gen 的 project 元数据, diagnosis 的 build log 片段), adapter 自己挑用</li>
 * </ul>
 *
 * <p>必填字段: {@link #capability} + {@link #userPrompt} (system prompt 可选, 大多数情况 user prompt 已经包含全部).
 */
public record LlmRequest(
        AiCapability capability,
        String userPrompt,
        String systemPrompt,
        String model, // null = 走 provider 默认 model
        LlmProvider preferredProvider, // null = 走全局默认 provider
        Map<String, Object> context // 业务参数
        ) {

    /**
     * 简化的 builder — 业务代码不用每次都填 6 个字段.
     *
     * <p>用法: {@code LlmRequest.of(AiCapability.PIPELINE_GEN, prompt).withContext(map)}
     */
    public static LlmRequest of(AiCapability capability, String userPrompt) {
        return new LlmRequest(capability, userPrompt, null, null, null, null);
    }

    /**
     * 加 system prompt.
     */
    public LlmRequest withSystemPrompt(String systemPrompt) {
        return new LlmRequest(capability, userPrompt, systemPrompt, model, preferredProvider, context);
    }

    /**
     * 加 model.
     */
    public LlmRequest withModel(String model) {
        return new LlmRequest(capability, userPrompt, systemPrompt, model, preferredProvider, context);
    }

    /**
     * 加 preferred provider — 单次覆盖全局默认.
     */
    public LlmRequest withProvider(LlmProvider provider) {
        return new LlmRequest(capability, userPrompt, systemPrompt, model, provider, context);
    }

    /**
     * 加 context — 业务侧参数.
     */
    public LlmRequest withContext(Map<String, Object> context) {
        return new LlmRequest(capability, userPrompt, systemPrompt, model, preferredProvider, context);
    }
}
