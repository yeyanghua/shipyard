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

import com.shipyard.entity.Project;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目响应体 — GET / POST / PUT /api/projects 统一返回.
 *
 * <p>关键: <b>{@code repoToken} 不回显</b> — 只暴露 {@code hasRepoToken: boolean} 让前端判断.
 */
@Data
public class ProjectResponse {

    private Long id;
    private String name;
    private String displayName;
    private String repoProvider;
    private String repoUrl;
    private Boolean hasRepoToken; // 替代 repoToken, 防泄漏
    private String defaultBranch;
    private String projectType;
    private String projectMeta;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse from(Project p) {
        if (p == null) return null;
        ProjectResponse r = new ProjectResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setDisplayName(p.getDisplayName());
        r.setRepoProvider(p.getRepoProvider());
        r.setRepoUrl(p.getRepoUrl());
        r.setHasRepoToken(p.getRepoTokenEnc() != null && !p.getRepoTokenEnc().isEmpty());
        r.setDefaultBranch(p.getDefaultBranch());
        r.setProjectType(p.getProjectType());
        r.setProjectMeta(p.getProjectMeta());
        r.setDescription(p.getDescription());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}
