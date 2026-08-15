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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shipyard.dto.ApiResponse;
import com.shipyard.dto.EnvCreateRequest;
import com.shipyard.dto.EnvResponse;
import com.shipyard.dto.EnvUpdateRequest;
import com.shipyard.dto.PageResponse;
import com.shipyard.entity.Env;
import com.shipyard.service.EnvService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Env Controller — /api/envs.
 *
 * <p>V1 (V5 撤回后) 5 个 env CRUD:
 * <ul>
 *   <li>GET 列表 / GET 详情 / POST 创建 / PUT 更新 / DELETE 软删</li>
 * </ul>
 *
 * <p>V1 阶段 in-process 模拟: env 表自己管 workerUrl / k8sNamespace (V5 撤回 V4 redesign, 回到 V3 模型).
 * worker 部署细节 (pod / token) 不再预登记, shipyard 后端内部维护 worker 状态.
 *
 * <p>未来如果重新评估 V1.5+ 接入真 worker, 从 git history 恢复 M9.5 commit d029106 + 写 V6 migration.
 */
@RestController
@RequestMapping("/api/envs")
@RequiredArgsConstructor
public class EnvController {

    private final EnvService envService;

    @GetMapping
    public ApiResponse<PageResponse<EnvResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean production) {
        Page<Env> p = envService.list(page, size, keyword, production);
        return ApiResponse.ok(PageResponse.from(p, EnvResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<EnvResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(EnvResponse.from(envService.get(id)));
    }

    @PostMapping
    public ApiResponse<EnvResponse> create(@RequestBody @Valid EnvCreateRequest req) {
        Env e = new Env();
        BeanUtils.copyProperties(req, e);
        // V1 阶段 (V5 撤回后): env 表自管 workerUrl / k8sNamespace, BeanUtils.copyProperties 自动从 req 复制
        return ApiResponse.ok(EnvResponse.from(envService.create(e)));
    }

    @PutMapping("/{id}")
    public ApiResponse<EnvResponse> update(@PathVariable Long id, @RequestBody @Valid EnvUpdateRequest req) {
        Env e = new Env();
        BeanUtils.copyProperties(req, e);
        return ApiResponse.ok(EnvResponse.from(envService.update(id, e)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        envService.delete(id);
        return ApiResponse.ok();
    }
}
