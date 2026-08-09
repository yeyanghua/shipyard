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

package com.shipyard.ai.handler;

import com.shipyard.ai.LlmRequest;
import com.shipyard.common.enums.AiCapability;

/**
 * AI capability 处理器 — 把 {@code AiCapability} (pipeline_gen / diagnosis / decision)
 * 拆成 3 个独立 handler, 每个管自己的 prompt 拼装 + response 解析.
 *
 * <p>调用方 ({@code LlmService}) 不用知道每个 capability 怎么拼 prompt, 只调:
 * <ol>
 *   <li>{@link #buildRequest(AiRequestContext)} — 业务 ctx → {@link LlmRequest}</li>
 *   <li>调 LLM 拿 raw text</li>
 *   <li>{@link #parseResponse(String, AiRequestContext)} — raw text → 结构化结果 R</li>
 *   <li>{@link #describeAction(AiRequestContext, Object)} — 自然语言描述, 落 {@code ai_interaction.output_action}</li>
 * </ol>
 *
 * <p>泛型 {@code R} 是结构化结果的类型, 每个 capability 不同:
 * <ul>
 *   <li>{@code pipeline_gen} → {@code String} (YAML 内容, 直接写进 pipeline_template.yaml_content)</li>
 *   <li>{@code diagnosis}    → {@code DiagnosisResult} (root_cause + suggestion)</li>
 *   <li>{@code decision}     → {@code DecisionResult} (recommendation + reason)</li>
 * </ul>
 */
public interface AiCapabilityHandler<R> {

    /**
     * 自报 capability — 用于 LlmService 路由.
     */
    AiCapability capability();

    /**
     * 业务 ctx 拼装 LLM 请求.
     *
     * <p>包含 system prompt + user prompt, 加 context 进 LlmRequest.withContext().
     */
    LlmRequest buildRequest(AiRequestContext ctx);

    /**
     * LLM 响应 raw text 解析成结构化结果.
     *
     * @param rawText provider 返的原始文本 (Mock 是固定 canned; 真实 LLM 是模型输出)
     * @param ctx     同一个 ctx, 解析时可能要回查 (例: pipeline_gen 解析失败时回退到默认 YAML)
     * @return 结构化结果
     * @throws IllegalArgumentException 解析失败抛 (LlmService 接住, 落痕后转 BusinessException)
     */
    R parseResponse(String rawText, AiRequestContext ctx);

    /**
     * 输出动作描述 — 自然语言, 写进 {@code ai_interaction.output_action}.
     *
     * <p>业务侧: 让人在 UI 上能看懂"AI 这次到底干了啥". 例:
     * <ul>
     *   <li>{@code "为 project 5 创建 pipeline v3 (draft, java_maven 标准模板)"}</li>
     *   <li>{@code "诊断 build 12 失败: 根因是 test 步骤 assert 失败, 建议..."}</li>
     *   <li>{@code "决策 build 12: 建议手动发布 (p50 耗时超阈值 1.5x)"}</li>
     * </ul>
     */
    String describeAction(AiRequestContext ctx, R result);
}
