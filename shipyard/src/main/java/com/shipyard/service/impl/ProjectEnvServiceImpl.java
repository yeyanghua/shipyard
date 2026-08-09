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
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.entity.ProjectEnv;
import com.shipyard.mapper.EnvMapper;
import com.shipyard.mapper.ProjectEnvMapper;
import com.shipyard.mapper.ProjectMapper;
import com.shipyard.service.ProjectEnvService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ProjectEnv Service 实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectEnvServiceImpl implements ProjectEnvService {

    private final ProjectEnvMapper projectEnvMapper;
    private final ProjectMapper projectMapper;
    private final EnvMapper envMapper;

    @Override
    public List<ProjectEnv> listByProject(Long projectId) {
        // 校验 project 存在
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在: id=" + projectId);
        }
        return projectEnvMapper.selectList(
            new LambdaQueryWrapper<ProjectEnv>()
                .eq(ProjectEnv::getProjectId, projectId)
                .orderByAsc(ProjectEnv::getEnvId)
        );
    }

    @Override
    @Transactional
    public ProjectEnv associate(Long projectId, Long envId) {
        // 校验两端都存在
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在: id=" + projectId);
        }
        if (envMapper.selectById(envId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "环境不存在: id=" + envId);
        }

        // 幂等: 已存在就返回
        ProjectEnv existing = projectEnvMapper.selectOne(
            new LambdaQueryWrapper<ProjectEnv>()
                .eq(ProjectEnv::getProjectId, projectId)
                .eq(ProjectEnv::getEnvId, envId)
        );
        if (existing != null) {
            log.info("项目 {} 已关联环境 {}, 跳过", projectId, envId);
            return existing;
        }

        ProjectEnv pe = new ProjectEnv();
        pe.setProjectId(projectId);
        pe.setEnvId(envId);
        projectEnvMapper.insert(pe);
        log.info("项目 {} 关联环境 {} 成功", projectId, envId);
        return pe;
    }

    @Override
    @Transactional
    public void unassociate(Long projectId, Long envId) {
        int rows = projectEnvMapper.delete(
            new LambdaQueryWrapper<ProjectEnv>()
                .eq(ProjectEnv::getProjectId, projectId)
                .eq(ProjectEnv::getEnvId, envId)
        );
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                "项目 " + projectId + " 未关联环境 " + envId);
        }
        log.info("项目 {} 取消关联环境 {}", projectId, envId);
    }
}
