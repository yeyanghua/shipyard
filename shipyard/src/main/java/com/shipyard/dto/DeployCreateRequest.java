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

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 触发一次部署的请求体 — POST /api/projects/{id}/deployments.
 *
 * <p>两个二选一字段:
 * <ul>
 *   <li>{@code buildRecordId} — 用已有 build_record 提供的 image_tag</li>
 *   <li>{@code imageTag} — 手动指定镜像 (跳过 build, 直接 deploy, 高级模式)</li>
 * </ul>
 *
 * <p>必填:
 * <ul>
 *   <li>{@code envId} — 部署到哪个环境 (1 env ↔ 1 cluster, WorkerSelector 按 env 选 worker)</li>
 *   <li>buildRecordId 或 imageTag 二选一</li>
 * </ul>
 *
 * <p>可选:
 * <ul>
 *   <li>{@code replicas} — 覆盖 pipeline_template.replicas, 不传走 template 默认</li>
 *   <li>{@code envVars} — 临时覆盖 env vars (高级模式, V1.5)</li>
 *   <li>{@code triggeredBy} — 触发人 (默认 'unknown')</li>
 * </ul>
 */
@Data
public class DeployCreateRequest {

    /** 部署到哪个 env (必填) */
    @NotNull
    private Long envId;

    /** 用已有 build_record 的 image_tag (可选, 跟 imageTag 二选一) */
    private Long buildRecordId;

    /** 直接指定镜像 (高级模式, 跳过 build) */
    private String imageTag;

    /** 覆盖副本数 (可选, 不传走 pipeline_template.replicas) */
    private Integer replicas;

    /** 临时 env vars 覆盖 (V1.5, M9 留字段不接) */
    private java.util.Map<String, String> envVars;

    /** 触发人 (可选, 不传走 "unknown") */
    private String triggeredBy;
}
