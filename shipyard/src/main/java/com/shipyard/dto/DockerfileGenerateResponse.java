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

import com.shipyard.entity.ProjectDockerfile;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Dockerfile 生成响应.
 *
 * <p>preview 端点: renderedContent + template info.
 * <p>generate 端点: 上面 + projectDockerfileId + status (V1=draft).
 */
@Data
public class DockerfileGenerateResponse {

    private Long projectDockerfileId;
    private Long projectId;
    private Long templateId;
    private String templateName;
    private String renderedContent;
    private String status;
    private String repoBranch;
    private String commitMessage;
    private String repoCommitSha; // V1 留空, V1.5 真 commit 后填
    private LocalDateTime createdAt;

    public static DockerfileGenerateResponse from(ProjectDockerfile pd, String renderedContent, String templateName) {
        DockerfileGenerateResponse r = new DockerfileGenerateResponse();
        r.setProjectDockerfileId(pd.getId());
        r.setProjectId(pd.getProjectId());
        r.setTemplateId(pd.getDockerfileTemplateId());
        r.setTemplateName(templateName);
        r.setRenderedContent(renderedContent);
        r.setStatus(pd.getStatus());
        r.setRepoBranch(pd.getRepoBranch());
        r.setCommitMessage(pd.getCommitMessage());
        r.setRepoCommitSha(pd.getRepoCommitSha());
        r.setCreatedAt(pd.getCreatedAt());
        return r;
    }
}
