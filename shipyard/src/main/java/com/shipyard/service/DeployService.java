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

package com.shipyard.service;

import com.shipyard.dto.DeployCreateRequest;
import com.shipyard.dto.DeployResponse;
import com.shipyard.dto.DeploySnapshotResponse;
import java.util.List;

/**
 * 部署业务服务 — 跟 worker 集成的 deploy 链路主入口.
 *
 * <p>核心流程 (createDeploy):
 * <ol>
 *   <li>校验 projectId / envId 存在, buildRecordId 合法 (可选)</li>
 *   <li>解析 imageTag (build_record.imageTag 或 req.imageTag)</li>
 *   <li>查 env + pipeline_template, 拼 namespace + 渲染 yaml</li>
 *   <li>算 yaml sha256</li>
 *   <li>WorkerSelector 选 worker (按 envId, 被动路由)</li>
 *   <li>写 deploy_record (PENDING) + deploy_snapshot (yaml + sha256)</li>
 *   <li>调 worker (commit-5 补 WorkerClient.deploy), 异步等回调</li>
 *   <li>返 DeployResponse 给前端</li>
 * </ol>
 *
 * <p>跟 BuildService 平级 — 独立链路, 各自状态机, 中间靠 imageTag 衔接 (M9 fix-commit 决策 1).
 */
public interface DeployService {

    /**
     * 触发一次部署 — POST /api/projects/{id}/deployments.
     */
    DeployResponse createDeploy(Long projectId, DeployCreateRequest request);

    /**
     * 查部署详情 — GET /api/deployments/{id}.
     */
    DeployResponse getDeploy(Long id);

    /**
     * 列部署 (按 projectId, 可选 envId 过滤) — GET /api/deployments?projectId=&envId=.
     */
    List<DeployResponse> listDeploys(Long projectId, Long envId, int page, int size);

    /**
     * 列 deploy 下的所有 snapshot — GET /api/deployments/{id}/snapshots (回滚列表).
     */
    List<DeploySnapshotResponse> listSnapshots(Long deployId);

    /**
     * 列 project + env 下的所有 snapshot (跨 deploy, 高级模式用) — GET /api/deployments/{id}/snapshots?projectEnv=1.
     */
    List<DeploySnapshotResponse> listSnapshotsByProjectEnv(Long projectId, Long envId);

    /**
     * 一键回滚 — POST /api/deployments/{id}/rollback/{snapshotId}.
     *
     * <p>新创建 deploy_record (status=PENDING, 复用同一 imageTag), 把 snapshot 的 yaml 重发.
     */
    DeployResponse rollback(Long deployId, Long snapshotId, String triggeredBy);

    /**
     * 取消部署 — POST /api/deployments/{id}/cancel.
     *
     * <p>只能从 PENDING / RUNNING 取消, 终态报 BAD_REQUEST.
     */
    DeployResponse cancelDeploy(Long id);

    /**
     * 查 k8s 真生效的 manifest — GET /api/deployments/{id}/live-manifest (高级模式 diff 用).
     *
     * <p>委托 worker 调 k8s API 拿 (commit-5 补 WorkerClient.getManifest).
     */
    String getLiveManifest(Long deployId);

    // ==================== worker 回调 / 内部用 ====================

    /**
     * PENDING → RUNNING — DeployService 自己调 (commit-5 调 WorkerClient 后) 或 mock 用.
     */
    void markRunning(Long id);

    /**
     * 任意终态 (SUCCESS / FAILED / TIMEOUT / CANCELED) — worker 回调 / 内部用.
     */
    void markFinished(Long id, String status, String errorMessage);
}
