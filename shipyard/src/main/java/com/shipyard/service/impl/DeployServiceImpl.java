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
import com.shipyard.service.EnvService;
import com.shipyard.service.PipelineTemplateService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
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
    private final EnvService envService;
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "环境不存在: id=" + request.getEnvId());
        }

        // 3. 解析 imageTag
        String imageTag = resolveImageTag(request);
        if (imageTag == null || imageTag.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "imageTag 不能为空, 必须传 buildRecordId 或 imageTag 其中之一");
        }

        // 4. (V1 in-process 模拟) 跳过 worker 选, 之前是 WorkerSelector.selectDeployWorker
        //    V1 阶段无真 worker, 直接进 deploy_record 创建. 真 worker 接入见 V1.5 重新设计.

        // 5. 查 pipeline_template (active)
        PipelineTemplate activePipeline = pipelineTemplateService.getActive(projectId);
        if (activePipeline == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目没有 active 的 pipeline_template, 请先创建并 activate 一个");
        }
        if (request.getReplicas() != null) {
            activePipeline.setReplicas(request.getReplicas());
        }

        // 6. 渲染 yaml + 算 sha256
        String resourceName = computeResourceName(project.getName(), env.getName());
        String deployYaml = templateRenderer.render(env, activePipeline, resourceName, imageTag, request.getEnvVars());
        String sha256 = sha256Hex(deployYaml);

        // 7. 写 deploy_record (PENDING)
        DeployRecord record = new DeployRecord();
        record.setProjectId(projectId);
        record.setEnvId(request.getEnvId());
        record.setBuildRecordId(request.getBuildRecordId());
        record.setImageTag(imageTag);
        record.setNamespace(
                env.getName() == null
                        ? null
                        : templateRenderer.renderNamespace(activePipeline.getNamespacePattern(), env.getName()));
        record.setDeployYamlSha256(sha256);
        record.setStatus(DeployStatus.PENDING.name());
        record.setTriggeredBy(request.getTriggeredBy() != null ? request.getTriggeredBy() : "unknown");
        record.setTriggerType("MANUAL");
        deployRecordMapper.insert(record);

        // 8. 写 deploy_snapshot
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

        log.info(
                "[DeployService] createDeploy id={} projectId={} envId={} imageTag={} sha256={}",
                record.getId(),
                projectId,
                request.getEnvId(),
                imageTag,
                sha256);

        // 10. (V1 in-process 模拟) 不调真 worker, 5s 后异步标 SUCCESS
        //    V1.5+ 真 worker: 改回 triggerWorkerDeploy (调 worker /api/v1/tasks/deploy + 回调)
        simulateWorkerDeploy(record.getId(), deployYaml);

        return DeployResponse.from(record);
    }

    /**
     * V1 阶段 in-process 模拟: 标 RUNNING → 5s 后异步标 SUCCESS.
     *
     * <p>V1.5+ 重新设计: 改回 triggerWorkerDeploy — 调真 worker /api/v1/tasks/deploy,
     * worker 返 200 + code=0 标 SUCCESS, 返业务错 / 不可达标 FAILED.
     */
    private void simulateWorkerDeploy(Long deployRecordId, String deployYaml) {
        // PENDING → RUNNING (本地状态机, V1 阶段 in-process 模拟)
        markRunning(deployRecordId);

        // 模拟 worker apply 5s 后完成
        // V1 阶段: 不用 @Async 也不用 ScheduledExecutorService, 拉个 Thread 简单 sleep + markFinished
        // V1.5+ 真 worker: 这里改成调 workerClient.deploy() + 回调 /api/internal/deploy/callback
        Thread t = new Thread(
                () -> {
                    try {
                        Thread.sleep(5000);
                        markFinished(
                                deployRecordId,
                                "SUCCESS",
                                "V1 in-process simulated apply (V1.5+ will call real worker)");
                        log.info("[DeployService] V1 模拟 deploy SUCCESS: id={}", deployRecordId);
                    } catch (InterruptedException e) {
                        markFinished(deployRecordId, "FAILED", "V1 模拟被中断: " + e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                },
                "v1-simulate-deploy-" + deployRecordId);
        t.setDaemon(true);
        t.start();
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "snapshot 跟 deploy 不在同一 project/env, 拒绝回滚");
        }

        // 3. (V1 in-process 模拟) 跳过 worker 选, 之前是 selectDeployWorker

        // 4. 写新 deploy_record (复用原 imageTag, 新 sha256 是 snapshot 的 sha256)
        DeployRecord record = new DeployRecord();
        record.setProjectId(originalDeploy.getProjectId());
        record.setEnvId(originalDeploy.getEnvId());
        record.setBuildRecordId(null); // 回滚不绑 build
        record.setImageTag(originalDeploy.getImageTag());
        record.setNamespace(originalDeploy.getNamespace());
        record.setDeployYamlSha256(snapshot.getDeployYamlSha256());
        record.setStatus(DeployStatus.PENDING.name());
        record.setTriggeredBy(triggeredBy != null ? triggeredBy : "rollback:" + deployId);
        record.setTriggerType("ROLLBACK");
        deployRecordMapper.insert(record);

        // 5. 写新 snapshot
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

        log.info("[DeployService] rollback id={} from snapshotId={}", record.getId(), snapshotId);

        // 7. (V1 in-process 模拟) 调 simulateWorkerDeploy, V1.5+ 改回 triggerWorkerDeploy
        simulateWorkerDeploy(record.getId(), snapshot.getDeployYaml());

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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "部署已经在终态, 不能取消: " + current);
        }
        deployRecordMapper.markFinished(id, DeployStatus.CANCELED.name(), "用户取消", LocalDateTime.now());
        DeployRecord updated = deployRecordMapper.selectById(id);
        log.info("[DeployService] cancel id={} was {}", id, current);
        return DeployResponse.from(updated);
    }

    @Override
    public String getLiveManifest(Long deployId) {
        // 1. 查 deploy
        DeployRecord r = deployRecordMapper.selectById(deployId);
        if (r == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部署不存在: id=" + deployId);
        }
        // 2. (V1 in-process 模拟) 返 snapshot 里的 yaml, 不调 K8s
        //    V1.5+ 真 worker: 改成 selectDeployWorker(r.getEnvId()) + workerClient.getManifest(...)
        DeploySnapshot snapshot = deploySnapshotMapper.selectById(r.getCurrentSnapshotId());
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "deploy " + deployId + " 关联的 snapshot 不存在");
        }
        return snapshot.getDeployYaml();
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
                throw new BusinessException(ErrorCode.NOT_FOUND, "build_record 不存在: id=" + request.getBuildRecordId());
            }
            if (br.getImageTag() == null || br.getImageTag().isBlank()) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "build_record " + request.getBuildRecordId() + " 还没镜像 (status=" + br.getStatus() + ")");
            }
            return br.getImageTag();
        }
        return request.getImageTag();
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
