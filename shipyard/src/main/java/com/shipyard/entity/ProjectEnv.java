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

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目-环境关联表 — 一个项目可以在多个环境部署 (dev/staging/prod), 一个环境也可以挂多个项目.
 *
 * <p>对应 V1__init.sql 的 {@code project_env} 表 — 复合主键 ({@code project_id}, {@code env_id}).
 *
 * <p>注意: 此表**不继承 {@link BaseEntity}** — 因为:
 * <ul>
 *   <li>没有自增主键 (用 {@code projectId + envId} 复合主键) — MyBatis-Plus 弱支持复合主键, V1 走 Service 层 LambdaQueryWrapper 显式条件</li>
 *   <li>没有软删字段 (V1 关联关系是硬删, 反正重建成本低)</li>
 *   <li>字段少, 直接平铺更清晰</li>
 * </ul>
 *
 * <p>Service 层使用模式:
 * <pre>{@code
 * // 给项目 5 关联环境 1
 * ProjectEnv pe = new ProjectEnv();
 * pe.setProjectId(5L);
 * pe.setEnvId(1L);
 * projectEnvMapper.insert(pe);
 *
 * // 查项目 5 的所有环境
 * List<ProjectEnv> list = projectEnvMapper.selectList(
 *     new LambdaQueryWrapper<ProjectEnv>().eq(ProjectEnv::getProjectId, 5L)
 * );
 *
 * // 删除关联 (硬删, 不走软删)
 * projectEnvMapper.delete(
 *     new LambdaQueryWrapper<ProjectEnv>()
 *         .eq(ProjectEnv::getProjectId, 5L)
 *         .eq(ProjectEnv::getEnvId, 1L)
 * );
 * }</pre>
 *
 * <p>{@code createdAt} 字段不通过 MetaObjectHandler 填, 走 SQL DEFAULT CURRENT_TIMESTAMP —
 * 这样 Service 层不用关心时间, 插入时只 set {@code projectId + envId} 即可.
 */
@Data
@TableName("project_env")
public class ProjectEnv implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目 ID (复合主键 1/2).
     */
    private Long projectId;

    /**
     * 环境 ID (复合主键 2/2).
     */
    private Long envId;

    /**
     * 关联时间 — 走 SQL DEFAULT, 业务层不写.
     * V1 业务代码不需要读这个字段 (前端不展示), 留着方便排查.
     */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
