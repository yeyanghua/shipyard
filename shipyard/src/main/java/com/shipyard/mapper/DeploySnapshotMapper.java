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
import com.shipyard.entity.DeploySnapshot;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * DeploySnapshot Mapper — 继承 {@link BaseMapper} 拿现成 CRUD.
 *
 * <p>V9 自定义 SQL:
 * <ul>
 *   <li>{@link #selectByDeployRecordId(Long)} — 查某个 deploy 下的所有 snapshot (回滚列表)</li>
 *   <li>{@link #selectByProjectAndEnv(Long, Long)} — 按 project + env 列历史 snapshot</li>
 * </ul>
 */
@Mapper
public interface DeploySnapshotMapper extends BaseMapper<DeploySnapshot> {

    /**
     * 按 deploy_record_id 列所有 snapshot, 按 id DESC (最新在前).
     */
    @Select("SELECT * FROM deploy_snapshot WHERE deploy_record_id = #{deployRecordId} ORDER BY id DESC")
    List<DeploySnapshot> selectByDeployRecordId(@Param("deployRecordId") Long deployRecordId);

    /**
     * 按 project_id + env_id 列所有 snapshot, 按 id DESC.
     *
     * <p>不带 deploy_record_id 过滤 — 用于 DeployDetail 页 "历史 snapshot" 视图
     * (同一 project + env 跨多次 deploy 的所有 snapshot, 支持跨 deploy 回滚).
     */
    @Select("SELECT * FROM deploy_snapshot "
            + "WHERE project_id = #{projectId} AND env_id = #{envId} "
            + "ORDER BY id DESC")
    List<DeploySnapshot> selectByProjectAndEnv(
            @Param("projectId") Long projectId,
            @Param("envId") Long envId);
}
