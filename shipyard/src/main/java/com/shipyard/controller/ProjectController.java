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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.dto.ApiResponse;
import com.shipyard.dto.PageResponse;
import com.shipyard.dto.ProjectCreateRequest;
import com.shipyard.dto.ProjectResponse;
import com.shipyard.dto.ProjectUpdateRequest;
import com.shipyard.entity.Project;
import com.shipyard.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project Controller — /api/projects.
 *
 * <p>5 个端点: GET 列表 / GET 详情 / POST 创建 / PUT 更新 / DELETE 软删.
 * 所有响应统一包 {@link ApiResponse}.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ApiResponse<PageResponse<ProjectResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Page<Project> p = projectService.list(page, size, keyword);
        return ApiResponse.ok(PageResponse.from(p, ProjectResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(ProjectResponse.from(projectService.get(id)));
    }

    @PostMapping
    public ApiResponse<ProjectResponse> create(@RequestBody @Valid ProjectCreateRequest req) {
        Project p = new Project();
        BeanUtils.copyProperties(req, p, "repoToken", "projectMeta"); // 跳过 Object 字段
        p.setProjectMeta(stringifyProjectMeta(req.getProjectMeta()));
        p.setRepoTokenEnc(req.getRepoToken()); // 字段名复用, Service 加密
        return ApiResponse.ok(ProjectResponse.from(projectService.create(p)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectResponse> update(@PathVariable Long id, @RequestBody @Valid ProjectUpdateRequest req) {
        Project p = new Project();
        BeanUtils.copyProperties(req, p, "repoToken", "projectMeta");
        // 只在 request 显式传了 projectMeta 时才更新 (避免 PUT 部分字段把原值覆盖成 null)
        if (req.getProjectMeta() != null) {
            p.setProjectMeta(stringifyProjectMeta(req.getProjectMeta()));
        }
        p.setRepoTokenEnc(req.getRepoToken());
        return ApiResponse.ok(ProjectResponse.from(projectService.update(id, p)));
    }

    /**
     * 把任意 Object 转 JSON 字符串 — projectMeta 字段入库前 stringify.
     *
     * <p>支持:
     * <ul>
     *   <li>{@code LinkedHashMap} (Jackson 反序列化 JSON 对象) → 写回 JSON 字符串</li>
     *   <li>{@code String} (用户已传 JSON 字符串) → 原样返回</li>
     *   <li>{@code null} → null</li>
     * </ul>
     */
    private String stringifyProjectMeta(Object projectMeta) {
        if (projectMeta == null) return null;
        if (projectMeta instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(projectMeta);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "projectMeta JSON 序列化失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.ok();
    }
}
