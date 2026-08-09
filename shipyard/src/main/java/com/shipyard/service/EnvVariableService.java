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

import com.shipyard.entity.EnvVariable;

import java.util.List;
import java.util.Map;

/**
 * EnvVariable Service — 环境变量 (env × project × key, 加密) CRUD + resolve.
 *
 * <p>核心职责 (V1):
 * <ul>
 *   <li><b>加密/解密</b> — {@code value} 走 {@code Encrypter} 加密入库, 列表展示时 secret 隐藏</li>
 *   <li><b>resolveAll</b> — 给构建时 (M7 drone) 用, 返回 {@code Map<key, value>}, 项目级覆盖全局</li>
 *   <li><b>启动校验</b> — {@link #validateAllOnStartup()} 全量解密, 失败抛 {@code CryptoException} 阻止启动</li>
 * </ul>
 *
 * <p>查询优先级 (resolveAll):
 * <ol>
 *   <li>查询 env_id + project_id 非空 的项目级变量</li>
 *   <li>查询 env_id + project_id 空 的全局变量</li>
 *   <li>同 key 项目级覆盖全局</li>
 * </ol>
 */
public interface EnvVariableService {

    /**
     * 列出某 env (+ 可选 project) 的所有变量.
     *
     * <p>返回的 {@link EnvVariable}:
     * <ul>
     *   <li>{@code isSecret=1} — {@code value} 字段被改写为 {@code "***"}, 不暴露明文</li>
     *   <li>{@code isSecret=0} — {@code value} 字段为解密后的明文</li>
     * </ul>
     *
     * @param envId     环境 ID (必填)
     * @param projectId 项目 ID (选填, null=只查全局变量)
     */
    List<EnvVariable> list(Long envId, Long projectId);

    /**
     * 查询单个变量的明文值 (解密后) — 供前端"显示明文"按钮调用.
     *
     * @param envId     环境 ID
     * @param projectId 项目 ID (选填, null=查全局)
     * @param key       变量名
     * @return 解密后的明文
     * @throws com.shipyard.common.exception.BusinessException 变量不存在
     */
    String getDecryptedValue(Long envId, Long projectId, String key);

    /**
     * 批量 upsert — 同一 {@code (envId, projectId, key)} 已存在则更新 value, 否则新增.
     *
     * <p>所有 value 都会被 {@code Encrypter.encrypt()} 加密.
     * 同名同 key 重复传 → 后者覆盖前者 (前端编辑常用).
     *
     * @param envId     环境 ID
     * @param projectId 项目 ID (选填, null=全局变量)
     * @param items     待 upsert 列表 ({@code key, value, isSecret, description})
     * @param updatedBy 更新人 (V1 demo 写 {@code "demo-user"})
     * @return upsert 后的变量列表
     */
    List<EnvVariable> batchUpsert(Long envId, Long projectId, List<EnvVariable> items, String updatedBy);

    /**
     * 删除单个变量.
     */
    void delete(Long envId, Long projectId, String key);

    /**
     * 解析 (resolve) — 给构建时 (M7 drone) 用, 返回所有变量的明文 Map.
     *
     * <p>规则: 项目级 ({@code projectId != NULL}) 优先于全局 ({@code projectId == NULL}), 同 key 覆盖.
     *
     * @param envId     环境 ID
     * @param projectId 项目 ID (选填, null=只要全局变量)
     * @return {@code Map<key, value>} 解密后的明文, 直接喂 drone vars.yaml
     */
    Map<String, String> resolveAll(Long envId, Long projectId);

    /**
     * 启动时全量校验 — 解密每条记录, 失败抛 {@code CryptoException} 阻止启动.
     *
     * <p>设计动机: shipyard 启动后发现"运行到一半 500" 体验极差.
     * 启动时一次性校验, 列出损坏的变量 ID + key, 让运维立即修.
     *
     * @return 校验通过的记录数
     * @throws com.shipyard.crypto.CryptoException 有任何变量解密失败
     */
    int validateAllOnStartup();
}
