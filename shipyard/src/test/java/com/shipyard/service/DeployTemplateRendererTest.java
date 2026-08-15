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

package com.shipyard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shipyard.common.exception.BusinessException;
import com.shipyard.entity.Env;
import com.shipyard.entity.PipelineTemplate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DeployTemplateRenderer 单元测试 — 渲染 K8s yaml 的核心组件.
 *
 * <p>覆盖:
 * <ul>
 *   <li>render 整体 — 包含 Deployment + Service, 标准格式, label 正确</li>
 *   <li>renderNamespace — shipyard-{env_name} 替换 + 兜底 + 多 placeholder 不动</li>
 *   <li>renderEnvBlock — 字母序排 + 空列表 + 转义</li>
 *   <li>containerPort NULL / resourceName 空 / image 空 都抛 BusinessException</li>
 * </ul>
 */
@DisplayName("DeployTemplateRenderer — 渲染 K8s yaml")
class DeployTemplateRendererTest {

    private DeployTemplateRenderer renderer;
    private Env env;
    private PipelineTemplate template;

    @BeforeEach
    void setUp() {
        renderer = new DeployTemplateRenderer();

        env = new Env();
        env.setId(1L);
        env.setName("dev");

        template = new PipelineTemplate();
        template.setProjectId(1L);
        template.setVersion(1);
        template.setContainerPort(8080);
        template.setReplicas(3);
        template.setNamespacePattern("shipyard-{env_name}");
    }

    @Test
    @DisplayName("render 整体: 包含 Deployment + Service, label/replicas/port 正确")
    void renderFull() {
        Map<String, String> envs = new LinkedHashMap<>();
        envs.put("LOG_LEVEL", "INFO");
        envs.put("APP_NAME", "myapp");

        String yaml = renderer.render(env, template, "myapp-dev", "nginx:1.27.0", envs);

        // Deployment 部分
        assertThat(yaml).contains("apiVersion: apps/v1");
        assertThat(yaml).contains("kind: Deployment");
        assertThat(yaml).contains("name: myapp-dev");
        assertThat(yaml).contains("namespace: shipyard-dev");
        assertThat(yaml).contains("replicas: 3");
        assertThat(yaml).contains("image: nginx:1.27.0");
        assertThat(yaml).contains("containerPort: 8080");
        // Service 部分
        assertThat(yaml).contains("kind: Service");
        assertThat(yaml).contains("targetPort: 8080");
        assertThat(yaml).contains("type: ClusterIP");
        // env (字母序 APP_NAME 在前)
        int appIdx = yaml.indexOf("APP_NAME");
        int logIdx = yaml.indexOf("LOG_LEVEL");
        assertThat(appIdx).isLessThan(logIdx);
    }

    @Test
    @DisplayName("render: envVars 空 → 渲染 '[]' 占位")
    void renderEmptyEnvVars() {
        String yaml = renderer.render(env, template, "myapp-dev", "nginx:1.27.0", null);
        assertThat(yaml).contains("env:\n            []");
    }

    @Test
    @DisplayName("render: 同一 input 永远出同一输出 (sha256 稳定)")
    void renderDeterministic() {
        Map<String, String> envs = new HashMap<>();
        envs.put("A", "1");
        envs.put("B", "2");
        String yaml1 = renderer.render(env, template, "r1", "nginx:1.27.0", envs);
        String yaml2 = renderer.render(env, template, "r1", "nginx:1.27.0", envs);
        assertThat(yaml1).isEqualTo(yaml2);
    }

    @Test
    @DisplayName("renderNamespace: shipyard-{env_name} → shipyard-dev")
    void renderNamespaceReplace() {
        assertThat(renderer.renderNamespace("shipyard-{env_name}", "dev")).isEqualTo("shipyard-dev");
        assertThat(renderer.renderNamespace("team-a-{env_name}", "test")).isEqualTo("team-a-test");
    }

    @Test
    @DisplayName("renderNamespace: pattern null/空 → 兜底 'shipyard-{envName}'")
    void renderNamespaceFallback() {
        assertThat(renderer.renderNamespace(null, "dev")).isEqualTo("shipyard-dev");
        assertThat(renderer.renderNamespace("", "dev")).isEqualTo("shipyard-dev");
    }

    @Test
    @DisplayName("renderNamespace: 多 placeholder 不替换 (V1 简版硬编码只替 {env_name})")
    void renderNamespaceMultiPlaceholder() {
        // 期望只替 {env_name}, 留下 {user_name} 不动
        String result = renderer.renderNamespace("ns-{env_name}-{user_name}", "dev");
        assertThat(result).isEqualTo("ns-dev-{user_name}");
    }

    @Test
    @DisplayName("renderEnvBlock: 字母序排, 字母序早的在前面")
    void renderEnvBlockSorted() {
        Map<String, String> envs = new LinkedHashMap<>();
        envs.put("Z_LAST", "z");
        envs.put("A_FIRST", "a");
        envs.put("M_MID", "m");

        String block = renderer.renderEnvBlock(envs);
        int aIdx = block.indexOf("A_FIRST");
        int mIdx = block.indexOf("M_MID");
        int zIdx = block.indexOf("Z_LAST");
        assertThat(aIdx).isLessThan(mIdx);
        assertThat(mIdx).isLessThan(zIdx);
    }

    @Test
    @DisplayName("renderEnvBlock: 空 map / null → 返 '[]'")
    void renderEnvBlockEmpty() {
        assertThat(renderer.renderEnvBlock(null)).isEqualTo("            []");
        assertThat(renderer.renderEnvBlock(new HashMap<>())).isEqualTo("            []");
    }

    @Test
    @DisplayName("renderEnvBlock: value 含双引号 → 转义")
    void renderEnvBlockEscapesQuotes() {
        Map<String, String> envs = new HashMap<>();
        envs.put("MSG", "say \"hi\"");
        String block = renderer.renderEnvBlock(envs);
        assertThat(block).contains("value: \"say \\\"hi\\\"\"");
    }

    @Test
    @DisplayName("containerPort NULL 抛 BusinessException")
    void containerPortNull() {
        template.setContainerPort(null);
        assertThatThrownBy(() -> renderer.render(env, template, "r1", "nginx:1.27.0", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("containerPort");
    }

    @Test
    @DisplayName("resourceName 空 抛 BusinessException")
    void resourceNameBlank() {
        assertThatThrownBy(() -> renderer.render(env, template, "", "nginx:1.27.0", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("resourceName");
    }

    @Test
    @DisplayName("image 空 抛 BusinessException")
    void imageBlank() {
        assertThatThrownBy(() -> renderer.render(env, template, "r1", "", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("image");
    }
}
