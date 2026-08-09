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

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目元数据 — 船厂里"造一艘船"的基本信息.
 *
 * <p>对应 V1__init.sql 的 {@code project} 表 (M2 已落库).
 *
 * <p>关键字段:
 * <ul>
 *   <li>{@code name} — 英文唯一标识 (小写字母/数字/中划线), 业务层校验格式</li>
 *   <li>{@code repoTokenEnc} — 仓库访问 token 的密文, 由 {@code Encrypter} 加密, 不存明文</li>
 *   <li>{@code projectMeta} — JSON 字符串: 语言版本/主类/jar 名/端口 等动态字段</li>
 *   <li>{@code projectType} — 枚举: java_maven / java_gradle / node_pnpm / python_poetry / other</li>
 * </ul>
 *
 * <p>被引用方:
 * <ul>
 *   <li>{@link com.shipyard.entity.ProjectEnv} — 关联环境 (N:N)</li>
 *   <li>{@link com.shipyard.entity.EnvVariable} — 项目级环境变量 (project_id 非空)</li>
 *   <li>(M5+) pipeline_template / build_record / deploy_record / project_dockerfile</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project")
public class Project extends BaseEntity {

    /**
     * 项目唯一名 (英文, 小写字母/数字/中划线, 1-64 字符).
     */
    private String name;

    /**
     * 显示名 (中文/任意, 1-128 字符, UI 展示用).
     */
    private String displayName;

    /**
     * 仓库平台 — {@code gitlab} / {@code gitee}.
     * 业务层校验 (不允许其他值), M4 暂不验证 token 有效性.
     */
    private String repoProvider;

    /**
     * 仓库 URL — 例如 {@code https://gitlab.example.com/group/demo-java-app.git}.
     */
    private String repoUrl;

    /**
     * 仓库访问 token 的密文 (AES-256 加密, Base64 编码).
     *
     * <p>前端传明文 {@code repoToken}, Service 层调 {@code Encrypter.encrypt()} 加密后入库.
     * 列表/详情 API 返回时**不**回显明文, 只暴露 {@code hasRepoToken: boolean} 标记.
     */
    private String repoTokenEnc;

    /**
     * 默认构建分支, 默认 {@code main}.
     */
    private String defaultBranch;

    /**
     * 项目类型 — {@code java_maven} / {@code java_gradle} / {@code node_pnpm} / {@code python_poetry} / {@code other}.
     * 决定 M12 选哪个 Dockerfile 模板.
     */
    private String projectType;

    /**
     * 项目元数据 JSON 字符串, 例如:
     * <pre>{@code
     * {
     *   "javaVersion": "21",
     *   "mainClass": "com.example.App",
     *   "jarName": "app.jar",
     *   "port": 8080
     * }
     * }</pre>
     *
     * <p>V1 用 String 存, 业务层 parse; V1.5 可改 JSON 字段 + JacksonTypeHandler.
     */
    private String projectMeta;

    /**
     * 项目描述 (选填, 最多 512 字符).
     */
    private String description;
}
