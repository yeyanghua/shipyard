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

package com.shipyard.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 pipeline 版本请求体 — POST /api/projects/{projectId}/pipeline.
 *
 * <p>两种用法 (按 {@code aiGenerate} 区分):
 * <ul>
 *   <li><b>用户手动</b>: {@code aiGenerate=false}, {@code yamlContent} 必填 (1-64KB)</li>
 *   <li><b>AI 生成</b>: {@code aiGenerate=true}, {@code yamlContent} 可空 (AI 填),
 *       可选 {@code aiPrompt} 给 AI 加额外指令</li>
 * </ul>
 *
 * <p>两种方式都创建 {@code draft} 状态, version 自动算 (= MAX+1).
 */
@Data
public class PipelineCreateRequest {

    /**
     * 用户手动填的 YAML 内容.
     * <ul>
     *   <li>{@code aiGenerate=false} — 必填, 1-65535 字符 (MEDIUMTEXT 上限)</li>
     *   <li>{@code aiGenerate=true}  — 可空, 留空时由 AI 生成</li>
     * </ul>
     */
    @Size(max = 65535)
    private String yamlContent;

    /**
     * 是否用 AI 生成.
     * <ul>
     *   <li>{@code true}  — 调 LlmService, 把生成结果作为 yamlContent 创建 (如果用户同时填了 yamlContent, AI 改写)</li>
     *   <li>{@code false} — 用户手动填, 默认</li>
     * </ul>
     */
    private Boolean aiGenerate = false;

    /**
     * AI 改写时的额外 prompt.
     * <ul>
     *   <li>{@code aiGenerate=true}  — 可选, 例 "加 docker scan step"</li>
     *   <li>{@code aiGenerate=false} — 忽略</li>
     * </ul>
     */
    @Size(max = 2048)
    private String aiPrompt;

    /**
     * 单次覆盖全局 AI provider — 可选, 业务场景: 平时用 mock, 重要版本用真 LLM.
     * 例: "mock" / "tongyi" / "deepseek".
     */
    @Size(max = 32)
    private String aiProvider;
}
