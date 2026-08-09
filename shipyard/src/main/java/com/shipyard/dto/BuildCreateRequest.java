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

import lombok.Data;

/**
 * 触发构建请求 — {@code POST /api/builds} body.
 */
@Data
public class BuildCreateRequest {

    /** 项目 ID */
    private Long projectId;

    /** commit SHA (1-64 字符) */
    private String commitSha;

    /** commit message (选填, 默认 "manual trigger") */
    private String commitMessage;

    /** 触发人 (前端传 userId / 邮箱, 默认 "unknown") */
    private String triggeredBy;
}
