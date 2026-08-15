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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 worker 请求 — POST /api/envs/{envId}/workers.
 *
 * <p>M9.5: 用户在 shipyard UI 创建 worker (预登记), shipyard 自动生成 token,
 * 返 token 明文 (一次性, 用户复制到 k8s manifest 的 WORKER_TOKEN env var).
 */
@Data
public class WorkerCreateRequest {

    /** shipyard 内部展示名 (同 env 下唯一). */
    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]+$", message = "只能包含小写字母/数字/中划线")
    @Size(max = 64)
    private String name;

    /**
     * k8s pod metadata.name (同 env 下唯一, 跟 register 严格匹配).
     *
     * <p>用户创建 worker 时, 应该已经知道要部署的 pod 名 (跟 k8s manifest 一致).
     * 命名建议: {@code shipyard-worker-<env>-<n>} (例: shipyard-worker-dev-1).
     */
    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]+$", message = "只能包含小写字母/数字/中划线 (跟 k8s pod name 规则一致)")
    @Size(max = 128)
    private String podName;

    /** 备注 / 描述 (可选). */
    @Size(max = 256)
    private String description;
}
