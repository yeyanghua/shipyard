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

import com.shipyard.dto.BuildCreateRequest;
import com.shipyard.dto.BuildResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 构建业务服务 — BuildRecord 状态机 + 跟 drone 交互.
 *
 * <p>V1 demo 阶段, mock drone 走 {@code MockDroneClient.runMockBuild} 异步调以下方法:
 * <ul>
 *   <li>{@link #markBuildRunning(Long, LocalDateTime)} — PENDING → RUNNING</li>
 *   <li>{@link #saveStepLog(Long, int, String, String, LocalDateTime, LocalDateTime)} — 落 build_log</li>
 *   <li>{@link #markBuildFinished(Long, String, String, String, LocalDateTime)} — 终态</li>
 * </ul>
 *
 * <p>外部 API 走这些:
 * <ul>
 *   <li>{@link #createBuild(BuildCreateRequest)} — 触发构建</li>
 *   <li>{@link #getBuild(Long)} — 查构建</li>
 *   <li>{@link #listBuilds(Long, String, int, int)} — 分页列表</li>
 *   <li>{@link #cancelBuild(Long)} — 取消</li>
 *   <li>{@link #getStepLog(Long, String)} — 查单个 step 日志</li>
 *   <li>{@link #listStepLogs(Long)} — 列构建所有 step</li>
 * </ul>
 */
public interface BuildService {

    /**
     * 触发一次构建 — 落 PENDING record + 调 droneClient.triggerBuild.
     *
     * @param request 含 projectId / commitSha / commitMessage / triggeredBy
     * @return 新创建的 build record (status=PENDING, droneBuildId 已分配)
     */
    BuildResponse createBuild(BuildCreateRequest request);

    /**
     * 按 ID 查构建详情.
     *
     * @param id build_record.id
     * @return build record
     * @throws com.shipyard.common.exception.BusinessException NOT_FOUND 如果 id 不存在
     */
    BuildResponse getBuild(Long id);

    /**
     * 查项目下所有构建 — 分页, 可选 status 过滤.
     *
     * @param projectId  项目 ID
     * @param status     可选, status 过滤 (例 "SUCCESS" / "FAILED")
     * @param pageNum    1-based
     * @param pageSize   每页条数 (1-100, shipyard 默认 20)
     */
    List<BuildResponse> listBuilds(Long projectId, String status, int pageNum, int pageSize);

    /**
     * 取消构建 — 调 droneClient.cancelBuild + 改 status=CANCELED.
     *
     * <p>只能从 PENDING / RUNNING 取消, 终态报 BAD_REQUEST.
     */
    BuildResponse cancelBuild(Long id);

    /**
     * 查单个 step 日志 — 用于 BuildDetail 页右侧实时日志区.
     */
    String getStepLog(Long buildRecordId, String stepName);

    /**
     * 查构建下所有 step 元信息 (不含 logContent) — BuildDetail 页左侧 step 列表.
     */
    List<com.shipyard.dto.BuildLogResponse> listStepLogs(Long buildRecordId);

    // ============== drone 回调 / mock 内部用 ==============

    /**
     * PENDING → RUNNING — MockDroneClient 异步任务开始时调.
     */
    void markBuildRunning(Long id, LocalDateTime startedAt);

    /**
     * 落单个 step 日志 — drone webhook (V1.5) 或 mock drone 调.
     */
    void saveStepLog(
            Long buildRecordId,
            int stepOrder,
            String stepName,
            String logContent,
            LocalDateTime startedAt,
            LocalDateTime finishedAt);

    /**
     * 状态到终态 — drone webhook / mock drone 调.
     *
     * @param status          终态 (SUCCESS / FAILED / TIMEOUT / CANCELED)
     * @param imageTag        镜像 tag (SUCCESS 时填, 其他可 null)
     * @param harborImageUrl  Harbor URL (SUCCESS 时填, 其他可 null)
     * @param finishedAt      结束时间
     */
    void markBuildFinished(Long id, String status, String imageTag, String harborImageUrl, LocalDateTime finishedAt);
}
