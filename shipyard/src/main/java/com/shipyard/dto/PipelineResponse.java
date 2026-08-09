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

import com.shipyard.entity.PipelineTemplate;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Pipeline 响应体 — GET / POST / PUT /api/projects/{id}/pipeline 统一返回.
 *
 * <p>包含所有字段, 业务上没有 secret 所以不回显问题.
 */
@Data
public class PipelineResponse {

    private Long id;
    private Long projectId;
    private Integer version;
    private String yamlContent;
    private String reviewStatus;
    private Integer isActive;
    private String createdBy;
    private String aiModifiedBy;
    private String aiPrompt;
    private LocalDateTime createdAt;

    public static PipelineResponse from(PipelineTemplate p) {
        if (p == null) return null;
        PipelineResponse r = new PipelineResponse();
        r.setId(p.getId());
        r.setProjectId(p.getProjectId());
        r.setVersion(p.getVersion());
        r.setYamlContent(p.getYamlContent());
        r.setReviewStatus(p.getReviewStatus());
        r.setIsActive(p.getIsActive());
        r.setCreatedBy(p.getCreatedBy());
        r.setAiModifiedBy(p.getAiModifiedBy());
        r.setAiPrompt(p.getAiPrompt());
        r.setCreatedAt(p.getCreatedAt());
        return r;
    }
}
