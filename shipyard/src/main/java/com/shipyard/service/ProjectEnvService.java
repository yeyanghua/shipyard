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

import com.shipyard.entity.ProjectEnv;
import java.util.List;

/**
 * ProjectEnv Service — 项目-环境关联 CRUD.
 *
 * <p>特点:
 * <ul>
 *   <li>复合主键 — 不用 {@code selectById}, 全程走 {@code LambdaQueryWrapper}</li>
 *   <li>硬删 — 不走软删, 重建成本低</li>
 *   <li>幂等: 重复关联 (project_id, env_id) 走"已存在"返回, 不抛错</li>
 * </ul>
 */
public interface ProjectEnvService {

    /**
     * 列出某项目关联的所有环境 — 返回 ProjectEnv 记录 (含 envId).
     * 调用方拿 envId 再去查 Env 详情 (按需 join).
     */
    List<ProjectEnv> listByProject(Long projectId);

    /**
     * 关联项目和环境 (幂等 — 重复关联返回已存在记录).
     */
    ProjectEnv associate(Long projectId, Long envId);

    /**
     * 取消关联 (硬删).
     */
    void unassociate(Long projectId, Long envId);
}
