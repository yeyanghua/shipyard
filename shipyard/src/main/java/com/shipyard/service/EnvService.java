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

package com.shipyard.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shipyard.entity.Env;

/**
 * Env Service — 环境元数据 CRUD.
 *
 * <p>跟 ProjectService 类似, 区别:
 * <ul>
 *   <li>字段: {@code k8sNamespace} / {@code workerUrl} / {@code workerTokenEnc} / {@code isProduction}</li>
 *   <li>加密: {@code workerTokenEnc} 跟 {@code repoTokenEnc} 一样加密</li>
 *   <li>{@code isProduction} 默认 0 (dev), 创建时可指定</li>
 * </ul>
 */
public interface EnvService {

    /**
     * 分页查询环境列表 — 按 {@code name} / {@code displayName} 模糊匹配.
     *
     * @param page          页码
     * @param size          每页大小
     * @param keyword       搜索关键字 (选填)
     * @param production    按生产环境过滤 (选填, null=全部)
     * @return 分页结果
     */
    Page<Env> list(int page, int size, String keyword, Boolean production);

    /**
     * 获取环境详情.
     */
    Env get(Long id);

    /**
     * 创建环境.
     */
    Env create(Env env);

    /**
     * 更新环境.
     */
    Env update(Long id, Env env);

    /**
     * 软删环境.
     */
    void delete(Long id);
}
