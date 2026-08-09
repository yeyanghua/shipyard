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
 * 流水线模板 — 一个项目可以有 N 个版本, 同时只有一个 active 版本.
 *
 * <p>对应 V1__init.sql 的 {@code pipeline_template} 表 (M2 已落库).
 *
 * <p><b>注意</b>: 这个实体 <b>不继承 {@link BaseEntity}</b>, 因为 pipeline_template 表
 * 没有 {@code updated_at} 字段 — 业务语义上"模板改时间"用 version 表达, 不需要
 * MyBatis-Plus 的通用 updated_at. 跟 {@link BuildRecord} 一样单独维护 createdAt.
 *
 * <p>关键字段:
 * <ul>
 *   <li>{@code version} — 从 1 自增, 同一 project_id 下唯一 (DB unique key {@code uk_pipeline_project_version})</li>
 *   <li>{@code reviewStatus} — {@link com.shipyard.common.enums.ReviewStatus}, 只有 APPROVED 能 active</li>
 *   <li>{@code isActive} — 是否当前生效, 一个 project 同时只有一个 active (DB key {@code idx_pipeline_active})</li>
 *   <li>{@code aiModifiedBy} — 格式 {@code provider/model}, M6 AI 接入后填充</li>
 *   <li>{@code aiPrompt} — AI 改时的 prompt, 用于审计 + debug</li>
 * </ul>
 *
 * <p>状态机 (v1):
 * <pre>
 *   create/AI生成 ──▶ draft ──approve──▶ approved (可以 active)
 *                       │
 *                       └──reject──▶ rejected (终态)
 * </pre>
 */
@Data
@TableName("pipeline_template")
public class PipelineTemplate {

    /** 主键 (雪花 ID) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属项目 ID */
    @TableField("project_id")
    private Long projectId;

    /**
     * 版本号 — 从 1 自增, 同一 project_id 下唯一.
     * Service 层通过 {@code MAX(version)+1} 算出, 不用 DB sequence.
     */
    @TableField("version")
    private Integer version;

    /**
     * 流水线 YAML 内容 — drone pipeline 格式.
     * V1 直接存, 不解析; M6 3 前端 PipelineEdit 页可改.
     */
    @TableField("yaml_content")
    private String yamlContent;

    /**
     * 审核状态 — {@link com.shipyard.common.enums.ReviewStatus} 字符串值.
     * 用 String 不用 enum: 跟 DB 解耦, 后续扩状态不用动 entity 编译.
     */
    @TableField("review_status")
    private String reviewStatus;

    /**
     * 是否当前生效 — 一个 project 同时只有一个 active 版本.
     * Service 层在 "activate" 时, 先把同 project 的其他 active 置 0, 再把目标版本置 1.
     */
    @TableField("is_active")
    private Integer isActive;

    /** 创建人 (用户名或 userId) */
    @TableField("created_by")
    private String createdBy;

    /**
     * AI 修改人 — 格式 {@code provider/model}, M6 2 接入 AI 改后填.
     * 例如 {@code tongyi/qwen-turbo} / {@code mock/v1} / {@code deepseek/deepseek-chat}.
     * NULL 表示用户手动创建/修改.
     */
    @TableField("ai_modified_by")
    private String aiModifiedBy;

    /**
     * AI 修改时的 prompt — 留痕, 用于审计 + 用户复现.
     * NULL 表示非 AI 创建/修改.
     */
    @TableField("ai_prompt")
    private String aiPrompt;

    /** 创建时间 — INSERT 时自动填 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 逻辑删除标记 — 0=未删, 1=已删 */
    @TableLogic
    private Integer deleted;
}
