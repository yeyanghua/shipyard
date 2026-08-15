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

package com.shipyard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shipyard.entity.DeployRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * DeployRecord Mapper — 继承 {@link BaseMapper} 拿现成 CRUD.
 *
 * <p>V9 自定义 SQL:
 * <ul>
 *   <li>{@link #markRunning(Long, LocalDateTime)} — PENDING → RUNNING 时填 started_at</li>
 *   <li>{@link #markFinished(Long, String, String, LocalDateTime)} — 任意终态更新</li>
 *   <li>{@link #updateCurrentSnapshot(Long, Long)} — 部署成功后回填 current_snapshot_id</li>
 * </ul>
 */
@Mapper
public interface DeployRecordMapper extends BaseMapper<DeployRecord> {

    /**
     * PENDING → RUNNING 时填 started_at.
     *
     * <p>条件 {@code status = 'PENDING'} 防止重复 mark.
     */
    @Update("UPDATE deploy_record SET status = 'RUNNING', started_at = #{startedAt} "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markRunning(@Param("id") Long id, @Param("startedAt") LocalDateTime startedAt);

    /**
     * 任意终态 (SUCCESS / FAILED / TIMEOUT / CANCELED) 更新.
     *
     * <p>条件 {@code status NOT IN ('SUCCESS','FAILED','TIMEOUT','CANCELED')} 防止覆盖终态.
     */
    @Update("UPDATE deploy_record SET status = #{status}, "
            + "error_message = IFNULL(#{errorMessage}, error_message), "
            + "finished_at = #{finishedAt} "
            + "WHERE id = #{id} AND status NOT IN ('SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELED')")
    int markFinished(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") LocalDateTime finishedAt);

    /**
     * 部署成功后回填 current_snapshot_id (部署快照 id).
     */
    @Update("UPDATE deploy_record SET current_snapshot_id = #{snapshotId} WHERE id = #{deployId}")
    int updateCurrentSnapshot(@Param("deployId") Long deployId, @Param("snapshotId") Long snapshotId);

    /**
     * 按 projectId + envId 列 deploy 记录, 按 id DESC (最新在前).
     */
    @Select("SELECT * FROM deploy_record "
            + "WHERE project_id = #{projectId} AND env_id = #{envId} AND deleted = 0 "
            + "ORDER BY id DESC")
    java.util.List<DeployRecord> selectByProjectAndEnv(@Param("projectId") Long projectId, @Param("envId") Long envId);
}
