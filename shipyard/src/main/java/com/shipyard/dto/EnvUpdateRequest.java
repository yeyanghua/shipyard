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
 * 更新环境请求体 — PUT /api/envs/{id}.
 *
 * <p>所有字段选填.
 */
@Data
public class EnvUpdateRequest {

    @Pattern(regexp = "^[a-z0-9-]+$", message = "只能包含小写字母/数字/中划线")
    @Size(max = 64)
    private String name;

    @Size(max = 128)
    private String displayName;

    @Pattern(regexp = "^(k8s)$", message = "V1 只支持 k8s")
    private String clusterType;

    @Size(max = 64)
    private String k8sNamespace;

    @Size(max = 512)
    private String workerUrl;

    @Size(max = 512)
    private String workerToken;

    private Integer isProduction;
}
