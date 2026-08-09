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

import com.shipyard.entity.PipelineTemplate;
import java.util.List;

/**
 * PipelineTemplate Service — 流水线模板版本管理.
 *
 * <p>V1 业务规则 (M6 拍板):
 * <ul>
 *   <li><b>版本只增不改</b>: 已 approved 的版本是 immutable, 想改只能 fork 新版本 (review_status 重置 draft)</li>
 *   <li><b>一项目一 active</b>: 同一 project 同时只有一个 {@code is_active=1} 的版本, activate 时自动 unactivate 其他</li>
 *   <li><b>active 必须 approved</b>: 不能 activate 一个 draft 状态的版本</li>
 *   <li><b>删除</b>: draft / rejected 可软删, approved + active 的不能直接删 (要先 activate 其他版本)</li>
 * </ul>
 *
 * <p>Controller / M6 4 BuildService 集成会通过这个 service 做:
 * <ul>
 *   <li>用户手动编辑 pipeline (走 {@link #create} / {@link #update})</li>
 *   <li>AI 生成 pipeline (走 {@link #create} + 写 {@code aiModifiedBy} / {@code aiPrompt})</li>
 *   <li>用户审批/驳回 (走 {@link #approve} / {@link #reject})</li>
 *   <li>用户切换 active 版本 (走 {@link #activate})</li>
 * </ul>
 */
public interface PipelineTemplateService {

    /**
     * 查项目的所有版本, 按 version 降序.
     *
     * @param projectId 项目 ID
     * @return 版本列表 (空列表 = 该项目还没建过 pipeline)
     */
    List<PipelineTemplate> listByProject(Long projectId);

    /**
     * 查项目当前 active 版本 — 0 或 1 个.
     *
     * @param projectId 项目 ID
     * @return active 版本, 或 {@code null} (项目还没 activate 过任何版本)
     */
    PipelineTemplate getActive(Long projectId);

    /**
     * 按 ID 查 pipeline 详情.
     *
     * @param id pipeline ID
     * @return 实体
     * @throws com.shipyard.common.exception.BusinessException 资源不存在时抛 NOT_FOUND
     */
    PipelineTemplate get(Long id);

    /**
     * 创建新版本 — 自动算 version (= MAX+1), 状态默认 draft, is_active=0.
     *
     * <p>适用场景:
     * <ul>
     *   <li>用户手动新建 (AI 字段全 NULL)</li>
     *   <li>AI 生成 (填 {@code aiModifiedBy} + {@code aiPrompt})</li>
     *   <li>Fork 已 approved 版本 (传入已 approved 的 yamlContent, 创建新 draft 版本)</li>
     * </ul>
     *
     * @param template  新版本内容 (yamlContent / aiModifiedBy / aiPrompt 必填或可空)
     * @param createdBy 创建人 (用户 ID)
     * @return 创建后的版本 (含自动生成的 id / version / reviewStatus=draft / isActive=0 / createdAt)
     * @throws com.shipyard.common.exception.BusinessException projectId 不存在或 yamlContent 为空时抛
     */
    PipelineTemplate create(PipelineTemplate template, String createdBy);

    /**
     * 更新版本内容 — <b>只能更新 draft / rejected 状态</b>, approved 是 immutable.
     *
     * <p>更新范围: yamlContent / aiModifiedBy / aiPrompt. 不允许改: projectId, version, reviewStatus, isActive.
     *
     * @param id     pipeline ID
     * @param patch  增量字段 (null 字段不动)
     * @return 更新后的版本
     * @throws com.shipyard.common.exception.BusinessException 不存在 / 已 approved 时抛
     */
    PipelineTemplate update(Long id, PipelineTemplate patch);

    /**
     * 审批通过 — {@code review_status: draft → approved}.
     *
     * <p>不会自动 activate, 调用方需要再调 {@link #activate}.
     *
     * @param id pipeline ID
     * @return 更新后的版本
     * @throws com.shipyard.common.exception.BusinessException 不存在 / 状态非 draft 时抛
     */
    PipelineTemplate approve(Long id);

    /**
     * 驳回 — {@code review_status: draft → rejected}.
     *
     * <p>rejected 是终态, 不能复活, 想改就 fork 新版本.
     *
     * @param id pipeline ID
     * @return 更新后的版本
     * @throws com.shipyard.common.exception.BusinessException 不存在 / 状态非 draft 时抛
     */
    PipelineTemplate reject(Long id);

    /**
     * 激活 — 把目标版本设为本项目 active, 同时把同 project 其他 active 置 0.
     *
     * <p>前置条件: 目标版本必须是 {@code approved} 状态, 否则抛 {@code BAD_REQUEST}.
     *
     * @param id pipeline ID
     * @return 更新后的版本
     * @throws com.shipyard.common.exception.BusinessException 不存在 / 未 approved 时抛
     */
    PipelineTemplate activate(Long id);

    /**
     * 软删版本.
     *
     * <p>业务约束:
     * <ul>
     *   <li>approved + active 的不能删, 要先 activate 其他版本</li>
     *   <li>approved + 非 active 的可以删 (用户主动废弃)</li>
     *   <li>draft / rejected 任意删</li>
     * </ul>
     *
     * @param id pipeline ID
     * @throws com.shipyard.common.exception.BusinessException 不存在 / 违反约束时抛
     */
    void delete(Long id);
}
