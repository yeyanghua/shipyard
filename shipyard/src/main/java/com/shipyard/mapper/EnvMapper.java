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
import com.shipyard.entity.Env;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Env Mapper — 继承 {@link BaseMapper} 拿现成 CRUD.
 *
 * <p>raw SQL {@link #selectIdByNameRaw(String)} 绕过 {@code @TableLogic}, 用于复活同名环境.
 */
@Mapper
public interface EnvMapper extends BaseMapper<Env> {

    @Select("SELECT id FROM env WHERE name = #{name} LIMIT 1")
    Long selectIdByNameRaw(@Param("name") String name);
}
