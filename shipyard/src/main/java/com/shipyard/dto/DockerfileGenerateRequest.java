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
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 生成 / 预览 Dockerfile 请求.
 *
 * <p>两个端点共用 (preview + generate):
 * <ul>
 *   <li>{@code templateName} — 必填, 例 "java_maven_jdk21"</li>
 *   <li>{@code variables} — 模板变量值, 例 {jarName: "app.jar", port: 8080}</li>
 *   <li>{@code repoBranch} — 目标分支 (V1 仅记录, V1.5 真 commit)</li>
 *   <li>{@code commitMessage} — 提交 message</li>
 * </ul>
 */
@Data
public class DockerfileGenerateRequest {

    @NotBlank
    @Size(max = 64)
    private String templateName;

    /** 模板变量值, key 不存在时 Service 渲染时填空字符串 */
    private Map<String, String> variables;

    /** 目标分支, V1 demo 默认 "main" */
    @Size(max = 64)
    private String repoBranch;

    @Size(max = 512)
    private String commitMessage;
}
