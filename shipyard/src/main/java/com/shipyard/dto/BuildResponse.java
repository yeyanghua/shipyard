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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 构建记录响应 — {@code GET /api/builds/{id}} / 列表项.
 *
 * <p>字段说明:
 * <ul>
 *   <li>{@code status} — {@link com.shipyard.common.enums.BuildStatus} 字符串</li>
 *   <li>{@code imageTag} / {@code harborImageUrl} — 成功后填, 其他状态可能 null (用 {@code @JsonInclude(NON_NULL)} 隐藏)</li>
 *   <li>{@code logPersisted} — 0=日志还没落, 1=已落 {@code build_log} (前端可查 step log)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuildResponse {
    private Long id;
    private Long projectId;
    private Long pipelineTemplateId;
    private String commitSha;
    private String commitMessage;
    private String triggeredBy;
    private String triggerType;
    private String droneBuildId;
    private String status;
    private String imageTag;
    private String harborImageUrl;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer logPersisted;
    private LocalDateTime createdAt;
}
