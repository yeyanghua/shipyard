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
 * 环境定义 — 一套 k8s 集群 + worker 服务的元数据.
 *
 * <p>对应 V1__init.sql 的 {@code env} 表.
 *
 * <p>关键字段:
 * <ul>
 *   <li>{@code name} — 英文唯一标识 (dev / staging / prod)</li>
 *   <li>{@code workerUrl} — worker 服务 URL (每环境一个独立 Go 进程, M8 实现)</li>
 *   <li>{@code workerTokenEnc} — worker 鉴权 token 密文, shipyard → worker 调用凭据</li>
 *   <li>{@code isProduction} — 生产环境标记, 影响前端确认弹窗和告警级别</li>
 * </ul>
 *
 * <p>被引用方:
 * <ul>
 *   <li>{@link com.shipyard.entity.ProjectEnv} — 关联项目 (N:N)</li>
 *   <li>{@link com.shipyard.entity.EnvVariable} — 环境变量 (一对多, 必填 env_id)</li>
 *   <li>(M7+) worker 表 (一对多, 一环境多 worker)</li>
 *   <li>(M7+) deploy_record (一对多)</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("env")
public class Env extends BaseEntity {

    /**
     * 环境唯一名 (英文, 小写字母/数字/中划线), 例如 {@code dev} / {@code staging} / {@code prod}.
     */
    private String name;

    /**
     * 显示名, 例如 "开发环境" / "预发环境" / "生产环境".
     */
    private String displayName;

    /**
     * 集群类型 — V1 只支持 {@code k8s}. V1.5 可能加 {@code docker-compose}.
     */
    private String clusterType;

    /**
     * k8s namespace — 该环境部署的目标 namespace.
     * 例如 {@code demo-java-app-dev} / {@code demo-java-app-prod}.
     */
    private String k8sNamespace;

    /**
     * worker 服务 URL — 每环境一个 worker (Go 进程, M8 实现).
     * 例如 {@code http://worker-dev.shipyard.svc.cluster.local:8081}.
     */
    private String workerUrl;

    /**
     * worker 鉴权 token 密文 (AES-256 加密, Base64 编码).
     *
     * <p>shipyard → worker 调用时解密后放 {@code Authorization: Bearer xxx} 头.
     * 列表/详情 API 不回显明文.
     */
    private String workerTokenEnc;

    /**
     * 是否生产环境 — 影响:
     * <ul>
     *   <li>前端: 触发构建/发布时二次确认弹窗</li>
     *   <li>告警: P0/P1 升级策略</li>
     *   <li>审计: 单独标记</li>
     * </ul>
     */
    private Integer isProduction;
}
