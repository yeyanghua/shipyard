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

package com.shipyard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shipyard.common.enums.DeployStatus;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.dto.DeployCreateRequest;
import com.shipyard.dto.DeployResponse;
import com.shipyard.dto.DeploySnapshotResponse;
import com.shipyard.entity.BuildRecord;
import com.shipyard.entity.DeployRecord;
import com.shipyard.entity.DeploySnapshot;
import com.shipyard.entity.Env;
import com.shipyard.entity.PipelineTemplate;
import com.shipyard.entity.Project;
import com.shipyard.mapper.BuildRecordMapper;
import com.shipyard.mapper.DeployRecordMapper;
import com.shipyard.mapper.DeploySnapshotMapper;
import com.shipyard.mapper.EnvMapper;
import com.shipyard.mapper.PipelineTemplateMapper;
import com.shipyard.mapper.ProjectMapper;
import com.shipyard.service.DeployService;
import com.shipyard.service.DeployTemplateRenderer;
import com.shipyard.service.PipelineTemplateService;
import com.shipyard.worker.entity.Worker;
import com.shipyard.worker.mapper.WorkerMapper;
import com.shipyard.worker.selector.WorkerSelector;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DeployService 主实现.
 *
 * <p>commit-3 阶段只实现 createDeploy / getDeploy / listDeploys / listSnapshots / listSnapshotsByProjectEnv
 * + 状态机 markRunning / markFinished. cancel / rollback / liveManifest 走 placeholder, commit-5 WorkerClient
 * 调通后补, commit-6 Controller 一并接上.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeployServiceImpl implements DeployService {

    private final DeployRecordMapper deployRecordMapper;
    private final DeploySnapshotMapper deploySnapshotMapper;
    private final ProjectMapper projectMapper;
    private final EnvMapper envMapper;
    private final BuildRecordMapper buildRecordMapper;
    private final PipelineTemplateMapper pipelineTemplateMapper;
    private final PipelineTemplateService pipelineTemplateService;
    private final WorkerMapper workerMapper;
    private final WorkerSelector activeWorkerSelector;
    private final DeployTemplateRenderer templateRenderer;

    // ============================================================
    // 业务 API
    // ============================================================

    @Override
    @Transactional
    public DeployResponse createDeploy(Long projectId, DeployCreateRequest request) {
        // 1. 校验 project
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在: id=" + projectId);
        }
        // 2. 校验 env
        Env env = envMapper.selectById(request.getEnvId());
        if (env == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "环境不存在: id=" + request.getEnvId());
        }

        // 3. 解析 imageTag
        String imageTag = resolveImageTag(request);
        if (imageTag == null || imageTag.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "imageTag 不能为空, 必须传 buildRecordId 或 imageTag 其中之一");
        }

        // 4. 选 worker (WorkerSelector 抽象, M9 fix-commit)
        Worker worker = selectDeployWorker(request.getEnvId());
        if (worker == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "环境 [" + env.getName() + "] 没有可用的 worker (online 列表为空), "
                    + "请先在集群里启动 worker 并等 30s 心跳");
        }

        // 5. 查 pipeline_template (active)
        PipelineTemplate activePipeline = pipelineTemplateService.getActive(projectId);
        if (activePipeline == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "项目没有 active 的 pipeline_template, 请先创建并 activate 一个");
        }
        if (request.getReplicas() != null) {
            activePipeline.setReplicas(request.getReplicas());
        }

        // 6. 渲染 yaml + 算 sha256
        String resourceName = computeResourceName(project.getName(), env.getName());
        String deployYaml = templateRenderer.render(
                env, activePipeline, resourceName, imageTag, request.getEnvVars());
        String sha256 = sha256Hex(deployYaml);

        // 7. 写 deploy_record (PENDING)
        DeployRecord record = new DeployRecord();
        record.setProjectId(projectId);
        record.setEnvId(request.getEnvId());
        record.setBuildRecordId(request.getBuildRecordId());
        record.setImageTag(imageTag);
        record.setNamespace(env.getName() == null ? null
                : templateRenderer.renderNamespace(activePipeline.getNamespacePattern(), env.getName()));
        record.setDeployYamlSha256(sha256);
        record.setStatus(DeployStatus.PENDING.name());
        record.setTriggeredBy(request.getTriggeredBy() != null ? request.getTriggeredBy() : "unknown");
        record.setTriggerType("MANUAL");
        deployRecordMapper.insert(record);

        // 8. 写 deploy_snapshot (deploy 镜像 + yaml + sha256)
        DeploySnapshot snapshot = new DeploySnapshot();
        snapshot.setDeployRecordId(record.getId());
        snapshot.setEnvId(request.getEnvId());
        snapshot.setProjectId(projectId);
        snapshot.setDeployYaml(deployYaml);
        snapshot.setDeployYamlSha256(sha256);
        snapshot.setCreatedBy(request.getTriggeredBy() != null ? request.getTriggeredBy() : "unknown");
        deploySnapshotMapper.insert(snapshot);

        // 9. 回填 current_snapshot_id
        deployRecordMapper.updateCurrentSnapshot(record.getId(), snapshot.getId());

        log.info("[DeployService] createDeploy id={} projectId={} envId={} workerId={} imageTag={} sha256={}",
                record.getId(), projectId, request.getEnvId(), worker.getId(), imageTag, sha256);

        // 10. 调 worker (commit-5 补 WorkerClient.deploy, 当前是 placeholder)
        //   异步 — 不阻塞 HTTP 响应; worker 返 200 后调 DeployServiceImpl.markRunning,
        //   worker 返终态后调 markFinished (由 WorkerHeartbeatScanner 链路或 worker 自身回调触发)
        // TODO commit-5: workerClient.deployAsync(worker.getWorkerUrl(), new DeployRequest(...))

        return DeployResponse.from(record);
    }

    @Override
    public DeployResponse getDeploy(Long id) {
        DeployRecord r = deployRecordMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部署不存在: id=" + id);
        }
        return DeployResponse.from(r);
    }

    @Override
    public List<DeployResponse> listDeploys(Long projectId, Long envId, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;

        LambdaQueryWrapper<DeployRecord> q = new LambdaQueryWrapper<>();
        if (projectId != null) q.eq(DeployRecord::getProjectId, projectId);
        if (envId != null) q.eq(DeployRecord::getEnvId, envId);
        q.orderByDesc(DeployRecord::getId);

        // 简单 list 实现 (不引 Page<> 因为 shipyard 部署场景单 project 通常 < 50 个 deploy)
        List<DeployRecord> all = deployRecordMapper.selectList(q);
        int from = (page - 1) * size;
        int to = Math.min(from + size, all.size());
        if (from >= all.size()) return List.of();
        return all.subList(from, to).stream().map(DeployResponse::from).toList();
    }

    @Override
    public List<DeploySnapshotResponse> listSnapshots(Long deployId) {
        // 校验 deploy 存在
        DeployRecord deploy = deployRecordMapper.selectById(deployId);
        if (deploy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部署不存在: id=" + deployId);
        }
        return deploySnapshotMapper.selectByDeployRecordId(deployId).stream()
                .map(DeploySnapshotResponse::from)
                .toList();
    }

    @Override
    public List<DeploySnapshotResponse> listSnapshotsByProjectEnv(Long projectId, Long envId) {
        return deploySnapshotMapper.selectByProjectAndEnv(projectId, envId).stream()
                .map(DeploySnapshotResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public DeployResponse rollback(Long deployId, Long snapshotId, String triggeredBy) {
        // 1. 校验原 deploy
        DeployRecord originalDeploy = deployRecordMapper.selectById(deployId);
        if (originalDeploy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部署不存在: id=" + deployId);
        }

        // 2. 校验 snapshot 存在 + 属于该项目 / env
        DeploySnapshot snapshot = deploySnapshotMapper.selectById(snapshotId);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "snapshot 不存在: id=" + snapshotId);
        }
        if (!snapshot.getProjectId().equals(originalDeploy.getProjectId())
                || !snapshot.getEnvId().equals(originalDeploy.getEnvId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "snapshot 跟 deploy 不在同一 project/env, 拒绝回滚");
        }

        // 3. 选 worker (跟 createDeploy 一样走 WorkerSelector)
        Worker worker = selectDeployWorker(originalDeploy.getEnvId());
        if (worker == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "环境 [" + originalDeploy.getEnvId() + "] 没有可用的 worker");
        }

        // 4. 写新 deploy_record (复用原 imageTag, 新 sha256 是 snapshot 的 sha256)
        DeployRecord record = new DeployRecord();
        record.setProjectId(originalDeploy.getProjectId());
        record.setEnvId(originalDeploy.getEnvId());
        record.setBuildRecordId(null);  // 回滚不绑 build
        record.setImageTag(originalDeploy.getImageTag());
        record.setNamespace(originalDeploy.getNamespace());
        record.setDeployYamlSha256(snapshot.getDeployYamlSha256());
        record.setStatus(DeployStatus.PENDING.name());
        record.setTriggeredBy(triggeredBy != null ? triggeredBy : "rollback:" + deployId);
        record.setTriggerType("ROLLBACK");
        deployRecordMapper.insert(record);

        // 5. 写新 snapshot (deployYAML = 原 snapshot 的 deployYAML, sha256 同)
        DeploySnapshot newSnap = new DeploySnapshot();
        newSnap.setDeployRecordId(record.getId());
        newSnap.setEnvId(originalDeploy.getEnvId());
        newSnap.setProjectId(originalDeploy.getProjectId());
        newSnap.setDeployYaml(snapshot.getDeployYaml());
        newSnap.setDeployYamlSha256(snapshot.getDeployYamlSha256());
        newSnap.setCreatedBy(triggeredBy != null ? triggeredBy : "rollback:" + deployId);
        deploySnapshotMapper.insert(newSnap);

        // 6. 回填 current_snapshot_id
        deployRecordMapper.updateCurrentSnapshot(record.getId(), newSnap.getId());

        log.info("[DeployService] rollback id={} from snapshotId={} workerId={}",
                record.getId(), snapshotId, worker.getId());

        // TODO commit-5: workerClient.deployAsync(worker.getWorkerUrl(), new DeployRequest(snapshot.getDeployYaml(), ...))

        return DeployResponse.from(record);
    }

    @Override
    @Transactional
    public DeployResponse cancelDeploy(Long id) {
        DeployRecord r = deployRecordMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部署不存在: id=" + id);
        }
        DeployStatus current = DeployStatus.valueOf(r.getStatus());
        if (current.isTerminal()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "部署已经在终态, 不能取消: " + current);
        }
        deployRecordMapper.markFinished(id, DeployStatus.CANCELED.name(),
                "用户取消", LocalDateTime.now());
        DeployRecord updated = deployRecordMapper.selectById(id);
        log.info("[DeployService] cancel id={} was {}", id, current);
        return DeployResponse.from(updated);
    }

    @Override
    public String getLiveManifest(Long deployId) {
        // TODO commit-5: 查 worker_url + namespace + name, 调 worker getManifest
        DeployRecord r = deployRecordMapper.selectById(deployId);
        if (r == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部署不存在: id=" + deployId);
        }
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED,
                "getLiveManifest 留 commit-5 接 WorkerClient 后实现");
    }

    // ============================================================
    // 状态机回调
    // ============================================================

    @Override
    public void markRunning(Long id) {
        int affected = deployRecordMapper.markRunning(id, LocalDateTime.now());
        if (affected == 0) {
            log.warn("[DeployService] markRunning no-op (id={} not in PENDING?)", id);
        }
    }

    @Override
    public void markFinished(Long id, String status, String errorMessage) {
        DeployStatus target;
        try {
            target = DeployStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            log.error("[DeployService] markFinished invalid status={}", status, e);
            return;
        }
        int affected = deployRecordMapper.markFinished(id, target.name(), errorMessage, LocalDateTime.now());
        if (affected == 0) {
            log.warn("[DeployService] markFinished no-op (id={} already terminal?)", id);
        }
    }

    // ============================================================
    // 私有辅助
    // ============================================================

    /**
     * 解析 imageTag — 优先 buildRecordId, 否则用 req.imageTag.
     */
    private String resolveImageTag(DeployCreateRequest request) {
        if (request.getBuildRecordId() != null) {
            BuildRecord br = buildRecordMapper.selectById(request.getBuildRecordId());
            if (br == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND,
                        "build_record 不存在: id=" + request.getBuildRecordId());
            }
            if (br.getImageTag() == null || br.getImageTag().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "build_record " + request.getBuildRecordId()
                        + " 还没镜像 (status=" + br.getStatus() + ")");
            }
            return br.getImageTag();
        }
        return request.getImageTag();
    }

    /**
     * 选 deploy worker — M9 fix-commit 后, 走 WorkerSelector 抽象, 不再硬编码 role.
     *
     * <p>查 status=online + last_heartbeat_at DESC, WorkerSelector 从中按策略选 1 个.
     * 没找到 → 返 null (业务层抛 NOT_FOUND 错).
     */
    private Worker selectDeployWorker(Long envId) {
        List<Worker> candidates = workerMapper.selectByEnvAndStatus(envId, "online");
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return activeWorkerSelector.select(candidates);
    }

    /**
     * K8s 资源名 = {@code <project>-<env>} (K8s 资源名规则: 小写字母数字-, 最多 63).
     *
     * <p>V1 简版: 不做转换, 直接连字符拼接. project / env 名已经约束小写.
     */
    String computeResourceName(String projectName, String envName) {
        return (projectName + "-" + envName).toLowerCase();
    }

    /**
     * 算字符串 SHA-256 (hex 编码, 64 字符).
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 不可用: " + e.getMessage());
        }
    }
}
