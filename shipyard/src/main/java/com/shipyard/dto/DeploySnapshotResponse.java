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

import com.shipyard.entity.DeploySnapshot;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 部署快照响应体 — GET /api/deployments/{id}/snapshots 返回.
 *
 * <p>包含完整 deployYaml (LONGTEXT), DeployDetail 高级模式 yaml 预览用它.
 */
@Data
@Builder
public class DeploySnapshotResponse {

    private Long id;
    private Long deployRecordId;
    private Long envId;
    private Long projectId;
    private String deployYaml;
    private String deployYamlSha256;
    private String createdBy;
    private LocalDateTime createdAt;

    public static DeploySnapshotResponse from(DeploySnapshot s) {
        return DeploySnapshotResponse.builder()
                .id(s.getId())
                .deployRecordId(s.getDeployRecordId())
                .envId(s.getEnvId())
                .projectId(s.getProjectId())
                .deployYaml(s.getDeployYaml())
                .deployYamlSha256(s.getDeployYamlSha256())
                .createdBy(s.getCreatedBy())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
