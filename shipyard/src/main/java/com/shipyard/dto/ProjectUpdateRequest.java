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

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新项目请求体 — PUT /api/projects/{id}.
 *
 * <p>所有字段选填, 没传的字段保持不变. 业务层走 {@code BeanUtils.copyProperties} + null skip.
 */
@Data
public class ProjectUpdateRequest {

    @Pattern(regexp = "^[a-z0-9-]+$", message = "只能包含小写字母/数字/中划线")
    @Size(max = 64)
    private String name;

    @Size(max = 128)
    private String displayName;

    @Pattern(regexp = "^(gitlab|gitee)$", message = "必须是 gitlab 或 gitee")
    private String repoProvider;

    @Size(max = 512)
    private String repoUrl;

    @Size(max = 512)
    private String repoToken;   // 填了则覆盖并重新加密, 留空/null 不变

    @Size(max = 64)
    private String defaultBranch;

    @Pattern(regexp = "^(java_maven|java_gradle|node_pnpm|python_poetry|other)$",
        message = "必须是 java_maven/java_gradle/node_pnpm/python_poetry/other 之一")
    private String projectType;

    private String projectMeta;

    @Size(max = 512)
    private String description;
}
