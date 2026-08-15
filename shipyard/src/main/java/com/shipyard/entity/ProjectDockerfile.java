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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目 Dockerfile 实例 — 一次"生成"对应一条记录.
 *
 * <p>状态机:
 * <ul>
 *   <li>{@code draft} — 已渲染, 等待 commit (V1 demo 卡这里)</li>
 *   <li>{@code pushed} — 已 commit 到 repo (V1.5 真接 Gitea 后用)</li>
 *   <li>{@code rejected} — 拒绝 (V1.5 审批流)</li>
 * </ul>
 */
@Data
@TableName("project_dockerfile")
public class ProjectDockerfile {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;
    private Long dockerfileTemplateId;

    /** 渲染后的 Dockerfile 内容 (即将 commit 进项目仓库) */
    private String renderedContent;

    /** 渲染时用的变量值 (JSON string) */
    private String variableValues;

    /** 提交的目标分支 */
    private String repoBranch;

    /** 提交后的 commit SHA (V1 留空, V1.5 真接 Gitea 后填) */
    private String repoCommitSha;

    /** 提交 message */
    private String commitMessage;

    /** draft / pushed / rejected */
    private String status;

    private LocalDateTime createdAt;

    /** push 时间 (V1 留空) */
    private LocalDateTime pushedAt;

    @TableLogic
    private Integer deleted;
}
