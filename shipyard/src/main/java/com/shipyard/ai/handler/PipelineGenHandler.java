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

import com.shipyard.ai.LlmRequest;
import com.shipyard.common.enums.AiCapability;
import com.shipyard.entity.Project;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pipeline 生成 handler — {@link AiCapability#PIPELINE_GEN} 的实现.
 *
 * <p>输入: project 元数据 (type / repoUrl / language / build tool)
 * <br>输出: 完整 drone pipeline YAML
 *
 * <p><b>V1 简化</b>: 真实 LLM 应该根据 project type 选模板 (java_maven / node_pnpm / ...),
 * V1 mock 简化成只看 projectType 返固定模板, V1.5 接真 LLM 时再让模型自己挑.
 */
@Slf4j
@Component
public class PipelineGenHandler implements AiCapabilityHandler<String> {

    @Override
    public AiCapability capability() {
        return AiCapability.PIPELINE_GEN;
    }

    @Override
    public LlmRequest buildRequest(AiRequestContext ctx) {
        Project p = ctx.project();
        if (p == null) {
            throw new IllegalStateException("pipeline_gen capability 必须有 project");
        }

        String systemPrompt =
                """
            你是 shipyard 平台的 CI 流水线生成助手. 你的任务是根据项目元数据生成 drone 格式的 pipeline YAML.

            要求:
            - 顶层用 kind: pipeline (YAML key:value 风格)
            - 包含 name 字段 (项目英文名)
            - steps 至少 3 个: compile (编译) / test (测试) / docker (打镜像, V1 mock 不真推 Harbor, log 标记即可)
            - 用 ${DRONE_REPO_NAME} ${DRONE_COMMIT_SHA} 等 drone 内置变量, 不要硬编码
            - 镜像仓库地址用 ${HARBOR_REGISTRY}/${projectName}/${repoName}:${commitShaShort}, 由 caller 注入

            只返 YAML 文本, 不要 markdown 围栏, 不要任何解释.
            """;

        String userPrompt = String.format(
                """
            项目元数据:
            - name: %s
            - displayName: %s
            - projectType: %s
            - repoUrl: %s
            - defaultBranch: %s

            请生成该项目的 drone pipeline YAML.
            """,
                p.getName(), p.getDisplayName(), p.getProjectType(), p.getRepoUrl(), p.getDefaultBranch());

        Map<String, Object> ctx2 = new HashMap<>();
        ctx2.put("projectId", p.getId());
        ctx2.put("projectName", p.getName());
        ctx2.put("projectType", p.getProjectType());

        return LlmRequest.of(capability(), userPrompt)
                .withSystemPrompt(systemPrompt)
                .withContext(ctx2);
    }

    @Override
    public String parseResponse(String rawText, AiRequestContext ctx) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("pipeline_gen 响应为空");
        }
        // 简单清洗: 去掉 markdown 围栏 (LLM 偶尔会返 ```yaml ... ```)
        String yaml = rawText.trim();
        if (yaml.startsWith("```")) {
            int firstNewline = yaml.indexOf('\n');
            int lastFence = yaml.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                yaml = yaml.substring(firstNewline + 1, lastFence).trim();
            }
        }
        // V1 基础校验: 必须含 kind: pipeline
        if (!yaml.contains("kind: pipeline")) {
            throw new IllegalArgumentException("pipeline_gen 响应不合法 (缺少 'kind: pipeline'), 内容: "
                    + (yaml.length() > 200 ? yaml.substring(0, 200) + "..." : yaml));
        }
        return yaml;
    }

    @Override
    public String describeAction(AiRequestContext ctx, String yamlContent) {
        Project p = ctx.project();
        // yaml 头几行作预览
        String preview = yamlContent.lines().limit(3).reduce("", (a, b) -> a + b + " | ");
        return String.format(
                "为 project %s (%s) 生成 pipeline 模板, %d 字符, 预览: %s",
                p.getId(), p.getProjectType(), yamlContent.length(), preview);
    }
}
