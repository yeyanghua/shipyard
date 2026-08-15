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
import com.shipyard.dto.DockerfileGenerateRequest;
import com.shipyard.dto.DockerfileGenerateResponse;
import com.shipyard.dto.DockerfileTemplateResponse;
import com.shipyard.entity.DockerfileTemplate;
import com.shipyard.entity.ProjectDockerfile;
import com.shipyard.service.DockerfileTemplateService;
import com.shipyard.service.ProjectDockerfileService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dockerfile Controller — 3 个端点:
 * <ul>
 *   <li>{@code GET    /api/dockerfile-templates}              列所有模板 (按 language, build_tool 排序)</li>
 *   <li>{@code POST   /api/projects/{id}/dockerfile/preview} 预览渲染 (不存数据库, 不动 project)</li>
 *   <li>{@code POST   /api/projects/{id}/dockerfile/generate} 渲染 + 写 project_dockerfile (status=draft)</li>
 * </ul>
 *
 * <p>V1 简化: generate 不真提交到 Gitea (V1.5 接 Gitea adapter 后改 status=draft → pushed + 填 repoCommitSha).
 */
@RestController
@RequiredArgsConstructor
public class DockerfileController {

    private final DockerfileTemplateService templateService;
    private final ProjectDockerfileService projectDockerfileService;

    /** GET /api/dockerfile-templates — 列出所有可用模板 */
    @GetMapping("/api/dockerfile-templates")
    public ApiResponse<List<DockerfileTemplateResponse>> listTemplates() {
        List<DockerfileTemplate> templates = templateService.listAll();
        List<DockerfileTemplateResponse> resp =
                templates.stream().map(DockerfileTemplateResponse::from).toList();
        return ApiResponse.ok(resp);
    }

    /** POST /api/projects/{id}/dockerfile/preview — 预览渲染 */
    @PostMapping("/api/projects/{projectId}/dockerfile/preview")
    public ApiResponse<DockerfileGenerateResponse> preview(
            @PathVariable Long projectId, @RequestBody @Valid DockerfileGenerateRequest req) {
        // preview 不查 project (变量只跟 template 相关, 不跟 project 绑)
        // 但仍然要 template 存在
        DockerfileTemplate template = templateService.getByName(req.getTemplateName());
        String rendered = templateService.render(template, req.getVariables());
        // 返个假 ProjectDockerfile 字段 (用 0 填充), 让前端 response shape 一致
        ProjectDockerfile fake = new ProjectDockerfile();
        fake.setProjectId(projectId);
        fake.setDockerfileTemplateId(template.getId());
        fake.setStatus("preview");
        fake.setRepoBranch(req.getRepoBranch());
        fake.setCommitMessage(req.getCommitMessage());
        return ApiResponse.ok(DockerfileGenerateResponse.from(fake, rendered, template.getName()));
    }

    /** POST /api/projects/{id}/dockerfile/generate — 真生成 (写 draft) */
    @PostMapping("/api/projects/{projectId}/dockerfile/generate")
    public ApiResponse<DockerfileGenerateResponse> generate(
            @PathVariable Long projectId, @RequestBody @Valid DockerfileGenerateRequest req) {
        DockerfileTemplate template = templateService.getByName(req.getTemplateName());
        ProjectDockerfile pd = projectDockerfileService.generate(
                projectId, template, req.getVariables(), req.getRepoBranch(), req.getCommitMessage());
        return ApiResponse.ok(DockerfileGenerateResponse.from(pd, pd.getRenderedContent(), template.getName()));
    }
}
