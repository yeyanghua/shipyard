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

package com.shipyard.service;

import com.shipyard.entity.DockerfileTemplate;
import com.shipyard.entity.ProjectDockerfile;

import java.util.List;
import java.util.Map;

/**
 * 项目 Dockerfile 服务 — preview (纯渲染不存) + generate (渲染 + 写 project_dockerfile).
 */
public interface ProjectDockerfileService {

    /**
     * 预览: 渲染模板返内容, 不存数据库, 不动 project.
     */
    String preview(String templateName, Map<String, String> variables);

    /**
     * 生成: 渲染 + 写 project_dockerfile (status=draft, V1 demo 不真提交 repo).
     *
     * @param projectId 项目 ID
     * @param template 模板 (已查好)
     * @param variables 变量值
     * @param repoBranch 目标分支
     * @param commitMessage 提交 message
     * @return [渲染后内容, 创建的 project_dockerfile 记录]
     */
    ProjectDockerfile generate(
        Long projectId,
        DockerfileTemplate template,
        Map<String, String> variables,
        String repoBranch,
        String commitMessage
    );

    /** 按 project 列所有 Dockerfile 实例 (按 id DESC) */
    List<ProjectDockerfile> listByProject(Long projectId);

    /** 按 id 查 */
    ProjectDockerfile get(Long id);
}
