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

package com.shipyard.controller;

import com.shipyard.dto.ApiResponse;
import com.shipyard.dto.EnvVariableResponse;
import com.shipyard.dto.EnvVariableUpsertRequest;
import com.shipyard.entity.EnvVariable;
import com.shipyard.service.EnvVariableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * EnvVariable Controller — /api/envs/{envId}/variables.
 *
 * <p>4 个端点: GET 列表 / PUT 批量 upsert / GET 单查明文 / DELETE 删除.
 */
@RestController
@RequestMapping("/api/envs/{envId}/variables")
@RequiredArgsConstructor
public class EnvVariableController {

    private final EnvVariableService envVariableService;

    /** GET /api/envs/{envId}/variables?projectId=xxx — 列表 (secret 显示 "***"). */
    @GetMapping
    public ApiResponse<List<EnvVariableResponse>> list(
        @PathVariable Long envId,
        @RequestParam(required = false) Long projectId
    ) {
        List<EnvVariable> list = envVariableService.list(envId, projectId);
        return ApiResponse.ok(list.stream().map(EnvVariableResponse::from).toList());
    }

    /** PUT /api/envs/{envId}/variables?projectId=xxx — 批量 upsert. */
    @PutMapping
    public ApiResponse<List<EnvVariableResponse>> batchUpsert(
        @PathVariable Long envId,
        @RequestParam(required = false) Long projectId,
        @RequestBody @Valid EnvVariableUpsertRequest req
    ) {
        List<EnvVariable> items = req.getItems().stream()
            .map(i -> {
                EnvVariable v = new EnvVariable();
                // 注意: DTO 字段是 key, Entity 字段是 varKey — BeanUtils 不会自动转换
                v.setVarKey(i.getKey());
                v.setVarValueEnc(i.getValue());   // 字段名复用, Service 加密
                v.setIsSecret(i.getIsSecret());
                v.setDescription(i.getDescription());
                return v;
            })
            .toList();
        List<EnvVariable> upserted = envVariableService.batchUpsert(envId, projectId, items, "demo-user");
        return ApiResponse.ok(upserted.stream().map(EnvVariableResponse::from).toList());
    }

    /**
     * GET /api/envs/{envId}/variables/{key}?projectId=xxx — 单查明文.
     *
     * <p>返回格式: {@code {"value": "明文"}} — 前端"显示明文"按钮调用.
     */
    @GetMapping("/{key}")
    public ApiResponse<Map<String, String>> getDecrypted(
        @PathVariable Long envId,
        @PathVariable String key,
        @RequestParam(required = false) Long projectId
    ) {
        String value = envVariableService.getDecryptedValue(envId, projectId, key);
        return ApiResponse.ok(Map.of("value", value));
    }

    /** DELETE /api/envs/{envId}/variables/{key}?projectId=xxx — 删除. */
    @DeleteMapping("/{key}")
    public ApiResponse<Void> delete(
        @PathVariable Long envId,
        @PathVariable String key,
        @RequestParam(required = false) Long projectId
    ) {
        envVariableService.delete(envId, projectId, key);
        return ApiResponse.ok();
    }
}
