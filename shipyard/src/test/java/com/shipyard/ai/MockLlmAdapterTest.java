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
import static org.mockito.Mockito.when;

import com.shipyard.common.enums.AiCapability;
import com.shipyard.common.enums.LlmProvider;
import com.shipyard.config.ShipyardAiProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * MockLlmAdapter 单元测试 — 验证 3 capability 各自的 canned response 格式.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MockLlmAdapterTest {

    @Mock
    private ShipyardAiProperties aiProperties;

    private MockLlmAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MockLlmAdapter(aiProperties);
        when(aiProperties.getDefaultModelFor(LlmProvider.MOCK)).thenReturn("v1");
    }

    @Test
    @DisplayName("provider() 返 MOCK")
    void provider_returnsMock() {
        assertThat(adapter.provider()).isEqualTo(LlmProvider.MOCK);
    }

    @Test
    @DisplayName("pipeline_gen + java_maven: 返含 maven 镜像的 YAML")
    void pipelineGen_javaMaven_containsMavenImage() {
        LlmRequest req = LlmRequest.of(AiCapability.PIPELINE_GEN, "test prompt")
                .withContext(Map.of("projectType", "java_maven"));

        LlmResponse resp = adapter.complete(req);

        assertThat(resp.provider()).isEqualTo(LlmProvider.MOCK);
        assertThat(resp.model()).isEqualTo("v1");
        assertThat(resp.content()).contains("kind: pipeline");
        assertThat(resp.content()).contains("maven:3.9-eclipse-temurin-21");
        assertThat(resp.content()).contains("mvn -B test");
    }

    @Test
    @DisplayName("pipeline_gen + node_pnpm: 返含 node 镜像 + pnpm install 的 YAML")
    void pipelineGen_nodePnpm_containsNodeImage() {
        LlmRequest req =
                LlmRequest.of(AiCapability.PIPELINE_GEN, "test prompt").withContext(Map.of("projectType", "node_pnpm"));

        LlmResponse resp = adapter.complete(req);

        assertThat(resp.content()).contains("node:22");
        assertThat(resp.content()).contains("corepack enable");
        assertThat(resp.content()).contains("pnpm install --frozen-lockfile");
    }

    @Test
    @DisplayName("pipeline_gen + 未知 type: 返 fallback 占位步骤")
    void pipelineGen_unknownType_fallback() {
        LlmRequest req = LlmRequest.of(AiCapability.PIPELINE_GEN, "test prompt")
                .withContext(Map.of("projectType", "rust_cargo"));

        LlmResponse resp = adapter.complete(req);

        assertThat(resp.content()).contains("rust_cargo");
        assertThat(resp.content()).contains("TODO");
    }

    @Test
    @DisplayName("diagnosis + test 失败: 返 test 根因 JSON")
    void diagnosis_testFailure() {
        LlmRequest req = LlmRequest.of(AiCapability.DIAGNOSIS, "test prompt").withContext(Map.of("failedStep", "test"));

        LlmResponse resp = adapter.complete(req);

        assertThat(resp.content()).contains("\"failedStep\": \"test\"");
        assertThat(resp.content()).contains("\"severity\": \"medium\"");
        assertThat(resp.content()).contains("\"confidence\":");
    }

    @Test
    @DisplayName("diagnosis + compile 失败: 返 compile 根因 JSON")
    void diagnosis_compileFailure() {
        LlmRequest req =
                LlmRequest.of(AiCapability.DIAGNOSIS, "test prompt").withContext(Map.of("failedStep", "compile"));

        LlmResponse resp = adapter.complete(req);

        assertThat(resp.content()).contains("\"failedStep\": \"compile\"");
        assertThat(resp.content()).contains("\"severity\": \"high\"");
    }

    @Test
    @DisplayName("decision + SUCCESS: 返 go 建议")
    void decision_success_returnsGo() {
        LlmRequest req =
                LlmRequest.of(AiCapability.DECISION, "test prompt").withContext(Map.of("buildStatus", "SUCCESS"));

        LlmResponse resp = adapter.complete(req);

        assertThat(resp.content()).contains("\"recommendation\": \"go\"");
    }

    @Test
    @DisplayName("decision + FAILED: 返 rollback 建议")
    void decision_failed_returnsRollback() {
        LlmRequest req =
                LlmRequest.of(AiCapability.DECISION, "test prompt").withContext(Map.of("buildStatus", "FAILED"));

        LlmResponse resp = adapter.complete(req);

        assertThat(resp.content()).contains("\"recommendation\": \"rollback\"");
    }

    @Test
    @DisplayName("request 指定 model 时, response 用指定 model")
    void pipelineGen_customModel_usesItInResponse() {
        LlmRequest req = LlmRequest.of(AiCapability.PIPELINE_GEN, "test prompt")
                .withModel("custom-v2")
                .withContext(Map.of("projectType", "java_maven"));

        LlmResponse resp = adapter.complete(req);

        assertThat(resp.model()).isEqualTo("custom-v2");
    }
}
