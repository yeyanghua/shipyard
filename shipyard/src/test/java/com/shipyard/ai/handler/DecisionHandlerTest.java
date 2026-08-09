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
 * DecisionHandler 单元测试 — JSON 解析 + recommendation 白名单校验.
 */
class DecisionHandlerTest {

    private DecisionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DecisionHandler(new ObjectMapper());
    }

    @Test
    @DisplayName("parseResponse: go 决策解析成功")
    void parseResponse_go() {
        String raw =
                """
            {"recommendation":"go","reason":"build 成功","confidence":0.8,"riskFactors":["first deploy"]}
            """;
        DecisionResult r = handler.parseResponse(raw, AiRequestContext.builder().build());
        assertThat(r.recommendation()).isEqualTo("go");
        assertThat(r.riskFactors()).containsExactly("first deploy");
    }

    @Test
    @DisplayName("parseResponse: hold 决策解析成功")
    void parseResponse_hold() {
        String raw =
                """
            {"recommendation":"hold","reason":"耗时超阈值","confidence":0.6,"riskFactors":[]}
            """;
        DecisionResult r = handler.parseResponse(raw, AiRequestContext.builder().build());
        assertThat(r.recommendation()).isEqualTo("hold");
    }

    @Test
    @DisplayName("parseResponse: 非法 recommendation 抛 IllegalArgumentException")
    void parseResponse_invalidRecommendation_throws() {
        String raw =
                """
            {"recommendation":"MAYBE","reason":"x","confidence":0.5,"riskFactors":[]}
            """;
        assertThatThrownBy(() ->
                        handler.parseResponse(raw, AiRequestContext.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("go/hold/rollback");
    }

    @Test
    @DisplayName("parseResponse: 清洗 markdown 围栏")
    void parseResponse_markdownFences_cleaned() {
        String raw = "```\n{\"recommendation\":\"go\",\"reason\":\"x\",\"confidence\":0.7,\"riskFactors\":[]}\n```";
        DecisionResult r = handler.parseResponse(raw, AiRequestContext.builder().build());
        assertThat(r.recommendation()).isEqualTo("go");
    }
}
