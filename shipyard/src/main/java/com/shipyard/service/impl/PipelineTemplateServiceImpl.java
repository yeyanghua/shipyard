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

import com.shipyard.common.BeanUtils;
import com.shipyard.common.enums.ReviewStatus;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.entity.PipelineTemplate;
import com.shipyard.mapper.PipelineTemplateMapper;
import com.shipyard.service.PipelineTemplateService;
import com.shipyard.service.ProjectService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * PipelineTemplate Service 实现.
 *
 * <p>关键业务规则 (跟 interface 注释一致):
 * <ul>
 *   <li>approved 版本 immutable, 想改 fork 新版本</li>
 *   <li>一项目同时只有一个 active, activate 时 unactivate 其他</li>
 *   <li>active 必须 approved</li>
 *   <li>approved + active 的不能直接删</li>
 * </ul>
 *
 * <p>事务边界: {@link #create} / {@link #activate} / {@link #approve} / {@link #reject} 都加
 * {@code @Transactional}, 因为涉及多条 SQL (insert + unactivateOthers) 必须原子.
 * {@link #update} / {@link #delete} 单条 SQL 自动 commit, 跟 M5 一样不加事务.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineTemplateServiceImpl implements PipelineTemplateService {

    private final PipelineTemplateMapper pipelineTemplateMapper;
    private final ProjectService projectService;

    @Override
    public List<PipelineTemplate> listByProject(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "projectId 不能为空");
        }
        return pipelineTemplateMapper.selectVersionsByProjectId(projectId);
    }

    @Override
    public List<PipelineTemplate> listByProjectIncludeDeleted(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "projectId 不能为空");
        }
        return pipelineTemplateMapper.selectVersionsByProjectIdIncludeDeleted(projectId);
    }

    @Override
    public PipelineTemplate getActive(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "projectId 不能为空");
        }
        return pipelineTemplateMapper.selectActiveByProjectId(projectId);
    }

    @Override
    public PipelineTemplate get(Long id) {
        PipelineTemplate t = pipelineTemplateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Pipeline 不存在: id=" + id);
        }
        return t;
    }

    @Override
    @Transactional
    public PipelineTemplate create(PipelineTemplate template, String createdBy) {
        validateForCreate(template);

        // 校验项目存在 — 复用 ProjectService.get() 的 NOT_FOUND 业务码
        projectService.get(template.getProjectId());

        // 算下一个 version 号 (= MAX + 1, 0 表示首个版本, 变 1)
        int nextVersion = pipelineTemplateMapper.selectMaxVersion(template.getProjectId()) + 1;

        template.setVersion(nextVersion);
        template.setReviewStatus(ReviewStatus.DRAFT.name());
        template.setIsActive(0);
        template.setCreatedBy(StringUtils.hasText(createdBy) ? createdBy : "unknown");
        // createdAt 由 MetaObjectHandlerImpl 自动填, deleted 由 @TableLogic 默认 0

        pipelineTemplateMapper.insert(template);
        log.info(
                "[PipelineTemplate] 创建 project={} version={} id={} createdBy={} aiModifiedBy={}",
                template.getProjectId(),
                nextVersion,
                template.getId(),
                createdBy,
                template.getAiModifiedBy());
        return template;
    }

    @Override
    public PipelineTemplate update(Long id, PipelineTemplate patch) {
        PipelineTemplate existing = get(id); // 复用 get 校验存在

        // immutable 校验: approved 状态不能改
        if (ReviewStatus.APPROVED.name().equals(existing.getReviewStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "已审批的 Pipeline 不可修改, 请 fork 新版本: id=" + id);
        }

        // 复制可更新字段 (跳过 null + id/projectId/version/reviewStatus/isActive/createdAt/deleted)
        BeanUtils.copyNonNullProperties(
                patch, existing, "id", "projectId", "version", "reviewStatus", "isActive", "createdAt", "deleted");

        // YAML 非空兜底校验 (如果 patch 显式传了 yamlContent)
        if (patch.getYamlContent() != null && !StringUtils.hasText(patch.getYamlContent())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "yamlContent 不能为空字符串");
        }

        pipelineTemplateMapper.updateById(existing);
        log.info(
                "[PipelineTemplate] 更新 id={} version={} (reviewStatus={})",
                existing.getId(),
                existing.getVersion(),
                existing.getReviewStatus());
        return existing;
    }

    @Override
    @Transactional
    public PipelineTemplate approve(Long id) {
        PipelineTemplate existing = get(id);
        if (!ReviewStatus.DRAFT.name().equals(existing.getReviewStatus())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST, "只能审批 DRAFT 状态的 Pipeline, 当前状态: " + existing.getReviewStatus());
        }
        existing.setReviewStatus(ReviewStatus.APPROVED.name());
        pipelineTemplateMapper.updateById(existing);
        log.info(
                "[PipelineTemplate] 审批通过 id={} version={} project={}",
                existing.getId(),
                existing.getVersion(),
                existing.getProjectId());
        return existing;
    }

    @Override
    @Transactional
    public PipelineTemplate reject(Long id) {
        PipelineTemplate existing = get(id);
        if (!ReviewStatus.DRAFT.name().equals(existing.getReviewStatus())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST, "只能驳回 DRAFT 状态的 Pipeline, 当前状态: " + existing.getReviewStatus());
        }
        existing.setReviewStatus(ReviewStatus.REJECTED.name());
        pipelineTemplateMapper.updateById(existing);
        log.info(
                "[PipelineTemplate] 驳回 id={} version={} project={}",
                existing.getId(),
                existing.getVersion(),
                existing.getProjectId());
        return existing;
    }

    @Override
    @Transactional
    public PipelineTemplate activate(Long id) {
        PipelineTemplate existing = get(id);

        // 前置校验: active 必须 approved
        if (!ReviewStatus.APPROVED.name().equals(existing.getReviewStatus())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST, "只能激活 APPROVED 状态的 Pipeline, 当前状态: " + existing.getReviewStatus());
        }

        // 1) 同 project 其他 active 置 0
        pipelineTemplateMapper.unactivateOthersByProjectId(existing.getProjectId(), existing.getId());

        // 2) 目标版本置 1
        existing.setIsActive(1);
        pipelineTemplateMapper.updateById(existing);

        log.info(
                "[PipelineTemplate] 激活 id={} version={} project={} (其他同 project active 已 unactivate)",
                existing.getId(),
                existing.getVersion(),
                existing.getProjectId());
        return existing;
    }

    @Override
    public void delete(Long id) {
        PipelineTemplate existing = get(id);

        // 业务约束: approved + active 的不能删
        if (ReviewStatus.APPROVED.name().equals(existing.getReviewStatus())
                && Integer.valueOf(1).equals(existing.getIsActive())) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_CONFLICT, "已审批且 active 的 Pipeline 不能删除, 请先 activate 其他版本: id=" + id);
        }

        pipelineTemplateMapper.deleteByIdForce(existing.getId());
        log.info(
                "[PipelineTemplate] 软删 id={} version={} project={} reviewStatus={} isActive={}",
                existing.getId(),
                existing.getVersion(),
                existing.getProjectId(),
                existing.getReviewStatus(),
                existing.getIsActive());
    }

    @Override
    public void forceDelete(Long id) {
        // forceDelete 路径: 直接调 mapper 物理删, 不查 row (避免 @TableLogic 过滤 + 软删行也能删)
        int affected = pipelineTemplateMapper.deleteByIdForce(id);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Pipeline 不存在: id=" + id);
        }
        log.info("[PipelineTemplate] 强制删除 id={} (跳过业务约束, affected={})", id, affected);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验 create 入参.
     */
    private void validateForCreate(PipelineTemplate t) {
        if (t == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Pipeline 不能为空");
        }
        if (t.getProjectId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "projectId 不能为空");
        }
        if (!StringUtils.hasText(t.getYamlContent())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "yamlContent 不能为空");
        }
    }
}
