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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipyard.ai.handler.AiRequestContext;
import com.shipyard.common.enums.AiCapability;
import com.shipyard.common.enums.LlmProvider;
import com.shipyard.entity.AiInteraction;
import com.shipyard.mapper.AiInteractionMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 交互流水服务 — 所有 LLM 调用的落痕统一走这里.
 *
 * <p>调用流程: LlmService 在调 adapter 之前/之后都调这个 service 写一行, 包含
 * <ul>
 *   <li>request 上下文 (脱敏后的 prompt + raw LLM request JSON)</li>
 *   <li>response 完整内容 (raw LLM response JSON)</li>
 *   <li>失败时只记 error message, response 字段为 null</li>
 * </ul>
 *
 * <p>不可变: 落库后永不修改, 业务代码不要尝试 update / delete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInteractionService {

    private final AiInteractionMapper aiInteractionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 落 LLM 调用成功记录.
     *
     * @param ctx           业务 context (userId / projectId 从这取)
     * @param capability    调用的 capability
     * @param provider      实际调用的 provider
     * @param model         实际调用的 model
     * @param rawPrompt     完整 prompt (会脱敏)
     * @param rawRequest    LLM request JSON 字符串 (含 system prompt + user prompt + params)
     * @param rawResponse   LLM response JSON 字符串 (含 content + usage + finish_reason)
     * @param outputAction  业务动作描述 (handler.describeAction() 生成)
     */
    public void recordSuccess(
            AiRequestContext ctx,
            AiCapability capability,
            LlmProvider provider,
            String model,
            String rawPrompt,
            String rawRequest,
            String rawResponse,
            String outputAction) {
        AiInteraction entity = new AiInteraction();
        entity.setUserId(resolveUserId(ctx));
        entity.setCapability(capability.getValue());
        entity.setInputPrompt(PromptSanitizer.sanitize(rawPrompt));
        entity.setLlmProvider(provider.getValue());
        entity.setLlmModel(model);
        entity.setLlmRequest(rawRequest);
        entity.setLlmResponse(rawResponse);
        entity.setOutputAction(outputAction);

        aiInteractionMapper.insert(entity);
        log.debug("[AiInteraction] 落痕成功 capability={} provider={} id={}", capability, provider, entity.getId());
    }

    /**
     * 落 LLM 调用失败记录 (response 字段为 null, action 描述失败原因).
     */
    public void recordFailure(
            AiRequestContext ctx,
            AiCapability capability,
            LlmProvider provider,
            String model,
            String rawPrompt,
            String rawRequest,
            Throwable error) {
        AiInteraction entity = new AiInteraction();
        entity.setUserId(resolveUserId(ctx));
        entity.setCapability(capability.getValue());
        entity.setInputPrompt(PromptSanitizer.sanitize(rawPrompt));
        entity.setLlmProvider(provider.getValue());
        entity.setLlmModel(model);
        entity.setLlmRequest(rawRequest);
        entity.setLlmResponse(null); // 失败没响应
        entity.setOutputAction(buildFailureAction(capability, error));

        aiInteractionMapper.insert(entity);
        log.warn(
                "[AiInteraction] 落痕失败 capability={} provider={} id={} err={}",
                capability,
                provider,
                entity.getId(),
                error.toString());
    }

    /**
     * 把对象序列化成 JSON 字符串, 给 {@code llmRequest} / {@code llmResponse} 字段用.
     *
     * <p>序列化失败时返 {@code "<serialize-error: msg>"}, 不抛 (落痕路径不能反过来把落痕本身搞崩).
     */
    public String toJson(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "<serialize-error: " + e.getMessage() + ">";
        }
    }

    /**
     * 从 ctx 拿 userId, 拿不到时返 "unknown".
     */
    private String resolveUserId(AiRequestContext ctx) {
        if (ctx == null) return "unknown";
        if (ctx.userId() != null && !ctx.userId().isBlank()) {
            return ctx.userId();
        }
        return "unknown";
    }

    /**
     * 失败时的 action 描述 — 简短一句, 不含堆栈 (堆栈进日志, 不进 DB).
     */
    private String buildFailureAction(AiCapability capability, Throwable error) {
        String kind = error instanceof LlmException le ? le.getKind().name() : "UNKNOWN";
        return String.format("%s 失败: %s (%s)", capability.getValue(), error.getMessage(), kind);
    }

    /**
     * 暴露给测试 / 上层用 — {@link PromptSanitizer} 包装方法, 便于 mock.
     */
    public String sanitizeForTest(String s) {
        return PromptSanitizer.sanitize(s);
    }

    /**
     * 给上层 (LlmService) 用 — 拿 ctx.extras 转 JSON (供 llmRequest 字段存).
     *
     * <p>不是业务方法, 但 LlmService 不想直接 import ObjectMapper 跟 JsonProcessingException
     * 的话, 可以走这个简化包装.
     */
    public String extrasToJson(Map<String, Object> extras) {
        return toJson(extras);
    }
}
