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
 * <p>V1 阶段 (V5 撤回后) env 表自管 workerUrl / k8sNamespace (跟 V3 一致).
 * workerTokenEnc 不返前端 (V1 阶段 shipyard 后端内部存, 不暴露明文/token 摘要).
 */
@Data
public class EnvResponse {

    private Long id;
    private String name;
    private String displayName;
    private String clusterType;
    private Integer isProduction;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 该 env 下的 worker 服务 URL (V1 阶段 shipyard 内部维护, 演示 K8s 调谁用).
     */
    private String workerUrl;

    /**
     * 该 env 对应的 k8s namespace (V1 阶段 shipyard 调 K8s API 用).
     */
    private String k8sNamespace;

    /**
     * 该 env 下的 worker 数量 (V1 阶段 in-process 模拟, 创 env 时自动建 1 个).
     */
    private Long workerCount;

    public static EnvResponse from(Env e) {
        if (e == null) return null;
        EnvResponse r = new EnvResponse();
        r.setId(e.getId());
        r.setName(e.getName());
        r.setDisplayName(e.getDisplayName());
        r.setClusterType(e.getClusterType());
        r.setIsProduction(e.getIsProduction());
        r.setCreatedAt(e.getCreatedAt());
        r.setUpdatedAt(e.getUpdatedAt());
        r.setWorkerUrl(e.getWorkerUrl());
        r.setK8sNamespace(e.getK8sNamespace());
        return r;
    }
}
