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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DiagnosisHandler 单元测试 — parseResponse JSON 解析 + 字段映射.
 */
class DiagnosisHandlerTest {

    private DiagnosisHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DiagnosisHandler(new ObjectMapper());
    }

    @Test
    @DisplayName("parseResponse: 标准 JSON 解析成 DiagnosisResult")
    void parseResponse_validJson() {
        String raw =
                """
            {
              "failedStep": "test",
              "rootCause": "assert 失败",
              "severity": "high",
              "suggestion": "检查边界条件",
              "confidence": 0.8
            }
            """;
        DiagnosisResult result =
                handler.parseResponse(raw, AiRequestContext.builder().build());
        assertThat(result.failedStep()).isEqualTo("test");
        assertThat(result.severity()).isEqualTo("high");
        assertThat(result.confidence()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("parseResponse: 清洗 markdown 围栏")
    void parseResponse_markdownFences_cleaned() {
        String raw = "```json\n{\"failedStep\":\"test\",\"rootCause\":\"x\",\"severity\":\"low\","
                + "\"suggestion\":\"y\",\"confidence\":0.5}\n```";
        DiagnosisResult result =
                handler.parseResponse(raw, AiRequestContext.builder().build());
        assertThat(result.failedStep()).isEqualTo("test");
    }

    @Test
    @DisplayName("parseResponse: 非 JSON 抛 IllegalArgumentException")
    void parseResponse_invalidJson_throws() {
        String raw = "this is not json";
        assertThatThrownBy(() ->
                        handler.parseResponse(raw, AiRequestContext.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON 解析失败");
    }

    @Test
    @DisplayName("parseResponse: 空字符串抛 IllegalArgumentException")
    void parseResponse_empty_throws() {
        assertThatThrownBy(() ->
                        handler.parseResponse("", AiRequestContext.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("响应为空");
    }
}
