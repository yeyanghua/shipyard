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
 * Worker 心跳请求 — POST /api/workers/{id}/heartbeat.
 *
 * <p>worker 每 30s 上报. shipyard 更新 last_heartbeat_at + status.
 */
@Data
public class WorkerHeartbeatRequest {

    /** worker ID (跟 URL path 里的 ID 一致, 冗余方便 worker 端 debug). */
    @NotNull
    private Long workerId;

    /** 状态: online / unhealthy. 默认 online. */
    @NotBlank
    private String status;

    /** (M13+) CPU 负载, M8.2 不强要. */
    private String cpuLoad;

    /** (M13+) 内存使用, M8.2 不强要. */
    private String memoryUsage;

    /** (M13+) 看到的 pod 数, M8.2 不强要. */
    private Integer podsCount;
}
