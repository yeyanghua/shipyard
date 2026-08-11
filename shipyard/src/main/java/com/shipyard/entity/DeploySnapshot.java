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
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 部署 yaml 快照 — 每次部署成功的 yaml 落一行, 一键回滚源.
 *
 * <p>对应 V2__add_deploy_tables.sql 的 {@code deploy_snapshot} 表 (M9 新增).
 *
 * <p>关键字段:
 * <ul>
 *   <li>{@code deployRecordId} — 关联 deploy_record.id</li>
 *   <li>{@code deployYaml} — shipyard 渲染完的完整 K8s yaml (LONGTEXT, 多个 --- 分隔的 resource)</li>
 *   <li>{@code deployYamlSha256} — 同 deploy_record.deploy_yaml_sha256, 冗余方便 diff</li>
 * </ul>
 *
 * <p>V1.5 考虑:加 {@code liveManifest} 字段(worker 调 k8s API 拿到的真生效 spec, 用于跟 deployYaml diff).
 *
 * <p><b>注意</b>: 跟 deploy_record 不同, snapshot 不继承 {@link BaseEntity}, 因为:
 * <ol>
 *   <li>snapshot 是 append-only(只插不删不改), 没有 updated_at 业务语义</li>
 *   <li>snapshot 不需要 @TableLogic 软删 — 删除时直接 DELETE FROM deploy_snapshot WHERE id=?, 因为
 *       业务上 snapshot 被误删不应该"复活"</li>
 * </ol>
 */
@Data
@TableName("deploy_snapshot")
public class DeploySnapshot {

    /** 主键 (雪花 ID) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联 deploy_record.id */
    @TableField("deploy_record_id")
    private Long deployRecordId;

    /** 冗余 env_id (查 list 时过滤用, 避免 join deploy_record) */
    @TableField("env_id")
    private Long envId;

    /** 冗余 project_id */
    @TableField("project_id")
    private Long projectId;

    /** shipyard 渲染完的完整 K8s yaml (LONGTEXT) */
    @TableField("deploy_yaml")
    private String deployYaml;

    /** deployYaml 的 SHA-256, 16 进制 64 字符 */
    @TableField("deploy_yaml_sha256")
    private String deployYamlSha256;

    /** 创建人 (前端传 userId) */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
