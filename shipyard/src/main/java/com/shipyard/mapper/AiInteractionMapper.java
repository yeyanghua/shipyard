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
import com.shipyard.entity.AiInteraction;
import org.apache.ibatis.annotations.Mapper;

/**
 * AiInteraction Mapper — 继承 {@link BaseMapper} 拿现成 CRUD.
 *
 * <p>V1 范围: 流水表只 insert + 分页 select, 走 {@code LambdaQueryWrapper} 即可,
 * 不需要自定义 SQL. 后续如果要按 capability / provider 走索引查询,
 * 用 MyBatis-Plus 的 wrapper 也会自动利用 {@code idx_ai_capability} / {@code idx_ai_provider}.
 *
 * <p><b>注意</b>: 这表是 <b>不可变</b> — 没有 update 也没有 delete,
 * 业务层一旦写入不应修改 (审计要求).
 */
@Mapper
public interface AiInteractionMapper extends BaseMapper<AiInteraction> {}
