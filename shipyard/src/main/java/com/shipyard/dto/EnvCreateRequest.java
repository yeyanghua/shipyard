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
 * 创建环境请求体 — POST /api/envs.
 *
 * <p>V1 阶段 (V5 撤回后): 恢复 workerUrl + k8sNamespace 字段 (V3 模式).
 * workerTokenEnc 不让前端填 — shipyard 后端自动生成 + AES-256 加密存.
 *
 * <p>字段:
 * <ul>
 *   <li>name / displayName / clusterType / isProduction — env 集群元数据</li>
 *   <li>workerUrl (可选) — 该 env 下的 worker 服务 URL, 留空走 shipyard 内部默认</li>
 *   <li>k8sNamespace (可选) — env 对应 k8s namespace, 留空走 env.name 默认</li>
 * </ul>
 */
@Data
public class EnvCreateRequest {

    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]+$", message = "只能包含小写字母/数字/中划线")
    @Size(max = 64)
    private String name;

    @NotBlank
    @Size(max = 128)
    private String displayName;

    @Pattern(regexp = "^(k8s)$", message = "V1 只支持 k8s")
    private String clusterType = "k8s";

    /** null=0 (dev), 1=生产. */
    private Integer isProduction;

    /**
     * 该 env 下的 worker 服务 URL (V1 阶段可选, shipyard 内部维护).
     * 留空: shipyard 默认 {@code http://shipyard-tunnel.shipyard-tunnel.svc.cluster.local:30090}
     * (走 shipyard-tunnel 跳板, V1 阶段演示用).
     */
    @Size(max = 512)
    private String workerUrl;

    /**
     * env 对应 k8s namespace. 留空: 默认 = env.name.
     */
    @Size(max = 64)
    private String k8sNamespace;
}
