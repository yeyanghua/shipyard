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
import com.shipyard.entity.PipelineTemplate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * PipelineTemplate Mapper — 继承 {@link BaseMapper} 拿现成 CRUD.
 *
 * <p>V1 自定义 SQL:
 * <ul>
 *   <li>{@link #selectMaxVersion(Long)} — 查 project 的当前最大 version, 用于新版本自增</li>
 *   <li>{@link #selectActiveByProjectId(Long)} — 查 project 当前 active 版本 (0 或 1 个)</li>
 *   <li>{@link #unactivateOthersByProjectId(Long, Long)} — activate 前, 把同 project 其他 active 置 0</li>
 *   <li>{@link #selectVersionsByProjectId(Long)} — 查 project 的所有版本, 按 version DESC</li>
 * </ul>
 */
@Mapper
public interface PipelineTemplateMapper extends BaseMapper<PipelineTemplate> {

    /**
     * 查 project 的最大 version — 0 表示该项目还没建过任何 pipeline.
     *
     * <p>对应 SQL: {@code SELECT COALESCE(MAX(version), 0) FROM pipeline_template
     *                WHERE project_id = #{projectId} AND deleted = 0}.
     *
     * <p>用 {@code COALESCE} 让空表返 0, 业务层 {@code +1} 即可得第一个版本号.
     */
    @Select("SELECT COALESCE(MAX(version), 0) FROM pipeline_template "
            + "WHERE project_id = #{projectId} AND deleted = 0")
    int selectMaxVersion(@Param("projectId") Long projectId);

    /**
     * 查 project 当前 active 版本 — 0 或 1 个 (业务约束保证).
     *
     * <p>对应 SQL: {@code SELECT * FROM pipeline_template
     *                WHERE project_id = #{projectId} AND is_active = 1 AND deleted = 0 LIMIT 1}.
     */
    @Select("SELECT * FROM pipeline_template "
            + "WHERE project_id = #{projectId} AND is_active = 1 AND deleted = 0 LIMIT 1")
    PipelineTemplate selectActiveByProjectId(@Param("projectId") Long projectId);

    /**
     * 激活目标版本前, 先把同 project 的其他 active 置 0 — 保证"一项目同时只有一个 active".
     *
     * <p>对应 SQL: {@code UPDATE pipeline_template SET is_active = 0
     *                WHERE project_id = #{projectId} AND id != #{exceptId} AND is_active = 1}.
     *
     * @param projectId 项目 ID
     * @param exceptId  排除的 ID (即将被 activate 的版本)
     * @return 受影响行数
     */
    @Update("UPDATE pipeline_template SET is_active = 0 "
            + "WHERE project_id = #{projectId} AND id != #{exceptId} AND is_active = 1")
    int unactivateOthersByProjectId(@Param("projectId") Long projectId, @Param("exceptId") Long exceptId);

    /**
     * 查 project 的所有版本, 按 version 降序.
     *
     * <p>对应 SQL: {@code SELECT * FROM pipeline_template
     *                WHERE project_id = #{projectId} AND deleted = 0
     *                ORDER BY version DESC}.
     */
    @Select("SELECT * FROM pipeline_template " + "WHERE project_id = #{projectId} AND deleted = 0 "
            + "ORDER BY version DESC")
    List<PipelineTemplate> selectVersionsByProjectId(@Param("projectId") Long projectId);
}
