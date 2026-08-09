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

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 构建 step 日志元信息 — {@code GET /api/builds/{id}/steps}.
 *
 * <p><b>不</b>含 {@code logContent} (大字段, 按需查) — 这是 step 列表, 详情页左侧用.
 * 单个 step 全文日志走 {@code GET /api/builds/{id}/steps/{stepName}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildLogResponse {
    private Long id;
    private Long buildRecordId;
    private String stepName;
    private Integer stepOrder;
    private Long logSizeBytes;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
