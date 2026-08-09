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

package com.shipyard.common.enums;

import lombok.Getter;

/**
 * AI 能力类型 — 跟 {@code ai_interaction.capability} 字段对应.
 *
 * <p>shipyard V1 接入 3 个 AI capability, 每个走不同的 prompt 模板 + 输出 schema:
 * <ul>
 *   <li>{@link #PIPELINE_GEN} — 流水线生成 (从项目元数据 → drone YAML)</li>
 *   <li>{@link #DIAGNOSIS}    — 构建失败诊断 (build log + 退出码 → 根因 + 修复建议)</li>
 *   <li>{@link #DECISION}     — 发布决策 (build 结果 + 历史 → 自动/手动发布建议)</li>
 * </ul>
 *
 * <p>3 个 capability 共享同一个 {@code LlmAdapter} 接口, 区别仅在 prompt 拼装和 response 解析.
 * M6 V1 默认走 {@code MockLlmAdapter} (返回 canned data), 真实 LLM 走
 * {@code TONGYI_API_KEY} / {@code DEEPSEEK_API_KEY} env var 切换.
 */
@Getter
public enum AiCapability {

    /** 流水线生成 — 输入项目元数据, 输出完整 drone YAML 模板 */
    PIPELINE_GEN,

    /** 构建诊断 — 输入 build log + 失败步骤, 输出根因 + 修复建议 */
    DIAGNOSIS,

    /** 发布决策 — 输入 build 结果 + 历史发布成功率, 输出 go/no-go 建议 */
    DECISION;

    /**
     * 字符串值 — 跟 {@code ai_interaction.capability} 字段对应.
     *
     * <p>用小写 snake_case (跟 {@link LlmProvider#getValue()} 风格一致),
     * 业务层解析时大小写不敏感.
     */
    public String getValue() {
        return name().toLowerCase();
    }

    /**
     * 从字符串反查 — 大小写不敏感.
     *
     * @throws IllegalArgumentException 找不到时抛
     */
    public static AiCapability fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("AiCapability value 不能为 null");
        }
        for (AiCapability c : values()) {
            if (c.getValue().equalsIgnoreCase(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("未知的 AiCapability: " + value);
    }
}
