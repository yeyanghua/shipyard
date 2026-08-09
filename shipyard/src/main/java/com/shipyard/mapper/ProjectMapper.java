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
import com.shipyard.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Project Mapper — 继承 {@link BaseMapper} 拿现成 CRUD.
 *
 * <p>业务查询 (分页+关键字) 走 Service 层 {@code LambdaQueryWrapper} 拼条件, 不需要自定义 SQL.
 *
 * <p>V1 加一个 raw SQL 方法: {@link #selectIdByNameRaw(String)} — 绕过 {@code @TableLogic} 过滤,
 * 用于"软删后用同名复活" 的业务场景 (见 {@code ProjectServiceImpl.create()}).
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 按 name 查 ID — 不过滤软删 (复活逻辑用).
     *
     * <p>对应 SQL: {@code SELECT id FROM project WHERE name = #{name} LIMIT 1}.
     * 注意: 跟 {@code @TableLogic} 互斥, 临时绕过.
     */
    @Select("SELECT id FROM project WHERE name = #{name} LIMIT 1")
    Long selectIdByNameRaw(@Param("name") String name);

    /**
     * 按 ID 查 — 不过滤软删 (复活逻辑用).
     *
     * <p>对应 SQL: {@code SELECT * FROM project WHERE id = #{id}}.
     */
    @Select("SELECT * FROM project WHERE id = #{id}")
    Project selectByIdIncludeDeleted(@Param("id") Long id);
}
