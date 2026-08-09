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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建项目请求体 — POST /api/projects.
 *
 * <p>校验规则:
 * <ul>
 *   <li>{@code name} — 必填, 小写字母/数字/中划线, 1-64 字符</li>
 *   <li>{@code displayName} — 必填, 1-128 字符</li>
 *   <li>{@code repoProvider} — 必填, {@code gitlab} 或 {@code gitee}</li>
 *   <li>{@code repoUrl} — 必填, 1-512 字符</li>
 *   <li>{@code repoToken} — 选填, 1-512 字符 (填了则加密)</li>
 *   <li>{@code projectType} — 必填, 5 种之一</li>
 *   <li>{@code projectMeta} — 选填, JSON 字符串 (业务层 parse)</li>
 *   <li>{@code description} — 选填, 最多 512 字符</li>
 * </ul>
 */
@Data
public class ProjectCreateRequest {

    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]+$", message = "只能包含小写字母/数字/中划线")
    @Size(max = 64)
    private String name;

    @NotBlank
    @Size(max = 128)
    private String displayName;

    @NotBlank
    @Pattern(regexp = "^(gitlab|gitee)$", message = "必须是 gitlab 或 gitee")
    private String repoProvider;

    @NotBlank
    @Size(max = 512)
    private String repoUrl;

    @Size(max = 512)
    private String repoToken;   // 明文, Service 加密

    @Size(max = 64)
    private String defaultBranch = "main";

    @NotBlank
    @Pattern(regexp = "^(java_maven|java_gradle|node_pnpm|python_poetry|other)$",
        message = "必须是 java_maven/java_gradle/node_pnpm/python_poetry/other 之一")
    private String projectType;

    /**
     * Project Meta — 灵活接收 object 或 string.
     *
     * <p>V1 阶段: DTO 字段是 {@code Object} (Jackson 自动反序列化为 {@code LinkedHashMap}),
     * Service 层 {@code ObjectMapper.writeValueAsString(projectMeta)} 序列化成字符串入库.
     * 这样前端传 JSON 对象或字符串都接受, 不用纠结引号转义.
     *
     * <p>V1.5 改: 走 MyBatis-Plus JacksonTypeHandler 存 JSON 字段, 直接反序列化成 Map.
     */
    private Object projectMeta;

    @Size(max = 512)
    private String description;
}
