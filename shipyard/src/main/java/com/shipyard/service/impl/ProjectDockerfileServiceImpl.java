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

package com.shipyard.service.impl;

import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.entity.DockerfileTemplate;
import com.shipyard.entity.Project;
import com.shipyard.entity.ProjectDockerfile;
import com.shipyard.mapper.ProjectDockerfileMapper;
import com.shipyard.service.DockerfileTemplateService;
import com.shipyard.service.ProjectDockerfileService;
import com.shipyard.service.ProjectService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectDockerfileServiceImpl implements ProjectDockerfileService {

    private final DockerfileTemplateService templateService;
    private final ProjectDockerfileMapper projectDockerfileMapper;
    private final ProjectService projectService;

    @Override
    public String preview(String templateName, Map<String, String> variables) {
        DockerfileTemplate template = templateService.getByName(templateName);
        return templateService.render(template, variables);
    }

    @Override
    public ProjectDockerfile generate(
            Long projectId,
            DockerfileTemplate template,
            Map<String, String> variables,
            String repoBranch,
            String commitMessage) {
        // 1. 校验 project 存在
        Project project = projectService.get(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在: id=" + projectId);
        }

        // 2. 渲染
        String rendered = templateService.render(template, variables);

        // 3. 写 project_dockerfile 记录 (V1 status=draft, repo_commit_sha=null, 不真推 Gitea)
        ProjectDockerfile pd = new ProjectDockerfile();
        pd.setProjectId(projectId);
        pd.setDockerfileTemplateId(template.getId());
        pd.setRenderedContent(rendered);
        pd.setVariableValues(toJson(variables));
        pd.setRepoBranch(repoBranch == null || repoBranch.isBlank() ? "main" : repoBranch);
        pd.setCommitMessage(
                commitMessage == null || commitMessage.isBlank()
                        ? "chore: add Dockerfile via shipyard"
                        : commitMessage);
        pd.setStatus("draft");
        pd.setCreatedAt(LocalDateTime.now());
        projectDockerfileMapper.insert(pd);

        log.info(
                "[ProjectDockerfileService] project={} 渲染 Dockerfile 模板={} (V1 status=draft, 未真推 repo)",
                projectId,
                template.getName());
        return pd;
    }

    @Override
    public List<ProjectDockerfile> listByProject(Long projectId) {
        return projectDockerfileMapper.listByProject(projectId);
    }

    @Override
    public ProjectDockerfile get(Long id) {
        ProjectDockerfile pd = projectDockerfileMapper.selectById(id);
        if (pd == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "project_dockerfile 不存在: id=" + id);
        }
        return pd;
    }

    /**
     * 简化: 不用 Jackson, 手动构造 JSON object 字符串.
     * 避免在 Service 引入 ObjectMapper 依赖.
     */
    private String toJson(Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(e.getKey())).append("\":");
            sb.append("\"").append(escape(e.getValue())).append("\"");
            first = false;
        }
        return sb.append("}").toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
