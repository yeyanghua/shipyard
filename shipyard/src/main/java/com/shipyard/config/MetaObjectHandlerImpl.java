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

package com.shipyard.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus 自动填充处理器 — 给 {@code @TableField(fill=...)} 字段填默认值.
 *
 * <p>对应 {@link com.shipyard.entity.BaseEntity} 的:
 * <ul>
 *   <li>{@code createdAt} — INSERT 时填 {@code LocalDateTime.now()}</li>
 *   <li>{@code updatedAt} — INSERT + UPDATE 时填 {@code LocalDateTime.now()}</li>
 * </ul>
 *
 * <p>{@code deleted} 字段不自动填 (业务层显式 set, MyBatis-Plus 0/1 默认值走 SQL DEFAULT).
 *
 * <p>不继承 BaseEntity 的表 (例如 {@code project_env}) 不受影响 — 没有 fill 注解.
 */
@Slf4j
@Component
public class MetaObjectHandlerImpl implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
