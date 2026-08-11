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
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 部署记录 — 每次 shipyard 触发 worker 部署一行.
 *
 * <p>对应 V2__add_deploy_tables.sql 的 {@code deploy_record} 表 (M9 新增).
 *
 * <p>关键字段:
 * <ul>
 *   <li>{@code projectId} / {@code envId} — 目标项目 + 目标环境(决定 deploy 到哪个集群)</li>
 *   <li>{@code buildRecordId} — 镜像来源 build, 可空(手动填 imageTag)</li>
 *   <li>{@code imageTag} / {@code namespace} — 实际部署的镜像 + 目标 ns (例 shipyard-shanghai-dev)</li>
 *   <li>{@code deployYamlSha256} — shipyard 渲染完的 yaml sha256 (回滚查重 + diff 用)</li>
 *   <li>{@code currentSnapshotId} — 当前生效的 deploy_snapshot.id, deploy_record 1—N snapshot</li>
 *   <li>{@code status} — {@link com.shipyard.common.enums.DeployStatus} 字符串值</li>
 * </ul>
 *
 * <p>状态机见 {@code DeployStatus} enum.
 */
@Data
@TableName("deploy_record")
public class DeployRecord {

    /** 主键 (雪花 ID) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属项目 */
    @TableField("project_id")
    private Long projectId;

    /** 所属环境 — 决定 deploy 到哪个集群 (1 env ↔ 1 cluster) */
    @TableField("env_id")
    private Long envId;

    /** 关联的 build_record.id, 可空(手动选 image) */
    @TableField("build_record_id")
    private Long buildRecordId;

    /** 实际部署的镜像 tag (例 nginx:1.27.0) */
    @TableField("image_tag")
    private String imageTag;

    /** 实际 ns (shipyard-{env_name}) */
    @TableField("namespace")
    private String namespace;

    /** shipyard 渲染完的 yaml sha256, 16 进制 64 字符 */
    @TableField("deploy_yaml_sha256")
    private String deployYamlSha256;

    /** 当前生效的 deploy_snapshot.id, deploy 成功后填 */
    @TableField("current_snapshot_id")
    private Long currentSnapshotId;

    /** 状态 — {@link com.shipyard.common.enums.DeployStatus} 字符串值 */
    @TableField("status")
    private String status;

    /** 错误信息 (FAILED 时填) */
    @TableField("error_message")
    private String errorMessage;

    /** 实际开始时间 (PENDING → RUNNING 时填) */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 结束时间 (任何终态都填) */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /** 触发人 (前端传 userId) */
    @TableField("triggered_by")
    private String triggeredBy;

    /** 触发方式 — MANUAL / GIT_PUSH (V1.5) */
    @TableField("trigger_type")
    private String triggerType;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
