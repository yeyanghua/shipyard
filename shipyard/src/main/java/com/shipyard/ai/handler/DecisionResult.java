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
 * 发布决策结果 — AI decision capability 的结构化输出.
 *
 * <p>字段:
 * <ul>
 *   <li>{@code recommendation} — 建议: {@code go} (自动发布) / {@code hold} (暂停, 人工确认) / {@code rollback} (回滚)</li>
 *   <li>{@code reason} — 理由, 1-2 句话, 例 "build #12 成功, 近 10 次发布 p50 耗时未超阈值, 无异常"</li>
 *   <li>{@code confidence} — 自信度 0.0-1.0, hold 时一般 < 0.7</li>
 *   <li>{@code riskFactors} — 风险因素列表, 例 ["首次发布", "周五晚 22:00", "依赖 1 个 snapshot"]</li>
 * </ul>
 */
public record DecisionResult(
        String recommendation, String reason, Double confidence, java.util.List<String> riskFactors) {}
