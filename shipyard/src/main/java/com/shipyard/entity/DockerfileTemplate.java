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
 * Dockerfile 模板 (shipyard 内置 4-5 套主流栈, 用户 V1.5 可自定义).
 *
 * <p>渲染流程:
 * <ol>
 *   <li>前端列模板选一个 (例: java_maven_jdk21)</li>
 *   <li>按 variable_schema 收集变量值 (mainClass, jarName, port, ...)</li>
 *   <li>Service 用简单 ${var} 替换渲染 template_content</li>
 *   <li>写入 project_dockerfile.rendered_content (status=draft)</li>
 *   <li>V1: status 卡 draft, V1.5 真 commit 到 repo 后转 pushed</li>
 * </ol>
 */
@Data
@TableName("dockerfile_template")
public class DockerfileTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 模板名 (如 java_maven_jdk21) — 唯一 key, 前端用这个选 */
    private String name;

    /** 显示名 (UI 给人看) */
    private String displayName;

    /** 语言: java / node / python */
    private String language;

    /** 构建工具: maven / gradle / pnpm / poetry */
    private String buildTool;

    /** 模板内容 (含 ${var} 占位符, Service 渲染时替换) */
    private String templateContent;

    /**
     * 变量定义 (JSON string).
     * 格式: [{key, type, default, description, required}]
     * V1 简化: 不严格解析, 仅供 UI 提示.
     */
    private String variableSchema;

    /** 模板版本 (同 name 可多版本, V1 都用 1) */
    private Integer version;

    /** 1=内置 (shipyard 自带), 0=用户自定义 (V1.5) */
    private Integer isBuiltin;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
