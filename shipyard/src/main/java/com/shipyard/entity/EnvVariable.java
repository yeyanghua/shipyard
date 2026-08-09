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
 * 环境变量 — env × project × key 三维度的 Key-Value 存储, 值加密.
 *
 * <p>对应 V1__init.sql 的 {@code env_variable} 表.
 *
 * <p>关键设计点:
 * <ul>
 *   <li>{@code projectId} 可空 — NULL 表示"全局变量"(所有项目共享), 非空表示"项目级变量"</li>
 *   <li>唯一约束: {@code (env_id, project_id, var_key)} — 同一环境同一项目不能有重复 key</li>
 *   <li>{@code varValueEnc} — 密文, 走 {@code Encrypter.encrypt()}; 启动时全量校验解密</li>
 *   <li>{@code isSecret} — 1=敏感(列表不显示值, 显示 "***"), 0=明文</li>
 *   <li>查询优先级: 项目级 ({@code project_id != NULL}) > 全局 ({@code project_id == NULL}), 同 key 覆盖</li>
 * </ul>
 *
 * <p>被引用方:
 * <ul>
 *   <li>(M7) VariableInjector — 拼 vars.yaml 喂给 drone</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("env_variable")
public class EnvVariable extends BaseEntity {

    /**
     * 环境 ID (必填).
     */
    private Long envId;

    /**
     * 项目 ID — NULL 表示全局变量, 非 NULL 表示项目级变量.
     */
    private Long projectId;

    /**
     * 变量名 (KEY), 1-128 字符, 业务层校验 ([A-Z0-9_]+ 推荐).
     */
    private String varKey;

    /**
     * 变量值 (密文, AES-256-GCM 加密, Base64 编码).
     *
     * <p>Service 层在 {@code list()} 时:
     * <ul>
     *   <li>{@code isSecret=1} — 返回 {@code "***"} 代替</li>
     *   <li>{@code isSecret=0} — 解密后返回明文</li>
     * </ul>
     */
    private String varValueEnc;

    /**
     * 是否敏感 — 1=敏感(UI 隐藏, API 列表返回 "***"), 0=明文(API 列表也返回明文).
     *
     * <p>默认 1 (敏感), 因为绝大多数环境变量是密码/token.
     */
    private Integer isSecret;

    /**
     * 变量说明 (选填, 最多 512 字符).
     */
    private String description;

    /**
     * 更新人 (V1 demo 固定写 {@code "demo-user"}, V1.5 接用户系统).
     */
    private String updatedBy;
}
