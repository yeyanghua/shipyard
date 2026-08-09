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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 批量 upsert 环境变量请求体 — PUT /api/envs/{envId}/variables?projectId=xxx.
 *
 * <p>body 是数组, 每项 {@link Item} 是单条变量.
 */
@Data
public class EnvVariableUpsertRequest {

    @NotNull
    @NotEmpty
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        /** 变量名 (必填, 1-128 字符, 业务层校验格式). */
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 128)
        private String key;

        /** 变量值 (必填, 明文, Service 加密). */
        @jakarta.validation.constraints.NotNull
        private String value;

        /** 是否敏感, 1=隐藏, 0=明文. 默认 1. */
        private Integer isSecret;

        /** 选填, 最多 512 字符. */
        @jakarta.validation.constraints.Size(max = 512)
        private String description;
    }
}
