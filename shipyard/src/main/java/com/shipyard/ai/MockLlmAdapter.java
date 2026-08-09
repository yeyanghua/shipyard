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

import com.shipyard.common.enums.AiCapability;
import com.shipyard.common.enums.LlmProvider;
import com.shipyard.config.ShipyardAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock LLM Adapter — V1 demo 默认实现, 离线返 canned data.
 *
 * <p>3 个 capability 各自有 canned response:
 * <ul>
 *   <li>{@link AiCapability#PIPELINE_GEN} — 按 projectType 返固定 YAML 模板</li>
 *   <li>{@link AiCapability#DIAGNOSIS}    — 返 canned JSON (test 失败 / 编译失败 两类)</li>
 *   <li>{@link AiCapability#DECISION}     — 返 canned JSON (go/hold 二选一)</li>
 * </ul>
 *
 * <p>为什么用 mock: V1 demo 不接真 LLM (零依赖 + 单元测试可控), 但要保证流程跑通.
 * V1.5 接真 LLM 时换 {@code TongyiLlmAdapter} / {@code DeepseekLlmAdapter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockLlmAdapter implements LlmAdapter {

    private final ShipyardAiProperties aiProperties;

    @Override
    public LlmResponse complete(LlmRequest request) {
        log.info(
                "[MockLlm] 收到 capability={} model={} promptLen={}",
                request.capability(),
                request.model(),
                request.userPrompt().length());

        String content =
                switch (request.capability()) {
                    case PIPELINE_GEN -> mockPipelineGen(request);
                    case DIAGNOSIS -> mockDiagnosis(request);
                    case DECISION -> mockDecision(request);
                };

        String model = request.model() != null ? request.model() : aiProperties.getDefaultModelFor(LlmProvider.MOCK);

        log.info("[MockLlm] 返 contentLen={} model={}", content.length(), model);
        return LlmResponse.mock(model, content);
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.MOCK;
    }

    // ==================== Canned Responses ====================

    /**
     * Mock pipeline_gen — 按 projectType 返固定 YAML 模板.
     *
     * <p>简化: V1 只覆盖 2 种类型, 其它都走 java_maven fallback.
     */
    private String mockPipelineGen(LlmRequest request) {
        String projectType =
                request.context() != null ? (String) request.context().get("projectType") : "java_maven";
        return switch (projectType) {
            case "java_maven" -> """
                kind: pipeline
                name: default
                steps:
                - name: compile
                  image: maven:3.9-eclipse-temurin-21
                  commands:
                  - mvn -B clean compile
                - name: test
                  image: maven:3.9-eclipse-temurin-21
                  commands:
                  - mvn -B test
                - name: docker
                  image: plugins/docker
                  settings:
                    username: ${HARBOR_USER}
                    password: ${HARBOR_PASSWORD}
                    registry: ${HARBOR_REGISTRY}
                  commands:
                  - docker build -t ${HARBOR_REGISTRY}/${DRONE_REPO_NAME}:${DRONE_COMMIT_SHA:0:8} .
                  - docker push ${HARBOR_REGISTRY}/${DRONE_REPO_NAME}:${DRONE_COMMIT_SHA:0:8}
                """;
            case "node_pnpm" -> """
                kind: pipeline
                name: default
                steps:
                - name: install
                  image: node:22
                  commands:
                  - corepack enable
                  - pnpm install --frozen-lockfile
                - name: test
                  image: node:22
                  commands:
                  - pnpm test
                - name: build
                  image: node:22
                  commands:
                  - pnpm build
                - name: docker
                  image: plugins/docker
                  settings:
                    username: ${HARBOR_USER}
                    password: ${HARBOR_PASSWORD}
                    registry: ${HARBOR_REGISTRY}
                  commands:
                  - docker build -t ${HARBOR_REGISTRY}/${DRONE_REPO_NAME}:${DRONE_COMMIT_SHA:0:8} .
                  - docker push ${HARBOR_REGISTRY}/${DRONE_REPO_NAME}:${DRONE_COMMIT_SHA:0:8}
                """;
            default -> """
                kind: pipeline
                name: default
                steps:
                - name: compile
                  commands:
                  - echo "TODO: 未知 projectType=%s, 用占位步骤"
                - name: test
                  commands:
                  - echo "TODO: 未知 projectType=%s, 用占位步骤"
                - name: docker
                  commands:
                  - echo "TODO: 未知 projectType=%s, 用占位步骤"
                """
                    .formatted(projectType, projectType, projectType);
        };
    }

    /**
     * Mock diagnosis — 根据 failedStep 返不同 canned JSON.
     */
    private String mockDiagnosis(LlmRequest request) {
        String failedStep =
                request.context() != null ? (String) request.context().get("failedStep") : "test";
        if ("test".equals(failedStep)) {
            return """
                {
                  "failedStep": "test",
                  "rootCause": "单元测试失败: 期望值与实际值不符",
                  "severity": "medium",
                  "suggestion": "请检查最近修改的 Calculator.divide() 边界条件 (mock 给的根因, 真实请调 LLM)",
                  "confidence": 0.75
                }
                """;
        }
        if ("compile".equals(failedStep)) {
            return """
                {
                  "failedStep": "compile",
                  "rootCause": "Java 编译失败: 类 com.example.Foo 不存在",
                  "severity": "high",
                  "suggestion": "请检查 import 路径, 或确认 Foo.java 已 commit (mock 给的根因, 真实请调 LLM)",
                  "confidence": 0.85
                }
                """;
        }
        return """
            {
              "failedStep": "%s",
              "rootCause": "未知失败 (mock 无法诊断具体 step, V1 demo 限制)",
              "severity": "low",
              "suggestion": "请人工查看 build log 定位",
              "confidence": 0.3
            }
            """
                .formatted(failedStep);
    }

    /**
     * Mock decision — 根据 buildStatus 返 go / hold.
     */
    private String mockDecision(LlmRequest request) {
        String buildStatus =
                request.context() != null ? (String) request.context().get("buildStatus") : "SUCCESS";
        if (!"SUCCESS".equals(buildStatus)) {
            return """
                {
                  "recommendation": "rollback",
                  "reason": "build status 非 SUCCESS, mock 直接回滚 (V1 demo 简化)",
                  "confidence": 0.95,
                  "riskFactors": ["build 失败", "mock 默认回滚"]
                }
                """;
        }
        return """
            {
              "recommendation": "go",
              "reason": "build 成功, mock 默认放行 (V1 demo 简化, 真实请调 LLM 综合判断)",
              "confidence": 0.7,
              "riskFactors": ["mock 决策", "无历史数据"]
            }
            """;
    }
}
