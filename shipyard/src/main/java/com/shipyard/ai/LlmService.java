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

import com.shipyard.ai.handler.AiCapabilityHandler;
import com.shipyard.ai.handler.AiRequestContext;
import com.shipyard.common.enums.AiCapability;
import com.shipyard.common.enums.LlmProvider;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.config.ShipyardAiProperties;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LLM 业务入口 — 业务代码调这个, 不直接接触 adapter / handler.
 *
 * <p>调用流程:
 * <ol>
 *   <li>按 {@code capability} 路由到 {@link AiCapabilityHandler}</li>
 *   <li>调 {@code handler.buildRequest(ctx)} 拿 {@link LlmRequest}</li>
 *   <li>按 provider 路由到 {@link LlmAdapter} (request 优先 > 全局默认)</li>
 *   <li>调 {@code adapter.complete(request)} 拿 {@link LlmResponse} (失败抛 {@link LlmException})</li>
 *   <li>调 {@code handler.parseResponse(content, ctx)} 拿结构化结果 R</li>
 *   <li>调 {@code handler.describeAction(ctx, result)} 拿 action 描述</li>
 *   <li>调 {@code AiInteractionService.recordSuccess} 落痕</li>
 *   <li>返 R</li>
 * </ol>
 *
 * <p>异常路径: 任意步骤失败都落痕 {@code recordFailure}, 然后包成 {@link BusinessException} 抛给 Controller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private final List<LlmAdapter> adapters;
    private final List<AiCapabilityHandler<?>> handlers;
    private final AiInteractionService aiInteractionService;
    private final ShipyardAiProperties aiProperties;

    private final Map<LlmProvider, LlmAdapter> adapterByProvider = new EnumMap<>(LlmProvider.class);
    private final Map<AiCapability, AiCapabilityHandler<?>> handlerByCapability = new EnumMap<>(AiCapability.class);

    /**
     * 启动时建立索引 — adapter / handler 都是 Spring bean, 启动就能拿到.
     */
    @PostConstruct
    public void init() {
        for (LlmAdapter adapter : adapters) {
            LlmAdapter prev = adapterByProvider.put(adapter.provider(), adapter);
            if (prev != null) {
                throw new IllegalStateException("LlmProvider " + adapter.provider() + " 有多个 adapter: "
                        + prev.getClass().getName() + " vs "
                        + adapter.getClass().getName());
            }
        }
        for (AiCapabilityHandler<?> handler : handlers) {
            AiCapabilityHandler<?> prev = handlerByCapability.put(handler.capability(), handler);
            if (prev != null) {
                throw new IllegalStateException("AiCapability " + handler.capability() + " 有多个 handler: "
                        + prev.getClass().getName() + " vs "
                        + handler.getClass().getName());
            }
        }
        log.info(
                "[LlmService] init 完成: providers={}, handlers={}",
                adapterByProvider.keySet(),
                handlerByCapability.keySet());
    }

    /**
     * 调 LLM — 业务代码入口.
     *
     * @param capability 调用的 capability
     * @param ctx        业务 context
     * @return 结构化结果 (handler 决定类型: pipeline_gen → String YAML, diagnosis → DiagnosisResult, ...)
     * @throws BusinessException 任何步骤失败都包成 BAD_REQUEST 或 INTERNAL_ERROR 抛
     */
    @SuppressWarnings("unchecked")
    public <R> R call(AiCapability capability, AiRequestContext ctx) {
        // 1) 路由 handler
        AiCapabilityHandler<R> handler = (AiCapabilityHandler<R>) handlerByCapability.get(capability);
        if (handler == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未找到 capability handler: " + capability);
        }

        // 2) 拼装 LlmRequest
        LlmRequest request;
        try {
            request = handler.buildRequest(ctx);
        } catch (RuntimeException e) {
            log.error("[LlmService] buildRequest 失败 capability={}", capability, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "拼装 prompt 失败: " + e.getMessage(), e);
        }

        // 3) 路由 adapter
        LlmProvider provider =
                request.preferredProvider() != null ? request.preferredProvider() : aiProperties.getDefaultProvider();
        LlmAdapter adapter = adapterByProvider.get(provider);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未找到 LlmProvider adapter: " + provider);
        }

        // model 兜底: request 没填就用 provider 默认
        String model = request.model() != null ? request.model() : aiProperties.getDefaultModelFor(provider);

        // 4) 调 LLM + 5) 解析 + 6) describe + 7) 落痕
        String rawRequestJson = aiInteractionService.toJson(request);
        try {
            LlmResponse response = adapter.complete(request);
            R result = handler.parseResponse(response.content(), ctx);
            String action = handler.describeAction(ctx, result);
            aiInteractionService.recordSuccess(
                    ctx,
                    capability,
                    provider,
                    model,
                    request.userPrompt(),
                    rawRequestJson,
                    aiInteractionService.toJson(response),
                    action);
            log.info(
                    "[LlmService] 调成功 capability={} provider={} model={} resultType={}",
                    capability,
                    provider,
                    model,
                    result.getClass().getSimpleName());
            return result;
        } catch (LlmException e) {
            aiInteractionService.recordFailure(
                    ctx, capability, provider, model, request.userPrompt(), rawRequestJson, e);
            log.warn(
                    "[LlmService] LLM 调失败 capability={} provider={} kind={} msg={}",
                    capability,
                    provider,
                    e.getKind(),
                    e.getMessage());
            throw mapLlmException(e);
        } catch (IllegalArgumentException e) {
            // parseResponse 失败 — LLM 返了非法响应, 算客户端能 retry 的错
            aiInteractionService.recordFailure(
                    ctx, capability, provider, model, request.userPrompt(), rawRequestJson, e);
            log.warn("[LlmService] LLM 响应解析失败 capability={} provider={} msg={}", capability, provider, e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM 响应解析失败: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            aiInteractionService.recordFailure(
                    ctx, capability, provider, model, request.userPrompt(), rawRequestJson, e);
            log.error("[LlmService] 未知异常 capability={} provider={}", capability, provider, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LLM 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * LlmException → BusinessException 映射.
     *
     * <p>按 kind 分类:
     * <ul>
     *   <li>AUTH_FAILED    → INTERNAL_ERROR (配置问题, 不该让用户重试)</li>
     *   <li>RATE_LIMITED   → INTERNAL_ERROR (业务侧目前不暴露重试)</li>
     *   <li>NETWORK_ERROR  → INTERNAL_ERROR (同 rate limited)</li>
     *   <li>INVALID_RESPONSE → BAD_REQUEST (LLM 返了 malformed, 业务重试可能 OK)</li>
     *   <li>UNKNOWN        → INTERNAL_ERROR</li>
     * </ul>
     */
    private BusinessException mapLlmException(LlmException e) {
        return switch (e.getKind()) {
            case AUTH_FAILED -> new BusinessException(ErrorCode.INTERNAL_ERROR, "LLM 鉴权失败: " + e.getMessage(), e);
            case RATE_LIMITED, NETWORK_ERROR -> new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "LLM 调用失败: " + e.getMessage(), e);
            case INVALID_RESPONSE -> new BusinessException(ErrorCode.BAD_REQUEST, "LLM 响应格式异常: " + e.getMessage(), e);
            case UNKNOWN -> new BusinessException(ErrorCode.INTERNAL_ERROR, "LLM 调用未知错误: " + e.getMessage(), e);
        };
    }
}
