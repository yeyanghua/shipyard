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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shipyard.ai.handler.AiCapabilityHandler;
import com.shipyard.ai.handler.AiRequestContext;
import com.shipyard.common.enums.AiCapability;
import com.shipyard.common.enums.LlmProvider;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.config.ShipyardAiProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * LlmService 单元测试 — 路由 + 成功/失败路径 + 落痕验证.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LlmServiceTest {

    @Mock
    private LlmAdapter mockAdapter;

    @Mock
    private LlmAdapter tongyiAdapter;

    @Mock
    private AiCapabilityHandler<String> pipelineGenHandler;

    @Mock
    private AiInteractionService aiInteractionService;

    @Mock
    private ShipyardAiProperties aiProperties;

    private LlmService service;
    private AiRequestContext ctx;

    @BeforeEach
    void setUp() {
        when(mockAdapter.provider()).thenReturn(LlmProvider.MOCK);
        when(tongyiAdapter.provider()).thenReturn(LlmProvider.TONGYI);
        when(pipelineGenHandler.capability()).thenReturn(AiCapability.PIPELINE_GEN);

        when(aiProperties.getDefaultProvider()).thenReturn(LlmProvider.MOCK);
        when(aiProperties.getDefaultModelFor(LlmProvider.MOCK)).thenReturn("v1");
        when(aiProperties.getDefaultModelFor(LlmProvider.TONGYI)).thenReturn("qwen-turbo");

        when(aiInteractionService.toJson(any())).thenReturn("<json>");

        service = new LlmService(
                List.of(mockAdapter, tongyiAdapter), List.of(pipelineGenHandler), aiInteractionService, aiProperties);
        service.init();

        ctx = AiRequestContext.builder()
                .userId("alice")
                .put("projectType", "java_maven")
                .build();
    }

    // ==================== 路由 ====================

    @Test
    @DisplayName("init: 重复 provider 抛 IllegalStateException")
    void init_duplicateProvider_throws() {
        LlmAdapter dup = org.mockito.Mockito.mock(LlmAdapter.class);
        when(dup.provider()).thenReturn(LlmProvider.MOCK); // 跟 mockAdapter 冲突

        LlmService s = new LlmService(
                List.of(mockAdapter, dup), List.of(pipelineGenHandler), aiInteractionService, aiProperties);

        assertThatThrownBy(s::init).isInstanceOf(IllegalStateException.class).hasMessageContaining("MOCK");
    }

    @Test
    @DisplayName("init: 重复 capability 抛 IllegalStateException")
    void init_duplicateCapability_throws() {
        AiCapabilityHandler<String> dup = org.mockito.Mockito.mock(AiCapabilityHandler.class);
        when(dup.capability()).thenReturn(AiCapability.PIPELINE_GEN);

        LlmService s = new LlmService(
                List.of(mockAdapter), List.of(pipelineGenHandler, dup), aiInteractionService, aiProperties);

        assertThatThrownBy(s::init).isInstanceOf(IllegalStateException.class).hasMessageContaining("PIPELINE_GEN");
    }

    // ==================== 成功路径 ====================

    @Test
    @DisplayName("call 成功: 调 adapter → 解析 → 落痕 → 返结果")
    void call_success_routesAndRecords() {
        LlmRequest req = LlmRequest.of(AiCapability.PIPELINE_GEN, "build me a pipeline");
        LlmResponse resp = LlmResponse.mock("v1", "kind: pipeline\nname: x\nsteps: []");
        when(pipelineGenHandler.buildRequest(ctx)).thenReturn(req);
        when(mockAdapter.complete(req)).thenReturn(resp);
        when(pipelineGenHandler.parseResponse("kind: pipeline\nname: x\nsteps: []", ctx))
                .thenReturn("kind: pipeline\nname: x\nsteps: []");
        when(pipelineGenHandler.describeAction(eq(ctx), anyString())).thenReturn("生成了 pipeline 模板");

        String result = service.call(AiCapability.PIPELINE_GEN, ctx);

        assertThat(result).isEqualTo("kind: pipeline\nname: x\nsteps: []");
        verify(mockAdapter, times(1)).complete(req);
        verify(pipelineGenHandler, times(1)).parseResponse("kind: pipeline\nname: x\nsteps: []", ctx);
        verify(aiInteractionService, times(1))
                .recordSuccess(
                        eq(ctx),
                        eq(AiCapability.PIPELINE_GEN),
                        eq(LlmProvider.MOCK),
                        eq("v1"),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq("生成了 pipeline 模板"));
        verify(aiInteractionService, never()).recordFailure(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("request.preferredProvider 优先于全局默认")
    void call_preferredProviderOverridesDefault() {
        LlmRequest req =
                LlmRequest.of(AiCapability.PIPELINE_GEN, "build me a pipeline").withProvider(LlmProvider.TONGYI);
        LlmResponse resp = LlmResponse.mock("qwen-turbo", "kind: pipeline");
        when(pipelineGenHandler.buildRequest(ctx)).thenReturn(req);
        when(tongyiAdapter.complete(req))
                .thenThrow(new LlmException(LlmProvider.TONGYI, LlmException.Kind.NETWORK_ERROR, "not implemented"));
        when(pipelineGenHandler.describeAction(any(), any())).thenReturn("action");

        // 期望: 走 tongyiAdapter, 抛 LlmException, 落痕 recordFailure
        assertThatThrownBy(() -> service.call(AiCapability.PIPELINE_GEN, ctx)).isInstanceOf(BusinessException.class);

        verify(tongyiAdapter, times(1)).complete(req);
        verify(mockAdapter, never()).complete(any());
    }

    // ==================== 失败路径 ====================

    @Test
    @DisplayName("adapter 抛 LlmException → 落痕 recordFailure + 抛 BusinessException(INTERNAL_ERROR)")
    void call_llmException_mapsToBusinessException() {
        LlmRequest req = LlmRequest.of(AiCapability.PIPELINE_GEN, "x");
        when(pipelineGenHandler.buildRequest(ctx)).thenReturn(req);
        when(mockAdapter.complete(req))
                .thenThrow(new LlmException(LlmProvider.MOCK, LlmException.Kind.NETWORK_ERROR, "网络挂了"));

        assertThatThrownBy(() -> service.call(AiCapability.PIPELINE_GEN, ctx))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                });

        verify(aiInteractionService, times(1))
                .recordFailure(
                        eq(ctx),
                        eq(AiCapability.PIPELINE_GEN),
                        eq(LlmProvider.MOCK),
                        eq("v1"),
                        anyString(),
                        anyString(),
                        any());
    }

    @Test
    @DisplayName("adapter 抛 AUTH_FAILED → 落痕 + INTERNAL_ERROR (配置问题, 不该让用户重试)")
    void call_authFailed_mapsToInternalError() {
        LlmRequest req = LlmRequest.of(AiCapability.PIPELINE_GEN, "x");
        when(pipelineGenHandler.buildRequest(ctx)).thenReturn(req);
        when(mockAdapter.complete(req))
                .thenThrow(new LlmException(LlmProvider.MOCK, LlmException.Kind.AUTH_FAILED, "401"));

        assertThatThrownBy(() -> service.call(AiCapability.PIPELINE_GEN, ctx))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    @DisplayName("parseResponse 抛 IllegalArgumentException → 落痕 + BAD_REQUEST")
    void call_parseFailure_mapsToBadRequest() {
        LlmRequest req = LlmRequest.of(AiCapability.PIPELINE_GEN, "x");
        LlmResponse resp = LlmResponse.mock("v1", "malformed response");
        when(pipelineGenHandler.buildRequest(ctx)).thenReturn(req);
        when(mockAdapter.complete(req)).thenReturn(resp);
        when(pipelineGenHandler.parseResponse("malformed response", ctx))
                .thenThrow(new IllegalArgumentException("缺少 kind: pipeline"));

        assertThatThrownBy(() -> service.call(AiCapability.PIPELINE_GEN, ctx))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(aiInteractionService, times(1))
                .recordFailure(
                        eq(ctx),
                        eq(AiCapability.PIPELINE_GEN),
                        eq(LlmProvider.MOCK),
                        eq("v1"),
                        anyString(),
                        anyString(),
                        any());
    }

    @Test
    @DisplayName("未知 capability → INTERNAL_ERROR (没落痕, 因为还没进 LLM 调用)")
    void call_unknownCapability_throwsImmediately() {
        assertThatThrownBy(() -> service.call(AiCapability.DIAGNOSIS, ctx))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));

        verify(mockAdapter, never()).complete(any());
        verify(aiInteractionService, never()).recordFailure(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("buildRequest 抛 RuntimeException → 落痕 + INTERNAL_ERROR")
    void call_buildRequestFailure_mapsToInternalError() {
        when(pipelineGenHandler.buildRequest(ctx)).thenThrow(new IllegalStateException("project 不能为空"));

        assertThatThrownBy(() -> service.call(AiCapability.PIPELINE_GEN, ctx))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(be.getMessage()).contains("拼装 prompt 失败");
                });

        // buildRequest 失败时还没生成 rawRequest, 不落痕 (无法归类到具体 provider/model)
        verify(aiInteractionService, never()).recordFailure(any(), any(), any(), any(), any(), any(), any());
        verify(mockAdapter, never()).complete(any());
    }
}
