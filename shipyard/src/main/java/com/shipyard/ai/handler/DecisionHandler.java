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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipyard.ai.LlmRequest;
import com.shipyard.common.enums.AiCapability;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 发布决策 handler — {@link AiCapability#DECISION} 的实现.
 *
 * <p>输入: build id + 当前 build 结果 + 近 N 次发布历史
 * <br>输出: {@link DecisionResult} (recommendation: go/hold/rollback + reason + riskFactors)
 *
 * <p>预期 LLM 返 JSON, 解析成结构化对象. V1 mock 直接返 canned JSON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionHandler implements AiCapabilityHandler<DecisionResult> {

    private final ObjectMapper objectMapper;

    @Override
    public AiCapability capability() {
        return AiCapability.DECISION;
    }

    @Override
    public LlmRequest buildRequest(AiRequestContext ctx) {
        Long buildId = ctx.optional("buildId", Long.class);
        String buildStatus = ctx.optional("buildStatus", String.class);
        String buildHistory = ctx.optional("buildHistory", String.class);
        // envName 由 caller 通过 extras 传入 (V1 简化: 关联表 ProjectEnv 不带 name)
        String envName = ctx.optional("envName", String.class);
        if (envName == null) {
            envName = ctx.env() != null ? "env-" + ctx.env().getEnvId() : "?";
        }

        String systemPrompt =
                """
            你是 shipyard 平台的发布决策助手. 你的任务是基于 build 结果和发布历史, 给出 go / hold / rollback 建议.

            决策规则:
            - build status 失败 → 直接 rollback
            - build status 成功, 但近 10 次发布 p50 耗时 > 1.5x 历史中位数 → hold (人工确认)
            - build status 成功, 风险因素 > 2 个 → hold
            - 其它 → go

            必须严格只返 JSON (无 markdown 围栏), schema:
            {
              "recommendation": "go | hold | rollback",
              "reason": "1-2 句理由",
              "confidence": 0.0-1.0,
              "riskFactors": ["...", "..."]
            }
            """;

        String userPrompt = String.format(
                """
            build ID: %s
            env: %s
            build status: %s
            build history (近 10 次, 格式 "build_id status duration"):
            %s

            请给发布决策.
            """,
                buildId != null ? buildId : "?",
                envName,
                buildStatus != null ? buildStatus : "(unknown)",
                buildHistory != null ? buildHistory : "(no history)");

        Map<String, Object> ctx2 = new HashMap<>();
        ctx2.put("buildId", buildId);
        ctx2.put("envName", envName);
        ctx2.put("buildStatus", buildStatus);

        return LlmRequest.of(capability(), userPrompt)
                .withSystemPrompt(systemPrompt)
                .withContext(ctx2);
    }

    @Override
    public DecisionResult parseResponse(String rawText, AiRequestContext ctx) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("decision 响应为空");
        }
        String text = rawText.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        try {
            DecisionResult result = objectMapper.readValue(text, DecisionResult.class);
            // 校验 recommendation 在白名单内
            if (!List.of("go", "hold", "rollback").contains(result.recommendation())) {
                throw new IllegalArgumentException(
                        "decision recommendation 必须是 go/hold/rollback 之一, 实际: " + result.recommendation());
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "decision 响应 JSON 解析失败: " + e.getMessage() + ", 内容: "
                            + (text.length() > 200 ? text.substring(0, 200) + "..." : text),
                    e);
        }
    }

    @Override
    public String describeAction(AiRequestContext ctx, DecisionResult result) {
        Long buildId = ctx.optional("buildId", Long.class);
        return String.format(
                "决策 build %s: %s (confidence=%.2f, 风险因素: %s)",
                buildId != null ? buildId : "?",
                result.recommendation(),
                result.confidence() != null ? result.confidence() : 0.0,
                result.riskFactors() != null ? String.join(" / ", result.riskFactors()) : "(none)");
    }
}
