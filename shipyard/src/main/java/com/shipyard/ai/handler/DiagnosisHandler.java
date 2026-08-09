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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 构建诊断 handler — {@link AiCapability#DIAGNOSIS} 的实现.
 *
 * <p>输入: build id + 失败 step + 该 step 完整日志 (extras.buildLog)
 * <br>输出: {@link DiagnosisResult} (root_cause / severity / suggestion / confidence)
 *
 * <p>预期 LLM 返 JSON, 解析成结构化对象. V1 mock 直接返 canned JSON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosisHandler implements AiCapabilityHandler<DiagnosisResult> {

    private final ObjectMapper objectMapper;

    @Override
    public AiCapability capability() {
        return AiCapability.DIAGNOSIS;
    }

    @Override
    public LlmRequest buildRequest(AiRequestContext ctx) {
        String buildLog = ctx.require("buildLog", String.class);
        String failedStep = ctx.optional("failedStep", String.class);
        Long buildId = ctx.optional("buildId", Long.class);

        String systemPrompt =
                """
            你是 shipyard 平台的 CI 失败诊断助手. 你的任务是分析构建失败日志, 输出根因 + 修复建议.

            必须严格只返 JSON (无 markdown 围栏), schema:
            {
              "failedStep": "失败的具体 step 名 (e.g. 'test', 'docker-push')",
              "rootCause": "一句话根因",
              "severity": "low | medium | high",
              "suggestion": "1-2 句修复建议",
              "confidence": 0.0-1.0
            }
            """;

        String userPrompt = String.format(
                """
            构建 ID: %s
            失败 step: %s
            日志全文:
            ```
            %s
            ```

            请诊断根因 + 给修复建议.
            """,
                buildId != null ? buildId : "?",
                failedStep != null ? failedStep : "(unknown)",
                buildLog.length() > 4000 ? buildLog.substring(0, 4000) + "\n...[truncated]" : buildLog);

        Map<String, Object> ctx2 = new HashMap<>();
        ctx2.put("buildId", buildId);
        ctx2.put("failedStep", failedStep);

        return LlmRequest.of(capability(), userPrompt)
                .withSystemPrompt(systemPrompt)
                .withContext(ctx2);
    }

    @Override
    public DiagnosisResult parseResponse(String rawText, AiRequestContext ctx) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("diagnosis 响应为空");
        }
        String text = rawText.trim();
        // 清洗 markdown 围栏
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        try {
            return objectMapper.readValue(text, DiagnosisResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "diagnosis 响应 JSON 解析失败: " + e.getMessage() + ", 内容: "
                            + (text.length() > 200 ? text.substring(0, 200) + "..." : text),
                    e);
        }
    }

    @Override
    public String describeAction(AiRequestContext ctx, DiagnosisResult result) {
        Long buildId = ctx.optional("buildId", Long.class);
        return String.format(
                "诊断 build %s 失败: root_cause='%s', severity=%s, 建议='%s'",
                buildId != null ? buildId : "?", result.rootCause(), result.severity(), result.suggestion());
    }
}
