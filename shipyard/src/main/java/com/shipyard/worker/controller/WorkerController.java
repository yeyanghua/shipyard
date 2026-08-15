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
import com.shipyard.worker.dto.WorkerCreateRequest;
import com.shipyard.worker.dto.WorkerHeartbeatRequest;
import com.shipyard.worker.dto.WorkerRegisterRequest;
import com.shipyard.worker.dto.WorkerRegisterResponse;
import com.shipyard.worker.dto.WorkerResponse;
import com.shipyard.worker.dto.WorkerTokenResponse;
import com.shipyard.worker.dto.WorkerUpdateRequest;
import com.shipyard.worker.entity.Worker;
import com.shipyard.worker.service.WorkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Worker Controller — /api/workers.
 *
 * <p>M9.5 redesign 后的端点 (vs M8.2 7 个 → M9.5 11 个):
 * <pre>
 *   UI 调 (CRUD):
 *     POST   /api/envs/{envId}/workers              创建 worker (预登记), 返明文 token (一次性)
 *     GET    /api/workers                           列表 (分页, envId 可选过滤)
 *     GET    /api/workers/{id}                      详情
 *     PUT    /api/workers/{id}                      更新 (V1 只能改 description)
 *     DELETE /api/workers/{id}                      软删
 *     POST   /api/workers/{id}/regenerate-token     重新生成 token, 旧 token 立即失效
 *
 *   Worker 主动调 shipyard (注册 + 心跳):
 *     POST   /api/workers/register                  worker 主动注册, 严格模式 (M9.5)
 *     POST   /api/workers/{id}/heartbeat            worker 30s 上报
 *
 *   shipyard Web 调 (集群信息代理 worker):
 *     GET    /api/workers/{id}/cluster/namespaces  代理 worker 拿 ns
 *     GET    /api/workers/{id}/cluster/pods         代理 worker 拿 pod (?namespace=xxx)
 *     GET    /api/workers/{id}/cluster/deployments  代理 worker 拿 deployment
 *     GET    /api/workers/{id}/cluster/worker-pods  代理 worker 拿 worker pod 列表 (M9 兼容)
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    // ==================== Worker 主动调 shipyard (注册 + 心跳) ====================

    /**
     * Worker 主动注册 (M9.5 严格模式).
     *
     * <p>worker 启动后调这个, shipyard 用 (env_id, pod_name) 严格匹配预登记 row,
     * 校验 token, 返 worker ID 给 worker.
     *
     * @throws BusinessException 404 (没预登记) / 401 (token 错)
     */
    @PostMapping("/register")
    public ApiResponse<WorkerRegisterResponse> register(@RequestBody @Valid WorkerRegisterRequest req) {
        log.info("worker 注册: podName={} env={} url={}", req.getPodName(), req.getEnv(), req.getWorkerUrl());
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

    /**
     * 创建 worker (预登记) — M9.5 新增.
     *
     * <p>用户在 shipyard UI 填 name + podName, shipyard 生成 token, 存哈希, 返明文 (一次性).
     *
     * <p>注意: 这个端点路径是 {@code /api/envs/{envId}/workers}, 在 EnvController 里
     * 实现 (跟 env 关联, M9.5 worker 严格属于 1 个 env).
     * 这里只放 /api/workers/{id}/* 的端点.
     */

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

    /**
     * 更新 worker (V1 阶段只能改 description).
     */
    @PutMapping("/{id}")
    public ApiResponse<WorkerResponse> update(@PathVariable Long id, @RequestBody @Valid WorkerUpdateRequest req) {
        Worker w = workerService.update(id, req, "system");  // V1 默认 system, V1.5 从 JWT 拿
        return ApiResponse.ok(WorkerResponse.from(w));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        workerService.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 重新生成 token (M9.5 新增).
     *
     * <p>旧 token 立即失效, 返新 token 明文 (一次性).
     */
    @PostMapping("/{id}/regenerate-token")
    public ApiResponse<WorkerTokenResponse> regenerateToken(@PathVariable Long id) {
        return ApiResponse.ok(workerService.regenerateToken(id, "system"));
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
