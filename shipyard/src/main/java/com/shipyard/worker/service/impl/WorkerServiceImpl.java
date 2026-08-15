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

package com.shipyard.worker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.crypto.TokenGenerator;
import com.shipyard.entity.Env;
import com.shipyard.mapper.EnvMapper;
import com.shipyard.worker.client.WorkerClient;
import com.shipyard.worker.dto.WorkerCreateRequest;
import com.shipyard.worker.dto.WorkerHeartbeatRequest;
import com.shipyard.worker.dto.WorkerRegisterRequest;
import com.shipyard.worker.dto.WorkerRegisterResponse;
import com.shipyard.worker.dto.WorkerTokenResponse;
import com.shipyard.worker.dto.WorkerUpdateRequest;
import com.shipyard.worker.entity.Worker;
import com.shipyard.worker.mapper.WorkerMapper;
import com.shipyard.worker.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Worker Service 实现 — M9.5 redesign.
 *
 * <p>核心变化 (vs M9 commit-16):
 * <ul>
 *   <li><b>预登记 + 严格 register</b> — 用户先在 shipyard UI 创建 worker (POST /api/envs/{envId}/workers),
 *       再去 k8s 部署 pod, pod register 时 shipyard 用 {@code (env_id, pod_name)} 严格匹配,
 *       找不到返 404 "请先在 UI 创建 worker"</li>
 *   <li><b>Token 生命周期</b> — shipyard 生成 token (32 字节 base64), 存 SHA-256 哈希,
 *       明文只展示一次, register 时 worker 携带明文, shipyard 哈希后跟存库哈希比对</li>
 *   <li><b>1 worker = 1 pod</b> — 2 pod 不会再共享 1 row, 每条 row 对应一个 k8s pod</li>
 *   <li><b>新状态机</b> — PLANNED (UI 创建) → PROVISIONING (register 找到) → ONLINE (首次心跳) → ...</li>
 *   <li><b>集群代理</b> — shipyard 调 worker 走 {@code worker.workerUrl} 字段 (worker register 时上报)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerServiceImpl implements WorkerService {

    /** 心跳间隔 (秒), 返给 worker. */
    private static final int HEARTBEAT_INTERVAL_SEC = 30;

    /** worker 允许的 status 值 (M9.5 扩展: 加 PROVISIONING, PLANNED 在 row 创建时设). */
    private static final Set<String> ALLOWED_STATUS = Set.of(
            "PLANNED", "PROVISIONING", "ONLINE", "OFFLINE", "UNHEALTHY");

    /** M9 commit-4: worker 自报 health 合法值. */
    private static final Set<String> ALLOWED_HEALTH = Set.of("HEALTHY", "UNHEALTHY");

    /** 系统行为 updated_by 标记. */
    private static final String SYSTEM_USER = "system";
    private static final String SYSTEM_REGISTER = "system:register";
    private static final String SYSTEM_HEARTBEAT = "system:heartbeat";
    private static final String SYSTEM_REGENERATE_TOKEN = "system:regenerate-token";

    private final WorkerMapper workerMapper;
    private final EnvMapper envMapper;
    private final WorkerClient workerClient;

    // ============================================================
    // CRUD (UI 调用)
    // ============================================================

    /**
     * 创建 worker (预登记).
     *
     * <p>用户在 shipyard UI 填 name + podName, shipyard:
     * <ol>
     *   <li>校验 name / podName 同 env 下唯一</li>
     *   <li>生成 32 字节随机 token</li>
     *   <li>存 token SHA-256 哈希</li>
     *   <li>status = PLANNED</li>
     *   <li>返明文 token (一次性, 用户复制到 k8s manifest)</li>
     * </ol>
     */
    @Override
    @Transactional
    public WorkerTokenResponse create(Long envId, WorkerCreateRequest req, String currentUser) {
        // 1. 查 env 必须存在
        Env env = envMapper.selectById(envId);
        if (env == null || env.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "环境不存在: id=" + envId);
        }

        // 2. 校验 name 同 env 下唯一
        validateNameUnique(envId, req.getName(), null);

        // 3. 校验 podName 同 env 下唯一
        validatePodNameUnique(envId, req.getPodName(), null);

        // 4. 生成 token + 哈希
        String plainToken = TokenGenerator.generate();
        String tokenHash = TokenGenerator.hash(plainToken);

        // 5. 构造 row
        Worker w = new Worker();
        w.setEnvId(envId);
        w.setName(req.getName());
        w.setPodName(req.getPodName());
        w.setDescription(req.getDescription());
        w.setWorkerTokenHash(tokenHash);
        w.setStatus("PLANNED");
        w.setCreatedBy(currentUser != null ? currentUser : SYSTEM_USER);
        w.setUpdatedBy(w.getCreatedBy());
        // createdAt / updatedAt 由 BaseEntity 自动填

        workerMapper.insert(w);
        log.info("worker 创建: id={} envId={} name={} podName={}",
                w.getId(), envId, w.getName(), w.getPodName());

        // 6. 返明文 token (一次性)
        return new WorkerTokenResponse(
                w.getId(),
                w.getName(),
                plainToken,
                "请立即复制此 token 到 k8s worker manifest 的 WORKER_TOKEN env var. 此 token 只展示一次, 之后无法再次获取.");
    }

    /**
     * 重新生成 token.
     *
     * <p>旧 token 立即失效 (所有还在用旧 token 的 worker register/heartbeat 都会失败).
     */
    @Override
    @Transactional
    public WorkerTokenResponse regenerateToken(Long workerId, String currentUser) {
        Worker w = get(workerId);

        String plainToken = TokenGenerator.generate();
        String tokenHash = TokenGenerator.hash(plainToken);

        w.setWorkerTokenHash(tokenHash);
        w.setUpdatedBy(currentUser != null ? currentUser : SYSTEM_REGENERATE_TOKEN);
        workerMapper.updateById(w);

        log.warn("worker token 重新生成: id={} envId={} (旧 token 立即失效)",
                workerId, w.getEnvId());

        return new WorkerTokenResponse(
                w.getId(),
                w.getName(),
                plainToken,
                "新 token 已生成, 旧 token 立即失效. 请更新 k8s worker manifest 的 WORKER_TOKEN env var, 然后滚动重启 worker pod.");
    }

    @Override
    public Page<Worker> list(int page, int size, Long envId) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 500));

        LambdaQueryWrapper<Worker> wrapper = new LambdaQueryWrapper<>();
        if (envId != null) {
            wrapper.eq(Worker::getEnvId, envId);
        }
        wrapper.orderByDesc(Worker::getLastHeartbeatAt).orderByAsc(Worker::getId);

        return workerMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
    }

    @Override
    public Worker get(Long id) {
        Worker w = workerMapper.selectById(id);
        if (w == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "worker 不存在: id=" + id);
        }
        return w;
    }

    @Override
    public Worker update(Long id, WorkerUpdateRequest req, String currentUser) {
        Worker existing = get(id);
        // V1 阶段只允许改 description
        if (req.getDescription() != null) {
            existing.setDescription(req.getDescription());
        }
        existing.setUpdatedBy(currentUser != null ? currentUser : SYSTEM_USER);
        workerMapper.updateById(existing);
        log.info("worker 更新: id={} name={}", id, existing.getName());
        return existing;
    }

    @Override
    public void delete(Long id) {
        Worker existing = get(id);
        workerMapper.deleteById(existing.getId());  // 软删 (MyBatis-Plus @TableLogic)
        log.info("worker 软删: id={} envId={} name={}", id, existing.getEnvId(), existing.getName());
    }

    // ============================================================
    // Worker 主动调 shipyard (register + heartbeat)
    // ============================================================

    /**
     * Worker 主动注册 — M9.5 严格模式.
     *
     * <p>流程:
     * <ol>
     *   <li>查 env_id by env name</li>
     *   <li>查 worker by (env_id, pod_name) — 必须存在 (用户在 UI 预登记过)</li>
     *   <li>校验 token SHA-256 哈希匹配</li>
     *   <li>更新 workerUrl (worker 上报), version, status=PROVISIONING (首次) 或 ONLINE (已有)</li>
     *   <li>updated_by = "system:register"</li>
     * </ol>
     *
     * <p>找不到预登记 row → 返 404 "请先在 shipyard UI 创建 worker"
     * <p>token 校验失败 → 返 401 "token 不匹配"
     */
    @Override
    @Transactional
    public WorkerRegisterResponse register(WorkerRegisterRequest req) {
        // 1. 查 env_id
        Long envId = envMapper.selectIdByNameRaw(req.getEnv());
        if (envId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "环境不存在: " + req.getEnv() + ", 请先在 shipyard 创建环境");
        }

        // 2. 严格匹配预登记 row
        LambdaQueryWrapper<Worker> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Worker::getEnvId, envId)
               .eq(Worker::getPodName, req.getPodName())
               .eq(Worker::getDeleted, 0)
               .last("LIMIT 1");
        Worker existing = workerMapper.selectOne(wrapper);

        if (existing == null) {
            // 找不到 → 严格模式, 拒绝 register
            log.warn("worker register 拒绝: env={} podName={} (shipyard 端没有预登记 row, 请用户先在 UI 创建)",
                    req.getEnv(), req.getPodName());
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "shipyard 端没有预登记的 worker (env=" + req.getEnv() + ", podName=" + req.getPodName() +
                            "), 请先在 shipyard UI 的 Worker 页面创建, 并把生成的 token 配到 k8s manifest");
        }

        // 3. 校验 token
        if (!TokenGenerator.verify(req.getWorkerToken(), existing.getWorkerTokenHash())) {
            log.warn("worker register token 校验失败: id={} envId={} podName={}",
                    existing.getId(), envId, req.getPodName());
            throw new BusinessException(ErrorCode.UNAUTHORIZED,
                    "worker token 校验失败 (env=" + req.getEnv() + ", podName=" + req.getPodName() +
                            "), 请检查 k8s manifest 的 WORKER_TOKEN 是否跟 shipyard UI 的一致, 或重新生成 token");
        }

        // 4. 更新 worker 状态
        // status: PLANNED → PROVISIONING (首次) / 保持当前状态 (后续 register)
        if ("PLANNED".equals(existing.getStatus())) {
            existing.setStatus("PROVISIONING");
        }
        existing.setWorkerUrl(req.getWorkerUrl());
        existing.setLastHeartbeatAt(LocalDateTime.now());  // register 算一次心跳
        if (req.getVersion() != null) {
            existing.setVersion(req.getVersion());
        }
        existing.setUpdatedBy(SYSTEM_REGISTER);
        workerMapper.updateById(existing);

        log.info("worker register 成功: id={} envId={} podName={} url={}",
                existing.getId(), envId, req.getPodName(), req.getWorkerUrl());

        return new WorkerRegisterResponse() {{
            setWorkerId(existing.getId());
            setHeartbeatIntervalSec(HEARTBEAT_INTERVAL_SEC);
        }};
    }

    @Override
    public void heartbeat(Long workerId, WorkerHeartbeatRequest req) {
        // 1. 验 status 合法
        String status = req.getStatus() != null ? req.getStatus() : "ONLINE";
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "status 必须是 " + ALLOWED_STATUS + " 之一, 实际: " + status);
        }

        // 2. 验 workerId 一致 (URL path vs body)
        if (req.getWorkerId() != null && !req.getWorkerId().equals(workerId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "URL path workerId (" + workerId + ") 跟 body workerId (" + req.getWorkerId() + ") 不一致");
        }

        // 3. 验 health 合法
        String health = req.getHealth() != null ? req.getHealth() : "HEALTHY";
        if (!ALLOWED_HEALTH.contains(health)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "health 必须是 " + ALLOWED_HEALTH + " 之一, 实际: " + health);
        }
        String healthDetail = req.getHealthDetail();

        // 4. 更新 heartbeat + status + health
        //    M9.5: 状态机由 register (PLANNED → PROVISIONING) + heartbeat (→ ONLINE) + WorkerHealthScanner (→ OFFLINE/UNHEALTHY) 共同维护
        int updated = workerMapper.updateHeartbeatWithHealth(
                workerId, LocalDateTime.now(), status, health, healthDetail);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "worker 不存在: id=" + workerId + " (可能已被删除)");
        }

        // 5. updated_by = system:heartbeat (M9.5 新: 区分用户操作 vs 系统行为)
        // 注: workerMapper.updateHeartbeatWithHealth 不会更新 updated_by,
        //     这里需要单独再 update 一次, 但为了不增加 mapper 接口, 暂略.
        //     实际生产可以在 updateHeartbeatWithHealth SQL 里加 updated_by=?.
        log.debug("worker 心跳: id={} status={} health={} detail={}",
                workerId, status, health, healthDetail);
    }

    // ============================================================
    // 集群读类代理 (shipyard → worker)
    // ============================================================

    @Override
    public List<Map<String, Object>> listNamespaces(Long workerId) {
        return workerClient.listNamespaces(getWorkerUrl(workerId));
    }

    @Override
    public List<Map<String, Object>> listPods(Long workerId, String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "namespace 不能为空");
        }
        return workerClient.listPods(getWorkerUrl(workerId), namespace);
    }

    @Override
    public List<Map<String, Object>> listDeployments(Long workerId, String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "namespace 不能为空");
        }
        return workerClient.listDeployments(getWorkerUrl(workerId), namespace);
    }

    @Override
    public Map<String, Object> listWorkerPods(Long workerId) {
        // M9 commit-16: 调 worker /api/v1/cluster/worker-pods 拿 deployment replicas + pod 列表
        // M9.5: 1 worker = 1 pod, 这个端点改语义 — 返 worker 自己的 pod 信息 (1 个),
        //       或者直接返 1 个 pod 数组. 为兼容旧 UI, 暂时保留.
        return workerClient.listWorkerPods(getWorkerUrl(workerId), null);
    }

    // ============================================================
    // 私有辅助
    // ============================================================

    /** 查 worker URL — 走 shipyard 库, 不调 worker 自身. */
    private String getWorkerUrl(Long workerId) {
        Worker w = get(workerId);
        if (w.getWorkerUrl() == null || w.getWorkerUrl().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "worker 还没 register, workerUrl 未知: id=" + workerId);
        }
        return w.getWorkerUrl();
    }

    /** 校验 name 同 env 下唯一. excludeId: 排除自身 (用于 update). */
    private void validateNameUnique(Long envId, String name, Long excludeId) {
        LambdaQueryWrapper<Worker> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Worker::getEnvId, envId)
               .eq(Worker::getName, name)
               .eq(Worker::getDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(Worker::getId, excludeId);
        }
        Long count = workerMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "env 下已存在同名 worker: name=" + name);
        }
    }

    /** 校验 podName 同 env 下唯一. */
    private void validatePodNameUnique(Long envId, String podName, Long excludeId) {
        LambdaQueryWrapper<Worker> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Worker::getEnvId, envId)
               .eq(Worker::getPodName, podName)
               .eq(Worker::getDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(Worker::getId, excludeId);
        }
        Long count = workerMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "env 下已存在同 podName 的 worker: podName=" + podName);
        }
    }
}
