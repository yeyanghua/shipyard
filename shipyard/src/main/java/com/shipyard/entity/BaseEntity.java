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

package com.shipyard.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类 — 抽公共字段: 主键(雪花)/ 创建时间/ 更新时间/ 逻辑删除.
 *
 * <p>所有 V1 业务实体都继承这个类(关联表 project_env 因为没有自增主键, 不继承).
 *
 * <p>MyBatis-Plus 字段填充策略:
 * <ul>
 *   <li>{@code createdAt} — INSERT 时自动填充当前时间</li>
 *   <li>{@code updatedAt} — INSERT + UPDATE 时自动填充当前时间</li>
 *   <li>{@code deleted}   — 软删标记, 0=未删, 1=已删, @TableLogic 自动过滤</li>
 * </ul>
 *
 * <p>自动填充由 {@link com.shipyard.config.MetaObjectHandlerImpl} 拦截.
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 (雪花 ID) — 分布式安全,无需 DB 自增.
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 创建时间 — INSERT 时自动填充,业务代码不要 set.
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间 — INSERT + UPDATE 时自动填充.
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记 — 0=未删, 1=已删.
     *
     * <p>MyBatis-Plus {@code @TableLogic} 拦截 SELECT/UPDATE/DELETE, 自动:
     * <ul>
     *   <li>SELECT — 拼 {@code deleted = 0}</li>
     *   <li>UPDATE — 拼 {@code deleted = 0} (不影响软删的恢复逻辑)</li>
     *   <li>DELETE — 改为 UPDATE deleted=1</li>
     * </ul>
     */
    @TableLogic
    private Integer deleted;
}
