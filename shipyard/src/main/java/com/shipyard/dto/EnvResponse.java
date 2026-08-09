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

import com.shipyard.entity.Env;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 环境响应体 — GET / POST / PUT /api/envs 统一返回.
 *
 * <p>关键: <b>{@code workerToken} 不回显</b> — 只暴露 {@code hasWorkerToken: boolean}.
 */
@Data
public class EnvResponse {

    private Long id;
    private String name;
    private String displayName;
    private String clusterType;
    private String k8sNamespace;
    private String workerUrl;
    private Boolean hasWorkerToken;
    private Integer isProduction;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static EnvResponse from(Env e) {
        if (e == null) return null;
        EnvResponse r = new EnvResponse();
        r.setId(e.getId());
        r.setName(e.getName());
        r.setDisplayName(e.getDisplayName());
        r.setClusterType(e.getClusterType());
        r.setK8sNamespace(e.getK8sNamespace());
        r.setWorkerUrl(e.getWorkerUrl());
        r.setHasWorkerToken(
                e.getWorkerTokenEnc() != null && !e.getWorkerTokenEnc().isEmpty());
        r.setIsProduction(e.getIsProduction());
        r.setCreatedAt(e.getCreatedAt());
        r.setUpdatedAt(e.getUpdatedAt());
        return r;
    }
}
