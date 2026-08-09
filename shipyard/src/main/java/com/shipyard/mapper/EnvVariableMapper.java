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
import com.shipyard.entity.EnvVariable;
import org.apache.ibatis.annotations.Mapper;

/**
 * EnvVariable Mapper — 继承 {@link BaseMapper} 拿现成 CRUD.
 *
 * <p>业务查询 (按 env / project 过滤, secret 隐藏) 走 Service 层 {@code LambdaQueryWrapper}.
 */
@Mapper
public interface EnvVariableMapper extends BaseMapper<EnvVariable> {
}
