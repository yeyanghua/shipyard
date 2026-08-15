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
 * 环境定义 — 一套 k8s 集群的元数据.
 *
 * <p>对应 V4__redesign_worker_table.sql 的 {@code env} 表 (M9.5 redesign).
 *
 * <p>关键字段:
 * <ul>
 *   <li>{@code name} — 英文唯一标识 (dev / staging / prod)</li>
 *   <li>{@code isProduction} — 生产环境标记, 影响前端确认弹窗和告警级别</li>
 * </ul>
 *
 * <p>M9.5 删的字段 (env 不再管 worker 部署细节):
 * <ul>
 *   <li>{@code k8sNamespace} — 移到 worker 表 (每个 worker 自己有 pod metadata.namespace)</li>
 *   <li>{@code workerUrl} — 移到 worker 表 (worker register 时上报, shipyard → worker 调用走这个)</li>
 *   <li>{@code workerTokenEnc} — 移到 worker 表 (每个 worker 自己的 token, 哈希存库)</li>
 * </ul>
 *
 * <p>被引用方:
 * <ul>
 *   <li>{@link com.shipyard.entity.ProjectEnv} — 关联项目 (N:N)</li>
 *   <li>{@link com.shipyard.entity.EnvVariable} — 环境变量 (一对多, 必填 env_id)</li>
 *   <li>{@link com.shipyard.worker.entity.Worker} — worker (1:N, M9.5: 1 env 可挂多个 worker, 跨集群跨地域)</li>
 *   <li>{@link com.shipyard.entity.DeployRecord} — 发布记录 (一对多)</li>
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
     * 是否生产环境 — 影响:
     * <ul>
     *   <li>前端: 触发构建/发布时二次确认弹窗</li>
     *   <li>告警: P0/P1 升级策略</li>
     *   <li>审计: 单独标记</li>
     * </ul>
     */
    private Integer isProduction;
}
