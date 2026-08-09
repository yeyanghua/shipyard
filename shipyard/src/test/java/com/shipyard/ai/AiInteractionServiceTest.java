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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipyard.ai.handler.AiRequestContext;
import com.shipyard.common.enums.AiCapability;
import com.shipyard.common.enums.LlmProvider;
import com.shipyard.entity.AiInteraction;
import com.shipyard.mapper.AiInteractionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * AiInteractionService 单元测试 — 落痕字段正确性 + 脱敏.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiInteractionServiceTest {

    @Mock
    private AiInteractionMapper aiInteractionMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiInteractionService service;

    @Test
    @DisplayName("recordSuccess: 所有字段正确填, prompt 脱敏, mapper.insert 调用 1 次")
    void recordSuccess_allFieldsCorrect() {
        AiRequestContext ctx =
                AiRequestContext.builder().userId("alice").put("projectId", 5L).build();

        service.recordSuccess(
                ctx,
                AiCapability.PIPELINE_GEN,
                LlmProvider.MOCK,
                "v1",
                "Deploy with password=secret123 to harbor",
                "<raw request json>",
                "<raw response json>",
                "生成了 pipeline");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionMapper, times(1)).insert(captor.capture());
        AiInteraction entity = captor.getValue();

        assertThat(entity.getUserId()).isEqualTo("alice");
        assertThat(entity.getCapability()).isEqualTo("pipeline_gen");
        assertThat(entity.getLlmProvider()).isEqualTo("mock");
        assertThat(entity.getLlmModel()).isEqualTo("v1");
        assertThat(entity.getLlmRequest()).isEqualTo("<raw request json>");
        assertThat(entity.getLlmResponse()).isEqualTo("<raw response json>");
        assertThat(entity.getOutputAction()).isEqualTo("生成了 pipeline");
        // prompt 脱敏: secret123 替换成 ***
        assertThat(entity.getInputPrompt()).contains("***");
        assertThat(entity.getInputPrompt()).doesNotContain("secret123");
    }

    @Test
    @DisplayName("recordFailure: response 字段为 null, action 描述失败原因")
    void recordFailure_responseNullActionDescribes() {
        AiRequestContext ctx = AiRequestContext.builder().userId("alice").build();

        service.recordFailure(
                ctx,
                AiCapability.DIAGNOSIS,
                LlmProvider.TONGYI,
                "qwen-turbo",
                "diagnose this",
                "<req>",
                new LlmException(LlmProvider.TONGYI, LlmException.Kind.NETWORK_ERROR, "网络挂"));

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionMapper, times(1)).insert(captor.capture());
        AiInteraction entity = captor.getValue();

        assertThat(entity.getCapability()).isEqualTo("diagnosis");
        assertThat(entity.getLlmProvider()).isEqualTo("tongyi");
        assertThat(entity.getLlmResponse()).isNull(); // 失败没响应
        assertThat(entity.getOutputAction()).contains("diagnosis 失败");
        assertThat(entity.getOutputAction()).contains("网络挂");
        assertThat(entity.getOutputAction()).contains("NETWORK_ERROR");
    }

    @Test
    @DisplayName("userId 缺失时, 默认 'unknown'")
    void recordSuccess_missingUserId_defaultsToUnknown() {
        AiRequestContext ctx = AiRequestContext.builder().build();

        service.recordSuccess(ctx, AiCapability.PIPELINE_GEN, LlmProvider.MOCK, "v1", "x", "x", "x", "x");

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(aiInteractionMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("sanitizeForTest: 包装 PromptSanitizer.sanitize")
    void sanitizeForTest_delegatesToPromptSanitizer() {
        String result = service.sanitizeForTest("token=abc123");
        assertThat(result).contains("***").doesNotContain("abc123");
    }

    @Test
    @DisplayName("toJson: 正常对象序列化成 JSON")
    void toJson_normalObject() {
        String json = service.toJson(java.util.Map.of("key", "value"));
        assertThat(json).contains("\"key\"").contains("\"value\"");
    }

    @Test
    @DisplayName("toJson: null 返 null")
    void toJson_nullReturnsNull() {
        assertThat(service.toJson(null)).isNull();
    }
}
