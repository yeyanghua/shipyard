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
 * <p>M9.5 redesign: 删 workerUrl / hasWorkerToken / k8sNamespace 字段, env 只管集群元数据.
 * worker 相关的 token / URL 走 worker 表.
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
     * 该 env 下的 worker 数量 (M9.5 新加, 方便 UI 一眼看到 env 部署了几个 worker).
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
        return r;
    }
}
