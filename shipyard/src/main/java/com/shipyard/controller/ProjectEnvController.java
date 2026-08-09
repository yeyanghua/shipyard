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

package com.shipyard.controller;

import com.shipyard.dto.ApiResponse;
import com.shipyard.dto.ProjectEnvResponse;
import com.shipyard.entity.ProjectEnv;
import com.shipyard.service.ProjectEnvService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ProjectEnv Controller — /api/projects/{projectId}/envs.
 *
 * <p>3 个端点: GET 列出关联 / POST 关联 / DELETE 取消关联.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/envs")
@RequiredArgsConstructor
public class ProjectEnvController {

    private final ProjectEnvService projectEnvService;

    @GetMapping
    public ApiResponse<List<ProjectEnvResponse>> list(@PathVariable Long projectId) {
        List<ProjectEnv> list = projectEnvService.listByProject(projectId);
        List<ProjectEnvResponse> mapped = list.stream()
                .map(pe -> new ProjectEnvResponse(pe.getProjectId(), pe.getEnvId()))
                .toList();
        return ApiResponse.ok(mapped);
    }

    @PostMapping
    public ApiResponse<ProjectEnvResponse> associate(@PathVariable Long projectId, @RequestBody ProjectEnvRequest req) {
        ProjectEnv pe = projectEnvService.associate(projectId, req.getEnvId());
        return ApiResponse.ok(new ProjectEnvResponse(pe.getProjectId(), pe.getEnvId()));
    }

    @DeleteMapping("/{envId}")
    public ApiResponse<Void> unassociate(@PathVariable Long projectId, @PathVariable Long envId) {
        projectEnvService.unassociate(projectId, envId);
        return ApiResponse.ok();
    }

    /** POST 请求体: {@code {"envId": 1}}. */
    @lombok.Data
    public static class ProjectEnvRequest {
        private Long envId;
    }
}
