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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shipyard.common.enums.BuildStatus;
import com.shipyard.common.enums.TriggerType;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.dto.BuildCreateRequest;
import com.shipyard.dto.BuildLogResponse;
import com.shipyard.dto.BuildResponse;
import com.shipyard.drone.DroneClient;
import com.shipyard.drone.DroneClient.DroneBuildRequest;
import com.shipyard.entity.BuildLog;
import com.shipyard.entity.BuildRecord;
import com.shipyard.entity.Project;
import com.shipyard.mapper.BuildLogMapper;
import com.shipyard.mapper.BuildRecordMapper;
import com.shipyard.mapper.ProjectMapper;
import com.shipyard.service.BuildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BuildService 主实现 — 业务编排.
 *
 * <p>核心流程 ({@link #createBuild}):
 * <ol>
 *   <li>校验 projectId 存在 + commit 字段合法</li>
 *   <li>分配 droneBuildId = UUID</li>
 *   <li>落 build_record (status=PENDING)</li>
 *   <li>调 {@link DroneClient#triggerBuild} (mock 立即返回, real 调 drone API)</li>
 *   <li>返回 BuildResponse</li>
 * </ol>
 *
 * <p>V1 mock 模式: MockDroneClient 内部 {@code @Async} 任务会异步调
 * {@link #markBuildRunning} → {@link #saveStepLog} → {@link #markBuildFinished},
 * 业务不用等.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildServiceImpl implements BuildService {

    private final BuildRecordMapper buildRecordMapper;
    private final BuildLogMapper buildLogMapper;
    private final ProjectMapper projectMapper;
    private final DroneClient droneClient;

    // ============== 业务 API ==============

    @Override
    public BuildResponse createBuild(BuildCreateRequest request) {
        // 1. 校验 project 存在
        Project project = projectMapper.selectById(request.getProjectId());
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在: id=" + request.getProjectId());
        }

        // 2. 校验 commit 字段
        if (request.getCommitSha() == null || request.getCommitSha().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "commitSha is required");
        }
        if (request.getCommitSha().length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "commitSha too long (max 64)");
        }

        // 3. 分配 droneBuildId
        String droneBuildId = "drone-" + UUID.randomUUID();

        // 4. 落 build_record (PENDING) — 单 SQL 自动 commit
        BuildRecord record = new BuildRecord();
        record.setProjectId(request.getProjectId());
        record.setCommitSha(request.getCommitSha());
        record.setCommitMessage(request.getCommitMessage());
        record.setTriggeredBy(request.getTriggeredBy() != null ? request.getTriggeredBy() : "unknown");
        record.setTriggerType(TriggerType.MANUAL.name());
        record.setDroneBuildId(droneBuildId);
        record.setStatus(BuildStatus.PENDING.name());
        record.setLogPersisted(0);
        buildRecordMapper.insert(record);

        log.info("[BuildService] createBuild id={} projectId={} droneBuildId={}",
            record.getId(), record.getProjectId(), droneBuildId);

        // 5. 调 drone (mock 立即返回, real 同步调 drone API)
        DroneBuildRequest droneRequest = new DroneBuildRequest(
            droneBuildId,
            request.getProjectId(),
            project.getRepoUrl(),
            request.getCommitSha(),
            request.getCommitMessage(),
            Map.of()  // V1 mock 不接 env vars, V5 接 EnvVariableService.resolveAll
        );
        try {
            droneClient.triggerBuild(droneRequest);
        } catch (Exception e) {
            log.error("[BuildService] droneClient.triggerBuild failed, mark FAILED", e);
            // drone 调度失败也要标终态, 不然 record 永远卡 PENDING
            buildRecordMapper.markFinished(record.getId(), BuildStatus.FAILED.name(),
                null, null, LocalDateTime.now());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "drone trigger failed: " + e.getMessage());
        }

        return toResponse(record);
    }

    @Override
    public BuildResponse getBuild(Long id) {
        BuildRecord record = buildRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "构建不存在: id=" + id);
        }
        return toResponse(record);
    }

    @Override
    public List<BuildResponse> listBuilds(Long projectId, String status, int pageNum, int pageSize) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100;

        Page<BuildRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<BuildRecord> q = new LambdaQueryWrapper<>();
        q.eq(BuildRecord::getProjectId, projectId);
        if (status != null && !status.isBlank()) {
            q.eq(BuildRecord::getStatus, status);
        }
        q.orderByDesc(BuildRecord::getId);
        IPage<BuildRecord> result = buildRecordMapper.selectPage(page, q);
        return result.getRecords().stream().map(this::toResponse).toList();
    }

    @Override
    public BuildResponse cancelBuild(Long id) {
        BuildRecord record = buildRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "构建不存在: id=" + id);
        }
        BuildStatus current = BuildStatus.valueOf(record.getStatus());
        if (current.isTerminal()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "build already in terminal status: " + current);
        }

        // 通知 drone (mock: 仅设标志; real: 调 drone cancel API)
        droneClient.cancelBuild(record.getDroneBuildId());

        // 标 CANCELED — 用 markFinished 走通用终态更新
        buildRecordMapper.markFinished(id, BuildStatus.CANCELED.name(),
            null, null, LocalDateTime.now());

        BuildRecord updated = buildRecordMapper.selectById(id);
        return toResponse(updated);
    }

    @Override
    public String getStepLog(Long buildRecordId, String stepName) {
        BuildLog log = buildLogMapper.selectByBuildRecordIdAndStepName(buildRecordId, stepName);
        if (log == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                "step log not found: buildRecordId=" + buildRecordId + " stepName=" + stepName);
        }
        return log.getLogContent();
    }

    @Override
    public List<BuildLogResponse> listStepLogs(Long buildRecordId) {
        List<BuildLog> logs = buildLogMapper.selectByBuildRecordIdOrderByStepOrder(buildRecordId);
        return logs.stream()
            .map(l -> BuildLogResponse.builder()
                .id(l.getId())
                .buildRecordId(l.getBuildRecordId())
                .stepName(l.getStepName())
                .stepOrder(l.getStepOrder())
                .logSizeBytes(l.getLogSizeBytes())
                .startedAt(l.getStartedAt())
                .finishedAt(l.getFinishedAt())
                .createdAt(l.getCreatedAt())
                .build())
            .toList();
    }

    // ============== drone 回调 / mock 内部用 ==============

    @Override
    public void markBuildRunning(Long id, LocalDateTime startedAt) {
        int affected = buildRecordMapper.markRunning(id, startedAt);
        if (affected == 0) {
            log.warn("[BuildService] markBuildRunning no-op (id={} not in PENDING?)", id);
        }
    }

    @Override
    public void saveStepLog(Long buildRecordId, int stepOrder, String stepName,
                             String logContent, LocalDateTime startedAt, LocalDateTime finishedAt) {
        BuildLog stepLog = new BuildLog();
        stepLog.setBuildRecordId(buildRecordId);
        stepLog.setStepOrder(stepOrder);
        stepLog.setStepName(stepName);
        stepLog.setLogContent(logContent);
        stepLog.setLogSizeBytes((long) logContent.getBytes().length);
        stepLog.setStartedAt(startedAt);
        stepLog.setFinishedAt(finishedAt);
        stepLog.setCreatedAt(LocalDateTime.now());
        buildLogMapper.insert(stepLog);
        log.info("[BuildService] saveStepLog buildRecordId={} step={} ({} bytes)",
            buildRecordId, stepName, stepLog.getLogSizeBytes());
    }

    @Override
    public void markBuildFinished(Long id, String status, String imageTag,
                                    String harborImageUrl, LocalDateTime finishedAt) {
        int affected = buildRecordMapper.markFinished(id, status, imageTag, harborImageUrl, finishedAt);
        if (affected == 0) {
            log.warn("[BuildService] markBuildFinished no-op (id={} already terminal?)", id);
        }
    }

    // ============== helper ==============

    private BuildResponse toResponse(BuildRecord r) {
        return BuildResponse.builder()
            .id(r.getId())
            .projectId(r.getProjectId())
            .commitSha(r.getCommitSha())
            .commitMessage(r.getCommitMessage())
            .triggeredBy(r.getTriggeredBy())
            .triggerType(r.getTriggerType())
            .droneBuildId(r.getDroneBuildId())
            .status(r.getStatus())
            .imageTag(r.getImageTag())
            .harborImageUrl(r.getHarborImageUrl())
            .startedAt(r.getStartedAt())
            .finishedAt(r.getFinishedAt())
            .logPersisted(r.getLogPersisted())
            .createdAt(r.getCreatedAt())
            .build();
    }
}
