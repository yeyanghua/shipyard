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

package com.shipyard.controller;

import com.shipyard.dto.ApiResponse;
import com.shipyard.dto.DeployCreateRequest;
import com.shipyard.dto.DeployResponse;
import com.shipyard.dto.DeploySnapshotResponse;
import com.shipyard.service.DeployService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deploy Controller — /api/...
 *
 * <p>7 个端点 (M9):
 * <pre>
 *   POST   /api/projects/{id}/deployments              触发部署
 *   GET    /api/deployments/{id}                       部署详情
 *   GET    /api/deployments?projectId=&envId=          列表 (分页)
 *   GET    /api/deployments/{id}/snapshots             某 deploy 的 snapshot (回滚列表)
 *   GET    /api/deployments/snapshots?projectId=&envId=  跨 deploy snapshot (高级模式)
 *   POST   /api/deployments/{id}/rollback/{snapshotId} 一键回滚
 *   POST   /api/deployments/{id}/cancel                取消 (PENDING/RUNNING 状态)
 *   GET    /api/deployments/{id}/live-manifest         k8s 真生效的 manifest (高级模式 diff 用)
 * </pre>
 *
 * <p>注: 实际是 8 个端点 (含 live-manifest), M9 计划写 7 个是我多加了 1 个.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DeployController {

    private final DeployService deployService;

    /**
     * 触发一次部署 — POST /api/projects/{id}/deployments.
     */
    @PostMapping("/api/projects/{id}/deployments")
    public ApiResponse<DeployResponse> createDeploy(
            @PathVariable("id") Long projectId,
            @RequestBody @Valid DeployCreateRequest request
    ) {
        log.info("触发部署: projectId={} envId={} buildRecordId={} imageTag={}",
                projectId, request.getEnvId(), request.getBuildRecordId(), request.getImageTag());
        return ApiResponse.ok(deployService.createDeploy(projectId, request));
    }

    /**
     * 部署详情 — GET /api/deployments/{id}.
     */
    @GetMapping("/api/deployments/{id}")
    public ApiResponse<DeployResponse> getDeploy(@PathVariable Long id) {
        return ApiResponse.ok(deployService.getDeploy(id));
    }

    /**
     * 部署列表 — GET /api/deployments?projectId=&envId=&page=&size=.
     */
    @GetMapping("/api/deployments")
    public ApiResponse<List<DeployResponse>> listDeploys(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long envId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(deployService.listDeploys(projectId, envId, page, size));
    }

    /**
     * 某 deploy 下的 snapshot 列表 (回滚用) — GET /api/deployments/{id}/snapshots.
     */
    @GetMapping("/api/deployments/{id}/snapshots")
    public ApiResponse<List<DeploySnapshotResponse>> listSnapshots(@PathVariable Long id) {
        return ApiResponse.ok(deployService.listSnapshots(id));
    }

    /**
     * 跨 deploy snapshot 列表 (project + env 维度) — GET /api/deployments/snapshots?projectId=&envId=.
     *
     * <p>高级模式: 列出 project + env 下所有历史 snapshot, 跨 deploy 视图.
     */
    @GetMapping("/api/deployments/snapshots")
    public ApiResponse<List<DeploySnapshotResponse>> listSnapshotsByProjectEnv(
            @RequestParam Long projectId,
            @RequestParam Long envId
    ) {
        return ApiResponse.ok(deployService.listSnapshotsByProjectEnv(projectId, envId));
    }

    /**
     * 一键回滚 — POST /api/deployments/{id}/rollback/{snapshotId}.
     */
    @PostMapping("/api/deployments/{id}/rollback/{snapshotId}")
    public ApiResponse<DeployResponse> rollback(
            @PathVariable Long id,
            @PathVariable Long snapshotId,
            @RequestParam(required = false, defaultValue = "unknown") String triggeredBy
    ) {
        log.info("回滚部署: id={} snapshotId={} triggeredBy={}", id, snapshotId, triggeredBy);
        return ApiResponse.ok(deployService.rollback(id, snapshotId, triggeredBy));
    }

    /**
     * 取消部署 — POST /api/deployments/{id}/cancel.
     */
    @PostMapping("/api/deployments/{id}/cancel")
    public ApiResponse<DeployResponse> cancelDeploy(@PathVariable Long id) {
        log.info("取消部署: id={}", id);
        return ApiResponse.ok(deployService.cancelDeploy(id));
    }

    /**
     * k8s 真生效的 manifest (高级模式 diff 用) — GET /api/deployments/{id}/live-manifest.
     *
     * <p>返 raw yaml 字符串 (不是 ApiResponse 包装), 因为前端 diff 组件直接吃 yaml 字符串.
     */
    @GetMapping("/api/deployments/{id}/live-manifest")
    public String getLiveManifest(@PathVariable Long id) {
        return deployService.getLiveManifest(id);
    }
}
