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

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 构建记录 — 每次 shipyard 触发 drone 构建一行.
 *
 * <p>对应 V1__init.sql 的 {@code build_record} 表 (M2 已落库).
 *
 * <p>关键字段:
 * <ul>
 *   <li>{@code status} — {@link com.shipyard.common.enums.BuildStatus} 字符串值</li>
 *   <li>{@code droneBuildId} — drone 端的 build id (V1 mock 阶段 shipyard 自己生成 UUID)</li>
 *   <li>{@code imageTag} / {@code harborImageUrl} — 成功后填充</li>
 *   <li>{@code logPersisted} — 1=全部 step 日志已落 {@code build_log} (shipyard 这边可查)</li>
 * </ul>
 *
 * <p>状态机见 {@code BuildStatus} enum.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("build_record")
public class BuildRecord extends BaseEntity {

    /** 所属项目 */
    @TableField("project_id")
    private Long projectId;

    /** 使用的流水线模板版本 ID (M5 V1 demo 阶段 NULL, M6 接入 AI 生成 pipeline 后填充) */
    @TableField("pipeline_template_id")
    private Long pipelineTemplateId;

    /** 构建的 commit SHA (V1 demo 默认 'mock-sha-{timestamp}') */
    @TableField("commit_sha")
    private String commitSha;

    /** commit message (V1 demo 阶段前端传入或自动生成) */
    @TableField("commit_message")
    private String commitMessage;

    /** 触发人 (前端传 userId) */
    @TableField("triggered_by")
    private String triggeredBy;

    /** 触发方式 — {@link com.shipyard.common.enums.TriggerType} 字符串值 */
    @TableField("trigger_type")
    private String triggerType;

    /** drone 端 build id (V1 mock 阶段 shipyard 生成 UUID, V1.5 改用真实 drone 返回值) */
    @TableField("drone_build_id")
    private String droneBuildId;

    /**
     * 状态 — 跟 {@link com.shipyard.common.enums.BuildStatus} 一一对应.
     *
     * <p>用 String 而不是 enum: 跟 DB 解耦, 后续扩状态不用动 entity 编译.
     */
    @TableField("status")
    private String status;

    /** 构建出的镜像 tag (成功后填, 例 {@code v1.0.0-abc1234}) */
    @TableField("image_tag")
    private String imageTag;

    /** Harbor 镜像 URL (成功后填, 例 {@code harbor.shipyard.local/demo/myapp:v1.0.0-abc1234}) */
    @TableField("harbor_image_url")
    private String harborImageUrl;

    /** drone 实际开始时间 (从 PENDING → RUNNING 时填) */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 结束时间 (任何终态都填) */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /** drone 日志 URL (兜底用, V1 demo 阶段 NULL) */
    @TableField("log_url")
    private String logUrl;

    /**
     * 日志是否已落 shipyard (1=是).
     *
     * <p>drone webhook 落 {@code build_log} 完成后置 1, 前端"查日志"先看这里.
     */
    @TableField("log_persisted")
    private Integer logPersisted;
}
