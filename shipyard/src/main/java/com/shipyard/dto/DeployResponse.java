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

package com.shipyard.dto;

import com.shipyard.entity.DeployRecord;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 部署响应体 — GET / POST /api/deployments 统一返回.
 *
 * <p>字段跟 deploy_record 一一对应 + 加 {@code workerId} (选中派活的 worker, 前端展示用).
 *
 * <p>回滚源 {@code currentSnapshotId} 也返, DeployDetail 页用它拉 snapshot 详情.
 */
@Data
@Builder
public class DeployResponse {

    private Long id;
    private Long projectId;
    private Long envId;
    private Long buildRecordId;
    private String imageTag;
    private String namespace;
    private String deployYamlSha256;
    private Long currentSnapshotId;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String triggeredBy;
    private String triggerType;
    private LocalDateTime createdAt;

    /** 选中执行 deploy 的 worker ID (M9 新增, 前端展示 "派给哪个 worker") */
    private Long workerId;

    public static DeployResponse from(DeployRecord r) {
        return DeployResponse.builder()
                .id(r.getId())
                .projectId(r.getProjectId())
                .envId(r.getEnvId())
                .buildRecordId(r.getBuildRecordId())
                .imageTag(r.getImageTag())
                .namespace(r.getNamespace())
                .deployYamlSha256(r.getDeployYamlSha256())
                .currentSnapshotId(r.getCurrentSnapshotId())
                .status(r.getStatus())
                .errorMessage(r.getErrorMessage())
                .startedAt(r.getStartedAt())
                .finishedAt(r.getFinishedAt())
                .triggeredBy(r.getTriggeredBy())
                .triggerType(r.getTriggerType())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
