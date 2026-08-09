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
import com.shipyard.entity.BuildRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * BuildRecord Mapper — 继承 {@link BaseMapper} 拿现成 CRUD.
 *
 * <p>V1 自定义 SQL:
 * <ul>
 *   <li>{@link #selectByDroneBuildId(String)} — drone webhook 用 build_id 找记录</li>
 *   <li>{@link #markRunning(Long, java.time.LocalDateTime)} — PENDING → RUNNING 时填 started_at</li>
 *   <li>{@link #markFinished(Long, String, String, java.time.LocalDateTime)} — 任意终态更新</li>
 *   <li>{@link #markLogPersisted(Long)} — drone webhook 落完日志后置 1</li>
 * </ul>
 */
@Mapper
public interface BuildRecordMapper extends BaseMapper<BuildRecord> {

    /**
     * 按 drone_build_id 查 — 跳过 @TableLogic 过滤 (drone webhook 找任意状态记录).
     *
     * <p>对应 SQL: {@code SELECT * FROM build_record WHERE drone_build_id = #{droneBuildId} LIMIT 1}.
     */
    @Select("SELECT * FROM build_record WHERE drone_build_id = #{droneBuildId} LIMIT 1")
    BuildRecord selectByDroneBuildId(@Param("droneBuildId") String droneBuildId);

    /**
     * 状态从 PENDING → RUNNING 时, 填 started_at.
     *
     * <p>注意: 不用 MyBatis-Plus updateById, 因为 update 时也会刷 updated_at, 但我们要的是
     * "业务事件" 语义 (started_at 不是更新, 是开始时间), 用原生 SQL 显式更清楚.
     */
    @Update("UPDATE build_record SET status = 'RUNNING', started_at = #{startedAt} "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markRunning(@Param("id") Long id, @Param("startedAt") java.time.LocalDateTime startedAt);

    /**
     * 状态到终态 (SUCCESS / FAILED / TIMEOUT / CANCELED), 填 finished_at + 终态 status.
     *
     * <p>如果 imageTag / harborImageUrl 为 NULL, SQL 不更新 (用 IFNULL).
     */
    @Update("UPDATE build_record SET status = #{status}, " + "image_tag = IFNULL(#{imageTag}, image_tag), "
            + "harbor_image_url = IFNULL(#{harborImageUrl}, harbor_image_url), "
            + "finished_at = #{finishedAt} "
            + "WHERE id = #{id} AND status NOT IN ('SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELED')")
    int markFinished(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("imageTag") String imageTag,
            @Param("harborImageUrl") String harborImageUrl,
            @Param("finishedAt") java.time.LocalDateTime finishedAt);

    /**
     * drone webhook 落完 build_log 后, 把 log_persisted 置 1.
     */
    @Update("UPDATE build_record SET log_persisted = 1 WHERE id = #{id}")
    int markLogPersisted(@Param("id") Long id);
}
