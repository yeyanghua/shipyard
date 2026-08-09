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

import com.shipyard.entity.DockerfileTemplate;
import lombok.Data;

/**
 * Dockerfile 模板响应 — list 端点 + get 端点.
 *
 * <p>故意不返 template_content (太大, 前端按 name 选模板后再去 preview 渲染).
 */
@Data
public class DockerfileTemplateResponse {

    private Long id;
    private String name;
    private String displayName;
    private String language;
    private String buildTool;
    private String variableSchema;
    private Integer version;
    private Integer isBuiltin;

    public static DockerfileTemplateResponse from(DockerfileTemplate t) {
        if (t == null) return null;
        DockerfileTemplateResponse r = new DockerfileTemplateResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setDisplayName(t.getDisplayName());
        r.setLanguage(t.getLanguage());
        r.setBuildTool(t.getBuildTool());
        r.setVariableSchema(t.getVariableSchema());
        r.setVersion(t.getVersion());
        r.setIsBuiltin(t.getIsBuiltin());
        return r;
    }
}
