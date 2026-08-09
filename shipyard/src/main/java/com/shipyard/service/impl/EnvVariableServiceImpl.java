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
import com.shipyard.crypto.CryptoException;
import com.shipyard.crypto.Encrypter;
import com.shipyard.entity.EnvVariable;
import com.shipyard.mapper.EnvMapper;
import com.shipyard.mapper.EnvVariableMapper;
import com.shipyard.mapper.ProjectMapper;
import com.shipyard.service.EnvVariableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EnvVariable Service 实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvVariableServiceImpl implements EnvVariableService {

    /** 列表展示时 secret 字段的占位符. */
    private static final String SECRET_PLACEHOLDER = "***";

    private final EnvVariableMapper envVariableMapper;
    private final EnvMapper envMapper;
    private final ProjectMapper projectMapper;
    private final Encrypter encrypter;

    // ==================== 查询 ====================

    @Override
    public List<EnvVariable> list(Long envId, Long projectId) {
        validateEnvAndProject(envId, projectId);

        LambdaQueryWrapper<EnvVariable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EnvVariable::getEnvId, envId);
        if (projectId != null) {
            // 查项目级 + 全局
            wrapper.and(w -> w
                .eq(EnvVariable::getProjectId, projectId)
                .or().isNull(EnvVariable::getProjectId)
            );
        } else {
            // 只查全局
            wrapper.isNull(EnvVariable::getProjectId);
        }
        wrapper.orderByAsc(EnvVariable::getVarKey);

        List<EnvVariable> list = envVariableMapper.selectList(wrapper);
        // secret 改占位符, 非 secret 解密
        for (EnvVariable v : list) {
            if (Integer.valueOf(1).equals(v.getIsSecret())) {
                v.setVarValueEnc(SECRET_PLACEHOLDER);
            } else {
                try {
                    v.setVarValueEnc(encrypter.decrypt(v.getVarValueEnc()));
                } catch (CryptoException e) {
                    log.warn("env_variable 解密失败 id={}, key={}, error={}",
                        v.getId(), v.getVarKey(), e.getMessage());
                    v.setVarValueEnc("[解密失败: " + e.getMessage() + "]");
                }
            }
        }
        return list;
    }

    @Override
    public String getDecryptedValue(Long envId, Long projectId, String key) {
        validateEnvAndProject(envId, projectId);
        EnvVariable v = findOne(envId, projectId, key);
        if (v == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "变量不存在: " + key);
        }
        try {
            return encrypter.decrypt(v.getVarValueEnc());
        } catch (CryptoException e) {
            log.error("变量解密失败 envId={} key={}", envId, key, e);
            throw new BusinessException(ErrorCode.CRYPTO_ERROR, "变量解密失败: " + key, e);
        }
    }

    // ==================== 写入 ====================

    @Override
    @Transactional
    public List<EnvVariable> batchUpsert(Long envId, Long projectId, List<EnvVariable> items, String updatedBy) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        validateEnvAndProject(envId, projectId);

        List<EnvVariable> result = new ArrayList<>(items.size());
        for (EnvVariable item : items) {
            validateItem(item);
            String key = item.getVarKey();
            String plainValue = item.getVarValueEnc();   // 入参是明文

            EnvVariable existing = findOne(envId, projectId, key);
            if (existing == null) {
                // 新增
                EnvVariable v = new EnvVariable();
                v.setEnvId(envId);
                v.setProjectId(projectId);
                v.setVarKey(key);
                v.setVarValueEnc(encrypter.encrypt(plainValue));
                v.setIsSecret(item.getIsSecret() != null ? item.getIsSecret() : 1);
                v.setDescription(item.getDescription());
                v.setUpdatedBy(updatedBy != null ? updatedBy : "demo-user");
                envVariableMapper.insert(v);
                result.add(v);
                log.info("新增 env_variable envId={} projectId={} key={}", envId, projectId, key);
            } else {
                // 更新 (value 重新加密; 同 key 传相同 value 不重复加密? 简化起见每次都重加密)
                existing.setVarValueEnc(encrypter.encrypt(plainValue));
                existing.setIsSecret(item.getIsSecret() != null ? item.getIsSecret() : existing.getIsSecret());
                existing.setDescription(item.getDescription() != null ? item.getDescription() : existing.getDescription());
                existing.setUpdatedBy(updatedBy != null ? updatedBy : "demo-user");
                envVariableMapper.updateById(existing);
                result.add(existing);
                log.info("更新 env_variable envId={} projectId={} key={}", envId, projectId, key);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public void delete(Long envId, Long projectId, String key) {
        EnvVariable existing = findOne(envId, projectId, key);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "变量不存在: " + key);
        }
        envVariableMapper.deleteById(existing.getId());
        log.info("删除 env_variable envId={} projectId={} key={}", envId, projectId, key);
    }

    // ==================== Resolve (M7 drone 用) ====================

    @Override
    public Map<String, String> resolveAll(Long envId, Long projectId) {
        validateEnvAndProject(envId, projectId);

        Map<String, String> result = new HashMap<>();

        // 1. 先查全局 (projectId == NULL), 作为基础
        List<EnvVariable> globals = envVariableMapper.selectList(
            new LambdaQueryWrapper<EnvVariable>()
                .eq(EnvVariable::getEnvId, envId)
                .isNull(EnvVariable::getProjectId)
        );
        for (EnvVariable v : globals) {
            result.put(v.getVarKey(), encrypter.decrypt(v.getVarValueEnc()));
        }

        // 2. 项目级覆盖
        if (projectId != null) {
            List<EnvVariable> projectScoped = envVariableMapper.selectList(
                new LambdaQueryWrapper<EnvVariable>()
                    .eq(EnvVariable::getEnvId, envId)
                    .eq(EnvVariable::getProjectId, projectId)
            );
            for (EnvVariable v : projectScoped) {
                result.put(v.getVarKey(), encrypter.decrypt(v.getVarValueEnc()));
            }
        }
        log.debug("env {} project {} 解析出 {} 个变量", envId, projectId, result.size());
        return result;
    }

    // ==================== 启动校验 ====================

    @Override
    public int validateAllOnStartup() {
        // 查所有非软删的 env_variable
        List<EnvVariable> all = envVariableMapper.selectList(null);
        int failed = 0;
        List<String> failedKeys = new ArrayList<>();
        for (EnvVariable v : all) {
            try {
                encrypter.decrypt(v.getVarValueEnc());
            } catch (CryptoException e) {
                failed++;
                failedKeys.add("id=" + v.getId() + " envId=" + v.getEnvId()
                    + " projectId=" + v.getProjectId() + " key=" + v.getVarKey());
            }
        }
        if (failed > 0) {
            String msg = "启动中止: " + failed + " 个 env_variable 解密失败\n"
                + String.join("\n", failedKeys);
            log.error(msg);
            throw new CryptoException(msg);
        }
        log.info("✅ 所有 env_variable 解密校验通过 ({} 条)", all.size());
        return all.size();
    }

    // ==================== 私有辅助 ====================

    /**
     * 校验 env / project 存在性.
     */
    private void validateEnvAndProject(Long envId, Long projectId) {
        if (envId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "envId 不能为空");
        }
        if (envMapper.selectById(envId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "环境不存在: id=" + envId);
        }
        if (projectId != null && projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在: id=" + projectId);
        }
    }

    /**
     * 查单条 (envId, projectId, key) — 包含 projectId 可能是 NULL 的特殊情况.
     *
     * <p>走 LambdaQueryWrapper, 因为 MyBatis-Plus 复合条件 + NULL 判断用 wrapper 较干净.
     */
    private EnvVariable findOne(Long envId, Long projectId, String key) {
        LambdaQueryWrapper<EnvVariable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EnvVariable::getEnvId, envId)
            .eq(EnvVariable::getVarKey, key);
        if (projectId == null) {
            wrapper.isNull(EnvVariable::getProjectId);
        } else {
            wrapper.eq(EnvVariable::getProjectId, projectId);
        }
        return envVariableMapper.selectOne(wrapper);
    }

    /**
     * 校验单条入参.
     */
    private void validateItem(EnvVariable item) {
        if (item == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "item 不能为空");
        }
        if (!StringUtils.hasText(item.getVarKey())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "varKey 不能为空");
        }
        if (item.getVarKey().length() > 128) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "varKey 不能超过 128 字符");
        }
        if (item.getVarValueEnc() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "value 不能为空");
        }
    }
}
