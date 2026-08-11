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
import com.shipyard.entity.Env;
import com.shipyard.mapper.EnvMapper;
import com.shipyard.worker.client.WorkerClient;
import com.shipyard.worker.dto.WorkerHeartbeatRequest;
import com.shipyard.worker.dto.WorkerRegisterRequest;
import com.shipyard.worker.dto.WorkerRegisterResponse;
import com.shipyard.worker.entity.Worker;
import com.shipyard.worker.mapper.WorkerMapper;
import com.shipyard.worker.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Worker Service 实现 — 跟 {@link com.shipyard.service.impl.EnvServiceImpl} 风格保持一致.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerServiceImpl implements WorkerService {

    /** 心跳间隔 (秒), 返给 worker. */
    private static final int HEARTBEAT_INTERVAL_SEC = 30;

    /** worker 允许的 status 值. */
    private static final Set<String> ALLOWED_STATUS = Set.of("online", "offline", "unhealthy");

    /** M9 commit-4: worker 自报 health 合法值. */
    private static final Set<String> ALLOWED_HEALTH = Set.of("HEALTHY", "UNHEALTHY");

    private final WorkerMapper workerMapper;
    private final EnvMapper envMapper;
    private final WorkerClient workerClient;

    @Override
    @Transactional
    public WorkerRegisterResponse register(WorkerRegisterRequest req) {
        // === 1. 查 env_id (按 env name) ===
        Long envId = envMapper.selectIdByNameRaw(req.getEnv());
        if (envId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                "环境不存在: " + req.getEnv() + ", 请先在 shipyard 创建环境");
        }

        // === 2. 查 worker 是否已注册 (按 name + env_id) ===
        LambdaQueryWrapper<Worker> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Worker::getWorkerUrl, req.getWorkerUrl())
               .eq(Worker::getEnvId, envId)
               .eq(Worker::getDeleted, 0)
               .last("LIMIT 1");
        Worker existing = workerMapper.selectOne(wrapper);
        if (existing != null) {
            // 已存在 → 复用 ID, 更新 token hash / url / version / status
            log.info("worker 已存在 (id={}), 走更新流程: name={}", existing.getId(), req.getWorkerName());
            existing.setWorkerTokenHash(hashToken(req.getWorkerToken()));
            existing.setStatus("online");
            existing.setLastHeartbeatAt(LocalDateTime.now());
            if (req.getVersion() != null) {
                existing.setVersion(req.getVersion());
            }
            workerMapper.updateById(existing);
            return new WorkerRegisterResponse() {{
                setWorkerId(existing.getId());
                setHeartbeatIntervalSec(HEARTBEAT_INTERVAL_SEC);
            }};
        }

        // === 3. 不存在 → 插入新行 ===
        Worker w = new Worker();
        w.setEnvId(envId);
        w.setWorkerUrl(req.getWorkerUrl());
        w.setWorkerTokenHash(hashToken(req.getWorkerToken()));
        w.setStatus("online");
        w.setLastHeartbeatAt(LocalDateTime.now());
        w.setVersion(req.getVersion());
        workerMapper.insert(w);

        log.info("worker 注册成功: id={} env={} name={} url={}",
                w.getId(), req.getEnv(), req.getWorkerName(), req.getWorkerUrl());

        return new WorkerRegisterResponse() {{
            setWorkerId(w.getId());
            setHeartbeatIntervalSec(HEARTBEAT_INTERVAL_SEC);
        }};
    }

    @Override
    public void heartbeat(Long workerId, WorkerHeartbeatRequest req) {
        // 验 status 合法
        String status = req.getStatus() != null ? req.getStatus() : "online";
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "status 必须是 " + ALLOWED_STATUS + " 之一, 实际: " + status);
        }
        // 验 workerId 一致 (URL path vs body)
        if (req.getWorkerId() != null && !req.getWorkerId().equals(workerId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "URL path workerId (" + workerId + ") 跟 body workerId (" + req.getWorkerId() + ") 不一致");
        }

        // M9 commit-4: 接 worker 自报 health 字段
        // 不传时默认 HEALTHY (老 worker 客户端兼容)
        String health = req.getHealth() != null ? req.getHealth() : "HEALTHY";
        if (!ALLOWED_HEALTH.contains(health)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "health 必须是 " + ALLOWED_HEALTH + " 之一, 实际: " + health);
        }
        String healthDetail = req.getHealthDetail();  // null OK

        int updated = workerMapper.updateHeartbeatWithHealth(
                workerId, LocalDateTime.now(), status, health, healthDetail);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                "worker 不存在: id=" + workerId + " (可能已被删除)");
        }
        log.debug("worker 心跳: id={} status={} health={} detail={}",
                workerId, status, health, healthDetail);
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
    public void delete(Long id) {
        Worker existing = get(id);
        workerMapper.deleteById(existing.getId());
        log.info("worker 删除: id={} envId={} url={}", id, existing.getEnvId(), existing.getWorkerUrl());
    }

    // ==================== 集群读类代理 ====================

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

    // ==================== 私有辅助 ====================

    /** 查 worker URL — 走 shipyard 库, 不调 worker 自身. */
    private String getWorkerUrl(Long workerId) {
        Worker w = get(workerId);
        return w.getWorkerUrl();
    }

    /** token SHA-256 哈希 (hex 编码, 64 字符). */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 永远可用, 真没这算法就是 JDK 坏了
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 不可用: " + e.getMessage());
        }
    }
}
