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
import com.shipyard.worker.dto.WorkerCreateRequest;
import com.shipyard.worker.dto.WorkerHeartbeatRequest;
import com.shipyard.worker.dto.WorkerRegisterRequest;
import com.shipyard.worker.dto.WorkerRegisterResponse;
import com.shipyard.worker.dto.WorkerTokenResponse;
import com.shipyard.worker.dto.WorkerUpdateRequest;
import com.shipyard.worker.entity.Worker;

import java.util.List;
import java.util.Map;

/**
 * Worker Service — M9.5 redesign.
 *
 * <p>核心变化 (vs M8.2 / M9):
 * <ul>
 *   <li>新增 CRUD: create / update / regenerateToken (UI 调)</li>
 *   <li>register 改严格模式 (必须先有预登记 row + token 校验)</li>
 *   <li>1 worker = 1 pod (新增 podName 唯一约束)</li>
 *   <li>状态机扩展: PLANNED / PROVISIONING / ONLINE / OFFLINE / UNHEALTHY</li>
 * </ul>
 */
public interface WorkerService {

    // ============================================================
    // UI 调 (CRUD)
    // ============================================================

    /**
     * 创建 worker (预登记) — 用户在 shipyard UI 填 name + podName.
     *
     * <p>shipyard 自动生成 32 字节 token, 存 SHA-256 哈希, 返明文 (一次性).
     *
     * @param envId       所属环境 ID
     * @param req         创建请求 (name / podName / description)
     * @param currentUser 当前操作人 (V1 demo 默认 'system')
     * @return 含明文 token, 用户复制到 k8s manifest
     */
    WorkerTokenResponse create(Long envId, WorkerCreateRequest req, String currentUser);

    /**
     * 重新生成 token — 旧 token 立即失效.
     *
     * @param workerId    worker ID
     * @param currentUser 当前操作人
     * @return 新 token 明文
     */
    WorkerTokenResponse regenerateToken(Long workerId, String currentUser);

    /**
     * 列表 (分页, 可选 envId 过滤).
     */
    Page<Worker> list(int page, int size, Long envId);

    /**
     * 详情 — 不存在抛 BusinessException(NOT_FOUND).
     */
    Worker get(Long id);

    /**
     * 更新 (V1 阶段只允许改 description).
     */
    Worker update(Long id, WorkerUpdateRequest req, String currentUser);

    /**
     * 软删 — 调 WorkerMapper.deleteById (MyBatis-Plus @TableLogic 自动改 deleted=1).
     */
    void delete(Long id);

    // ============================================================
    // Worker 主动调 shipyard
    // ============================================================

    /**
     * Worker 主动注册 — 严格模式: 必须先有预登记 row + token 校验通过.
     *
     * @param req worker 端发上来的注册请求 (含 podName / env / workerUrl / workerToken)
     * @return shipyard 分配的 worker ID + 心跳间隔
     * @throws BusinessException 404 (没预登记) / 401 (token 错) / 500
     */
    WorkerRegisterResponse register(WorkerRegisterRequest req);

    /**
     * Worker 心跳 — 更新 last_heartbeat_at + status + health.
     */
    void heartbeat(Long workerId, WorkerHeartbeatRequest req);

    // ============================================================
    // 集群读类代理 (shipyard → worker)
    // ============================================================

    List<Map<String, Object>> listNamespaces(Long workerId);

    List<Map<String, Object>> listPods(Long workerId, String namespace);

    List<Map<String, Object>> listDeployments(Long workerId, String namespace);

    /**
     * M9 commit-16: 拿 worker 自己的 deployment 状态.
     * <p>M9.5: 1 worker = 1 pod, 返 1 个 pod 数组, 兼容旧 UI.
     */
    Map<String, Object> listWorkerPods(Long workerId);
}
