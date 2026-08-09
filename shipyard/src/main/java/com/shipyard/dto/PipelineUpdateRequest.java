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
 * 更新 pipeline 版本请求体 — PUT /api/projects/{projectId}/pipeline/{versionId}.
 *
 * <p>只能更新 {@code draft} / {@code rejected} 状态 (approved immutable, 业务规则).
 *
 * <p>所有字段可选: 不传 = 不动. 业务上至少传一个字段.
 */
@Data
public class PipelineUpdateRequest {

    /** 新 YAML 内容 — 1-65535 字符 */
    @Size(max = 65535)
    private String yamlContent;

    /** 备注本次修改的 prompt (例: "调整 docker step 镜像版本") */
    @Size(max = 2048)
    private String aiPrompt;

    /**
     * 是否标记为 AI 修改 (M6 3 前端 PipelineEdit 页的 "AI 帮我改" 按钮触发时 = true).
     * 默认 false. 业务上影响 ai_modified_by 字段的填充.
     */
    private Boolean aiModified = false;
}
