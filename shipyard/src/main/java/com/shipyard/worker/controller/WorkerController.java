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

package com.shipyard.worker.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shipyard.dto.ApiResponse;
import com.shipyard.dto.PageResponse;
import com.shipyard.worker.dto.WorkerHeartbeatRequest;
import com.shipyard.worker.dto.WorkerRegisterRequest;
import com.shipyard.worker.dto.WorkerRegisterResponse;
import com.shipyard.worker.dto.WorkerResponse;
import com.shipyard.worker.entity.Worker;
import com.shipyard.worker.service.WorkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Worker Controller — /api/workers.
 *
 * <p>7 个端点 (M8.2):
 * <pre>
 *   POST   /api/workers/register                  worker 主动注册, 返 ID
 *   POST   /api/workers/{id}/heartbeat            worker 30s 上报
 *   GET    /api/workers                           列表 (分页, envId 可选过滤)
 *   GET    /api/workers/{id}                      详情
 *   DELETE /api/workers/{id}                      软删
 *   GET    /api/workers/{id}/cluster/namespaces  代理 worker 拿 ns
 *   GET    /api/workers/{id}/cluster/pods         代理 worker 拿 pod
 *   GET    /api/workers/{id}/cluster/deployments  代理 worker 拿 deployment
 * </pre>
 *
 * <p>注意: cluster/pods 和 cluster/deployments 都接 {@code ?namespace=xxx}.
 */
@Slf4j
@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    // ==================== Worker 主动调 shipyard (注册 + 心跳) ====================

    /**
     * Worker 主动注册.
     *
     * <p>worker 启动后调这个, shipyard 写表, 返 worker ID 给 worker.
     * Worker 用这个 ID 后续发心跳 + 接受 shipyard 调度的凭据.
     */
    @PostMapping("/register")
    public ApiResponse<WorkerRegisterResponse> register(@RequestBody @Valid WorkerRegisterRequest req) {
        log.info("worker 注册: name={} env={} url={}", req.getWorkerName(), req.getEnv(), req.getWorkerUrl());
        return ApiResponse.ok(workerService.register(req));
    }

    /**
     * Worker 心跳上报.
     */
    @PostMapping("/{id}/heartbeat")
    public ApiResponse<Void> heartbeat(@PathVariable Long id, @RequestBody @Valid WorkerHeartbeatRequest req) {
        workerService.heartbeat(id, req);
        return ApiResponse.ok();
    }

    // ==================== shipyard Web 调 (CRUD) ====================

    @GetMapping
    public ApiResponse<PageResponse<WorkerResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long envId
    ) {
        Page<Worker> p = workerService.list(page, size, envId);
        return ApiResponse.ok(PageResponse.from(p, WorkerResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkerResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(WorkerResponse.from(workerService.get(id)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        workerService.delete(id);
        return ApiResponse.ok();
    }

    // ==================== shipyard Web 调 (集群信息代理 worker) ====================

    @GetMapping("/{id}/cluster/namespaces")
    public ApiResponse<List<Map<String, Object>>> listNamespaces(@PathVariable Long id) {
        return ApiResponse.ok(workerService.listNamespaces(id));
    }

    @GetMapping("/{id}/cluster/pods")
    public ApiResponse<List<Map<String, Object>>> listPods(
            @PathVariable Long id,
            @RequestParam(defaultValue = "default") String namespace
    ) {
        return ApiResponse.ok(workerService.listPods(id, namespace));
    }

    @GetMapping("/{id}/cluster/deployments")
    public ApiResponse<List<Map<String, Object>>> listDeployments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "default") String namespace
    ) {
        return ApiResponse.ok(workerService.listDeployments(id, namespace));
    }

    /**
     * M9 commit-16: 拿 worker 自己的 deployment 状态 (replicas + pod 列表).
     *
     * <p>前端用这个展示 "1 worker DB row 对应 N 个 k8s pod" 关系.
     * 返 {@code Map<String, Object>} — 含 workerName/namespace/replicas/readyReplicas/pods[].
     */
    @GetMapping("/{id}/cluster/worker-pods")
    public ApiResponse<Map<String, Object>> listWorkerPods(@PathVariable Long id) {
        return ApiResponse.ok(workerService.listWorkerPods(id));
    }
}
