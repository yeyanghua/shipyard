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

import lombok.Data;

/**
 * Worker 注册响应 — POST /api/workers/register 返给 worker.
 *
 * <p>包含 shipyard 分配的 worker ID + 心跳间隔 (秒). worker 用 ID 发心跳.
 */
@Data
public class WorkerRegisterResponse {

    /** shipyard 分配的 worker 主键. */
    private Long workerId;

    /** 心跳间隔 (秒), worker 用这个值调 setInterval 心跳. */
    private Integer heartbeatIntervalSec;
}
