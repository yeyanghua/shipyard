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
import com.shipyard.entity.ProjectEnv;
import org.apache.ibatis.annotations.Mapper;

/**
 * ProjectEnv Mapper — 复合主键 ({@code projectId, envId}).
 *
 * <p>注意: 不要用 {@code selectById()}, 因为 MyBatis-Plus 会按 {@code @TableId} 字段查单字段,
 * 跟 SQL 复合主键不匹配. 统一用 {@code LambdaQueryWrapper} 显式条件.
 */
@Mapper
public interface ProjectEnvMapper extends BaseMapper<ProjectEnv> {}
