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

/**
 * 构建诊断结果 — AI diagnosis capability 的结构化输出.
 *
 * <p>字段:
 * <ul>
 *   <li>{@code rootCause} — 根因一句话描述, 例 "单元测试失败: 期望 42, 实际 0"</li>
 *   <li>{@code failedStep} — 失败的具体 step 名 (drone step name), 例 "test"</li>
 *   <li>{@code severity} — 严重程度: low / medium / high, 业务上 high 触发告警</li>
 *   <li>{@code suggestion} — 修复建议, 1-2 句话, 例 "检查 Calculator.divide() 边界条件"</li>
 *   <li>{@code confidence} — AI 自信度 0.0-1.0, 低于 0.5 时建议"请人工复核"</li>
 * </ul>
 */
public record DiagnosisResult(
        String failedStep, String rootCause, String severity, String suggestion, Double confidence) {}
