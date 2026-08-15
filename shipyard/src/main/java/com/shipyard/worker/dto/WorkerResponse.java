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
 * Worker 响应统一格式 — GET /api/workers 系列端点.
 *
 * <p>M9.5: 16 字段全返回 (跟 Worker entity 一致, UI 直接展示).
 * <p>token 明文不返回, 只返 hasToken 标志位 (UI 调 regenerate-token 时才返明文).
 */
@Data
public class WorkerResponse {

    private Long id;
    private Long envId;
    private String name;
    private String podName;
    private String description;
    private String workerUrl;
    private Boolean hasToken;       // true = 已生成 token, 不返明文
    private String status;          // PLANNED / PROVISIONING / ONLINE / OFFLINE / UNHEALTHY
    private String health;          // HEALTHY / UNHEALTHY
    private String healthDetail;
    private LocalDateTime lastHeartbeatAt;
    private String version;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    /** 心跳是否新鲜 (last_heartbeat_at 在 90s 内). 方便前端 UI 标红/标绿. */
    private Boolean heartbeatFresh;

    public static WorkerResponse from(Worker w) {
        if (w == null) return null;
        WorkerResponse r = new WorkerResponse();
        r.setId(w.getId());
        r.setEnvId(w.getEnvId());
        r.setName(w.getName());
        r.setPodName(w.getPodName());
        r.setDescription(w.getDescription());
        r.setWorkerUrl(w.getWorkerUrl());
        r.setHasToken(w.getWorkerTokenHash() != null && !w.getWorkerTokenHash().isEmpty());
        r.setStatus(w.getStatus());
        r.setHealth(w.getHealth());
        r.setHealthDetail(w.getHealthDetail());
        r.setLastHeartbeatAt(w.getLastHeartbeatAt());
        r.setVersion(w.getVersion());
        r.setCreatedBy(w.getCreatedBy());
        r.setCreatedAt(w.getCreatedAt());
        r.setUpdatedBy(w.getUpdatedBy());
        r.setUpdatedAt(w.getUpdatedAt());
        r.setHeartbeatFresh(w.getLastHeartbeatAt() != null
                && w.getLastHeartbeatAt().isAfter(LocalDateTime.now().minusSeconds(90)));
        return r;
    }
}
