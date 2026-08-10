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

package com.shipyard.worker.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shipyard.worker.dto.WorkerHeartbeatRequest;
import com.shipyard.worker.dto.WorkerRegisterRequest;
import com.shipyard.worker.dto.WorkerRegisterResponse;
import com.shipyard.worker.entity.Worker;

import java.util.List;
import java.util.Map;

/**
 * Worker Service — shipyard 端 worker 生命周期管理.
 *
 * <p>M8.2 阶段:
 * <ul>
 *   <li>register: worker 主动调 shipyard, shipyard 查 env → 写 worker 表 → 返 ID</li>
 *   <li>heartbeat: 30s/次, 更新 last_heartbeat_at + status</li>
 *   <li>list / get: UI 展示用</li>
 *   <li>cluster 代理: shipyard Web 调 worker 读类接口, 透传响应</li>
 * </ul>
 */
public interface WorkerService {

    /**
     * Worker 主动注册.
     *
     * @param req worker 端发上来的注册请求
     * @return shipyard 分配的 worker ID + 心跳间隔
     */
    WorkerRegisterResponse register(WorkerRegisterRequest req);

    /**
     * Worker 心跳 — 更新 last_heartbeat_at + status.
     *
     * @param workerId worker ID
     * @param req 心跳请求
     */
    void heartbeat(Long workerId, WorkerHeartbeatRequest req);

    /**
     * 列表 (分页, 可选 envId 过滤).
     */
    Page<Worker> list(int page, int size, Long envId);

    /**
     * 详情 — 不存在抛 BusinessException(NOT_FOUND).
     */
    Worker get(Long id);

    /**
     * 软删 — 调 WorkerMapper.deleteById.
     */
    void delete(Long id);

    // ==================== 集群读类代理 (透传 worker 响应) ====================

    /**
     * 列出所有 namespace — 调 worker 拿.
     */
    List<Map<String, Object>> listNamespaces(Long workerId);

    /**
     * 列出指定 ns 的 pod — 调 worker 拿.
     */
    List<Map<String, Object>> listPods(Long workerId, String namespace);

    /**
     * 列出指定 ns 的 deployment — 调 worker 拿.
     */
    List<Map<String, Object>> listDeployments(Long workerId, String namespace);
}
