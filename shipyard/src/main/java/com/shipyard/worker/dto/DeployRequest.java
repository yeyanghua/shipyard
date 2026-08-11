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

package com.shipyard.worker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 部署任务请求体 — shipyard → worker 调 {@code POST /api/v1/tasks/deploy} 用.
 *
 * <p>M9 commit-5: shipyard 后端拼好 yaml 后包这个 body 给 worker, worker 走
 * client-go (DynamicClient + unstructured) 真 apply 资源到 k8s.
 *
 * <p>必填:
 * <ul>
 *   <li>{@code deployRecordId} — shipyard 端 deploy_record.id, worker 异步回调用</li>
 *   <li>{@code namespace} — 目标 namespace (例 shipyard-shanghai-dev)</li>
 *   <li>{@code yaml} — shipyard 渲染完的完整 K8s yaml (Deployment + Service 等, --- 分隔)</li>
 * </ul>
 */
@Data
public class DeployRequest {

    /** shipyard 端 deploy_record.id, worker apply 完回调 shipyard 用 */
    @NotNull
    private Long deployRecordId;

    /** 目标 namespace (例 shipyard-shanghai-dev) */
    @NotBlank
    private String namespace;

    /** shipyard 渲染完的 K8s yaml (LONGTEXT) */
    @NotBlank
    private String yaml;

    /** 资源名 (K8s Deployment name, 例 myapp-dev) — worker apply 时拿做 sanity check */
    private String resourceName;
}
