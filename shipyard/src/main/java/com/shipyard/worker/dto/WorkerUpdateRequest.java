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

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 worker 请求 — PUT /api/workers/{id}.
 *
 * <p>M9.5: V1 阶段只允许改 description (name / podName / token 改的成本高, 不让改).
 * 要改 name / podName 走 "删 worker + 重新创建" 流程.
 */
@Data
public class WorkerUpdateRequest {

    /** 备注 / 描述 (可选, 不传 = 不改). */
    @Size(max = 256)
    private String description;
}
