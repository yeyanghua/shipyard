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

import com.shipyard.worker.entity.Worker;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Worker 响应体 — GET /api/workers 系列统一返回.
 *
 * <p>token 哈希不回显 (内部字段).
 */
@Data
public class WorkerResponse {

    private Long id;
    private Long envId;
    private String workerUrl;
    private String status;
    private String version;
    private LocalDateTime lastHeartbeatAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 心跳是否新鲜 (last_heartbeat_at 在 90s 内). 方便前端 UI 标红/标绿. */
    private Boolean heartbeatFresh;

    public static WorkerResponse from(Worker w) {
        if (w == null) return null;
        WorkerResponse r = new WorkerResponse();
        r.setId(w.getId());
        r.setEnvId(w.getEnvId());
        r.setWorkerUrl(w.getWorkerUrl());
        r.setStatus(w.getStatus());
        r.setVersion(w.getVersion());
        r.setLastHeartbeatAt(w.getLastHeartbeatAt());
        r.setCreatedAt(w.getCreatedAt());
        r.setUpdatedAt(w.getUpdatedAt());
        r.setHeartbeatFresh(w.getLastHeartbeatAt() != null
                && w.getLastHeartbeatAt().isAfter(LocalDateTime.now().minusSeconds(90)));
        return r;
    }
}
