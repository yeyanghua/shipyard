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

import com.shipyard.entity.EnvVariable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 环境变量响应体 — GET /api/envs/{envId}/variables 列表.
 *
 * <p>{@code value} 字段语义:
 * <ul>
 *   <li>{@code isSecret=1} — Service 层 list() 已经把 value 改成 {@code "***"} 占位符</li>
 *   <li>{@code isSecret=0} — Service 层 list() 已经解密, value 是明文</li>
 * </ul>
 *
 * <p>要查 secret 明文走单独端点: {@code GET /api/envs/{envId}/variables/{key}} (单查解密).
 */
@Data
public class EnvVariableResponse {

    private Long id;
    private Long envId;
    private Long projectId; // null=全局
    private String key;
    private String value; // 已处理 (*** 或明文)
    private Integer isSecret;
    private String description;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public static EnvVariableResponse from(EnvVariable v) {
        if (v == null) return null;
        EnvVariableResponse r = new EnvVariableResponse();
        r.setId(v.getId());
        r.setEnvId(v.getEnvId());
        r.setProjectId(v.getProjectId());
        r.setKey(v.getVarKey());
        r.setValue(v.getVarValueEnc());
        r.setIsSecret(v.getIsSecret());
        r.setDescription(v.getDescription());
        r.setUpdatedBy(v.getUpdatedBy());
        r.setUpdatedAt(v.getUpdatedAt());
        return r;
    }
}
