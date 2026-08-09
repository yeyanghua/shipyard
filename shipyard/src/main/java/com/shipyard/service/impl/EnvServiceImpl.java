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
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.crypto.Encrypter;
import com.shipyard.entity.Env;
import com.shipyard.mapper.EnvMapper;
import com.shipyard.service.EnvService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Env Service 实现 — 跟 {@link ProjectServiceImpl} 同构, 字段差异.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvServiceImpl implements EnvService {

    /** 集群类型白名单. V1 只支持 k8s. */
    private static final Set<String> CLUSTER_TYPES = Set.of("k8s");

    private final EnvMapper envMapper;
    private final Encrypter encrypter;

    @Override
    public Page<Env> list(int page, int size, String keyword, Boolean production) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 500));

        LambdaQueryWrapper<Env> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                .like(Env::getName, keyword)
                .or().like(Env::getDisplayName, keyword)
            );
        }
        if (production != null) {
            wrapper.eq(Env::getIsProduction, production ? 1 : 0);
        }
        wrapper.orderByAsc(Env::getIsProduction).orderByAsc(Env::getName);

        return envMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
    }

    @Override
    public Env get(Long id) {
        Env e = envMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "环境不存在: id=" + id);
        }
        return e;
    }

    @Override
    public Env create(Env env) {
        validateEnv(env);

        // name 唯一 (含软删复活)
        Long existingId = envMapper.selectIdByNameRaw(env.getName());
        if (existingId != null) {
            log.info("环境 {} 已存在 (id={}), 走复活+更新流程", env.getName(), existingId);
            Env existing = envMapper.selectById(existingId);
            BeanUtils.copyProperties(env, existing, "id", "createdAt", "deleted");
            if (StringUtils.hasText(env.getWorkerTokenEnc())) {
                existing.setWorkerTokenEnc(encrypter.encrypt(env.getWorkerTokenEnc()));
            }
            envMapper.updateById(existing);
            return existing;
        }

        // 加密 worker token
        if (StringUtils.hasText(env.getWorkerTokenEnc())) {
            env.setWorkerTokenEnc(encrypter.encrypt(env.getWorkerTokenEnc()));
        }
        // 默认 clusterType
        if (!StringUtils.hasText(env.getClusterType())) {
            env.setClusterType("k8s");
        }
        // 默认 isProduction=0
        if (env.getIsProduction() == null) {
            env.setIsProduction(0);
        }

        envMapper.insert(env);
        return env;
    }

    @Override
    public Env update(Long id, Env env) {
        Env existing = get(id);

        if (StringUtils.hasText(env.getName()) && !env.getName().equals(existing.getName())) {
            Long conflictId = envMapper.selectIdByNameRaw(env.getName());
            if (conflictId != null && !conflictId.equals(id)) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "环境名已被占用: " + env.getName());
            }
        }

        BeanUtils.copyProperties(env, existing, "id", "createdAt", "deleted");
        if (StringUtils.hasText(env.getWorkerTokenEnc())) {
            existing.setWorkerTokenEnc(encrypter.encrypt(env.getWorkerTokenEnc()));
        }
        validateEnv(existing);

        envMapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        Env existing = get(id);
        envMapper.deleteById(existing.getId());
    }

    // ==================== 私有辅助方法 ====================

    private void validateEnv(Env e) {
        if (e == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "env 不能为空");
        }
        if (!StringUtils.hasText(e.getName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "name 不能为空");
        }
        if (!StringUtils.hasText(e.getDisplayName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "displayName 不能为空");
        }
        if (!StringUtils.hasText(e.getK8sNamespace())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "k8sNamespace 不能为空");
        }
        if (!StringUtils.hasText(e.getWorkerUrl())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "workerUrl 不能为空");
        }
        // clusterType 允许默认 ("k8s")
        if (StringUtils.hasText(e.getClusterType()) && !CLUSTER_TYPES.contains(e.getClusterType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "clusterType 必须是 " + CLUSTER_TYPES + " 之一, 实际: " + e.getClusterType());
        }
    }
}
