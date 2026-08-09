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

import com.shipyard.ai.LlmRequest;
import com.shipyard.common.enums.AiCapability;
import com.shipyard.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PipelineGenHandler 单元测试 — 覆盖 buildRequest / parseResponse / describeAction.
 */
class PipelineGenHandlerTest {

    private PipelineGenHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PipelineGenHandler();
    }

    @Test
    @DisplayName("capability() 返 PIPELINE_GEN")
    void capability_returnsPipelineGen() {
        assertThat(handler.capability()).isEqualTo(AiCapability.PIPELINE_GEN);
    }

    @Test
    @DisplayName("buildRequest: project 元数据进 user prompt, system prompt 包含 kind: pipeline 要求")
    void buildRequest_includesProjectMetadata() {
        Project p = new Project();
        p.setId(5L);
        p.setName("demo-java-app");
        p.setDisplayName("Demo Java App");
        p.setProjectType("java_maven");
        p.setRepoUrl("https://gitlab.example.com/group/demo.git");
        p.setDefaultBranch("main");
        AiRequestContext ctx =
                AiRequestContext.builder().userId("alice").project(p).build();

        LlmRequest req = handler.buildRequest(ctx);

        assertThat(req.capability()).isEqualTo(AiCapability.PIPELINE_GEN);
        assertThat(req.systemPrompt()).contains("kind: pipeline");
        assertThat(req.userPrompt()).contains("demo-java-app");
        assertThat(req.userPrompt()).contains("java_maven");
        assertThat(req.context()).containsEntry("projectId", 5L);
        assertThat(req.context()).containsEntry("projectType", "java_maven");
    }

    @Test
    @DisplayName("buildRequest: 没 project 抛 IllegalStateException")
    void buildRequest_noProject_throws() {
        AiRequestContext ctx = AiRequestContext.builder().userId("alice").build();

        assertThatThrownBy(() -> handler.buildRequest(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("project");
    }

    @Test
    @DisplayName("parseResponse: 普通 YAML 直接返 (trim 后内容一致)")
    void parseResponse_plainYaml_returned() {
        // 末尾 \n 会被 trim 掉, 业务上不可见, 验证 trim 后内容正确
        String yaml = "kind: pipeline\nname: x\nsteps: []";
        AiRequestContext ctx = AiRequestContext.builder().build();
        assertThat(handler.parseResponse(yaml + "\n", ctx)).isEqualTo(yaml);
    }

    @Test
    @DisplayName("parseResponse: 清洗 markdown 围栏 ```yaml ... ```")
    void parseResponse_markdownFences_cleaned() {
        String raw = "```yaml\nkind: pipeline\nname: x\nsteps: []\n```";
        AiRequestContext ctx = AiRequestContext.builder().build();
        String result = handler.parseResponse(raw, ctx);
        assertThat(result).doesNotContain("```");
        assertThat(result).contains("kind: pipeline");
    }

    @Test
    @DisplayName("parseResponse: 缺 kind: pipeline 抛 IllegalArgumentException")
    void parseResponse_missingKind_throws() {
        String bad = "name: x\nsteps: []\n"; // 没 kind: pipeline
        AiRequestContext ctx = AiRequestContext.builder().build();

        assertThatThrownBy(() -> handler.parseResponse(bad, ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kind: pipeline");
    }

    @Test
    @DisplayName("parseResponse: 空字符串抛 IllegalArgumentException")
    void parseResponse_empty_throws() {
        AiRequestContext ctx = AiRequestContext.builder().build();
        assertThatThrownBy(() -> handler.parseResponse("", ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("响应为空");
    }

    @Test
    @DisplayName("describeAction: 含 projectId / projectType / 字符数")
    void describeAction_containsKeyInfo() {
        Project p = new Project();
        p.setId(5L);
        p.setProjectType("java_maven");
        AiRequestContext ctx = AiRequestContext.builder().project(p).build();
        String yaml = "kind: pipeline\nname: x\nsteps: []\n";

        String action = handler.describeAction(ctx, yaml);

        assertThat(action).contains("project 5");
        assertThat(action).contains("java_maven");
        assertThat(action).contains("字符");
    }
}
