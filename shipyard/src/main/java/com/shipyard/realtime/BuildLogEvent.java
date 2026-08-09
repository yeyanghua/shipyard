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

package com.shipyard.realtime;

import com.shipyard.entity.BuildLog;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 事件 payload — build_log 步骤 或 build 终态.
 *
 * <p>前端 EventSource 通过 {@code event: name} 区分:
 * <ul>
 *   <li>{@code "step"} — {@link #stepName} 字段填充, step 日志</li>
 *   <li>{@code "build"} — {@link #status} 字段填充, build 终态</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildLogEvent {

    /** build_record.id (SSE 用, 客户端过滤) */
    private Long buildId;

    /** 事件类型 — "step" / "build" */
    private String eventType;

    // === step 事件用 ===
    private String stepName;
    private Integer stepOrder;
    private String logContent;
    private Long logSizeBytes;
    private LocalDateTime stepStartedAt;
    private LocalDateTime stepFinishedAt;

    // === build 事件用 ===
    private String status;
    private String imageTag;
    private String harborImageUrl;

    public static BuildLogEvent fromStep(BuildLog log) {
        return BuildLogEvent.builder()
                .buildId(log.getBuildRecordId())
                .eventType("step")
                .stepName(log.getStepName())
                .stepOrder(log.getStepOrder())
                .logContent(log.getLogContent())
                .logSizeBytes(log.getLogSizeBytes())
                .stepStartedAt(log.getStartedAt())
                .stepFinishedAt(log.getFinishedAt())
                .build();
    }

    public static BuildLogEvent fromFinished(Long buildId, String status, String imageTag, String harborImageUrl) {
        return BuildLogEvent.builder()
                .buildId(buildId)
                .eventType("build")
                .status(status)
                .imageTag(imageTag)
                .harborImageUrl(harborImageUrl)
                .build();
    }
}
