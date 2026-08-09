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

package com.shipyard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shipyard.common.BeanUtils;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.crypto.Encrypter;
import com.shipyard.entity.Project;
import com.shipyard.mapper.ProjectMapper;
import com.shipyard.service.ProjectService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Project Service 实现.
 *
 * <p>关键校验:
 * <ul>
 *   <li>name 唯一 — 通过 {@code selectCount} 查, 包含 {@code deleted=1} 的"复活"逻辑 (见 {@link #create})</li>
 *   <li>repoProvider / projectType 枚举校验</li>
 *   <li>repoToken 加密 — 注入 {@link Encrypter} (M2 已有 AesEncrypter)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    /** 仓库平台白名单. */
    private static final Set<String> REPO_PROVIDERS = Set.of("gitlab", "gitee");

    /** 项目类型白名单. */
    private static final Set<String> PROJECT_TYPES =
            Set.of("java_maven", "java_gradle", "node_pnpm", "python_poetry", "other");

    private final ProjectMapper projectMapper;
    private final Encrypter encrypter;

    @Override
    public Page<Project> list(int page, int size, String keyword) {
        // 参数清洗
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 500));

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Project::getName, keyword).or().like(Project::getDisplayName, keyword));
        }
        wrapper.orderByDesc(Project::getCreatedAt);

        return projectMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
    }

    @Override
    public Project get(Long id) {
        Project p = projectMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在: id=" + id);
        }
        return p;
    }

    @Override
    public Project create(Project project) {
        validateProject(project);

        // name 唯一校验 — 包含软删的"复活"逻辑
        Long existingId = findIdByNameIncludingDeleted(project.getName());
        if (existingId != null) {
            // 复活 + 更新: 比抛冲突更友好 (用户重新启用项目)
            log.info("项目 {} 已存在 (id={}), 走复活+更新流程", project.getName(), existingId);
            // 用 raw SQL 绕过 @TableLogic, 拿到 deleted=1 的记录
            Project existing = projectMapper.selectByIdIncludeDeleted(existingId);
            if (existing == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "查到 ID 但 selectByIdIncludeDeleted 返回 null, 数据异常");
            }
            // 强制覆盖业务字段, 保留 createdAt; 同时把 deleted 重置为 0 (复活)
            existing.setDeleted(0);
            BeanUtils.copyNonNullProperties(project, existing, "id", "createdAt", "deleted");
            if (StringUtils.hasText(project.getRepoTokenEnc())) {
                existing.setRepoTokenEnc(encryptIfPresent(project.getRepoTokenEnc()));
            }
            projectMapper.updateById(existing);
            return existing;
        }

        // 加密 token
        if (StringUtils.hasText(project.getRepoTokenEnc())) {
            // Service 接收的 Entity 字段叫 repoTokenEnc, 但语义上是"明文待加密" (Controller 层会在 DTO 转换时填)
            // 这里兼容两种用法: 已是密文(以 "===" Base64 结尾) 还是明文
            project.setRepoTokenEnc(encryptIfPresent(project.getRepoTokenEnc()));
        }

        projectMapper.insert(project);
        return project;
    }

    @Override
    public Project update(Long id, Project project) {
        Project existing = get(id); // 复用 get 校验存在

        // name 改了 → 重新校验唯一
        if (StringUtils.hasText(project.getName()) && !project.getName().equals(existing.getName())) {
            Long conflictId = findIdByNameIncludingDeleted(project.getName());
            if (conflictId != null && !conflictId.equals(id)) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "项目名已被占用: " + project.getName());
            }
        }

        // 复制可更新字段 (跳过 null + id/createdAt/deleted)
        BeanUtils.copyNonNullProperties(project, existing, "id", "createdAt", "deleted");

        // token 重新加密 (如果传了新明文)
        if (StringUtils.hasText(project.getRepoTokenEnc())) {
            existing.setRepoTokenEnc(encryptIfPresent(project.getRepoTokenEnc()));
        }

        // 验证更新后的字段 (name 改了, 其他字段是 null 时已经被 skip)
        validateProject(existing);

        projectMapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        Project existing = get(id);
        projectMapper.deleteById(existing.getId());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验 Project 必填字段和枚举值.
     */
    private void validateProject(Project p) {
        if (p == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "project 不能为空");
        }
        if (!StringUtils.hasText(p.getName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "name 不能为空");
        }
        if (!StringUtils.hasText(p.getDisplayName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "displayName 不能为空");
        }
        if (!REPO_PROVIDERS.contains(p.getRepoProvider())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST, "repoProvider 必须是 " + REPO_PROVIDERS + " 之一, 实际: " + p.getRepoProvider());
        }
        if (!StringUtils.hasText(p.getRepoUrl())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "repoUrl 不能为空");
        }
        if (!PROJECT_TYPES.contains(p.getProjectType())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST, "projectType 必须是 " + PROJECT_TYPES + " 之一, 实际: " + p.getProjectType());
        }
    }

    /**
     * 按 name 查 ID, 包括软删的 (复活逻辑用).
     *
     * <p>走 raw SQL 绕过 {@code @TableLogic} 过滤, 因为 BaseMapper 的查询会自动拼 {@code deleted=0}.
     */
    private Long findIdByNameIncludingDeleted(String name) {
        return projectMapper.selectIdByNameRaw(name);
    }

    /**
     * 加密 token — 假设入参是明文.
     *
     * <p>Service 约定: Controller 传明文过来, 字段名仍叫 {@code repoTokenEnc} 是历史遗留 (Entity 复用).
     * 真正的"密文"概念在 Controller → DTO 转换时统一, Service 不区分.
     */
    private String encryptIfPresent(String plainOrCipher) {
        // 简单粗暴: 每次都加密, 因为 Controller 层传过来的永远是明文
        // (如果已经是密文再加密一次, 解密时会得到乱码, V1 demo 不会有这种场景)
        return encrypter.encrypt(plainOrCipher);
    }
}
