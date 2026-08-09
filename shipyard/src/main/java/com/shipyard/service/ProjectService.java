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
import com.shipyard.entity.Project;

/**
 * Project Service — 项目元数据 CRUD.
 *
 * <p>V1 范围: 基础 CRUD, 软删, name 唯一校验, 列表分页+关键字搜索.
 *
 * <p>加密策略: 入参的 {@code repoToken} 是明文, Service 层调 {@code Encrypter} 加密后存 {@code repoTokenEnc}.
 * 列表/详情 API 不回显明文, 只暴露 {@code hasRepoToken: boolean} 标记 (V1.5 加 DTO 字段).
 */
public interface ProjectService {

    /**
     * 分页查询项目列表 — 按 {@code name} 或 {@code displayName} 模糊匹配.
     *
     * @param page    页码 (从 1 开始)
     * @param size    每页大小 (默认 20, 最大 500)
     * @param keyword 搜索关键字 (选填, 匹配 name / displayName)
     * @return 分页结果, {@code records} 是当前页数据
     */
    Page<Project> list(int page, int size, String keyword);

    /**
     * 获取项目详情.
     *
     * @param id 项目 ID
     * @return 项目实体
     * @throws com.shipyard.common.exception.BusinessException 资源不存在时抛 NOT_FOUND
     */
    Project get(Long id);

    /**
     * 创建项目.
     *
     * <p>校验项:
     * <ul>
     *   <li>{@code name} 唯一 — 已存在抛 RESOURCE_CONFLICT</li>
     *   <li>{@code repoProvider} 必须在 {@code [gitlab, gitee]} 内</li>
     *   <li>{@code projectType} 必须在 {@code [java_maven, java_gradle, node_pnpm, python_poetry, other]} 内</li>
     *   <li>{@code repoUrl} 必填</li>
     * </ul>
     *
     * @param project 待创建项目 (含明文 {@code repoToken}, Service 层加密)
     * @return 创建后的项目 (含自动生成的 id + createdAt)
     */
    Project create(Project project);

    /**
     * 更新项目.
     *
     * <p>校验项: 同 {@link #create}, 但 {@code name} 唯一约束只对"改名"生效.
     *
     * @param id      项目 ID
     * @param project 待更新字段 (含明文 {@code repoToken} 则加密; null 则不动)
     * @return 更新后的项目
     * @throws com.shipyard.common.exception.BusinessException 资源不存在时抛 NOT_FOUND
     */
    Project update(Long id, Project project);

    /**
     * 软删项目 — MyBatis-Plus {@code @TableLogic} 自动改 {@code deleted=1}.
     *
     * @param id 项目 ID
     * @throws com.shipyard.common.exception.BusinessException 资源不存在时抛 NOT_FOUND
     */
    void delete(Long id);
}
